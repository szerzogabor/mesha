package com.mesha.api.model;

public enum AutomationTriggerType {
    PR_OPENED,
    PR_MERGED,
    PR_CLOSED,
    BLOCKS_SESSION_STARTED,
    BLOCKS_SESSION_COMPLETED,
    BLOCKS_SESSION_FAILED,
    /** Fires when an issue's status is updated to the value stored in triggerValue. */
    STATUS_UPDATED,
    /** Fires when a label (identified by triggerValue = label UUID) is added to an issue. */
    LABEL_ADDED,
    /** Fires when the AI session ends because the token/context limit was reached. */
    AI_TOKEN_LIMIT_HIT
}
