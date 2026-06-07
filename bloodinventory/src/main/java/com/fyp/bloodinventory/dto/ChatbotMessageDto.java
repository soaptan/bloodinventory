package com.fyp.bloodinventory.dto;

import jakarta.validation.constraints.Size;

public class ChatbotMessageDto {

    @Size(max = 20, message = "Message role is too long.")
    private String role;

    @Size(max = 2000, message = "Chat history message is too long.")
    private String content;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
