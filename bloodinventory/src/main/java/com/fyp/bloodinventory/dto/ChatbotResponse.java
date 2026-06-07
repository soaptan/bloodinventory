package com.fyp.bloodinventory.dto;

public class ChatbotResponse {

    private boolean success;
    private String reply;
    private String message;

    public static ChatbotResponse success(String reply) {
        ChatbotResponse response = new ChatbotResponse();
        response.setSuccess(true);
        response.setReply(reply);
        response.setMessage("OK");
        return response;
    }

    public static ChatbotResponse error(String message) {
        ChatbotResponse response = new ChatbotResponse();
        response.setSuccess(false);
        response.setReply("");
        response.setMessage(message);
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
