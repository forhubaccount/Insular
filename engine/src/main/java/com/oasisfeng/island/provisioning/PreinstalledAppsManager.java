package com.oasisfeng.island.provisioning;

import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED;
import static android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.S;
import static android.os.Build.VERSION_CODES.TIRAMISU;

import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.Session;
import android.content.pm.PackageInstaller.SessionParams;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Installs pre-bundled APKs from assets/preinstalled/ into the work profile during provisioning.
 */
public class PreinstalledAppsManager {

	@VisibleForTesting static final String TAG = "Island.Preinstall";
	@VisibleForTesting static final String ASSETS_DIR = "preinstalled";
	private static final int BUFFER_SIZE = 65536;
	@VisibleForTesting static final int INSTALL_TIMEOUT_SECONDS = 120;

	@WorkerThread
	public static void installPreinstalledApps(final Context context) {
		final String[] apkFiles;
		try {
			apkFiles = context.getAssets().list(ASSETS_DIR);
		} catch (final IOException e) {
			Log.w(TAG, "No preinstalled apps directory found", e);
			return;
		}
		if (apkFiles == null || apkFiles.length == 0) {
			Log.i(TAG, "No preinstalled APKs found");
			return;
		}

		for (final String apkName : apkFiles) {
			if (! apkName.endsWith(".apk")) continue;
			final String pkg = extractPackageName(apkName);

			if (isPackageInstalled(context, pkg)) {
				Log.i(TAG, "Package " + pkg + " is already installed, skipping");
				autoGrantPermissions(context, pkg);
				continue;
			}

			Log.i(TAG, "Installing preinstalled app: " + pkg);
			try {
				installApkFromAssets(context, ASSETS_DIR + "/" + apkName, pkg);
				autoGrantPermissions(context, pkg);
			} catch (final Exception e) {
				Log.e(TAG, "Failed to install " + pkg, e);
			}
		}
	}

	/**
	 * Auto-grants all dangerous (runtime) permissions for the given package.
	 * Only works when called by a profile/device owner.
	 */
	@VisibleForTesting
	static void autoGrantPermissions(final Context context, final String pkg) {
		final DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
		final ComponentName admin = getAdminComponent(context);
		if (dpm == null || admin == null) {
			Log.w(TAG, "Cannot auto-grant permissions: DPM or admin unavailable");
			return;
		}
		autoGrantPermissions(context, pkg, dpm, admin);
	}

	@VisibleForTesting
	static void autoGrantPermissions(final Context context, final String pkg,
			final DevicePolicyManager dpm, final ComponentName admin) {
		final String[] permissions = getDangerousPermissions(context, pkg);
		if (permissions.length == 0) {
			Log.i(TAG, "No dangerous permissions to grant for " + pkg);
			return;
		}

		for (final String permission : permissions) {
			try {
				if (dpm.setPermissionGrantState(admin, pkg, permission, PERMISSION_GRANT_STATE_GRANTED)) {
					Log.i(TAG, "Auto-granted permission " + permission + " to " + pkg);
				} else {
					Log.w(TAG, "Failed to auto-grant permission " + permission + " to " + pkg);
				}
			} catch (final SecurityException e) {
				Log.w(TAG, "Not allowed to grant " + permission + " to " + pkg, e);
			}
		}
	}

	/**
	 * Returns the list of dangerous (runtime) permissions declared in the package's manifest.
	 */
	@VisibleForTesting
	static String[] getDangerousPermissions(final Context context, final String pkg) {
		final PackageInfo pkgInfo;
		try {
			pkgInfo = context.getPackageManager().getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);
		} catch (final PackageManager.NameNotFoundException e) {
			Log.w(TAG, "Package not found for permission query: " + pkg);
			return new String[0];
		}
		if (pkgInfo.requestedPermissions == null) return new String[0];

