package com.calcforge.controller.local;

import com.calcforge.dto.request.WorkspaceRequest;
import com.calcforge.dto.response.WorkspaceResponse;
import com.calcforge.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/local/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @GetMapping
    public List<WorkspaceResponse> list() {
        return workspaceService.listLocal();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse create(@Valid @RequestBody WorkspaceRequest request) {
        return workspaceService.create(request, null);
    }

    @GetMapping("/{id}")
    public WorkspaceResponse get(@PathVariable Long id) {
        return workspaceService.get(id);
    }

    @PutMapping("/{id}")
    public WorkspaceResponse update(@PathVariable Long id, @Valid @RequestBody WorkspaceRequest request) {
        return workspaceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        workspaceService.delete(id);
    }
}
