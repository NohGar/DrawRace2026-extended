package backend.drawrace.domain.user.service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import backend.drawrace.domain.round.dto.gateway.GatewayChatRequest;
import backend.drawrace.domain.round.dto.gateway.GatewayChatResponse;
import backend.drawrace.global.config.AiProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GuestNicknameGenerator {

    private final AiProperties aiProperties;

    private static final List<String> FALLBACK_NICKNAMES =
            List.of("익명토끼", "낙서꾼", "그림왕", "붓질러", "색칠왕", "몽상가", "도화지", "크레파스", "물감쟁이", "연필깎이");

    public String generate() {
        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl(aiProperties.baseUrl())
                    .defaultHeader("Authorization", "Bearer " + aiProperties.apiKey())
                    .build();

            GatewayChatRequest request = GatewayChatRequest.builder()
                    .model(aiProperties.model())
                    .temperature(1.0)
                    .messages(List.of(
                            GatewayChatRequest.systemMessage(buildSystemPrompt()),
                            GatewayChatRequest.userMessage("게스트 닉네임 하나 생성해줘.")))
                    .build();

            GatewayChatResponse response = restClient
                    .post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(GatewayChatResponse.class);

            String nickname = extractNickname(response);
            if (nickname.isBlank()) {
                log.warn("GLM 닉네임 응답이 비어 있어 fallback 사용");
                return getFallback();
            }
            return nickname;
        } catch (Exception e) {
            log.warn("GLM 닉네임 생성 실패, fallback 사용", e);
            return getFallback();
        }
    }

    private String buildSystemPrompt() {
        return """
                너는 그림 그리기 게임의 게스트 닉네임 생성기다.
                조건:
                - 한국어로 2~6글자
                - 귀엽거나 재미있는 느낌의 명사 또는 형용사+명사 조합
                - 욕설, 비방, 성적 표현 금지
                - 닉네임 1개만 반환하고 설명, 따옴표, 번호, 부가 문장 없이 출력
                """;
    }

    private String extractNickname(GatewayChatResponse response) {
        if (response == null
                || response.getChoices() == null
                || response.getChoices().isEmpty()
                || response.getChoices().get(0).getMessage() == null) {
            return "";
        }
        String content = response.getChoices().get(0).getMessage().getContent();
        if (content == null) return "";
        return content.trim()
                .replace("\"", "")
                .replace("'", "")
                .replaceAll("\\s+", "")
                .replaceAll("[^가-힣a-zA-Z0-9]", "");
    }

    private String getFallback() {
        return FALLBACK_NICKNAMES.get(ThreadLocalRandom.current().nextInt(FALLBACK_NICKNAMES.size()));
    }
}
