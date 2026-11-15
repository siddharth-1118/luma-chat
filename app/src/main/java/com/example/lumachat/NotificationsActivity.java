package com.example.lumachat;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.lumachat.data.DatabaseHelper;
import com.example.lumachat.model.NotificationItem;

import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private LinearLayout notificationList;
    private TextView tvEmptyNotifications;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        notificationList = findViewById(R.id.notificationList);
        tvEmptyNotifications = findViewById(R.id.tvEmptyNotifications);

        findViewById(R.id.btnBackNotifications).setOnClickListener(v -> finish());

        findViewById(R.id.btnMarkRead).setOnClickListener(v -> {
            DatabaseHelper.getInstance(this).markNotificationsRead();
            loadNotifications();
            Toast.makeText(this, "All notifications marked as read", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnClearNotifications).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Clear Notifications")
                    .setMessage("Are you sure you want to clear all notifications?")
                    .setPositiveButton("Clear", (dialog, which) -> {
                        DatabaseHelper.getInstance(this).clearNotifications();
                        loadNotifications();
                        Toast.makeText(this, "Notifications cleared", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNotifications();
    }

    private void loadNotifications() {
        notificationList.removeAllViews();
        List<NotificationItem> list = DatabaseHelper.getInstance(this).getNotifications();

        if (list.isEmpty()) {
            tvEmptyNotifications.setVisibility(View.VISIBLE);
        } else {
            tvEmptyNotifications.setVisibility(View.GONE);

            for (NotificationItem notif : list) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(30, 26, 30, 26);
                row.setBackgroundResource(R.drawable.card_background);

                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                rowParams.bottomMargin = 20;
                row.setLayoutParams(rowParams);

                // Avatar
                TextView tvAvatar = new TextView(this);
                tvAvatar.setText(notif.getSenderAvatar());
                tvAvatar.setTextSize(20);
                tvAvatar.setTypeface(null, Typeface.BOLD);
                tvAvatar.setTextColor(Color.parseColor("#19152F"));
                tvAvatar.setGravity(Gravity.CENTER);
                tvAvatar.setBackgroundResource(R.drawable.input_field);
                tvAvatar.setLayoutParams(new LinearLayout.LayoutParams(110, 110));
                row.addView(tvAvatar);

                // Info
                LinearLayout info = new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);
                info.setPadding(26, 0, 0, 0);
                LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
                );
                info.setLayoutParams(infoParams);

                TextView tvSender = new TextView(this);
                tvSender.setText(notif.getSenderName());
                tvSender.setTextSize(15);
                tvSender.setTypeface(null, Typeface.BOLD);
                tvSender.setTextColor(Color.parseColor("#19152F"));
                info.addView(tvSender);

                TextView tvContent = new TextView(this);
                tvContent.setText(notif.getContent());
                tvContent.setTextSize(12);
                tvContent.setTextColor(Color.parseColor("#77718A"));
                tvContent.setPadding(0, 6, 0, 0);
                info.addView(tvContent);

                TextView tvTime = new TextView(this);
                tvTime.setText(notif.getTimeAgo());
                tvTime.setTextSize(11);
                tvTime.setTextColor(Color.parseColor("#77718A"));
                tvTime.setPadding(0, 6, 0, 0);
                info.addView(tvTime);

                row.addView(info);

                // Unread dot indicator
                if (!notif.isRead()) {
                    TextView tvDot = new TextView(this);
                    tvDot.setBackgroundResource(R.drawable.badge_bg);
                    tvDot.setLayoutParams(new LinearLayout.LayoutParams(24, 24));
                    row.addView(tvDot);
                }

                // Click to view relevant item
                row.setOnClickListener(v -> {
                    String targetId = notif.getTargetConversationId();
                    if (targetId != null && !targetId.isEmpty()) {
                        Intent intent = new Intent(NotificationsActivity.this, ChatActivity.class);
                        intent.putExtra("id", targetId);
                        intent.putExtra("name", notif.getSenderName());
                        intent.putExtra("avatar", notif.getSenderAvatar());
                        intent.putExtra("is_group", targetId.startsWith("circle_"));
                        startActivity(intent);
                    }
                });

                notificationList.addView(row);
            }
        }
    }
}
