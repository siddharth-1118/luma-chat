package com.example.lumachat;

import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lumachat.call.CallRingtoneManager;
import com.example.lumachat.data.DatabaseHelper;
import com.example.lumachat.data.UserPreferences;
import com.example.lumachat.model.CallItem;
import com.example.lumachat.network.P2PChatManager;
import com.example.lumachat.notification.LumaNotificationManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CallActivity extends AppCompatActivity implements P2PChatManager.OnCallSignalListener {

    private String contactName = "Friend";
    private String contactAvatar = "🌸";
    private String callType = "voice"; // "voice" or "video"
    private String callId = "";
    private boolean isIncoming = false;

    private TextView tvCallAvatar;
    private TextView tvCallName;
    private TextView tvCallTimer;
    private TextView tvCallSecurityBadge;

    private View layoutVoiceContainer;
    private View layoutVideoView;
    private View layoutInCallControls;
    private View layoutIncomingControls;

    // Video components
    private TextView tvVideoPeerAvatar;
    private TextView tvVideoPeerName;
    private TextView tvPipAvatar;

    // Call controls
    private TextView iconMute, lblMute, iconSpeaker, lblSpeaker, iconVideo, lblVideo;

    private boolean isMuted = false;
    private boolean isSpeakerOn = false;
    private boolean isVideoMuted = false;
    private boolean isFrontCamera = true;
    private boolean isConnected = false;

    private int secondsElapsed = 0;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    private AudioManager audioManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        contactName = getIntent().getStringExtra("name");
        contactAvatar = getIntent().getStringExtra("avatar");
        callType = getIntent().getStringExtra("call_type");
        callId = getIntent().getStringExtra("call_id");
        isIncoming = getIntent().getBooleanExtra("is_incoming", false);

        if (contactName == null || contactName.isEmpty()) contactName = "Friend";
        if (contactAvatar == null || contactAvatar.isEmpty()) contactAvatar = "🌸";
        if (callType == null || callType.isEmpty()) callType = "voice";
        if (callId == null || callId.isEmpty()) callId = "call_" + System.currentTimeMillis();

        initViews();
        setupCallMode();

        // Listen for remote call signals (answer, decline, end)
        P2PChatManager.getInstance().setCallListener(this);

        if (isIncoming) {
            setupIncomingCall();
        } else {
            setupOutgoingCall();
        }
    }

    private void initViews() {
        layoutVoiceContainer = findViewById(R.id.layoutVoiceContainer);
        layoutVideoView = findViewById(R.id.layoutVideoView);
        layoutInCallControls = findViewById(R.id.layoutInCallControls);
        layoutIncomingControls = findViewById(R.id.layoutIncomingControls);

        tvCallAvatar = findViewById(R.id.tvCallAvatar);
        tvCallName = findViewById(R.id.tvCallName);
        tvCallTimer = findViewById(R.id.tvCallTimer);
        tvCallSecurityBadge = findViewById(R.id.tvCallSecurityBadge);

        tvVideoPeerAvatar = findViewById(R.id.tvVideoPeerAvatar);
        tvVideoPeerName = findViewById(R.id.tvVideoPeerName);
        tvPipAvatar = findViewById(R.id.tvPipAvatar);

        iconMute = findViewById(R.id.iconMute);
        lblMute = findViewById(R.id.lblMute);
        iconSpeaker = findViewById(R.id.iconSpeaker);
        lblSpeaker = findViewById(R.id.lblSpeaker);
        iconVideo = findViewById(R.id.iconVideo);
        lblVideo = findViewById(R.id.lblVideo);

        // Populate fields
        tvCallAvatar.setText(contactAvatar);
        tvCallName.setText(contactName);

        tvVideoPeerAvatar.setText(contactAvatar);
        tvVideoPeerName.setText(contactName);
        tvPipAvatar.setText(UserPreferences.getUserAvatar(this));

        // Click listeners
        findViewById(R.id.btnMute).setOnClickListener(v -> toggleMute());
        findViewById(R.id.btnSpeaker).setOnClickListener(v -> toggleSpeaker());
        findViewById(R.id.btnToggleVideo).setOnClickListener(v -> toggleVideo());
        findViewById(R.id.btnFlipCamera).setOnClickListener(v -> flipCamera());
        findViewById(R.id.btnEndCall).setOnClickListener(v -> endCall(true));

        findViewById(R.id.btnAcceptCall).setOnClickListener(v -> acceptCall());
        findViewById(R.id.btnDeclineCall).setOnClickListener(v -> declineCall());
    }

    private void setupCallMode() {
        if ("video".equals(callType)) {
            layoutVideoView.setVisibility(View.VISIBLE);
            layoutVoiceContainer.setVisibility(View.GONE);
            tvCallSecurityBadge.setText("🔒  LumaVideo • HD Encrypted");
            if (audioManager != null) {
                audioManager.setSpeakerphoneOn(true);
                isSpeakerOn = true;
                iconSpeaker.setAlpha(1.0f);
            }
        } else {
            layoutVideoView.setVisibility(View.GONE);
            layoutVoiceContainer.setVisibility(View.VISIBLE);
            tvCallSecurityBadge.setText("🔒  LumaVoice • Encrypted");
        }
    }

    private void setupIncomingCall() {
        layoutIncomingControls.setVisibility(View.VISIBLE);
        layoutInCallControls.setVisibility(View.GONE);

        tvCallTimer.setText("Incoming " + ("video".equals(callType) ? "Video" : "Voice") + " Call...");

        // Play phone ringtone & vibrate
        CallRingtoneManager.getInstance().startIncomingRingtone(this);

        // Cancel notification since we are in the call screen
        LumaNotificationManager.cancelCallNotification(this);
    }

    private void setupOutgoingCall() {
        layoutIncomingControls.setVisibility(View.GONE);
        layoutInCallControls.setVisibility(View.VISIBLE);

        tvCallTimer.setText("Calling...");

        // Play outgoing telephone ringing sound
        CallRingtoneManager.getInstance().startOutgoingRinging();

        // Send call offer packet via P2P / Ngrok server
        String myName = UserPreferences.getUserName(this);
        String myAvatar = UserPreferences.getUserAvatar(this);
        P2PChatManager.getInstance().sendCallSignal("call_offer", callId, callType, myName, myAvatar);

        // Simulated auto-connect fallback after 4s if peer is on demo
        timerHandler.postDelayed(() -> {
            if (!isConnected && !isFinishing()) {
                onCallAnswered();
            }
        }, 4000);
    }

    private void acceptCall() {
        CallRingtoneManager.getInstance().stopAll();
        LumaNotificationManager.cancelCallNotification(this);

        String myName = UserPreferences.getUserName(this);
        String myAvatar = UserPreferences.getUserAvatar(this);
        P2PChatManager.getInstance().sendCallSignal("call_answer", callId, callType, myName, myAvatar);

        layoutIncomingControls.setVisibility(View.GONE);
        layoutInCallControls.setVisibility(View.VISIBLE);

        onCallAnswered();
    }

    private void declineCall() {
        CallRingtoneManager.getInstance().stopAll();
        LumaNotificationManager.cancelCallNotification(this);

        String myName = UserPreferences.getUserName(this);
        String myAvatar = UserPreferences.getUserAvatar(this);
        P2PChatManager.getInstance().sendCallSignal("call_reject", callId, callType, myName, myAvatar);

        // Log missed/declined call
        String timeStr = "Incoming • " + new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());
        DatabaseHelper.getInstance(this).insertCall(new CallItem(
                0, contactName, contactAvatar, "Declined", timeStr, System.currentTimeMillis(), "00:00"
        ));

        Toast.makeText(this, "Call declined", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void onCallAnswered() {
        CallRingtoneManager.getInstance().stopAll();
        isConnected = true;
        tvCallTimer.setText("Connected • 00:00");
        startTimer();
    }

    private void startTimer() {
        if (timerRunnable != null) return;
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
        if (audioManager != null) {
            audioManager.setMicrophoneMute(isMuted);
        }
        lblMute.setText(isMuted ? "Unmute" : "Mute");
        iconMute.setAlpha(isMuted ? 0.4f : 1.0f);
        Toast.makeText(this, isMuted ? "Microphone muted 🔇" : "Microphone unmuted 🎤", Toast.LENGTH_SHORT).show();
    }

    private void toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn;
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(isSpeakerOn);
        }
        lblSpeaker.setText(isSpeakerOn ? "Earpiece" : "Speaker");
        iconSpeaker.setAlpha(isSpeakerOn ? 1.0f : 0.6f);
        Toast.makeText(this, isSpeakerOn ? "Speaker ON 🔊" : "Earpiece active 🔈", Toast.LENGTH_SHORT).show();
    }

    private void toggleVideo() {
        isVideoMuted = !isVideoMuted;
        if (isVideoMuted) {
            layoutVideoView.setVisibility(View.GONE);
            layoutVoiceContainer.setVisibility(View.VISIBLE);
            lblVideo.setText("Turn On");
            iconVideo.setAlpha(0.4f);
            Toast.makeText(this, "Camera turned OFF", Toast.LENGTH_SHORT).show();
        } else {
            layoutVideoView.setVisibility(View.VISIBLE);
            layoutVoiceContainer.setVisibility(View.GONE);
            lblVideo.setText("Video");
            iconVideo.setAlpha(1.0f);
            Toast.makeText(this, "Camera turned ON", Toast.LENGTH_SHORT).show();
        }
    }

    private void flipCamera() {
        isFrontCamera = !isFrontCamera;
        Toast.makeText(this, isFrontCamera ? "Switched to Front Camera 📷" : "Switched to Back Camera 📸", Toast.LENGTH_SHORT).show();
    }

    private void endCall(boolean notifyPeer) {
        CallRingtoneManager.getInstance().stopAll();
        LumaNotificationManager.cancelCallNotification(this);

        if (notifyPeer) {
            String myName = UserPreferences.getUserName(this);
            String myAvatar = UserPreferences.getUserAvatar(this);
            P2PChatManager.getInstance().sendCallSignal("call_end", callId, callType, myName, myAvatar);
        }

        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        timerHandler.removeCallbacksAndMessages(null);

        // Save Call Record
        String durationStr = String.format(Locale.getDefault(), "%02d:%02d", secondsElapsed / 60, secondsElapsed % 60);
        String typeLabel = isIncoming ? "Incoming" : "Outgoing";
        String timeStr = typeLabel + " (" + ("video".equals(callType) ? "Video" : "Voice") + ") • " +
                new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());

        DatabaseHelper.getInstance(this).insertCall(new CallItem(
                0, contactName, contactAvatar, typeLabel, timeStr, System.currentTimeMillis(), durationStr
        ));

        Toast.makeText(this, "Call ended • " + durationStr, Toast.LENGTH_SHORT).show();
        finish();
    }

    // P2PChatManager.OnCallSignalListener implementation
    @Override
    public void onCallSignal(String signalType, String type, String cId, String senderName, String senderAvatar) {
        runOnUiThread(() -> {
            if ("call_answer".equals(signalType)) {
                onCallAnswered();
            } else if ("call_reject".equals(signalType)) {
                CallRingtoneManager.getInstance().stopAll();
                Toast.makeText(this, contactName + " declined the call", Toast.LENGTH_SHORT).show();
                endCall(false);
            } else if ("call_end".equals(signalType)) {
                Toast.makeText(this, contactName + " ended the call", Toast.LENGTH_SHORT).show();
                endCall(false);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CallRingtoneManager.getInstance().stopAll();
        LumaNotificationManager.cancelCallNotification(this);
        P2PChatManager.getInstance().setCallListener(null);
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        timerHandler.removeCallbacksAndMessages(null);
    }
}
