package com.mymeet.dto;

public class ChatMessage {

    private String roomId;
    private String participantId;
    private String name;
    private String message;

    public ChatMessage() {
    }

    public ChatMessage(
            String roomId,
            String participantId,
            String name,
            String message
    ) {
        this.roomId = roomId;
        this.participantId = participantId;
        this.name = name;
        this.message = message;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getParticipantId() {
        return participantId;
    }

    public String getName() {
        return name;
    }

    public String getMessage() {
        return message;
    }
}