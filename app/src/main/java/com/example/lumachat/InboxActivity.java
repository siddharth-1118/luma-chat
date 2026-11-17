package com.example.lumachat;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.example.lumachat.data.DatabaseHelper;
import com.example.lumachat.data.UserPreferences;
import com.example.lumachat.model.Conversation;
import com.example.lumachat.model.Message;
import com.example.lumachat.model.OnlineUser;
import com.example.lumachat.network.P2PChatManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InboxActivity extends AppCompatActivity {

    private LinearLayout conversationListContainer;
    private TextView tvEmptyInbox;
    private EditText etInboxSearch;
    private TextView tabAll, tabUnread, tabPinned;
    private LinearLayout onlineUsersContainer;
    private TextView tvOnlineCountBadge;

    private String currentFilter = "All";
    private String currentSearch = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        conversationListContainer = findViewById(R.id.conversationListContainer);
        tvEmptyInbox = findViewById(R.id.tvEmptyInbox);
        etInboxSearch = findViewById(R.id.etInboxSearch);
        onlineUsersContainer = findViewById(R.id.onlineUsersContainer);
        tvOnlineCountBadge = findViewById(R.id.tvOnlineCountBadge);

        tabAll = findViewById(R.id.tabAll);
        tabUnread = findViewById(R.id.tabUnread);
        tabPinned = findViewById(R.id.tabPinned);

        tabAll.setOnClickListener(v -> selectFilter("All"));
        tabUnread.setOnClickListener(v -> selectFilter("Unread"));
        tabPinned.setOnClickListener(v -> selectFilter("Pinned"));

        // New Chat FAB
        findViewById(R.id.btnNewChat).setOnClickListener(v -> showNewChatDialog());
        findViewById(R.id.btnP2PInbox).setOnClickListener(v -> showP2PDialog());

        P2PChatManager p2p = P2PChatManager.getInstance();
        p2p.init(this);
        p2p.setUsersListener(users -> runOnUiThread(() -> renderOnlineUsers(users)));

        // Search listener
        etInboxSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearch = s.toString();
                loadConversations();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Intent search query
        String initialSearch = getIntent().getStringExtra("search_query");
        if (initialSearch != null && !initialSearch.isEmpty()) {
            etInboxSearch.setText(initialSearch);
            currentSearch = initialSearch;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConversations();

        P2PChatManager p2p = P2PChatManager.getInstance();
        p2p.init(this);
        if (!p2p.isConnected()) {
            String lastSaved = p2p.getLastSavedAddress(this);
            if (lastSaved != null && !lastSaved.isEmpty()) {
                p2p.connectToPeer(lastSaved, P2PChatManager.DEFAULT_PORT);
            }
        }
        renderOnlineUsers(p2p.getOnlineUsers());
    }

    private void renderOnlineUsers(List<OnlineUser> users) {
        if (onlineUsersContainer == null) return;
        onlineUsersContainer.removeAllViews();

        if (users == null || users.isEmpty()) {
            tvOnlineCountBadge.setText("0 active");
            TextView prompt = new TextView(this);
            prompt.setText("Tap to connect to relay server");
            prompt.setTextSize(11);
            prompt.setTextColor(Color.parseColor("#7C5CBF"));
            prompt.setBackgroundResource(R.drawable.input_field);
            prompt.setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8));
            prompt.setOnClickListener(v -> showP2PDialog());
            onlineUsersContainer.addView(prompt);
            return;
        }

        tvOnlineCountBadge.setText(users.size() + " active");

        for (OnlineUser user : users) {
            View card = createOnlineUserCard(user);
            onlineUsersContainer.addView(card);
        }
    }

    private View createOnlineUserCard(OnlineUser user) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardLp.setMargins(dpToPx(4), dpToPx(2), dpToPx(10), dpToPx(4));
        card.setLayoutParams(cardLp);
        card.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));

        // Frame with avatar and online green dot
        FrameLayout frame = new FrameLayout(this);
        frame.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(52), dpToPx(52)));

        TextView tvAvatar = new TextView(this);
        tvAvatar.setLayoutParams(new FrameLayout.LayoutParams(dpToPx(52), dpToPx(52)));
        tvAvatar.setGravity(Gravity.CENTER);
        tvAvatar.setText(user.getAvatar());
        tvAvatar.setTextSize(24);
        tvAvatar.setBackgroundResource(R.drawable.button_background);

        TextView tvDot = new TextView(this);
        FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dpToPx(13), dpToPx(13));
        dotLp.gravity = Gravity.BOTTOM | Gravity.END;
        tvDot.setLayoutParams(dotLp);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(Color.parseColor("#4CAF50"));
        dotBg.setStroke(dpToPx(2), Color.WHITE);
        tvDot.setBackground(dotBg);

        frame.addView(tvAvatar);
        frame.addView(tvDot);

        // Name
        TextView tvName = new TextView(this);
        tvName.setText(user.getName());
        tvName.setTextSize(11);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setTextColor(Color.parseColor("#19152F"));
        tvName.setGravity(Gravity.CENTER);
        tvName.setMaxLines(1);
        tvName.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                dpToPx(60), ViewGroup.LayoutParams.WRAP_CONTENT
        );
        nameLp.setMargins(0, dpToPx(4), 0, 0);
        tvName.setLayoutParams(nameLp);

        card.addView(frame);
        card.addView(tvName);

        // Click to open or create 1-on-1 chat
        card.setOnClickListener(v -> {
            String convId = "chat_" + user.getUserId();
            DatabaseHelper db = DatabaseHelper.getInstance(this);
            Conversation conv = db.getConversation(convId);
            if (conv == null) {
                String time = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());
                conv = new Conversation(
                        convId, user.getName(), user.getAvatar(), false, "",
                        user.getBio().isEmpty() ? "Connected online" : user.getBio(),
                        time, 0, false, "Online", System.currentTimeMillis()
                );
                db.insertConversation(conv);
                loadConversations();
            }

            Intent intent = new Intent(InboxActivity.this, ChatActivity.class);
            intent.putExtra("id", convId);
            intent.putExtra("name", user.getName());
            intent.putExtra("avatar", user.getAvatar());
            intent.putExtra("is_group", false);
            intent.putExtra("status", "Online");
            startActivity(intent);
        });

        return card;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void selectFilter(String filter) {
        currentFilter = filter;
        updateTabStyles();
        loadConversations();
    }

    private void updateTabStyles() {
        tabAll.setBackgroundResource("All".equals(currentFilter) ? R.drawable.button_background : R.drawable.input_field);
        tabUnread.setBackgroundResource("Unread".equals(currentFilter) ? R.drawable.button_background : R.drawable.input_field);
        tabPinned.setBackgroundResource("Pinned".equals(currentFilter) ? R.drawable.button_background : R.drawable.input_field);
    }

    private void loadConversations() {
        conversationListContainer.removeAllViews();
        List<Conversation> list = DatabaseHelper.getInstance(this)
                .getConversationsWithFilter(currentFilter, currentSearch, false);

        if (list.isEmpty()) {
            tvEmptyInbox.setVisibility(View.VISIBLE);
        } else {
            tvEmptyInbox.setVisibility(View.GONE);
            LayoutInflater inflater = LayoutInflater.from(this);

            for (Conversation conv : list) {
                View item = inflater.inflate(R.layout.chat_item, conversationListContainer, false);

                TextView tvAvatar = item.findViewById(R.id.tvAvatar);
                TextView tvName = item.findViewById(R.id.tvContactName);
                TextView tvMessage = item.findViewById(R.id.tvLastMessage);
                TextView tvTime = item.findViewById(R.id.tvTime);
                TextView tvUnread = item.findViewById(R.id.tvUnreadCount);

                tvAvatar.setText(conv.getAvatar());
                tvName.setText(conv.getName() + (conv.isPinned() ? " 📌" : ""));
                tvMessage.setText(conv.getLastMessage());
                tvTime.setText(conv.getLastTime());

                if (conv.getUnreadCount() > 0) {
                    tvUnread.setText(String.valueOf(conv.getUnreadCount()));
                    tvUnread.setVisibility(View.VISIBLE);
                } else {
                    tvUnread.setVisibility(View.GONE);
                }

                item.setOnClickListener(v -> {
                    DatabaseHelper.getInstance(this).markConversationRead(conv.getId());
                    Intent intent = new Intent(InboxActivity.this, ChatActivity.class);
                    intent.putExtra("id", conv.getId());
                    intent.putExtra("name", conv.getName());
                    intent.putExtra("avatar", conv.getAvatar());
                    intent.putExtra("is_group", conv.isGroup());
                    intent.putExtra("status", conv.getOnlineStatus());
                    startActivity(intent);
                });

                item.setOnLongClickListener(v -> {
                    showConversationOptions(conv);
                    return true;
                });

                conversationListContainer.addView(item);
            }
        }
    }

    private void showConversationOptions(Conversation conv) {
        String pinAction = conv.isPinned() ? "Unpin Conversation" : "Pin to Top";
        String readAction = (conv.getUnreadCount() > 0) ? "Mark as Read" : "Mark as Unread";
        String[] options = {pinAction, readAction, "Clear Messages", "Delete Conversation"};

        new AlertDialog.Builder(this)
                .setTitle(conv.getName())
                .setItems(options, (dialog, which) -> {
                    DatabaseHelper db = DatabaseHelper.getInstance(this);
                    switch (which) {
                        case 0:
                            db.setConversationPinned(conv.getId(), !conv.isPinned());
                            Toast.makeText(this, conv.isPinned() ? "Unpinned" : "Pinned", Toast.LENGTH_SHORT).show();
                            loadConversations();
                            break;
                        case 1:
                            if (conv.getUnreadCount() > 0) {
                                db.markConversationRead(conv.getId());
                            } else {
                                conv.setUnreadCount(1);
                                db.updateConversation(conv);
                            }
                            loadConversations();
                            break;
                        case 2:
                            db.clearMessages(conv.getId());
                            Toast.makeText(this, "Messages cleared", Toast.LENGTH_SHORT).show();
                            loadConversations();
                            break;
                        case 3:
                            db.deleteConversation(conv.getId());
                            Toast.makeText(this, "Conversation deleted", Toast.LENGTH_SHORT).show();
                            loadConversations();
                            break;
                    }
                })
                .show();
    }

    private void showNewChatDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_new_chat, null);
        EditText etName = dialogView.findViewById(R.id.etNewChatName);
        EditText etAvatar = dialogView.findViewById(R.id.etNewChatAvatar);
        EditText etMsg = dialogView.findViewById(R.id.etNewChatMessage);

        // Populate online friends chips if any
        LinearLayout dialogChips = dialogView.findViewById(R.id.dialogOnlineChipsContainer);
        List<OnlineUser> online = P2PChatManager.getInstance().getOnlineUsers();
        if (online.isEmpty()) {
            dialogView.findViewById(R.id.layoutDialogOnlineFriends).setVisibility(View.GONE);
        } else {
            dialogView.findViewById(R.id.layoutDialogOnlineFriends).setVisibility(View.VISIBLE);
            dialogChips.removeAllViews();
            for (OnlineUser u : online) {
                TextView chip = new TextView(this);
                chip.setText("● " + u.getAvatar() + " " + u.getName());
                chip.setTextSize(12);
                chip.setTextColor(Color.parseColor("#19152F"));
                chip.setBackgroundResource(R.drawable.button_background);
                chip.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                lp.setMargins(0, 0, dpToPx(8), 0);
                chip.setLayoutParams(lp);

                chip.setOnClickListener(v -> {
                    etName.setText(u.getName());
                    etAvatar.setText(u.getAvatar());
                });
                dialogChips.addView(chip);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btnCancelNewChat).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnCreateNewChat).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a contact name", Toast.LENGTH_SHORT).show();
                return;
            }

            String avatar = etAvatar.getText().toString().trim();
            if (avatar.isEmpty()) {
                avatar = name.substring(0, 1).toUpperCase();
            }

            String msg = etMsg.getText().toString().trim();
            String time = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());

            String convId = "chat_" + name.toLowerCase().replaceAll("[^a-zA-Z0-9]", "_");
            Conversation newConv = new Conversation(
                    convId, name, avatar, false, "",
                    msg.isEmpty() ? "Started new conversation" : msg,
                    time, 0, false, "Online", System.currentTimeMillis()
            );

            DatabaseHelper db = DatabaseHelper.getInstance(this);
            db.insertConversation(newConv);

            if (!msg.isEmpty()) {
                String myName = UserPreferences.getUserName(this);
                String myAvatar = UserPreferences.getUserAvatar(this);
                db.insertMessage(new Message(
                        0, convId, myName, myAvatar, msg,
                        System.currentTimeMillis(), time, true, "sent"
                ));
            }

            dialog.dismiss();
            loadConversations();

            // Open the new chat directly!
            Intent intent = new Intent(InboxActivity.this, ChatActivity.class);
            intent.putExtra("id", convId);
            intent.putExtra("name", name);
            intent.putExtra("avatar", avatar);
            intent.putExtra("is_group", false);
            intent.putExtra("status", "Online");
            startActivity(intent);
        });

        dialog.show();
    }

    private void showP2PDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_p2p_connect, null);
        TextView tvMyIp = dialogView.findViewById(R.id.tvMyIpAddress);
        TextView btnHost = dialogView.findViewById(R.id.btnHostServer);
        EditText etPeerIp = dialogView.findViewById(R.id.etPeerIp);
        TextView btnConnect = dialogView.findViewById(R.id.btnConnectPeer);
        TextView tvStatus = dialogView.findViewById(R.id.tvP2PStatus);
        TextView btnClose = dialogView.findViewById(R.id.btnCloseP2P);

        com.example.lumachat.network.P2PChatManager p2p = com.example.lumachat.network.P2PChatManager.getInstance();
        p2p.init(this);
        String myIp = p2p.getLocalIpAddress(this);
        tvMyIp.setText(myIp + " : " + com.example.lumachat.network.P2PChatManager.DEFAULT_PORT);

        String lastSaved = p2p.getLastSavedAddress(this);
        if (lastSaved != null && !lastSaved.isEmpty()) {
            etPeerIp.setText(lastSaved);
        }

        TextView btnPresetCloud = dialogView.findViewById(R.id.btnPresetCloud);
        TextView btnPresetWifi = dialogView.findViewById(R.id.btnPresetWifi);
        if (btnPresetCloud != null) {
            btnPresetCloud.setOnClickListener(v -> {
                String cloudUrl = "https://nancey-pandemoniacal-candra.ngrok-free.dev";
                etPeerIp.setText(cloudUrl);
                tvStatus.setText("Connecting to Cloud Relay...");
                p2p.connectToPeer(cloudUrl, com.example.lumachat.network.P2PChatManager.DEFAULT_PORT);
            });
        }
        if (btnPresetWifi != null) {
            btnPresetWifi.setOnClickListener(v -> {
                String wifiUrl = "http://10.9.151.112:8080";
                etPeerIp.setText(wifiUrl);
                tvStatus.setText("Connecting to PC Wi-Fi Relay...");
                p2p.connectToPeer(wifiUrl, com.example.lumachat.network.P2PChatManager.DEFAULT_PORT);
            });
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnHost.setOnClickListener(v -> {
            p2p.startServer(com.example.lumachat.network.P2PChatManager.DEFAULT_PORT);
            tvStatus.setText("📡 Hosting on port " + com.example.lumachat.network.P2PChatManager.DEFAULT_PORT + "... Waiting for peer to connect.");
            btnHost.setText("✓ Hosting Active");
        });

        btnConnect.setOnClickListener(v -> {
            String peerIp = etPeerIp.getText().toString().trim();
            if (peerIp.isEmpty()) {
                Toast.makeText(this, "Please enter your friend's IP", Toast.LENGTH_SHORT).show();
                return;
            }
            tvStatus.setText("Connecting to " + peerIp + "...");
            p2p.connectToPeer(peerIp, com.example.lumachat.network.P2PChatManager.DEFAULT_PORT);
        });

        p2p.setStatusListener((connected, msg) -> {
            if (tvStatus != null) {
                tvStatus.setText(msg);
            }
            if (connected) {
                Toast.makeText(this, "Connected! Open chat to talk live with friend.", Toast.LENGTH_LONG).show();
            }
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
