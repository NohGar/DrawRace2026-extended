package backend.drawrace.domain.chat.service;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Service;

import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EmbeddingService {
    private Predictor<String, float[]> predictor;

    @PostConstruct
    public void init() throws Exception {

        Criteria<String, float[]> criteria = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optModelUrls(
                        "djl://ai.djl.huggingface.pytorch/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2")
                .optEngine("OnnxRuntime")
                .optEngine("PyTorch")
                .build();

        ZooModel<String, float[]> model = criteria.loadModel();
        this.predictor = model.newPredictor();
        log.info("임베딩 AI 모델 로드 완료!");
    }

    public float[] embed(String text) {
        try {
            return predictor.predict(text);
        } catch (Exception e) {
            log.error("임베딩 생성 실패: {}", e.getMessage());
            return new float[0];
        }
    }

    // 두 문장의 유사도 계산 (코사인 유사도)
    public double calculateSimilarity(float[] vectorA, float[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
