package com.mymeet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;


/*
 * WebSocketConfig defines the WebSocket + STOMP communication
 * architecture used by MyMeet.
 *
 * MyMeet uses:
 *
 *     WebSocket
 *         +
 *     STOMP
 *
 * WebSocket provides the persistent, full-duplex connection
 * between browser and server.
 *
 * STOMP provides a structured messaging protocol on top of
 * that connection using destinations such as:
 *
 *     /app/meet/join
 *     /topic/meet/{roomId}
 *
 * The configuration mainly defines two things:
 *
 * 1. How the browser establishes the WebSocket connection.
 * 2. How STOMP messages are routed after the connection is established.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig
        implements WebSocketMessageBrokerConfigurer {


    /*
     * Configures how STOMP messages are routed inside MyMeet.
     *
     * There are two important destination prefixes:
     *
     *     /app
     *     /topic
     *
     * They have different responsibilities.
     */
    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry registry
    ) {

        /*
         * Messages sent by the frontend to /app are treated as
         * application messages.
         *
         * These messages are routed to methods annotated with
         * @MessageMapping in our controllers.
         *
         * Example:
         *
         * Frontend:
         *
         *     SEND /app/meet/join
         *
         * Spring removes the /app prefix and matches:
         *
         *     /meet/join
         *
         * with:
         *
         *     @MessageMapping("/meet/join")
         *
         * in MeetController.
         *
         * Therefore:
         *
         *     /app
         *         |
         *         v
         *     Controller
         *
         * It represents the client -> server application path.
         */
        registry.setApplicationDestinationPrefixes(
                "/app"
        );


        /*
         * Enables Spring's built-in simple STOMP message broker
         * for destinations beginning with /topic.
         *
         * These destinations are used for server -> client
         * broadcasting.
         *
         * Example:
         *
         *     /topic/meet/demo
         *
         * Participants subscribed to this destination receive
         * messages published to it.
         *
         * MyMeet uses room-specific topics:
         *
         *     /topic/meet/{roomId}
         *
         * Example:
         *
         *     /topic/meet/room123
         *
         * Therefore:
         *
         *     /topic
         *         |
         *         v
         *     STOMP Broker
         *         |
         *         v
         *     Subscribed clients
         *
         * The simple broker is sufficient for the current MyMeet
         * architecture because the application is intentionally
         * keeping the meeting state in memory and does not require
         * an external message broker at this stage.
         */
        registry.enableSimpleBroker(
                "/topic"
        );
    }


    /*
     * Registers the endpoint through which the browser initially
     * establishes the WebSocket connection.
     *
     * This is NOT a STOMP application destination.
     *
     * It is the actual WebSocket handshake endpoint.
     *
     * Frontend connects to:
     *
     *     ws://localhost:8080/ws
     *
     * Production can use:
     *
     *     wss://<backend-host>/ws
     *
     * The frontend should obtain this URL from:
     *
     *     NEXT_PUBLIC_BACKEND_WS_URL
     *
     * rather than hardcoding a deployment URL.
     */
    @Override
    public void registerStompEndpoints(
            StompEndpointRegistry registry
    ) {

        /*
         * /ws is the WebSocket handshake endpoint.
         *
         * The connection flow is:
         *
         * Browser
         *     |
         *     | WebSocket handshake
         *     v
         * /ws
         *     |
         *     v
         * STOMP connection
         *     |
         *     v
         * STOMP SUBSCRIBE /topic/meet/{roomId}
         *     |
         *     v
         * STOMP SEND /app/meet/join
         */
        registry.addEndpoint("/ws")

                /*
                 * Allows browser origins to establish the WebSocket
                 * connection.
                 *
                 * This is currently configured permissively for
                 * development.
                 *
                 * For production, this should ideally be restricted
                 * to the actual MyMeet frontend origin instead of
                 * allowing every origin.
                 */
                .setAllowedOriginPatterns("*");
    }
}