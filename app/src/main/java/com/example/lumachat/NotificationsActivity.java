package com.example.lumachat;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class NotificationsActivity extends AppCompatActivity {

    private LinearLayout notificationList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        notificationList = findViewById(R.id.notificationList);

        findViewById(R.id.btnBackNotifications).setOnClickListener(v -> finish());

        findViewById(R.id.btnMarkRead).setOnClickListener(v -> {
            Toast.makeText(this, "All notifications marked as read",
                    Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnClearNotifications).setOnClickListener(v -> {
            notificationList.removeAllViews();
            Toast.makeText(this, "Notifications cleared",
                    Toast.LENGTH_SHORT).show();
        });
    }
}