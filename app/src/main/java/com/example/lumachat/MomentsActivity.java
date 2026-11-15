package com.example.lumachat;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.lumachat.data.DatabaseHelper;
import com.example.lumachat.data.UserPreferences;
import com.example.lumachat.model.Moment;

import java.util.List;

public class MomentsActivity extends AppCompatActivity {

    private LinearLayout storiesReelContainer;
    private LinearLayout momentsFeedContainer;
    private TextView tvMyStatusAvatar;
    private TextView tvMyStatusSubtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moments);

        storiesReelContainer = findViewById(R.id.storiesReelContainer);
        momentsFeedContainer = findViewById(R.id.momentsFeedContainer);
        tvMyStatusAvatar = findViewById(R.id.tvMyStatusAvatar);
        tvMyStatusSubtitle = findViewById(R.id.tvMyStatusSubtitle);

        findViewById(R.id.btnBackMoments).setOnClickListener(v -> finish());
        findViewById(R.id.btnAddMoment).setOnClickListener(v -> showNewMomentDialog());
        findViewById(R.id.btnMyStory).setOnClickListener(v -> showNewMomentDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMoments();
    }

    private void loadMoments() {
        String myAvatar = UserPreferences.getUserAvatar(this);
        tvMyStatusAvatar.setText(myAvatar);

        storiesReelContainer.removeAllViews();
        momentsFeedContainer.removeAllViews();

        List<Moment> moments = DatabaseHelper.getInstance(this).getMoments();

        // 1. Stories Reel
        for (Moment moment : moments) {
            LinearLayout storyItem = new LinearLayout(this);
            storyItem.setOrientation(LinearLayout.VERTICAL);
            storyItem.setGravity(Gravity.CENTER);
            storyItem.setLayoutParams(new LinearLayout.LayoutParams(160, LinearLayout.LayoutParams.MATCH_PARENT));

            TextView tvAvatar = new TextView(this);
            tvAvatar.setText(moment.getAuthorAvatar());
            tvAvatar.setTextSize(22);
            tvAvatar.setTypeface(null, Typeface.BOLD);
            tvAvatar.setTextColor(Color.parseColor("#19152F"));
            tvAvatar.setGravity(Gravity.CENTER);
            tvAvatar.setBackgroundResource(R.drawable.button_background);
            tvAvatar.setLayoutParams(new LinearLayout.LayoutParams(120, 120));
            storyItem.addView(tvAvatar);

            TextView tvName = new TextView(this);
            tvName.setText(moment.getAuthorName());
            tvName.setTextSize(11);
            tvName.setTextColor(Color.parseColor("#19152F"));
            tvName.setPadding(0, 10, 0, 0);
            tvName.setMaxLines(1);
            storyItem.addView(tvName);

            storyItem.setOnClickListener(v -> {
                Intent intent = new Intent(MomentsActivity.this, StoryViewerActivity.class);
                intent.putExtra("author", moment.getAuthorName());
                intent.putExtra("avatar", moment.getAuthorAvatar());
                intent.putExtra("emoji", moment.getEmoji());
                intent.putExtra("caption", moment.getCaption());
                intent.putExtra("timeAgo", moment.getTimeAgo());
                startActivity(intent);
            });

            storiesReelContainer.addView(storyItem);
        }

        // 2. Feed Cards
        for (Moment moment : moments) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(32, 28, 32, 24);
            card.setBackgroundResource(R.drawable.card_background);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.bottomMargin = 24;
            card.setLayoutParams(cardParams);

            // Large emoji
            TextView tvEmoji = new TextView(this);
            tvEmoji.setText(moment.getEmoji());
            tvEmoji.setTextSize(48);
            tvEmoji.setGravity(Gravity.CENTER);
            card.addView(tvEmoji);

            // Caption
            TextView tvCaption = new TextView(this);
            tvCaption.setText(moment.getCaption());
            tvCaption.setTextSize(14);
            tvCaption.setTypeface(null, Typeface.BOLD);
            tvCaption.setTextColor(Color.parseColor("#19152F"));
            tvCaption.setGravity(Gravity.CENTER);
            tvCaption.setPadding(0, 14, 0, 0);
            card.addView(tvCaption);

            // Author and time
            TextView tvMeta = new TextView(this);
            tvMeta.setText(moment.getAuthorName() + " • " + moment.getTimeAgo());
            tvMeta.setTextSize(11);
            tvMeta.setTextColor(Color.parseColor("#77718A"));
            tvMeta.setPadding(0, 8, 0, 0);
            card.addView(tvMeta);

            // Interaction Bar
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER);
            actions.setPadding(0, 16, 0, 0);

            TextView btnLike = new TextView(this);
            btnLike.setText((moment.isLiked() ? "❤️ " : "♡ ") + moment.getLikesCount() + " likes");
            btnLike.setTextSize(12);
            btnLike.setTextColor(Color.parseColor("#F4B9A9"));
            btnLike.setTypeface(null, Typeface.BOLD);
            btnLike.setPadding(16, 8, 24, 8);
            btnLike.setOnClickListener(v -> {
                DatabaseHelper.getInstance(this).toggleMomentLike(moment.getId());
                loadMoments();
            });
            actions.addView(btnLike);

            TextView btnComment = new TextView(this);
            btnComment.setText("💬 Say hi");
            btnComment.setTextSize(12);
            btnComment.setTextColor(Color.parseColor("#77718A"));
            btnComment.setPadding(16, 8, 16, 8);
            btnComment.setOnClickListener(v -> {
                Intent chatIntent = new Intent(MomentsActivity.this, ChatActivity.class);
                chatIntent.putExtra("name", moment.getAuthorName());
                chatIntent.putExtra("avatar", moment.getAuthorAvatar());
                startActivity(chatIntent);
            });
            actions.addView(btnComment);

            card.addView(actions);
            momentsFeedContainer.addView(card);
        }
    }

    private void showNewMomentDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_new_moment, null);
        EditText etEmoji = dialogView.findViewById(R.id.etMomentEmoji);
        EditText etCaption = dialogView.findViewById(R.id.etMomentCaption);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btnCancelMoment).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnSubmitMoment).setOnClickListener(v -> {
            String caption = etCaption.getText().toString().trim();
            if (caption.isEmpty()) {
                Toast.makeText(this, "Please write a caption", Toast.LENGTH_SHORT).show();
                return;
            }

            String emoji = etEmoji.getText().toString().trim();
            if (emoji.isEmpty()) emoji = "✨";

            String author = UserPreferences.getUserName(this);
            String avatar = UserPreferences.getUserAvatar(this);

            Moment newMoment = new Moment(
                    0, author, avatar, emoji, caption,
                    "Just now", System.currentTimeMillis(), true, 1, true
            );

            DatabaseHelper.getInstance(this).insertMoment(newMoment);
            dialog.dismiss();
            loadMoments();
            Toast.makeText(this, "Moment shared successfully! ✨", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }
}
