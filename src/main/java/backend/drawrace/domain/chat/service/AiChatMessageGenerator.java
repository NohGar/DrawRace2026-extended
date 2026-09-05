package backend.drawrace.domain.chat.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import backend.drawrace.domain.chat.entity.AiChatMessage.MessageType;
import backend.drawrace.domain.round.dto.gateway.GatewayChatRequest;
import backend.drawrace.domain.round.dto.gateway.GatewayChatResponse;
import backend.drawrace.global.config.AiProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatMessageGenerator {

    private final AiProperties aiProperties;

    private static final Map<MessageType, List<String>> FALLBACK_MESSAGES = Map.of(
            MessageType.JOIN,
                    List.of(
                            "안녕하세요~ 잘 부탁드려요! 😊",
                            "드디어 입장! 같이 즐겨봐요 🎮",
                            "오오 다들 그림 잘 그리시나요? 💪",
                            "AI가 왔다! 긴장하지 마세요 ㅎㅎ",
                            "반갑습니다~ 실력 한번 겨뤄봐요! ✨",
                            "늦지 않았죠? 열심히 해볼게요 🖌️"),
            MessageType.SUBMIT,
                    List.of(
                            "제출 완료! 어떻게 됐을지 두근두근 🤞",
                            "다 그렸어요! 맞춰봐요 ㅎㅎ",
                            "이번엔 좀 잘 그린 것 같은데? 😏",
                            "흠... 잘 나왔나 모르겠네요 😅",
                            "자신있어요! 이번엔 내 턴 💪",
                            "오늘 그림 실력이 좋은 것 같아요 🎨",
                            "제출! 결과가 궁금하다 👀"));

    public String generate(MessageType type) {
        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl(aiProperties.baseUrl())
                    .defaultHeader("Authorization", "Bearer " + aiProperties.apiKey())
                    .build();

            GatewayChatRequest request = GatewayChatRequest.builder()
                    .model(aiProperties.model())
                    .temperature(1.0)
                    .messages(List.of(
                            GatewayChatRequest.systemMessage(buildSystemPrompt(type)),
                            GatewayChatRequest.userMessage("채팅 메시지 하나 생성해줘.")))
                    .build();

            GatewayChatResponse response = restClient
                    .post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(GatewayChatResponse.class);

            String message = extractMessage(response);
            if (message.isBlank()) {
                log.warn("GLM 채팅 메시지 응답이 비어 있어 fallback 사용");
                return getFallback(type);
            }
            return message;
        } catch (Exception e) {
            log.warn("GLM 채팅 메시지 생성 실패, fallback 사용", e);
            return getFallback(type);
        }
    }

    private String buildSystemPrompt(MessageType type) {
        String situation =
                switch (type) {
                    case JOIN -> "방금 그림 그리기 게임 방에 입장했다";
                    case SUBMIT -> "방금 그림을 다 그려서 제출했다";
                };
        return """
                너는 그림 그리기 게임에 참여 중인 AI 참가자다.
                지금 상황: %s.
                조건:
                - 한국어로 1문장, 10~30자
                - 친근하고 자연스러운 채팅 말투, 이모지 0~1개 허용
                - 욕설, 비방, 성적 표현 금지
                - 메시지 1개만 반환하고 설명, 따옴표, 번호, 부가 문장 없이 출력
                """.formatted(situation);
    }

    private String extractMessage(GatewayChatResponse response) {
        if (response == null
                || response.getChoices() == null
                || response.getChoices().isEmpty()
                || response.getChoices().get(0).getMessage() == null) {
            return "";
        }
        String content = response.getChoices().get(0).getMessage().getContent();
        if (content == null) return "";
        return content.trim().replaceAll("^[\"']|[\"']$", "");
    }

    private String getFallback(MessageType type) {
        List<String> fallbacks = FALLBACK_MESSAGES.get(type);
        return fallbacks.get(ThreadLocalRandom.current().nextInt(fallbacks.size()));
    }
}
