package com.focuslock.app.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Device admin so the app cannot be uninstalled without deactivating this first. */
class FocusLockAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Uninstall protection is on", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Uninstall protection is off", Toast.LENGTH_SHORT).show()
    }
}
