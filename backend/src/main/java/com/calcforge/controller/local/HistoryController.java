package com.calcforge.controller.local;

import com.calcforge.dto.request.HistoryUpdateRequest;
import com.calcforge.dto.response.HistoryEntryResponse;
import com.calcforge.dto.response.PageResponse;
import com.calcforge.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/local/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    @GetMapping
    public PageResponse<HistoryEntryResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return historyService.search(null, q, tag, page, pageSize);
    }

    @GetMapping("/{id}")
    public HistoryEntryResponse get(@PathVariable Long id) {
        return historyService.get(id);
    }

    @PatchMapping("/{id}")
    public HistoryEntryResponse update(@PathVariable Long id, @RequestBody HistoryUpdateRequest request) {
        return historyService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        historyService.delete(id);
    }
}
