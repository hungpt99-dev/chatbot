package com.helpdesk.domain.engine;

/**
 * Provider boundary for the LLM that interprets an employee's message and decides
 * the next SOP step. Phase 1C ships an OpenAI-compatible REST implementation
 * ({@code OpenAiCompatibleLlmPort}); the boundary is provider-agnostic so a
 * different vendor can be dropped in without touching the execution layer.
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #isConfigured()} returns false when no provider key is set, so the
 *       caller falls back to the deterministic {@link OfflineInterpreter} (off-mode).</li>
 *   <li>{@link #decide(ConversationSnapshot, String)} returns a structured
 *       {@link LlmStepDecision}; the app validates it before the engine applies it.</li>
 *   <li>The port never throws on a transient provider error in normal flow — it
 *       should return null (or a CONTINUE default) so the caller can degrade safely.</li>
 * </ul>
 */
public interface LlmPort {
    boolean isConfigured();

    LlmStepDecision decide(ConversationSnapshot snapshot, String userMessage);
}
