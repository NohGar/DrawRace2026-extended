package backend.drawrace.domain.round.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import backend.drawrace.global.exception.ServiceException;

class QuickDrawKeywordTest {

    @Test
    @DisplayName("한국어 키워드로 영어 파일명을 조회한다")
    void from_validKeyword_returnsCorrectFilename() {
        assertThat(QuickDrawKeyword.from("사과").getFilename()).isEqualTo("apple");
        assertThat(QuickDrawKeyword.from("고양이").getFilename()).isEqualTo("cat");
        assertThat(QuickDrawKeyword.from("비행기").getFilename()).isEqualTo("airplane");
    }

    @Test
    @DisplayName("미등록 키워드는 400 예외를 던진다")
    void from_unknownKeyword_throws400() {
        assertThatThrownBy(() -> QuickDrawKeyword.from("없는키워드"))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("resultCode", "400-1");
    }

    @Test
    @DisplayName("모든 키워드의 파일명이 비어 있지 않다")
    void allKeywords_haveNonBlankFilename() {
        for (QuickDrawKeyword keyword : QuickDrawKeyword.values()) {
            assertThat(keyword.getFilename())
                    .as("키워드 %s의 파일명이 비어 있습니다", keyword.name())
                    .isNotBlank();
        }
    }
}
