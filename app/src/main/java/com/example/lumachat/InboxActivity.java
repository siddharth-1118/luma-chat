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

import com.example.lumachat.data.DatabaseHelper;
import com.example.lumachat.data.UserPreferences;
import com.example.lumachat.model.Conversation;
import com.example.lumachat.model.Message;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InboxActivity extends AppCompatActivity {

    private LinearLayout conversationListContainer;
    private TextView tvEmptyInbox;
    private EditText etInboxSearch;
    private TextView tabAll, tabUnread, tabPinned;

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

        tabAll = findViewById(R.id.tabAll);
        tabUnread = findViewById(R.id.tabUnread);
        tabPinned = findViewById(R.id.tabPinned);

        tabAll.setOnClickListener(v -> selectFilter("All"));
        tabUnread.setOnClickListener(v -> selectFilter("Unread"));
        tabPinned.setOnClickListener(v -> selectFilter("Pinned"));

        // New Chat FAB
        findViewById(R.id.btnNewChat).setOnClickListener(v -> showNewChatDialog());
        findViewById(R.id.btnP2PInbox).setOnClickListener(v -> showP2PDialog());

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
