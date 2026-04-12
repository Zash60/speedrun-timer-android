package il.ronmad.speedruntimer.ui.util

/**
 * One-shot event wrapper. [consume] can only be called once,
 * preventing multiple observers from handling the same event.
 */
class OneShotEvent<out T>(private val data: T) {

    private var consumed = false

    fun consume(): T? = if (consumed) null else data.also { consumed = true }

    fun peek(): T = data
}
