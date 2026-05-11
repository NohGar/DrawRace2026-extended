package backend.drawrace.domain.round.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import backend.drawrace.domain.round.dto.DrawingData;
import backend.drawrace.global.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Service
@ConditionalOnProperty(name = "ai.mode", havingValue = "quickdraw")
@RequiredArgsConstructor
public class QuickDrawAiDrawingService implements AiDrawingService {

    private static final Logger log = LoggerFactory.getLogger(QuickDrawAiDrawingService.class);
    private static final Random RANDOM = new Random();

    private final ObjectMapper objectMapper;

    @Override
    public DrawingData generateDrawing(String keyword) {
        QuickDrawKeyword mapping = QuickDrawKeyword.from(keyword);
        String resourcePath = "quickdraw/" + mapping.getFilename() + ".ndjson";

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new ServiceException("500-1", "QuickDraw 파일을 찾을 수 없습니다: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line = pickRandomLine(reader);
                if (line == null) {
                    throw new ServiceException("500-1", "QuickDraw 데이터가 비어 있습니다.");
                }
                return parseDrawingData(line);
            }
        } catch (IOException e) {
            throw new ServiceException("500-1", "QuickDraw 파일 읽기에 실패했습니다.");
        }
    }

    // 파일 전체를 메모리에 올리지 않는 reservoir sampling
    private String pickRandomLine(BufferedReader reader) throws IOException {
        String selected = null;
        int count = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            count++;
            if (RANDOM.nextInt(count) == 0) {
                selected = line;
            }
        }
        return selected;
    }

    private DrawingData parseDrawingData(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            List<List<List<Integer>>> strokes =
                    objectMapper.convertValue(root.get("drawing"), new TypeReference<>() {});
            return new DrawingData(strokes);
        } catch (Exception e) {
            log.error("QuickDraw 데이터 파싱 실패: {}", e.getMessage());
            throw new ServiceException("500-1", "QuickDraw 데이터 파싱에 실패했습니다.");
        }
    }
}
