package com.fyp.bloodinventory.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatbotRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsChatAndAgentModes() {
        ChatbotRequest chatRequest = requestWithMode("chat");
        ChatbotRequest agentRequest = requestWithMode("AGENT");

        assertThat(validator.validate(chatRequest)).isEmpty();
        assertThat(validator.validate(agentRequest)).isEmpty();
        assertThat(agentRequest.getMode()).isEqualTo("agent");
    }

    @Test
    void rejectsUnsupportedMode() {
        ChatbotRequest request = requestWithMode("autonomous");

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("mode"));
    }

    @Test
    void acceptsSupportedAssistantLanguagesAndNormalizesChineseLocale() {
        ChatbotRequest malayRequest = requestWithMode("chat");
        malayRequest.setLanguageCode("ms");
        ChatbotRequest chineseRequest = requestWithMode("chat");
        chineseRequest.setLanguageCode("zh-CN");

        assertThat(validator.validate(malayRequest)).isEmpty();
        assertThat(validator.validate(chineseRequest)).isEmpty();
        assertThat(chineseRequest.getLanguageCode()).isEqualTo("zh");
    }

    @Test
    void rejectsUnsupportedAssistantLanguageCode() {
        ChatbotRequest request = requestWithMode("chat");
        request.setLanguageCode("unsupported");

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("languageCode"));
    }

    private ChatbotRequest requestWithMode(String mode) {
        ChatbotRequest request = new ChatbotRequest();
        request.setMessage("Help me review inventory.");
        request.setMode(mode);
        return request;
    }
}
