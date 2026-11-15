package com.example.lumachat.call;

import android.content.Context;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class CallRingtoneManager {

    private static CallRingtoneManager instance;

    private Ringtone incomingRingtone;
    private Vibrator vibrator;
    private ToneGenerator toneGenerator;
    private Handler toneHandler = new Handler(Looper.getMainLooper());
    private Runnable toneRunnable;

    private boolean isRinging = false;

    public static synchronized CallRingtoneManager getInstance() {
        if (instance == null) {
            instance = new CallRingtoneManager();
        }
        return instance;
    }

    public synchronized void startIncomingRingtone(Context context) {
        if (isRinging) return;
        isRinging = true;

        try {
            Uri ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(context.getApplicationContext(), RingtoneManager.TYPE_RINGTONE);
            if (ringtoneUri == null) {
                ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }
            incomingRingtone = RingtoneManager.getRingtone(context.getApplicationContext(), ringtoneUri);
            if (incomingRingtone != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    incomingRingtone.setLooping(true);
                }
                incomingRingtone.play();
            }

            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                long[] pattern = new long[]{0, 1000, 1000, 1000};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void startOutgoingRinging() {
        if (isRinging) return;
        isRinging = true;

        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, ToneGenerator.MAX_VOLUME);
            toneRunnable = new Runnable() {
                @Override
                public void run() {
                    if (toneGenerator != null && isRinging) {
                        toneGenerator.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1500);
                        toneHandler.postDelayed(this, 4000);
                    }
                }
            };
            toneHandler.post(toneRunnable);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void stopAll() {
        isRinging = false;

        if (incomingRingtone != null) {
            try {
                incomingRingtone.stop();
            } catch (Exception ignored) {}
            incomingRingtone = null;
        }

        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception ignored) {}
            vibrator = null;
        }

        if (toneHandler != null && toneRunnable != null) {
            toneHandler.removeCallbacks(toneRunnable);
            toneRunnable = null;
        }

        if (toneGenerator != null) {
            try {
                toneGenerator.stopTone();
                toneGenerator.release();
            } catch (Exception ignored) {}
            toneGenerator = null;
        }
    }
}
