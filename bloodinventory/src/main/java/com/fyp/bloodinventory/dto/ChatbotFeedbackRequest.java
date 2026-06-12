package com.fyp.bloodinventory.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChatbotFeedbackRequest {

    @NotBlank(message = "Please enter your name.")
    @Size(max = 100, message = "Name must be 100 characters or fewer.")
    private String name;

    @NotBlank(message = "Please enter your email.")
    @Email(message = "Please enter a valid email address.")
    @Size(max = 100, message = "Email must be 100 characters or fewer.")
    private String email;

    @NotBlank(message = "Please enter your feedback.")
    @Size(max = 700, message = "Feedback must be 700 characters or fewer.")
    private String message;

    @Size(max = 160, message = "Page title is too long.")
    private String pageTitle;

    @Size(max = 240, message = "Page path is too long.")
    private String pagePath;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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
}
