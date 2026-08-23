package com.helpdesk.domain.repository;

import com.helpdesk.domain.model.Conversation;
import com.helpdesk.domain.model.ConversationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    long countByHotelId(String hotelId);

    long countByHotelIdAndStatus(String hotelId, ConversationStatus status);

    long countByStatus(ConversationStatus status);
}
