package com.company.devicemgr.receivers;
  public class DeviceAdminReceiver extends android.app.admin.DeviceAdminReceiver {
	@Override
	public void onEnabled(android.content.Context context, android.content.Intent intent) {
		android.widget.Toast.makeText(context, "Device admin enabled", android.widget.Toast.LENGTH_SHORT).show();
	}
	
	@Override
	public void onDisabled(android.content.Context context, android.content.Intent intent) {
		android.widget.Toast.makeText(context, "Device admin disabled", android.widget.Toast.LENGTH_SHORT).show();
	}
}