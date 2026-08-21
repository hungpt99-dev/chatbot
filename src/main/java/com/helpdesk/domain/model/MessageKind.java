package com.helpdesk.domain.model;

/** Categorises a message so audit and UI can distinguish intent. */
public enum MessageKind {
    PROBLEM,    // initial employee problem statement
    QUESTION,   // assistant asking the user to do/check something or answer
    ANSWER,     // employee's reply
    RESULT,     // assistant reporting an interim result (e.g. step outcome)
    ESCALATION, // assistant announcing escalation to IT
    RESOLUTION, // assistant announcing resolution
    SYSTEM      // system/internal note
}
