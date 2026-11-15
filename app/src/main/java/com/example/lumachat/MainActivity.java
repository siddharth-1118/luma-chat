package com.example.lumachat;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.lumachat.data.DatabaseHelper;
import com.example.lumachat.data.UserPreferences;

public class MainActivity extends AppCompatActivity {

    private TextView tvHeaderAvatar;
    private TextView tvHeaderGreeting;
    private TextView tvPersonalSpaceAvatar;
    private TextView tvNotifBadge;
    private EditText etMainSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply persisted dark mode
        if (UserPreferences.isDarkMode(this)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvHeaderAvatar = findViewById(R.id.tvHeaderAvatar);
        tvHeaderGreeting = findViewById(R.id.tvHeaderGreeting);
        tvPersonalSpaceAvatar = findViewById(R.id.tvPersonalSpaceAvatar);
        tvNotifBadge = findViewById(R.id.tvNotifBadge);
        etMainSearch = findViewById(R.id.etMainSearch);

        // Navigation Clicks
        findViewById(R.id.cardInbox).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, InboxActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.cardCircles).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CirclesActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.cardMoments).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MomentsActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.cardActivity).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ActivityLogActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.cardPersonalSpace).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PersonalSpaceActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnNotifications).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, NotificationsActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.cardP2PConnect).setOnClickListener(v -> showP2PDialog());

        // Search action
        if (etMainSearch != null) {
            etMainSearch.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    String query = etMainSearch.getText().toString().trim();
                    Intent intent = new Intent(MainActivity.this, InboxActivity.class);
                    intent.putExtra("search_query", query);
                    startActivity(intent);
                    return true;
                }
                return false;
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void updateUI() {
        String name = UserPreferences.getUserName(this);
        String avatar = UserPreferences.getUserAvatar(this);

        if (tvHeaderGreeting != null) {
            tvHeaderGreeting.setText("Hello, " + name + "!");
        }
        if (tvHeaderAvatar != null) {
            tvHeaderAvatar.setText(avatar);
        }
        if (tvPersonalSpaceAvatar != null) {
            tvPersonalSpaceAvatar.setText(avatar);
        }

        // Check unread notifications
        int unreadNotifs = DatabaseHelper.getInstance(this).getUnreadNotificationCount();
        if (tvNotifBadge != null) {
            if (unreadNotifs > 0) {
                tvNotifBadge.setVisibility(View.VISIBLE);
                tvNotifBadge.setText(String.valueOf(unreadNotifs));
            } else {
                tvNotifBadge.setVisibility(View.GONE);
            }
        }
    }

    private void showP2PDialog() {
        View dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_p2p_connect, null);
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

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
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
                android.widget.Toast.makeText(this, "Please enter your friend's IP", android.widget.Toast.LENGTH_SHORT).show();
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
                android.widget.Toast.makeText(this, "Connected! Tap any chat in Inbox to talk live with your friend.", android.widget.Toast.LENGTH_LONG).show();
            }
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
