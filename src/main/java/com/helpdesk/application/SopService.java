package com.helpdesk.application;

import com.helpdesk.domain.model.Sop;
import com.helpdesk.domain.model.SopAssembler;
import com.helpdesk.domain.repository.SopRepository;
import com.helpdesk.domain.retrieval.LexicalOrVectorRetrievalStrategy;
import com.helpdesk.domain.retrieval.LexicalSopRetriever;
import com.helpdesk.domain.retrieval.RetrievalResult;
import com.helpdesk.web.exception.DuplicateSopException;
import com.helpdesk.web.exception.SopNotFoundException;
import com.helpdesk.web.dto.SopRequest;
import com.helpdesk.web.dto.SopResponse;
import com.helpdesk.web.dto.SopSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * SOP use cases (CRUD + lexical retrieval), scoped to a hotel (multi-tenant).
 * Controllers depend on this; business rules stay here, not in the web layer.
 */
@Service
public class SopService {

    private final SopRepository sopRepository;
    private final LexicalOrVectorRetrievalStrategy retriever;

    public SopService(SopRepository sopRepository, LexicalOrVectorRetrievalStrategy retriever) {
        this.sopRepository = sopRepository;
        this.retriever = retriever;
    }

    public List<SopSummary> list(String hotelId, String category) {
        List<Sop> all = (category == null || category.isBlank())
                ? sopRepository.findByHotelId(hotelId)
                : sopRepository.findByHotelIdAndCategory(hotelId, category);
        return all.stream()
                .map(s -> new SopSummary(s.getCode(), s.getTitle(), s.getCategory(), s.getDescription()))
                .toList();
    }

    public SopResponse get(String hotelId, String code) {
        return SopResponse.from(load(hotelId, code));
    }

    @Transactional
    public SopResponse create(String hotelId, SopRequest req) {
        if (sopRepository.existsByHotelIdAndCode(hotelId, req.id())) {
            throw new DuplicateSopException(hotelId + ":" + req.id());
        }
        Sop saved = sopRepository.save(SopAssembler.toEntity(req, hotelId));
        return SopResponse.from(saved);
    }

    @Transactional
    public SopResponse update(String hotelId, String code, SopRequest req) {
        Sop existing = load(hotelId, code); // throws if missing
        SopAssembler.apply(req, existing, hotelId);
        existing.setVersion(existing.getVersion() + 1);
        Sop saved = sopRepository.save(existing);
        return SopResponse.from(saved);
    }

    public RetrievalResult retrieve(String hotelId, String problemText) {
        return retriever.retrieve(hotelId, problemText);
    }

    public Sop load(String hotelId, String code) {
        return sopRepository.findByHotelIdAndCode(hotelId, code)
                .orElseThrow(() -> new SopNotFoundException(hotelId + ":" + code));
    }

    /** Load by composite id (used internally by conversation flow). */
    public Sop loadById(String id) {
        return sopRepository.findById(id)
                .orElseThrow(() -> new SopNotFoundException(id));
    }

    public Optional<Sop> findByHotelAndCode(String hotelId, String code) {
        return sopRepository.findByHotelIdAndCode(hotelId, code);
    }
}
