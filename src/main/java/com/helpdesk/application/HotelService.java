package com.helpdesk.application;

import com.helpdesk.domain.model.Hotel;
import com.helpdesk.domain.repository.HotelRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** Hotel (property) directory for the chain. Read-only in Phase 1F; admin CRUD is a follow-on. */
@Service
public class HotelService {

    private final HotelRepository hotelRepository;

    public HotelService(HotelRepository hotelRepository) {
        this.hotelRepository = hotelRepository;
    }

    public List<Hotel> list() {
        return hotelRepository.findAllByOrderByIdAsc();
    }
}
