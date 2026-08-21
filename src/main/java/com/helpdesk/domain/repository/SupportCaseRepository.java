package com.helpdesk.domain.repository;

import com.helpdesk.domain.model.ConversationStatus;
import com.helpdesk.domain.model.SupportCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupportCaseRepository extends JpaRepository<SupportCase, Long> {

    List<SupportCase> findByStatusOrderByStartedAtDesc(ConversationStatus status);

    List<SupportCase> findAllByOrderByStartedAtDesc();

    SupportCase findByReference(String reference);

    SupportCase findByConversationId(Long conversationId);
}
