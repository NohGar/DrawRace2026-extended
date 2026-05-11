package backend.drawrace.domain.room.controller;

import static java.util.concurrent.TimeUnit.*;
import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import backend.drawrace.domain.room.dto.response.DrawData;
import backend.drawrace.domain.room.entity.Participant;
import backend.drawrace.domain.room.entity.Room;
import backend.drawrace.domain.room.repository.ParticipantRepository;
import backend.drawrace.domain.room.repository.RoomRepository;
import backend.drawrace.domain.user.entity.User;
import backend.drawrace.domain.user.repository.UserRepository;
import backend.drawrace.global.security.JwtTokenProvider;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private UserRepository userRepository;

    private WebSocketStompClient stompClient;
    private String token;
    private User testUser;
    private Room testRoom;

    @BeforeEach
    void setUp() {
        // 테스트용 유저 생성
        testUser = userRepository.save(User.builder()
                .email("test@test.com")
                .nickname("테스트유저")
                .password("1234")
                .build());

        // 테스트용 방 생성
        testRoom = roomRepository.save(Room.builder()
                .title("테스트방")
                .maxPlayers((short) 4)
                .hostId(testUser.getId())
                .build());

        // 유저를 방 참여자로 등록 (StompHandler 보안 통과용)
        participantRepository.save(Participant.builder()
                .userId(testUser)
                .room(testRoom)
                .isHost(true)
                .build());

        // 저장된 유저 ID로 토큰 생성
        token = jwtTokenProvider.createAccessToken(testUser.getId(), testUser.getEmail());

        stompClient = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @Test
    @DisplayName("웹소켓 연결 및 드로잉 데이터 공유 테스트 - 성공")
    void webSocket_drawData_success() throws Exception {

        String url = "ws://localhost:" + port + "/ws-draw";
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token); // 보안 통과를 위한 헤더 추가

        CompletableFuture<DrawData> subscribeFuture = new CompletableFuture<>();

        // 웹소켓 연결
        StompSession session = stompClient
                .connectAsync(url, (WebSocketHttpHeaders) null, connectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, SECONDS);

        String subPath = "/sub/rooms/" + testRoom.getId() + "/draw";
        String pubPath = "/pub/rooms/" + testRoom.getId() + "/draw";

        // 특정 방 구독 (/sub/rooms/1/draw)
        session.subscribe(subPath, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return DrawData.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                subscribeFuture.complete((DrawData) payload);
            }
        });

        // 메시지 발행 (/pub/rooms/1/draw)
        DrawData sendData = DrawData.builder()
                .x(10.5)
                .y(20.5)
                .type("START")
                .color("#FF0000")
                .penSize(5)
                .build();
        session.send(pubPath, sendData);

        // 전송한 데이터가 구독 경로로 다시 잘 돌아오는지 확인 (5초 대기)
        DrawData receivedData = subscribeFuture.get(5, SECONDS);
        assertThat(receivedData.x()).isEqualTo(10.5);
        assertThat(receivedData.type()).isEqualTo("START");
    }
}
