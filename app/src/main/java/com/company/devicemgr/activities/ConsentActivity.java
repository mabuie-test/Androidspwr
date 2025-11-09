package com.company.devicemgr.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;

import com.company.devicemgr.services.CallRecorderService;
import com.company.devicemgr.services.ContactsUploaderService;

public class ConsentActivity extends Activity {
	private static final int REQ_PERMS = 1234;
	CheckBox chkContacts, chkCalls;
	Button btnGrant;
	TextView tvStatus;
	
	String[] perms = new String[]{
		android.Manifest.permission.READ_CONTACTS,
		android.Manifest.permission.RECORD_AUDIO,
		android.Manifest.permission.READ_PHONE_STATE,
		android.Manifest.permission.READ_CALL_LOG,
		android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
		android.Manifest.permission.READ_EXTERNAL_STORAGE
	};
	
	@Override
	protected void onCreate(Bundle s) {
		super.onCreate(s);
		setContentView(com.company.devicemgr.R.layout.activity_consent);
		
		chkContacts = findViewById(com.company.devicemgr.R.id.chkContacts);
		chkCalls = findViewById(com.company.devicemgr.R.id.chkCalls);
		btnGrant = findViewById(com.company.devicemgr.R.id.btnGrant);
		tvStatus = findViewById(com.company.devicemgr.R.id.tvStatus);
		
		btnGrant.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) {
				boolean wantContacts = chkContacts.isChecked();
				boolean wantCalls = chkCalls.isChecked();
				if (!wantContacts && !wantCalls) {
					Toast.makeText(ConsentActivity.this, "Seleciona ao menos uma opção", Toast.LENGTH_SHORT).show();
					return;
				}
				ActivityCompat.requestPermissions(ConsentActivity.this, perms, REQ_PERMS);
			}
		});
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == REQ_PERMS) {
			boolean allGranted = true;
			for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) allGranted = false;
			if (!allGranted) {
				tvStatus.setText("Permissões não concedidas. Dá permissões nas definições.");
				Toast.makeText(this, "Permissões não concedidas.", Toast.LENGTH_LONG).show();
				return;
			}
			boolean wantContacts = ((CheckBox)findViewById(com.company.devicemgr.R.id.chkContacts)).isChecked();
			boolean wantCalls = ((CheckBox)findViewById(com.company.devicemgr.R.id.chkCalls)).isChecked();
			getSharedPreferences("devicemgr_prefs", MODE_PRIVATE)
			.edit()
			.putBoolean("consent_contacts", wantContacts)
			.putBoolean("consent_calls", wantCalls)
			.apply();
			
			tvStatus.setText("Consentimento guardado.");
			
			if (wantContacts) {
				try { startService(new Intent(this, ContactsUploaderService.class)); } catch (Exception e) {}
			}
			if (wantCalls) {
				try { startService(new Intent(this, CallRecorderService.class)); } catch (Exception e) {}
			}
			
			Toast.makeText(this, "Consentimento registado e serviços iniciados", Toast.LENGTH_SHORT).show();
			finish();
			} else {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}
}