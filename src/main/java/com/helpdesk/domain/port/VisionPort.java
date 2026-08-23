package com.helpdesk.domain.port;

/**
 * Provider boundary for vision / image analysis (the Screenshot feature, BRD §4).
 * The assistant asks the vision provider to describe an attached screenshot so the
 * textual description can be fed into the SOP execution engine / LLM context.
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #isConfigured()} returns false when no provider key is set; the
 *       caller should degrade gracefully (the message is still stored, the
 *       assistant simply has no image description to work with — off-mode).</li>
 *   <li>{@link #analyze(byte[], String, String)} returns a free-text description,
 *       or {@code null} on any transport/parse failure so the caller never fails
 *       the request because vision is unavailable.</li>
 * </ul>
 *
 * Implementations live in {@code com.helpdesk.infrastructure.vision} and are
 * provider-agnostic; the application/domain layers must not depend on a specific
 * vision vendor.
 */
public interface VisionPort {

    boolean isConfigured();

    String analyze(byte[] image, String contentType, String question);
}
