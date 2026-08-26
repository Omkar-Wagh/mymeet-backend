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
 * RoomManager is the in-memory state manager for MyMeet rooms.
 *
 * MyMeet intentionally does not use a database for meeting state.
 * Therefore, while the backend application is running, RoomManager
 * keeps track of:
 *
 * - Active rooms
 * - Participants inside each room
 * - WebSocket sessions
 * - Which participant owns which WebSocket session
 *
 *
 * Overall relationship:
 *
 *     roomId
 *        |
 *        +---- participantId -> Participant
 *
 *     sessionId
 *        |
 *        +---- ParticipantSession
 *
 *     participantId
 *        |
 *        +---- sessionId
 *
 *
 * These three mappings work together to answer questions such as:
 *
 * - Who is currently in this room?
 * - Which participant owns this WebSocket connection?
 * - Does this participant actually belong to this room?
 * - Is this participantId already being used?
 *
 *
 * Since multiple WebSocket clients can interact with the backend
 * concurrently, the underlying maps use ConcurrentHashMap.
 *
 * Some operations are additionally synchronized because they modify
 * multiple maps together and therefore need to behave as one
 * atomic room-management operation.
 */
@Component
public class RoomManager {


    /*
     * =========================================================
     * ROOM MEMBERS
     * =========================================================
     *
     * Stores the participants belonging to each room.
     *
     * Structure:
     *
     *     roomId
     *        |
     *        v
     *     participantId -> Participant
     *
     * Example:
     *
     *     room-123
     *        |
     *        +---- uuid-tab-1 -> Nana
     *        +---- uuid-tab-2 -> Omkar
     *        +---- uuid-tab-3 -> Rahul
     *
     *
     * The participantId is used as the key because it uniquely
     * identifies a participant within the room.
     *
     * A nested map makes participant lookup efficient:
     *
     *     rooms.get(roomId).get(participantId)
     *
     *
     * Each browser tab generates its own participantId, so two
     * tabs opened by the same user can still represent different
     * participants.
     */
    private final Map<String, Map<String, Participant>> rooms =
            new ConcurrentHashMap<>();


    /*
     * =========================================================
     * WEBSOCKET SESSION MAPPING
     * =========================================================
     *
     * Maps the actual WebSocket session to the participant
     * represented by that connection.
     *
     * Structure:
     *
     *     WebSocket sessionId
     *             |
     *             v
     *     ParticipantSession
     *
     * ParticipantSession contains information such as:
     *
     * - sessionId
     * - roomId
     * - participantId
     *
     *
     * This mapping is important because the sessionId comes from
     * the WebSocket connection itself and is therefore a trusted
     * backend-side identity for the current connection.
     */
    private final Map<String, ParticipantSession> sessions =
            new ConcurrentHashMap<>();


    /*
     * =========================================================
     * PARTICIPANT OWNERSHIP
     * =========================================================
     *
     * Maps:
     *
     *     participantId
     *            |
     *            v
     *     WebSocket sessionId
     *
     *
     * This mapping prevents the SAME participantId from being
     * simultaneously represented by multiple WebSocket connections.
     *
     * Example:
     *
     *     Tab 1 -> UUID-A -> session-1
     *     Tab 2 -> UUID-B -> session-2
     *
     * Both are valid because they have different participant IDs.
     *
     *
     * But:
     *
     *     Tab 1 -> UUID-A -> session-1
     *     Tab 2 -> UUID-A -> session-2
     *
     * is rejected because UUID-A is already owned by session-1.
     *
     *
     * Normally collisions should be extremely unlikely because
     * the frontend generates participant IDs using:
     *
     *     crypto.randomUUID()
     *
     * However, the backend still validates ownership because the
     * client-provided participantId must never be blindly trusted.
     */
    private final Map<String, String> participantSessions =
            new ConcurrentHashMap<>();


    /* =========================================================
       JOIN
       ========================================================= */

