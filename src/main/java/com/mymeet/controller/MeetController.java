package com.mymeet.controller;

import com.mymeet.dto.JoinRequest;
import com.mymeet.dto.LeaveRequest;
import com.mymeet.dto.WebSocketEvent;
import com.mymeet.model.Participant;
import com.mymeet.model.ParticipantSession;
import com.mymeet.room.RoomManager;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class MeetController {

    private final SimpMessagingTemplate messagingTemplate;

    private final RoomManager roomManager;


    public MeetController(
            SimpMessagingTemplate messagingTemplate,
            RoomManager roomManager
    ) {

        this.messagingTemplate = messagingTemplate;

        this.roomManager = roomManager;
    }


    /* =========================================================
       JOIN
       ========================================================= */

    @MessageMapping("/meet/join")
    public void join(
            @Payload JoinRequest request,
            SimpMessageHeaderAccessor headerAccessor
    ) {

        String sessionId =
                headerAccessor.getSessionId();


        System.out.println(
                "[MyMeet] JOIN REQUEST:"
                        + " session=" + sessionId
                        + ", room=" + request.getRoomId()
                        + ", participant=" + request.getParticipantId()
                        + ", name=" + request.getName()
        );


        /*
         * A STOMP session is mandatory because RoomManager
         * uses the WebSocket session as the authoritative
         * connection identity.
         */
        if (sessionId == null || sessionId.isBlank()) {

            System.err.println(
                    "[MyMeet] JOIN FAILED: "
                            + "WebSocket session ID is missing"
            );

            return;
        }


        try {

            /*
             * =====================================================
             * JOIN ROOM
             * =====================================================
             */

            RoomManager.JoinResult result =
                    roomManager.join(
                            sessionId,
                            request.getRoomId(),
                            request.getParticipantId(),
                            request.getName()
                    );


            /*
             * =====================================================
             * INVALID SESSION
             * =====================================================
             */

            if (result.invalidSession()) {

                System.err.println(
                        "[MyMeet] JOIN REJECTED:"
                                + " invalid session"
                                + ", session=" + sessionId
                                + ", room=" + request.getRoomId()
                );

                messagingTemplate.convertAndSend(
                        "/topic/meet/" + request.getRoomId(),
                        WebSocketEvent.joinRejected(
                                request.getRoomId(),
                                request.getParticipantId(),
                                "Invalid WebSocket session"
                        )
                );

                return;
            }


            /*
             * =====================================================
             * DUPLICATE PARTICIPANT
             * =====================================================
             */

            if (result.duplicateParticipant()) {

                System.err.println(
                        "[MyMeet] JOIN REJECTED:"
                                + " participant already connected"
                                + ", participant="
                                + request.getParticipantId()
                );

                messagingTemplate.convertAndSend(
                        "/topic/meet/" + request.getRoomId(),
                        WebSocketEvent.joinRejected(
                                request.getRoomId(),
                                request.getParticipantId(),
                                "Participant is already connected"
                        )
                );

                return;
            }


            /*
             * =====================================================
             * ROOM STATE
             * =====================================================
             *
             * The RoomManager snapshot is authoritative.
             *
             * This means the ROOM_STATE contains the current
             * backend participant state:
             *
             *     muted
             *     cameraOff
             *     screenSharing
             *     handRaised
             *
             * No state is reconstructed with defaults here.
             */
            messagingTemplate.convertAndSend(
                    "/topic/meet/" + request.getRoomId(),
                    WebSocketEvent.roomState(
                            request.getRoomId(),
                            result.participants()
                    )
            );


            /*
             * =====================================================
             * PARTICIPANT JOINED
             * =====================================================
             *
             * Only broadcast this when a participant was actually
             * added to the room.
             *
             * A reconnect/repeated JOIN does not generate a new
             * PARTICIPANT_JOINED event.
             */
            if (result.newlyJoined()) {

                messagingTemplate.convertAndSend(
                        "/topic/meet/" + request.getRoomId(),
                        WebSocketEvent.participantJoined(
                                request.getRoomId(),
                                request.getParticipantId(),
                                request.getName()
                        )
                );
            }


            System.out.println(
                    "[MyMeet] JOIN SUCCESS:"
                            + " session=" + sessionId
                            + ", room=" + request.getRoomId()
                            + ", participant="
                            + request.getParticipantId()
                            + ", participants="
                            + result.participants().size()
            );

        } catch (Exception exception) {

            System.err.println(
                    "[MyMeet] JOIN FAILED: "
                            + exception.getMessage()
            );

            exception.printStackTrace();


            messagingTemplate.convertAndSend(
                    "/topic/meet/" + request.getRoomId(),
                    WebSocketEvent.joinRejected(
                            request.getRoomId(),
                            request.getParticipantId(),
                            exception.getMessage()
                    )
            );
        }
    }


    /* =========================================================
       LEAVE
       ========================================================= */

    @MessageMapping("/meet/leave")
    public void leave(
            @Payload LeaveRequest request,
            SimpMessageHeaderAccessor headerAccessor
    ) {

        String sessionId =
                headerAccessor.getSessionId();


        System.out.println(
                "[MyMeet] LEAVE REQUEST:"
                        + " session=" + sessionId
                        + ", room=" + request.getRoomId()
                        + ", participant="
                        + request.getParticipantId()
        );


        if (sessionId == null || sessionId.isBlank()) {

            System.err.println(
                    "[MyMeet] LEAVE FAILED: "
                            + "WebSocket session ID is missing"
            );

            return;
        }


        try {

            /*
             * =====================================================
             * LEAVE ROOM
             * =====================================================
             */

            ParticipantSession removed =
                    roomManager.leave(
                            sessionId,
                            request.getRoomId(),
                            request.getParticipantId()
                    );


            /*
             * If null is returned, the participant/session
             * relationship was invalid or already removed.
             */
            if (removed == null) {

                System.err.println(
                        "[MyMeet] LEAVE REJECTED:"
                                + " participant/session mismatch"
                );

                return;
            }


            /*
             * =====================================================
             * NOTIFY OTHER PARTICIPANTS
             * =====================================================
             */

            messagingTemplate.convertAndSend(
                    "/topic/meet/" + request.getRoomId(),
                    WebSocketEvent.participantLeft(
                            request.getRoomId(),
                            request.getParticipantId()
                    )
            );


            System.out.println(
                    "[MyMeet] LEAVE SUCCESS:"
                            + " session=" + sessionId
                            + ", room=" + request.getRoomId()
                            + ", participant="
                            + request.getParticipantId()
            );

        } catch (Exception exception) {

            System.err.println(
                    "[MyMeet] LEAVE FAILED: "
                            + exception.getMessage()
            );

            exception.printStackTrace();
        }
    }


    /* =========================================================
       CHAT MESSAGE
       ========================================================= */

    @MessageMapping("/meet/message")
    public void message(
            @Payload Map<String, Object> payload
    ) {

        String roomId =
                stringValue(payload, "roomId");

        String participantId =
                stringValue(payload, "participantId");

        String name =
                stringValue(payload, "name");

        String message =
                stringValue(payload, "message");


        if (
                roomId == null
                        || participantId == null
                        || message == null
        ) {
            return;
        }


        System.out.println(
                "[MyMeet] MESSAGE:"
                        + " room=" + roomId
                        + ", participant=" + participantId
                        + ", message=" + message
        );


        messagingTemplate.convertAndSend(
                "/topic/meet/" + roomId,
                WebSocketEvent.chatMessage(
                        roomId,
                        participantId,
                        message,
                        name
                )
        );
    }


    /* =========================================================
       MEDIA STATUS
       ========================================================= */

    @MessageMapping("/meet/media-status")
    public void mediaStatus(
            @Payload Map<String, Object> payload,
            SimpMessageHeaderAccessor headerAccessor
    ) {

        String sessionId =
                headerAccessor.getSessionId();

        String roomId =
                stringValue(payload, "roomId");

        String participantId =
                stringValue(payload, "participantId");


        /*
         * Basic validation.
         */
        if (
                sessionId == null
                        || roomId == null
                        || participantId == null
        ) {
            return;
        }


        /*
         * =====================================================
         * MEMBERSHIP VALIDATION
         * =====================================================
         *
         * Make sure the WebSocket session is actually
         * representing this participant in this room.
         */
        if (
                !roomManager.isMember(
                        sessionId,
                        roomId,
                        participantId
                )
        ) {

            System.err.println(
                    "[MyMeet] MEDIA STATUS REJECTED:"
                            + " invalid membership"
                            + ", session=" + sessionId
                            + ", room=" + roomId
                            + ", participant=" + participantId
            );

            return;
        }


        boolean muted =
                booleanValue(
                        payload,
                        "muted",
                        false
                );

        boolean cameraOff =
                booleanValue(
                        payload,
                        "cameraOff",
                        false
                );


        System.out.println(
                "[MyMeet] MEDIA STATUS:"
                        + " room=" + roomId
                        + ", participant=" + participantId
                        + ", muted=" + muted
                        + ", cameraOff=" + cameraOff
        );


        /*
         * =====================================================
         * AUTHORITATIVE STATE UPDATE
         * =====================================================
         *
         * IMPORTANT:
         *
         * updateMediaState() changes ONLY:
         *
         *     muted
         *     cameraOff
         *
         * It does NOT reset:
         *
         *     screenSharing
         *     handRaised
         */
        Participant updatedParticipant =
                roomManager.updateMediaState(
                        roomId,
                        participantId,
                        muted,
                        cameraOff
                );


        /*
         * Participant disappeared between membership validation
         * and state update.
         */
        if (updatedParticipant == null) {

            System.err.println(
                    "[MyMeet] MEDIA STATUS REJECTED:"
                            + " participant state not found"
                            + ", room=" + roomId
                            + ", participant=" + participantId
            );

            return;
        }


        /*
         * Broadcast the state that is actually stored in the
         * backend authority.
         */
        messagingTemplate.convertAndSend(
                "/topic/meet/" + roomId,
                WebSocketEvent.mediaStatus(
                        roomId,
                        participantId,
                        updatedParticipant.isMuted(),
                        updatedParticipant.isCameraOff()
                )
        );
    }


    /* =========================================================
       HAND RAISE
       ========================================================= */

    @MessageMapping("/meet/hand-raise")
    public void handRaise(
            @Payload Map<String, Object> payload,
            SimpMessageHeaderAccessor headerAccessor
    ) {

        String sessionId =
                headerAccessor.getSessionId();

        String roomId =
                stringValue(payload, "roomId");

        String participantId =
                stringValue(payload, "participantId");


        /*
         * Basic validation.
         */
        if (
                sessionId == null
                        || roomId == null
                        || participantId == null
        ) {
            return;
        }


        /*
         * Validate that the session owns this participant.
         */
        if (
                !roomManager.isMember(
                        sessionId,
                        roomId,
                        participantId
                )
        ) {

            System.err.println(
                    "[MyMeet] HAND RAISE REJECTED:"
                            + " invalid membership"
                            + ", session=" + sessionId
                            + ", room=" + roomId
                            + ", participant=" + participantId
            );

            return;
        }


        boolean handRaised =
                booleanValue(
                        payload,
                        "handRaised",
                        false
                );


        System.out.println(
                "[MyMeet] HAND RAISE:"
                        + " room=" + roomId
                        + ", participant=" + participantId
                        + ", handRaised=" + handRaised
        );


        /*
         * =====================================================
         * AUTHORITATIVE STATE UPDATE
         * =====================================================
         *
         * ONLY handRaised is changed.
         *
         * muted, cameraOff and screenSharing are preserved.
         */
        Participant updatedParticipant =
                roomManager.updateHandRaised(
                        roomId,
                        participantId,
                        handRaised
                );


        if (updatedParticipant == null) {

            System.err.println(
                    "[MyMeet] HAND RAISE REJECTED:"
                            + " participant state not found"
                            + ", room=" + roomId
                            + ", participant=" + participantId
            );

            return;
        }


        /*
         * Broadcast authoritative backend state.
         */
        messagingTemplate.convertAndSend(
                "/topic/meet/" + roomId,
                WebSocketEvent.handRaise(
                        roomId,
                        participantId,
                        updatedParticipant.isHandRaised()
                )
        );
    }


    /* =========================================================
       SCREEN SHARE
       ========================================================= */

    @MessageMapping("/meet/screen-share")
    public void screenShare(
            @Payload Map<String, Object> payload,
            SimpMessageHeaderAccessor headerAccessor
    ) {

        String sessionId =
                headerAccessor.getSessionId();

        String roomId =
                stringValue(payload, "roomId");

        String participantId =
                stringValue(payload, "participantId");


        /*
         * Basic validation.
         */
        if (
                sessionId == null
                        || roomId == null
                        || participantId == null
        ) {
            return;
        }


        /*
         * Validate participant membership.
         */
        if (
                !roomManager.isMember(
                        sessionId,
                        roomId,
                        participantId
                )
        ) {

            System.err.println(
                    "[MyMeet] SCREEN SHARE REJECTED:"
                            + " invalid membership"
                            + ", session=" + sessionId
                            + ", room=" + roomId
                            + ", participant=" + participantId
            );

            return;
        }


        boolean screenSharing =
                booleanValue(
                        payload,
                        "screenSharing",
                        false
                );


        System.out.println(
                "[MyMeet] SCREEN SHARE:"
                        + " room=" + roomId
                        + ", participant=" + participantId
                        + ", screenSharing=" + screenSharing
        );


        /*
         * =====================================================
         * AUTHORITATIVE STATE UPDATE
         * =====================================================
         *
         * ONLY screenSharing is changed.
         *
         * muted, cameraOff and handRaised are preserved.
         */
        Participant updatedParticipant =
                roomManager.updateScreenSharing(
                        roomId,
                        participantId,
                        screenSharing
                );


        if (updatedParticipant == null) {

            System.err.println(
                    "[MyMeet] SCREEN SHARE REJECTED:"
                            + " participant state not found"
                            + ", room=" + roomId
                            + ", participant=" + participantId
            );

            return;
        }


        /*
         * Broadcast authoritative backend state.
         */
        messagingTemplate.convertAndSend(
                "/topic/meet/" + roomId,
                WebSocketEvent.screenShare(
                        roomId,
                        participantId,
                        updatedParticipant.isScreenSharing()
                )
        );
    }


    /* =========================================================
       REACTION
       ========================================================= */

    @MessageMapping("/meet/reaction")
    public void reaction(
            @Payload Map<String, Object> payload,
            SimpMessageHeaderAccessor headerAccessor
    ) {

        String sessionId =
                headerAccessor.getSessionId();

        String roomId =
                stringValue(payload, "roomId");

        String participantId =
                stringValue(payload, "participantId");

        String emoji =
                stringValue(payload, "emoji");


        if (
                sessionId == null
                        || roomId == null
                        || participantId == null
                        || emoji == null
        ) {
            return;
        }


        if (
                !roomManager.isMember(
                        sessionId,
                        roomId,
                        participantId
                )
        ) {

            return;
        }


        System.out.println(
                "[MyMeet] REACTION:"
                        + " room=" + roomId
                        + ", participant=" + participantId
                        + ", emoji=" + emoji
        );


        /*
         * Reaction remains transient.
         *
         * It is not stored as authoritative Participant state.
         */
        messagingTemplate.convertAndSend(
                "/topic/meet/" + roomId,
                WebSocketEvent.reaction(
                        roomId,
                        participantId,
                        emoji
                )
        );
    }


    /* =========================================================
       WEBRTC OFFER
       ========================================================= */

    @MessageMapping("/webrtc/offer")
    public void offer(
            @Payload Map<String, Object> payload,
            SimpMessageHeaderAccessor headerAccessor
    ) {

        String sessionId =
                headerAccessor.getSessionId();

        String roomId =
                stringValue(payload, "roomId");

        String participantId =
                stringValue(payload, "participantId");

        String targetParticipantId =
                stringValue(
                        payload,
                        "targetParticipantId"
                );

        Object offer =
                payload.get("offer");


        if (
                sessionId == null
                        || roomId == null
                        || participantId == null
                        || targetParticipantId == null
                        || offer == null
        ) {
            return;
        }


        if (
                !roomManager.isMember(
                        sessionId,
                        roomId,
                        participantId
                )
        ) {
            return;
        }


        System.out.println(
                "[MyMeet] WEBRTC OFFER:"
                        + " from=" + participantId
                        + " to=" + targetParticipantId
        );


        messagingTemplate.convertAndSend(
                "/topic/meet/" + roomId,
                WebSocketEvent.offer(
                        roomId,
                        participantId,
                        targetParticipantId,
                        offer
                )
        );
    }


    /* =========================================================
       WEBRTC ANSWER
       ========================================================= */

    @MessageMapping("/webrtc/answer")
    public void answer(
            @Payload Map<String, Object> payload,
            SimpMessageHeaderAccessor headerAccessor
    ) {

        String sessionId =
                headerAccessor.getSessionId();

        String roomId =
                stringValue(payload, "roomId");

        String participantId =
                stringValue(payload, "participantId");

        String targetParticipantId =
                stringValue(
                        payload,
                        "targetParticipantId"
                );

        Object answer =
                payload.get("answer");


        if (
                sessionId == null
                        || roomId == null
                        || participantId == null
                        || targetParticipantId == null
                        || answer == null
        ) {
            return;
        }


        if (
                !roomManager.isMember(
                        sessionId,
                        roomId,
                        participantId
                )
        ) {
            return;
        }


        System.out.println(
                "[MyMeet] WEBRTC ANSWER:"
                        + " from=" + participantId
                        + " to=" + targetParticipantId
        );


        messagingTemplate.convertAndSend(
                "/topic/meet/" + roomId,
                WebSocketEvent.answer(
                        roomId,
                        participantId,
                        targetParticipantId,
                        answer
                )
        );
    }


    /* =========================================================
       WEBRTC ICE
       ========================================================= */

    @MessageMapping("/webrtc/ice")
    public void ice(
            @Payload Map<String, Object> payload,
            SimpMessageHeaderAccessor headerAccessor
    ) {

        String sessionId =
                headerAccessor.getSessionId();

        String roomId =
                stringValue(payload, "roomId");

        String participantId =
                stringValue(payload, "participantId");

        String targetParticipantId =
                stringValue(
                        payload,
                        "targetParticipantId"
                );

        Object candidate =
                payload.get("candidate");


        if (
                sessionId == null
                        || roomId == null
                        || participantId == null
                        || targetParticipantId == null
                        || candidate == null
        ) {
            return;
        }


        if (
                !roomManager.isMember(
                        sessionId,
                        roomId,
                        participantId
                )
        ) {
            return;
        }


        System.out.println(
                "[MyMeet] WEBRTC ICE:"
                        + " from=" + participantId
                        + " to=" + targetParticipantId
        );


        messagingTemplate.convertAndSend(
                "/topic/meet/" + roomId,
                WebSocketEvent.ice(
                        roomId,
                        participantId,
                        targetParticipantId,
                        candidate
                )
        );
    }


    /* =========================================================
       STRING HELPER
       ========================================================= */

    private String stringValue(
            Map<String, Object> payload,
            String key
    ) {

        Object value =
                payload.get(key);


        if (value == null) {
            return null;
        }


        String result =
                value.toString().trim();


        return result.isEmpty()
                ? null
                : result;
    }


    /* =========================================================
       BOOLEAN HELPER
       ========================================================= */

    private boolean booleanValue(
            Map<String, Object> payload,
            String key,
            boolean defaultValue
    ) {

        Object value =
                payload.get(key);


        if (value == null) {
            return defaultValue;
        }


        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }


        return Boolean.parseBoolean(
                value.toString()
        );
    }
}