package backend.drawrace.domain.round.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import backend.drawrace.domain.round.dto.DrawingData;
import backend.drawrace.global.exception.ServiceException;

class QuickDrawAiDrawingServiceTest {

    private QuickDrawAiDrawingService service;

    @BeforeEach
    void setUp() {
        service = new QuickDrawAiDrawingService(new ObjectMapper());
    }

    @Test
    @DisplayName("유효한 키워드로 그림 데이터를 생성한다")
    void generateDrawing_validKeyword_returnsDrawingData() {
        DrawingData result = service.generateDrawing("사과");

        assertThat(result).isNotNull();
        assertThat(result.strokes()).isNotEmpty();
    }

    @Test
    @DisplayName("지원하지 않는 키워드는 400 예외를 던진다")
    void generateDrawing_unknownKeyword_throws400() {
        assertThatThrownBy(() -> service.generateDrawing("존재하지않는키워드"))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "400-1");
    }

    @Test
    @DisplayName("그림 데이터의 각 획은 x, y 좌표 리스트를 포함한다")
    void generateDrawing_strokesHaveCoordinates() {
        DrawingData result = service.generateDrawing("고양이");

        result.strokes().forEach(stroke -> assertThat(stroke).hasSize(2));
    }

    @Test
    @DisplayName("같은 키워드로 호출해도 매번 결과를 반환한다")
    void generateDrawing_repeatedCalls_alwaysReturnsData() {
        for (int i = 0; i < 5; i++) {
            DrawingData result = service.generateDrawing("자동차");
            assertThat(result.strokes()).isNotEmpty();
        }
    }
}
