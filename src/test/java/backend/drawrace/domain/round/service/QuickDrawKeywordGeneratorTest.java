package backend.drawrace.domain.round.service;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuickDrawKeywordGeneratorTest {

    private final QuickDrawKeywordGenerator generator = new QuickDrawKeywordGenerator();

    @Test
    @DisplayName("생성된 키워드는 QuickDraw 345종 중 하나다")
    void generateKeyword_returnsValidQuickDrawKeyword() {
        Set<String> validNames = Arrays.stream(QuickDrawKeyword.values())
                .map(QuickDrawKeyword::name)
                .collect(Collectors.toSet());

        for (int i = 0; i < 100; i++) {
            String keyword = generator.generateKeyword();
            assertThat(validNames).contains(keyword);
        }
    }

    @Test
    @DisplayName("반복 호출 시 다양한 키워드가 생성된다")
    void generateKeyword_returnsVaryingKeywords() {
        Set<String> results = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            results.add(generator.generateKeyword());
        }

        // 100번 중 모두 같은 단어가 나올 확률은 사실상 0에 가까움
        assertThat(results.size()).isGreaterThan(1);
    }
}
