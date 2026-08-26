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

        Object candidate

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
                null
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
                candidate
        );
    }
}