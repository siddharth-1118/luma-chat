package com.example.lumachat;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
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
import com.example.lumachat.model.Conversation;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CirclesActivity extends AppCompatActivity {

    private LinearLayout groupsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_circles);

        groupsContainer = findViewById(R.id.groupsContainer);

        findViewById(R.id.btnBackCircles).setOnClickListener(v -> finish());

        findViewById(R.id.btnCreateGroup).setOnClickListener(v -> showCreateGroupDialog());
        findViewById(R.id.btnAddCircle).setOnClickListener(v -> showCreateGroupDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadGroups();
    }

    private void loadGroups() {
        groupsContainer.removeAllViews();
        List<Conversation> groups = DatabaseHelper.getInstance(this).getConversations(true);

        for (Conversation group : groups) {
            // Build dynamic row layout inside card
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(36, 32, 36, 32);
            row.setBackgroundResource(R.drawable.card_background);

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            rowParams.bottomMargin = 24;
            row.setLayoutParams(rowParams);

            // Icon
            TextView tvIcon = new TextView(this);
            tvIcon.setText(group.getAvatar());
            tvIcon.setTextSize(26);
            tvIcon.setGravity(android.view.Gravity.CENTER);
            tvIcon.setBackgroundResource(R.drawable.input_field);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(130, 130);
            tvIcon.setLayoutParams(iconParams);
            row.addView(tvIcon);

            // Details
            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(30, 0, 0, 0);
            LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
            );
            details.setLayoutParams(detailsParams);

            TextView tvName = new TextView(this);
            tvName.setText(group.getName());
            tvName.setTextSize(16);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            tvName.setTextColor(Color.parseColor("#19152F"));
            details.addView(tvName);

            TextView tvLastMsg = new TextView(this);
            tvLastMsg.setText(group.getLastMessage());
            tvLastMsg.setTextSize(13);
            tvLastMsg.setTextColor(Color.parseColor("#77718A"));
            tvLastMsg.setPadding(0, 8, 0, 0);
            tvLastMsg.setMaxLines(1);
            tvLastMsg.setEllipsize(android.text.TextUtils.TruncateAt.END);
            details.addView(tvLastMsg);

            TextView tvMembers = new TextView(this);
            String membersStr = (group.getMembers() != null && !group.getMembers().isEmpty())
                    ? group.getMembers() : "Active members";
            tvMembers.setText(membersStr);
            tvMembers.setTextSize(11);
            tvMembers.setTextColor(Color.parseColor("#77718A"));
            tvMembers.setPadding(0, 6, 0, 0);
            tvMembers.setMaxLines(1);
            details.addView(tvMembers);

            row.addView(details);

            // Online indicator dot
            TextView tvDot = new TextView(this);
            tvDot.setText("●");
            tvDot.setTextSize(13);
            tvDot.setTextColor(Color.parseColor("#58B88A"));
            row.addView(tvDot);

            row.setOnClickListener(v -> {
                Intent intent = new Intent(CirclesActivity.this, ChatActivity.class);
                intent.putExtra("id", group.getId());
                intent.putExtra("name", group.getName());
                intent.putExtra("avatar", group.getAvatar());
                intent.putExtra("is_group", true);
                intent.putExtra("status", group.getOnlineStatus());
                startActivity(intent);
            });

            groupsContainer.addView(row);
        }
    }

    private void showCreateGroupDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_new_group, null);
        EditText etName = dialogView.findViewById(R.id.etGroupName);
        EditText etIcon = dialogView.findViewById(R.id.etGroupIcon);
        EditText etMembers = dialogView.findViewById(R.id.etGroupMembers);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btnCancelGroup).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnSubmitGroup).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a circle name", Toast.LENGTH_SHORT).show();
                return;
            }

            String icon = etIcon.getText().toString().trim();
            if (icon.isEmpty()) icon = "🌟";

            String members = etMembers.getText().toString().trim();
            if (members.isEmpty()) members = "You, Ananya, Rahul";

            String convId = "circle_" + name.toLowerCase().replaceAll("[^a-zA-Z0-9]", "_");
            String time = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());

            Conversation newGroup = new Conversation(
                    convId, name, icon, true, members,
                    "Group created by " + UserPreferences.getUserName(this),
                    time, 0, false, "Group • Active", System.currentTimeMillis()
            );

            DatabaseHelper.getInstance(this).insertConversation(newGroup);
            dialog.dismiss();
            loadGroups();
            Toast.makeText(this, "Circle '" + name + "' created!", Toast.LENGTH_SHORT).show();

            // Open chat immediately
            Intent intent = new Intent(CirclesActivity.this, ChatActivity.class);
            intent.putExtra("id", convId);
            intent.putExtra("name", name);
            intent.putExtra("avatar", icon);
            intent.putExtra("is_group", true);
            intent.putExtra("status", "Group • Active");
            startActivity(intent);
        });

        dialog.show();
    }
}
