package com.example.lumachat;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lumachat.data.DatabaseHelper;
import com.example.lumachat.data.UserPreferences;
import com.example.lumachat.model.Message;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StoryViewerActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private Handler progressHandler = new Handler(Looper.getMainLooper());
    private int currentProgress = 0;
    private boolean isPaused = false;

    private String authorName;
    private String authorAvatar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_story_viewer);

        authorName = getIntent().getStringExtra("author");
        authorAvatar = getIntent().getStringExtra("avatar");
        String emoji = getIntent().getStringExtra("emoji");
        String caption = getIntent().getStringExtra("caption");
        String timeAgo = getIntent().getStringExtra("timeAgo");

        if (authorName == null) authorName = "Friend";
        if (authorAvatar == null) authorAvatar = "F";
        if (emoji == null) emoji = "🌸";
        if (caption == null) caption = "Sharing a sweet moment ✨";
        if (timeAgo == null) timeAgo = "Recently";

        TextView tvAvatar = findViewById(R.id.tvStoryAvatar);
        TextView tvAuthor = findViewById(R.id.tvStoryAuthor);
        TextView tvTime = findViewById(R.id.tvStoryTime);
        TextView tvEmoji = findViewById(R.id.tvStoryEmoji);
        TextView tvCaption = findViewById(R.id.tvStoryCaption);
        progressBar = findViewById(R.id.storyProgressBar);

        tvAvatar.setText(authorAvatar);
        tvAuthor.setText(authorName);
        tvTime.setText(timeAgo);
        tvEmoji.setText(emoji);
        tvCaption.setText(caption);

        findViewById(R.id.btnCloseStory).setOnClickListener(v -> finish());

        EditText etReply = findViewById(R.id.etStoryReply);
        findViewById(R.id.btnSendStoryReply).setOnClickListener(v -> {
            String text = etReply.getText().toString().trim();
            if (!text.isEmpty()) {
                sendReplyToFriend(text);
            }
        });

        startProgressTimer();
    }

    private void startProgressTimer() {
        progressHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isPaused && !isFinishing()) {
                    currentProgress += 2;
                    progressBar.setProgress(currentProgress);
                    if (currentProgress >= 100) {
                        finish();
                        return;
                    }
                }
                progressHandler.postDelayed(this, 100);
            }
        }, 100);
    }

    private void sendReplyToFriend(String reply) {
        String myName = UserPreferences.getUserName(this);
        String myAvatar = UserPreferences.getUserAvatar(this);

        // Find or create conversation for author
        String convId = "chat_" + authorName.toLowerCase().replace(" ", "_");
        String timeStr = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());

        DatabaseHelper.getInstance(this).insertMessage(new Message(
                0, convId, myName, myAvatar,
                "Replied to story: " + reply,
                System.currentTimeMillis(), timeStr, true, "sent"
        ));

        Toast.makeText(this, "Reply sent to " + authorName + "!", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        progressHandler.removeCallbacksAndMessages(null);
    }
}
