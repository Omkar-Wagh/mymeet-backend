package com.mymeet.model;

/*
 * ParticipantSession connects a WebSocket connection with
 * the participant and room represented by that connection.
 *
 * Relationship:
 *
 *     sessionId
 *         |
 *         v
 *     ParticipantSession
 *         |
 *         +---- roomId
 *         +---- participantId
 *
 * This allows the backend to determine who owns a particular
 * WebSocket connection.
 */
public class ParticipantSession {

    private final String sessionId;
    private final String roomId;
    private final String participantId;

    public ParticipantSession(
            String sessionId,
            String roomId,
            String participantId
    ) {
        this.sessionId = sessionId;
        this.roomId = roomId;
        this.participantId = participantId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getParticipantId() {
        return participantId;
    }
}