    /*
     * Adds a participant to a room.
     *
     * This method performs several operations that must remain
     * consistent with each other:
     *
     * 1. Validate the request.
     * 2. Check the WebSocket session.
     * 3. Check participant ownership.
     * 4. Create the participant.
     * 5. Create the participant session.
     * 6. Create the room if necessary.
     * 7. Add the participant to the room.
     * 8. Register the WebSocket session.
     * 9. Register participant ownership.
     * 10. Return the updated room snapshot.
     *
     * The method is synchronized because these operations modify
     * multiple shared maps.
     *
     * Without synchronization, two concurrent JOIN requests could
     * potentially observe intermediate state and produce inconsistent
     * mappings.
     */
    public synchronized JoinResult join(
            String sessionId,
            String roomId,
            String participantId,
            String name
    ) {

        /*
         * -----------------------------------------------------
         * Validate input
         * -----------------------------------------------------
         *
         * Basic validation is performed before changing any
         * application state.
         */
        if (
                sessionId == null ||
                        sessionId.isBlank() ||
                        roomId == null ||
                        roomId.isBlank() ||
                        participantId == null ||
                        participantId.isBlank()
        ) {

            /*
             * The request cannot safely be processed.
             *
             * invalidSession = true is used by the caller to
             * distinguish an invalid join attempt from a normal
             * duplicate join.
             */
            return new JoinResult(
                    false,
                    false,
                    true,
                    Collections.emptyList()
            );
        }


        /*
         * -----------------------------------------------------
         * Check whether this WebSocket already joined
         * -----------------------------------------------------
         *
         * A single WebSocket connection should represent only
         * one participant.
         */
        ParticipantSession existingSession =
                sessions.get(sessionId);

        if (existingSession != null) {

            /*
             * Same WebSocket + same participant + same room.
             *
             * This means the frontend sent JOIN more than once
             * for the same connection.
             *
             * We treat this as a duplicate JOIN rather than creating
             * another participant.
             */
            if (
                    existingSession.getRoomId().equals(roomId)
                            &&
                            existingSession
                                    .getParticipantId()
                                    .equals(participantId)
            ) {

                /*
                 * newlyJoined = false
                 * duplicateParticipant = false
                 * invalidSession = false
                 *
                 * The existing room snapshot is returned so the
                 * caller still has access to the current room state.
                 */
                return new JoinResult(
                        false,
                        false,
                        false,
                        snapshot(roomId)
                );
            }


            /*
             * One WebSocket connection cannot represent multiple
             * participants or switch its identity arbitrarily.
             *
             * Therefore another JOIN using the same session is
             * rejected.
             */
            return new JoinResult(
                    false,
                    false,
                    true,
                    snapshot(roomId)
            );
        }


        /*
         * -----------------------------------------------------
         * Check participantId ownership
         * -----------------------------------------------------
         *
         * The participantId must not already belong to another
         * WebSocket session.
         *
         * Valid:
         *
         *     Tab 1 -> UUID-A
         *     Tab 2 -> UUID-B
         *
         *
         * Invalid:
         *
         *     Tab 1 -> UUID-A
         *     Tab 2 -> UUID-A
         *
         * The second connection is rejected.
         */
        String existingSessionId =
                participantSessions.get(participantId);

        if (
                existingSessionId != null
                        &&
                        !existingSessionId.equals(sessionId)
        ) {

            /*
             * duplicateParticipant = true tells the controller
             * that this participantId is already being used by
             * another WebSocket connection.
             */
            return new JoinResult(
                    false,
                    true,
                    false,
                    snapshot(roomId)
            );
        }


        /*
         * -----------------------------------------------------
         * Normalize name
         * -----------------------------------------------------
         *
         * Participant names are user-provided data.
         *
         * If no valid name is supplied, use "Guest" so the
         * Participant object always has a usable display name.
         */
        String safeName =
                name == null || name.isBlank()
                        ? "Guest"
                        : name.trim();


        /*
         * -----------------------------------------------------
         * Create participant
         * -----------------------------------------------------
         *
         * Participant represents the logical participant visible
         * inside the meeting room.
         */
        Participant participant =
                new Participant(
                        participantId,
                        safeName
                );


        /*
         * -----------------------------------------------------
         * Create participant session
         * -----------------------------------------------------
         *
         * ParticipantSession connects the WebSocket connection
         * with the logical participant.
         *
         * This distinction is important:
         *
         *     Participant
         *         = who is in the meeting
         *
         *     ParticipantSession
         *         = which WebSocket connection represents them
         */
        ParticipantSession participantSession =
                new ParticipantSession(
                        sessionId,
                        roomId,
                        participantId
                );


        /*
         * -----------------------------------------------------
         * Create room if required
         * -----------------------------------------------------
         *
         * MyMeet does not create rooms through a separate database
         * operation.
         *
         * The first participant joining a room causes the room to
         * be created in memory.
         *
         * computeIfAbsent means:
         *
         *     If room exists -> return existing room.
         *     If room does not exist -> create and store a new map.
         */
        Map<String, Participant> room =
                rooms.computeIfAbsent(
                        roomId,
                        ignored ->
                                new LinkedHashMap<>()
                );


        /*
         * -----------------------------------------------------
         * Add participant to room
         * -----------------------------------------------------
         *
         * The participant is now part of the room's in-memory
         * participant collection.
         */
        room.put(
                participantId,
                participant
        );


        /*
         * -----------------------------------------------------
         * Register WebSocket session
         * -----------------------------------------------------
         *
         * From this point onward, the backend can answer:
         *
         *     "Which participant belongs to this WebSocket?"
         *
         * by looking up:
         *
         *     sessions.get(sessionId)
         */
        sessions.put(
                sessionId,
                participantSession
        );


        /*
         * -----------------------------------------------------
         * Register participant ownership
         * -----------------------------------------------------
         *
         * This creates the reverse identity relationship:
         *
         *     participantId -> sessionId
         *
         * It allows the backend to detect duplicate participant IDs
         * across multiple WebSocket connections.
         */
        participantSessions.put(
                participantId,
                sessionId
        );


        /*
         * -----------------------------------------------------
         * Return updated room state
         * -----------------------------------------------------
         *
         * The caller needs the current participants so that the
         * backend can broadcast ROOM_STATE to the room.
         *
         * A snapshot is returned instead of exposing the internal
         * room map directly.
         */
        return new JoinResult(
                true,
                false,
                false,
                snapshot(roomId)
        );
    }


