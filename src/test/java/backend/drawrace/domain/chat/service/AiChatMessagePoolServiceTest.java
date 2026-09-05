package backend.drawrace.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import backend.drawrace.domain.chat.entity.AiChatMessage;
import backend.drawrace.domain.chat.entity.AiChatMessage.MessageType;
import backend.drawrace.domain.chat.repository.AiChatMessageRepository;
import backend.drawrace.domain.chat.scheduler.AiChatMessagePoolRefillScheduler;

@SpringBootTest
@Transactional
class AiChatMessagePoolServiceTest {

    @Autowired
    AiChatMessagePoolService aiChatMessagePoolService;

    @Autowired
    AiChatMessageRepository aiChatMessageRepository;

    @MockBean
    AiChatMessageGenerator messageGenerator;

    @MockBean
    AiChatMessagePoolRefillScheduler aiChatMessagePoolRefillScheduler;

    private void saveToPool(MessageType type, String message) {
        aiChatMessageRepository.save(AiChatMessage.of(type, message));
    }

    // ===== pop() =====

    @Test
    @DisplayName("pop_풀에_메시지_있을_때_반환_및_DB_삭제")
    void pop_returns_and_removes_message_when_pool_has_entry() {
        saveToPool(MessageType.JOIN, "테스트 입장 메시지");

        String result = aiChatMessagePoolService.pop(MessageType.JOIN);

        assertThat(result).isEqualTo("테스트 입장 메시지");
        assertThat(aiChatMessageRepository.countByType(MessageType.JOIN)).isZero();
    }

    @Test
    @DisplayName("pop_풀이_비어있을_때_null_반환")
    void pop_returns_null_when_pool_is_empty() {
        String result = aiChatMessagePoolService.pop(MessageType.SUBMIT);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("pop_다른_타입의_메시지는_소비하지_않음")
    void pop_does_not_consume_other_type() {
        saveToPool(MessageType.JOIN, "입장 메시지");

        String result = aiChatMessagePoolService.pop(MessageType.SUBMIT);

        assertThat(result).isNull();
        assertThat(aiChatMessageRepository.countByType(MessageType.JOIN)).isEqualTo(1);
    }

    // ===== refill() =====

    @Test
    @DisplayName("refill_풀이_비어있을_때_타입별_20개씩_채움")
    void refill_fills_pool_to_20_per_type_when_empty() {
        AtomicInteger counter = new AtomicInteger(0);
        given(messageGenerator.generate(org.mockito.ArgumentMatchers.any()))
                .willAnswer(inv -> "메시지" + counter.incrementAndGet());

        aiChatMessagePoolService.refill();

        assertThat(aiChatMessageRepository.countByType(MessageType.JOIN)).isEqualTo(20);
        assertThat(aiChatMessageRepository.countByType(MessageType.SUBMIT)).isEqualTo(20);
    }

    @Test
    @DisplayName("refill_부족한_만큼만_채움")
    void refill_fills_only_missing_count() {
        for (int i = 1; i <= 15; i++) {
            saveToPool(MessageType.JOIN, "기존메시지" + i);
        }
        AtomicInteger counter = new AtomicInteger(0);
        given(messageGenerator.generate(org.mockito.ArgumentMatchers.any()))
                .willAnswer(inv -> "신규메시지" + counter.incrementAndGet());

        aiChatMessagePoolService.refill();

        assertThat(aiChatMessageRepository.countByType(MessageType.JOIN)).isEqualTo(20);
    }

    @Test
    @DisplayName("refill_이미_20개_이상이면_생성하지_않음")
    void refill_does_not_overfill_when_pool_already_full() {
        for (int i = 1; i <= 20; i++) {
            saveToPool(MessageType.JOIN, "메시지" + i);
            saveToPool(MessageType.SUBMIT, "메시지" + i);
        }

        aiChatMessagePoolService.refill();

        assertThat(aiChatMessageRepository.countByType(MessageType.JOIN)).isEqualTo(20);
        assertThat(aiChatMessageRepository.countByType(MessageType.SUBMIT)).isEqualTo(20);
        verify(messageGenerator, never()).generate(org.mockito.ArgumentMatchers.any());
    }
}
