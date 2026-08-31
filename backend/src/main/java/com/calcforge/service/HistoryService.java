package com.calcforge.service;

import com.calcforge.domain.HistoryEntry;
import com.calcforge.dto.request.HistoryUpdateRequest;
import com.calcforge.dto.response.HistoryEntryResponse;
import com.calcforge.dto.response.PageResponse;
import com.calcforge.exception.ResourceNotFoundException;
import com.calcforge.repository.HistoryEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** Persistent, searchable, editable calculation history. */
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final HistoryEntryRepository historyEntryRepository;
    private final CalculationService calculationService;

    public PageResponse<HistoryEntryResponse> search(Long userId, String query, String tag, int page, int pageSize) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(pageSize, 1), 200);
        Page<HistoryEntry> result = historyEntryRepository.search(
                userId,
                (query == null || query.isBlank()) ? null : query.trim(),
                (tag == null || tag.isBlank()) ? null : tag.trim(),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<HistoryEntryResponse> items = result.getContent().stream().map(this::toResponse).toList();
        return new PageResponse<>(items, safePage, safeSize, result.getTotalElements(), result.getTotalPages());
    }

    public HistoryEntryResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    public HistoryEntry getEntity(Long id) {
        return historyEntryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ResourceNotFoundException.of("History entry", id));
    }

    @Transactional
    public HistoryEntryResponse update(Long id, HistoryUpdateRequest request) {
        HistoryEntry entry = getEntity(id);
        if (request.tags() != null) {
            entry.setTags(request.tags());
        }
        if (request.favorite() != null) {
            entry.setFavorite(request.favorite());
        }
        return toResponse(historyEntryRepository.save(entry));
    }

    @Transactional
    public void delete(Long id) {
        HistoryEntry entry = getEntity(id);
        entry.setDeletedAt(Instant.now());
        historyEntryRepository.save(entry);
    }

    private HistoryEntryResponse toResponse(HistoryEntry entry) {
        List<String> tags = (entry.getTags() == null || entry.getTags().isBlank())
                ? List.of()
                : Arrays.stream(entry.getTags().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return new HistoryEntryResponse(entry.getId(), entry.getExpression(), entry.getResult(),
                calculationService.deserializeTrail(entry.getTrailJson()), tags, entry.isFavorite(), entry.getCreatedAt());
    }
}
