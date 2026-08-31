package com.calcforge.controller.local;

import com.calcforge.dto.request.VariableRequest;
import com.calcforge.dto.response.VariableResponse;
import com.calcforge.service.VariableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/local/workspaces/{workspaceId}/variables")
@RequiredArgsConstructor
public class VariableController {

    private final VariableService variableService;

    @GetMapping
    public List<VariableResponse> list(@PathVariable Long workspaceId) {
        return variableService.list(workspaceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VariableResponse create(@PathVariable Long workspaceId, @Valid @RequestBody VariableRequest request) {
        return variableService.create(workspaceId, request);
    }

    @GetMapping("/{variableId}")
    public VariableResponse get(@PathVariable Long workspaceId, @PathVariable Long variableId) {
        return variableService.get(variableId);
    }

    @PutMapping("/{variableId}")
    public VariableResponse update(@PathVariable Long workspaceId, @PathVariable Long variableId,
                                    @Valid @RequestBody VariableRequest request) {
        return variableService.update(variableId, request);
    }

    @DeleteMapping("/{variableId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long workspaceId, @PathVariable Long variableId) {
        variableService.delete(variableId);
    }
}
