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
}
