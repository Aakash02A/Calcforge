package com.calcforge.controller.local;

import com.calcforge.dto.request.GraphAnalyzeRequest;
import com.calcforge.dto.request.GraphRequest;
import com.calcforge.dto.response.GraphAnalyzeResponse;
import com.calcforge.dto.response.GraphResponse;
import com.calcforge.service.GraphService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    @PostMapping({"/api/v1/local/graph", "/api/graph"})
    public GraphResponse generate(@Valid @RequestBody GraphRequest request) {
        return graphService.generate(request);
    }

    @PostMapping({"/api/graph/analyze", "/api/v1/local/graph/analyze"})
    public GraphAnalyzeResponse analyze(@Valid @RequestBody GraphAnalyzeRequest request) {
        return graphService.analyze(request);
    }
}
