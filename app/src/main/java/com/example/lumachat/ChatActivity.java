package com.example.lumachat;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AlphaAnimation;
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
import com.example.lumachat.model.Message;
import com.example.lumachat.model.NotificationItem;
import com.example.lumachat.network.P2PChatManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends AppCompatActivity implements P2PChatManager.OnMessageReceivedListener, P2PChatManager.OnPeerStatusListener {

    private String conversationId;
    private String chatName;
    private String chatAvatar;
    private boolean isGroup;
    private String onlineStatus;

    private TextView tvChatName;
    private TextView tvChatAvatar;
    private TextView tvChatStatus;
    private TextView btnLiveP2P;
    private LinearLayout messageContainer;
    private ScrollView chatScroll;
    private EditText etMessage;

    // Quoted reply
    private LinearLayout replyPreviewLayout;
    private TextView tvReplyPreviewTitle;
    private TextView tvReplyPreviewText;
    private String quotedMessage = null;

    private Handler handler = new Handler(Looper.getMainLooper());
    private P2PChatManager p2pManager;

    private static String activeChatConvId = null;

    public static boolean isCurrentlyActive(String convId) {
        return activeChatConvId != null && activeChatConvId.equals(convId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        activeChatConvId = conversationId;
        updateStatusDisplay();
    }

    @Override
    protected void onPause() {
        super.onPause();
        activeChatConvId = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        p2pManager = P2PChatManager.getInstance();
        p2pManager.init(this);
        p2pManager.setMessageListener(this);
        p2pManager.setStatusListener(this);

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
        btnLiveP2P = findViewById(R.id.btnLiveP2P);
        messageContainer = findViewById(R.id.messageContainer);
        chatScroll = findViewById(R.id.chatScroll);
        etMessage = findViewById(R.id.etMessage);

        replyPreviewLayout = findViewById(R.id.replyPreviewLayout);
        tvReplyPreviewTitle = findViewById(R.id.tvReplyPreviewTitle);
        tvReplyPreviewText = findViewById(R.id.tvReplyPreviewText);

        tvChatName.setText(chatName);
        tvChatAvatar.setText(chatAvatar);
        updateStatusDisplay();

        findViewById(R.id.btnBackChat).setOnClickListener(v -> finish());

        // Live P2P Wi-Fi dialog
        btnLiveP2P.setOnClickListener(v -> showP2PDialog());

        // Cancel Reply
        findViewById(R.id.btnCancelReply).setOnClickListener(v -> cancelReply());

        // Voice note quick action
        findViewById(R.id.btnVoiceNote).setOnClickListener(v -> sendVoiceNote());

        // Call Actions (Voice & Video)
        findViewById(R.id.btnCallChat).setOnClickListener(v -> {
            Intent callIntent = new Intent(ChatActivity.this, CallActivity.class);
            callIntent.putExtra("name", chatName);
            callIntent.putExtra("avatar", chatAvatar);
            callIntent.putExtra("call_type", "voice");
            callIntent.putExtra("is_incoming", false);
            startActivity(callIntent);
        });

        View btnVideo = findViewById(R.id.btnVideoCallChat);
        if (btnVideo != null) {
            btnVideo.setOnClickListener(v -> {
                Intent callIntent = new Intent(ChatActivity.this, CallActivity.class);
                callIntent.putExtra("name", chatName);
                callIntent.putExtra("avatar", chatAvatar);
                callIntent.putExtra("call_type", "video");
                callIntent.putExtra("is_incoming", false);
                startActivity(callIntent);
            });
        }

        // Menu Action
        findViewById(R.id.btnMenuChat).setOnClickListener(v -> showMenuDialog());

        // Attachment Action
        findViewById(R.id.btnAttach).setOnClickListener(v -> showAttachmentOptions());

        // Send Action
        findViewById(R.id.btnSend).setOnClickListener(v -> sendMessage());

        loadMessages();
    }

    private void updateStatusDisplay() {
        if (p2pManager.isConnected()) {
            tvChatStatus.setText("Live P2P Connected 🟢");
            tvChatStatus.setTextColor(Color.parseColor("#58B88A"));
            btnLiveP2P.setText("🟢 Live");
            btnLiveP2P.setBackgroundResource(R.drawable.button_background);
        } else {
            tvChatStatus.setText(onlineStatus);
            tvChatStatus.setTextColor(Color.parseColor("#77718A"));
            btnLiveP2P.setText("🌐 P2P");
            btnLiveP2P.setBackgroundResource(R.drawable.input_field);
        }
    }

    private void loadMessages() {
        messageContainer.removeAllViews();

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

        // Prepend quote if replying
        if (quotedMessage != null) {
            text = "↳ Replying: \"" + quotedMessage + "\"\n" + text;
            cancelReply();
        }

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

        // Check if P2P Live is connected
        if (p2pManager.isConnected()) {
            boolean sent = p2pManager.sendMessage(myMsg, conversationId);
            if (!sent) {
                scheduleBotReply(text);
            }
        } else {
            scheduleBotReply(text);
        }
    }

    private void sendVoiceNote() {
        String myName = UserPreferences.getUserName(this);
        String myAvatar = UserPreferences.getUserAvatar(this);
        String timeStr = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());
        String text = "🎙️ Voice note (0:18)";

        Message myMsg = new Message(
                0, conversationId, myName, myAvatar, text,
                System.currentTimeMillis(), timeStr, true, "sent"
        );

        DatabaseHelper.getInstance(this).insertMessage(myMsg);
        addMessageView(text, timeStr, true, myName);
        scrollToBottom();

        if (p2pManager.isConnected()) {
            p2pManager.sendMessage(myMsg, conversationId);
        } else {
            scheduleBotReply("voice message");
        }
    }

    private void scheduleBotReply(String userText) {
        handler.postDelayed(() -> {
            if (!isFinishing() && tvChatStatus != null && !p2pManager.isConnected()) {
                tvChatStatus.setText("typing...");
                tvChatStatus.setTextColor(Color.parseColor("#58B88A"));
            }
        }, 800);

        handler.postDelayed(() -> {
            if (isFinishing() || p2pManager.isConnected()) return;

            SmartReplyEngine.BotReply botReply = SmartReplyEngine.generateReply(
                    conversationId, chatName, chatAvatar, isGroup, userText
            );

            String timeStr = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());

            Message replyMsg = new Message(
                    0, conversationId, botReply.senderName, botReply.senderAvatar,
                    botReply.replyText, System.currentTimeMillis(), timeStr, false, "read"
            );

            DatabaseHelper.getInstance(ChatActivity.this).insertMessage(replyMsg);

            DatabaseHelper.getInstance(ChatActivity.this).insertNotification(new NotificationItem(
                    0, botReply.senderName, botReply.senderAvatar, botReply.replyText,
                    "Just now", false, conversationId, System.currentTimeMillis()
            ));

            addMessageView(botReply.replyText, timeStr, false, botReply.senderName);
            scrollToBottom();

            if (tvChatStatus != null) {
                updateStatusDisplay();
            }

            vibrate();

        }, 2200);
    }

    @Override
    public void onMessageReceived(Message message) {
        // Called when a real second phone sends a message over Wi-Fi!
        if (message.getConversationId().equals(conversationId) || !isGroup) {
            addMessageView(message.getMessageText(), message.getTimeFormatted(), false, message.getSenderName());
            scrollToBottom();
            vibrate();
        }
    }

    @Override
    public void onStatusChanged(boolean isConnected, String message) {
        updateStatusDisplay();
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
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

        // Bubble Container
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(isFromMe ? R.drawable.bubble_sent : R.drawable.bubble_received);
        bubble.setPadding(20, 14, 20, 10);

        // Audio Message Player UI
        if (text.startsWith("🎙️")) {
            LinearLayout audioBar = new LinearLayout(this);
            audioBar.setOrientation(LinearLayout.HORIZONTAL);
            audioBar.setGravity(Gravity.CENTER_VERTICAL);
            audioBar.setPadding(0, 4, 0, 4);

            TextView btnPlay = new TextView(this);
            btnPlay.setText("▶");
            btnPlay.setTextSize(16);
            btnPlay.setTextColor(Color.parseColor("#19152F"));
            btnPlay.setPadding(8, 0, 14, 0);

            TextView waveform = new TextView(this);
            waveform.setText(" ılılı·|·lılı·|·lı ");
            waveform.setTextSize(14);
            waveform.setTextColor(Color.parseColor("#7C5CBF"));

            TextView duration = new TextView(this);
            duration.setText(" 0:18");
            duration.setTextSize(12);
            duration.setTextColor(Color.parseColor("#77718A"));

            btnPlay.setOnClickListener(v -> {
                boolean isPlaying = "⏸".equals(btnPlay.getText().toString());
                btnPlay.setText(isPlaying ? "▶" : "⏸");
                Toast.makeText(this, isPlaying ? "Audio paused" : "Playing voice note...", Toast.LENGTH_SHORT).show();
            });

            audioBar.addView(btnPlay);
            audioBar.addView(waveform);
            audioBar.addView(duration);
            bubble.addView(audioBar);
        } else {
            // Regular Text Message
            TextView tvText = new TextView(this);
            tvText.setText(text);
            tvText.setTextSize(15);
            tvText.setTextColor(Color.parseColor("#19152F"));
            tvText.setLineSpacing(4, 1.1f);
            bubble.addView(tvText);
        }

        // Time and Double-Tick Indicator
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        footerParams.gravity = Gravity.END;
        footerParams.topMargin = 4;
        footer.setLayoutParams(footerParams);

        TextView tvTime = new TextView(this);
        tvTime.setText(time);
        tvTime.setTextSize(10);
        tvTime.setTextColor(Color.parseColor("#77718A"));
        footer.addView(tvTime);

        if (isFromMe) {
            TextView tvTick = new TextView(this);
            tvTick.setText("  ✓✓");
            tvTick.setTextSize(10);
            tvTick.setTextColor(Color.parseColor("#7C5CBF"));
            footer.addView(tvTick);
        }

        bubble.addView(footer);

        // Reaction Badge (Initially null, added when reacted)
        TextView reactionBadge = new TextView(this);
        reactionBadge.setTextSize(13);
        reactionBadge.setBackgroundResource(R.drawable.card_background);
        reactionBadge.setPadding(10, 4, 10, 4);
        reactionBadge.setVisibility(View.GONE);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        badgeParams.gravity = isFromMe ? Gravity.END : Gravity.START;
        badgeParams.topMargin = -10;
        reactionBadge.setLayoutParams(badgeParams);

        // Tap or Long press for reaction & reply
        bubble.setOnLongClickListener(v -> {
            showReactionAndOptionsPopup(text, reactionBadge);
            return true;
        });

        row.addView(bubble);
        row.addView(reactionBadge);

        // Smooth fade-in animation
        AlphaAnimation anim = new AlphaAnimation(0.0f, 1.0f);
        anim.setDuration(220);
        row.startAnimation(anim);

        messageContainer.addView(row);
    }

    private void showReactionAndOptionsPopup(String messageText, TextView reactionBadge) {
        String[] actions = {
                "❤️  Love",
                "😂  Haha",
                "😮  Wow",
                "👍  Thumbs Up",
                "🔥  Fire",
                "↳  Reply to message",
                "📋  Copy text"
        };

        new AlertDialog.Builder(this)
                .setTitle("Message Actions")
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0: setReaction(reactionBadge, "❤️"); break;
                        case 1: setReaction(reactionBadge, "😂"); break;
                        case 2: setReaction(reactionBadge, "😮"); break;
                        case 3: setReaction(reactionBadge, "👍"); break;
                        case 4: setReaction(reactionBadge, "🔥"); break;
                        case 5:
                            quotedMessage = messageText.length() > 40 ? messageText.substring(0, 37) + "..." : messageText;
                            replyPreviewLayout.setVisibility(View.VISIBLE);
                            tvReplyPreviewTitle.setText("Replying to " + chatName);
                            tvReplyPreviewText.setText(quotedMessage);
                            etMessage.requestFocus();
                            break;
                        case 6:
                            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            cm.setText(messageText);
                            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                            break;
                    }
                })
                .show();
    }

    private void setReaction(TextView reactionBadge, String emoji) {
        reactionBadge.setText(emoji);
        reactionBadge.setVisibility(View.VISIBLE);
        vibrate();
    }

    private void cancelReply() {
        quotedMessage = null;
        replyPreviewLayout.setVisibility(View.GONE);
    }

    private void showP2PDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_p2p_connect, null);
        TextView tvMyIp = dialogView.findViewById(R.id.tvMyIpAddress);
        TextView btnHost = dialogView.findViewById(R.id.btnHostServer);
        EditText etPeerIp = dialogView.findViewById(R.id.etPeerIp);
        TextView btnConnect = dialogView.findViewById(R.id.btnConnectPeer);
        TextView tvStatus = dialogView.findViewById(R.id.tvP2PStatus);
        TextView btnClose = dialogView.findViewById(R.id.btnCloseP2P);

        String myIp = p2pManager.getLocalIpAddress(this);
        tvMyIp.setText(myIp + " : " + P2PChatManager.DEFAULT_PORT);

        String lastSaved = p2pManager.getLastSavedAddress(this);
        if (lastSaved != null && !lastSaved.isEmpty()) {
            etPeerIp.setText(lastSaved);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnHost.setOnClickListener(v -> {
            p2pManager.startServer(P2PChatManager.DEFAULT_PORT);
            tvStatus.setText("📡 Hosting on port " + P2PChatManager.DEFAULT_PORT + "... Waiting for friend to connect.");
            tvStatus.setTextColor(Color.parseColor("#7C5CBF"));
            btnHost.setText("✓ Hosting Active");
        });

        btnConnect.setOnClickListener(v -> {
            String peerIp = etPeerIp.getText().toString().trim();
            if (peerIp.isEmpty()) {
                Toast.makeText(this, "Please enter your friend's IP", Toast.LENGTH_SHORT).show();
                return;
            }
            tvStatus.setText("Connecting to " + peerIp + "...");
            p2pManager.connectToPeer(peerIp, P2PChatManager.DEFAULT_PORT);
        });

        p2pManager.setStatusListener((connected, msg) -> {
            if (tvStatus != null) {
                tvStatus.setText(msg);
                tvStatus.setTextColor(connected ? Color.parseColor("#58B88A") : Color.parseColor("#77718A"));
            }
            updateStatusDisplay();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
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
                        case 0: showStickersDialog(); break;
                        case 1: sendQuickAttachment("📸 Photo [Sunset.jpg]"); break;
                        case 2: sendQuickAttachment("📍 Location: Central Park"); break;
                        case 3: sendVoiceNote(); break;
                    }
                })
                .show();
    }

    private void showStickersDialog() {
        String[] stickers = {"🌸 Cherry Blossom", "✨ Sparkles", "☕ Coffee Cup", "💖 Heart", "🎉 Party Popper", "🏖️ Vacation"};
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

        if (p2pManager.isConnected()) {
            p2pManager.sendMessage(myMsg, conversationId);
        } else {
            scheduleBotReply(content);
        }
    }

    private void showMenuDialog() {
        String[] options = {
                p2pManager.isConnected() ? "Disconnect P2P Session" : "Connect Two Phones (P2P Wi-Fi)",
                "Clear Chat",
                "Contact Info"
        };
        new AlertDialog.Builder(this)
                .setTitle(chatName)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (p2pManager.isConnected()) {
                            p2pManager.disconnect();
                        } else {
                            showP2PDialog();
                        }
                    } else if (which == 1) {
                        DatabaseHelper.getInstance(this).clearMessages(conversationId);
                        loadMessages();
                        Toast.makeText(this, "Chat cleared", Toast.LENGTH_SHORT).show();
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

    private void vibrate() {
        try {
            Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null) vib.vibrate(30);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
