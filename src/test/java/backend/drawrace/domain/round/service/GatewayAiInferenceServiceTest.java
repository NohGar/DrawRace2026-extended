package backend.drawrace.domain.round.service;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import backend.drawrace.domain.round.dto.gateway.GatewayChatResponse;
import backend.drawrace.domain.round.dto.gateway.GatewayInferenceResult;
import backend.drawrace.global.config.AiProperties;
import backend.drawrace.global.exception.ServiceException;

class GatewayAiInferenceServiceTest {

    private final GatewayAiInferenceService gatewayAiInferenceService = new GatewayAiInferenceService(
            new AiProperties("http://test", "test-key", "test-model"), new ObjectMapper());

    @Test
    @DisplayName("코드블록으로 감싸진 JSON 응답에서 JSON 본문만 추출한다")
    void sanitizeContent_codeBlockWrappedJson() throws Exception {
        String content = """
                ```json
                {
                  "aiAnswer": "사과",
                  "score": 0.82
                }
                ```
                """;

        String result = invokeSanitizeContent(content);

        assertThat(result).isEqualTo("""
                {
                  "aiAnswer": "사과",
                  "score": 0.82
                }""");
    }

    @Test
    @DisplayName("설명 문장이 섞여 있어도 JSON 본문만 추출한다")
    void sanitizeContent_extractJsonBody() throws Exception {
        String content = """
                아래는 판별 결과입니다.

                {
                  "aiAnswer": "사과",
                  "score": 0.91
                }
                """;

        String result = invokeSanitizeContent(content);

        assertThat(result).isEqualTo("""
                {
                  "aiAnswer": "사과",
                  "score": 0.91
                }""");
    }

    @Test
    @DisplayName("정상 JSON 문자열을 판별 결과 객체로 변환한다")
    void parseInferenceResult_success() throws Exception {
        String content = """
                {
                  "aiAnswer": "사과",
                  "score": 0.95
                }
                """;

        GatewayInferenceResult result = invokeParseInferenceResult(content);

        assertThat(result.getAiAnswer()).isEqualTo("사과");
        assertThat(result.getScore()).isEqualTo(0.95);
    }

    @Test
    @DisplayName("JSON 형식이 아니면 파싱 예외가 발생한다")
    void parseInferenceResult_fail_invalidJson() {
        String content = "사과입니다. score는 0.9입니다.";

        assertThatThrownBy(() -> invokeParseInferenceResult(content))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("AI 응답 파싱에 실패했습니다");
    }

    @Test
    @DisplayName("aiAnswer와 score가 정상 범위면 검증을 통과한다")
    void validateInferenceResult_success() throws Exception {
        GatewayInferenceResult result = new GatewayInferenceResult();
        setField(result, "aiAnswer", "사과");
        setField(result, "score", 0.77);

        assertThatCode(() -> invokeValidateInferenceResult(result)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("aiAnswer가 비어 있으면 검증 예외가 발생한다")
    void validateInferenceResult_fail_blankAnswer() throws Exception {
        GatewayInferenceResult result = new GatewayInferenceResult();
        setField(result, "aiAnswer", "");
        setField(result, "score", 0.77);

        assertThatThrownBy(() -> invokeValidateInferenceResult(result))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("AI 응답이 올바르지 않습니다");
    }

    @Test
    @DisplayName("score가 0 미만이면 검증 예외가 발생한다")
    void validateInferenceResult_fail_negativeScore() throws Exception {
        GatewayInferenceResult result = new GatewayInferenceResult();
        setField(result, "aiAnswer", "사과");
        setField(result, "score", -0.1);

        assertThatThrownBy(() -> invokeValidateInferenceResult(result))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("AI 응답이 올바르지 않습니다");
    }

    @Test
    @DisplayName("score가 1 초과면 검증 예외가 발생한다")
    void validateInferenceResult_fail_scoreOverOne() throws Exception {
        GatewayInferenceResult result = new GatewayInferenceResult();
        setField(result, "aiAnswer", "사과");
        setField(result, "score", 1.1);

        assertThatThrownBy(() -> invokeValidateInferenceResult(result))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("AI 응답이 올바르지 않습니다");
    }

    @Test
    @DisplayName("extractContent는 정상 응답에서 content를 추출한다")
    void extractContent_success() throws Exception {
        GatewayChatResponse response = createGatewayChatResponse("""
                {
                  "aiAnswer": "사과",
                  "score": 0.95
                }
                """);

        String content = invokeExtractContent(response);

        assertThat(content).contains("\"aiAnswer\": \"사과\"");
        assertThat(content).contains("\"score\": 0.95");
    }

    @Test
    @DisplayName("응답이 null이면 content 추출 예외가 발생한다")
    void extractContent_fail_nullResponse() {
        assertThatThrownBy(() -> invokeExtractContent(null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("AI 응답이 올바르지 않습니다");
    }

    @Test
    @DisplayName("choices가 없으면 content 추출 예외가 발생한다")
    void extractContent_fail_emptyChoices() {
        GatewayChatResponse response = new GatewayChatResponse();

        assertThatThrownBy(() -> invokeExtractContent(response))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("AI 응답이 올바르지 않습니다");
    }

    private String invokeSanitizeContent(String content) throws Exception {
        Method method = GatewayAiInferenceService.class.getDeclaredMethod("sanitizeContent", String.class);
        method.setAccessible(true);
        return (String) method.invoke(gatewayAiInferenceService, content);
    }

    private GatewayInferenceResult invokeParseInferenceResult(String content) throws Exception {
        Method method = GatewayAiInferenceService.class.getDeclaredMethod("parseInferenceResult", String.class);
        method.setAccessible(true);

        try {
            return (GatewayInferenceResult) method.invoke(gatewayAiInferenceService, content);
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private void invokeValidateInferenceResult(GatewayInferenceResult result) throws Exception {
        Method method = GatewayAiInferenceService.class.getDeclaredMethod(
                "validateInferenceResult", GatewayInferenceResult.class);
        method.setAccessible(true);

        try {
            method.invoke(gatewayAiInferenceService, result);
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private String invokeExtractContent(GatewayChatResponse response) throws Exception {
        Method method = GatewayAiInferenceService.class.getDeclaredMethod("extractContent", GatewayChatResponse.class);
        method.setAccessible(true);

        try {
            return (String) method.invoke(gatewayAiInferenceService, response);
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private GatewayChatResponse createGatewayChatResponse(String content) throws Exception {
        GatewayChatResponse response = new GatewayChatResponse();
        GatewayChatResponse.Choice choice = new GatewayChatResponse.Choice();
        GatewayChatResponse.Message message = new GatewayChatResponse.Message();

        setField(message, "content", content);
        setField(choice, "message", message);
        setField(response, "choices", java.util.List.of(choice));

        return response;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
