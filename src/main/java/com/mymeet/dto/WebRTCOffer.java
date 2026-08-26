package com.mymeet.dto;

public class WebRTCOffer {

    private String roomId;
    private String participantId;
    private String targetParticipantId;
    private Object offer;

    public WebRTCOffer() {
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

    public Object getOffer() {
        return offer;
    }

    public void setOffer(Object offer) {
        this.offer = offer;
    }
}