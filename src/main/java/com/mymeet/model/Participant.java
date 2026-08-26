package com.mymeet.model;

/*
 * Participant represents a person currently present inside
 * a MyMeet room.
 *
 * A Participant is identified by participantId and has a
 * display name that can be shown in the meeting UI.
 *
 * Participant represents the logical meeting member.
 *
 * The WebSocket connection itself is represented separately
 * by ParticipantSession.
 */
public class Participant {

    private final String participantId;
    private final String name;

    public Participant(
            String participantId,
            String name
    ) {
        this.participantId = participantId;
        this.name = name;
    }

    public String getParticipantId() {
        return participantId;
    }

    public String getName() {
        return name;
    }
}