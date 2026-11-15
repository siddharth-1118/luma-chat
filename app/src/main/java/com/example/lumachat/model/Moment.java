package com.example.lumachat.model;

public class Moment {
    private long id;
    private String authorName;
    private String authorAvatar;
    private String emoji;
    private String caption;
    private String timeAgo;
    private long timestamp;
    private boolean isMyStory;
    private int likesCount;
    private boolean isLiked;

    public Moment(long id, String authorName, String authorAvatar, String emoji,
                  String caption, String timeAgo, long timestamp, boolean isMyStory,
                  int likesCount, boolean isLiked) {
        this.id = id;
        this.authorName = authorName;
        this.authorAvatar = authorAvatar;
        this.emoji = emoji;
        this.caption = caption;
        this.timeAgo = timeAgo;
        this.timestamp = timestamp;
        this.isMyStory = isMyStory;
        this.likesCount = likesCount;
        this.isLiked = isLiked;
    }

    public long getId() { return id; }
    public String getAuthorName() { return authorName; }
    public String getAuthorAvatar() { return authorAvatar; }
    public String getEmoji() { return emoji; }
    public String getCaption() { return caption; }
    public String getTimeAgo() { return timeAgo; }
    public long getTimestamp() { return timestamp; }
    public boolean isMyStory() { return isMyStory; }
    public int getLikesCount() { return likesCount; }
    public void setLikesCount(int likesCount) { this.likesCount = likesCount; }
    public boolean isLiked() { return isLiked; }
    public void setLiked(boolean liked) { isLiked = liked; }
}
