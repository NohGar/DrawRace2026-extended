package backend.drawrace.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public record StorageProperties(Local local) {
    public record Local(String uploadDir, String baseUrl) {}
}