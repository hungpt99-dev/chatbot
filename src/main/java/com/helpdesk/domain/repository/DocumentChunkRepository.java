package com.helpdesk.domain.repository;

import com.helpdesk.domain.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Hotel-scoped access for the searchable document corpus (chunks). */
@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, String> {

    List<DocumentChunk> findByHotelId(String hotelId);

    List<DocumentChunk> findByHotelIdAndDocumentId(String hotelId, String documentId);

    void deleteByHotelIdAndDocumentId(String hotelId, String documentId);
}
