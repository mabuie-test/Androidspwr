package com.company.devicemgr.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.company.devicemgr.services.ForegroundTelemetryService;

public class BootReceiver extends BroadcastReceiver {
	@Override public void onReceive(Context context, Intent intent) {
		if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
			// optionally restart service if user wants
			// start service only if stored flag says to start
			android.content.SharedPreferences sp = context.getSharedPreferences("devicemgr_prefs", Context.MODE_PRIVATE);
			boolean started = sp.getBoolean("service_started", false);
			if (started) {
				Intent svc = new Intent(context, ForegroundTelemetryService.class);
				context.startForegroundService(svc);
			}
		}
	}
}