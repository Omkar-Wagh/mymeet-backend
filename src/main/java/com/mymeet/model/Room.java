package com.mymeet.model;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Room {

    private String roomId;

    private final Map<String, Participant> participants =
            new ConcurrentHashMap<>();

    public Room() {
    }

    public Room(String roomId) {
        this.roomId = roomId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public Map<String, Participant> getParticipants() {
        return participants;
    }

    public Collection<Participant> getParticipantList() {
        return participants.values();
    }

    /*
     * =========================================================
     * ADD PARTICIPANT
     * =========================================================
     */
    public void addParticipant(
            Participant participant
    ) {

        participants.put(
                participant.getParticipantId(),
                participant
        );
    }

    /*
     * =========================================================
     * REMOVE PARTICIPANT
     * =========================================================
     */
    public void removeParticipant(
            String participantId
    ) {

        participants.remove(
                participantId
        );
    }

    /*
     * =========================================================
     * CHECK ROOM EMPTY
     * =========================================================
     */
    public boolean isEmpty() {
        return participants.isEmpty();
    }
}