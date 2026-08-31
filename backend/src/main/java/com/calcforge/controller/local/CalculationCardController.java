package com.calcforge.controller.local;

import com.calcforge.dto.request.CalculationCardRequest;
import com.calcforge.dto.response.CalculationCardResponse;
import com.calcforge.service.CalculationCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/local/workspaces/{workspaceId}/calculations")
@RequiredArgsConstructor
public class CalculationCardController {

    private final CalculationCardService calculationCardService;

    @GetMapping
    public List<CalculationCardResponse> list(@PathVariable Long workspaceId) {
        return calculationCardService.list(workspaceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CalculationCardResponse create(@PathVariable Long workspaceId,
                                           @Valid @RequestBody CalculationCardRequest request) {
        return calculationCardService.create(workspaceId, request);
    }

    @GetMapping("/{cardId}")
    public CalculationCardResponse get(@PathVariable Long workspaceId, @PathVariable Long cardId) {
        return calculationCardService.get(cardId);
    }

    @PutMapping("/{cardId}")
    public CalculationCardResponse update(@PathVariable Long workspaceId, @PathVariable Long cardId,
                                           @Valid @RequestBody CalculationCardRequest request) {
        return calculationCardService.update(cardId, request);
    }

    @DeleteMapping("/{cardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long workspaceId, @PathVariable Long cardId) {
        calculationCardService.delete(cardId);
    }

    /** Body: {"cardIds": [3, 1, 2]} in the new desired order. */
    @PostMapping("/reorder")
    public void reorder(@PathVariable Long workspaceId, @RequestBody Map<String, List<Long>> body) {
        calculationCardService.reorder(workspaceId, body.getOrDefault("cardIds", List.of()));
    }
}
