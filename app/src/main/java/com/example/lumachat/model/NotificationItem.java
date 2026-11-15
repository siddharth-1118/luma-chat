package com.example.lumachat.model;

public class NotificationItem {
    private long id;
    private String senderName;
    private String senderAvatar;
    private String content;
    private String timeAgo;
    private boolean isRead;
    private String targetConversationId;
    private long timestamp;

    public NotificationItem(long id, String senderName, String senderAvatar, String content,
                            String timeAgo, boolean isRead, String targetConversationId, long timestamp) {
        this.id = id;
        this.senderName = senderName;
        this.senderAvatar = senderAvatar;
        this.content = content;
        this.timeAgo = timeAgo;
        this.isRead = isRead;
        this.targetConversationId = targetConversationId;
        this.timestamp = timestamp;
    }

    public long getId() { return id; }
    public String getSenderName() { return senderName; }
    public String getSenderAvatar() { return senderAvatar; }
    public String getContent() { return content; }
    public String getTimeAgo() { return timeAgo; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    public String getTargetConversationId() { return targetConversationId; }
    public long getTimestamp() { return timestamp; }
}
