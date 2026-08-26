package com.mymeet.controller;

import com.mymeet.dto.ChatMessageRequest;
import com.mymeet.dto.JoinRequest;
import com.mymeet.dto.LeaveRequest;
import com.mymeet.dto.WebSocketEvent;
import com.mymeet.model.Participant;
import com.mymeet.model.ParticipantSession;
import com.mymeet.room.RoomManager;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;


/*
 * MeetController is the STOMP message entry point for MyMeet.
 *
 * Frontend sends messages to /app destinations.
 *
 * Example:
 *
 * Frontend
 *     |
 *     | SEND /app/meet/join
 *     v
 * MeetController
 *     |
 *     v
 * RoomManager
 *     |
 *     v
 * SimpMessagingTemplate
 *     |
 *     | SEND /topic/meet/{roomId}
 *     v
 * All participants subscribed to that room
 *
 * The controller mainly handles:
 *
 * - Validating incoming WebSocket messages
 * - Identifying the WebSocket session
 * - Delegating room operations to RoomManager
 * - Broadcasting events to the room
 * - Routing WebRTC signaling messages
 * - Cleaning up participants when a WebSocket disconnects
 *
 * The controller does NOT contain the actual room-management
 * data structure or WebRTC negotiation logic.
 */
@Controller
public class MeetController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomManager roomManager;

    /*
     * SimpMessagingTemplate is Spring's mechanism for sending
     * STOMP messages from the backend to subscribed clients.
     *
     * RoomManager owns the in-memory meeting state.
     *
     * Keeping these responsibilities separate makes the controller
     * responsible mainly for message handling and routing.
     */
    public MeetController(
            SimpMessagingTemplate messagingTemplate,
            RoomManager roomManager
    ) {
        this.messagingTemplate = messagingTemplate;
        this.roomManager = roomManager;
    }


    /* =========================================================
       JOIN
       ========================================================= */

    /*
     * Handles:
     *
     *     SEND /app/meet/join
     *
     * The frontend sends the participant's:
     *
     * - roomId
     * - participantId
     * - name
     *
     * The WebSocket session ID is obtained from the STOMP
     * connection instead of trusting the frontend to provide it.
     */
    @MessageMapping("/meet/join")
    public void join(
            JoinRequest request,
            SimpMessageHeaderAccessor accessor
    ) {

        /*
         * Every WebSocket connection has a unique session ID.
         *
         * This session ID is important because the backend uses it
         * to associate a WebSocket connection with a participant.
         *
         * We should not trust the frontend to tell us which session
         * it belongs to.
         */
        String sessionId = accessor.getSessionId();

        if (sessionId == null) {
            return;
        }

        /*
         * Validate the incoming request before interacting with
         * the room manager.
         *
         * Invalid room IDs or participant IDs should never be allowed
         * to enter the room-management layer.
         */
        if (
                request == null ||
                        request.getRoomId() == null ||
                        request.getRoomId().isBlank() ||
                        request.getParticipantId() == null ||
                        request.getParticipantId().isBlank()
        ) {
            return;
        }

        /*
         * Normalize user-provided values before using them.
         *
         * This prevents accidental whitespace from creating different
         * room IDs or participant IDs.
         */
        String roomId =
                request.getRoomId().trim();

        String participantId =
                request.getParticipantId().trim();

        /*
         * Name is not required for joining the room.
         *
         * If the frontend does not provide a valid name,
         * MyMeet treats the participant as "Guest".
         */
        String name =
                request.getName() == null ||
                        request.getName().isBlank()
                        ? "Guest"
                        : request.getName().trim();


        /* -----------------------------------------------------
           JOIN ROOM
           ----------------------------------------------------- */

        /*
         * Delegate the actual room operation to RoomManager.
         *
         * RoomManager is responsible for:
         *
         * - Creating the room if necessary
         * - Adding the participant
         * - Detecting duplicate participants
         * - Detecting duplicate joins from the same session
         *
         * The controller should not duplicate that state-management
         * logic.
         */
        RoomManager.JoinResult result =
                roomManager.join(
                        sessionId,
                        roomId,
                        participantId,
                        name
                );


        /* -----------------------------------------------------
           DUPLICATE PARTICIPANT
           ----------------------------------------------------- */

        /*
         * A participantId represents the logical participant.
         *
         * If the same participant is already participating,
         * the backend rejects the new join instead of creating
         * an inconsistent room state.
         */
        if (result.duplicateParticipant()) {

            messagingTemplate.convertAndSend(
                    destination(roomId),
                    WebSocketEvent.joinRejected(
                            roomId,
                            participantId,
                            "You are already participating in this meeting."
                    )
            );

            return;
        }


        /* -----------------------------------------------------
           DUPLICATE JOIN FROM SAME SESSION
           ----------------------------------------------------- */

        /*
         * A client can accidentally send JOIN more than once.
         *
         * If the participant is already registered for this
         * WebSocket session, RoomManager reports newlyJoined() = false.
         *
         * No second ROOM_STATE or PARTICIPANT_JOINED event should
         * be generated for the same join operation.
         */
        if (!result.newlyJoined()) {
            return;
        }


        /* =====================================================
           ROOM STATE
           ===================================================== */

        /*
         * ROOM_STATE represents the current state of the room.
         *
         * It contains the participants currently known to the
         * backend and is broadcast to everyone subscribed to:
         *
         *     /topic/meet/{roomId}
         *
         * The frontend uses this information to synchronize its
         * participant list.
         *
         * Important architectural point:
         *
         * ROOM_STATE is a room synchronization event.
         *
         * It is NOT itself a WebRTC negotiation event.
         *
         * Because ROOM_STATE is broadcast to everyone, the frontend
         * must NOT start unlimited WebRTC negotiation every time
         * ROOM_STATE is received.
         *
         * WebRTC negotiation should happen only once per
         * participant pair, according to the frontend's negotiation
         * rules.
         */
        messagingTemplate.convertAndSend(
                destination(roomId),
                WebSocketEvent.roomState(
                        roomId,
                        result.participants()
                )
        );


        /* =====================================================
           PARTICIPANT JOINED
           ===================================================== */

        /*
         * PARTICIPANT_JOINED is a separate event from ROOM_STATE.
         *
         * ROOM_STATE tells clients the current complete room state.
         *
         * PARTICIPANT_JOINED tells clients that a specific participant
         * has just joined.
         *
         * Keeping these events separate allows the frontend to
         * distinguish between synchronization and a new join event.
         */
        messagingTemplate.convertAndSend(
                destination(roomId),
                WebSocketEvent.participantJoined(
                        roomId,
                        participantId,
                        name
                )
        );
    }


    /* =========================================================
       LEAVE
       ========================================================= */

    /*
     * Handles:
     *
     *     SEND /app/meet/leave
     *
     * A participant can explicitly leave the meeting by sending
     * this message.
     *
     * There is also a second cleanup mechanism:
     *
     *     SessionDisconnectEvent
     *
     * That handles cases where the browser closes, the network
     * connection disappears, or the WebSocket disconnects without
     * the frontend successfully sending LEAVE.
     */
    @MessageMapping("/meet/leave")
    public void leave(
            LeaveRequest request,
            SimpMessageHeaderAccessor accessor
    ) {

        /*
         * Identify the actual WebSocket connection making the request.
         */
        String sessionId =
                accessor.getSessionId();

        if (sessionId == null || request == null) {
            return;
        }


        /*
         * Retrieve the participant associated with this WebSocket
         * session.
         *
         * This allows the backend to verify that the session actually
         * belongs to the participant it is trying to remove.
         */
        ParticipantSession session =
                roomManager.getSession(sessionId);

        if (session == null) {
            return;
        }


        String requestedRoomId =
                request.getRoomId();

        String requestedParticipantId =
                request.getParticipantId();


        /*
         * Validate the room and participant information before
         * attempting to remove anything.
         */
        if (
                requestedRoomId == null ||
                        requestedRoomId.isBlank() ||
                        requestedParticipantId == null ||
                        requestedParticipantId.isBlank()
        ) {
            return;
        }


        requestedRoomId =
                requestedRoomId.trim();

        requestedParticipantId =
                requestedParticipantId.trim();


        /*
         * Security / consistency check:
         *
         * The WebSocket session can only leave its OWN participant.
         *
         * We do not allow a client to provide another participant's
         * participantId and remove that participant from the room.
         *
         * The identity relationship is:
         *
         *     WebSocket Session
         *             |
         *             v
         *     ParticipantSession
         *             |
         *             +---- roomId
         *             |
         *             +---- participantId
         *
         * Both values must match the leave request.
         */
        if (
                !session.getRoomId().equals(requestedRoomId) ||
                        !session.getParticipantId().equals(requestedParticipantId)
        ) {
            return;
        }


        /*
         * Remove the participant from RoomManager.
         *
         * RoomManager also handles the associated session/room
         * bookkeeping.
         */
        ParticipantSession removed =
                roomManager.leave(
                        sessionId,
                        requestedRoomId,
                        requestedParticipantId
                );

        if (removed == null) {
            return;
        }


        /*
         * Notify the remaining participants that this participant
         * has left the room.
         */
        broadcastParticipantLeft(removed);
    }


    /* =========================================================
       CHAT
       ========================================================= */

    /*
     * Handles:
     *
     *     SEND /app/meet/message
     *
     * Chat messages are also transported through the same
     * WebSocket/STOMP connection used by MyMeet for meeting events.
     *
     * The backend validates that the sender actually belongs to
     * the requested room before broadcasting the message.
     */
    @MessageMapping("/meet/message")
    public void message(
            ChatMessageRequest request,
            SimpMessageHeaderAccessor accessor
    ) {

        String sessionId =
                accessor.getSessionId();

        if (sessionId == null || request == null) {
            return;
        }


        String roomId =
                request.getRoomId();

        String participantId =
                request.getParticipantId();


        /*
         * Basic request validation.
         */
        if (
                roomId == null ||
                        roomId.isBlank() ||
                        participantId == null ||
                        participantId.isBlank()
        ) {
            return;
        }


        roomId =
                roomId.trim();

        participantId =
                participantId.trim();


        /*
         * Verify that the WebSocket session actually owns
         * this participant in this room.
         *
         * This prevents a client from simply changing the
         * participantId in the request and pretending to be
         * another participant.
         */
        if (
                !roomManager.isMember(
                        sessionId,
                        roomId,
                        participantId
                )
        ) {
            return;
        }


        /*
         * Retrieve the session after membership has been verified.
         */
        ParticipantSession session =
                roomManager.getSession(sessionId);

        if (session == null) {
            return;
        }


        /*
         * Retrieve the participant associated with the session.
         *
         * The backend uses this participant object to obtain trusted
         * participant information such as the display name.
         */
        Participant participant =
                roomManager.getParticipant(sessionId);

        if (participant == null) {
            return;
        }


        /*
         * Final consistency check.
         *
         * Even though membership was already verified, we explicitly
         * confirm that the session's stored identity matches the
         * request.
         */
        if (
                !session.getRoomId().equals(roomId) ||
                        !session.getParticipantId().equals(participantId)
        ) {
            return;
        }


        /*
         * Normalize the message.
         *
         * Empty messages are ignored instead of being broadcast.
         */
        String message =
                request.getMessage() == null
                        ? ""
                        : request.getMessage().trim();

        if (message.isEmpty()) {
            return;
        }


        /*
         * Broadcast the chat event to the room.
         *
         * The same room destination is used for chat, participant
         * events, room state, and WebRTC signaling.
         *
         * The frontend determines what action to perform based
         * on the event type.
         */
        messagingTemplate.convertAndSend(
                destination(roomId),
                WebSocketEvent.chatMessage(
                        roomId,
                        participantId,
                        message,
                        participant.getName()
                )
        );
    }


    /* =========================================================
       WEBRTC OFFER
       ========================================================= */

    /*
     * Handles:
     *
     *     SEND /app/webrtc/offer
     *
     * The backend does not create the WebRTC offer.
     *
     * The browser's RTCPeerConnection creates the offer.
     *
     * The backend's responsibility is to validate and route
     * the signaling message to the intended room.
     */
    @MessageMapping("/webrtc/offer")
    public void offer(
            WebSocketEvent request,
            SimpMessageHeaderAccessor accessor
    ) {

        sendWebRtcSignal(
                request,
                accessor,
                WebRtcSignalType.OFFER
        );
    }


    /* =========================================================
       WEBRTC ANSWER
       ========================================================= */

    /*
     * Handles:
     *
     *     SEND /app/webrtc/answer
     *
     * The browser creates the answer after receiving an offer.
     *
     * The backend only transports the signaling information.
     */
    @MessageMapping("/webrtc/answer")
    public void answer(
            WebSocketEvent request,
            SimpMessageHeaderAccessor accessor
    ) {

        sendWebRtcSignal(
                request,
                accessor,
                WebRtcSignalType.ANSWER
        );
    }


    /* =========================================================
       WEBRTC ICE
       ========================================================= */

    /*
     * Handles:
     *
     *     SEND /app/webrtc/ice
     *
     * ICE candidates are generated by the browser's WebRTC
     * implementation.
     *
     * The backend simply validates and routes them.
     */
    @MessageMapping("/webrtc/ice")
    public void ice(
            WebSocketEvent request,
            SimpMessageHeaderAccessor accessor
    ) {

        sendWebRtcSignal(
                request,
                accessor,
                WebRtcSignalType.ICE
        );
    }


    /* =========================================================
       WEBRTC SIGNAL TYPES
       ========================================================= */

    /*
     * Internal representation of the three signaling messages
     * supported by MyMeet.
     *
     * OFFER:
     *     Caller proposes a WebRTC connection.
     *
     * ANSWER:
     *     Receiver accepts/responds to the offer.
     *
     * ICE:
     *     Candidates used by WebRTC to discover possible
     *     network paths between peers.
     */
    private enum WebRtcSignalType {
        OFFER,
        ANSWER,
        ICE
    }


    /* =========================================================
       WEBRTC SIGNAL ROUTING
       ========================================================= */

    /*
     * Common routing logic for:
     *
     *     OFFER
     *     ANSWER
     *     ICE
     *
     * Instead of duplicating validation and routing code in
     * three controller methods, all three delegate here.
     */
    private void sendWebRtcSignal(
            WebSocketEvent request,
            SimpMessageHeaderAccessor accessor,
            WebRtcSignalType type
    ) {

        if (request == null) {
            return;
        }


        /*
         * Obtain the trusted WebSocket session identity.
         */
        String sessionId =
                accessor.getSessionId();

        if (sessionId == null) {
            return;
        }


        String roomId =
                request.roomId();

        String participantId =
                request.participantId();

        String targetParticipantId =
                request.targetParticipantId();


        /* -----------------------------------------------------
           BASIC VALIDATION
           ----------------------------------------------------- */

        /*
         * WebRTC signaling is peer-to-peer logically.
         *
         * Therefore every signaling event needs:
         *
         *     roomId
         *     sender participantId
         *     target participantId
         *
         * Without these values the backend cannot safely route
         * or validate the signal.
         */
        if (
                roomId == null ||
                        roomId.isBlank() ||
                        participantId == null ||
                        participantId.isBlank() ||
                        targetParticipantId == null ||
                        targetParticipantId.isBlank()
        ) {
            return;
        }


        roomId =
                roomId.trim();

        participantId =
                participantId.trim();

        targetParticipantId =
                targetParticipantId.trim();


        /* -----------------------------------------------------
           PREVENT SELF SIGNALING
           ----------------------------------------------------- */

        /*
         * A participant should never send an offer, answer, or ICE
         * candidate to itself.
         *
         * This also protects against incorrect frontend signaling
         * logic.
         */
        if (
                participantId.equals(targetParticipantId)
        ) {
            return;
        }


        /* -----------------------------------------------------
           VERIFY SENDER MEMBERSHIP
           ----------------------------------------------------- */

        /*
         * Before routing a WebRTC signal, verify that the sender
         * actually belongs to the requested room.
         *
         * The client cannot simply provide an arbitrary roomId and
         * participantId and start injecting signaling messages.
         */
        if (
                !roomManager.isMember(
                        sessionId,
                        roomId,
                        participantId
                )
        ) {
            return;
        }


        /*
         * Retrieve the server-side session associated with the
         * WebSocket connection.
         */
        ParticipantSession sender =
                roomManager.getSession(sessionId);

        if (sender == null) {
            return;
        }


        /* -----------------------------------------------------
           VERIFY SESSION IDENTITY
           ----------------------------------------------------- */

        /*
         * Verify that the request identity matches the identity
         * stored against the actual WebSocket session.
         *
         * This is another important trust boundary:
         *
         *     Client-provided participantId
         *                 vs
         *     Server-known session identity
         *
         * The server should trust its own session mapping.
         */
        if (
                !sender.getRoomId().equals(roomId) ||
                        !sender.getParticipantId().equals(participantId)
        ) {
            return;
        }


        /* -----------------------------------------------------
           VERIFY TARGET
           ----------------------------------------------------- */

        /*
         * The target participant must currently exist in the
         * requested room.
         *
         * Otherwise there is nobody valid to whom this signal
         * should be addressed.
         */
        if (
                !roomManager.isParticipantInRoom(
                        roomId,
                        targetParticipantId
                )
        ) {
            return;
        }


        WebSocketEvent event;


        /* =====================================================
           OFFER
           ===================================================== */

        /*
         * The frontend creates the SDP offer.
         *
         * The backend does not inspect or generate the WebRTC
         * negotiation itself. It only validates and transports it.
         */
        if (type == WebRtcSignalType.OFFER) {

            if (request.offer() == null) {
                return;
            }

            event =
                    WebSocketEvent.offer(
                            roomId,
                            participantId,
                            targetParticipantId,
                            request.offer()
                    );
        }


        /* =====================================================
           ANSWER
           ===================================================== */

        /*
         * The receiver's browser creates the SDP answer.
         *
         * Again, the backend acts only as a signaling transport.
         */
        else if (type == WebRtcSignalType.ANSWER) {

            if (request.answer() == null) {
                return;
            }

            event =
                    WebSocketEvent.answer(
                            roomId,
                            participantId,
                            targetParticipantId,
                            request.answer()
                    );
        }


        /* =====================================================
           ICE
           ===================================================== */

        /*
         * ICE candidates are generated asynchronously by the
         * browser while WebRTC attempts to discover usable
         * network paths.
         */
        else {

            if (request.candidate() == null) {
                return;
            }

            event =
                    WebSocketEvent.ice(
                            roomId,
                            participantId,
                            targetParticipantId,
                            request.candidate()
                    );
        }


        /* =====================================================
           BROADCAST SIGNAL
           ===================================================== */

        /*
         * MyMeet currently broadcasts the signaling event to the
         * entire room instead of sending it directly to a single
         * WebSocket session.
         *
         * The frontend uses targetParticipantId to determine whether
         * the received signaling message is intended for that client.
         *
         * Backend validation guarantees:
         *
         *     sender  -> belongs to room
         *     target  -> belongs to room
         *     sender  -> is not target
         *
         * The backend does NOT:
         *
         * - create WebRTC offers
         * - create WebRTC answers
         * - generate ICE candidates
         * - perform WebRTC negotiation
         *
         * Those responsibilities belong to the browser's
         * RTCPeerConnection.
         */
        messagingTemplate.convertAndSend(
                destination(roomId),
                event
        );
    }


    /* =========================================================
       DISCONNECT
       ========================================================= */

    /*
     * SessionDisconnectEvent is triggered by Spring when the
     * WebSocket/STOMP session is disconnected.
     *
     * This is important because a user may disappear without
     * successfully sending:
     *
     *     /app/meet/leave
     *
     * Examples:
     *
     * - Browser tab is closed
     * - Browser crashes
     * - Network connection is lost
     * - WebSocket connection is terminated
     *
     * Therefore explicit LEAVE and disconnect cleanup are both
     * required.
     */
    @EventListener
    public void handleDisconnect(
            SessionDisconnectEvent event
    ) {

        /*
         * SessionDisconnectEvent contains the STOMP message.
         *
         * StompHeaderAccessor gives us access to the WebSocket
         * session ID associated with that message.
         */
        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(
                        event.getMessage()
                );


        String sessionId =
                accessor.getSessionId();

        if (sessionId == null) {
            return;
        }


        /*
         * RoomManager uses the session ID to find and remove the
         * participant associated with the disconnected connection.
         *
         * This is why the backend maintains a relationship between
         * WebSocket session and participant.
         */
        ParticipantSession removed =
                roomManager.leaveBySession(sessionId);

        if (removed == null) {
            return;
        }


        /*
         * Notify the remaining participants about the departure.
         */
        broadcastParticipantLeft(removed);
    }


    /* =========================================================
       PARTICIPANT LEFT
       ========================================================= */

    /*
     * Centralizes the PARTICIPANT_LEFT broadcast so both:
     *
     *     explicit LEAVE
     *
     * and
     *
     *     WebSocket DISCONNECT
     *
     * produce the same frontend event.
     */
    private void broadcastParticipantLeft(
            ParticipantSession participant
    ) {

        messagingTemplate.convertAndSend(
                destination(
                        participant.getRoomId()
                ),
                WebSocketEvent.participantLeft(
                        participant.getRoomId(),
                        participant.getParticipantId()
                )
        );
    }


    /* =========================================================
       DESTINATION
       ========================================================= */

    /*
     * Builds the STOMP topic used by a meeting room.
     *
     * Frontend subscription:
     *
     *     /topic/meet/{roomId}
     *
     * Example:
     *
     *     /topic/meet/demo
     *
     * This follows the MyMeet STOMP architecture:
     *
     *     /app
     *         |
     *         +-- client -> server application messages
     *
     *     /topic
     *         |
     *         +-- server -> subscribed clients
     *
     * Therefore:
     *
     *     /app/meet/join
     *
     * is an application destination handled by this controller,
     * while:
     *
     *     /topic/meet/{roomId}
     *
     * is the broker destination to which participants subscribe.
     */
    private String destination(
            String roomId
    ) {

        return "/topic/meet/" + roomId;
    }
}