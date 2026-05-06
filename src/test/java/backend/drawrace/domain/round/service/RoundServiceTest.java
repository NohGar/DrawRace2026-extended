package backend.drawrace.domain.round.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import backend.drawrace.domain.room.entity.Participant;
import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.room.repository.ParticipantRepository;
import backend.drawrace.domain.room.repository.RoomRepository;
import backend.drawrace.domain.round.dto.AiInferenceResponse;
import backend.drawrace.domain.round.dto.CurrentRoundResponse;
import backend.drawrace.domain.round.dto.PlayerSubmittedEvent;
import backend.drawrace.domain.round.dto.RoundStartResponse;
import backend.drawrace.domain.round.dto.RoundSubmissionResponse;
import backend.drawrace.domain.round.dto.SubmitDrawingRequest;
import backend.drawrace.domain.round.dto.SubmitDrawingResponse;
import backend.drawrace.domain.round.entity.Round;
import backend.drawrace.domain.round.entity.RoundParticipant;
import backend.drawrace.domain.round.entity.RoundStatus;
import backend.drawrace.domain.round.entity.RoundSubmission;
import backend.drawrace.domain.round.repository.RoundParticipantRepository;
import backend.drawrace.domain.round.repository.RoundRepository;
import backend.drawrace.domain.round.repository.RoundSubmissionRepository;
import backend.drawrace.domain.round.validator.RoundValidator;
import backend.drawrace.domain.user.entity.User;
import backend.drawrace.global.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class RoundServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private RoundParticipantRepository roundParticipantRepository;

    @Mock
    private RoundSubmissionRepository roundSubmissionRepository;

    @Mock
    private KeywordGenerator keywordGenerator;

    @Mock
    private RoundValidator roundValidator;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AiInferenceService aiInferenceService;

    @Mock
    private org.springframework.beans.factory.ObjectProvider<AiSubmissionService> aiSubmissionServiceProvider;

    @InjectMocks
    private RoundService roundService;

    @Test
    @DisplayName("게임 시작 성공")
    void startGame_success() throws Exception {
        Long roomId = 1L;
        Long hostId = 1L;

        Room room = createRoom(roomId, false, hostId);
        Participant participant1 = createParticipant(100L, room, 0);
        Participant participant2 = createParticipant(101L, room, 0);

        given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
        given(participantRepository.countByRoomId(roomId)).willReturn(2L);
        given(roundRepository.findByRoomIdAndIsActiveTrue(roomId)).willReturn(Optional.empty());
        given(keywordGenerator.generateKeyword()).willReturn("사과");
        given(participantRepository.findByRoomId(roomId)).willReturn(List.of(participant1, participant2));
        given(roundRepository.save(any(Round.class))).willAnswer(invocation -> {
            Round saved = invocation.getArgument(0);
            setField(saved, "id", 10L);
            return saved;
        });

        RoundStartResponse response = roundService.startGame(roomId, hostId);

        then(roundValidator).should().validateStartGame(eq(room), eq(2L), eq(Optional.empty()), eq(hostId));
        then(roundParticipantRepository)
                .should()
                .saveAll(argThat((Iterable<RoundParticipant> iterable) -> countIterable(iterable) == 2));

        assertThat(response.getRoomId()).isEqualTo(roomId);
        assertThat(response.getRoundId()).isEqualTo(10L);
        assertThat(response.getRoundNumber()).isEqualTo(1);
        assertThat(response.getKeyword()).isEqualTo("사과");
        assertThat(response.getStatus()).isEqualTo(RoundStatus.IN_PROGRESS);
        assertThat(response.getStartedAt()).isNotNull();
        assertThat(room.isPlaying()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 방이면 게임 시작 예외")
    void startGame_roomNotFound() {
        Long roomId = 1L;
        Long userId = 1L;

        given(roomRepository.findById(roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roundService.startGame(roomId, userId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("존재하지 않는 방입니다");
    }

    @Test
    @DisplayName("아직 전원 제출 전이면 대기 응답을 반환한다")
    void submitDrawing_waitForOtherParticipants() throws Exception {
        Long roomId = 1L;
        Long roundId = 10L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true, 1L);
        Round round = createInProgressRound(roundId, room, 1, "사과");
        Participant participant = createParticipant(participantId, room, 0);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(true);
        given(roundSubmissionRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(false);
        given(aiInferenceService.infer("dummy-image", "사과")).willReturn(new AiInferenceResponse("사과", 0.88));
        given(roundSubmissionRepository.countByRoundId(roundId)).willReturn(1L);
        given(roundParticipantRepository.countByRoundId(roundId)).willReturn(2L);

        SubmitDrawingResponse response = roundService.submitDrawing(roundId, 1L, request);

        assertThat(response.getRoundId()).isEqualTo(roundId);
        assertThat(response.getSubmittedAiAnswer()).isEqualTo("사과");
        assertThat(response.getSubmittedScore()).isEqualTo(0.88);
        assertThat(response.getSubmittedCount()).isEqualTo(1);
        assertThat(response.getTotalParticipantCount()).isEqualTo(2);
        assertThat(response.isRoundFinished()).isFalse();
        assertThat(response.isGameFinished()).isFalse();
        assertThat(response.isTieBreakerStarted()).isFalse();
        assertThat(response.getRoundWinnerParticipantId()).isNull();
        assertThat(response.getRoundWinnerAiAnswer()).isNull();
        assertThat(response.getRoundWinnerScore()).isNull();

        assertThat(round.getStatus()).isEqualTo(RoundStatus.IN_PROGRESS);
        assertThat(participant.getRoundWinCount()).isEqualTo(0);

        then(roundSubmissionRepository).should().save(any(RoundSubmission.class));
    }

    @Test
    @DisplayName("그림 제출 시 제출 상태 이벤트를 웹소켓으로 전송한다")
    void submitDrawing_broadcastPlayerSubmittedEvent() throws Exception {
        Long roomId = 1L;
        Long roundId = 10L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true, 1L);
        Round round = createInProgressRound(roundId, room, 1, "사과");
        Participant participant = createParticipant(participantId, room, 0);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(true);
        given(roundSubmissionRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(false);
        given(aiInferenceService.infer("dummy-image", "사과")).willReturn(new AiInferenceResponse("사과", 0.88));
        given(roundSubmissionRepository.countByRoundId(roundId)).willReturn(1L);
        given(roundParticipantRepository.countByRoundId(roundId)).willReturn(2L);

        roundService.submitDrawing(roundId, 1L, request);

        then(messagingTemplate)
                .should()
                .convertAndSend(
                        eq("/sub/rooms/" + roomId),
                        argThat((PlayerSubmittedEvent event) -> event.getType().equals("PLAYER_SUBMITTED")
                                && event.getRoundId().equals(roundId)
                                && event.getParticipantId().equals(participantId)
                                && event.getSubmittedCount() == 1
                                && event.getTotalParticipantCount() == 2));
    }

    @Test
    @DisplayName("마지막 제출이면 라운드 승자를 선정하고 다음 일반 라운드를 생성한다")
    void submitDrawing_finishRoundAndCreateNextRound() throws Exception {
        Long roomId = 1L;
        Long roundId = 10L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true, 1L);
        setField(room, "totalRounds", (short) 3);

        Round round = createInProgressRound(roundId, room, 1, "사과");
        Participant participant = createParticipant(participantId, room, 0);
        Participant participant2 = createParticipant(101L, room, 0);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        RoundSubmission currentSubmission = RoundSubmission.create(round, participant, "dummy-image", "사과", 0.95);
        RoundSubmission otherSubmission = RoundSubmission.create(round, participant2, "other-image", "사과", 0.70);

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(true);
        given(roundSubmissionRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(false);
        given(aiInferenceService.infer("dummy-image", "사과")).willReturn(new AiInferenceResponse("사과", 0.95));
        given(roundSubmissionRepository.countByRoundId(roundId)).willReturn(2L);
        given(roundParticipantRepository.countByRoundId(roundId)).willReturn(2L);
        given(roundSubmissionRepository.findByRoundId(roundId)).willReturn(List.of(currentSubmission, otherSubmission));
        given(keywordGenerator.generateKeyword()).willReturn("자동차");
        given(participantRepository.findByRoomId(roomId)).willReturn(List.of(participant, participant2));
        given(roundRepository.save(any(Round.class))).willAnswer(invocation -> {
            Round saved = invocation.getArgument(0);
            setField(saved, "id", 20L);
            return saved;
        });

        SubmitDrawingResponse response = roundService.submitDrawing(roundId, 1L, request);

        assertThat(response.getRoundId()).isEqualTo(roundId);
        assertThat(response.getSubmittedAiAnswer()).isEqualTo("사과");
        assertThat(response.getSubmittedScore()).isEqualTo(0.95);
        assertThat(response.isRoundFinished()).isTrue();
        assertThat(response.isGameFinished()).isFalse();
        assertThat(response.isTieBreakerStarted()).isFalse();
        assertThat(response.getRoundWinnerParticipantId()).isEqualTo(participantId);
        assertThat(response.getRoundWinnerAiAnswer()).isEqualTo("사과");
        assertThat(response.getRoundWinnerScore()).isEqualTo(0.95);
        assertThat(response.getNextRoundId()).isEqualTo(20L);
        assertThat(response.getNextRoundNumber()).isEqualTo(2);
        assertThat(response.isNextRoundTieBreaker()).isFalse();

        assertThat(participant.getRoundWinCount()).isEqualTo(1);
        assertThat(round.getStatus()).isEqualTo(RoundStatus.FINISHED);
        assertThat(round.isActive()).isFalse();

        then(roundParticipantRepository)
                .should()
                .saveAll(argThat((Iterable<RoundParticipant> iterable) -> countIterable(iterable) == 2));
    }

    @Test
    @DisplayName("라운드 종료 시 현재 제출자 점수와 라운드 승자 점수를 따로 반환한다")
    void submitDrawing_finishRound_separateSubmittedScoreAndWinnerScore() throws Exception {
        Long roomId = 1L;
        Long roundId = 10L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true, 1L);
        setField(room, "totalRounds", (short) 3);

        Round round = createInProgressRound(roundId, room, 1, "사과");
        Participant participant = createParticipant(participantId, room, 0);
        Participant winnerParticipant = createParticipant(101L, room, 0);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        // 현재 요청자는 마지막 제출자지만 점수는 낮다
        RoundSubmission currentSubmission = RoundSubmission.create(round, participant, "dummy-image", "강아지", 0.30);

        // 다른 참가자가 더 높은 점수로 라운드 승자다
        RoundSubmission winnerSubmission = RoundSubmission.create(round, winnerParticipant, "winner-image", "사과", 0.95);

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(true);
        given(roundSubmissionRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(false);
        given(aiInferenceService.infer("dummy-image", "사과")).willReturn(new AiInferenceResponse("강아지", 0.30));
        given(roundSubmissionRepository.countByRoundId(roundId)).willReturn(2L);
        given(roundParticipantRepository.countByRoundId(roundId)).willReturn(2L);
        given(roundSubmissionRepository.findByRoundId(roundId))
                .willReturn(List.of(currentSubmission, winnerSubmission));
        given(keywordGenerator.generateKeyword()).willReturn("자동차");
        given(participantRepository.findByRoomId(roomId)).willReturn(List.of(participant, winnerParticipant));
        given(roundRepository.save(any(Round.class))).willAnswer(invocation -> {
            Round saved = invocation.getArgument(0);
            setField(saved, "id", 20L);
            return saved;
        });

        SubmitDrawingResponse response = roundService.submitDrawing(roundId, 1L, request);

        assertThat(response.getRoundId()).isEqualTo(roundId);
        assertThat(response.isRoundFinished()).isTrue();
        assertThat(response.isGameFinished()).isFalse();
        assertThat(response.isTieBreakerStarted()).isFalse();

        // 이번 요청으로 제출한 사람의 결과
        assertThat(response.getSubmittedAiAnswer()).isEqualTo("강아지");
        assertThat(response.getSubmittedScore()).isEqualTo(0.30);

        // 라운드 승자의 결과
        assertThat(response.getRoundWinnerParticipantId()).isEqualTo(winnerParticipant.getId());
        assertThat(response.getRoundWinnerAiAnswer()).isEqualTo("사과");
        assertThat(response.getRoundWinnerScore()).isEqualTo(0.95);

        assertThat(response.getNextRoundId()).isEqualTo(20L);
        assertThat(response.getNextRoundNumber()).isEqualTo(2);
        assertThat(response.isNextRoundTieBreaker()).isFalse();

        assertThat(participant.getRoundWinCount()).isEqualTo(0);
        assertThat(winnerParticipant.getRoundWinCount()).isEqualTo(1);
        assertThat(round.getStatus()).isEqualTo(RoundStatus.FINISHED);
        assertThat(round.isActive()).isFalse();
    }

    @Test
    @DisplayName("라운드 점수가 같으면 먼저 제출한 참가자를 승자로 선정한다")
    void submitDrawing_finishRound_sameScoreWinnerIsEarlierSubmission() throws Exception {
        Long roomId = 1L;
        Long roundId = 10L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true, 1L);
        setField(room, "totalRounds", (short) 3);

        Round round = createInProgressRound(roundId, room, 1, "사과");
        Participant participant = createParticipant(participantId, room, 0);
        Participant earlierParticipant = createParticipant(101L, room, 0);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        // 현재 요청자는 마지막 제출자이고, 점수는 동일하다
        RoundSubmission currentSubmission = RoundSubmission.create(round, participant, "dummy-image", "사과", 0.95);

        // 다른 참가자가 같은 점수지만 더 먼저 제출했다
        RoundSubmission earlierSubmission =
                RoundSubmission.create(round, earlierParticipant, "earlier-image", "사과", 0.95);

        setCreatedAt(earlierSubmission, LocalDateTime.of(2026, 1, 1, 10, 0));
        setCreatedAt(currentSubmission, LocalDateTime.of(2026, 1, 1, 10, 1));

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(true);
        given(roundSubmissionRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(false);
        given(aiInferenceService.infer("dummy-image", "사과")).willReturn(new AiInferenceResponse("사과", 0.95));
        given(roundSubmissionRepository.countByRoundId(roundId)).willReturn(2L);
        given(roundParticipantRepository.countByRoundId(roundId)).willReturn(2L);
        given(roundSubmissionRepository.findByRoundId(roundId))
                .willReturn(List.of(currentSubmission, earlierSubmission));
        given(keywordGenerator.generateKeyword()).willReturn("자동차");
        given(participantRepository.findByRoomId(roomId)).willReturn(List.of(participant, earlierParticipant));
        given(roundRepository.save(any(Round.class))).willAnswer(invocation -> {
            Round saved = invocation.getArgument(0);
            setField(saved, "id", 20L);
            return saved;
        });

        SubmitDrawingResponse response = roundService.submitDrawing(roundId, 1L, request);

        assertThat(response.isRoundFinished()).isTrue();
        assertThat(response.isGameFinished()).isFalse();
        assertThat(response.isTieBreakerStarted()).isFalse();

        // 이번 요청으로 제출한 사람의 결과
        assertThat(response.getSubmittedAiAnswer()).isEqualTo("사과");
        assertThat(response.getSubmittedScore()).isEqualTo(0.95);

        // 같은 점수면 먼저 제출한 참가자가 라운드 승자
        assertThat(response.getRoundWinnerParticipantId()).isEqualTo(earlierParticipant.getId());
        assertThat(response.getRoundWinnerAiAnswer()).isEqualTo("사과");
        assertThat(response.getRoundWinnerScore()).isEqualTo(0.95);

        assertThat(response.getNextRoundId()).isEqualTo(20L);
        assertThat(response.getNextRoundNumber()).isEqualTo(2);
        assertThat(response.isNextRoundTieBreaker()).isFalse();

        assertThat(participant.getRoundWinCount()).isEqualTo(0);
        assertThat(earlierParticipant.getRoundWinCount()).isEqualTo(1);
        assertThat(round.getStatus()).isEqualTo(RoundStatus.FINISHED);
        assertThat(round.isActive()).isFalse();

        then(roundParticipantRepository)
                .should()
                .saveAll(argThat((Iterable<RoundParticipant> iterable) -> countIterable(iterable) == 2));
    }

    @Test
    @DisplayName("마지막 일반 라운드 종료 후 단독 최고 점수자면 최종 우승 처리한다")
    void submitDrawing_finishLastRoundAndDecideWinner() throws Exception {
        Long roomId = 1L;
        Long roundId = 30L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true, 1L);
        setField(room, "totalRounds", (short) 3);

        Round round = createInProgressRound(roundId, room, 3, "사과");
        Participant participant = createParticipant(participantId, room, 1);
        Participant participant2 = createParticipant(101L, room, 1);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        RoundSubmission currentSubmission = RoundSubmission.create(round, participant, "dummy-image", "사과", 0.92);
        RoundSubmission otherSubmission = RoundSubmission.create(round, participant2, "other-image", "사과", 0.81);

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(true);
        given(roundSubmissionRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(false);
        given(aiInferenceService.infer("dummy-image", "사과")).willReturn(new AiInferenceResponse("사과", 0.92));
        given(roundSubmissionRepository.countByRoundId(roundId)).willReturn(2L);
        given(roundParticipantRepository.countByRoundId(roundId)).willReturn(2L);
        given(roundSubmissionRepository.findByRoundId(roundId)).willReturn(List.of(currentSubmission, otherSubmission));
        given(participantRepository.findByRoomId(roomId)).willReturn(List.of(participant, participant2));

        SubmitDrawingResponse response = roundService.submitDrawing(roundId, 1L, request);

        assertThat(response.isRoundFinished()).isTrue();
        assertThat(response.isGameFinished()).isTrue();
        assertThat(response.isTieBreakerStarted()).isFalse();
        assertThat(response.getSubmittedAiAnswer()).isEqualTo("사과");
        assertThat(response.getSubmittedScore()).isEqualTo(0.92);
        assertThat(response.getRoundWinnerParticipantId()).isEqualTo(participantId);
        assertThat(response.getRoundWinnerAiAnswer()).isEqualTo("사과");
        assertThat(response.getRoundWinnerScore()).isEqualTo(0.92);
        assertThat(response.getFinalWinnerParticipantId()).isEqualTo(participantId);

        assertThat(participant.getRoundWinCount()).isEqualTo(2);
        assertThat(participant.isWinner()).isTrue();
        assertThat(room.isPlaying()).isFalse();
    }

    @Test
    @DisplayName("마지막 일반 라운드 종료 후 최종 동점이면 결승 라운드를 생성한다")
    void submitDrawing_finishLastRoundAndCreateTieBreaker() throws Exception {
        Long roomId = 1L;
        Long roundId = 30L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true, 1L);
        setField(room, "totalRounds", (short) 3);

        Round round = createInProgressRound(roundId, room, 3, "사과");
        Participant participant = createParticipant(participantId, room, 1);
        Participant participant2 = createParticipant(101L, room, 2);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        RoundSubmission currentSubmission = RoundSubmission.create(round, participant, "dummy-image", "사과", 0.97);
        RoundSubmission otherSubmission = RoundSubmission.create(round, participant2, "other-image", "사과", 0.60);

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(true);
        given(roundSubmissionRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(false);
        given(aiInferenceService.infer("dummy-image", "사과")).willReturn(new AiInferenceResponse("사과", 0.97));
        given(roundSubmissionRepository.countByRoundId(roundId)).willReturn(2L);
        given(roundParticipantRepository.countByRoundId(roundId)).willReturn(2L);
        given(roundSubmissionRepository.findByRoundId(roundId)).willReturn(List.of(currentSubmission, otherSubmission));
        given(participantRepository.findByRoomId(roomId)).willReturn(List.of(participant, participant2));
        given(keywordGenerator.generateKeyword()).willReturn("자동차");
        given(roundRepository.save(any(Round.class))).willAnswer(invocation -> {
            Round saved = invocation.getArgument(0);
            setField(saved, "id", 40L);
            return saved;
        });

        SubmitDrawingResponse response = roundService.submitDrawing(roundId, 1L, request);

        assertThat(response.isRoundFinished()).isTrue();
        assertThat(response.isGameFinished()).isFalse();
        assertThat(response.isTieBreakerStarted()).isTrue();
        assertThat(response.getSubmittedAiAnswer()).isEqualTo("사과");
        assertThat(response.getSubmittedScore()).isEqualTo(0.97);
        assertThat(response.getRoundWinnerParticipantId()).isEqualTo(participantId);
        assertThat(response.getRoundWinnerAiAnswer()).isEqualTo("사과");
        assertThat(response.getRoundWinnerScore()).isEqualTo(0.97);
        assertThat(response.getNextRoundId()).isEqualTo(40L);
        assertThat(response.getNextRoundNumber()).isEqualTo(4);
        assertThat(response.isNextRoundTieBreaker()).isTrue();
        assertThat(response.getFinalWinnerParticipantId()).isNull();

        assertThat(participant.getRoundWinCount()).isEqualTo(2);
        assertThat(room.isPlaying()).isTrue();

        then(roundParticipantRepository)
                .should()
                .saveAll(argThat((Iterable<RoundParticipant> iterable) -> countIterable(iterable) == 2));
    }

    @Test
    @DisplayName("결승 라운드에서 마지막 제출이 들어오면 최종 우승자를 결정한다")
    void submitDrawing_finishTieBreakerAndFinishGame() throws Exception {
        Long roomId = 1L;
        Long roundId = 50L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true, 1L);
        Round round = createTieBreakerRound(roundId, room, 4, "사과");
        Participant participant = createParticipant(participantId, room, 2);
        Participant participant2 = createParticipant(101L, room, 2);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        RoundSubmission currentSubmission = RoundSubmission.create(round, participant, "dummy-image", "사과", 0.99);
        RoundSubmission otherSubmission = RoundSubmission.create(round, participant2, "other-image", "사과", 0.71);

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(true);
        given(roundSubmissionRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(false);
        given(aiInferenceService.infer("dummy-image", "사과")).willReturn(new AiInferenceResponse("사과", 0.99));
        given(roundSubmissionRepository.countByRoundId(roundId)).willReturn(2L);
        given(roundParticipantRepository.countByRoundId(roundId)).willReturn(2L);
        given(roundSubmissionRepository.findByRoundId(roundId)).willReturn(List.of(currentSubmission, otherSubmission));

        SubmitDrawingResponse response = roundService.submitDrawing(roundId, 1L, request);

        assertThat(response.isRoundFinished()).isTrue();
        assertThat(response.isGameFinished()).isTrue();
        assertThat(response.isTieBreakerStarted()).isFalse();
        assertThat(response.getSubmittedAiAnswer()).isEqualTo("사과");
        assertThat(response.getSubmittedScore()).isEqualTo(0.99);
        assertThat(response.getRoundWinnerParticipantId()).isEqualTo(participantId);
        assertThat(response.getRoundWinnerAiAnswer()).isEqualTo("사과");
        assertThat(response.getRoundWinnerScore()).isEqualTo(0.99);
        assertThat(response.getFinalWinnerParticipantId()).isEqualTo(participantId);

        assertThat(participant.getRoundWinCount()).isEqualTo(3);
        assertThat(participant.isWinner()).isTrue();
        assertThat(room.isPlaying()).isFalse();
    }

    @Test
    @DisplayName("결승 라운드에서 점수가 같으면 먼저 제출한 참가자를 최종 우승자로 선정한다")
    void submitDrawing_finishTieBreaker_sameScoreWinnerIsEarlierSubmission() throws Exception {
        Long roomId = 1L;
        Long roundId = 50L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true, 1L);
        Round round = createTieBreakerRound(roundId, room, 4, "사과");
        Participant participant = createParticipant(participantId, room, 2);
        Participant earlierParticipant = createParticipant(101L, room, 2);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        // 현재 요청자는 마지막 제출자이고, 점수는 동일하다
        RoundSubmission currentSubmission = RoundSubmission.create(round, participant, "dummy-image", "사과", 0.95);

        // 다른 참가자가 같은 점수지만 더 먼저 제출했다
        RoundSubmission earlierSubmission =
                RoundSubmission.create(round, earlierParticipant, "earlier-image", "사과", 0.95);

        setCreatedAt(earlierSubmission, LocalDateTime.of(2026, 1, 1, 10, 0));
        setCreatedAt(currentSubmission, LocalDateTime.of(2026, 1, 1, 10, 1));

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(true);
        given(roundSubmissionRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(false);
        given(aiInferenceService.infer("dummy-image", "사과")).willReturn(new AiInferenceResponse("사과", 0.95));
        given(roundSubmissionRepository.countByRoundId(roundId)).willReturn(2L);
        given(roundParticipantRepository.countByRoundId(roundId)).willReturn(2L);
        given(roundSubmissionRepository.findByRoundId(roundId))
                .willReturn(List.of(currentSubmission, earlierSubmission));

        SubmitDrawingResponse response = roundService.submitDrawing(roundId, 1L, request);

        assertThat(response.isRoundFinished()).isTrue();
        assertThat(response.isGameFinished()).isTrue();
        assertThat(response.isTieBreakerStarted()).isFalse();

        // 이번 요청으로 제출한 사람의 결과
        assertThat(response.getSubmittedAiAnswer()).isEqualTo("사과");
        assertThat(response.getSubmittedScore()).isEqualTo(0.95);

        // 결승 라운드에서도 같은 점수면 먼저 제출한 참가자가 최종 우승자
        assertThat(response.getRoundWinnerParticipantId()).isEqualTo(earlierParticipant.getId());
        assertThat(response.getRoundWinnerAiAnswer()).isEqualTo("사과");
        assertThat(response.getRoundWinnerScore()).isEqualTo(0.95);
        assertThat(response.getFinalWinnerParticipantId()).isEqualTo(earlierParticipant.getId());

        assertThat(participant.getRoundWinCount()).isEqualTo(2);
        assertThat(earlierParticipant.getRoundWinCount()).isEqualTo(3);
        assertThat(earlierParticipant.isWinner()).isTrue();
        assertThat(room.isPlaying()).isFalse();
    }

    @Test
    @DisplayName("이미 제출한 참가자는 다시 제출할 수 없다")
    void submitDrawing_fail_alreadySubmitted() throws Exception {
        Long roomId = 1L;
        Long roundId = 10L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true, 1L);
        Round round = createInProgressRound(roundId, room, 1, "사과");
        Participant participant = createParticipant(participantId, room, 0);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(true);
        given(roundSubmissionRepository.existsByRoundIdAndParticipantId(roundId, participantId))
                .willReturn(true);

        willThrow(new ServiceException("400-2", "이미 제출을 완료한 참가자입니다."))
                .given(roundValidator)
                .validateNotSubmitted(true);

        assertThatThrownBy(() -> roundService.submitDrawing(roundId, 1L, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("이미 제출을 완료한 참가자입니다");
    }

    @Test
    @DisplayName("존재하지 않는 라운드에 제출하면 예외가 발생한다")
    void submitDrawing_fail_roundNotFound() {
        Long roundId = 10L;
        SubmitDrawingRequest request = createSubmitDrawingRequest(100L, "dummy-image");

        given(roundRepository.findById(roundId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roundService.submitDrawing(roundId, 1L, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("존재하지 않는 라운드입니다");
    }

    @Test
    @DisplayName("현재 진행 중인 라운드를 조회한다")
    void getCurrentRound_success() throws Exception {
        Long roomId = 1L;
        Long roundId = 10L;

        Room room = createRoom(roomId, true, 1L);
        Round round = createInProgressRound(roundId, room, 2, "사과");
        Participant participant1 = createParticipant(100L, room, 1);
        Participant participant2 = createParticipant(101L, room, 0);

        RoundParticipant roundParticipant1 = createRoundParticipant(1L, round, participant1);
        RoundParticipant roundParticipant2 = createRoundParticipant(2L, round, participant2);

        given(participantRepository.existsByRoomIdAndUserId_Id(roomId, 1L)).willReturn(true);
        given(roundRepository.findByRoomIdAndIsActiveTrue(roomId)).willReturn(Optional.of(round));
        given(roundParticipantRepository.findByRoundId(roundId))
                .willReturn(List.of(roundParticipant1, roundParticipant2));
        given(roundSubmissionRepository.existsByRoundIdAndParticipantId(roundId, participant1.getId()))
                .willReturn(true);
        given(roundSubmissionRepository.existsByRoundIdAndParticipantId(roundId, participant2.getId()))
                .willReturn(false);

        CurrentRoundResponse response = roundService.getCurrentRound(roomId, 1L);

        assertThat(response.getRoomId()).isEqualTo(roomId);
        assertThat(response.getRoundId()).isEqualTo(roundId);
        assertThat(response.getRoundNumber()).isEqualTo(2);
        assertThat(response.getKeyword()).isEqualTo("사과");
        assertThat(response.getStatus()).isEqualTo(RoundStatus.IN_PROGRESS);
        assertThat(response.isTiebreaker()).isFalse();
        assertThat(response.getParticipants()).hasSize(2);
        assertThat(response.getParticipants().get(0).isSubmitted()).isTrue();
        assertThat(response.getParticipants().get(1).isSubmitted()).isFalse();
    }

    @Test
    @DisplayName("현재 진행 중인 라운드가 없으면 예외가 발생한다")
    void getCurrentRound_fail_noActiveRound() {
        Long roomId = 1L;

        given(participantRepository.existsByRoomIdAndUserId_Id(roomId, 1L)).willReturn(true);
        given(roundRepository.findByRoomIdAndIsActiveTrue(roomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roundService.getCurrentRound(roomId, 1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("현재 진행 중인 라운드가 없습니다");
    }

    @Test
    @DisplayName("본인 참가 정보가 아니면 그림 제출이 불가능하다")
    void submitDrawing_forbidden_notOwner() throws Exception {
        Long roomId = 1L;
        Long roundId = 10L;
        Long loginUserId = 1L;
        Long participantId = 100L;

        Room room = createRoom(roomId, true, 1L);
        Round round = createInProgressRound(roundId, room, 1, "사과");
        Participant participant = createParticipant(participantId, room, 0);

        SubmitDrawingRequest request = createSubmitDrawingRequest(participantId, "dummy-image");

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(participantId, roomId)).willReturn(Optional.of(participant));

        willThrow(new ServiceException("403-3", "본인 참가 정보로만 제출할 수 있습니다."))
                .given(roundValidator)
                .validateParticipantOwner(participant, loginUserId);

        assertThatThrownBy(() -> roundService.submitDrawing(roundId, loginUserId, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("본인 참가 정보로만 제출할 수 있습니다");
    }

    @Test
    @DisplayName("방 참가자가 아니면 현재 라운드를 조회할 수 없다")
    void getCurrentRound_forbidden_notRoomMember() {
        Long roomId = 1L;
        Long userId = 99L;

        given(participantRepository.existsByRoomIdAndUserId_Id(roomId, userId)).willReturn(false);

        assertThatThrownBy(() -> roundService.getCurrentRound(roomId, userId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("해당 방 참가자만 현재 라운드를 조회할 수 있습니다");
    }

    @Test
    @DisplayName("AI 참가자는 인증 검증을 거치지 않고 제출에 성공한다")
    void submitDrawing_ai_skipsOwnerValidation() throws Exception {
        Long roomId = 1L;
        Long roundId = 10L;
        Long aiParticipantId = 100L;
        Long aiUserId = 999L;

        Room room = createRoom(roomId, true, 1L);
        Round round = createInProgressRound(roundId, room, 1, "사과");
        Participant aiParticipant = createAiParticipant(aiParticipantId, room, aiUserId);

        SubmitDrawingRequest request = createSubmitDrawingRequest(aiParticipantId, "stroke-json");

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(aiParticipantId, roomId)).willReturn(Optional.of(aiParticipant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, aiParticipantId))
                .willReturn(true);
        given(roundSubmissionRepository.existsByRoundIdAndParticipantId(roundId, aiParticipantId))
                .willReturn(false);
        given(roundSubmissionRepository.countByRoundId(roundId)).willReturn(1L);
        given(roundParticipantRepository.countByRoundId(roundId)).willReturn(2L);

        roundService.submitDrawing(roundId, aiUserId, request);

        // AI는 validateParticipantOwner를 호출하지 않아야 한다
        then(roundValidator).should(never()).validateParticipantOwner(any(), any());
        then(roundSubmissionRepository).should().save(any(RoundSubmission.class));
    }

    @Test
    @DisplayName("AI 참가자는 추론 서비스를 호출하지 않고 0.70~0.85 사이의 점수를 받는다")
    void submitDrawing_ai_usesFixedScoreWithoutInference() throws Exception {
        Long roomId = 1L;
        Long roundId = 10L;
        Long aiParticipantId = 100L;
        Long aiUserId = 999L;

        Room room = createRoom(roomId, true, 1L);
        Round round = createInProgressRound(roundId, room, 1, "사과");
        Participant aiParticipant = createAiParticipant(aiParticipantId, room, aiUserId);

        SubmitDrawingRequest request = createSubmitDrawingRequest(aiParticipantId, "stroke-json");

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.findByIdAndRoomId(aiParticipantId, roomId)).willReturn(Optional.of(aiParticipant));
        given(roundParticipantRepository.existsByRoundIdAndParticipantId(roundId, aiParticipantId))
                .willReturn(true);
        given(roundSubmissionRepository.existsByRoundIdAndParticipantId(roundId, aiParticipantId))
                .willReturn(false);
        given(roundSubmissionRepository.countByRoundId(roundId)).willReturn(1L);
        given(roundParticipantRepository.countByRoundId(roundId)).willReturn(2L);

        roundService.submitDrawing(roundId, aiUserId, request);

        // AI는 추론 서비스를 호출하지 않아야 한다
        then(aiInferenceService).should(never()).infer(any(), any());

        // 저장된 제출의 점수가 0.70~0.85 사이인지 확인
        then(roundSubmissionRepository)
                .should()
                .save(argThat(submission -> submission.getScore() >= 0.70 && submission.getScore() < 0.85));
    }

    @Test
    @DisplayName("종료된 라운드의 제출 목록을 조회한다")
    void getRoundSubmissions_success() throws Exception {
        Long roomId = 1L;
        Long roundId = 10L;
        Long userId = 1L;

        Room room = createRoom(roomId, true, userId);
        Round round = createInProgressRound(roundId, room, 1, "버섯");
        round.finish();

        Participant participant1 = createParticipantWithNickname(100L, room, "도환", 0);
        Participant participant2 = createParticipantWithNickname(101L, room, "AI", 0);

        RoundSubmission submission1 = RoundSubmission.create(round, participant1, "image-1", "버섯", 0.95);
        RoundSubmission submission2 = RoundSubmission.create(round, participant2, "image-2", "버섯", 0.70);

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.existsByRoomIdAndUserId_Id(roomId, userId)).willReturn(true);
        given(roundSubmissionRepository.findAllWithParticipantAndUserByRoundIdOrderByScoreDescCreatedAtAsc(roundId))
                .willReturn(List.of(submission1, submission2));

        List<RoundSubmissionResponse> responses = roundService.getRoundSubmissions(roundId, userId);

        assertThat(responses).hasSize(2);

        assertThat(responses.get(0).getParticipantId()).isEqualTo(100L);
        assertThat(responses.get(0).getNickname()).isEqualTo("도환");
        assertThat(responses.get(0).getImageData()).isEqualTo("image-1");
        assertThat(responses.get(0).getAiAnswer()).isEqualTo("버섯");
        assertThat(responses.get(0).getScore()).isEqualTo(0.95);
        assertThat(responses.get(0).isWinner()).isTrue();

        assertThat(responses.get(1).getParticipantId()).isEqualTo(101L);
        assertThat(responses.get(1).getNickname()).isEqualTo("AI");
        assertThat(responses.get(1).getImageData()).isEqualTo("image-2");
        assertThat(responses.get(1).getAiAnswer()).isEqualTo("버섯");
        assertThat(responses.get(1).getScore()).isEqualTo(0.70);
        assertThat(responses.get(1).isWinner()).isFalse();
    }

    @Test
    @DisplayName("진행 중인 라운드의 제출 목록은 조회할 수 없다")
    void getRoundSubmissions_fail_roundInProgress() throws Exception {
        Long roomId = 1L;
        Long roundId = 10L;
        Long userId = 1L;

        Room room = createRoom(roomId, true, userId);
        Round round = createInProgressRound(roundId, room, 1, "버섯");

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.existsByRoomIdAndUserId_Id(roomId, userId)).willReturn(true);

        assertThatThrownBy(() -> roundService.getRoundSubmissions(roundId, userId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("종료된 라운드의 제출 목록만 조회할 수 있습니다");
    }

    @Test
    @DisplayName("방 참가자가 아니면 라운드 제출 목록을 조회할 수 없다")
    void getRoundSubmissions_fail_notRoomMember() throws Exception {
        Long roomId = 1L;
        Long roundId = 10L;
        Long userId = 99L;

        Room room = createRoom(roomId, true, 1L);
        Round round = createInProgressRound(roundId, room, 1, "버섯");
        round.finish();

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.existsByRoomIdAndUserId_Id(roomId, userId)).willReturn(false);

        assertThatThrownBy(() -> roundService.getRoundSubmissions(roundId, userId))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("해당 방 참가자만 현재 라운드를 조회할 수 있습니다");
    }

    @Test
    @DisplayName("제출 목록은 점수 내림차순이며 동점이면 먼저 제출한 참가자가 winner가 된다")
    void getRoundSubmissions_orderByScoreDescAndCreatedAtAsc() throws Exception {
        Long roomId = 1L;
        Long roundId = 10L;
        Long userId = 1L;

        Room room = createRoom(roomId, true, userId);
        Round round = createInProgressRound(roundId, room, 1, "시계");
        round.finish();

        Participant participant1 = createParticipantWithNickname(100L, room, "먼저제출", 0);
        Participant participant2 = createParticipantWithNickname(101L, room, "늦게제출", 0);
        Participant participant3 = createParticipantWithNickname(102L, room, "낮은점수", 0);

        RoundSubmission firstHighScore = RoundSubmission.create(round, participant1, "image-1", "시계", 0.90);
        RoundSubmission sameHighScoreButLater = RoundSubmission.create(round, participant2, "image-2", "시계", 0.90);
        RoundSubmission lowScore = RoundSubmission.create(round, participant3, "image-3", "원", 0.40);

        setCreatedAt(firstHighScore, LocalDateTime.of(2026, 1, 1, 10, 0));
        setCreatedAt(sameHighScoreButLater, LocalDateTime.of(2026, 1, 1, 10, 1));
        setCreatedAt(lowScore, LocalDateTime.of(2026, 1, 1, 10, 2));

        given(roundRepository.findById(roundId)).willReturn(Optional.of(round));
        given(participantRepository.existsByRoomIdAndUserId_Id(roomId, userId)).willReturn(true);
        given(roundSubmissionRepository.findAllWithParticipantAndUserByRoundIdOrderByScoreDescCreatedAtAsc(roundId))
                .willReturn(List.of(firstHighScore, sameHighScoreButLater, lowScore));

        List<RoundSubmissionResponse> responses = roundService.getRoundSubmissions(roundId, userId);

        assertThat(responses).hasSize(3);

        assertThat(responses.get(0).getParticipantId()).isEqualTo(100L);
        assertThat(responses.get(0).getNickname()).isEqualTo("먼저제출");
        assertThat(responses.get(0).isWinner()).isTrue();

        assertThat(responses.get(1).getParticipantId()).isEqualTo(101L);
        assertThat(responses.get(1).getNickname()).isEqualTo("늦게제출");
        assertThat(responses.get(1).isWinner()).isFalse();

        assertThat(responses.get(2).getParticipantId()).isEqualTo(102L);
        assertThat(responses.get(2).getNickname()).isEqualTo("낮은점수");
        assertThat(responses.get(2).isWinner()).isFalse();
    }

    private Participant createAiParticipant(Long participantId, Room room, Long aiUserId) throws Exception {
        User user = mock(User.class);
        given(user.isAi()).willReturn(true);

        Participant participant =
                Participant.builder().userId(user).room(room).isHost(false).build();
        setField(participant, "id", participantId);
        return participant;
    }

    private Room createRoom(Long id, boolean isPlaying, Long hostId) throws Exception {
        Room room = Room.builder()
                .title("테스트 방")
                .hostId(hostId)
                .totalRounds((short) 3)
                .maxPlayers((short) 4)
                .curPlayers((short) 2)
                .isPlaying(isPlaying)
                .build();
        setField(room, "id", id);
        return room;
    }

    private Round createInProgressRound(Long id, Room room, int roundNumber, String keyword) throws Exception {
        Round round = Round.create(room, roundNumber, keyword);
        round.start();
        setField(round, "id", id);
        return round;
    }

    private Round createTieBreakerRound(Long id, Room room, int roundNumber, String keyword) throws Exception {
        Round round = Round.createTieBreaker(room, roundNumber, keyword);
        round.start();
        setField(round, "id", id);
        return round;
    }

    private Participant createParticipant(Long participantId, Room room, int roundWinCount) throws Exception {
        User user = mock(User.class);

        Participant participant =
                Participant.builder().userId(user).room(room).isHost(false).build();

        setField(participant, "id", participantId);
        setField(participant, "roundWinCount", roundWinCount);
        return participant;
    }

    private RoundParticipant createRoundParticipant(Long id, Round round, Participant participant) throws Exception {
        RoundParticipant roundParticipant = RoundParticipant.of(round, participant);
        setField(roundParticipant, "id", id);
        return roundParticipant;
    }

    private SubmitDrawingRequest createSubmitDrawingRequest(Long participantId, String imageData) {
        SubmitDrawingRequest request = new SubmitDrawingRequest();
        ReflectionTestUtils.setField(request, "participantId", participantId);
        ReflectionTestUtils.setField(request, "imageData", imageData);
        return request;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void setCreatedAt(Object target, LocalDateTime createdAt) throws Exception {
        Field field = target.getClass().getSuperclass().getDeclaredField("createdAt");
        field.setAccessible(true);
        field.set(target, createdAt);
    }

    private int countIterable(Iterable<RoundParticipant> iterable) {
        int count = 0;
        for (RoundParticipant ignored : iterable) {
            count++;
        }
        return count;
    }

    private Participant createParticipantWithNickname(Long participantId, Room room, String nickname, int roundWinCount)
            throws Exception {
        User user = mock(User.class);
        given(user.getNickname()).willReturn(nickname);

        Participant participant =
                Participant.builder().userId(user).room(room).isHost(false).build();

        setField(participant, "id", participantId);
        setField(participant, "roundWinCount", roundWinCount);
        return participant;
    }
}
