package backend.drawrace.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.gateway")
public record AiProperties(String baseUrl, String apiKey, String model) {}
