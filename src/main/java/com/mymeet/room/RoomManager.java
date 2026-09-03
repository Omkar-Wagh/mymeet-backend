package com.mymeet.room;

import com.mymeet.model.Participant;
import com.mymeet.model.ParticipantSession;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/*
 * RoomManager is the authoritative in-memory state manager
 * for MyMeet rooms.
 *
 * It owns:
 *
 *     room
 *       |
 *       +-- participant
 *       |
 *       +-- participant realtime state
 *
 *
 * Participant state includes:
 *
 *     muted
 *     cameraOff
 *     screenSharing
 *     handRaised
 *
 *
 * WebRTC media itself is NOT stored here.
 *
 * RoomManager only stores the metadata/state required to keep
 * every participant synchronized.
 */
@Component
public class RoomManager {


    /*
     * =========================================================
     * ROOMS
     * =========================================================
     *
     * roomId
     *     |
     *     +-- participantId -> Participant
     *
     * HashMap is used together with synchronized methods so
     * access to the complete in-memory state is thread-safe.
     */
    private final Map<String, Map<String, Participant>> rooms =
            new HashMap<>();


    /*
     * =========================================================
     * SESSIONS
     * =========================================================
     *
     * sessionId
     *     |
     *     +-- ParticipantSession
     */
    private final Map<String, ParticipantSession> sessions =
            new HashMap<>();


    /*
     * =========================================================
     * PARTICIPANT SESSIONS
     * =========================================================
     *
     * participantId
     *     |
     *     +-- sessionId
     *
     * This allows a participant to reconnect using a new
     * WebSocket session without creating a second Participant.
     */
    private final Map<String, String> participantSessions =
            new HashMap<>();


    /* =========================================================
       JOIN
       ========================================================= */

    public synchronized JoinResult join(
            String sessionId,
            String roomId,
            String participantId,
            String name
    ) {

        /*
         * Basic validation.
         */
        if (
                sessionId == null ||
                        sessionId.isBlank() ||
                        roomId == null ||
                        roomId.isBlank() ||
                        participantId == null ||
                        participantId.isBlank()
        ) {

            return new JoinResult(
                    false,
                    false,
                    true,
                    Collections.emptyList()
            );
        }


        /*
         * =====================================================
         * EXISTING STOMP SESSION
         * =====================================================
         *
         * Prevent the same WebSocket session from representing
         * multiple participants.
         */
        ParticipantSession existingSession =
                sessions.get(sessionId);

        if (existingSession != null) {

            /*
             * Same session + same room + same participant.
             *
             * This is a repeated JOIN.
             *
             * Do NOT create a new Participant.
             *
             * Do NOT reset participant state.
             */
            if (
                    existingSession.getRoomId().equals(roomId)
                            &&
                            existingSession
                                    .getParticipantId()
                                    .equals(participantId)
            ) {

                return new JoinResult(
                        false,
                        false,
                        false,
                        snapshot(roomId)
                );
            }


            /*
             * Same WebSocket session attempting to represent
             * another participant or another room.
             */
            return new JoinResult(
                    false,
                    false,
                    true,
                    snapshot(roomId)
            );
        }


        /*
         * =====================================================
         * EXISTING PARTICIPANT SESSION
         * =====================================================
         *
         * If this participant already has a WebSocket session,
         * treat the new connection as a reconnect/replacement.
         *
         * IMPORTANT:
         *
         * The Participant object is NOT removed from the room.
         *
         * Therefore the following state survives reconnect:
         *
         *     muted
         *     cameraOff
         *     screenSharing
         *     handRaised
         *
         * The old WebSocket session mapping is replaced only.
         */
        String existingSessionId =
                participantSessions.get(participantId);

        if (
                existingSessionId != null
                        &&
                        !existingSessionId.equals(sessionId)
        ) {

            ParticipantSession oldSession =
                    sessions.get(existingSessionId);


            /*
             * =================================================
             * PARTICIPANT BELONGS TO ANOTHER ROOM
             * =================================================
             *
             * Do not allow the same participantId to replace
             * an active participant in another room.
             */
            if (
                    oldSession != null
                            &&
                            !oldSession.getRoomId().equals(roomId)
            ) {

                return new JoinResult(
                        false,
                        true,
                        false,
                        snapshot(roomId)
                );
            }


            /*
             * =================================================
             * STALE REVERSE MAPPING
             * =================================================
             *
             * participantSessions says that a session exists,
             * but the actual session is gone.
             *
             * Remove only the stale reverse mapping and allow
             * normal participant creation below.
             */
            if (oldSession == null) {

                participantSessions.remove(
                        participantId,
                        existingSessionId
                );

            } else {

                /*
                 * =================================================
                 * SESSION REPLACEMENT / RECONNECT
                 * =================================================
                 *
                 * Remove ONLY the old WebSocket session.
                 *
                 * IMPORTANT:
                 *
                 * DO NOT remove the Participant from rooms.
                 *
                 * The existing Participant object contains the
                 * authoritative realtime participant state.
                 */
                sessions.remove(existingSessionId);


                ParticipantSession replacement =
                        new ParticipantSession(
                                sessionId,
                                roomId,
                                participantId
                        );


                sessions.put(
                        sessionId,
                        replacement
                );


                participantSessions.put(
                        participantId,
                        sessionId
                );


                /*
                 * Return the existing room snapshot.
                 *
                 * This snapshot contains the participant's
                 * current authoritative state.
                 */
                return new JoinResult(
                        false,
                        false,
                        false,
                        snapshot(roomId)
                );
            }
        }


        /*
         * =====================================================
         * NEW PARTICIPANT
         * =====================================================
         */

        String safeName =
                name == null || name.isBlank()
                        ? "Guest"
                        : name.trim();


        /*
         * A genuinely new participant starts with the default
         * Participant state defined by the Participant model.
         *
         * This default is used ONLY when creating a new
         * participant.
         *
         * Existing participants are NEVER recreated during
         * state updates or reconnects.
         */
        Participant participant =
                new Participant(
                        participantId,
                        safeName
                );


        ParticipantSession participantSession =
                new ParticipantSession(
                        sessionId,
                        roomId,
                        participantId
                );


        /*
         * Create room only when it does not already exist.
         *
         * LinkedHashMap preserves participant insertion order
         * in ROOM_STATE snapshots.
         */
        Map<String, Participant> room =
                rooms.computeIfAbsent(
                        roomId,
                        ignored -> new LinkedHashMap<>()
                );


        room.put(
                participantId,
                participant
        );


        sessions.put(
                sessionId,
                participantSession
        );


        participantSessions.put(
                participantId,
                sessionId
        );


        return new JoinResult(
                true,
                false,
                false,
                snapshot(roomId)
        );
    }


