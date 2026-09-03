package com.mymeet.controller;

import com.mymeet.dto.JoinRequest;
import com.mymeet.dto.LeaveRequest;
import com.mymeet.dto.WebSocketEvent;
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
         * now uses the WebSocket session as the authoritative
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
             *
             * Current RoomManager signature:
             *
             * join(
             *     sessionId,
             *     roomId,
             *     participantId,
             *     name
             * )
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
             * This is sent for both:
             *
             * 1. newly joined participant
             * 2. repeated JOIN from the same session
             *
             * The frontend can use ROOM_STATE to initialize
             * the meeting participant list.
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
             * PARTICIPANT SESSION REPLACED
             * =====================================================
             *
             * The logical participant remains in the room, but the
             * old WebRTC session is no longer valid. Notify every
             * client so they can discard the old peer connection.
             * connectionId lets the newest tab distinguish itself
             * from the tab that was replaced.
             */

            if (result.replacedSession()) {

                messagingTemplate.convertAndSend(
                        "/topic/meet/" + request.getRoomId(),
                        WebSocketEvent.participantSessionReplaced(
                                request.getRoomId(),
                                request.getParticipantId(),
                                request.getConnectionId(),
                                result.participants()
                        )
                );
            }


            /*
             * =====================================================
             * PARTICIPANT JOINED
             * =====================================================
             *
             * Only broadcast this event when a participant
             * was actually added.
             *
             * If the same session sends JOIN again, RoomManager
             * returns newlyJoined=false.
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
             *
             * Current RoomManager signature:
             *
             * leave(
             *     sessionId,
             *     roomId,
             *     participantId
             * )
             */

            var removed =
                    roomManager.leave(
                            sessionId,
                            request.getRoomId(),
                            request.getParticipantId()
                    );


            /*
             * If null is returned, the participant was not
             * successfully removed.
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


        if (
                sessionId == null
                        || roomId == null
                        || participantId == null
        ) {
            return;
        }


        /*
         * Make sure the WebSocket session is actually
         * representing this participant.
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


        messagingTemplate.convertAndSend(
                "/topic/meet/" + roomId,
                WebSocketEvent.mediaStatus(
                        roomId,
                        participantId,
                        muted,
                        cameraOff
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


        if (
                sessionId == null
                        || roomId == null
                        || participantId == null
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


        messagingTemplate.convertAndSend(
                "/topic/meet/" + roomId,
                WebSocketEvent.handRaise(
                        roomId,
                        participantId,
                        handRaised
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


        if (
                sessionId == null
                        || roomId == null
                        || participantId == null
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


        messagingTemplate.convertAndSend(
                "/topic/meet/" + roomId,
                WebSocketEvent.screenShare(
                        roomId,
                        participantId,
                        screenSharing
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