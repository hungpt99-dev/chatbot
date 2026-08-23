package com.helpdesk.application;

import com.helpdesk.domain.model.Hotel;
import com.helpdesk.domain.repository.ConversationRepository;
import com.helpdesk.domain.repository.HotelRepository;
import com.helpdesk.domain.repository.SopRepository;
import com.helpdesk.web.DuplicateHotelException;
import com.helpdesk.web.HotelInUseException;
import com.helpdesk.web.HotelNotFoundException;
import com.helpdesk.web.dto.HotelAdminRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Hotel (property) directory for the chain. Admin CRUD for the multi-tenant model. */
@Service
public class HotelService {

    private final HotelRepository hotelRepository;
    private final SopRepository sopRepository;
    private final ConversationRepository conversationRepository;

    public HotelService(HotelRepository hotelRepository,
                        SopRepository sopRepository,
                        ConversationRepository conversationRepository) {
        this.hotelRepository = hotelRepository;
        this.sopRepository = sopRepository;
        this.conversationRepository = conversationRepository;
    }

    public List<Hotel> list() {
        return hotelRepository.findAllByOrderByIdAsc();
    }

    public Optional<Hotel> get(String id) {
        return hotelRepository.findById(id);
    }

    @Transactional
    public Hotel create(HotelAdminRequest req) {
        if (hotelRepository.existsById(req.id())) {
            throw new DuplicateHotelException(req.id());
        }
        Hotel hotel = new Hotel(req.id(), req.name(), req.location());
        hotel.setRegion(req.region());
        return hotelRepository.save(hotel);
    }

    @Transactional
    public Hotel update(String id, HotelAdminRequest req) {
        Hotel hotel = hotelRepository.findById(id)
                .orElseThrow(() -> new HotelNotFoundException(id));
        hotel.setName(req.name());
        if (req.location() != null) {
            hotel.setLocation(req.location());
        }
        if (req.region() != null) {
            hotel.setRegion(req.region());
        }
        return hotelRepository.save(hotel);
    }

    @Transactional
    public void delete(String id) {
        if (hotelRepository.findById(id).isEmpty()) {
            throw new HotelNotFoundException(id);
        }
        boolean inUse = sopRepository.countByHotelId(id) > 0
                || conversationRepository.countByHotelId(id) > 0;
        if (inUse) {
            throw new HotelInUseException(id);
        }
        hotelRepository.deleteById(id);
    }
}
