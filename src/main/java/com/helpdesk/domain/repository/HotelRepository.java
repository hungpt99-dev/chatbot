package com.helpdesk.domain.repository;

import com.helpdesk.domain.model.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HotelRepository extends JpaRepository<Hotel, String> {
    List<Hotel> findAllByOrderByIdAsc();
}