		final java.util.List<String> dangerous = new java.util.ArrayList<>();
		for (final String permission : pkgInfo.requestedPermissions) {
			try {
				final PermissionInfo info = context.getPackageManager().getPermissionInfo(permission, 0);
				if ((info.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE) == PermissionInfo.PROTECTION_DANGEROUS) {
					dangerous.add(permission);
				}
			} catch (final PackageManager.NameNotFoundException ignored) {
				// Unknown permission, skip
			}
		}
		return dangerous.toArray(new String[0]);
	}

	@VisibleForTesting
	static ComponentName getAdminComponent(final Context context) {
		try {
			return com.oasisfeng.island.util.DeviceAdmins.getComponentName(context);
		} catch (final Exception e) {
			Log.w(TAG, "Failed to get admin component", e);
			return null;
		}
	}

	@VisibleForTesting
	static String extractPackageName(final String apkFileName) {
		return apkFileName.substring(0, apkFileName.length() - 4);
	}

	@VisibleForTesting
	static boolean isPackageInstalled(final Context context, final String pkg) {
		try {
			context.getPackageManager().getApplicationInfo(pkg, 0);
			return true;
		} catch (final PackageManager.NameNotFoundException e) {
			return false;
		}
	}

	@WorkerThread
	@VisibleForTesting
	static void installApkFromAssets(final Context context, final String assetPath, final String pkg) throws IOException {
		final File tempApk = extractAssetToTempFile(context, assetPath, pkg);
		try {
			installApk(context, tempApk, pkg);
		} finally {
			if (tempApk.exists() && ! tempApk.delete()) Log.w(TAG, "Failed to delete temp file: " + tempApk);
		}
	}

	@VisibleForTesting
	static File extractAssetToTempFile(final Context context, final String assetPath, final String pkg) throws IOException {
		final String prefix = pkg.length() >= 3 ? pkg : pkg + "___";  // createTempFile requires prefix >= 3 chars
		final File tempFile = File.createTempFile(prefix, ".apk", context.getCacheDir());
		try (final InputStream in = context.getAssets().open(assetPath);
			 final OutputStream out = new FileOutputStream(tempFile)) {
			final byte[] buffer = new byte[BUFFER_SIZE];
			int count;
			while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
		} catch (final IOException e) {
			if (tempFile.exists()) tempFile.delete();
			throw e;
		}
		return tempFile;
	}

	@WorkerThread
	@VisibleForTesting
	static void installApk(final Context context, final File apkFile, final String pkg) throws IOException {
		final PackageInstaller installer = context.getPackageManager().getPackageInstaller();
		final SessionParams params = new SessionParams(MODE_FULL_INSTALL);
		params.setAppPackageName(pkg);
		if (SDK_INT >= S) params.setRequireUserAction(SessionParams.USER_ACTION_NOT_REQUIRED);

		final int sessionId = installer.createSession(params);
		Session session = null;
		try {
			session = installer.openSession(sessionId);
			writeApkToSession(session, apkFile, pkg);
			commitAndAwait(context, session, sessionId, pkg);
			session = null;  // Ownership transferred to commitAndAwait
		} finally {
			if (session != null) {
				try { session.abandon(); } catch (final Exception e) { Log.w(TAG, "Failed to abandon session", e); }
			}
		}
	}

	private static void writeApkToSession(final Session session, final File apkFile, final String pkg) throws IOException {
		try (final InputStream in = new FileInputStream(apkFile);
			 final OutputStream out = session.openWrite(pkg, 0, apkFile.length())) {
			final byte[] buffer = new byte[BUFFER_SIZE];
			int count;
			while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
			session.fsync(out);
		}
	}

	@WorkerThread
	private static void commitAndAwait(final Context context, final Session session, final int sessionId, final String pkg) {
		final CountDownLatch latch = new CountDownLatch(1);
		final int[] result = { PackageInstaller.STATUS_FAILURE };
		final String action = "com.oasisfeng.island.INSTALL_STATUS_" + sessionId;

		final BroadcastReceiver receiver = new BroadcastReceiver() {
			@Override public void onReceive(final Context ctx, final Intent intent) {
				result[0] = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
				final String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
				if (result[0] == PackageInstaller.STATUS_SUCCESS) {
					Log.i(TAG, "Successfully installed " + pkg);
				} else {
					Log.e(TAG, "Failed to install " + pkg + ": status=" + result[0] + ", message=" + msg);
				}
				latch.countDown();
			}
		};

		if (SDK_INT >= TIRAMISU) {
			context.registerReceiver(receiver, new IntentFilter(action), Context.RECEIVER_NOT_EXPORTED);
		} else {
			context.registerReceiver(receiver, new IntentFilter(action));
		}
		try {
			final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, sessionId,
					new Intent(action).setPackage(context.getPackageName()),
					PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
			session.commit(pendingIntent.getIntentSender());
			session.close();

			if (! latch.await(INSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				Log.e(TAG, "Installation of " + pkg + " timed out");
			}
		} catch (final InterruptedException e) {
			Log.e(TAG, "Installation of " + pkg + " interrupted", e);
			Thread.currentThread().interrupt();
		} finally {
			context.unregisterReceiver(receiver);
		}
	}
}
