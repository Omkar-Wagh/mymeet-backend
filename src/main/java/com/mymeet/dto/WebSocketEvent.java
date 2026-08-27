package com.mymeet.dto;

import com.mymeet.model.Participant;

import java.util.List;

public record WebSocketEvent(

        String type,

        String roomId,

        String participantId,

        String targetParticipantId,

        String name,

        String message,

        List<Participant> participants,

        Object offer,

        Object answer,

        Object candidate,

        Boolean muted,

        Boolean cameraOff,

        Boolean handRaised,

        Boolean screenSharing,

        String emoji

) {


    /* =========================================================
       ROOM STATE
       ========================================================= */

    public static WebSocketEvent roomState(
            String roomId,
            List<Participant> participants
    ) {

        return new WebSocketEvent(
                "ROOM_STATE",
                roomId,
                null,
                null,
                null,
                null,
                participants,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }


    /* =========================================================
       PARTICIPANT JOINED
       ========================================================= */

    public static WebSocketEvent participantJoined(
            String roomId,
            String participantId,
            String name
    ) {

        return new WebSocketEvent(
                "PARTICIPANT_JOINED",
                roomId,
                participantId,
                null,
                name,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }


    /* =========================================================
       PARTICIPANT LEFT
       ========================================================= */

    public static WebSocketEvent participantLeft(
            String roomId,
            String participantId
    ) {

        return new WebSocketEvent(
                "PARTICIPANT_LEFT",
                roomId,
                participantId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }


    /* =========================================================
       JOIN REJECTED
       ========================================================= */

    public static WebSocketEvent joinRejected(
            String roomId,
            String participantId,
            String message
    ) {

        return new WebSocketEvent(
                "JOIN_REJECTED",
                roomId,
                participantId,
                null,
                null,
                message,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }


    /* =========================================================
       CHAT
       ========================================================= */

    public static WebSocketEvent chatMessage(
            String roomId,
            String participantId,
            String message,
            String name
    ) {

        return new WebSocketEvent(
                "CHAT_MESSAGE",
                roomId,
                participantId,
                null,
                name,
                message,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }


    /* =========================================================
       MEDIA STATUS
       ========================================================= */

    public static WebSocketEvent mediaStatus(
            String roomId,
            String participantId,
            boolean muted,
            boolean cameraOff
    ) {

        return new WebSocketEvent(
                "MEDIA_STATUS_UPDATE",
                roomId,
                participantId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                muted,
                cameraOff,
                null,
                null,
                null
        );
    }


    /* =========================================================
       HAND RAISE
       ========================================================= */

    public static WebSocketEvent handRaise(
            String roomId,
            String participantId,
            boolean handRaised
    ) {

        return new WebSocketEvent(
                "HAND_RAISE_TOGGLE",
                roomId,
                participantId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                handRaised,
                null,
                null
        );
    }


    /* =========================================================
       SCREEN SHARE
       ========================================================= */

    public static WebSocketEvent screenShare(
            String roomId,
            String participantId,
            boolean screenSharing
    ) {

        return new WebSocketEvent(
                "SCREEN_SHARE_STATUS",
                roomId,
                participantId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                screenSharing,
                null
        );
    }


    /* =========================================================
       REACTION
       ========================================================= */

    public static WebSocketEvent reaction(
            String roomId,
            String participantId,
            String emoji
    ) {

        return new WebSocketEvent(
                "REACTION",
                roomId,
                participantId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                emoji
        );
    }


    /* =========================================================
       WEBRTC OFFER
       ========================================================= */

    public static WebSocketEvent offer(
            String roomId,
            String participantId,
            String targetParticipantId,
            Object offer
    ) {

        return new WebSocketEvent(
                "WEBRTC_OFFER",
                roomId,
                participantId,
                targetParticipantId,
                null,
                null,
                null,
                offer,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }


    /* =========================================================
       WEBRTC ANSWER
       ========================================================= */

    public static WebSocketEvent answer(
            String roomId,
            String participantId,
            String targetParticipantId,
            Object answer
    ) {

        return new WebSocketEvent(
                "WEBRTC_ANSWER",
                roomId,
                participantId,
                targetParticipantId,
                null,
                null,
                null,
                null,
                answer,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }


    /* =========================================================
       WEBRTC ICE
       ========================================================= */

    public static WebSocketEvent ice(
            String roomId,
            String participantId,
            String targetParticipantId,
            Object candidate
    ) {

        return new WebSocketEvent(
                "WEBRTC_ICE",
                roomId,
                participantId,
                targetParticipantId,
                null,
                null,
                null,
                null,
                null,
                candidate,
                null,
                null,
                null,
                null,
                null
        );
    }
}