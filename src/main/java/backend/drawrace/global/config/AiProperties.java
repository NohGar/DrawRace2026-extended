package backend.drawrace.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.gateway")
public record AiProperties(String baseUrl, String apiKey, String model, Integer maxCompletionTokens) {

    /**
     * 비전 요청은 입력 토큰이 커서 출력 상한(max_tokens)이 부족하면 본문이 비고 finish_reason=length 로 끝나는 경우가 있다.
     * null·너무 작은 값은 기본 4096으로 보정한다.
     */
    public AiProperties {
        if (maxCompletionTokens == null || maxCompletionTokens < 16) {
            maxCompletionTokens = 4096;
        }
    }
}
