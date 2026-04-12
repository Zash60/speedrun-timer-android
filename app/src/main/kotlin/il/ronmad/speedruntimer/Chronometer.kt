package il.ronmad.speedruntimer

import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import il.ronmad.speedruntimer.databinding.TimerOverlayBinding
import java.lang.ref.WeakReference

/**
 * Modern chronometer using a single TextView with monospace font.
 * Displays time as "H:MM:SS.ss" with dynamic visibility for hours and sign.
 */
class Chronometer(
    private val binding: TimerOverlayBinding,
    val config: Config
) {

    data class Config(
        val colorNeutral: Int,
        val colorAhead: Int,
        val colorBehind: Int,
        val colorPB: Int,
        val colorBestSegment: Int,
        val countdown: Long = 0L,
        val showMillis: Boolean = true,
        val alwaysMinutes: Boolean = true,
    )

    var timeElapsed: Long = 0
        private set

    var compareAgainst: Long = 0L

    var isRunning: Boolean = false
        private set

    var isStarted: Boolean = false
        private set

    private var base: Long = 0L
    private var currentColor: Int = config.colorNeutral
    private val chronoHandler: Handler

    private val timeView: TextView get() = binding.timerTime
    private val minusView: TextView get() = binding.timerMinus
    private val deltaView: TextView get() = binding.timerDelta
    private val splitView: TextView get() = binding.timerSplit

    init {
        // Apply monospace typeface
        arrayOf(timeView, minusView, deltaView, splitView).forEach {
            it.typeface = Typeface.MONOSPACE
        }
        timeView.setTextColor(config.colorNeutral)
        chronoHandler = ChronoHandler(this)
        reset()
    }

    fun start() {
        isStarted = true
        isRunning = true
        base = SystemClock.elapsedRealtime() - timeElapsed
        updateRunning()
    }

    fun stop() {
        isRunning = false
        updateRunning()
        if (timeElapsed > 0 && (compareAgainst == 0L || timeElapsed < compareAgainst)) {
            timerColor = config.colorPB
        }
    }

    fun reset() {
        stop()
        isStarted = false
        timeElapsed = -config.countdown
        compareAgainst = 0L
        updateTime()
        updateVisibility()
        timerColor = config.colorNeutral
    }

    private fun update() {
        timeElapsed = SystemClock.elapsedRealtime() - base
        updateTime()
        updateVisibility()
        updateColor()
    }

    private fun updateTime() {
        val (hours, minutes, seconds, millis) = timeElapsed.getTimeUnits(twoDecimalPlaces = true)
        timeView.text = buildTimeString(hours, minutes, seconds, millis)
    }

    private fun buildTimeString(hours: Int, minutes: Int, seconds: Int, millis: Int): String {
        return when {
            hours > 0 -> "%d:%02d:%02d.%02d".format(hours, minutes, seconds, millis)
            minutes > 0 || config.alwaysMinutes -> "%d:%02d.%02d".format(minutes, seconds, millis)
            else -> "%d.%02d".format(seconds, millis)
        }
    }

    private fun updateVisibility() {
        minusView.visibility = if (timeElapsed < 0) View.VISIBLE else View.GONE
        splitView.visibility = View.GONE // managed externally
    }

    fun showSplit(show: Boolean) {
        splitView.visibility = if (show) View.VISIBLE else View.GONE
    }

    fun setSplitText(text: String) {
        splitView.text = text
    }

    fun showDelta(show: Boolean) {
        deltaView.visibility = if (show) View.VISIBLE else View.GONE
    }

    fun setDeltaText(text: String) {
        deltaView.text = text
    }

    fun setDeltaColor(color: Int) {
        deltaView.setTextColor(color)
    }

    private fun updateColor() {
        if (compareAgainst == 0L || timeElapsed < 0) {
            timerColor = config.colorNeutral
            return
        }
        timerColor = if (timeElapsed < compareAgainst) config.colorAhead else config.colorBehind
    }

    private var timerColor: Int
        get() = currentColor
        set(value) {
            if (value == currentColor) return
            timeView.setTextColor(value)
            minusView.setTextColor(value)
            currentColor = value
        }

    private fun updateRunning() {
        if (isRunning) {
            update()
            chronoHandler.sendMessageDelayed(
                Message.obtain(chronoHandler, TICK_WHAT),
                TICK_INTERVAL_MS
            )
        } else {
            chronoHandler.removeMessages(TICK_WHAT)
        }
    }

    private class ChronoHandler(instance: Chronometer) : Handler(Looper.getMainLooper()) {
        private val ref = WeakReference(instance)

        override fun handleMessage(msg: Message) {
            val chronometer = ref.get() ?: return
            if (chronometer.isRunning) {
                chronometer.update()
                sendMessageDelayed(Message.obtain(this, TICK_WHAT), TICK_INTERVAL_MS)
            }
        }
    }

    companion object {
        private const val TICK_WHAT = 2
        private const val TICK_INTERVAL_MS = 15L
    }
}
