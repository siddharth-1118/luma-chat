package com.example.lumachat;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lumachat.data.DatabaseHelper;
import com.example.lumachat.model.CallItem;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CallActivity extends AppCompatActivity {

    private String contactName = "Contact";
    private String contactAvatar = "C";
    private TextView tvCallTimer;
    private TextView iconMute, lblMute, iconSpeaker, lblSpeaker;

    private boolean isMuted = false;
    private boolean isSpeakerOn = false;
    private boolean isConnected = false;

    private int secondsElapsed = 0;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        contactName = getIntent().getStringExtra("name");
        contactAvatar = getIntent().getStringExtra("avatar");
        if (contactName == null) contactName = "Friend";
        if (contactAvatar == null) contactAvatar = "F";

        TextView tvAvatar = findViewById(R.id.tvCallAvatar);
        TextView tvName = findViewById(R.id.tvCallName);
        tvCallTimer = findViewById(R.id.tvCallTimer);

        tvAvatar.setText(contactAvatar);
        tvName.setText(contactName);

        iconMute = findViewById(R.id.iconMute);
        lblMute = findViewById(R.id.lblMute);
        iconSpeaker = findViewById(R.id.iconSpeaker);
        lblSpeaker = findViewById(R.id.lblSpeaker);

        findViewById(R.id.btnMute).setOnClickListener(v -> toggleMute());
        findViewById(R.id.btnSpeaker).setOnClickListener(v -> toggleSpeaker());
        findViewById(R.id.btnKeypad).setOnClickListener(v ->
                Toast.makeText(this, "Keypad tone enabled", Toast.LENGTH_SHORT).show());

        findViewById(R.id.btnEndCall).setOnClickListener(v -> endCall());

        startCallSequence();
    }

    private void startCallSequence() {
        tvCallTimer.setText("Connecting...");

        timerHandler.postDelayed(() -> {
            if (!isFinishing()) {
                tvCallTimer.setText("Ringing...");
            }
        }, 1200);

        timerHandler.postDelayed(() -> {
            if (!isFinishing()) {
                isConnected = true;
                startTimer();
            }
        }, 2800);
    }

    private void startTimer() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                secondsElapsed++;
                int mins = secondsElapsed / 60;
                int secs = secondsElapsed % 60;
                tvCallTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", mins, secs));
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void toggleMute() {
        isMuted = !isMuted;
        lblMute.setText(isMuted ? "Unmute" : "Mute");
        iconMute.setAlpha(isMuted ? 0.5f : 1.0f);
        Toast.makeText(this, isMuted ? "Microphone muted" : "Microphone active", Toast.LENGTH_SHORT).show();
    }

    private void toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn;
        lblSpeaker.setText(isSpeakerOn ? "Earpiece" : "Speaker");
        iconSpeaker.setAlpha(isSpeakerOn ? 0.6f : 1.0f);
        Toast.makeText(this, isSpeakerOn ? "Speaker ON" : "Speaker OFF", Toast.LENGTH_SHORT).show();
    }

    private void endCall() {
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        timerHandler.removeCallbacksAndMessages(null);

        // Save Call Record
        String durationStr = String.format(Locale.getDefault(), "%02d:%02d", secondsElapsed / 60, secondsElapsed % 60);
        String timeStr = "Outgoing • " + new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());

        DatabaseHelper.getInstance(this).insertCall(new CallItem(
                0, contactName, contactAvatar, "Outgoing", timeStr, System.currentTimeMillis(), durationStr
        ));

        Toast.makeText(this, "Call ended • " + durationStr, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        timerHandler.removeCallbacksAndMessages(null);
    }
}
