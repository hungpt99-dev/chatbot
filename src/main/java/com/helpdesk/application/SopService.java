package com.helpdesk.application;

import com.helpdesk.domain.model.Sop;
import com.helpdesk.domain.model.SopAssembler;
import com.helpdesk.domain.repository.SopRepository;
import com.helpdesk.domain.retrieval.LexicalSopRetriever;
import com.helpdesk.domain.retrieval.RetrievalResult;
import com.helpdesk.web.SopNotFoundException;
import com.helpdesk.web.dto.SopRequest;
import com.helpdesk.web.dto.SopResponse;
import com.helpdesk.web.dto.SopSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SOP use cases (CRUD + lexical retrieval). Controllers depend on this; business
 * rules stay here, not in the web layer (FinPay convention: no logic in controllers).
 */
@Service
public class SopService {

    private final SopRepository sopRepository;
    private final LexicalSopRetriever retriever;

    public SopService(SopRepository sopRepository, LexicalSopRetriever retriever) {
        this.sopRepository = sopRepository;
        this.retriever = retriever;
    }

    public List<SopSummary> list(String category) {
        List<Sop> all = (category == null || category.isBlank())
                ? sopRepository.findAll()
                : sopRepository.findByCategory(category);
        return all.stream()
                .map(s -> new SopSummary(s.getId(), s.getTitle(), s.getCategory(), s.getDescription()))
                .toList();
    }

    public SopResponse get(String id) {
        return SopResponse.from(load(id));
    }

    @Transactional
    public SopResponse create(SopRequest req) {
        if (sopRepository.existsById(req.id())) {
            throw new com.helpdesk.web.DuplicateSopException(req.id());
        }
        Sop saved = sopRepository.save(SopAssembler.toEntity(req));
        return SopResponse.from(saved);
    }

    @Transactional
    public SopResponse update(String id, SopRequest req) {
        Sop existing = load(id); // throws if missing
        SopAssembler.apply(req, existing);
        existing.setVersion(existing.getVersion() + 1);
        Sop saved = sopRepository.save(existing);
        return SopResponse.from(saved);
    }

    public RetrievalResult retrieve(String problemText) {
        return retriever.retrieve(problemText);
    }

    public Sop load(String id) {
        return sopRepository.findById(id)
                .orElseThrow(() -> new SopNotFoundException(id));
    }
}
