package com.radiocompanion.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.radiocompanion.RadioCompanionApp

/**
 * Result callback from PackageInstaller. "Pending user action" means Android wants
 * the user to confirm: we open its confirmation screen. Any other status is passed
 * to [UpdateManager]. On success Android replaces and restarts the app.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            if (confirm != null) {
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure {
                        Log.w("RdmUpdate", "Couldn't open installer confirmation", it)
                        (context.applicationContext as RadioCompanionApp).updates.onInstallFinished(PackageInstaller.STATUS_FAILURE_BLOCKED)
                    }
            }
            return
        }
        Log.i("RdmUpdate", "Install status $status: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        (context.applicationContext as RadioCompanionApp).updates.onInstallFinished(status)
    }
}
