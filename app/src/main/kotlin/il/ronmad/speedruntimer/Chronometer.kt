package il.ronmad.speedruntimer

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.View
import il.ronmad.speedruntimer.databinding.TimerOverlayBinding
import java.lang.ref.WeakReference

/**
 * Custom chronometer with per-digit display, color comparison, and countdown support.
 * Uses a Handler-based tick loop at 15ms intervals for smooth millisecond display.
 */
class Chronometer(
    private val binding: TimerOverlayBinding,
    val config: Config
) {

    /**
     * Configuration container — replaces the old companion object mutable state.
     */
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

    // === State ===
    var timeElapsed: Long = 0
        private set

    var compareAgainst: Long = 0L

    var isRunning: Boolean = false
        private set

    var isStarted: Boolean = false
        private set

    // === Internal ===
    private var base: Long = 0L
    private var currentColor: Int = config.colorNeutral
    private val chronoHandler: Handler

    init {
        if (!config.showMillis) {
            binding.apply {
                chronoMilli1.visibility = View.GONE
                chronoMilli2.visibility = View.GONE
                chronoDot.visibility = View.GONE
            }
        }
        binding.chronoViewSet.forEach { it.setTextColor(config.colorNeutral) }
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

    fun reset(newPB: Long = 0L) {
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
        binding.apply {
            chronoHr2.text = (hours / 10).toString()
            chronoHr1.text = (hours % 10).toString()
            chronoMin2.text = (minutes / 10).toString()
            chronoMin1.text = (minutes % 10).toString()
            chronoSec2.text = (seconds / 10).toString()
            chronoSec1.text = (seconds % 10).toString()
            chronoMilli2.text = (millis / 10).toString()
            chronoMilli1.text = (millis % 10).toString()
        }
    }

    private fun updateVisibility() {
        val (hours, minutes, seconds, _) = timeElapsed.getTimeUnits(twoDecimalPlaces = true)
        binding.apply {
            chronoMinus.visibility = if (timeElapsed < 0) View.VISIBLE else View.GONE
            when {
                hours > 0 -> {
                    chronoHr2.visibility = if (hours / 10 > 0) View.VISIBLE else View.GONE
                    chronoHr1.visibility = View.VISIBLE
                    chronoHrMinColon.visibility = View.VISIBLE
                    chronoMin2.visibility = View.VISIBLE
                    chronoMin1.visibility = View.VISIBLE
                    chronoMinSecColon.visibility = View.VISIBLE
                    chronoSec2.visibility = View.VISIBLE
                }
                minutes > 0 -> {
                    chronoHr2.visibility = View.GONE
                    chronoHr1.visibility = View.GONE
                    chronoHrMinColon.visibility = View.GONE
                    chronoMin2.visibility = if (minutes / 10 > 0) View.VISIBLE else View.GONE
                    chronoMin1.visibility = View.VISIBLE
                    chronoMinSecColon.visibility = View.VISIBLE
                    chronoSec2.visibility = View.VISIBLE
                }
                else -> {
                    chronoHr2.visibility = View.GONE
                    chronoHr1.visibility = View.GONE
                    chronoHrMinColon.visibility = View.GONE
                    chronoMin2.visibility = View.GONE
                    chronoMin1.visibility = if (config.alwaysMinutes) View.VISIBLE else View.GONE
                    chronoMinSecColon.visibility = if (config.alwaysMinutes) View.VISIBLE else View.GONE
                    chronoSec2.visibility = if (config.alwaysMinutes || seconds / 10 > 0) View.VISIBLE else View.GONE
                }
            }
        }
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
            binding.chronoViewSet.forEach { it.setTextColor(value) }
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

    /**
     * Handler for periodic timer ticks. Uses WeakReference to avoid leaks.
     */
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
