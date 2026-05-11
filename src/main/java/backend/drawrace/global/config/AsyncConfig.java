package backend.drawrace.global.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); // 평소에 5개의 스레드 유지
        executor.setMaxPoolSize(10); // 채팅이 몰리면 최대 10개까지 확장
        executor.setQueueCapacity(500); // 10개가 다 차면 500개까지 줄 세움
        executor.setThreadNamePrefix("ChatAsync-"); // 로그 찍힐 때 이름 (디버깅용)
        executor.initialize();
        return executor;
    }
}
