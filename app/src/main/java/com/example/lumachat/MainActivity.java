package com.example.lumachat;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
            Intent intent = new Intent(MainActivity.this,
                    NotificationsActivity.class);
            startActivity(intent);
        });
    }
}