    /* =========================================================
       LEAVE BY SESSION
       ========================================================= */

    /*
     * Removes a participant when only the WebSocket session ID
     * is known.
     *
     * This is particularly useful during:
     *
     *     SessionDisconnectEvent
     *
     * because the disconnect event gives us the session ID.
     *
     * The method first finds the ParticipantSession and then
     * delegates to the normal leave operation.
     */
    public synchronized ParticipantSession leaveBySession(
            String sessionId
    ) {

        ParticipantSession session =
                sessions.get(sessionId);

        if (session == null) {
            return null;
        }

        /*
         * ParticipantSession already contains the roomId and
         * participantId, so the caller does not need to supply
         * those values separately.
         */
        return leave(
                sessionId,
                session.getRoomId(),
                session.getParticipantId()
        );
    }


    /* =========================================================
       EXPLICIT LEAVE
       ========================================================= */

    /*
     * Removes a participant from a room when the participant
     * explicitly sends:
     *
     *     /app/meet/leave
     *
     * The same method is also reused by leaveBySession().
     *
     * This keeps the actual removal logic in one place.
     */
    public synchronized ParticipantSession leave(
            String sessionId,
            String roomId,
            String participantId
    ) {

        /*
         * Retrieve the participant associated with this
         * WebSocket session.
         */
        ParticipantSession session =
                sessions.get(sessionId);

        if (session == null) {
            return null;
        }


        /*
         * -----------------------------------------------------
         * Security / consistency check
         * -----------------------------------------------------
         *
         * A WebSocket connection can remove ONLY its own
         * participant.
         *
         * The client cannot provide another participant's
         * participantId and remove that participant.
         *
         * The server compares the requested identity with the
         * identity already associated with the WebSocket session.
         */
        if (
                !session.getRoomId().equals(roomId)
                        ||
                        !session.getParticipantId().equals(participantId)
        ) {
            return null;
        }


        /*
         * -----------------------------------------------------
         * Remove WebSocket session
         * -----------------------------------------------------
         *
         * After this operation, the backend no longer considers
         * this WebSocket session associated with the participant.
         */
        ParticipantSession removed =
                sessions.remove(sessionId);

        if (removed == null) {
            return null;
        }


        /*
         * -----------------------------------------------------
         * Remove participant ownership
         * -----------------------------------------------------
         *
         * The second argument of remove() is important.
         *
         *     remove(key, value)
         *
         * removes the mapping only when the participantId still
         * belongs to this exact sessionId.
         *
         * This protects the mapping from accidentally removing a
         * newer session that might have been registered later.
         */
        participantSessions.remove(
                participantId,
                sessionId
        );


        /*
         * -----------------------------------------------------
         * Remove participant from room
         * -----------------------------------------------------
         */
        Map<String, Participant> room =
                rooms.get(roomId);

        if (room != null) {

            room.remove(participantId);


            /*
             * -------------------------------------------------
             * Delete room when nobody remains
             * -------------------------------------------------
             *
             * MyMeet keeps room state only while the room has
             * participants.
             *
             * Once the last participant leaves, the room can be
             * removed from memory.
             *
             * This prevents abandoned empty rooms from accumulating.
             */
            if (room.isEmpty()) {

                rooms.remove(
                        roomId,
                        room
                );
            }
        }


        /*
         * Return the removed session so the caller can use its
         * roomId and participantId when broadcasting the departure.
         */
        return removed;
    }


