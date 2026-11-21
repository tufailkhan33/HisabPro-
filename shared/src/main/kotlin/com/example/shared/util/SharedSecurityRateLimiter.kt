package com.example.shared.util

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

class SharedSecurityRateLimiter(
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    enum class Operation(
        val maxFailures: Int,
        val windowMillis: Long,
        val lockoutMillis: Long,
        val label: String
    ) {
        LOGIN(
            maxFailures = 5,
            windowMillis = 5 * 60 * 1000L,
            lockoutMillis = 2 * 60 * 1000L,
            label = "login"
        ),
        PASSWORD_RESET(
            maxFailures = 4,
            windowMillis = 10 * 60 * 1000L,
            lockoutMillis = 3 * 60 * 1000L,
            label = "password reset"
        ),
        PASSWORD_CHANGE(
            maxFailures = 5,
            windowMillis = 5 * 60 * 1000L,
            lockoutMillis = 2 * 60 * 1000L,
            label = "password change"
        )
    }

    private data class Bucket(
        val failureTimestamps: MutableList<Long> = mutableListOf(),
        var lockedUntilMillis: Long = 0L
    )

    private val buckets = ConcurrentHashMap<String, Bucket>()

    private fun bucketKey(operation: Operation, identifier: String): String =
        "${operation.name}:${identifier.trim().lowercase()}"

    fun checkAllowed(operation: Operation, identifier: String) {
        val key = bucketKey(operation, identifier)
        val bucket = buckets[key] ?: return
        val now = clockMillis()
        synchronized(bucket) {
            if (bucket.lockedUntilMillis > now) {
                val remainingSec = ceil((bucket.lockedUntilMillis - now) / 1000.0).toLong().coerceAtLeast(1L)
                throw IllegalStateException(
                    "Too many failed ${operation.label} attempts. Please wait $remainingSec seconds before trying again."
                )
            }
            bucket.failureTimestamps.removeAll { now - it > operation.windowMillis }
            if (bucket.lockedUntilMillis in 1L..now) {
                bucket.lockedUntilMillis = 0L
            }
        }
    }

    fun recordFailure(operation: Operation, identifier: String) {
        val key = bucketKey(operation, identifier)
        val bucket = buckets.computeIfAbsent(key) { Bucket() }
        val now = clockMillis()
        synchronized(bucket) {
            bucket.failureTimestamps.removeAll { now - it > operation.windowMillis }
            bucket.failureTimestamps.add(now)
            if (bucket.failureTimestamps.size >= operation.maxFailures) {
                bucket.lockedUntilMillis = now + operation.lockoutMillis
                bucket.failureTimestamps.clear()
            }
        }
    }

    fun recordSuccess(operation: Operation, identifier: String) {
        buckets.remove(bucketKey(operation, identifier))
    }

    fun remainingAttempts(operation: Operation, identifier: String): Int {
        val key = bucketKey(operation, identifier)
        val bucket = buckets[key] ?: return operation.maxFailures
        val now = clockMillis()
        synchronized(bucket) {
            if (bucket.lockedUntilMillis > now) return 0
            bucket.failureTimestamps.removeAll { now - it > operation.windowMillis }
            return (operation.maxFailures - bucket.failureTimestamps.size).coerceAtLeast(0)
        }
    }

    fun resetAll() {
        buckets.clear()
    }
}
