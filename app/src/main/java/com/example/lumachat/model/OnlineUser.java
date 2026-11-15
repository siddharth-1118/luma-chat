package com.example.lumachat.model;

public class OnlineUser {
    private String userId;
    private String name;
    private String avatar;
    private String bio;
    private String status;
    private boolean isOnline;
    private long lastSeen;

    public OnlineUser(String userId, String name, String avatar, String bio, String status, boolean isOnline, long lastSeen) {
        this.userId = userId;
        this.name = name;
        this.avatar = avatar;
        this.bio = bio;
        this.status = status;
        this.isOnline = isOnline;
        this.lastSeen = lastSeen;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }

    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
}
