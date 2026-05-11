package backend.drawrace.domain.chat.service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import backend.drawrace.domain.chat.dto.ChatMessageDto;
import backend.drawrace.domain.round.dto.gateway.GatewayChatRequest;
import backend.drawrace.domain.round.dto.gateway.GatewayChatResponse;
import backend.drawrace.global.config.AiProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnProperty(name = "ai.mode", havingValue = "quickdraw")
@RequiredArgsConstructor
public class AiChatService {

    private static final long CHAT_DELAY_MIN_MS = 2_000L;
    private static final long CHAT_DELAY_MAX_MS = 5_000L;

    private final AiProperties aiProperties;
    private final SimpMessagingTemplate messagingTemplate;

    @Async
    public void triggerOnAiJoin(Long roomId, String aiNickname) {
        try {
            sleep();
            String message = generateChatMessage(buildJoinPrompt());
            broadcast(roomId, aiNickname, message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("AI 채팅 생성 실패 (입장). roomId={}", roomId, e);
        }
    }

    @Async
    public void triggerOnRoundStart(Long roomId, String keyword, String aiNickname) {
        try {
            sleep();
            String message = generateChatMessage(buildRoundStartPrompt(keyword));
            broadcast(roomId, aiNickname, message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("AI 채팅 생성 실패 (라운드 시작). roomId={}", roomId, e);
        }
    }

    @Async
    public void triggerOnRoundEnd(Long roomId, String keyword, String aiNickname, boolean aiIsWinner) {
        try {
            sleep();
            String message = generateChatMessage(buildRoundEndPrompt(keyword, aiIsWinner));
            broadcast(roomId, aiNickname, message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("AI 채팅 생성 실패 (라운드 종료). roomId={}", roomId, e);
        }
    }

    private void sleep() throws InterruptedException {
        long delay = ThreadLocalRandom.current().nextLong(CHAT_DELAY_MIN_MS, CHAT_DELAY_MAX_MS + 1);
        Thread.sleep(delay);
    }

    private String generateChatMessage(String systemPrompt) {
        RestClient restClient = RestClient.builder()
                .baseUrl(aiProperties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + aiProperties.apiKey())
                .build();

        GatewayChatRequest request = GatewayChatRequest.builder()
                .model(aiProperties.model())
                .messages(List.of(GatewayChatRequest.systemMessage(systemPrompt)))
                .temperature(0.8)
                .build();

        GatewayChatResponse response = restClient
                .post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .toEntity(GatewayChatResponse.class)
                .getBody();

        return response.getChoices().get(0).getMessage().getContent().trim();
    }

    private String buildJoinPrompt() {
        return """
                너는 그림 맞추기 게임에 참가한 AI 플레이어야.
                방에 막 입장했어. 자연스럽고 짧은 한국어 인삿말을 한 문장으로 만들어줘.
                이모지 포함 가능. 말투는 친근하게. 반드시 한 문장만 출력해.
                """;
    }

    private String buildRoundStartPrompt(String keyword) {
        return String.format("""
                너는 그림 맞추기 게임에 참가한 AI 플레이어야.
                지금 라운드의 제시어는 '%s'야.
                게임 참가자로서 자연스럽고 짧은 한국어 채팅 메시지를 한 문장으로 만들어줘.
                그림 그리기 시작에 어울리는 말이어야 해.
                이모지 포함 가능. 말투는 친근하게. 반드시 한 문장만 출력해.
                """, keyword);
    }

    private String buildRoundEndPrompt(String keyword, boolean aiIsWinner) {
        String context = aiIsWinner ? "이번 라운드에서 내가 이겼어" : "이번 라운드에서 내가 졌어";
        return String.format("""
                너는 그림 맞추기 게임에 참가한 AI 플레이어야.
                제시어는 '%s'였고, %s.
                게임 참가자로서 자연스럽고 짧은 한국어 채팅 메시지를 한 문장으로 만들어줘.
                이모지 포함 가능. 말투는 친근하게. 반드시 한 문장만 출력해.
                """, keyword, context);
    }

    private void broadcast(Long roomId, String aiNickname, String message) {
        ChatMessageDto chatMessage = ChatMessageDto.builder()
                .type(ChatMessageDto.MessageType.TALK)
                .roomId(roomId)
                .sender(aiNickname)
                .message(message)
                .build();
        messagingTemplate.convertAndSend("/sub/rooms/" + roomId + "/chat", chatMessage);
    }
}
