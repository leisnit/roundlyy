package app.roundly.core.model

import kotlinx.serialization.Serializable

/**
 * One alert occurrence and how it ended. [occurrenceId] (alarmId@scheduledAt) is shared with
 * the watch, so the first device to respond wins. Entries are kept for 30 days.
 */
@Serializable
data class AlertLog(
    val id: Long = 0,
    val alarmId: Long,
    val occurrenceId: String,
    /** Label at the time of the alert, so history and "Missed" notices survive later edits. */
    val label: String,
    val scheduledAt: Long,
    val outcome: AlertOutcome,
    val respondedAt: Long? = null,
    val respondedOn: RespondedOn? = null,
) {
    /**
     * Phone/watch reconciliation: should [incoming] replace this entry for the same occurrence?
     * - DONE is final: the first device acknowledged wins.
     * - MISSED (rang out on one device) is replaced by a real answer from the other device.
     * - SNOOZED can still become DONE or MISSED; an identical repeat is ignored.
     */
    fun acceptsUpdate(incoming: AlertLog): Boolean = when (outcome) {
        AlertOutcome.DONE -> false
        AlertOutcome.MISSED -> incoming.outcome != AlertOutcome.MISSED
        AlertOutcome.SNOOZED -> !(incoming.outcome == AlertOutcome.SNOOZED && incoming.respondedOn == respondedOn)
    }
}
