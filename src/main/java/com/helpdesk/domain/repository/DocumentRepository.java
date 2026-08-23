package com.helpdesk.domain.repository;

import com.helpdesk.domain.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Hotel-scoped access for uploaded document metadata. */
@Repository
public interface DocumentRepository extends JpaRepository<Document, String> {

    List<Document> findByHotelId(String hotelId);

    List<Document> findByHotelIdAndId(String hotelId, String id);
}
