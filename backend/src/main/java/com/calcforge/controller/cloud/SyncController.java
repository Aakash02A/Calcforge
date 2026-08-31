package com.calcforge.controller.cloud;

import com.calcforge.dto.request.SyncPullRequest;
import com.calcforge.dto.request.SyncPushRequest;
import com.calcforge.dto.response.SyncResultResponse;
import com.calcforge.security.SecurityUtils;
import com.calcforge.service.SyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cloud/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    @PostMapping("/push")
    public SyncResultResponse push(@Valid @RequestBody SyncPushRequest request) {
        return syncService.push(SecurityUtils.requireCurrentUserId(), request);
    }

    @PostMapping("/pull")
    public SyncResultResponse pull(@Valid @RequestBody SyncPullRequest request) {
        return syncService.pull(SecurityUtils.requireCurrentUserId(), request);
    }
}
