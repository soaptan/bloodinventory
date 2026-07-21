package com.fyp.bloodinventory.service;

import com.fyp.bloodinventory.dto.ChatbotRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatbotServiceTests {

    @Test
    void providesRoleAwareAgentGuidanceWithoutExternalApiKey() {
        ChatbotService service = serviceWithoutApiKey();
        ChatbotRequest request = request("Review inventory risks and tell me what to check first", "agent");

        String reply = service.ask(request, "admin", List.of("ROLE_BLOOD_ADMINISTRATOR"));

        assertThat(reply)
                .contains("Goal: Review inventory risks")
                .contains("Current step:")
                .contains("[[navigate:/admin/inventory|Open Inventory Monitoring]]")
                .contains("authorized review");
    }

    @Test
    void providesContextualChatGuidanceWithoutExternalApiKey() {
        ChatbotService service = serviceWithoutApiKey();
        ChatbotRequest request = request("What can I do here?", "chat");
        request.setPagePath("/admin/storage");

        String reply = service.ask(request, "admin", List.of("ROLE_BLOOD_ADMINISTRATOR"));

        assertThat(reply)
                .contains("Storage Configuration")
                .contains("archive or restore locations")
                .doesNotContain("not configured");
    }

    @Test
    void keepsBloodAdministratorInsideAdministratorModules() {
        ChatbotService service = serviceWithoutApiKey();
        ChatbotRequest request = request("Guide me through processing a donation safely", "agent");

        String reply = service.ask(request, "admin", List.of("ROLE_BLOOD_ADMINISTRATOR"));

        assertThat(reply)
                .contains("outside the Blood Administrator role")
                .doesNotContain("/medical/")
                .doesNotContain("Open Donor Eligibility");
    }

    @Test
    void removesCrossModuleNavigationEvenWhenModelSuggestsIt() {
        ChatbotService service = serviceWithoutApiKey();
        String modelReply = """
                Review the records.
                [[navigate:/medical/donor-eligibility|Open Donor Eligibility]]
                [[navigate:/admin/inventory|Open Inventory Monitoring]]
                """;

        String reply = service.sanitizeNavigationTokens(
                modelReply,
                List.of("ROLE_BLOOD_ADMINISTRATOR")
        );

        assertThat(reply)
                .doesNotContain("/medical/")
                .contains("[[navigate:/admin/inventory|Open Inventory Monitoring]]");
    }

    @Test
    void understandsMalayAndRepliesInMalayWithoutExternalApiKey() {
        ChatbotService service = serviceWithoutApiKey();
        ChatbotRequest request = request("Tolong semak risiko inventori dan stok yang hampir luput", "agent");

        String reply = service.ask(request, "admin", List.of("ROLE_BLOOD_ADMINISTRATOR"));

        assertThat(reply)
                .contains("Matlamat:")
                .contains("Langkah semasa:")
                .contains("[[navigate:/admin/inventory|Buka Pemantauan Inventori]]");
    }

    @Test
    void understandsChineseAndRepliesInChineseWithoutExternalApiKey() {
        ChatbotService service = serviceWithoutApiKey();
        ChatbotRequest request = request("如何检查库存和临近过期的血液成分？", "agent");

        String reply = service.ask(request, "admin", List.of("ROLE_BLOOD_ADMINISTRATOR"));

        assertThat(reply)
                .contains("目标：")
                .contains("当前步骤：")
                .contains("[[navigate:/admin/inventory|打开库存监控]]");
    }

    @Test
    void usesPreferredInterfaceLanguageWhenQuestionLanguageIsAmbiguous() {
        ChatbotService service = serviceWithoutApiKey();
        ChatbotRequest request = request("TTI", "chat");
        request.setLanguageCode("ms");

        String reply = service.ask(request, "lab", List.of("ROLE_LAB_TECHNICIAN"));

        assertThat(reply).contains("Saya memahami Bahasa Malaysia");
    }

    private ChatbotService serviceWithoutApiKey() {
        return new ChatbotService(
                "https://integrate.api.nvidia.com",
                "",
                "meta/llama-3.3-70b-instruct",
                "https://example.test/vision",
                "meta/llama-3.2-90b-vision-instruct",
                0.2,
                700
        );
    }

    private ChatbotRequest request(String message, String mode) {
        ChatbotRequest request = new ChatbotRequest();
        request.setMessage(message);
        request.setMode(mode);
        return request;
    }
}
