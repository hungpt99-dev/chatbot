package com.helpdesk.domain.repository;

import com.helpdesk.domain.model.MessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, Long> {

    List<MessageAttachment> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
}
