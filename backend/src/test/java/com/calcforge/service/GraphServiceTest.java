package com.calcforge.service;

import com.calcforge.dto.request.GraphAnalyzeRequest;
import com.calcforge.dto.request.GraphRequest;
import com.calcforge.dto.response.GraphAnalyzeResponse;
import com.calcforge.dto.response.GraphResponse;
import com.calcforge.repository.HistoryEntryRepository;
import com.calcforge.repository.VariableRepository;
import com.calcforge.repository.WorkspaceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GraphServiceTest {

    @Mock
    private VariableRepository variableRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private HistoryEntryRepository historyEntryRepository;

    private CalculationService calculationService;
    private GraphService graphService;

    @BeforeEach
    void setUp() {
        calculationService = new CalculationService(
                historyEntryRepository,
                variableRepository,
                workspaceRepository,
                new ObjectMapper()
        );
        graphService = new GraphService(calculationService);
    }

    @Test
    @DisplayName("generate() produces uniform samples across min and max")
    void testStandardGenerate() {
        GraphRequest req = new GraphRequest(
                "sin(x)",
                "x",
                BigDecimal.valueOf(-10),
                BigDecimal.valueOf(10),
                50,
                Map.of(),
                null,
                "RADIANS"
        );

        GraphResponse res = graphService.generate(req);

        assertNotNull(res);
        assertEquals(50, res.points().size());
        assertEquals(0, BigDecimal.valueOf(-10).compareTo(res.points().get(0).x()));
        assertEquals(0, BigDecimal.valueOf(10).compareTo(res.points().get(49).x()));
    }

    @Test
    @DisplayName("analyze() adaptively injects 10x high density sample points across steep transitions")
    void testAdaptiveGraphScanSteepTransition() {
        GraphAnalyzeRequest req = new GraphAnalyzeRequest(
                "1 / x",
                "x",
                BigDecimal.valueOf(-2),
                null,
                BigDecimal.valueOf(2),
                null,
                Map.of(),
                null,
                "RADIANS",
                15,
                BigDecimal.valueOf(10.0),
                40,
                10
        );

        GraphAnalyzeResponse res = graphService.analyze(req);

        assertNotNull(res);
        assertTrue(res.totalPoints() > 40, "Total points should exceed base samples due to dynamic injection");
        assertTrue(res.injectedPoints() > 0, "Points should be injected in steep/asymptote regions");
        assertTrue(res.steepSegmentsCount() > 0, "Steep segments should be detected around 1/x singularity");
        assertFalse(res.steepRegions().isEmpty(), "Steep regions metadata should be recorded");
    }

    @Test
    @DisplayName("analyze() evaluates polynomial and smooth functions with workspace variables")
    void testAnalyzeWithVariables() {
        GraphAnalyzeRequest req = new GraphAnalyzeRequest(
                "a * x^2 + b",
                "x",
                BigDecimal.valueOf(-5),
                null,
                BigDecimal.valueOf(5),
                null,
                Map.of("a", BigDecimal.valueOf(2), "b", BigDecimal.valueOf(3)),
                null,
                "DEGREES",
                15,
                BigDecimal.valueOf(15.0),
                50,
                10
        );

        GraphAnalyzeResponse res = graphService.analyze(req);

        assertNotNull(res);
        assertEquals(0, BigDecimal.valueOf(-5).compareTo(res.points().get(0).x()));
        assertEquals(0, BigDecimal.valueOf(5).compareTo(res.points().get(res.points().size() - 1).x()));

        boolean foundZero = false;
        for (GraphAnalyzeResponse.GraphPointDto p : res.points()) {
            if (p.x().compareTo(BigDecimal.ZERO) == 0) {
                foundZero = true;
                assertNotNull(p.y());
                assertEquals(0, BigDecimal.valueOf(3).compareTo(p.y()));
            }
        }
        assertTrue(foundZero || res.points().size() >= 50);
    }

    @Test
    @DisplayName("analyze() handles domain errors gracefully as null without crashing")
    void testAnalyzeDomainErrorGracefulHandling() {
        GraphAnalyzeRequest req = new GraphAnalyzeRequest(
                "sqrt(x)",
                "x",
                BigDecimal.valueOf(-5),
                null,
                BigDecimal.valueOf(5),
                null,
                Map.of(),
                null,
                "DEGREES",
                15,
                BigDecimal.valueOf(10.0),
                30,
                10
        );

        GraphAnalyzeResponse res = graphService.analyze(req);

        assertNotNull(res);
        assertFalse(res.points().isEmpty());

        GraphAnalyzeResponse.GraphPointDto firstPoint = res.points().get(0);
        assertNull(firstPoint.y());

        for (GraphAnalyzeResponse.GraphPointDto p : res.points()) {
            if (p.x().compareTo(BigDecimal.valueOf(4)) == 0) {
                assertNotNull(p.y());
                assertEquals(0, BigDecimal.valueOf(2).compareTo(p.y()));
            }
        }
    }

    @Test
    @DisplayName("analyze() throws IllegalArgumentException when startX >= endX")
    void testInvalidViewportRange() {
        GraphAnalyzeRequest req = new GraphAnalyzeRequest(
                "x^2",
                "x",
                BigDecimal.valueOf(10),
                null,
                BigDecimal.valueOf(5),
                null,
                Map.of(),
                null,
                "DEGREES",
                15,
                BigDecimal.valueOf(10.0),
                50,
                10
        );

        assertThrows(IllegalArgumentException.class, () -> graphService.analyze(req));
    }
}
