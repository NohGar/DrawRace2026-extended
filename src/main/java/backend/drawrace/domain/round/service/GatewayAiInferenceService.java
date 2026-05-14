package backend.drawrace.domain.round.service;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import backend.drawrace.domain.round.dto.AiInferenceResponse;
import backend.drawrace.domain.round.dto.gateway.GatewayChatRequest;
import backend.drawrace.domain.round.dto.gateway.GatewayChatResponse;
import backend.drawrace.domain.round.dto.gateway.GatewayChatResponse.Choice;
import backend.drawrace.domain.round.dto.gateway.GatewayChatResponse.Message;
import backend.drawrace.domain.round.dto.gateway.GatewayInferenceResult;
import backend.drawrace.global.config.AiProperties;
import backend.drawrace.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ConditionalOnExpression("'${ai.mode:}' == 'gateway' or '${ai.mode:}' == 'quickdraw'")
@RequiredArgsConstructor
public class GatewayAiInferenceService implements AiInferenceService {

    private static final int LOG_PREVIEW_MAX = 240;

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    /**
     * AI Gateway를 호출해 그림 판별을 수행한다.
     * - 1차 실패 시 1회 재시도한다.
     * - 최종 실패 시 예외를 던져 제출을 실패 처리한다.
     */
    @Override
    public AiInferenceResponse infer(String imageData, String keyword) {
        try {
            return requestInference(imageData, keyword);
        } catch (Exception first) {
            log.warn("AI 판별 1차 실패. 재시도합니다. keyword={}", keyword, first);

            try {
                return requestInference(imageData, keyword);
            } catch (Exception second) {
                log.error("AI 판별 최종 실패. keyword={}", keyword, second);
                throw new ServiceException("500-1", "AI 판별에 실패했습니다. 다시 시도해주세요.");
            }
        }
    }

    /**
     * 실제 Gateway 요청과 응답 파싱을 수행한다.
     */
    private AiInferenceResponse requestInference(String imageData, String keyword) {
        RestClient restClient = RestClient.builder()
                .baseUrl(aiProperties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + aiProperties.apiKey())
                .build();

        GatewayChatRequest request = GatewayChatRequest.builder()
                .model(aiProperties.model())
                .temperature(0.2)
                .maxTokens(aiProperties.maxCompletionTokens())
                .messages(List.of(
                        GatewayChatRequest.systemMessage(buildSystemPrompt()),
                        GatewayChatRequest.userMessage(List.of(
                                GatewayChatRequest.textContent(buildUserPrompt(keyword)),
                                GatewayChatRequest.imageContent(imageData)))))
                .build();

        int imageChars = imageData != null ? imageData.length() : 0;
        GatewayChatResponse response = restClient
                .post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(GatewayChatResponse.class);

        String rawContent = extractContentOrNull(response);
        if (rawContent == null) {
            logGatewayResponseShape(keyword, imageChars, response, "message.content 없음 또는 choices 비어 있음");
            throw new ServiceException("500-1", "AI 응답이 올바르지 않습니다.");
        }

        String content = sanitizeContent(rawContent);
        if (content.isBlank()) {
            logGatewayResponseShape(keyword, imageChars, response, "sanitize 후 본문 비어 있음 rawLen=" + rawContent.length());
            throw new ServiceException("500-1", "AI 응답이 올바르지 않습니다.");
        }

        GatewayInferenceResult result = parseInferenceResult(content, keyword, imageChars);
        validateInferenceResult(result, keyword, imageChars);

        return new AiInferenceResponse(result.getAiAnswer(), result.getScore());
    }

