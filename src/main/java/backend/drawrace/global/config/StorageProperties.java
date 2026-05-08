package backend.drawrace.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public record StorageProperties(Local local, S3 s3) {
    public record Local(String uploadDir, String baseUrl) {}
    public record S3(String bucket, String region, String baseUrl) {}
}
