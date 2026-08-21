package com.helpdesk.domain.model;

/**
 * How a terminal step concludes the flow.
 */
public enum TerminalKind {
    /** Issue resolved within the SOP. */
    RESOLVE,
    /** SOP exhausted; escalate to IT Support. */
    ESCALATE
}
