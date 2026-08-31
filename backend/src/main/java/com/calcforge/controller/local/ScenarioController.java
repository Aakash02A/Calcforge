package com.calcforge.controller.local;

import com.calcforge.dto.request.ScenarioRequest;
import com.calcforge.dto.request.ScenarioRunRequest;
import com.calcforge.dto.response.ScenarioResponse;
import com.calcforge.dto.response.ScenarioRunResultDto;
import com.calcforge.service.ScenarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/local/workspaces/{workspaceId}/scenarios")
@RequiredArgsConstructor
public class ScenarioController {

    private final ScenarioService scenarioService;

    @GetMapping
    public List<ScenarioResponse> list(@PathVariable Long workspaceId) {
        return scenarioService.list(workspaceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScenarioResponse create(@PathVariable Long workspaceId, @Valid @RequestBody ScenarioRequest request) {
        return scenarioService.create(workspaceId, request);
    }

    @GetMapping("/{scenarioId}")
    public ScenarioResponse get(@PathVariable Long workspaceId, @PathVariable Long scenarioId) {
        return scenarioService.get(scenarioId);
    }

    @PutMapping("/{scenarioId}")
    public ScenarioResponse update(@PathVariable Long workspaceId, @PathVariable Long scenarioId,
                                    @Valid @RequestBody ScenarioRequest request) {
        return scenarioService.update(scenarioId, request);
    }

    @DeleteMapping("/{scenarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long workspaceId, @PathVariable Long scenarioId) {
        scenarioService.delete(scenarioId);
    }

    @PostMapping("/run")
    public List<ScenarioRunResultDto> run(@PathVariable Long workspaceId, @Valid @RequestBody ScenarioRunRequest request) {
        return scenarioService.run(workspaceId, request);
    }
}
