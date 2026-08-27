//package com.mymeet.service;
//
//import com.mymeet.dto.RoomState;
//import com.mymeet.model.Participant;
//import com.mymeet.model.Room;
//import org.springframework.stereotype.Service;
//
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//
//
///*
// * RoomService is responsible for managing the lifecycle and
// * state of meeting rooms in MyMeet.
// *
// * Since MyMeet currently does not use a database for meeting
// * state, rooms are maintained in memory while the application
// * is running.
// *
// * The basic relationship is:
// *
// *     roomId
// *        |
// *        v
// *      Room
// *        |
// *        +---- Participant
// *        +---- Participant
// *        +---- Participant
// *
// *
// * This service provides operations to:
// *
// * - Create/find a room
// * - Add a participant
// * - Remove a participant
// * - Retrieve the current room state
// * - Delete an empty room
// *
// *
// * RoomService works at the business/service layer.
// *
// * The controller should handle WebSocket messages and delegate
// * room-related operations to this service instead of directly
// * manipulating the internal room collection.
// */
//@Service
//public class RoomService {
//
//    /*
//     * Stores all active rooms in memory.
//     *
//     * Structure:
//     *
//     *     roomId -> Room
//     *
//     * Example:
//     *
//     *     "demo" -> Room
//     *     "abc"  -> Room
//     *
//     *
//     * ConcurrentHashMap is used because multiple WebSocket
//     * clients can join and leave rooms concurrently.
//     *
//     * There is no database persistence here.
//     *
//     * Therefore, if the backend application restarts,
//     * these rooms and participants are lost.
//     */
//    private final Map<String, Room> rooms =
//            new ConcurrentHashMap<>();
//
//
//    /*
//     * Adds a participant to a room.
//     *
//     * The operation is:
//     *
//     *     roomId
//     *        |
//     *        v
//     *     find/create Room
//     *        |
//     *        v
//     *     create Participant
//     *        |
//     *        v
//     *     add Participant to Room
//     *        |
//     *        v
//     *     convert Room -> RoomState
//     */
//    public RoomState join(
//            String roomId,
//            String participantId,
//            String name
//    ) {
//
//        /*
//         * Find the room using roomId.
//         *
//         * If the room does not exist, computeIfAbsent creates
//         * a new Room using the roomId.
//         *
//         * Room::new is a method reference equivalent to:
//         *
//         *     roomId -> new Room(roomId)
//         *
//         *
//         * This means the first participant creates the room,
//         * while later participants reuse the existing room.
//         */
//        Room room = rooms.computeIfAbsent(
//                roomId,
//                Room::new
//        );
//
//
//        /*
//         * Create the logical participant that will be stored
//         * inside the room.
//         *
//         * participantId identifies the participant within the
//         * meeting.
//         */
//        Participant participant =
//                new Participant(participantId, name);
//
//
//        /*
//         * Add the participant to the room.
//         *
//         * Room owns the actual participant collection, so the
//         * service delegates the modification to Room instead of
//         * directly manipulating Room's internal collection.
//         */
//        room.addParticipant(participant);
//
//
//        /*
//         * Convert the internal Room object into a RoomState DTO.
//         *
//         * RoomState is the representation that can be sent to
//         * the frontend through STOMP.
//         *
//         * This keeps the internal model separate from the data
//         * exposed through WebSocket messages.
//         */
//        return toRoomState(room);
//    }
//
//
//    /*
//     * Removes a participant from a room.
//     *
//     * This method is used when a participant explicitly leaves
//     * or when the backend detects that a participant's WebSocket
//     * connection has disconnected.
//     */
//    public RoomState leave(
//            String roomId,
//            String participantId
//    ) {
//
//        /*
//         * Find the room containing the participant.
//         */
//        Room room = rooms.get(roomId);
//
//
//        /*
//         * If the room does not exist, there is nothing to remove.
//         *
//         * We still return a valid RoomState containing an empty
//         * participant list so the caller has a predictable result.
//         */
//        if (room == null) {
//            return new RoomState(
//                    roomId,
//                    java.util.List.of()
//            );
//        }
//
//
//        /*
//         * Remove the participant from the room.
//         */
//        room.removeParticipant(participantId);
//
//
//        /*
//         * Create the updated snapshot of the room AFTER the
//         * participant has been removed.
//         *
//         * This is the state that should be broadcast to the
//         * remaining participants.
//         */
//        RoomState roomState = toRoomState(room);
//
//
//        /*
//         * If nobody remains in the room, remove the room itself
//         * from the in-memory room map.
//         *
//         * This prevents empty rooms from remaining in memory
//         * indefinitely.
//         */
//        if (room.isEmpty()) {
//            rooms.remove(roomId);
//        }
//
//
//        /*
//         * Return the updated room state.
//         */
//        return roomState;
//    }
//
//
//    /*
//     * Returns the current state of a room.
//     *
//     * This is useful when the application needs to obtain the
//     * latest participant list without modifying the room.
//     */
//    public RoomState getRoomState(String roomId) {
//
//        /*
//         * Locate the room using its ID.
//         */
//        Room room = rooms.get(roomId);
//
//
//        /*
//         * If the room does not exist, return an empty room state.
//         */
//        if (room == null) {
//            return new RoomState(
//                    roomId,
//                    java.util.List.of()
//            );
//        }
//
//
//        /*
//         * Convert the internal Room model into the DTO exposed
//         * to the rest of the application/frontend.
//         */
//        return toRoomState(room);
//    }
//
//
//    /*
//     * Converts the internal Room model into a RoomState DTO.
//     *
//     * This helper keeps the conversion logic in one place.
//     *
//     * Internal model:
//     *
//     *     Room
//     *
//     *        |
//     *        v
//     *
//     * External/application representation:
//     *
//     *     RoomState
//     *
//     *
//     * Keeping this conversion separate makes the service easier
//     * to maintain if the internal Room structure changes later.
//     */
//    private RoomState toRoomState(Room room) {
//
//        return new RoomState(
//                room.getRoomId(),
//                room.getParticipantList()
//        );
//    }
//}