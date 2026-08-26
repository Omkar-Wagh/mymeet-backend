package com.mymeet.service;

import com.mymeet.model.ParticipantSession;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/*
 * ParticipantSessionService maintains the mapping between a
 * WebSocket session and the participant represented by that
 * connection.
 *
 * The important relationship is:
 *
 *     WebSocket sessionId
 *             |
 *             v
 *     ParticipantSession
 *             |
 *             +---- roomId
 *             |
 *             +---- participantId
 *
 *
 * Why is this mapping required?
 *
 * A WebSocket connection has its own server-side session ID.
 * MyMeet needs to know which participant and room are associated
 * with that connection.
 *
 * This becomes especially important when:
 *
 * - A participant sends a message
 * - A participant sends WebRTC signaling
 * - A participant explicitly leaves
 * - A WebSocket disconnects unexpectedly
 *
 *
 * Example:
 *
 *     session-123
 *         |
 *         v
 *     ParticipantSession
 *         |
 *         +---- roomId = "demo"
 *         +---- participantId = "uuid-abc"
 *
 *
 * This service is intentionally small.
 *
 * It is responsible only for WebSocket-session mapping.
 * Room membership itself is handled by RoomManager / RoomService
 * depending on the current application flow.
 */
@Service
public class ParticipantSessionService {


    /*
     * Stores:
     *
     *     sessionId -> ParticipantSession
     *
     *
     * ConcurrentHashMap is used because WebSocket connections are
     * concurrent by nature.
     *
     * Multiple participants can join, leave, disconnect, or send
     * messages at approximately the same time.
     *
     * ConcurrentHashMap allows these operations to safely access
     * the shared session mapping without using a regular HashMap.
     */
    private final ConcurrentMap<String, ParticipantSession> sessions =
            new ConcurrentHashMap<>();


    /*
     * Registers a WebSocket session against a participant.
     *
     * This creates the server-side relationship:
     *
     *     sessionId
     *         |
     *         v
     *     ParticipantSession
     *
     * The frontend does not control the sessionId.
     * It is obtained from the WebSocket/STOMP connection and is
     * used by the backend to identify the current connection.
     */
    public void register(
            String sessionId,
            String roomId,
            String participantId
    ) {

        /*
         * Create the object containing the information required
         * to associate this WebSocket connection with its
         * participant and meeting room.
         */
        ParticipantSession participantSession =
                new ParticipantSession(
                        sessionId,
                        roomId,
                        participantId
                );


        /*
         * Store the mapping:
         *
         *     sessionId -> ParticipantSession
         *
         * Once registered, the backend can later retrieve the
         * participant information using only the WebSocket
         * session ID.
         */
        sessions.put(sessionId, participantSession);
    }


    /*
     * Retrieves the participant session associated with a
     * WebSocket session.
     *
     * Returns null when the session is not currently registered.
     *
     * This is useful when handling:
     *
     * - authenticated/validated WebSocket operations
     * - explicit leave
     * - disconnect cleanup
     * - participant identity checks
     */
    public ParticipantSession get(String sessionId) {

        return sessions.get(sessionId);
    }


    /*
     * Removes the WebSocket session mapping.
     *
     * The removed ParticipantSession is returned so the caller
     * still knows which participant and room belonged to the
     * disconnected session.
     *
     * Example:
     *
     *     ParticipantSession removed =
     *             sessions.remove(sessionId);
     *
     * The caller can then use:
     *
     *     removed.getRoomId()
     *     removed.getParticipantId()
     *
     * to update the corresponding room state.
     */
    public ParticipantSession remove(String sessionId) {

        return sessions.remove(sessionId);
    }
}