    /* =========================================================
       UPDATE COMPLETE PARTICIPANT STATE
       ========================================================= */

    /*
     * Updates all realtime state fields together.
     *
     * This method is intentionally kept available for cases
     * where the COMPLETE participant state is known.
     *
     * IMPORTANT:
     *
     * Do NOT use this method for partial events such as:
     *
     *     media-status
     *     hand-raise
     *     screen-share
     *
     * because those events do not necessarily contain every
     * participant state field.
     *
     * The partial update methods below must be used instead.
     */
    public synchronized Participant updateParticipantState(
            String roomId,
            String participantId,
            boolean muted,
            boolean cameraOff,
            boolean screenSharing,
            boolean handRaised
    ) {

        Map<String, Participant> room =
                rooms.get(roomId);

        if (room == null) {
            return null;
        }


        Participant participant =
                room.get(participantId);

        if (participant == null) {
            return null;
        }


        participant.setMuted(muted);

        participant.setCameraOff(cameraOff);

        participant.setScreenSharing(screenSharing);

        participant.setHandRaised(handRaised);


        return participant;
    }


    /* =========================================================
       UPDATE MEDIA STATE
       ========================================================= */

    /*
     * Updates ONLY:
     *
     *     muted
     *     cameraOff
     *
     * The following fields are intentionally preserved:
     *
     *     screenSharing
     *     handRaised
     *
     * This prevents a media-status event from accidentally
     * resetting unrelated participant state.
     */
    public synchronized Participant updateMediaState(
            String roomId,
            String participantId,
            boolean muted,
            boolean cameraOff
    ) {

        Map<String, Participant> room =
                rooms.get(roomId);

        if (room == null) {
            return null;
        }


        Participant participant =
                room.get(participantId);

        if (participant == null) {
            return null;
        }


        /*
         * Update ONLY media state.
         */
        participant.setMuted(muted);

        participant.setCameraOff(cameraOff);


        /*
         * screenSharing and handRaised remain untouched.
         */
        return participant;
    }


    /* =========================================================
       UPDATE HAND RAISED
       ========================================================= */

    /*
     * Updates ONLY:
     *
     *     handRaised
     *
     * The following fields are intentionally preserved:
     *
     *     muted
     *     cameraOff
     *     screenSharing
     */
    public synchronized Participant updateHandRaised(
            String roomId,
            String participantId,
            boolean handRaised
    ) {

        Map<String, Participant> room =
                rooms.get(roomId);

        if (room == null) {
            return null;
        }


        Participant participant =
                room.get(participantId);

        if (participant == null) {
            return null;
        }


        /*
         * Update ONLY hand-raise state.
         */
        participant.setHandRaised(handRaised);


        /*
         * All other participant state remains unchanged.
         */
        return participant;
    }


