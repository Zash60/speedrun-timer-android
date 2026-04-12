package il.ronmad.speedruntimer

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.*
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.checkbox.checkBoxPrompt
import com.afollestad.materialdialogs.checkbox.isCheckPromptChecked
import il.ronmad.speedruntimer.activities.MainActivity
import il.ronmad.speedruntimer.databinding.TimerOverlayBinding
import il.ronmad.speedruntimer.realm.*
import io.realm.Realm
import io.realm.RealmChangeListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Foreground service that displays a draggable timer overlay on screen.
 * Manages timer state, splits, delta display, and persistent notification.
 */
class TimerService : Service() {

    // === Database ===
    private lateinit var realm: Realm
    private lateinit var realmChangeListener: RealmChangeListener<Realm>

    // === State ===
    lateinit var prefs: SharedPreferences
        private set
    private lateinit var category: Category
    private var hasSplits = false
    private var comparison: Comparison = Comparison.PERSONAL_BEST

    // === Splits tracking ===
    private var splitsIter: MutableListIterator<Split>? = null
    private val segmentTimes = mutableListOf<Long>()
    private var currentSplitStartTime = 0L

    // === Overlay ===
    private lateinit var mBinding: TimerOverlayBinding
    private lateinit var mWindowManager: WindowManager
    private lateinit var mWindowParams: WindowManager.LayoutParams
    private var moved = false
    private var overlayAdded = false

    // === Timer ===
    private lateinit var chronometer: Chronometer

    // === Notification ===
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var receiver: BroadcastReceiver

    private var startedProperly = false

