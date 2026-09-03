package com.mymeet.room;

import com.mymeet.model.Participant;
import com.mymeet.model.ParticipantSession;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


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
     * roomId
     *     |
     *     +-- participantId -> Participant
     */
    private final Map<String, Map<String, Participant>> rooms =
            new ConcurrentHashMap<>();


    /*
     * sessionId
     *     |
     *     +-- ParticipantSession
     */
    private final Map<String, ParticipantSession> sessions =
            new ConcurrentHashMap<>();


    /*
     * participantId
     *     |
     *     +-- sessionId
     */
    private final Map<String, String> participantSessions =
            new ConcurrentHashMap<>();


    /* =========================================================
       JOIN
       ========================================================= */

    public synchronized JoinResult join(
            String sessionId,
            String roomId,
            String participantId,
            String name
    ) {

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
                    false,
                    Collections.emptyList()
            );
        }


        /*
         * Prevent the same WebSocket session from representing
         * multiple participants.
         */
        ParticipantSession existingSession =
                sessions.get(sessionId);

        if (existingSession != null) {

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
                        false,
                        snapshot(roomId)
                );
            }


            return new JoinResult(
                    false,
                    false,
                    true,
                    false,
                    snapshot(roomId)
            );
        }


        /*
         * If this participant already has a WebSocket session,
         * treat the new connection as a reconnect/replacement.
         *
         * The participant itself remains in the room. Only the
         * old WebSocket session mapping is replaced.
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
             * Do not allow a participantId currently associated
             * with another room to replace that connection.
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
                        false,
                        snapshot(roomId)
                );
            }

            /*
             * If the reverse mapping is stale, remove it and
             * continue as a normal new participant join.
             */
            if (oldSession == null) {

                participantSessions.remove(
                        participantId,
                        existingSessionId
                );

            } else {

                /*
                 * Remove only the old WebSocket session.
                 *
                 * IMPORTANT:
                 * Do NOT remove the Participant from rooms.
                 * The existing Participant object contains the
                 * authoritative realtime state and must survive
                 * the reconnect.
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

                return new JoinResult(
                        false,
                        false,
                        false,
                        true,
                        snapshot(roomId)
                );
            }
        }

        String safeName =
                name == null || name.isBlank()
                        ? "Guest"
                        : name.trim();


        /*
         * New participants always start with the default state:
         *
         * muted         = false
         * cameraOff     = false
         * screenSharing = false
         * handRaised    = false
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
                false,
                snapshot(roomId)
        );
    }


    /* =========================================================
       UPDATE PARTICIPANT STATE
       ========================================================= */

    /*
     * Updates the authoritative realtime state of a participant.
     *
     * Returns the updated Participant.
     *
     * Returns null if:
     *
     * - room does not exist
     * - participant does not exist
     *
     *
     * This method is synchronized because several participant
     * fields are changed together as one logical state update.
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


        participantSessions.remove(
                participantId,
                sessionId
        );


        Map<String, Participant> room =
                rooms.get(roomId);

        if (room != null) {

            room.remove(participantId);


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
         * Return a copy.
         *
         * This prevents callers from modifying the internal
         * room collection directly.
         */
        return new ArrayList<>(
                room.values()
        );
    }


    /* =========================================================
       GET SESSION
       ========================================================= */

    public ParticipantSession getSession(
            String sessionId
    ) {

        return sessions.get(sessionId);
    }


    /* =========================================================
       GET PARTICIPANT
       ========================================================= */

    public Participant getParticipant(
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

    public boolean isMember(
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

    public boolean isParticipantInRoom(
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
            boolean replacedSession,
            List<Participant> participants
    ) {
    }
}