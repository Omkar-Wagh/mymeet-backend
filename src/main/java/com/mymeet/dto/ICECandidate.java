package com.mymeet.dto;

public class ICECandidate {

    private String roomId;
    private String participantId;
    private String targetParticipantId;
    private Object candidate;

    public ICECandidate() {
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

    public Object getCandidate() {
        return candidate;
    }

    public void setCandidate(Object candidate) {
        this.candidate = candidate;
    }
}