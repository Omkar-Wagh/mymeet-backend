package com.mymeet.service;

import com.mymeet.dto.ICECandidate;
import com.mymeet.dto.WebRTCAnswer;
import com.mymeet.dto.WebRTCOffer;
import com.mymeet.dto.MeetEvent;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;


/*
 * WebRTCSignalingService is responsible for transporting WebRTC
 * signaling messages between participants.
 *
 * WebRTC itself does NOT use the backend to transfer the actual
 * audio/video stream.
 *
 * Instead, the backend is used only during the connection setup
 * phase to exchange signaling information:
 *
 *     Participant A
 *          |
 *          | OFFER
 *          v
 *     Spring Boot
 *          |
 *          v
 *     Participant B
 *
 *
 *     Participant B
 *          |
 *          | ANSWER
 *          v
 *     Spring Boot
 *          |
 *          v
 *     Participant A
 *
 *
 *     Participant A/B
 *          |
 *          | ICE candidates
 *          v
 *     Spring Boot
 *          |
 *          v
 *     Other participant
 *
 *
 * After WebRTC negotiation succeeds, the actual media path is
 * handled by WebRTC between the peers.
 *
 *
 * Therefore the responsibility of this service is intentionally
 * simple:
 *
 *     Receive signaling data
 *             |
 *             v
 *     Wrap it inside MeetEvent
 *             |
 *             v
 *     Broadcast to the room topic
 *
 *
 * The frontend is responsible for checking the targetParticipantId
 * and deciding whether the received signaling message belongs to
 * its WebRTC peer connection.
 */
@Service
public class WebRTCSignalingService {


    /*
     * SimpMessagingTemplate is Spring's abstraction for sending
     * STOMP messages from the server to subscribed clients.
     *
     * WebRTC signaling messages are published to:
     *
     *     /topic/meet/{roomId}
     *
     * All participants in that room are subscribed to the same
     * topic.
     *
     * The frontend then uses the target participant information
     * inside the signaling payload to determine whether it should
     * process the message.
     */
    private final SimpMessagingTemplate messagingTemplate;


    /*
     * Constructor injection makes the messaging dependency
     * explicit and keeps the service easy to test.
     */
    public WebRTCSignalingService(
            SimpMessagingTemplate messagingTemplate
    ) {
        this.messagingTemplate = messagingTemplate;
    }


    /*
     * =========================================================
     * WEBRTC OFFER
     * =========================================================
     *
     * An SDP offer is normally created by the participant that
     * initiates the WebRTC negotiation.
     *
     * The offer contains information describing the proposed
     * WebRTC session, such as:
     *
     * - Media capabilities
     * - Supported codecs
     * - ICE information
     * - Connection parameters
     *
     *
     * Important:
     *
     * This backend does NOT create the offer.
     *
     * The browser creates it using:
     *
     *     RTCPeerConnection.createOffer()
     *
     * The frontend then sends the generated offer to the backend.
     *
     * The backend simply transports it to the meeting room.
     */
    public void sendOffer(WebRTCOffer request) {


        /*
         * Wrap the WebRTC offer inside the common MeetEvent
         * structure used by the application.
         *
         * The event type allows the frontend to determine which
         * kind of message it has received.
         */
        MeetEvent event = new MeetEvent(
                "WEBRTC_OFFER",
                request
        );


        /*
         * Broadcast the offer to the room topic.
         *
         * Example:
         *
         *     /topic/meet/demo
         *
         *
         * The backend intentionally does not establish the
         * WebRTC connection itself.
         *
         * It only transports the signaling message.
         */
        messagingTemplate.convertAndSend(
                "/topic/meet/" + request.getRoomId(),
                event
        );
    }


    /*
     * =========================================================
     * WEBRTC ANSWER
     * =========================================================
     *
     * The participant receiving an offer creates an SDP answer.
     *
     * The browser generates it using:
     *
     *     RTCPeerConnection.createAnswer()
     *
     * The answer is then sent to the backend and routed through
     * the same room topic.
     *
     * The backend does not generate or modify the answer.
     */
    public void sendAnswer(WebRTCAnswer request) {


        /*
         * Wrap the answer inside the common MeetEvent structure.
         */
        MeetEvent event = new MeetEvent(
                "WEBRTC_ANSWER",
                request
        );


        /*
         * Broadcast the answer to everyone subscribed to the room.
         *
         * The intended participant is determined by the signaling
         * payload on the frontend.
         */
        messagingTemplate.convertAndSend(
                "/topic/meet/" + request.getRoomId(),
                event
        );
    }


    /*
     * =========================================================
     * WEBRTC ICE CANDIDATE
     * =========================================================
     *
     * ICE candidates contain possible network paths through which
     * WebRTC may establish a peer-to-peer connection.
     *
     * Candidates are discovered asynchronously by the browser
     * through the ICE process.
     *
     * The frontend sends each discovered candidate to the backend.
     *
     * The backend broadcasts the candidate through the room topic.
     *
     * The receiving browser then passes it to its
     * RTCPeerConnection using:
     *
     *     addIceCandidate()
     */
    public void sendIceCandidate(ICECandidate request) {


        /*
         * Wrap the ICE candidate inside the common MeetEvent
         * structure.
         */
        MeetEvent event = new MeetEvent(
                "WEBRTC_ICE",
                request
        );


        /*
         * Broadcast the ICE candidate to the room.
         *
         * As with offers and answers, this service is only a
         * signaling transport layer.
         *
         * It does NOT carry the actual audio/video data.
         */
        messagingTemplate.convertAndSend(
                "/topic/meet/" + request.getRoomId(),
                event
        );
    }
}