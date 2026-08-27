package com.mymeet.listener;

import com.mymeet.dto.WebSocketEvent;
import com.mymeet.model.ParticipantSession;
import com.mymeet.room.RoomManager;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketEventListener {

    private final RoomManager roomManager;
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketEventListener(
            RoomManager roomManager,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.roomManager = roomManager;
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void handleWebSocketDisconnect(
            SessionDisconnectEvent event
    ) {

        String sessionId = event.getSessionId();

        ParticipantSession session =
                roomManager.getSession(sessionId);

        if (session == null) {
            return;
        }

        String roomId = session.getRoomId();
        String participantId = session.getParticipantId();

        ParticipantSession removed =
                roomManager.leaveBySession(sessionId);

        if (removed == null) {
            return;
        }

        messagingTemplate.convertAndSend(
                "/topic/meet/" + roomId,
                WebSocketEvent.roomState(
                        roomId,
                        roomManager.snapshot(roomId)
                )
        );
    }
}