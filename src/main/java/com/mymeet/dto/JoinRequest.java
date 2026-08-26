package com.mymeet.dto;

public class JoinRequest {

    private String roomId;
    private String participantId;
    private String name;


    public JoinRequest() {
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


    public String getName() {
        return name;
    }


    public void setName(String name) {
        this.name = name;
    }
}