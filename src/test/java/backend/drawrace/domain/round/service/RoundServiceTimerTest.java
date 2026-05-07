package backend.drawrace.domain.round.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import backend.drawrace.domain.chat.service.AiChatService;
import backend.drawrace.domain.room.dto.response.GameEvent;
import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.room.repository.ParticipantRepository;
import backend.drawrace.domain.room.repository.RoomRepository;
import backend.drawrace.domain.room.service.RoomService;
import backend.drawrace.domain.round.entity.Round;
import backend.drawrace.domain.round.entity.RoundStatus;
import backend.drawrace.domain.round.repository.RoundParticipantRepository;
import backend.drawrace.domain.round.repository.RoundRepository;
import backend.drawrace.domain.round.repository.RoundSubmissionRepository;
import backend.drawrace.domain.round.validator.RoundValidator;

@ExtendWith(MockitoExtension.class)
class RoundServiceTimerTest {

    @InjectMocks
    private RoundService roundService;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private RoundValidator roundValidator;

    @Mock
    private KeywordGenerator keywordGenerator;

    @Mock
    private RoomService roomService;

    @Mock
    private RoundSubmissionRepository roundSubmissionRepository;

    @Mock
    private AiInferenceService aiInferenceService;

    @Mock
    private RoundParticipantRepository roundParticipantRepository;

    @Mock
    private org.springframework.beans.factory.ObjectProvider<AiSubmissionService> aiSubmissionServiceProvider;

    @Mock
    private org.springframework.beans.factory.ObjectProvider<AiChatService> aiChatServiceProvider;

    @Test
    @DisplayName("게임 시작 시 20초 스케줄러가 등록되어야 한다")
    void startGameTimerScheduleTest() {
        Long roomId = 1L;
        Room room = Room.builder().build();
        ReflectionTestUtils.setField(room, "id", roomId);
        ReflectionTestUtils.setField(room, "hostId", 1L);

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomIdAndIsLeftFalse(roomId)).thenReturn(List.of());
        when(keywordGenerator.generateKeyword()).thenReturn("사과");

        when(roundRepository.save(any(Round.class))).thenAnswer(i -> {
            Round r = i.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 50L);
            return r;
        });

        roundService.startGame(roomId, 1L);

        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("타이머 만료 시 TIME_OVER 이벤트가 전송되어야 한다")
    void handleTimeoutEventTest() {
        Long roundId = 50L;
        Room room = Room.builder().build();
        ReflectionTestUtils.setField(room, "id", 1L);

        Round round = Round.create(room, 1, "사과");
        ReflectionTestUtils.setField(round, "status", RoundStatus.IN_PROGRESS);

        when(roundRepository.findById(roundId)).thenReturn(Optional.of(round));

        roundService.handleRoundTimeout(roundId);

        verify(messagingTemplate).convertAndSend(eq("/sub/rooms/1"), (Object) argThat(argument -> {
            if (argument instanceof GameEvent event) {
                return "TIME_OVER".equals(event.type());
            }
            return false;
        }));
    }
}
