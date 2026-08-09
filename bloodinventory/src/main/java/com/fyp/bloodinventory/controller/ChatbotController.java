package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.dto.ChatbotFeedbackRequest;
import com.fyp.bloodinventory.dto.ChatbotRequest;
import com.fyp.bloodinventory.dto.ChatbotResponse;
import com.fyp.bloodinventory.service.ChatbotService;
import com.fyp.bloodinventory.service.SystemNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Collection;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private final ChatbotService chatbotService;
    private final SystemNotificationService notificationService;

    public ChatbotController(ChatbotService chatbotService, SystemNotificationService notificationService) {
        this.chatbotService = chatbotService;
        this.notificationService = notificationService;
    }

    @PostMapping("/ask")
    public ResponseEntity<ChatbotResponse> ask(@Valid @RequestBody ChatbotRequest request,
                                               Principal principal,
                                               Authentication authentication) {
        try {
            String reply = chatbotService.ask(
                    request,
                    actorName(principal),
                    authorityNames(authentication)
            );
            return ResponseEntity.ok(ChatbotResponse.success(reply));
        } catch (IllegalStateException ex) {
            String errorMessage = safeMessage(ex.getMessage(), "The assistant is unavailable right now.");
            HttpStatus status = errorMessage.contains("not configured")
                    ? HttpStatus.SERVICE_UNAVAILABLE
                    : HttpStatus.BAD_GATEWAY;
            return ResponseEntity.status(status).body(ChatbotResponse.error(errorMessage));
        }
    }

    @PostMapping("/feedback")
    public ResponseEntity<ChatbotResponse> feedback(@Valid @RequestBody ChatbotFeedbackRequest request,
                                                    Principal principal,
                                                    HttpServletRequest servletRequest) {
        try {
            notificationService.record(
                    "Chatbot Feedback",
                    "FEEDBACK",
                    feedbackMessage(request),
                    actorName(principal),
                    sourceIp(servletRequest)
            );
            return ResponseEntity.ok(ChatbotResponse.success("Feedback received. Thank you."));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatbotResponse.error("Unable to save feedback right now."));
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ChatbotResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "Please check your message." : error.getDefaultMessage())
                .orElse("Please check your message.");
        return ResponseEntity.badRequest().body(ChatbotResponse.error(message));
    }

    private Collection<String> authorityNames(Authentication authentication) {
        if (authentication == null) {
            return java.util.List.of();
        }

        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority() == null ? "" : authority.getAuthority())
                .filter(authority -> !authority.isBlank())
                .toList();
    }

    private String actorName(Principal principal) {
        if (principal == null) {
            return "system";
        }

        String name = principal.getName();
        return name == null || name.isBlank() ? "system" : name;
    }

    private String feedbackMessage(ChatbotFeedbackRequest request) {
        String pagePath = clean(request.getPagePath());
        String location = pagePath.isBlank() ? "" : " on " + pagePath;
        return truncate(
                "Feedback from " + clean(request.getName()) + " (" + clean(request.getEmail()) + ")"
                        + location + ": " + clean(request.getMessage()),
                255
        );
    }

    private String sourceIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        return request.getRemoteAddr();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String safeMessage(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }
}
