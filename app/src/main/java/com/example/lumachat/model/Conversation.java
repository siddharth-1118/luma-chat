package com.example.lumachat.model;

public class Conversation {
    private String id;
    private String name;
    private String avatar;
    private boolean isGroup;
    private String members;
    private String lastMessage;
    private String lastTime;
    private int unreadCount;
    private boolean isPinned;
    private String onlineStatus;
    private long updatedAt;

    public Conversation(String id, String name, String avatar, boolean isGroup,
                        String members, String lastMessage, String lastTime,
                        int unreadCount, boolean isPinned, String onlineStatus, long updatedAt) {
        this.id = id;
        this.name = name;
        this.avatar = avatar;
        this.isGroup = isGroup;
        this.members = members;
        this.lastMessage = lastMessage;
        this.lastTime = lastTime;
        this.unreadCount = unreadCount;
        this.isPinned = isPinned;
        this.onlineStatus = onlineStatus;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public boolean isGroup() { return isGroup; }
    public String getMembers() { return members; }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public String getLastTime() { return lastTime; }
    public void setLastTime(String lastTime) { this.lastTime = lastTime; }
    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
    public boolean isPinned() { return isPinned; }
    public void setPinned(boolean pinned) { isPinned = pinned; }
    public String getOnlineStatus() { return onlineStatus; }
    public void setOnlineStatus(String onlineStatus) { this.onlineStatus = onlineStatus; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
