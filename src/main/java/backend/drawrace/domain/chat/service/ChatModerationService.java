package backend.drawrace.domain.chat.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import backend.drawrace.domain.round.dto.gateway.GatewayChatRequest;
import backend.drawrace.domain.round.dto.gateway.GatewayChatResponse;
import backend.drawrace.global.config.AiProperties;
import backend.drawrace.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatModerationService {

    private final AiProperties aiProperties;

    private final Map<Long, LastChatInfo> chatHistory = new ConcurrentHashMap<>();
    private static final int SPAM_LIMIT_MS = 1000;

    public String filterMessage(Long userId, String originalMessage) {
        try {
            // 도배 체크
            checkSpam(userId, originalMessage);

            // 분리된 메서드 호출
            String aiDecision = getAiDecision(originalMessage);

            // AI 판정 결과
            return aiDecision;

        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 검열 중 오류 발생: {}", e.getMessage());
            return originalMessage;
        }
    }

    protected String getAiDecision(String message) {
        RestClient restClient = RestClient.builder()
                .baseUrl(aiProperties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + aiProperties.apiKey())
                .build();

        // 프롬프트
        String systemPrompt = """
                [기본 원칙]
                당신은 유저가 그린 그림을 AI가 판별하는 게임 'DrawRace'의 커뮤니티 관리자입니다. 유저들은 AI의 판별 결과에 따라 순위 경쟁을 하고 있습니다. 아래 기준에 따라 부적절한 메시지의 해당 부분만 ****로 치환하세요.

                [상세 검열 기준]
                1. 비속어 및 공격적 언어 (Profanity & Aggression)

                - 직접적인 욕설, 패드립, 타인에 대한 비하 발언.

                - 변형된 욕설 및 초성 욕설 (예: 'ㅅㅂ', 'ㄲㅈ' 등).

                2. 유저 간 비난 및 도발 (Competitive Toxicity)

                - 레이스 중 다른 참가자의 그림 실력을 심하게 비하하거나 조롱하여 불쾌감을 주는 행위.

                - (예: "와 님 손은 장식임? 진짜 드럽게 못 그리네" -> "와 님 손은 장식임? 진짜 **** 그리네")

                3. 성적 콘텐츠 및 혐오 표현 (Sexual & Hate Speech)

                - 음란한 표현, 성희롱, 특정 집단(성별, 지역 등)에 대한 혐오 발언.

                4. 도배 및 스팸 (Spamming)

                - 의미 없는 문자의 반복, 홍보성 문구 등 게임 진행을 방해하는 채팅.

                [출력 규칙]
                - 필터링된 결과값만 출력 (설명이나 인사말 절대 금지).

                - 금지 단어에 해당하는 부분만 정확히 ****로 치환.

                - 해당하는 위반 사항이 전혀 없으면 유저의 원문을 토씨 하나 안 틀리고 그대로 출력.
            """;

        GatewayChatRequest request = GatewayChatRequest.builder()
                .model(aiProperties.model())
                .messages(List.of(
                        GatewayChatRequest.systemMessage(systemPrompt), GatewayChatRequest.userMessage(message)))
                .temperature(0.0)
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

    private void checkSpam(Long userId, String message) {
        long now = System.currentTimeMillis();
        LastChatInfo last = chatHistory.get(userId);

        if (last != null) {
            if (now - last.timestamp < SPAM_LIMIT_MS) throw new ServiceException("429-1", "채팅이 너무 빠릅니다.");
            if (last.message.equals(message)) throw new ServiceException("429-2", "동일한 메시지 반복 금지.");
        }
        chatHistory.put(userId, new LastChatInfo(message, now));
    }

    private record LastChatInfo(String message, long timestamp) {}
}
