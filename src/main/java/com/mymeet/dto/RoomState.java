package com.mymeet.dto;

import com.mymeet.model.Participant;

import java.util.Collection;

public class RoomState {

    private String roomId;
    private Collection<Participant> participants;

    public RoomState() {
    }

    public RoomState(
            String roomId,
            Collection<Participant> participants
    ) {
        this.roomId = roomId;
        this.participants = participants;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public Collection<Participant> getParticipants() {
        return participants;
    }

    public void setParticipants(Collection<Participant> participants) {
        this.participants = participants;
    }
}