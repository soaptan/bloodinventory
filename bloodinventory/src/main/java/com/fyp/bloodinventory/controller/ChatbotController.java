package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.dto.ChatbotRequest;
import com.fyp.bloodinventory.dto.ChatbotResponse;
import com.fyp.bloodinventory.service.ChatbotService;
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

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
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

    private String safeMessage(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }
}
