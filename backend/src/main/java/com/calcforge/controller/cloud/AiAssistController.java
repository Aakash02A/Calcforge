package com.calcforge.controller.cloud;

import com.calcforge.dto.request.AiAssistRequest;
import com.calcforge.dto.response.AiAssistResponse;
import com.calcforge.service.AiAssistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cloud/ai")
@RequiredArgsConstructor
public class AiAssistController {

    private final AiAssistService aiAssistService;

    @PostMapping("/ask")
    public AiAssistResponse ask(@Valid @RequestBody AiAssistRequest request) {
        return aiAssistService.ask(request);
    }
}
