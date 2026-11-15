package com.example.lumachat;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.lumachat.data.DatabaseHelper;
import com.example.lumachat.data.UserPreferences;
import com.example.lumachat.engine.SmartReplyEngine;
import com.example.lumachat.model.Conversation;
import com.example.lumachat.model.Message;
import com.example.lumachat.model.NotificationItem;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends AppCompatActivity {

    private String conversationId;
    private String chatName;
    private String chatAvatar;
    private boolean isGroup;
    private String onlineStatus;

    private TextView tvChatName;
    private TextView tvChatAvatar;
    private TextView tvChatStatus;
    private LinearLayout messageContainer;
    private ScrollView chatScroll;
    private EditText etMessage;

    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        conversationId = getIntent().getStringExtra("id");
        chatName = getIntent().getStringExtra("name");
        chatAvatar = getIntent().getStringExtra("avatar");
        isGroup = getIntent().getBooleanExtra("is_group", false);
        onlineStatus = getIntent().getStringExtra("status");

        if (chatName == null) chatName = "Friend";
        if (chatAvatar == null) chatAvatar = chatName.substring(0, 1).toUpperCase();
        if (conversationId == null) {
            conversationId = "chat_" + chatName.toLowerCase().replaceAll("[^a-zA-Z0-9]", "_");
        }
        if (onlineStatus == null) {
            onlineStatus = isGroup ? "Group • Active" : "Online";
        }

        tvChatName = findViewById(R.id.tvChatName);
        tvChatAvatar = findViewById(R.id.tvChatAvatar);
        tvChatStatus = findViewById(R.id.tvChatStatus);
        messageContainer = findViewById(R.id.messageContainer);
        chatScroll = findViewById(R.id.chatScroll);
        etMessage = findViewById(R.id.etMessage);

        tvChatName.setText(chatName);
        tvChatAvatar.setText(chatAvatar);
        tvChatStatus.setText(onlineStatus);

        findViewById(R.id.btnBackChat).setOnClickListener(v -> finish());

        // Call Action
        findViewById(R.id.btnCallChat).setOnClickListener(v -> {
            Intent callIntent = new Intent(ChatActivity.this, CallActivity.class);
            callIntent.putExtra("name", chatName);
            callIntent.putExtra("avatar", chatAvatar);
            startActivity(callIntent);
        });

        // Menu Action
        findViewById(R.id.btnMenuChat).setOnClickListener(v -> showMenuDialog());

        // Attachment Action
        findViewById(R.id.btnAttach).setOnClickListener(v -> showAttachmentOptions());

        // Send Action
        findViewById(R.id.btnSend).setOnClickListener(v -> sendMessage());

        loadMessages();
    }

    private void loadMessages() {
        messageContainer.removeAllViews();

        // Add Date Header
        TextView dateHeader = new TextView(this);
        dateHeader.setText("TODAY");
        dateHeader.setTextSize(11);
        dateHeader.setTextColor(Color.parseColor("#77718A"));
        dateHeader.setGravity(Gravity.CENTER);
        dateHeader.setPadding(0, 10, 0, 18);
        messageContainer.addView(dateHeader);

        List<Message> messages = DatabaseHelper.getInstance(this).getMessages(conversationId);
        for (Message msg : messages) {
            addMessageView(msg.getMessageText(), msg.getTimeFormatted(), msg.isFromMe(), msg.getSenderName());
        }

        scrollToBottom();
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        String myName = UserPreferences.getUserName(this);
        String myAvatar = UserPreferences.getUserAvatar(this);
        String timeStr = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());

        Message myMsg = new Message(
                0, conversationId, myName, myAvatar, text,
                System.currentTimeMillis(), timeStr, true, "sent"
        );

        DatabaseHelper.getInstance(this).insertMessage(myMsg);
        addMessageView(text, timeStr, true, myName);
        etMessage.setText("");
        scrollToBottom();

        // Trigger Simulated Response
        scheduleBotReply(text);
    }

    private void scheduleBotReply(String userText) {
        // Step 1: Typing Indicator after 800ms
        handler.postDelayed(() -> {
            if (!isFinishing() && tvChatStatus != null) {
                tvChatStatus.setText("typing...");
                tvChatStatus.setTextColor(Color.parseColor("#58B88A"));
            }
        }, 800);

        // Step 2: Send Reply after 2000ms
        handler.postDelayed(() -> {
            if (isFinishing()) return;

            SmartReplyEngine.BotReply botReply = SmartReplyEngine.generateReply(
                    conversationId, chatName, chatAvatar, isGroup, userText
            );

            String timeStr = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());

            Message replyMsg = new Message(
                    0, conversationId, botReply.senderName, botReply.senderAvatar,
                    botReply.replyText, System.currentTimeMillis(), timeStr, false, "read"
            );

            DatabaseHelper.getInstance(ChatActivity.this).insertMessage(replyMsg);

            // Add notification
            DatabaseHelper.getInstance(ChatActivity.this).insertNotification(new NotificationItem(
                    0, botReply.senderName, botReply.senderAvatar, botReply.replyText,
                    "Just now", false, conversationId, System.currentTimeMillis()
            ));

            addMessageView(botReply.replyText, timeStr, false, botReply.senderName);
            scrollToBottom();

            if (tvChatStatus != null) {
                tvChatStatus.setText(onlineStatus);
                tvChatStatus.setTextColor(Color.parseColor("#77718A"));
            }

            try {
                Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (vib != null) vib.vibrate(30);
            } catch (Exception ignored) {}

        }, 2200);
    }

    private void addMessageView(String text, String time, boolean isFromMe, String senderName) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.gravity = isFromMe ? Gravity.END : Gravity.START;
        rowParams.bottomMargin = 14;
        rowParams.leftMargin = isFromMe ? 60 : 0;
        rowParams.rightMargin = isFromMe ? 0 : 60;
        row.setLayoutParams(rowParams);

        // Group Sender Label
        if (!isFromMe && isGroup && senderName != null && !senderName.isEmpty()) {
            TextView tvSender = new TextView(this);
            tvSender.setText(senderName);
            tvSender.setTextSize(11);
            tvSender.setTypeface(null, Typeface.BOLD);
            tvSender.setTextColor(Color.parseColor("#77718A"));
            tvSender.setPadding(12, 0, 0, 4);
            row.addView(tvSender);
        }

        // Bubble Content Container
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(isFromMe ? R.drawable.bubble_sent : R.drawable.bubble_received);
        bubble.setPadding(20, 14, 20, 10);

        TextView tvText = new TextView(this);
        tvText.setText(text);
        tvText.setTextSize(15);
        tvText.setTextColor(Color.parseColor("#19152F"));
        tvText.setLineSpacing(4, 1.1f);
        bubble.addView(tvText);

        TextView tvTime = new TextView(this);
        tvTime.setText(time);
        tvTime.setTextSize(10);
        tvTime.setTextColor(Color.parseColor("#77718A"));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        timeParams.gravity = Gravity.END;
        timeParams.topMargin = 4;
        tvTime.setLayoutParams(timeParams);
        bubble.addView(tvTime);

        row.addView(bubble);
        messageContainer.addView(row);
    }

    private void scrollToBottom() {
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void showAttachmentOptions() {
        String[] options = {"🌸 Send Sticker", "📸 Share Photo", "📍 Share Location", "🎙️ Audio Message"};
        new AlertDialog.Builder(this)
                .setTitle("Share in Chat")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showStickersDialog();
                            break;
                        case 1:
                            sendQuickAttachment("📸 Photo attachment [Sunset.jpg]");
                            break;
                        case 2:
                            sendQuickAttachment("📍 Shared location: City Park, Cafe Bloom");
                            break;
                        case 3:
                            sendQuickAttachment("🎙️ Voice note (0:18)");
                            break;
                    }
                })
                .show();
    }

    private void showStickersDialog() {
        String[] stickers = {"🌸 Cherry Blossom", "✨ Sparkles", "☕ Coffee Cup", "💖 Heart", "🎉 Party Popper", "🏖️ Vacation Vibes"};
        new AlertDialog.Builder(this)
                .setTitle("Select Sticker")
                .setItems(stickers, (dialog, which) -> {
                    String[] emojis = {"🌸", "✨", "☕", "💖", "🎉", "🏖️"};
                    sendQuickAttachment(emojis[which]);
                })
                .show();
    }

    private void sendQuickAttachment(String content) {
        String myName = UserPreferences.getUserName(this);
        String myAvatar = UserPreferences.getUserAvatar(this);
        String timeStr = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());

        Message myMsg = new Message(
                0, conversationId, myName, myAvatar, content,
                System.currentTimeMillis(), timeStr, true, "sent"
        );

        DatabaseHelper.getInstance(this).insertMessage(myMsg);
        addMessageView(content, timeStr, true, myName);
        scrollToBottom();
        scheduleBotReply(content);
    }

    private void showMenuDialog() {
        String[] options = {"Clear Chat", "Search Messages", "Contact Info"};
        new AlertDialog.Builder(this)
                .setTitle(chatName)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        DatabaseHelper.getInstance(this).clearMessages(conversationId);
                        loadMessages();
                        Toast.makeText(this, "Chat cleared", Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        Toast.makeText(this, "Search active: Type above to find items", Toast.LENGTH_SHORT).show();
                    } else if (which == 2) {
                        new AlertDialog.Builder(this)
                                .setTitle(chatName)
                                .setMessage("Status: " + onlineStatus + "\n\nEnd-to-end encrypted connection with LumaChat.")
                                .setPositiveButton("OK", null)
                                .show();
                    }
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
