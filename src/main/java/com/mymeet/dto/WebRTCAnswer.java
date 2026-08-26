package com.mymeet.dto;

public class WebRTCAnswer {

    private String roomId;
    private String participantId;
    private String targetParticipantId;
    private Object answer;

    public WebRTCAnswer() {
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    public String getTargetParticipantId() {
        return targetParticipantId;
    }

    public void setTargetParticipantId(String targetParticipantId) {
        this.targetParticipantId = targetParticipantId;
    }

    public Object getAnswer() {
        return answer;
    }

    public void setAnswer(Object answer) {
        this.answer = answer;
    }
}