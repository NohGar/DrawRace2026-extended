package backend.drawrace.domain.round.dto.gateway;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GatewayChatRequest {

    private String model;
    private List<Message> messages;
    private double temperature;

    @Getter
    @Builder
    public static class Message {
        private String role;
        private Object content;
    }

    public static Message systemMessage(String content) {
        return Message.builder().role("system").content(content).build();
    }

    public static Message userMessage(Object content) {
        return Message.builder().role("user").content(content).build();
    }

    public static Map<String, Object> textContent(String text) {
        return Map.of("type", "text", "text", text);
    }

    public static Map<String, Object> imageContent(String imageData) {
        return Map.of("type", "image_url", "image_url", Map.of("url", imageData));
    }
}