    /**
     * 그림 판별 AI의 역할과 출력 규칙을 지정한다.
     */
    private String buildSystemPrompt() {
        return """
                너는 그림 판별 AI다.
                사용자가 제출한 이미지를 보고 무엇을 그렸는지 판단한다.

                정답 키워드가 함께 주어지더라도 aiAnswer에는 정답 키워드를 그대로 복사하지 말고,
                이미지에서 실제로 보이는 대상을 적어야 한다.

                점수는 후하게 주지 말고 이미지 자체의 명확성을 기준으로 엄격하게 평가한다.
                반드시 JSON 형식으로만 응답해야 하며, 설명 문장, 마크다운, 코드블록 없이 JSON만 반환한다.

                응답 형식:
                {
                  "aiAnswer": "이미지에서 추론한 단어",
                  "score": 0.0
                }

                score는 0.0 이상 1.0 이하의 실수로 반환한다.
                """;
    }

    /**
     * 판별 대상 그림과 정답 키워드 조건을 전달한다.
     */
    private String buildUserPrompt(String keyword) {
        return """
                첨부된 이미지를 먼저 보고, 사용자가 무엇을 그렸는지 추론하라.

                정답 키워드는 "%s" 이다.
                하지만 aiAnswer에는 정답 키워드를 그대로 복사하지 말고,
                이미지에서 실제로 보이는 대상을 적어라.

                그다음 이미지가 정답 키워드와 얼마나 일치하는지 score를 0.0~1.0 사이로 평가하라.

                점수 기준:
                - 0.95~1.00: 누가 봐도 정답 키워드이며, 핵심 특징이 여러 개 명확하고 완성도가 높음
                - 0.80~0.94: 정답으로 볼 수 있으며, 핵심 특징이 명확하지만 일부 특징이 부족하거나 단순함
                - 0.60~0.79: 정답과 비슷하지만 애매하거나 핵심 특징 일부만 표현됨
                - 0.40~0.59: 정답과 관련된 특징이 조금 있으나 다른 대상으로도 볼 수 있음
                - 0.20~0.39: 대상 표현 의도는 약하게 보이지만 정답으로 보기 어려움
                - 0.00~0.19: 빈 그림, 무작위 낙서, 식별 불가, 정답과 무관한 그림

                추가 평가 규칙:
                - 정답 키워드를 알고 있더라도 이미지 자체에서 확인되지 않으면 높은 점수를 주지 마라.
                - aiAnswer가 정답 키워드와 같더라도 그림의 완성도가 낮으면 점수를 낮게 줘라.
                - 핵심 특징이 하나만 표현된 단순한 그림은 최대 0.7까지만 준다.
                - 대상처럼 보이긴 하지만 선 몇 개로만 매우 단순하게 표현된 그림은 최대 0.5까지만 준다.
                - 무작위 선, 낙서, 빈 그림처럼 특정 대상을 식별하기 어려운 경우는 최대 0.3까지만 준다.
                - 정답이라고 확신하기 어렵다면 0.6 이하로 평가한다.

                반드시 아래 JSON 형식만 반환하라.
                {
                  "aiAnswer": "이미지에서 추론한 단어",
                  "score": 0.0
                }
                """.formatted(keyword);
    }

    /**
     * Gateway 응답에서 assistant content를 꺼낸다. 구조가 맞지 않거나 content가 null이면 null.
     */
    private String extractContentOrNull(GatewayChatResponse response) {
        if (response == null
                || response.getChoices() == null
                || response.getChoices().isEmpty()) {
            return null;
        }
        Choice choice = response.getChoices().get(0);
        if (choice == null || choice.getMessage() == null) {
            return null;
        }
        return choice.getMessage().getContent();
    }

