package backend.drawrace.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import backend.drawrace.domain.round.service.AiInferenceService;
import backend.drawrace.domain.round.service.ConcurrentLimitedAiInferenceService;
import backend.drawrace.domain.round.service.GatewayAiInferenceService;

@Configuration
@ConditionalOnBean(GatewayAiInferenceService.class)
public class AiInferenceConcurrencyConfiguration {

    @Bean
    @Primary
    public AiInferenceService boundedAiInferenceService(
            GatewayAiInferenceService gatewayAiInferenceService,
            @Value("${ai.inference.max-concurrent:4}") int maxConcurrent,
            @Value("${ai.inference.acquire-timeout-seconds:120}") long acquireTimeoutSeconds) {
        return new ConcurrentLimitedAiInferenceService(gatewayAiInferenceService, maxConcurrent, acquireTimeoutSeconds);
    }
}
