package backend.drawrace.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient(AiProperties aiProperties) {
        return RestClient.builder()
                .baseUrl(aiProperties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + aiProperties.apiKey()) // API 키 설정
                .build();
    }
}
