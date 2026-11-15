package com.example.lumachat;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import androidx.appcompat.app.AppCompatActivity;

public class ChatActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        String name = getIntent().getStringExtra("name");
        String avatar = getIntent().getStringExtra("avatar");

        TextView chatName = findViewById(R.id.tvChatName);
        TextView chatAvatar = findViewById(R.id.tvChatAvatar);

        if (name != null) chatName.setText(name);
        if (avatar != null) chatAvatar.setText(avatar);

        findViewById(R.id.btnBackChat).setOnClickListener(v -> finish());

        EditText input = findViewById(R.id.etMessage);
        TextView send = findViewById(R.id.btnSend);
        LinearLayout container = findViewById(R.id.messageContainer);
        ScrollView scroll = findViewById(R.id.chatScroll);

        send.setOnClickListener(v -> {
            String message = input.getText().toString().trim();

            if (!message.isEmpty()) {
                TextView bubble = new TextView(this);
                bubble.setText(message);
                bubble.setTextSize(15);
                bubble.setTextColor(Color.rgb(25, 21, 47));
                bubble.setPadding(16, 12, 16, 12);

                GradientDrawable shape = new GradientDrawable();
                shape.setColor(Color.rgb(205, 184, 246));
                shape.setCornerRadius(30);
                bubble.setBackground(shape);

                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT);

                params.gravity = Gravity.END;
                params.bottomMargin = 14;
                bubble.setLayoutParams(params);

                container.addView(bubble);
                input.setText("");

                scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            }
        });
    }
}