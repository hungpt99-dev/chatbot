package com.helpdesk.application;

import com.helpdesk.domain.model.ConversationStatus;
import com.helpdesk.domain.repository.ConversationRepository;
import com.helpdesk.web.dto.KpiResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Aggregates operational KPIs for the AI IT Support Assistant. Resolution rate
 * and counts are derived from the authoritative {@link ConversationRepository}
 * (the conversation is the source of truth for its own terminal status); latency
 * is read from the live telemetry timers recorded by {@code ConversationService}.
 *
 * <p>Tenant-scoped: when {@code hotelId} is supplied the figures cover only that
 * hotel, otherwise they aggregate across all hotels.
 */
@Service
public class KpiService {

    private final ConversationRepository conversationRepository;
    private final MeterRegistry meterRegistry;

    public KpiService(ConversationRepository conversationRepository, MeterRegistry meterRegistry) {
        this.conversationRepository = conversationRepository;
        this.meterRegistry = meterRegistry;
    }

    public KpiResponse compute(String hotelId) {
        boolean scoped = hotelId != null && !hotelId.isBlank();
        long totalConversations = scoped
                ? conversationRepository.countByHotelId(hotelId)
                : conversationRepository.count();
        long resolved = scoped
                ? conversationRepository.countByHotelIdAndStatus(hotelId, ConversationStatus.RESOLVED)
                : conversationRepository.countByStatus(ConversationStatus.RESOLVED);
        long escalated = scoped
                ? conversationRepository.countByHotelIdAndStatus(hotelId, ConversationStatus.ESCALATED)
                : conversationRepository.countByStatus(ConversationStatus.ESCALATED);

        long closed = resolved + escalated;
        double resolutionRate = closed > 0 ? (double) resolved / closed : 0.0;
        double avgLatencyMs = averageLatencyMs(hotelId);

        return new KpiResponse(resolutionRate, totalConversations, escalated, avgLatencyMs);
    }

    private double averageLatencyMs(String hotelId) {
        var timer = (hotelId == null || hotelId.isBlank())
                ? meterRegistry.find("conversation.message.latency").timer()
                : meterRegistry.find("conversation.message.latency").tag("hotel", hotelId).timer();
        if (timer == null) {
            return 0.0;
        }
        return timer.mean(TimeUnit.MILLISECONDS);
    }
}
