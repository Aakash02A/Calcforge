package com.calcforge.controller.cloud;

import com.calcforge.dto.response.SharedWorkspaceResponse;
import com.calcforge.dto.response.WorkspaceResponse;
import com.calcforge.security.SecurityUtils;
import com.calcforge.service.CalculationCardService;
import com.calcforge.service.FormulaService;
import com.calcforge.service.VariableService;
import com.calcforge.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class WorkspaceSharingController {

    private final WorkspaceService workspaceService;
    private final CalculationCardService calculationCardService;
    private final VariableService variableService;
    private final FormulaService formulaService;

    /** Owner-only: turns sharing on/off. Body: {"shared": true} */
    @PostMapping("/api/v1/cloud/workspaces/{id}/share")
    public WorkspaceResponse setShared(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean shared = Boolean.TRUE.equals(body.get("shared"));
        return workspaceService.setShared(id, shared, SecurityUtils.requireCurrentUserId());
    }

    /** Public, unauthenticated, read-only view of a workspace its owner has explicitly shared. */
    @GetMapping("/api/v1/cloud/shared/workspaces/{id}")
    public SharedWorkspaceResponse viewShared(@PathVariable Long id) {
        WorkspaceResponse workspace = workspaceService.getSharedOrThrow(id);
        return new SharedWorkspaceResponse(
                workspace,
                calculationCardService.list(id),
                variableService.list(id),
                formulaService.list(id));
    }
}
