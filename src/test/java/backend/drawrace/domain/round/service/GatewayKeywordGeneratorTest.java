package backend.drawrace.domain.round.service;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import backend.drawrace.global.config.AiProperties;

class GatewayKeywordGeneratorTest {

    private final GatewayKeywordGenerator gatewayKeywordGenerator =
            new GatewayKeywordGenerator(new AiProperties("http://test", "test-key", "test-model"));

    @Test
    @DisplayName("코드블록으로 감싸진 응답에서 제시어만 추출한다")
    void sanitizeKeyword_codeBlockWrapped() throws Exception {
        String content = """
                ```text
                사과
                ```
                """;

        String result = invokeSanitizeKeyword(content);

        assertThat(result).isEqualTo("사과");
    }

    @Test
    @DisplayName("따옴표가 포함된 응답에서 제시어만 추출한다")
    void sanitizeKeyword_withQuotes() throws Exception {
        String content = "\"사과\"";

        String result = invokeSanitizeKeyword(content);

        assertThat(result).isEqualTo("사과");
    }

    @Test
    @DisplayName("접두 문구가 포함된 응답에서 제시어만 추출한다")
    void sanitizeKeyword_withPrefix() throws Exception {
        String content = "제시어: 사과";

        String result = invokeSanitizeKeyword(content);

        assertThat(result).isEqualTo("사과");
    }

    @Test
    @DisplayName("번호가 붙은 응답에서 제시어만 추출한다")
    void sanitizeKeyword_withNumbering() throws Exception {
        String content = "1. 사과";

        String result = invokeSanitizeKeyword(content);

        assertThat(result).isEqualTo("사과");
    }

    @Test
    @DisplayName("여러 줄 응답이면 첫 줄만 사용한다")
    void sanitizeKeyword_multiline() throws Exception {
        String content = """
                사과
                설명입니다
                """;

        String result = invokeSanitizeKeyword(content);

        assertThat(result).isEqualTo("사과");
    }

    @Test
    @DisplayName("공백이 포함된 응답은 공백 제거 후 반환한다")
    void sanitizeKeyword_removeWhitespace() throws Exception {
        String content = "  사 과  ";

        String result = invokeSanitizeKeyword(content);

        assertThat(result).isEqualTo("사과");
    }

    @Test
    @DisplayName("응답이 null이면 빈 문자열을 반환한다")
    void extractContent_nullResponse_returnsEmptyString() throws Exception {
        String result = invokeExtractContent(null);

        assertThat(result).isEmpty();
    }

    private String invokeSanitizeKeyword(String content) throws Exception {
        Method method = GatewayKeywordGenerator.class.getDeclaredMethod("sanitizeKeyword", String.class);
        method.setAccessible(true);
        return (String) method.invoke(gatewayKeywordGenerator, content);
    }

    private String invokeExtractContent(Object response) throws Exception {
        Method method = GatewayKeywordGenerator.class.getDeclaredMethod(
                "extractContent", backend.drawrace.domain.round.dto.gateway.GatewayChatResponse.class);
        method.setAccessible(true);
        return (String) method.invoke(gatewayKeywordGenerator, response);
    }
}
