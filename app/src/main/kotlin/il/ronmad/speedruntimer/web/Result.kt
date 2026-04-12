package il.ronmad.speedruntimer.web

/**
 * Result wrapper for operations that can succeed or fail.
 */
sealed interface Result<out T>
data class Success<out T>(val value: T) : Result<T>
data object Failure : Result<Nothing>