    /** 이미지·토큰은 남기지 않고 응답 형태만 요약한다. */
    private void logGatewayResponseShape(String keyword, int imageChars, GatewayChatResponse response, String reason) {
        if (response == null) {
            log.warn("[AI-GATEWAY] keyword={} imageChars={} reason={} response=null", keyword, imageChars, reason);
            return;
        }
        List<Choice> choices = response.getChoices();
        int choiceN = choices == null ? 0 : choices.size();
        String finishReason = null;
        String role = null;
        int contentLen = -1;
        boolean blankContent = false;
        boolean hasRefusal = false;
        boolean hasToolCalls = false;
        if (choiceN > 0 && choices.get(0) != null) {
            Choice c = choices.get(0);
            finishReason = c.getFinishReason();
            Message m = c.getMessage();
            if (m != null) {
                role = m.getRole();
                String ct = m.getContent();
                if (ct == null) {
                    contentLen = -1;
                } else {
                    contentLen = ct.length();
                    blankContent = ct.isBlank();
                }
                hasRefusal = m.getRefusal() != null && !m.getRefusal().isBlank();
                hasToolCalls = m.getToolCalls() != null
                        && !m.getToolCalls().isNull()
                        && m.getToolCalls().isArray()
                        && m.getToolCalls().size() > 0;
            }
        }
        log.warn(
                "[AI-GATEWAY] keyword={} model={} imageChars={} reason={} choices={} finishReason={} role={} contentLen={} blankContent={} hasRefusal={} hasToolCalls={} maxCompletionTokens={}",
                keyword,
                aiProperties.model(),
                imageChars,
                reason,
                choiceN,
                finishReason,
                role,
                contentLen,
                blankContent,
                hasRefusal,
                hasToolCalls,
                aiProperties.maxCompletionTokens());
        if ("length".equalsIgnoreCase(finishReason) && contentLen <= 0) {
            log.warn(
                    "[AI-GATEWAY] 힌트: finish_reason=length 이고 assistant 본문이 비었습니다. 비전 입력 토큰으로 출력 한도가 소진됐을 가능성이 큽니다. "
                            + "ai.gateway.max-completion-tokens(또는 AI_MAX_COMPLETION_TOKENS)를 더 올리거나, 프론트에서 제출 이미지 해상도·용량을 줄여 보세요.");
        }
    }

    private static String previewForLog(String s) {
        if (s == null) {
            return "";
        }
        String oneLine = s.replace('\n', ' ').replace('\r', ' ');
        if (oneLine.length() <= LOG_PREVIEW_MAX) {
            return oneLine;
        }
        return oneLine.substring(0, LOG_PREVIEW_MAX) + "…(총 " + s.length() + "자)";
    }

    /**
     * AI 응답에서 코드블록이나 불필요한 텍스트를 제거해 JSON 본문만 남긴다.
     */
    private String sanitizeContent(String content) {
        String cleaned = content.replace("```json", "").replace("```", "").trim();

        int start = cleaned.indexOf("{");
        int end = cleaned.lastIndexOf("}");

        if (start >= 0 && end >= start) {
            return cleaned.substring(start, end + 1);
        }

        return cleaned;
    }

    /**
     * 정제된 JSON 문자열을 판별 결과 객체로 변환한다.
     */
    private GatewayInferenceResult parseInferenceResult(String content, String keyword, int imageChars) {
        try {
            return objectMapper.readValue(content, GatewayInferenceResult.class);
        } catch (JsonProcessingException e) {
            log.error(
                    "[AI-GATEWAY] JSON 파싱 실패 keyword={} imageChars={} sanitizedLen={} preview={}",
                    keyword,
                    imageChars,
                    content != null ? content.length() : 0,
                    previewForLog(content),
                    e);
            throw new ServiceException("500-1", "AI 응답 파싱에 실패했습니다.");
        }
    }

    /**
     * 판별 결과의 필수 값과 score 범위를 검증한다.
     */
    private void validateInferenceResult(GatewayInferenceResult result, String keyword, int imageChars) {
        if (result == null
                || result.getAiAnswer() == null
                || result.getAiAnswer().isBlank()
                || result.getScore() < 0.0
                || result.getScore() > 1.0) {
            log.warn(
                    "[AI-GATEWAY] 필드 검증 실패 keyword={} imageChars={} aiAnswerBlank={} score={}",
                    keyword,
                    imageChars,
                    result == null
                            || result.getAiAnswer() == null
                            || result.getAiAnswer().isBlank(),
                    result != null ? result.getScore() : null);
            throw new ServiceException("500-1", "AI 응답이 올바르지 않습니다.");
        }
    }
}
