package com.helpdesk.domain.repository;

import com.helpdesk.domain.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
}