    // ========================================================================
    // Lifecycle
    // ========================================================================

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        realm = Realm.getDefaultInstance()
        realmChangeListener = RealmChangeListener { onRealmDataChanged() }
        realm.addChangeListener(realmChangeListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        gameName = intent?.getStringExtra(getString(R.string.extra_game)).orEmpty()
        categoryName = intent?.getStringExtra(getString(R.string.extra_category)).orEmpty()

        if (gameName.isEmpty() || categoryName.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        category = realm.getCategoryByName(gameName, categoryName)
            ?: run { stopSelf(); return START_NOT_STICKY }

        hasSplits = category.splits.isNotEmpty()

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        setupOverlay()
        loadPreferences()
        setupNotificationBroadcastReceiver()
        startForeground(R.integer.notification_id, buildAndShowNotification())

        startedProperly = true
        IS_ACTIVE = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        realm.removeChangeListener(realmChangeListener)
        if (startedProperly) {
            if (overlayAdded) {
                mWindowManager.removeView(mBinding.root)
            }
            unregisterReceiverSafe(receiver)
        }
        realm.close()
        IS_ACTIVE = false
        super.onDestroy()
    }

    // ========================================================================
    // Public API
    // ========================================================================

    fun closeTimer(fromOnResume: Boolean) {
        if (chronometer.isStarted) {
            Dialogs.showTimerActiveDialog(this, fromOnResume) { stopSelf() }
        } else {
            stopSelf()
        }
    }

    // ========================================================================
    // Realm change listener
    // ========================================================================

    private fun onRealmDataChanged() {
        notificationBuilder.setContentText(
            if (category.bestTime > 0) "PB: ${category.bestTime.getFormattedTime()}" else null
        )
        notificationManager.notify(R.integer.notification_id, notificationBuilder.build())
    }

    // ========================================================================
    // Preferences
    // ========================================================================

    private fun loadPreferences() {
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val chronometerConfig = Chronometer.Config(
            colorNeutral = getPrefColor(R.string.key_pref_color_neutral, R.color.colorTimerNeutralDefault),
            colorAhead = getPrefColor(R.string.key_pref_color_ahead, R.color.colorTimerAheadDefault),
            colorBehind = getPrefColor(R.string.key_pref_color_behind, R.color.colorTimerBehindDefault),
            colorPB = getPrefColor(R.string.key_pref_color_pb, R.color.colorTimerPBDefault),
            colorBestSegment = getPrefColor(R.string.key_pref_color_best_segment, R.color.colorTimerBestSegmentDefault),
            countdown = prefs.getLong(getString(R.string.key_pref_timer_countdown), 0L),
            showMillis = prefs.getBoolean(getString(R.string.key_pref_timer_show_millis), true),
            alwaysMinutes = prefs.getBoolean(getString(R.string.key_pref_timer_always_minutes), true),
        )
        chronometer = Chronometer(mBinding, chronometerConfig)
        comparison = getComparison()
    }

    private fun getPrefColor(keyRes: Int, defaultRes: Int): Int =
        prefs.getInt(getString(keyRes), getColorCpt(defaultRes))

    // ========================================================================
    // Notification & broadcast receiver
    // ========================================================================

    private fun setupNotificationBroadcastReceiver() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == getString(R.string.action_close_timer)) {
                    val fromOnResume = intent.getBooleanExtra(
                        getString(R.string.extra_close_timer_from_onresume), true
                    )
                    closeTimer(fromOnResume)
                }
            }
        }
        val filter = IntentFilter(getString(R.string.action_close_timer))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun unregisterReceiverSafe(receiver: BroadcastReceiver) {
        try {
            unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // Receiver was never registered or already unregistered
        }
    }

    private fun buildAndShowNotification(): Notification {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val closeIntent = Intent(getString(R.string.action_close_timer)).apply {
            putExtra(getString(R.string.extra_close_timer_from_onresume), false)
        }

        notificationBuilder = NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
            .setSmallIcon(R.drawable.ic_timer_black_48dp)
            .setContentTitle("${category.gameName} ${category.name}")
            .setContentText(if (category.bestTime > 0) "PB: ${category.bestTime.getFormattedTime()}" else null)
            .setContentIntent(
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), pendingFlags)
            )
            .addAction(
                R.drawable.ic_close_black_24dp,
                getString(R.string.close_timer),
                PendingIntent.getBroadcast(this, 0, closeIntent, pendingFlags)
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        return notificationBuilder.build().also {
            notificationManager.notify(R.integer.notification_id, it)
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            getString(R.string.notification_channel_id),
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            enableVibration(false)
            enableLights(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    // ========================================================================
    // Overlay setup
    // ========================================================================

    private fun setupOverlay() {
        setTheme(R.style.AppTheme)
        mWindowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mBinding = TimerOverlayBinding.inflate(LayoutInflater.from(this))

        applyDisplayPreferences()
        setupTouchHandling()
        addOverlayToWindow()
    }

    private fun applyDisplayPreferences() {
        mBinding.root.setBackgroundColor(
            prefs.getInt(
                getString(R.string.key_pref_color_background),
                getColorCpt(R.color.colorTimerBackgroundDefault)
            )
        )
        applyTimerSize()
    }

    private fun applyTimerSize() {
        val sizesArray = resources.getStringArray(R.array.timer_sizes_values)
        val baseSize = prefs.getString(
            getString(R.string.key_pref_timer_size),
            sizesArray[1]
        )!!.toFloat()

        mBinding.timerTime.textSize = baseSize
        mBinding.timerMinus.textSize = baseSize
        mBinding.timerDelta.textSize = baseSize * 0.44f
        mBinding.timerSplit.textSize = baseSize * 0.39f
    }

    // ========================================================================
    // Touch handling (drag, tap, long-press)
    // ========================================================================

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchHandling() {
        mBinding.root.setOnTouchListener(createDragTouchListener())
        mBinding.root.setOnLongClickListener(::onLongClick)
    }

    private fun createDragTouchListener(): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var touchTime = 0L
            private var prevTapTime = 0L
            private val warmupEnd = System.currentTimeMillis() + 300

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (System.currentTimeMillis() < warmupEnd) return false

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = mWindowParams.x
                        initialY = mWindowParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        touchTime = System.currentTimeMillis()
                        moved = false
                    }

                    MotionEvent.ACTION_UP -> {
                        if (moved) {
                            category.getGame().getPosition().set(mWindowParams.x, mWindowParams.y)
                        }
                        if (moved || System.currentTimeMillis() - touchTime >= 250) return false

                        val now = System.currentTimeMillis()
                        if (now - prevTapTime < 400) return false
                        prevTapTime = now

                        onTap(v)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        onDragMove(v, event, initialX, initialY, initialTouchX, initialTouchY)
                    }
                }
                v.performClick()
                return false
            }
        }
    }

    private fun onTap(view: View) {
        val splitTime = chronometer.timeElapsed

        if (chronometer.isRunning) {
            if (splitTime < 0) return
            if (!hasSplits) {
                timerStop()
                return
            }
            segmentTimes += splitTime - currentSplitStartTime
            if (prefs.getBoolean(getString(R.string.key_pref_timer_show_delta), true)) {
                getCurrentSplit()?.let { updateDelta(it, splitTime) }
            }
            if (splitsIter?.hasNext() == true) {
                timerSplit()
            } else {
                timerStop()
            }
        } else {
            if (splitsIter == null) {
                timerStart()
            }
        }
        currentSplitStartTime = splitTime.coerceAtLeast(0L)
    }

    private fun onDragMove(
        view: View,
        event: MotionEvent,
        initialX: Int,
        initialY: Int,
        initialTouchX: Float,
        initialTouchY: Float
    ) {
        val (screenW, screenH) = getScreenSize()
        val dx = event.rawX - initialTouchX
        val dy = event.rawY - initialTouchY
        val targetX = (initialX - dx.toInt()).coerceIn(0, screenW - view.width)
        val targetY = (initialY - dy.toInt()).coerceIn(0, screenH - view.height)

        val distSq = (targetX - initialX).toDouble().let { it * it } +
                (targetY - initialY).toDouble().let { it * it }
        if (!moved && distSq < 30 * 30) return

        moved = true
        mWindowParams.x = targetX
        mWindowParams.y = targetY
        mWindowManager.updateViewLayout(mBinding.root, mWindowParams)
    }

    private fun onLongClick(v: View): Boolean {
        if (moved) return false

        val time = chronometer.timeElapsed
        val saveData = prefs.getBoolean(getString(R.string.key_pref_save_time_data), true)

        when (decideResetAction(time, saveData)) {
            ResetAction.RESET_NO_SAVE -> timerReset(updateData = false)
            ResetAction.RESET_WITH_SAVE -> timerReset(updateData = saveData)
            ResetAction.RESET_WITH_NEW_PB -> timerReset(newPB = time, updateData = saveData)
            ResetAction.SHOW_PB_DIALOG -> showNewPersonalBestDialog(time, saveData)
        }
        return true
    }

    private enum class ResetAction {
        RESET_NO_SAVE, RESET_WITH_SAVE, RESET_WITH_NEW_PB, SHOW_PB_DIALOG
    }

    private fun decideResetAction(time: Long, saveData: Boolean): ResetAction {
        return when {
            time <= 0 -> ResetAction.RESET_NO_SAVE
            chronometer.isRunning || (category.bestTime > 0 && time >= category.bestTime) ->
                ResetAction.RESET_WITH_SAVE
            category.bestTime == 0L -> ResetAction.RESET_WITH_NEW_PB
            !saveData -> ResetAction.RESET_NO_SAVE
            else -> ResetAction.SHOW_PB_DIALOG
        }
    }

    private fun showNewPersonalBestDialog(time: Long, saveData: Boolean) {
        MaterialDialog(this).show {
            title(text = "New personal best!")
            val format = "%-16s%s"
            message(
                text = format.format("Previous PB:", category.bestTime.getFormattedTime()) +
                        "\n" + format.format("Improvement:", (time - category.bestTime).getFormattedTime())
            )
            checkBoxPrompt(
                text = if (hasSplits) "Save splits" else "Save",
                isCheckedDefault = true
            ) {}
            positiveButton(R.string.reset) {
                timerReset(newPB = time, updateData = isCheckPromptChecked())
            }
            negativeButton(android.R.string.cancel)
            @Suppress("DEPRECATION")
            window?.setType(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            )
        }
    }

    // ========================================================================
    // Window management
    // ========================================================================

    private fun addOverlayToWindow() {
        mWindowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
        }

        mWindowManager.addView(mBinding.root, mWindowParams)
        overlayAdded = true
        resetTimerPosition()
    }

    /**
     * Returns screen dimensions using modern WindowMetrics API (API 30+)
     * to avoid deprecated Display APIs that crash on Android 15.
     */
    private fun getScreenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = mWindowManager.maximumWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            mWindowManager.defaultDisplay.getMetrics(metrics)
            Pair(metrics.widthPixels, metrics.heightPixels)
        }
    }

    private fun resetTimerPosition() {
        val (savedX, savedY) = category.getGame().getPosition()
        val (screenW, screenH) = getScreenSize()
        val viewW = mBinding.root.width.takeIf { it > 0 } ?: mBinding.root.measuredWidth
        val viewH = mBinding.root.height.takeIf { it > 0 } ?: mBinding.root.measuredHeight

        mWindowParams.x = savedX.coerceIn(0, screenW - viewW)
        mWindowParams.y = savedY.coerceIn(0, screenH - viewH)
        mWindowManager.updateViewLayout(mBinding.root, mWindowParams)
    }

    // ========================================================================
    // Timer state machine
    // ========================================================================

    private fun timerStart() {
        if (hasSplits) {
            splitsIter = category.splits.listIterator()
            timerSplit()
            chronometer.showSplit(
                prefs.getBoolean(getString(R.string.key_pref_timer_show_current_split), true)
            )
        } else {
            chronometer.compareAgainst = category.bestTime
        }
        chronometer.start()
    }

    private fun timerSplit() {
        splitsIter?.next()?.let { split ->
            chronometer.compareAgainst = if (split.hasTime(comparison)) {
                split.calculateSplitTime(comparison)
            } else 0L
            chronometer.setSplitText(split.name)
        }
    }

    private fun timerStop() {
        chronometer.stop()
        chronometer.showSplit(false)
    }

    private fun timerReset(newPB: Long = 0L, updateData: Boolean = true) {
        chronometer.reset()
        if (updateData) {
            if (newPB == 0L) {
                category.incrementRunCount()
                category.updateSplits(segmentTimes, isNewPB = false)
            } else {
                category.updateData(bestTime = newPB, runCount = category.runCount + 1)
                category.updateSplits(segmentTimes, isNewPB = true)
                FSTWidget.forceUpdateWidgets(this)
            }
        }
        clearSplits()
        resetTimerPosition()
    }

    private fun clearSplits() {
        segmentTimes.clear()
        splitsIter = null
        chronometer.showDelta(false)
        chronometer.showSplit(false)
    }

    private fun getCurrentSplit(): Split? {
        splitsIter?.previous()
        return splitsIter?.next()
    }

    private fun updateDelta(currentSplit: Split, splitTime: Long) {
        val segmentTime = splitTime - currentSplitStartTime
        val delta = splitTime - currentSplit.calculateSplitTime(comparison)

        chronometer.setDeltaText(delta.getFormattedTime(plusSign = true))
        chronometer.setDeltaColor(
            when {
                segmentTime < currentSplit.bestTime -> chronometer.config.colorBestSegment
                delta < 0 -> chronometer.config.colorAhead
                else -> chronometer.config.colorBehind
            }
        )
        chronometer.showDelta(currentSplit.hasTime(comparison))
    }

    // ========================================================================
    // Companion object / launcher
    // ========================================================================

    companion object {

        var IS_ACTIVE = false
        var gameName = ""
        var categoryName = ""

        private val scope = CoroutineScope(Dispatchers.Main)

        fun launchTimer(
            context: Context?,
            gameName: String,
            categoryName: String,
            minimizeIfNoGameLaunch: Boolean = true
        ) = scope.launch {
            context ?: return@launch

            if (IS_ACTIVE) {
                context.showToast(context.getString(R.string.toast_close_active_timer))
                return@launch
            }

            if (!context.tryLaunchGame(gameName)) {
                if (minimizeIfNoGameLaunch) context.minimizeApp()
            }
            context.startTimerService(gameName, categoryName)
        }
    }
}
