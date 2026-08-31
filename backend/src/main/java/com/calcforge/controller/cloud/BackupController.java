package com.calcforge.controller.cloud;

import com.calcforge.dto.response.SyncResultResponse;
import com.calcforge.security.SecurityUtils;
import com.calcforge.service.SyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cloud/backup")
@RequiredArgsConstructor
public class BackupController {

    private final SyncService syncService;

    @GetMapping("/export")
    public SyncResultResponse export() {
        return syncService.exportAll(SecurityUtils.requireCurrentUserId());
    }

    @PostMapping("/restore")
    public SyncResultResponse restore(@Valid @RequestBody SyncResultResponse backup) {
        return syncService.restoreFromBackup(SecurityUtils.requireCurrentUserId(), backup);
    }
}
