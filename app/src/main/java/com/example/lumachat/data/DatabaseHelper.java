package com.example.lumachat.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.lumachat.model.CallItem;
import com.example.lumachat.model.Conversation;
import com.example.lumachat.model.Message;
import com.example.lumachat.model.Moment;
import com.example.lumachat.model.NotificationItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "lumachat.db";
    private static final int DATABASE_VERSION = 1;

    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Table: Conversations
        db.execSQL("CREATE TABLE conversations (" +
                "id TEXT PRIMARY KEY, " +
                "name TEXT, " +
                "avatar TEXT, " +
                "is_group INTEGER, " +
                "members TEXT, " +
                "last_message TEXT, " +
                "last_time TEXT, " +
                "unread_count INTEGER, " +
                "is_pinned INTEGER, " +
                "online_status TEXT, " +
                "updated_at INTEGER)");

        // Table: Messages
        db.execSQL("CREATE TABLE messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "conversation_id TEXT, " +
                "sender_name TEXT, " +
                "sender_avatar TEXT, " +
                "message_text TEXT, " +
                "timestamp INTEGER, " +
                "time_formatted TEXT, " +
                "is_from_me INTEGER, " +
                "status TEXT)");

        // Table: Moments
        db.execSQL("CREATE TABLE moments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "author_name TEXT, " +
                "author_avatar TEXT, " +
                "emoji TEXT, " +
                "caption TEXT, " +
                "time_ago TEXT, " +
                "timestamp INTEGER, " +
                "is_my_story INTEGER, " +
                "likes_count INTEGER, " +
                "is_liked INTEGER)");

        // Table: Calls
        db.execSQL("CREATE TABLE calls (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "contact_name TEXT, " +
                "contact_avatar TEXT, " +
                "call_type TEXT, " +
                "call_time TEXT, " +
                "timestamp INTEGER, " +
                "duration TEXT)");

        // Table: Notifications
        db.execSQL("CREATE TABLE notifications (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sender_name TEXT, " +
                "sender_avatar TEXT, " +
                "content TEXT, " +
                "time_ago TEXT, " +
                "is_read INTEGER, " +
                "target_conversation_id TEXT, " +
                "timestamp INTEGER)");

        seedInitialData(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS conversations");
        db.execSQL("DROP TABLE IF EXISTS messages");
        db.execSQL("DROP TABLE IF EXISTS moments");
        db.execSQL("DROP TABLE IF EXISTS calls");
        db.execSQL("DROP TABLE IF EXISTS notifications");
        onCreate(db);
    }

    private void seedInitialData(SQLiteDatabase db) {
        long now = System.currentTimeMillis();

        // 1. Seed Conversations
        insertConvDirect(db, "chat_ananya", "Ananya Sharma", "A", 0, "", "Hey! How are you?", "10:42 AM", 2, 1, "Online", now);
        insertConvDirect(db, "chat_rahul", "Rahul", "R", 0, "", "See you tomorrow!", "9:30 AM", 1, 0, "Last seen 15m ago", now - 3600000);
        insertConvDirect(db, "chat_sravani", "Sravani", "S", 0, "", "That was so fun!", "Yesterday", 0, 0, "Online", now - 86400000);
        insertConvDirect(db, "chat_priya", "Priya", "P", 0, "", "Sent you the photos", "Yesterday", 3, 0, "Active 2h ago", now - 90000000);

        // Group circles
        insertConvDirect(db, "circle_besties", "Besties Forever", "💕", 1, "You, Ananya, Sravani, Priya, Rahul", "Sravani: Let's meet today!", "11:15 AM", 0, 1, "5 members online", now - 1800000);
        insertConvDirect(db, "circle_college", "College Squad", "🎓", 1, "You, Rahul, Priya, Karan, Neha, Arjun, Meera, Sid", "Rahul: Assignment completed!", "Yesterday", 0, 0, "8 members", now - 80000000);
        insertConvDirect(db, "circle_family", "Family Circle", "🏡", 1, "Mom, Dad, Harshitha, Rohan, Aunt Geeta, Uncle Suresh", "Mom: Take care everyone ❤️", "2 days ago", 0, 0, "6 members", now - 170000000);

        // Saved Messages
        insertConvDirect(db, "saved_messages", "Saved Messages", "♡", 0, "", "Keep your favorite thoughts & links here 📌", "Just now", 0, 0, "Personal cloud space", now);

        // 2. Seed Messages
        insertMsgDirect(db, "chat_ananya", "Ananya Sharma", "A", "Hey Harshitha! Are we still on for coffee this afternoon? ☕", now - 1800000, "10:38 AM", 0);
        insertMsgDirect(db, "chat_ananya", "Harshitha", "H", "Yes absolutely! Let's meet at our usual spot.", now - 1200000, "10:40 AM", 1);
        insertMsgDirect(db, "chat_ananya", "Ananya Sharma", "A", "Hey! How are you?", now - 600000, "10:42 AM", 0);

        insertMsgDirect(db, "chat_rahul", "Rahul", "R", "Did you get a chance to finish reviewing the project slides?", now - 7200000, "9:25 AM", 0);
        insertMsgDirect(db, "chat_rahul", "Harshitha", "H", "Almost done, just refining the conclusions.", now - 5400000, "9:28 AM", 1);
        insertMsgDirect(db, "chat_rahul", "Rahul", "R", "See you tomorrow!", now - 3600000, "9:30 AM", 0);

        insertMsgDirect(db, "chat_sravani", "Sravani", "S", "The sunset yesterday was unreal! 🌅", now - 90000000, "Yesterday", 0);
        insertMsgDirect(db, "chat_sravani", "Harshitha", "H", "I saw your story, loved the photos!", now - 88000000, "Yesterday", 1);
        insertMsgDirect(db, "chat_sravani", "Sravani", "S", "That was so fun!", now - 86400000, "Yesterday", 0);

        insertMsgDirect(db, "chat_priya", "Priya", "P", "Hey, here are the snaps from the workshop 📸", now - 95000000, "Yesterday", 0);
        insertMsgDirect(db, "chat_priya", "Priya", "P", "Sent you the photos", now - 90000000, "Yesterday", 0);

        // Circles messages
        insertMsgDirect(db, "circle_besties", "Ananya Sharma", "A", "Hey everyone! What are our weekend plans? 🌸", now - 3600000, "10:30 AM", 0);
        insertMsgDirect(db, "circle_besties", "Priya", "P", "I vote for picnic in the park! 🧺", now - 2400000, "10:55 AM", 0);
        insertMsgDirect(db, "circle_besties", "Sravani", "S", "Sravani: Let's meet today!", now - 1800000, "11:15 AM", 0);

        insertMsgDirect(db, "circle_college", "Rahul", "R", "Assignment completed!", now - 80000000, "Yesterday", 0);
        insertMsgDirect(db, "circle_family", "Mom", "M", "Mom: Take care everyone ❤️", now - 170000000, "2 days ago", 0);

        // Saved messages
        insertMsgDirect(db, "saved_messages", "Harshitha", "H", "Welcome to your Personal Space! You can store notes, ideas, links, and reminders here 🌟", now, "Just now", 1);

        // 3. Seed Moments
        insertMomentDirect(db, "Ananya", "A", "🌸", "Enjoying the fresh cherry blossoms on this lovely morning! 🌸🍃", "2 hours ago", now - 7200000, 0, 14, 0);
        insertMomentDirect(db, "Sravani", "S", "🌅", "Golden hour walks by the lake never fail to amaze me ✨", "4 hours ago", now - 14400000, 0, 9, 0);
        insertMomentDirect(db, "Priya", "P", "☕", "Coffee time with friends! ☕🥐", "Yesterday", now - 86400000, 0, 22, 1);
        insertMomentDirect(db, "Rahul", "R", "💻", "Late night hackathon vibes 🎧🚀", "Yesterday", now - 90000000, 0, 8, 0);

        // 4. Seed Calls
        insertCallDirect(db, "Ananya Sharma", "A", "Incoming", "Incoming • 10:30 AM", now - 1800000, "04:12");
        insertCallDirect(db, "Rahul", "R", "Outgoing", "Outgoing • 9:15 AM", now - 7200000, "01:45");
        insertCallDirect(db, "Sravani", "S", "Missed", "Missed • Yesterday", now - 86400000, "00:00");
        insertCallDirect(db, "Priya", "P", "Incoming", "Incoming • Yesterday", now - 95000000, "08:30");

        // 5. Seed Notifications
        insertNotifDirect(db, "Ananya Sharma", "A", "Sent you a new message", "2 minutes ago", 0, "chat_ananya", now - 120000);
        insertNotifDirect(db, "Sravani", "S", "Added a new moment", "1 hour ago", 0, "chat_sravani", now - 3600000);
        insertNotifDirect(db, "Rahul", "R", "Mentioned you in College Squad", "Yesterday", 1, "circle_college", now - 86400000);
        insertNotifDirect(db, "Priya", "P", "Shared photos with you", "Yesterday", 1, "chat_priya", now - 90000000);
    }

    private void insertConvDirect(SQLiteDatabase db, String id, String name, String avatar,
                                  int isGroup, String members, String lastMessage, String lastTime,
                                  int unread, int pinned, String status, long updatedAt) {
        ContentValues cv = new ContentValues();
        cv.put("id", id);
        cv.put("name", name);
        cv.put("avatar", avatar);
        cv.put("is_group", isGroup);
        cv.put("members", members);
        cv.put("last_message", lastMessage);
        cv.put("last_time", lastTime);
        cv.put("unread_count", unread);
        cv.put("is_pinned", pinned);
        cv.put("online_status", status);
        cv.put("updated_at", updatedAt);
        db.insert("conversations", null, cv);
    }

    private void insertMsgDirect(SQLiteDatabase db, String convId, String senderName, String senderAvatar,
                                 String text, long timestamp, String timeFormatted, int isFromMe) {
        ContentValues cv = new ContentValues();
        cv.put("conversation_id", convId);
        cv.put("sender_name", senderName);
        cv.put("sender_avatar", senderAvatar);
        cv.put("message_text", text);
        cv.put("timestamp", timestamp);
        cv.put("time_formatted", timeFormatted);
        cv.put("is_from_me", isFromMe);
        cv.put("status", "read");
        db.insert("messages", null, cv);
    }

    private void insertMomentDirect(SQLiteDatabase db, String author, String avatar, String emoji,
                                    String caption, String timeAgo, long timestamp, int isMyStory,
                                    int likes, int isLiked) {
        ContentValues cv = new ContentValues();
        cv.put("author_name", author);
        cv.put("author_avatar", avatar);
        cv.put("emoji", emoji);
        cv.put("caption", caption);
        cv.put("time_ago", timeAgo);
        cv.put("timestamp", timestamp);
        cv.put("is_my_story", isMyStory);
        cv.put("likes_count", likes);
        cv.put("is_liked", isLiked);
        db.insert("moments", null, cv);
    }

    private void insertCallDirect(SQLiteDatabase db, String name, String avatar, String type,
                                  String time, long timestamp, String duration) {
        ContentValues cv = new ContentValues();
        cv.put("contact_name", name);
        cv.put("contact_avatar", avatar);
        cv.put("call_type", type);
        cv.put("call_time", time);
        cv.put("timestamp", timestamp);
        cv.put("duration", duration);
        db.insert("calls", null, cv);
    }

    private void insertNotifDirect(SQLiteDatabase db, String sender, String avatar, String content,
                                   String timeAgo, int isRead, String targetId, long timestamp) {
        ContentValues cv = new ContentValues();
        cv.put("sender_name", sender);
        cv.put("sender_avatar", avatar);
        cv.put("content", content);
        cv.put("time_ago", timeAgo);
        cv.put("is_read", isRead);
        cv.put("target_conversation_id", targetId);
        cv.put("timestamp", timestamp);
        db.insert("notifications", null, cv);
    }

    // --- CONVERSATIONS CRUD ---

    public List<Conversation> getConversations(boolean onlyGroups) {
        return getConversationsWithFilter("All", null, onlyGroups);
    }

    public List<Conversation> getConversationsWithFilter(String filter, String searchQuery, boolean onlyGroups) {
        List<Conversation> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        StringBuilder query = new StringBuilder("SELECT * FROM conversations WHERE 1=1 ");
        List<String> args = new ArrayList<>();

        if (onlyGroups) {
            query.append("AND is_group = 1 ");
        }

        if ("Unread".equalsIgnoreCase(filter)) {
            query.append("AND unread_count > 0 ");
        } else if ("Pinned".equalsIgnoreCase(filter)) {
            query.append("AND is_pinned = 1 ");
        }

        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            query.append("AND (name LIKE ? OR last_message LIKE ?) ");
            args.add("%" + searchQuery.trim() + "%");
            args.add("%" + searchQuery.trim() + "%");
        }

        query.append("ORDER BY is_pinned DESC, updated_at DESC");

        Cursor cursor = db.rawQuery(query.toString(), args.toArray(new String[0]));
        if (cursor != null) {
            while (cursor.moveToNext()) {
                list.add(new Conversation(
                        cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("avatar")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("is_group")) == 1,
                        cursor.getString(cursor.getColumnIndexOrThrow("members")),
                        cursor.getString(cursor.getColumnIndexOrThrow("last_message")),
                        cursor.getString(cursor.getColumnIndexOrThrow("last_time")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("unread_count")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("is_pinned")) == 1,
                        cursor.getString(cursor.getColumnIndexOrThrow("online_status")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
                ));
            }
            cursor.close();
        }
        return list;
    }

    public Conversation getConversation(String id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM conversations WHERE id = ?", new String[]{id});
        Conversation conv = null;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                conv = new Conversation(
                        cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("avatar")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("is_group")) == 1,
                        cursor.getString(cursor.getColumnIndexOrThrow("members")),
                        cursor.getString(cursor.getColumnIndexOrThrow("last_message")),
                        cursor.getString(cursor.getColumnIndexOrThrow("last_time")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("unread_count")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("is_pinned")) == 1,
                        cursor.getString(cursor.getColumnIndexOrThrow("online_status")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
                );
            }
            cursor.close();
        }
        return conv;
    }

    public Conversation getConversationByName(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM conversations WHERE LOWER(TRIM(name)) = LOWER(TRIM(?)) LIMIT 1",
                new String[]{name.trim()});
        Conversation conv = null;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                conv = new Conversation(
                        cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("avatar")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("is_group")) == 1,
                        cursor.getString(cursor.getColumnIndexOrThrow("members")),
                        cursor.getString(cursor.getColumnIndexOrThrow("last_message")),
                        cursor.getString(cursor.getColumnIndexOrThrow("last_time")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("unread_count")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("is_pinned")) == 1,
                        cursor.getString(cursor.getColumnIndexOrThrow("online_status")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
                );
            }
            cursor.close();
        }
        return conv;
    }

    public boolean messageExists(String convId, String text, long timestamp) {
        if (convId == null || text == null) return false;
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT id FROM messages WHERE conversation_id = ? AND message_text = ? AND ABS(timestamp - ?) < 3000 LIMIT 1",
                new String[]{convId, text, String.valueOf(timestamp)});
        boolean exists = false;
        if (cursor != null) {
            exists = cursor.moveToFirst();
            cursor.close();
        }
        return exists;
    }

    public void insertConversation(Conversation c) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("id", c.getId());
        cv.put("name", c.getName());
        cv.put("avatar", c.getAvatar());
        cv.put("is_group", c.isGroup() ? 1 : 0);
        cv.put("members", c.getMembers());
        cv.put("last_message", c.getLastMessage());
        cv.put("last_time", c.getLastTime());
        cv.put("unread_count", c.getUnreadCount());
        cv.put("is_pinned", c.isPinned() ? 1 : 0);
        cv.put("online_status", c.getOnlineStatus());
        cv.put("updated_at", c.getUpdatedAt());
        db.insertWithOnConflict("conversations", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void updateConversation(Conversation c) {
        insertConversation(c);
    }

    public void updateLastMessage(String convId, String message, String time, boolean incrementUnread) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        if (incrementUnread) {
            db.execSQL("UPDATE conversations SET last_message = ?, last_time = ?, updated_at = ?, unread_count = unread_count + 1 WHERE id = ?",
                    new Object[]{message, time, now, convId});
        } else {
            db.execSQL("UPDATE conversations SET last_message = ?, last_time = ?, updated_at = ? WHERE id = ?",
                    new Object[]{message, time, now, convId});
        }
    }

    public void markConversationRead(String convId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("unread_count", 0);
        db.update("conversations", cv, "id = ?", new String[]{convId});
    }

    public void setConversationPinned(String convId, boolean pinned) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("is_pinned", pinned ? 1 : 0);
        db.update("conversations", cv, "id = ?", new String[]{convId});
    }

    public void deleteConversation(String convId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("conversations", "id = ?", new String[]{convId});
        db.delete("messages", "conversation_id = ?", new String[]{convId});
    }

    public void clearMessages(String convId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("messages", "conversation_id = ?", new String[]{convId});
        ContentValues cv = new ContentValues();
        cv.put("last_message", "No messages yet");
        cv.put("unread_count", 0);
        db.update("conversations", cv, "id = ?", new String[]{convId});
    }

    // --- MESSAGES CRUD ---

    public List<Message> getMessages(String convId) {
        List<Message> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM messages WHERE conversation_id = ? ORDER BY timestamp ASC", new String[]{convId});
        if (cursor != null) {
            while (cursor.moveToNext()) {
                list.add(new Message(
                        cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("conversation_id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("sender_name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("sender_avatar")),
                        cursor.getString(cursor.getColumnIndexOrThrow("message_text")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                        cursor.getString(cursor.getColumnIndexOrThrow("time_formatted")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("is_from_me")) == 1,
                        cursor.getString(cursor.getColumnIndexOrThrow("status"))
                ));
            }
            cursor.close();
        }
        return list;
    }

    public long insertMessage(Message m) {
        if (messageExists(m.getConversationId(), m.getMessageText(), m.getTimestamp())) {
            // Already saved, prevent duplication
            return -1;
        }
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("conversation_id", m.getConversationId());
        cv.put("sender_name", m.getSenderName());
        cv.put("sender_avatar", m.getSenderAvatar());
        cv.put("message_text", m.getMessageText());
        cv.put("timestamp", m.getTimestamp());
        cv.put("time_formatted", m.getTimeFormatted());
        cv.put("is_from_me", m.isFromMe() ? 1 : 0);
        cv.put("status", m.getStatus());
        long id = db.insert("messages", null, cv);

        // Update conversation summary
        updateLastMessage(m.getConversationId(), m.getMessageText(), m.getTimeFormatted(), !m.isFromMe());
        return id;
    }

    // --- MOMENTS CRUD ---

    public List<Moment> getMoments() {
        List<Moment> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM moments ORDER BY timestamp DESC", null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                list.add(new Moment(
                        cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("author_name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("author_avatar")),
                        cursor.getString(cursor.getColumnIndexOrThrow("emoji")),
                        cursor.getString(cursor.getColumnIndexOrThrow("caption")),
                        cursor.getString(cursor.getColumnIndexOrThrow("time_ago")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("is_my_story")) == 1,
                        cursor.getInt(cursor.getColumnIndexOrThrow("likes_count")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("is_liked")) == 1
                ));
            }
            cursor.close();
        }
        return list;
    }

    public void insertMoment(Moment m) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("author_name", m.getAuthorName());
        cv.put("author_avatar", m.getAuthorAvatar());
        cv.put("emoji", m.getEmoji());
        cv.put("caption", m.getCaption());
        cv.put("time_ago", m.getTimeAgo());
        cv.put("timestamp", m.getTimestamp());
        cv.put("is_my_story", m.isMyStory() ? 1 : 0);
        cv.put("likes_count", m.getLikesCount());
        cv.put("is_liked", m.isLiked() ? 1 : 0);
        db.insert("moments", null, cv);
    }

    public void toggleMomentLike(long momentId) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT is_liked, likes_count FROM moments WHERE id = ?", new String[]{String.valueOf(momentId)});
        if (cursor != null && cursor.moveToFirst()) {
            int liked = cursor.getInt(0);
            int count = cursor.getInt(1);
            cursor.close();

            int newLiked = (liked == 1) ? 0 : 1;
            int newCount = (newLiked == 1) ? count + 1 : Math.max(0, count - 1);

            ContentValues cv = new ContentValues();
            cv.put("is_liked", newLiked);
            cv.put("likes_count", newCount);
            db.update("moments", cv, "id = ?", new String[]{String.valueOf(momentId)});
        }
    }

    // --- CALLS CRUD ---

    public List<CallItem> getCalls(String filter) {
        List<CallItem> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT * FROM calls ";
        String[] args = null;
        if (filter != null && !"All".equalsIgnoreCase(filter)) {
            query += "WHERE call_type = ? ";
            args = new String[]{filter};
        }
        query += "ORDER BY timestamp DESC";

        Cursor cursor = db.rawQuery(query, args);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                list.add(new CallItem(
                        cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("contact_name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("contact_avatar")),
                        cursor.getString(cursor.getColumnIndexOrThrow("call_type")),
                        cursor.getString(cursor.getColumnIndexOrThrow("call_time")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                        cursor.getString(cursor.getColumnIndexOrThrow("duration"))
                ));
            }
            cursor.close();
        }
        return list;
    }

    public void insertCall(CallItem call) {
        SQLiteDatabase db = getWritableDatabase();
        insertCallDirect(db, call.getContactName(), call.getContactAvatar(),
                call.getCallType(), call.getCallTime(), call.getTimestamp(), call.getDuration());
    }

    public void clearCalls() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("calls", null, null);
    }

    // --- NOTIFICATIONS CRUD ---

    public List<NotificationItem> getNotifications() {
        List<NotificationItem> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM notifications ORDER BY timestamp DESC", null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                list.add(new NotificationItem(
                        cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("sender_name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("sender_avatar")),
                        cursor.getString(cursor.getColumnIndexOrThrow("content")),
                        cursor.getString(cursor.getColumnIndexOrThrow("time_ago")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("is_read")) == 1,
                        cursor.getString(cursor.getColumnIndexOrThrow("target_conversation_id")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                ));
            }
            cursor.close();
        }
        return list;
    }

    public void insertNotification(NotificationItem item) {
        SQLiteDatabase db = getWritableDatabase();
        insertNotifDirect(db, item.getSenderName(), item.getSenderAvatar(),
                item.getContent(), item.getTimeAgo(), item.isRead() ? 1 : 0,
                item.getTargetConversationId(), item.getTimestamp());
    }

    public void markNotificationsRead() {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("is_read", 1);
        db.update("notifications", cv, null, null);
    }

    public void clearNotifications() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("notifications", null, null);
    }

    public int getUnreadNotificationCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM notifications WHERE is_read = 0", null);
        int count = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            cursor.close();
        }
        return count;
    }

    public int getTotalUnreadMessagesCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT SUM(unread_count) FROM conversations", null);
        int count = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            cursor.close();
        }
        return count;
    }

    public int getTotalChatsCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM conversations WHERE is_group = 0 AND id != 'saved_messages'", null);
        int count = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            cursor.close();
        }
        return count;
    }

    public int getTotalGroupsCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM conversations WHERE is_group = 1", null);
        int count = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            cursor.close();
        }
        return count;
    }
}
