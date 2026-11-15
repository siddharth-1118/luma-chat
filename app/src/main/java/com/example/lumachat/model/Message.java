package com.example.lumachat.model;

public class Message {
    private long id;
    private String conversationId;
    private String senderName;
    private String senderAvatar;
    private String messageText;
    private long timestamp;
    private String timeFormatted;
    private boolean isFromMe;
    private String status;

    public Message(long id, String conversationId, String senderName, String senderAvatar,
                   String messageText, long timestamp, String timeFormatted,
                   boolean isFromMe, String status) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderName = senderName;
        this.senderAvatar = senderAvatar;
        this.messageText = messageText;
        this.timestamp = timestamp;
        this.timeFormatted = timeFormatted;
        this.isFromMe = isFromMe;
        this.status = status;
    }

    public long getId() { return id; }
    public String getConversationId() { return conversationId; }
    public String getSenderName() { return senderName; }
    public String getSenderAvatar() { return senderAvatar; }
    public String getMessageText() { return messageText; }
    public long getTimestamp() { return timestamp; }
    public String getTimeFormatted() { return timeFormatted; }
    public boolean isFromMe() { return isFromMe; }
    public String getStatus() { return status; }
}
