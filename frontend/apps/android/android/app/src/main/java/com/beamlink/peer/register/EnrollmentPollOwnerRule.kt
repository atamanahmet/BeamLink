package com.beamlink.peer.register

/**
 * Start and stop rules for the app-scoped enrollment poll.
 */
object EnrollmentPollOwnerRule {

    enum class Command {
        START,
        STOP,
        PAUSE,
        RESUME_NOW,
        NONE,
    }

    /**
     * Starts on PENDING, including a persisted PENDING after restart.
     */
    fun onPersistedState(state: String): Command {
        return when (state) {
            NexusPrefs.STATE_PENDING -> Command.START
            NexusPrefs.STATE_APPROVED,
            NexusPrefs.STATE_REJECTED,
            NexusPrefs.STATE_NOT_CONFIGURED,
            -> Command.STOP
            else -> Command.NONE
        }
    }

    /**
     * Pauses the poll when the app goes to the background while pending.
     */
    fun onBackground(state: String): Command {
        return if (state == NexusPrefs.STATE_PENDING) Command.PAUSE else Command.NONE
    }

    /**
     * Resumes with an immediate poll when the app returns while pending.
     */
    fun onForeground(state: String): Command {
        return if (state == NexusPrefs.STATE_PENDING) Command.RESUME_NOW else Command.NONE
    }
}
