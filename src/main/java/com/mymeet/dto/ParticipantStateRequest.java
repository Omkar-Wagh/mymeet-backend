package com.mymeet.dto;


/*
 * ParticipantStateRequest represents a realtime participant-state
 * update sent by the frontend.
 *
 * Frontend destination:
 *
 *     /app/meet/state
 *
 *
 * The request represents the participant's desired current state:
 *
 *     muted
 *     cameraOff
 *     screenSharing
 *     handRaised
 *
 *
 * IMPORTANT:
 *
 * participantId is still validated against the WebSocket session.
 *
 * The backend never trusts participantId simply because the client
 * supplied it.
 */
public record ParticipantStateRequest(

        String roomId,

        String participantId,

        boolean muted,

        boolean cameraOff,

        boolean screenSharing,

        boolean handRaised

) {
}