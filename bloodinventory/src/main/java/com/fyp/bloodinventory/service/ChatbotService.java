package com.fyp.bloodinventory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fyp.bloodinventory.dto.ChatbotMessageDto;
import com.fyp.bloodinventory.dto.ChatbotRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class ChatbotService {

    private static final int MAX_IMAGE_DATA_URL_LENGTH = 220000;
    private static final String SYSTEM_PROMPT = """
            You are the Blood Inventory Assistant for the Blood Inventory Management System.
            Help authenticated staff understand and use the system clearly and safely.

            System modules:
            - Administrator: dashboard, staff management, storage configuration, deferral rules, inventory monitoring, reports and alerts, settings.
            - Medical staff: donor eligibility, blood collection and donation sessions, transfusion requests, safe blood matching, component review.
            - Lab technician: pending test queue, TTI screening, component status, traceability and audit trail.

            Guidance rules:
            - Give concise, practical steps that match the user's role and current page when possible.
            - Answer general blood inventory workflow questions, but do not invent exact donor, patient, staff, stock, test, or report data.
            - For exact records or counts, tell the user where to view or verify them in the system.
            - Do not make final clinical or lab safety decisions; tell users to follow hospital policy and authorized review.
            - Never ask for passwords, API keys, or secrets.
            """;

    private final RestClient restClient;
    private final RestClient visionRestClient;
    private final String apiKey;
    private final String chatModel;
    private final String visionUrl;
    private final String visionModel;
    private final double temperature;
    private final int maxTokens;

    public ChatbotService(@Value("${nvidia.nim.base-url:https://integrate.api.nvidia.com}") String baseUrl,
                          @Value("${nvidia.nim.api-key:}") String apiKey,
                          @Value("${nvidia.nim.chat-model:meta/llama-3.3-70b-instruct}") String chatModel,
                          @Value("${nvidia.nim.vision-url:https://ai.api.nvidia.com/v1/gr/meta/llama-3.2-90b-vision-instruct/meta/llama-3.2-90b-vision-instruct}") String visionUrl,
                          @Value("${nvidia.nim.vision-model:meta/llama-3.2-90b-vision-instruct}") String visionModel,
                          @Value("${nvidia.nim.temperature:0.2}") double temperature,
                          @Value("${nvidia.nim.max-tokens:700}") int maxTokens) {
        this.restClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(baseUrl))
                .build();
        this.visionRestClient = RestClient.builder().build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.chatModel = chatModel == null || chatModel.isBlank()
                ? "meta/llama-3.3-70b-instruct"
                : chatModel.trim();
        this.visionUrl = visionUrl == null || visionUrl.isBlank()
                ? "https://ai.api.nvidia.com/v1/gr/meta/llama-3.2-90b-vision-instruct/meta/llama-3.2-90b-vision-instruct"
                : visionUrl.trim();
        this.visionModel = visionModel == null || visionModel.isBlank()
                ? "meta/llama-3.2-90b-vision-instruct"
                : visionModel.trim();
        this.temperature = temperature;
        this.maxTokens = Math.max(128, Math.min(maxTokens, 1600));
    }

    public String ask(ChatbotRequest request, String username, Collection<String> authorities) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("The chatbot is not configured yet.");
        }

        if (hasImage(request)) {
            return askVision(request, username, authorities);
        }

        return askText(request, username, authorities);
    }

    private String askText(ChatbotRequest request, String username, Collection<String> authorities) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", chatModel);
        payload.put("messages", buildMessages(request, username, authorities));
        payload.put("temperature", temperature);
        payload.put("max_tokens", maxTokens);

        try {
            JsonNode response = postChatCompletion(payload);
            return extractReply(response);
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("The AI service returned an error. Please try again later.", ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Unable to reach the AI service. Please try again later.", ex);
        }
    }

    private JsonNode postChatCompletion(Map<String, Object> payload) {
        JsonNode response = restClient.post()
                .uri("/v1/chat/completions")
                .contentType(jsonMediaType())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);
        return Objects.requireNonNull(response, "AI response must not be null.");
    }

    private JsonNode postVisionCompletion(Map<String, Object> payload) {
        JsonNode response = visionRestClient.post()
                .uri(visionUrl)
                .contentType(jsonMediaType())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);
        return Objects.requireNonNull(response, "Vision AI response must not be null.");
    }

    private String askVision(ChatbotRequest request, String username, Collection<String> authorities) {
        String imageDataUrl = validatedImageDataUrl(request);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", visionModel);
        payload.put("messages", buildVisionMessages(request, username, authorities, imageDataUrl));
        payload.put("temperature", temperature);
        payload.put("max_tokens", maxTokens);

        try {
            JsonNode response = postVisionCompletion(payload);
            return extractReply(response);
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("The vision AI service returned an error. Please try a smaller PNG or JPG image.", ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Unable to reach the vision AI service. Please try again later.", ex);
        }
    }

    private List<Map<String, String>> buildMessages(ChatbotRequest request,
                                                    String username,
                                                    Collection<String> authorities) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", SYSTEM_PROMPT + "\n\nCurrent context:\n" + currentContext(request, username, authorities)));

        request.getHistory().stream()
                .map(this::sanitizeHistoryMessage)
                .flatMap(List::stream)
                .limit(10)
                .forEach(messages::add);

        messages.add(message("user", request.getMessage().trim()));
        return messages;
    }

    private List<Map<String, Object>> buildVisionMessages(ChatbotRequest request,
                                                          String username,
                                                          Collection<String> authorities,
                                                          String imageDataUrl) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of(
                "type", "text",
                "text", visionPrompt(request, username, authorities)
        ));
        content.add(Map.of(
                "type", "image_url",
                "image_url", Map.of("url", imageDataUrl)
        ));

        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", content);
        return List.of(userMessage);
    }

    private List<Map<String, String>> sanitizeHistoryMessage(ChatbotMessageDto historyMessage) {
        if (historyMessage == null) {
            return List.of();
        }

        String role = normalizeRole(historyMessage.getRole());
        String content = historyMessage.getContent() == null ? "" : historyMessage.getContent().trim();

        if (role.isBlank() || content.isBlank()) {
            return List.of();
        }

        return List.of(message(role, truncate(content, 2000)));
    }

    private String currentContext(ChatbotRequest request, String username, Collection<String> authorities) {
        Collection<String> safeAuthorities = authorities == null ? List.of() : authorities;
        List<String> lines = new ArrayList<>();
        lines.add("Signed-in username: " + safeContext(username, "unknown"));
        lines.add("Authorities: " + safeContext(String.join(", ", safeAuthorities), "unknown"));
        lines.add("Current page title: " + safeContext(request.getPageTitle(), "unknown"));
        lines.add("Current page path: " + safeContext(request.getPagePath(), "unknown"));
        if (hasImage(request)) {
            lines.add("Uploaded image: " + safeContext(request.getImageFileName(), "attached image"));
        }
        return String.join("\n", lines);
    }

    private String visionPrompt(ChatbotRequest request, String username, Collection<String> authorities) {
        return """
                %s

                Current context:
                %s

                User question:
                %s

                Inspect the attached image. If it is a screenshot of this system, explain what the user is seeing and guide the next action. If it contains forms, labels, or records, summarize visible information only and remind the user to verify exact data in the system. Do not identify people or make final clinical, transfusion, or lab safety decisions from the image alone.
                """.formatted(
                SYSTEM_PROMPT,
                currentContext(request, username, authorities),
                request.getMessage().trim()
        );
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "assistant" -> "assistant";
            case "user" -> "user";
            default -> "";
        };
    }

    private String extractReply(JsonNode response) {
        if (response == null) {
            throw new IllegalStateException("The AI service returned an empty response.");
        }

        String reply = response.path("choices").path(0).path("message").path("content").asText("");
        if (reply.isBlank()) {
            throw new IllegalStateException("The AI service returned an empty response.");
        }

        return reply.trim();
    }

    private boolean hasImage(ChatbotRequest request) {
        return request.getImageDataUrl() != null && !request.getImageDataUrl().isBlank();
    }

    private String validatedImageDataUrl(ChatbotRequest request) {
        String dataUrl = request.getImageDataUrl() == null ? "" : request.getImageDataUrl().trim();
        String lower = dataUrl.toLowerCase(Locale.ROOT);

        if (dataUrl.length() > MAX_IMAGE_DATA_URL_LENGTH) {
            throw new IllegalStateException("Uploaded image is too large. Please use a smaller PNG or JPG image.");
        }

        if (!(lower.startsWith("data:image/png;base64,")
                || lower.startsWith("data:image/jpeg;base64,")
                || lower.startsWith("data:image/jpg;base64,"))) {
            throw new IllegalStateException("Please upload a PNG or JPG image.");
        }

        int commaIndex = dataUrl.indexOf(',');
        if (commaIndex < 0 || commaIndex == dataUrl.length() - 1) {
            throw new IllegalStateException("The uploaded image could not be read.");
        }

        String encodedImage = dataUrl.substring(commaIndex + 1);
        if (!encodedImage.matches("[A-Za-z0-9+/=]+")) {
            throw new IllegalStateException("The uploaded image could not be read.");
        }

        return dataUrl;
    }

    private String safeContext(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        return truncate(value.replaceAll("\\s+", " ").trim(), 240);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength - 3) + "...";
    }

    private static MediaType jsonMediaType() {
        return MediaType.APPLICATION_JSON;
    }

    private static String trimTrailingSlash(String value) {
        String normalized = value == null || value.isBlank()
                ? "https://integrate.api.nvidia.com"
                : value.trim();

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }
}
