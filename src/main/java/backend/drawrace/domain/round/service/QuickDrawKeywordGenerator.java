package backend.drawrace.domain.round.service;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ai.mode=quickdraw 일 때 활성화되는 키워드 생성기.
 * AI가 그릴 수 있는 QuickDraw 345종 중 랜덤으로 제시어를 선택한다.
 */
@Component
@ConditionalOnProperty(name = "ai.mode", havingValue = "quickdraw")
public class QuickDrawKeywordGenerator implements KeywordGenerator {

    @Override
    public String generateKeyword() {
        QuickDrawKeyword[] values = QuickDrawKeyword.values();
        return values[ThreadLocalRandom.current().nextInt(values.length)].name();
    }
}
