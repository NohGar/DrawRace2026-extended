package backend.drawrace.domain.chat.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EmbeddingRealScoreTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Test
    void measureRealSimilarity() {
        float[] target = embeddingService.embed("그림 진짜 못 그린다");

        List<String> tests = List.of("그림 실력 실화냐", "진짜 못 그리네", "손가락 문제 있음?");

        System.out.println("\n=== 실제 AI 유사도 측정 결과 ===");
        for (String text : tests) {
            float[] testVec = embeddingService.embed(text);
            double score = embeddingService.calculateSimilarity(target, testVec);
            System.out.printf("문장: [%s] -> 유사도: %.4f%n", text, score);
        }
    }
}
