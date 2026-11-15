package com.example.lumachat.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.lumachat.CallActivity;
import com.example.lumachat.ChatActivity;
import com.example.lumachat.data.DatabaseHelper;
import com.example.lumachat.data.UserPreferences;
import com.example.lumachat.model.NotificationItem;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LumaNotificationManager {

    public static final String CHANNEL_MESSAGES_ID = "luma_messages_v2";
    public static final String CHANNEL_CALLS_ID = "luma_calls_v2";
    private static final int CALL_NOTIFICATION_ID = 99991;

    private static boolean channelsInitialized = false;

    public static void initChannels(Context context) {
        if (channelsInitialized || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        // 1. Messages Channel (High Importance, Sound + Vibrate)
        NotificationChannel msgChannel = new NotificationChannel(
                CHANNEL_MESSAGES_ID,
                "Luma Messages",
                NotificationManager.IMPORTANCE_HIGH
        );
        msgChannel.setDescription("Notifications for incoming messages");
        msgChannel.enableVibration(true);
        msgChannel.setVibrationPattern(new long[]{0, 250, 150, 250});
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .build();
        msgChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes);
        nm.createNotificationChannel(msgChannel);

        // 2. Calls Channel (Max Importance, Ringing)
        NotificationChannel callChannel = new NotificationChannel(
                CHANNEL_CALLS_ID,
                "Luma Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
        );
        callChannel.setDescription("Alerts for incoming voice and video calls");
        callChannel.enableVibration(true);
        callChannel.setVibrationPattern(new long[]{0, 800, 800, 800});
        AudioAttributes callAudio = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build();
        callChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), callAudio);
        nm.createNotificationChannel(callChannel);

        channelsInitialized = true;
    }

    public static void showMessageNotification(Context context, String convId, String senderName, String senderAvatar, String messageText) {
        if (context == null) return;
        if (!UserPreferences.isNotificationsEnabled(context)) return;

        initChannels(context);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        // Intent to open chat directly
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra("id", convId);
        intent.putExtra("name", senderName);
        intent.putExtra("avatar", senderAvatar);
        intent.putExtra("is_group", false);
        intent.putExtra("status", "Online");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(
                context,
                convId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(senderAvatar + "  " + senderName)
                .setContentText(messageText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(messageText))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        nm.notify(Math.abs(convId.hashCode()), builder.build());

        // Log into in-app notification center
        try {
            String timeStr = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());
            DatabaseHelper.getInstance(context).insertNotification(new NotificationItem(
                    0, senderName, senderAvatar, messageText, timeStr, false, convId, System.currentTimeMillis()
            ));
        } catch (Exception ignored) {}
    }

    public static void showIncomingCallNotification(Context context, String callType, String callerName, String callerAvatar, String callId) {
        if (context == null) return;
        initChannels(context);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        // Full screen intent to answer or view call
        Intent callIntent = new Intent(context, CallActivity.class);
        callIntent.putExtra("name", callerName);
        callIntent.putExtra("avatar", callerAvatar);
        callIntent.putExtra("call_type", callType);
        callIntent.putExtra("call_id", callId);
        callIntent.putExtra("is_incoming", true);
        callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                CALL_NOTIFICATION_ID,
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = (callType.equals("video") ? "📹 Incoming Video Call" : "📞 Incoming Voice Call");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_CALLS_ID)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle(title)
                .setContentText(callerAvatar + " " + callerName + " is calling you...")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .setAutoCancel(true)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_call, "Accept 📞", fullScreenPendingIntent);

        nm.notify(CALL_NOTIFICATION_ID, builder.build());
    }

    public static void cancelCallNotification(Context context) {
        if (context == null) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(CALL_NOTIFICATION_ID);
        }
    }
}
