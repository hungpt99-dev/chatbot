package com.helpdesk.domain.repository;

import com.helpdesk.domain.model.Sop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SopRepository extends JpaRepository<Sop, String> {
    List<Sop> findByCategory(String category);
}
