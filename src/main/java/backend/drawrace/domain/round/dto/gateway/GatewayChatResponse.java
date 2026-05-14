package backend.drawrace.domain.round.dto.gateway;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GatewayChatResponse {

    private List<Choice> choices;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private Message message;

        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private String role;
        private String content;

        /** 일부 모델/정책에서 거절 사유가 여기로 온다. */
        private String refusal;

        @JsonProperty("tool_calls")
        private JsonNode toolCalls;
    }
}
