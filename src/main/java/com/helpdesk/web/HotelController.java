package com.helpdesk.web;

import com.helpdesk.application.HotelService;
import com.helpdesk.domain.model.Hotel;
import com.helpdesk.web.dto.HotelAdminRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.helpdesk.web.exception.HotelNotFoundException;
import com.helpdesk.web.exception.DuplicateHotelException;
import com.helpdesk.web.exception.HotelInUseException;

@RestController
@RequestMapping("/api/hotels")
public class HotelController {

    private final HotelService hotelService;

    public HotelController(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    @GetMapping
    public List<Hotel> list() {
        return hotelService.list();
    }

    @GetMapping("/{id}")
    public Hotel get(@PathVariable String id) {
        return hotelService.get(id).orElseThrow(() -> new HotelNotFoundException(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('IT_ADMIN')")
    public Hotel create(@Valid @RequestBody HotelAdminRequest req) {
        return hotelService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('IT_ADMIN')")
    public Hotel update(@PathVariable String id, @Valid @RequestBody HotelAdminRequest req) {
        return hotelService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('IT_ADMIN')")
    public void delete(@PathVariable String id) {
        hotelService.delete(id);
    }

    @ExceptionHandler(HotelNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<String> notFound(HotelNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(DuplicateHotelException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<String> duplicate(DuplicateHotelException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(HotelInUseException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ResponseEntity<String> inUse(HotelInUseException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ex.getMessage());
    }
}
