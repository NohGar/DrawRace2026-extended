package backend.drawrace.domain.round.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;

import backend.drawrace.domain.chat.dto.ChatMessageDto;
import backend.drawrace.domain.room.entity.Participant;
import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.room.repository.ParticipantRepository;
import backend.drawrace.domain.room.service.RankingService;
import backend.drawrace.domain.room.service.RoomService;
import backend.drawrace.domain.round.dto.AiInferenceResponse;
import backend.drawrace.domain.round.dto.SubmitDrawingRequest;
import backend.drawrace.domain.round.dto.SubmitDrawingResponse;
import backend.drawrace.domain.round.entity.Round;
import backend.drawrace.domain.round.entity.RoundSubmission;
import backend.drawrace.domain.round.repository.RoundParticipantRepository;
import backend.drawrace.domain.round.repository.RoundRepository;
import backend.drawrace.domain.round.repository.RoundSubmissionRepository;
import backend.drawrace.domain.round.validator.RoundValidator;
import backend.drawrace.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class RoundServiceWebSocketTest {

    @InjectMocks
    private RoundService roundService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private RoundValidator roundValidator;

    @Mock
    private RoundSubmissionRepository roundSubmissionRepository;

    @Mock
    private RoundParticipantRepository roundParticipantRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private KeywordGenerator keywordGenerator;

    @Mock
    private AiInferenceService aiInferenceService;

    @Mock
    private RankingService rankingService;

    @Mock
    private ObjectProvider<AiSubmissionService> aiSubmissionServiceProvider;

    @Mock
    private ObjectProvider<backend.drawrace.domain.chat.service.AiChatService> aiChatServiceProvider;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private RoomService roomService;

    @Test
    @DisplayName("라운드 종료 시 웹소켓으로 결과가 전송되는지 확인한다")
    void shouldBroadcastWhenRoundFinished() {
        Long roundId = 1L;
        Long userId = 1L;
        Long roomId = 10L;
        Long participantId = 100L;

        Room room = mock(Room.class);
        lenient().when(room.getId()).thenReturn(roomId);
        lenient().when(room.getTotalRounds()).thenReturn((short) 3);

        Round round = mock(Round.class);
        lenient().when(round.getId()).thenReturn(roundId);
        lenient().when(round.getRoom()).thenReturn(room);
        lenient().when(round.getRoundNumber()).thenReturn(1);
        lenient().when(round.getKeyword()).thenReturn("강아지");
        lenient().when(roundRepository.findById(roundId)).thenReturn(Optional.of(round));
        lenient().when(roundRepository.findByIdForUpdate(roundId)).thenReturn(Optional.of(round));

        Participant participant = mock(Participant.class);
        lenient().when(participant.getId()).thenReturn(participantId);
        lenient().when(participant.isLeft()).thenReturn(false);
        lenient()
                .when(participantRepository.findByIdAndRoomId(anyLong(), anyLong()))
                .thenReturn(Optional.of(participant));

        User user = mock(User.class);
        lenient().when(user.getNickname()).thenReturn("테스트유저");
        lenient().when(user.isAi()).thenReturn(false);
        lenient().when(participant.getUserId()).thenReturn(user);

        SubmitDrawingRequest request = new SubmitDrawingRequest(participantId, "image-data");

        lenient().when(keywordGenerator.generateKeyword()).thenReturn("강아지");
        lenient().when(aiInferenceService.infer(anyString(), any())).thenReturn(new AiInferenceResponse("정답", 0.90));

        // 전원 제출 상황 시뮬레이션
        lenient().when(roundSubmissionRepository.countActiveByRoundId(roundId)).thenReturn(1L);
        lenient().when(roundParticipantRepository.countActiveByRoundId(roundId)).thenReturn(1L);

        Round nextRound = mock(Round.class);
        lenient().when(nextRound.getId()).thenReturn(2L);
        lenient().when(nextRound.getKeyword()).thenReturn("강아지");
        lenient().when(roundRepository.save(any(Round.class))).thenReturn(nextRound);

        RoundSubmission submission = mock(RoundSubmission.class);
        lenient().when(submission.getParticipant()).thenReturn(participant);
        lenient().when(submission.getScore()).thenReturn(0.90);
        lenient().when(roundSubmissionRepository.findByRoundId(roundId)).thenReturn(List.of(submission));

        lenient().when(participantRepository.findByRoomIdAndIsLeftFalse(roomId)).thenReturn(List.of(participant));

        roundService.submitDrawing(roundId, userId, request);

        // 실제로 웹소켓 발송이 일어났는지 검증
        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/sub/rooms/" + roomId), any(SubmitDrawingResponse.class));
    }

    @Test
    @DisplayName("라운드 종료 시 승리자 공지가 채팅창으로 발송된다")
    void shouldSendWinnerNoticeWhenRoundFinished() {
        // 1. Given: 라운드 종료 상황 세팅
        Long roundId = 1L;
        Long userId = 1L;
        Long roomId = 10L;
        Long participantId = 100L;

        Room room = mock(Room.class);
        lenient().when(room.getId()).thenReturn(roomId);
        lenient().when(room.getTotalRounds()).thenReturn((short) 3);

        Round round = mock(Round.class);
        lenient().when(round.getId()).thenReturn(roundId);
        lenient().when(round.getRoom()).thenReturn(room);
        lenient().when(round.getRoundNumber()).thenReturn(1);
        lenient().when(round.getKeyword()).thenReturn("사과");
        lenient().when(roundRepository.findById(roundId)).thenReturn(Optional.of(round));
        lenient().when(roundRepository.findByIdForUpdate(roundId)).thenReturn(Optional.of(round));

        Participant participant = mock(Participant.class);
        lenient().when(participant.getId()).thenReturn(participantId);
        lenient().when(participant.isLeft()).thenReturn(false);

        User user = mock(User.class);
        lenient().when(user.getNickname()).thenReturn("승리자유저A");
        lenient().when(user.isAi()).thenReturn(false);
        lenient().when(participant.getUserId()).thenReturn(user);

        lenient()
                .when(participantRepository.findByIdAndRoomId(anyLong(), anyLong()))
                .thenReturn(Optional.of(participant));

        SubmitDrawingRequest request = new SubmitDrawingRequest(participantId, "image-data");

        // 전원 제출 완료 상황 모킹
        given(roundSubmissionRepository.countActiveByRoundId(roundId)).willReturn(1L);
        given(roundParticipantRepository.countActiveByRoundId(roundId)).willReturn(1L);

        // 승자 선정을 위한 가짜 제출 기록
        RoundSubmission submission = mock(RoundSubmission.class);
        lenient().when(submission.getParticipant()).thenReturn(participant);
        lenient().when(submission.getScore()).thenReturn(0.95);
        lenient().when(roundSubmissionRepository.findByRoundId(roundId)).thenReturn(List.of(submission));

        // AI가 정답을 맞힌 상황
        given(aiInferenceService.infer(anyString(), any())).willReturn(new AiInferenceResponse("정답", 0.95));

        // 다음 라운드 생성을 위한 모킹
        Round nextRound = mock(Round.class);
        lenient().when(nextRound.getId()).thenReturn(2L);
        lenient().when(nextRound.getKeyword()).thenReturn("사과");
        lenient().when(roundRepository.save(any())).thenReturn(nextRound);
        lenient().when(keywordGenerator.generateKeyword()).thenReturn("사과");
        lenient().when(participantRepository.findByRoomIdAndIsLeftFalse(roomId)).thenReturn(List.of(participant));

        // 그림 제출 (이 호출이 라운드 종료를 트리거함)
        roundService.submitDrawing(roundId, userId, request);

        // 검증
        // [검증] /chat 채널로 WINNER 타입의 메시지가 갔는가?
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(
                        eq("/sub/rooms/" + roomId + "/chat"),
                        argThat((ChatMessageDto dto) -> dto.getType() == ChatMessageDto.MessageType.WINNER
                                && dto.getMessage().contains("라운드 승리자: 승리자유저A님!")));
    }
}
