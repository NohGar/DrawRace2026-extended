package backend.drawrace.global.config;

import java.io.IOException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.boot.test.context.TestConfiguration;

import redis.embedded.RedisServer;

@TestConfiguration
public class TestRedisConfig {

    private RedisServer redisServer;

    public TestRedisConfig() throws IOException {
        // 표준 레디스 포트인 6379에서 서버를 시작해
        this.redisServer = new RedisServer(6379);
    }

    @PostConstruct
    public void startRedis() {
        try {
            redisServer.start();
        } catch (Exception e) {
            // 이미 포트가 사용 중일 경우를 대비한 예외 처리
        }
    }

    @PreDestroy
    public void stopRedis() throws IOException {
        if (redisServer != null) {
            redisServer.stop();
        }
    }
}
