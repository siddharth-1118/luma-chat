package com.example.lumachat;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;

public class InboxActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        setChat(findViewById(R.id.chat1),
                "A", "Ananya Sharma", "Hey! How are you?",
                "10:42 AM", "2");

        setChat(findViewById(R.id.chat2),
                "R", "Rahul", "See you tomorrow!",
                "9:30 AM", "1");

        setChat(findViewById(R.id.chat3),
                "S", "Sravani", "That was so fun!",
                "Yesterday", "");

        setChat(findViewById(R.id.chat4),
                "P", "Priya", "Sent you the photos",
                "Yesterday", "3");

    }

    private void setChat(View item, String avatar, String name,
                         String message, String time, String unread) {
        ((TextView) item.findViewById(R.id.tvAvatar)).setText(avatar);
        ((TextView) item.findViewById(R.id.tvContactName)).setText(name);
        ((TextView) item.findViewById(R.id.tvLastMessage)).setText(message);
        ((TextView) item.findViewById(R.id.tvTime)).setText(time);

        TextView count = item.findViewById(R.id.tvUnreadCount);
        count.setText(unread);
        count.setVisibility(unread.isEmpty() ? View.GONE : View.VISIBLE);
        item.setOnClickListener(v -> {
            Intent intent = new Intent(InboxActivity.this, ChatActivity.class);
            intent.putExtra("name", name);
            intent.putExtra("avatar", avatar);
            startActivity(intent);
        });

    }
}