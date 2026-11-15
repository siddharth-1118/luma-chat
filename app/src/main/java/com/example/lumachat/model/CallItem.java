package com.example.lumachat.model;

public class CallItem {
    private long id;
    private String contactName;
    private String contactAvatar;
    private String callType; // "Incoming", "Outgoing", "Missed"
    private String callTime;
    private long timestamp;
    private String duration;

    public CallItem(long id, String contactName, String contactAvatar, String callType,
                    String callTime, long timestamp, String duration) {
        this.id = id;
        this.contactName = contactName;
        this.contactAvatar = contactAvatar;
        this.callType = callType;
        this.callTime = callTime;
        this.timestamp = timestamp;
        this.duration = duration;
    }

    public long getId() { return id; }
    public String getContactName() { return contactName; }
    public String getContactAvatar() { return contactAvatar; }
    public String getCallType() { return callType; }
    public String getCallTime() { return callTime; }
    public long getTimestamp() { return timestamp; }
    public String getDuration() { return duration; }
}