    /* =========================================================
       UPDATE SCREEN SHARING
       ========================================================= */

    /*
     * Updates ONLY:
     *
     *     screenSharing
     *
     * The following fields are intentionally preserved:
     *
     *     muted
     *     cameraOff
     *     handRaised
     */
    public synchronized Participant updateScreenSharing(
            String roomId,
            String participantId,
            boolean screenSharing
    ) {

        Map<String, Participant> room =
                rooms.get(roomId);

        if (room == null) {
            return null;
        }


        Participant participant =
                room.get(participantId);

        if (participant == null) {
            return null;
        }


        /*
         * Update ONLY screen-sharing state.
         */
        participant.setScreenSharing(screenSharing);


        /*
         * All other participant state remains unchanged.
         */
        return participant;
    }


    /* =========================================================
       LEAVE BY SESSION
       ========================================================= */

    public synchronized ParticipantSession leaveBySession(
            String sessionId
    ) {

        ParticipantSession session =
                sessions.get(sessionId);

        if (session == null) {
            return null;
        }


        return leave(
                sessionId,
                session.getRoomId(),
                session.getParticipantId()
        );
    }


    /* =========================================================
       EXPLICIT LEAVE
       ========================================================= */

    public synchronized ParticipantSession leave(
            String sessionId,
            String roomId,
            String participantId
    ) {

        ParticipantSession session =
                sessions.get(sessionId);

        if (session == null) {
            return null;
        }


        /*
         * A connection can remove only itself.
         */
        if (
                !session.getRoomId().equals(roomId)
                        ||
                        !session.getParticipantId().equals(participantId)
        ) {
            return null;
        }


        ParticipantSession removed =
                sessions.remove(sessionId);

        if (removed == null) {
            return null;
        }


        /*
         * Remove reverse participant -> session mapping only
         * when it still points to this session.
         *
         * This prevents an old session from accidentally removing
         * the mapping of a newer replacement session.
         */
        participantSessions.remove(
                participantId,
                sessionId
        );


        Map<String, Participant> room =
                rooms.get(roomId);

        if (room != null) {

            room.remove(participantId);


            /*
             * Remove empty rooms.
             */
            if (room.isEmpty()) {

                rooms.remove(
                        roomId,
                        room
                );
            }
        }


        return removed;
    }


    /* =========================================================
       SNAPSHOT
       ========================================================= */

    public synchronized List<Participant> snapshot(
            String roomId
    ) {

        Map<String, Participant> room =
                rooms.get(roomId);

        if (room == null || room.isEmpty()) {
            return Collections.emptyList();
        }


        /*
         * Return a new list so callers cannot directly modify
         * the internal room collection.
         *
         * The Participant objects themselves are the authoritative
         * objects stored by RoomManager.
         */
        return new ArrayList<>(
                room.values()
        );
    }


    /* =========================================================
       GET SESSION
       ========================================================= */

    public synchronized ParticipantSession getSession(
            String sessionId
    ) {

        return sessions.get(sessionId);
    }


    /* =========================================================
       GET PARTICIPANT
       ========================================================= */

    public synchronized Participant getParticipant(
            String sessionId
    ) {

        ParticipantSession session =
                sessions.get(sessionId);

        if (session == null) {
            return null;
        }


        Map<String, Participant> room =
                rooms.get(
                        session.getRoomId()
                );

        if (room == null) {
            return null;
        }


        return room.get(
                session.getParticipantId()
        );
    }


    /* =========================================================
       MEMBERSHIP CHECK
       ========================================================= */

    public synchronized boolean isMember(
            String sessionId,
            String roomId,
            String participantId
    ) {

        ParticipantSession session =
                sessions.get(sessionId);

        if (session == null) {
            return false;
        }


        return
                session.getRoomId().equals(roomId)
                        &&
                        session.getParticipantId()
                                .equals(participantId);
    }


    /* =========================================================
       PARTICIPANT EXISTS
       ========================================================= */

    public synchronized boolean isParticipantInRoom(
            String roomId,
            String participantId
    ) {

        Map<String, Participant> room =
                rooms.get(roomId);

        if (room == null) {
            return false;
        }


        return room.containsKey(
                participantId
        );
    }


    /* =========================================================
       GET PARTICIPANTS
       ========================================================= */

    public synchronized List<Participant> getParticipants(
            String roomId
    ) {

        return snapshot(roomId);
    }


    /* =========================================================
       JOIN RESULT
       ========================================================= */

    public record JoinResult(
            boolean newlyJoined,
            boolean duplicateParticipant,
            boolean invalidSession,
            List<Participant> participants
    ) {
    }
}