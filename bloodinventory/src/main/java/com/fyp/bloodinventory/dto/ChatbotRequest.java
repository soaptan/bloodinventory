package com.fyp.bloodinventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class ChatbotRequest {

    @NotBlank(message = "Please enter a question.")
    @Size(max = 2000, message = "Question must be 2000 characters or fewer.")
    private String message;

    @Valid
    @Size(max = 12, message = "Chat history is too long.")
    private List<ChatbotMessageDto> history = new ArrayList<>();

    @Size(max = 160, message = "Page title is too long.")
    private String pageTitle;

    @Size(max = 240, message = "Page path is too long.")
    private String pagePath;

    @Size(max = 220000, message = "Uploaded image is too large. Please use a smaller image.")
    private String imageDataUrl;

    @Size(max = 120, message = "Image file name is too long.")
    private String imageFileName;

    @Size(max = 40, message = "Image type is too long.")
    private String imageMimeType;

    @Pattern(regexp = "(?i)chat|agent", message = "Assistant mode must be chat or agent.")
    private String mode = "chat";

    @Pattern(regexp = "(?i)en|ms|zh", message = "Assistant language must be English, Bahasa Malaysia, or Chinese.")
    private String languageCode = "en";

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<ChatbotMessageDto> getHistory() {
        return history;
    }

    public void setHistory(List<ChatbotMessageDto> history) {
        this.history = history == null ? new ArrayList<>() : history;
    }

    public String getPageTitle() {
        return pageTitle;
    }

    public void setPageTitle(String pageTitle) {
        this.pageTitle = pageTitle;
    }

    public String getPagePath() {
        return pagePath;
    }

    public void setPagePath(String pagePath) {
        this.pagePath = pagePath;
    }

    public String getImageDataUrl() {
        return imageDataUrl;
    }

    public void setImageDataUrl(String imageDataUrl) {
        this.imageDataUrl = imageDataUrl;
    }

    public String getImageFileName() {
        return imageFileName;
    }

    public void setImageFileName(String imageFileName) {
        this.imageFileName = imageFileName;
    }

    public String getImageMimeType() {
        return imageMimeType;
    }

    public void setImageMimeType(String imageMimeType) {
        this.imageMimeType = imageMimeType;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode == null || mode.isBlank() ? "chat" : mode.trim().toLowerCase();
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        String normalized = languageCode == null ? "en" : languageCode.trim().toLowerCase();
        this.languageCode = normalized.startsWith("zh") ? "zh" : normalized;
    }
}
