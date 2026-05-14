package backend.drawrace.domain.chat.service;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import backend.drawrace.domain.chat.entity.AiChatMessage;
import backend.drawrace.domain.chat.entity.AiChatMessage.MessageType;
import backend.drawrace.domain.chat.repository.AiChatMessageRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AiChatMessageInitializer implements ApplicationRunner {

    private final AiChatMessageRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) return;

        List<AiChatMessage> messages = List.of(
                AiChatMessage.of(MessageType.JOIN, "안녕하세요~ 잘 부탁드려요! 😊"),
                AiChatMessage.of(MessageType.JOIN, "드디어 입장! 같이 즐겨봐요 🎮"),
                AiChatMessage.of(MessageType.JOIN, "오오 다들 그림 잘 그리시나요? 💪"),
                AiChatMessage.of(MessageType.JOIN, "AI가 왔다! 긴장하지 마세요 ㅎㅎ"),
                AiChatMessage.of(MessageType.JOIN, "반갑습니다~ 실력 한번 겨뤄봐요! ✨"),
                AiChatMessage.of(MessageType.JOIN, "늦지 않았죠? 열심히 해볼게요 🖌️"),
                AiChatMessage.of(MessageType.SUBMIT, "제출 완료! 어떻게 됐을지 두근두근 🤞"),
                AiChatMessage.of(MessageType.SUBMIT, "다 그렸어요! 맞춰봐요 ㅎㅎ"),
                AiChatMessage.of(MessageType.SUBMIT, "이번엔 좀 잘 그린 것 같은데? 😏"),
                AiChatMessage.of(MessageType.SUBMIT, "흠... 잘 나왔나 모르겠네요 😅"),
                AiChatMessage.of(MessageType.SUBMIT, "자신있어요! 이번엔 내 턴 💪"),
                AiChatMessage.of(MessageType.SUBMIT, "오늘 그림 실력이 좋은 것 같아요 🎨"),
                AiChatMessage.of(MessageType.SUBMIT, "제출! 결과가 궁금하다 👀"));

        repository.saveAll(messages);
    }
}
