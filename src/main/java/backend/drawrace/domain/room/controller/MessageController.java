package backend.drawrace.domain.room.controller;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import backend.drawrace.domain.room.dto.response.DrawData;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class MessageController {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 클라이언트가 보낸 드로잉 좌표를 해당 방의 모든 참여자에게 전달합니다.
     * 클라이언트 전송 경로: /pub/rooms/{roomId}/draw
     * 구독자 수신 경로: /sub/rooms/{roomId}/draw
     */
    @MessageMapping("/rooms/{roomId}/draw")
    public void handleDraw(@DestinationVariable Long roomId, DrawData drawData) {
        // 받은 좌표 데이터를 그대로 해당 방을 구독 중인 유저들에게 전송
        messagingTemplate.convertAndSend("/sub/rooms/" + roomId + "/draw", drawData);
    }
}
