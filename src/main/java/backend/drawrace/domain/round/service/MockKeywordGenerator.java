package backend.drawrace.domain.round.service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ai.mode", havingValue = "mock", matchIfMissing = true)
public class MockKeywordGenerator implements KeywordGenerator {

    private final List<String> keywords = List.of("사과", "자동차", "고양이", "비행기", "의자");

    @Override
    public String generateKeyword() {
        int index = ThreadLocalRandom.current().nextInt(keywords.size());
        return keywords.get(index);
    }
}
