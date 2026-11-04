package com.kawaqdr.doladown

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.lody.virtual.client.core.InstallStrategy
import com.lody.virtual.client.core.VirtualCore
import com.lody.virtual.client.ipc.VActivityManager
import java.io.File

object DolaSandbox {
    const val DOLA_PACKAGE = "com.larus.wolf"

    fun installExistingDola(context: Context): String {
        check(DolaDownApp.runtimeReady) {
            "Virtual runtime is not ready: ${DolaDownApp.startupError?.message ?: "unknown startup error"}"
        }

        val appInfo = try {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(DOLA_PACKAGE, PackageManager.GET_META_DATA)
        } catch (_: PackageManager.NameNotFoundException) {
            throw IllegalStateException(
                "Dola is not installed on this phone. Install the official Dola app first, then retry."
            )
        }

        // Play Store apps may be delivered as base + configuration split APKs.
        // VirtualApp's own installer passes the containing directory for split packages.
        val sourcePath = if (!appInfo.splitSourceDirs.isNullOrEmpty()) {
            File(appInfo.sourceDir).parentFile?.absolutePath ?: appInfo.sourceDir
        } else {
            appInfo.sourceDir
        }

        val core = VirtualCore.get()
        core.waitForEngine()
        val result = core.installPackage(
            sourcePath,
            InstallStrategy.UPDATE_IF_EXIST or
                InstallStrategy.COMPARE_VERSION or
                InstallStrategy.SKIP_DEX_OPT
        )
        check(result.isSuccess) {
            "Dola sandbox installation failed: ${result.error ?: "unknown engine error"}"
        }
        return "Dola installed in the private Dola Down sandbox."
    }

    fun launchDola(context: Context) {
        check(DolaDownApp.runtimeReady) {
            "Virtual runtime is not ready: ${DolaDownApp.startupError?.message ?: "unknown startup error"}"
        }
        val core = VirtualCore.get()
        core.waitForEngine()
        check(core.isAppInstalled(DOLA_PACKAGE)) {
            "Dola has not been installed into the sandbox yet. Tap Install Dola first."
        }
        val launchIntent: Intent = core.getLaunchIntent(DOLA_PACKAGE, 0)
            ?: error("Dola is installed in the sandbox but has no launchable activity.")
        // Guest activities must be started through VirtualApp's activity manager,
        // not Android's ordinary Activity.startActivity().
        VActivityManager.get().startActivity(launchIntent, 0)
    }

    fun isDolaInstalledInSandbox(): Boolean =
        DolaDownApp.runtimeReady && VirtualCore.get().isAppInstalled(DOLA_PACKAGE)
}