    /* =========================================================
       SNAPSHOT
       ========================================================= */

    /*
     * Returns a read-only representation of the current room
     * contents by creating a new List.
     *
     * The internal room map should never be exposed directly.
     *
     * Otherwise a caller could accidentally modify RoomManager's
     * internal state without going through its synchronization
     * and validation logic.
     */
    public synchronized List<Participant> snapshot(
            String roomId
    ) {

        Map<String, Participant> room =
                rooms.get(roomId);

        if (room == null || room.isEmpty()) {
            return Collections.emptyList();
        }


        /*
         * Return a copy of the values.
         *
         * The caller receives its own List and cannot directly
         * modify the internal LinkedHashMap used by the room.
         */
        return new ArrayList<>(
                room.values()
        );
    }


    /* =========================================================
       GET SESSION
       ========================================================= */

    /*
     * Finds the ParticipantSession associated with a WebSocket
     * session.
     *
     * This is used by the controller when it needs to verify
     * the identity of the current WebSocket connection.
     */
    public ParticipantSession getSession(
            String sessionId
    ) {

        return sessions.get(sessionId);
    }


    /* =========================================================
       GET PARTICIPANT
       ========================================================= */

    /*
     * Finds the logical Participant associated with a WebSocket
     * session.
     *
     * The lookup happens in two steps:
     *
     *     sessionId
     *         |
     *         v
     *     ParticipantSession
     *         |
     *         +---- roomId
     *         +---- participantId
     *         |
     *         v
     *     rooms
     *         |
     *         v
     *     Participant
     */
    public Participant getParticipant(
            String sessionId
    ) {

        ParticipantSession session =
                sessions.get(sessionId);

        if (session == null) {
            return null;
        }


        /*
         * Locate the room in which this WebSocket's participant
         * is currently registered.
         */
        Map<String, Participant> room =
                rooms.get(
                        session.getRoomId()
                );

        if (room == null) {
            return null;
        }


        /*
         * Finally locate the participant inside that room.
         */
        return room.get(
                session.getParticipantId()
        );
    }


    /* =========================================================
       MEMBERSHIP CHECK
       ========================================================= */

    /*
     * Verifies that a specific WebSocket session actually
     * represents a specific participant in a specific room.
     *
     * This is used before sensitive operations such as:
     *
     * - Sending chat messages
     * - Sending WebRTC signaling messages
     *
     * The important point is that the backend validates the
     * relationship using its own session mapping rather than
     * trusting only the values supplied by the frontend.
     */
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

    /*
     * Checks whether a participant currently exists inside
     * a particular room.
     *
     * This is especially useful for WebRTC signaling where the
     * backend must verify that the target participant is actually
     * present in the room.
     */
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

    /*
     * Public method for obtaining the participants in a room.
     *
     * It delegates to snapshot() so there is only one place where
     * the room-copying logic is maintained.
     */
    public synchronized List<Participant> getParticipants(
            String roomId
    ) {

        return snapshot(roomId);
    }


    /* =========================================================
       JOIN RESULT
       ========================================================= */

    /*
     * JoinResult is returned by join() instead of returning only
     * a boolean.
     *
     * A JOIN operation can have several different outcomes:
     *
     *     newlyJoined
     *         |
     *         +-- true  -> participant successfully joined
     *         +-- false -> no new participant was created
     *
     *     duplicateParticipant
     *         |
     *         +-- true  -> participantId already belongs to another
     *                      WebSocket session
     *
     *     invalidSession
     *         |
     *         +-- true  -> request/session combination is invalid
     *
     *     participants
     *         |
     *         +-- current snapshot of the room
     *
     *
     * Using a record keeps this result object concise and immutable.
     */
    public record JoinResult(
            boolean newlyJoined,
            boolean duplicateParticipant,
            boolean invalidSession,
            List<Participant> participants
    ) {
    }
}