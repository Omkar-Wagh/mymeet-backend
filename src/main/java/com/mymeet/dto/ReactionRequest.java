package com.mymeet.dto;


/*
 * ReactionRequest represents an ephemeral reaction sent by
 * a participant.
 *
 * Examples:
 *
 *     👍
 *     ❤️
 *     😂
 *     👏
 *     🎉
 *
 *
 * Reactions are intentionally NOT stored inside Participant.
 *
 * A reaction represents an event:
 *
 *     "Participant X reacted with 👍"
 *
 * rather than persistent participant state:
 *
 *     "Participant X is currently 👍"
 */
public record ReactionRequest(

        String roomId,

        String participantId,

        String reaction

) {
}