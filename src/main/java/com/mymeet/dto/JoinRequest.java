package com.mymeet.dto;

public class JoinRequest {

    private String roomId;
    private String participantId;
    private String connectionId;
    private String name;

    /*
     * Initial realtime participant state supplied by the frontend.
     * These values are used only when a participant is newly created.
     * Existing/reconnecting participants keep their RoomManager state.
     */
    private boolean muted;
    private boolean cameraOff;
    private boolean handRaised;
    private boolean screenSharing;

    public JoinRequest() {
    }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getParticipantId() { return participantId; }
    public void setParticipantId(String participantId) { this.participantId = participantId; }

    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }

    public boolean isCameraOff() { return cameraOff; }
    public void setCameraOff(boolean cameraOff) { this.cameraOff = cameraOff; }

    public boolean isHandRaised() { return handRaised; }
    public void setHandRaised(boolean handRaised) { this.handRaised = handRaised; }

    public boolean isScreenSharing() { return screenSharing; }
    public void setScreenSharing(boolean screenSharing) { this.screenSharing = screenSharing; }
}
