package com.helpdesk.domain.repository;

import com.helpdesk.domain.model.SopEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the in-process vector store ({@link SopEmbedding}). All reads are
 * scoped by {@code hotel_id} so retrieval stays tenant-isolated.
 */
@Repository
public interface SopEmbeddingRepository extends JpaRepository<SopEmbedding, Long> {

    Optional<SopEmbedding> findByHotelIdAndSopId(String hotelId, String sopId);

    List<SopEmbedding> findByHotelId(String hotelId);
}
