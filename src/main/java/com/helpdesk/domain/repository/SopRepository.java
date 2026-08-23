package com.helpdesk.domain.repository;

import com.helpdesk.domain.model.Sop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SopRepository extends JpaRepository<Sop, String> {

    List<Sop> findByHotelId(String hotelId);

    List<Sop> findByHotelIdAndCategory(String hotelId, String category);

    Optional<Sop> findByHotelIdAndCode(String hotelId, String code);

    boolean existsByHotelIdAndCode(String hotelId, String code);

    long countByHotelId(String hotelId);
}
