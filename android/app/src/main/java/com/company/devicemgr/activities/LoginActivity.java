package com.company.devicemgr.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.company.devicemgr.utils.HttpClient;

import org.json.JSONObject;

import java.util.UUID;

public class LoginActivity extends Activity {
	EditText etEmail, etPassword;
	Button btnLogin;
	
	@Override protected void onCreate(Bundle s) {
		super.onCreate(s);
		setContentView(com.company.devicemgr.R.layout.activity_login);
		
		etEmail = findViewById(com.company.devicemgr.R.id.etEmail);
		etPassword = findViewById(com.company.devicemgr.R.id.etPassword);
		btnLogin = findViewById(com.company.devicemgr.R.id.btnLogin);
		
		btnLogin.setOnClickListener(new View.OnClickListener(){
			@Override public void onClick(View v){
				final String email = etEmail.getText().toString().trim();
				final String password = etPassword.getText().toString().trim();
				if(email.length() == 0 || password.length() == 0) {
					Toast.makeText(LoginActivity.this, "Preenche email e password", Toast.LENGTH_SHORT).show();
					return;
				}
				btnLogin.setEnabled(false);
				new Thread(new Runnable(){ public void run(){
						try {
							JSONObject body = new JSONObject();
							body.put("email", email);
							body.put("password", password);
							
							// Backend login endpoint
							String url = "https://spymb.onrender.com/api/auth/login";
							String res = HttpClient.postJson(url, body.toString(), null);
							
							final JSONObject jo = new JSONObject(res);
							if (jo.has("token")) {
								final String token = jo.getString("token");
								final String userId = jo.optString("userId", null);
								final String role = jo.optString("role", "user");
								// deviceId: if not present, generate one and save locally
								SharedPreferences sp = getSharedPreferences("devicemgr_prefs", MODE_PRIVATE);
								String deviceId = sp.getString("deviceId", null);
								if (deviceId == null || deviceId.length() == 0) {
									deviceId = UUID.randomUUID().toString();
									sp.edit().putString("deviceId", deviceId).apply();
								}
								sp.edit().putString("auth_token", token).putString("userId", userId).putString("role", role).apply();
								
								// start foreground service
								runOnUiThread(new Runnable(){ public void run(){
										Toast.makeText(LoginActivity.this, "Login OK", Toast.LENGTH_SHORT).show();
										try {
											Intent svc = new Intent(LoginActivity.this, com.company.devicemgr.services.ForegroundTelemetryService.class);
											if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
												startForegroundService(svc);
												} else {
												startService(svc);
											}
										} catch (Exception e) { e.printStackTrace(); }
										
										// open permissions/main screen
										startActivity(new Intent(LoginActivity.this, MainPermissionsActivity.class));
										finish();
								}});
								} else {
								final String err = jo.optString("error", res);
								runOnUiThread(new Runnable(){ public void run(){
										Toast.makeText(LoginActivity.this, "Login falhou: " + err, Toast.LENGTH_LONG).show();
										btnLogin.setEnabled(true);
								}});
							}
							} catch (final Exception e) {
							e.printStackTrace();
							runOnUiThread(new Runnable(){ public void run(){
									Toast.makeText(LoginActivity.this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
									btnLogin.setEnabled(true);
							}});
						}
				}}).start();
			}
		});
	}
}