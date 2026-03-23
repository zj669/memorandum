package com.memorandum.util

import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Singleton
class RetryHelper @Inject constructor() {

    suspend fun <T> retryWithBackoff(
        maxRetries: Int = 2,
        initialDelayMs: Long = 2000,
        factor: Double = 2.0,
        block: suspend () -> T,
    ): Result<T> {
        var lastException: Throwable? = null
        repeat(maxRetries + 1) { attempt ->
            try {
                return Result.success(block())
            } catch (e: SecurityException) {
                // Auth errors: never retry
                return Result.failure(e)
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    delay((initialDelayMs * factor.pow(attempt)).toLong())
                }
            }
        }
        return Result.failure(lastException ?: IllegalStateException("Retry exhausted"))
    }
}
