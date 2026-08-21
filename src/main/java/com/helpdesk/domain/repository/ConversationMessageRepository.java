package com.helpdesk.domain.repository;

import com.helpdesk.domain.model.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    List<ConversationMessage> findByConversationIdOrderBySeqAsc(Long conversationId);

    @Query("SELECT COALESCE(MAX(m.seq), 0) FROM ConversationMessage m WHERE m.conversation.id = :cid")
    int maxSeq(@Param("cid") Long conversationId);
}
