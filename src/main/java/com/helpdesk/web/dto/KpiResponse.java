package com.helpdesk.web.dto;

/**
 * Aggregated KPI snapshot for a hotel (or globally when {@code hotelId} is
 * absent). Resolution rate is the AI-handled share of all closed conversations;
 * {@code avgLatencyMs} is the live per-message latency from the telemetry timers.
 */
public record KpiResponse(double resolutionRate, long totalConversations, long escalated, double avgLatencyMs) {
}
