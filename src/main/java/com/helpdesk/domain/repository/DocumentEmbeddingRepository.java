package com.helpdesk.domain.repository;

import com.helpdesk.domain.model.DocumentEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the in-process document vector store ({@link DocumentEmbedding}).
 * All reads are scoped by {@code hotel_id} so retrieval stays tenant-isolated.
 */
@Repository
public interface DocumentEmbeddingRepository extends JpaRepository<DocumentEmbedding, Long> {

    Optional<DocumentEmbedding> findByHotelIdAndChunkId(String hotelId, String chunkId);

    List<DocumentEmbedding> findByHotelId(String hotelId);

    void deleteByHotelIdAndDocumentId(String hotelId, String documentId);
}
