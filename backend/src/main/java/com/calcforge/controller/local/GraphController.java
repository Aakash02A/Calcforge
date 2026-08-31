package com.calcforge.controller.local;

import com.calcforge.dto.request.GraphRequest;
import com.calcforge.dto.response.GraphResponse;
import com.calcforge.service.GraphService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/local/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    @PostMapping
    public GraphResponse generate(@Valid @RequestBody GraphRequest request) {
        return graphService.generate(request);
    }
}
