package com.calcforge.service;

import com.calcforge.config.CloudFeatureProperties;
import com.calcforge.dto.request.AiAssistRequest;
import com.calcforge.dto.response.AiAssistResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Natural-language calculation assistance. Strictly optional and off by default - every
 * core feature works with this permanently disabled. When enabled with a valid API key,
 * asks Claude to explain an expression or answer a math question in plain language; when
 * not configured, or if the call fails, returns a clear "unavailable" response rather
 * than an error, so the frontend can degrade gracefully.
 */
@Service
@Slf4j
public class AiAssistService {

    private static final String SYSTEM_PROMPT = """
            You are the calculation assistant embedded in CalcForge, an offline-first calculator.
            Explain math clearly and concisely in plain text (no markdown tables). When helpful,
            show the CalcForge-style expression the user could type (e.g. "sqrt(2)^2 * pi") rather
            than just prose. Keep answers under 200 words unless the user asks for full derivation.
            """;

    private final CloudFeatureProperties featureProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String apiKey;
    private final String model;

    public AiAssistService(CloudFeatureProperties featureProperties,
                            ObjectMapper objectMapper,
                            @Value("${calcforge.cloud.ai.anthropic-api-key:}") String apiKey,
                            @Value("${calcforge.cloud.ai.model:claude-sonnet-4-5}") String model) {
        this.featureProperties = featureProperties;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    public AiAssistResponse ask(AiAssistRequest request) {
        if (!featureProperties.isAiAssistEnabled() || apiKey == null || apiKey.isBlank()) {
            return new AiAssistResponse(false,
                    "AI assistance is not enabled on this server. Set calcforge.cloud.ai-assist-enabled=true " +
                            "and calcforge.cloud.ai.anthropic-api-key to turn it on.",
                    null);
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", 1000,
                    "system", SYSTEM_PROMPT,
                    "messages", List.of(Map.of("role", "user", "content", request.question())));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/messages"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Anthropic API returned HTTP {}: {}", response.statusCode(), response.body());
                return new AiAssistResponse(false, "The AI assistant is temporarily unavailable. Please try again later.", null);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentArray = root.path("content");
            String answer = (contentArray.isArray() && !contentArray.isEmpty())
                    ? contentArray.get(0).path("text").asText("")
                    : "";
            return new AiAssistResponse(true, answer, root.path("model").asText(model));
        } catch (Exception e) {
            log.warn("AI assist call failed: {}", e.getMessage());
            return new AiAssistResponse(false, "The AI assistant is temporarily unavailable. Please try again later.", null);
        }
    }
}
