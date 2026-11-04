package com.beamlink.peer.register

/**
 * Token poll outcomes for device enrollment.
 */
object EnrollmentPollRule {

    const val DEFAULT_INTERVAL_SECONDS = 15
    const val BACKOFF_CAP_SECONDS = 30
    const val SLOW_DOWN_CAP_SECONDS = 60

    enum class Action {
        STORE_TOKENS,
        KEEP_PENDING,
        SET_REJECTED_STOP,
        RETRY_CHALLENGE_ONCE,
        STOP,
    }

    data class Wait(
        val sleepSeconds: Int,
        val stickySeconds: Int,
        val failureCount: Int,
    )

    /**
     * Maps HTTP status and error body. Null status is a network exception.
     */
    fun onError(httpStatus: Int?, error: String?, grantRetried: Boolean): Action {
        if (httpStatus == null || httpStatus == 429 || httpStatus >= 500) {
            return Action.KEEP_PENDING
        }
        if (error == "authorization_pending" || error == "slow_down") {
            return Action.KEEP_PENDING
        }
        if (error == "access_denied") {
            return Action.SET_REJECTED_STOP
        }
        if (error == "invalid_grant" && !grantRetried) {
            return Action.RETRY_CHALLENGE_ONCE
        }
        return Action.STOP
    }

    /**
     * Next poll wait. Floor is base. slow_down adds 5 s, cap 60 s.
     * Network, 429, and 5xx use full jitter, cap 30 s. 200 resets.
     */
    fun nextWait(
        baseSeconds: Int,
        stickySeconds: Int,
        failureCount: Int,
        httpStatus: Int?,
        error: String?,
        random: (Int) -> Int,
    ): Wait {
        val base = baseSeconds.coerceAtLeast(1)
        val sticky = stickySeconds.coerceAtLeast(base)
        if (httpStatus == 200) {
            return Wait(base, base, 0)
        }
        if (error == "slow_down") {
            val next = (sticky + 5).coerceAtMost(SLOW_DOWN_CAP_SECONDS)
            return Wait(next, next, failureCount)
        }
        if (httpStatus == null
            || (httpStatus == 429 && error != "slow_down")
            || httpStatus >= 500
        ) {
            val nextFail = failureCount + 1
            val shift = (nextFail - 1).coerceAtMost(10)
            val exp = base * (1 shl shift)
            val cap = exp.coerceAtMost(BACKOFF_CAP_SECONDS)
            val sleep = random(cap + 1).coerceAtLeast(base)
            return Wait(sleep, sticky, nextFail)
        }
        return Wait(sticky, sticky, failureCount)
    }
}
