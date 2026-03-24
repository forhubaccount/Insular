package com.oasisfeng.island.provisioning;

import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.res.AssetManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

@RunWith(MockitoJUnitRunner.Silent.class)
public class PreinstalledAppsManagerTest {

	@Rule public TemporaryFolder tempFolder = new TemporaryFolder();

	@Mock Context mockContext;
	@Mock AssetManager mockAssets;
	@Mock PackageManager mockPm;
	@Mock PackageInstaller mockInstaller;

	@Before
	public void setUp() {
		when(mockContext.getAssets()).thenReturn(mockAssets);
		when(mockContext.getPackageManager()).thenReturn(mockPm);
	}

	// ── extractPackageName ───────────────────────────────────────────────

	@Test
	public void extractPackageName_standardApk() {
		assertEquals("com.vkteams", PreinstalledAppsManager.extractPackageName("com.vkteams.apk"));
	}

	@Test
	public void extractPackageName_shortName() {
		assertEquals("a", PreinstalledAppsManager.extractPackageName("a.apk"));
	}

	@Test
	public void extractPackageName_dotsInName() {
		assertEquals("com.example.my.app", PreinstalledAppsManager.extractPackageName("com.example.my.app.apk"));
	}

	// ── isPackageInstalled ───────────────────────────────────────────────

	@Test
	public void isPackageInstalled_returnsTrue_whenPackageExists() throws Exception {
		when(mockPm.getApplicationInfo("com.vkteams", 0)).thenReturn(new ApplicationInfo());
		assertTrue(PreinstalledAppsManager.isPackageInstalled(mockContext, "com.vkteams"));
	}

	@Test
	public void isPackageInstalled_returnsFalse_whenPackageNotFound() throws Exception {
		when(mockPm.getApplicationInfo("com.vkteams", 0))
				.thenThrow(new PackageManager.NameNotFoundException("not found"));
		assertFalse(PreinstalledAppsManager.isPackageInstalled(mockContext, "com.vkteams"));
	}

	// ── installPreinstalledApps - assets listing ─────────────────────────

	@Test
	public void installPreinstalledApps_doesNothing_whenAssetsListThrowsIOException() throws Exception {
		when(mockAssets.list(PreinstalledAppsManager.ASSETS_DIR)).thenThrow(new IOException("no dir"));

		PreinstalledAppsManager.installPreinstalledApps(mockContext);
		// Should not crash — just log and return
	}

	@Test
	public void installPreinstalledApps_doesNothing_whenAssetsListReturnsNull() throws Exception {
		when(mockAssets.list(PreinstalledAppsManager.ASSETS_DIR)).thenReturn(null);

		PreinstalledAppsManager.installPreinstalledApps(mockContext);
		// Should not crash
	}

	@Test
	public void installPreinstalledApps_doesNothing_whenAssetsListReturnsEmpty() throws Exception {
		when(mockAssets.list(PreinstalledAppsManager.ASSETS_DIR)).thenReturn(new String[0]);

		PreinstalledAppsManager.installPreinstalledApps(mockContext);
		// Should not crash
	}

	@Test
	public void installPreinstalledApps_skipsNonApkFiles() throws Exception {
		when(mockAssets.list(PreinstalledAppsManager.ASSETS_DIR))
				.thenReturn(new String[]{"readme.txt", "data.json", "image.png"});

		PreinstalledAppsManager.installPreinstalledApps(mockContext);
		// No install attempt should be made — no PackageManager interaction beyond listing
		verify(mockPm, never()).getPackageInstaller();
	}

	@Test
	public void installPreinstalledApps_skipsAlreadyInstalledPackage() throws Exception {
		when(mockAssets.list(PreinstalledAppsManager.ASSETS_DIR))
				.thenReturn(new String[]{"com.vkteams.apk"});
		when(mockPm.getApplicationInfo("com.vkteams", 0)).thenReturn(new ApplicationInfo());

		PreinstalledAppsManager.installPreinstalledApps(mockContext);
		// Should not attempt to install
		verify(mockPm, never()).getPackageInstaller();
	}

	// ── extractAssetToTempFile ───────────────────────────────────────────

	@Test
	public void extractAssetToTempFile_writesContentToFile() throws Exception {
		final byte[] content = "fake apk content for testing".getBytes();
		when(mockContext.getCacheDir()).thenReturn(tempFolder.getRoot());
		when(mockAssets.open("preinstalled/com.vkteams.apk"))
				.thenReturn(new ByteArrayInputStream(content));

		final File result = PreinstalledAppsManager.extractAssetToTempFile(
				mockContext, "preinstalled/com.vkteams.apk", "com.vkteams");

		assertNotNull(result);
		assertTrue(result.exists());
		assertEquals(content.length, result.length());

		result.delete();
	}

	@Test
	public void extractAssetToTempFile_cleansUpOnIOException() throws Exception {
		when(mockContext.getCacheDir()).thenReturn(tempFolder.getRoot());
		when(mockAssets.open("preinstalled/bad.apk")).thenThrow(new IOException("asset not found"));

		try {
			PreinstalledAppsManager.extractAssetToTempFile(mockContext, "preinstalled/bad.apk", "bad");
			fail("Expected IOException");
		} catch (final IOException expected) {
			final File[] remaining = tempFolder.getRoot().listFiles((dir, name) -> name.startsWith("bad"));
			assertTrue("Temp file should be cleaned up", remaining == null || remaining.length == 0);
		}
	}

	@Test
	public void extractAssetToTempFile_handlesLargeContent() throws Exception {
		final byte[] content = new byte[256 * 1024]; // 256KB
		for (int i = 0; i < content.length; i++) content[i] = (byte) (i % 256);
		when(mockContext.getCacheDir()).thenReturn(tempFolder.getRoot());
		when(mockAssets.open("preinstalled/large.apk"))
				.thenReturn(new ByteArrayInputStream(content));

		final File result = PreinstalledAppsManager.extractAssetToTempFile(
				mockContext, "preinstalled/large.apk", "large");

		assertNotNull(result);
		assertEquals(content.length, result.length());
		result.delete();
	}

	@Test
	public void extractAssetToTempFile_handlesEmptyContent() throws Exception {
		when(mockContext.getCacheDir()).thenReturn(tempFolder.getRoot());
		when(mockAssets.open("preinstalled/empty.apk"))
				.thenReturn(new ByteArrayInputStream(new byte[0]));

		final File result = PreinstalledAppsManager.extractAssetToTempFile(
				mockContext, "preinstalled/empty.apk", "empty");

		assertNotNull(result);
		assertTrue(result.exists());
		assertEquals(0, result.length());
		result.delete();
	}

	// ── installApk - session management ──────────────────────────────────

	@Test
	public void installApk_abandonsSession_whenWriteFails() throws Exception {
		final PackageInstaller.Session mockSession = mock(PackageInstaller.Session.class);

		when(mockPm.getPackageInstaller()).thenReturn(mockInstaller);
		when(mockInstaller.createSession(any())).thenReturn(42);
		when(mockInstaller.openSession(42)).thenReturn(mockSession);
		when(mockSession.openWrite(anyString(), eq(0L), anyLong())).thenThrow(new IOException("write error"));

		final File fakeApk = tempFolder.newFile("com.vkteams.apk");
		try (final OutputStream os = new FileOutputStream(fakeApk)) {
			os.write("fake".getBytes());
		}

		try {
			PreinstalledAppsManager.installApk(mockContext, fakeApk, "com.vkteams");
			fail("Expected IOException");
		} catch (final IOException expected) {
			assertEquals("write error", expected.getMessage());
		}

		verify(mockSession).abandon();
	}

	@Test
	public void installApk_noSessionToAbandon_whenCreateSessionFails() throws Exception {
		when(mockPm.getPackageInstaller()).thenReturn(mockInstaller);
		when(mockInstaller.createSession(any())).thenThrow(new IOException("no space"));

		final File fakeApk = tempFolder.newFile("com.vkteams.apk");

		try {
			PreinstalledAppsManager.installApk(mockContext, fakeApk, "com.vkteams");
			fail("Expected IOException");
		} catch (final IOException expected) {
			assertEquals("no space", expected.getMessage());
		}
		// No session to abandon since createSession failed — no NPE
	}

	@Test
	public void installApk_noSessionToAbandon_whenOpenSessionFails() throws Exception {
		when(mockPm.getPackageInstaller()).thenReturn(mockInstaller);
		when(mockInstaller.createSession(any())).thenReturn(42);
		when(mockInstaller.openSession(42)).thenThrow(new IOException("session error"));

		final File fakeApk = tempFolder.newFile("com.vkteams.apk");

		try {
			PreinstalledAppsManager.installApk(mockContext, fakeApk, "com.vkteams");
			fail("Expected IOException");
		} catch (final IOException expected) {
			assertEquals("session error", expected.getMessage());
		}
		// session was null so no abandon call — no NPE
	}

	@Test
	public void installApk_writesDataAndFsyncs() throws Exception {
		final PackageInstaller.Session mockSession = mock(PackageInstaller.Session.class);
		final OutputStream mockOut = mock(OutputStream.class);

		when(mockPm.getPackageInstaller()).thenReturn(mockInstaller);
		when(mockInstaller.createSession(any())).thenReturn(42);
		when(mockInstaller.openSession(42)).thenReturn(mockSession);
		when(mockSession.openWrite(anyString(), eq(0L), anyLong())).thenReturn(mockOut);
		when(mockContext.getPackageName()).thenReturn("com.oasisfeng.island");

		final File fakeApk = tempFolder.newFile("test.apk");
		try (final OutputStream os = new FileOutputStream(fakeApk)) {
			os.write("test data".getBytes());
		}

		// installApk will eventually call commit, which will call PendingIntent.getBroadcast
		// which returns null (returnDefaultValues = true), so commit will get a NullPointerException.
		// That triggers session.abandon() in finally block.
		try {
			PreinstalledAppsManager.installApk(mockContext, fakeApk, "test");
		} catch (final Exception ignored) {
			// Expected — mock doesn't support full commit flow
		}

		verify(mockSession).fsync(mockOut);
		verify(mockOut).close();
	}

	// ── installApkFromAssets - integration of extract + install ───────────

	@Test
	public void installApkFromAssets_cleansTempFile_afterInstallAttempt() throws Exception {
		final byte[] content = "fake apk".getBytes();
		when(mockContext.getCacheDir()).thenReturn(tempFolder.getRoot());
		when(mockAssets.open("preinstalled/com.test.apk"))
				.thenReturn(new ByteArrayInputStream(content));
		when(mockPm.getPackageInstaller()).thenReturn(mockInstaller);
		when(mockInstaller.createSession(any())).thenThrow(new IOException("test"));

		try {
			PreinstalledAppsManager.installApkFromAssets(mockContext, "preinstalled/com.test.apk", "com.test");
		} catch (final IOException ignored) {}

		// Temp file should be cleaned up
		final File[] remaining = tempFolder.getRoot().listFiles((dir, name) -> name.startsWith("com.test"));
		assertTrue("Temp file should be deleted", remaining == null || remaining.length == 0);
	}

	@Test
	public void installApkFromAssets_cleansTempFile_onAssetExtractFailure() throws Exception {
		when(mockContext.getCacheDir()).thenReturn(tempFolder.getRoot());
		when(mockAssets.open("preinstalled/bad.apk")).thenThrow(new IOException("not found"));

		try {
			PreinstalledAppsManager.installApkFromAssets(mockContext, "preinstalled/bad.apk", "bad");
			fail("Expected IOException");
		} catch (final IOException expected) {
			// Asset extraction failure — temp file should not exist
		}

		final File[] remaining = tempFolder.getRoot().listFiles((dir, name) -> name.startsWith("bad"));
		assertTrue("No temp files should remain", remaining == null || remaining.length == 0);
	}

	// ── installPreinstalledApps - multiple APKs ──────────────────────────

	@Test
	public void installPreinstalledApps_processesMultipleApks() throws Exception {
		when(mockAssets.list(PreinstalledAppsManager.ASSETS_DIR))
				.thenReturn(new String[]{"com.app1.apk", "com.app2.apk", "readme.txt"});

		// app1 not installed, app2 already installed
		when(mockPm.getApplicationInfo("com.app1", 0))
				.thenThrow(new PackageManager.NameNotFoundException());
		when(mockPm.getApplicationInfo("com.app2", 0))
				.thenReturn(new ApplicationInfo());

		// app1 install will fail at asset extraction — that's fine, we're testing the flow
		when(mockContext.getCacheDir()).thenReturn(tempFolder.getRoot());
		when(mockAssets.open("preinstalled/com.app1.apk")).thenThrow(new IOException("no asset"));

		PreinstalledAppsManager.installPreinstalledApps(mockContext);

		// app1 attempted extraction, app2 skipped entirely
		verify(mockAssets).open("preinstalled/com.app1.apk");
		verify(mockAssets, never()).open("preinstalled/com.app2.apk");
	}

	@Test
	public void installPreinstalledApps_continuesAfterOneFailure() throws Exception {
		when(mockAssets.list(PreinstalledAppsManager.ASSETS_DIR))
				.thenReturn(new String[]{"com.fail.apk", "com.ok.apk"});
		when(mockPm.getApplicationInfo(anyString(), anyInt()))
				.thenThrow(new PackageManager.NameNotFoundException());
		when(mockContext.getCacheDir()).thenReturn(tempFolder.getRoot());

		// Both APKs fail on extraction
		when(mockAssets.open("preinstalled/com.fail.apk")).thenThrow(new IOException("corrupt"));
		when(mockAssets.open("preinstalled/com.ok.apk")).thenThrow(new IOException("also fails"));

		PreinstalledAppsManager.installPreinstalledApps(mockContext);
		// Should not throw — both failures are caught and logged

		// Verify both assets were attempted
		verify(mockAssets).open("preinstalled/com.fail.apk");
		verify(mockAssets).open("preinstalled/com.ok.apk");
	}

	// ── Edge cases ───────────────────────────────────────────────────────

	@Test
	public void installPreinstalledApps_handlesApkWithOnlyExtension() throws Exception {
		when(mockAssets.list(PreinstalledAppsManager.ASSETS_DIR))
				.thenReturn(new String[]{".apk"});
		when(mockPm.getApplicationInfo("", 0))
				.thenThrow(new PackageManager.NameNotFoundException());
		when(mockContext.getCacheDir()).thenReturn(tempFolder.getRoot());
		when(mockAssets.open("preinstalled/.apk")).thenThrow(new IOException("bad"));

		PreinstalledAppsManager.installPreinstalledApps(mockContext);
		// Should not crash
	}

	@Test
	public void installPreinstalledApps_mixedFileTypes() throws Exception {
		when(mockAssets.list(PreinstalledAppsManager.ASSETS_DIR))
				.thenReturn(new String[]{"notes.apk.bak", "com.app.apk", "backup.zip"});
		when(mockPm.getApplicationInfo("com.app", 0))
				.thenReturn(new ApplicationInfo()); // already installed

		PreinstalledAppsManager.installPreinstalledApps(mockContext);

		// "notes.apk.bak" doesn't end with ".apk" — skipped
		// "backup.zip" doesn't end with ".apk" — skipped
		// "com.app" is already installed — skipped
		verify(mockPm, never()).getPackageInstaller();
	}

	@Test
	public void extractAssetToTempFile_handlesShortPackageName() throws Exception {
		final byte[] content = "short".getBytes();
		when(mockContext.getCacheDir()).thenReturn(tempFolder.getRoot());
		when(mockAssets.open("preinstalled/ab.apk"))
				.thenReturn(new ByteArrayInputStream(content));

		// "ab" is only 2 chars — should not throw IllegalArgumentException
		final File result = PreinstalledAppsManager.extractAssetToTempFile(
				mockContext, "preinstalled/ab.apk", "ab");

		assertNotNull(result);
		assertTrue(result.exists());
		assertEquals(content.length, result.length());
		result.delete();
	}

	@Test
	public void extractAssetToTempFile_handlesSingleCharPackageName() throws Exception {
		final byte[] content = "x".getBytes();
		when(mockContext.getCacheDir()).thenReturn(tempFolder.getRoot());
		when(mockAssets.open("preinstalled/x.apk"))
				.thenReturn(new ByteArrayInputStream(content));

		final File result = PreinstalledAppsManager.extractAssetToTempFile(
				mockContext, "preinstalled/x.apk", "x");

		assertNotNull(result);
		assertTrue(result.exists());
		result.delete();
	}

	@Test
	public void installPreinstalledApps_singleApkNotInstalled_attemptsInstall() throws Exception {
		when(mockAssets.list(PreinstalledAppsManager.ASSETS_DIR))
				.thenReturn(new String[]{"com.vkteams.apk"});
		when(mockPm.getApplicationInfo("com.vkteams", 0))
				.thenThrow(new PackageManager.NameNotFoundException());
		when(mockContext.getCacheDir()).thenReturn(tempFolder.getRoot());
		when(mockAssets.open("preinstalled/com.vkteams.apk")).thenThrow(new IOException("no real apk"));

		PreinstalledAppsManager.installPreinstalledApps(mockContext);

		// Verify it attempted to open the asset (i.e. tried to install)
		verify(mockAssets).open("preinstalled/com.vkteams.apk");
	}

	// ── getDangerousPermissions ─────────────────────────────────────────

	@Test
	public void getDangerousPermissions_returnsDangerousOnly() throws Exception {
		final PackageInfo pkgInfo = new PackageInfo();
		pkgInfo.requestedPermissions = new String[]{
				"android.permission.CAMERA",
				"android.permission.INTERNET",
				"android.permission.READ_CONTACTS"
		};
		when(mockPm.getPackageInfo("com.test", PackageManager.GET_PERMISSIONS)).thenReturn(pkgInfo);

		final PermissionInfo cameraInfo = new PermissionInfo();
		cameraInfo.protectionLevel = PermissionInfo.PROTECTION_DANGEROUS;
		when(mockPm.getPermissionInfo("android.permission.CAMERA", 0)).thenReturn(cameraInfo);

		final PermissionInfo internetInfo = new PermissionInfo();
		internetInfo.protectionLevel = PermissionInfo.PROTECTION_NORMAL;
		when(mockPm.getPermissionInfo("android.permission.INTERNET", 0)).thenReturn(internetInfo);

		final PermissionInfo contactsInfo = new PermissionInfo();
		contactsInfo.protectionLevel = PermissionInfo.PROTECTION_DANGEROUS;
		when(mockPm.getPermissionInfo("android.permission.READ_CONTACTS", 0)).thenReturn(contactsInfo);

		final String[] result = PreinstalledAppsManager.getDangerousPermissions(mockContext, "com.test");

		assertArrayEquals(new String[]{"android.permission.CAMERA", "android.permission.READ_CONTACTS"}, result);
	}

	@Test
	public void getDangerousPermissions_returnsEmpty_whenPackageNotFound() {
		try {
			when(mockPm.getPackageInfo("com.missing", PackageManager.GET_PERMISSIONS))
					.thenThrow(new PackageManager.NameNotFoundException());
		} catch (final PackageManager.NameNotFoundException ignored) {}

		final String[] result = PreinstalledAppsManager.getDangerousPermissions(mockContext, "com.missing");

		assertEquals(0, result.length);
	}

	@Test
	public void getDangerousPermissions_returnsEmpty_whenNoRequestedPermissions() throws Exception {
		final PackageInfo pkgInfo = new PackageInfo();
		pkgInfo.requestedPermissions = null;
		when(mockPm.getPackageInfo("com.noperm", PackageManager.GET_PERMISSIONS)).thenReturn(pkgInfo);

		final String[] result = PreinstalledAppsManager.getDangerousPermissions(mockContext, "com.noperm");

		assertEquals(0, result.length);
	}

	@Test
	public void getDangerousPermissions_skipsUnknownPermissions() throws Exception {
		final PackageInfo pkgInfo = new PackageInfo();
		pkgInfo.requestedPermissions = new String[]{"com.custom.UNKNOWN_PERM", "android.permission.CAMERA"};
		when(mockPm.getPackageInfo("com.test", PackageManager.GET_PERMISSIONS)).thenReturn(pkgInfo);

		when(mockPm.getPermissionInfo("com.custom.UNKNOWN_PERM", 0))
				.thenThrow(new PackageManager.NameNotFoundException());

		final PermissionInfo cameraInfo = new PermissionInfo();
		cameraInfo.protectionLevel = PermissionInfo.PROTECTION_DANGEROUS;
		when(mockPm.getPermissionInfo("android.permission.CAMERA", 0)).thenReturn(cameraInfo);

		final String[] result = PreinstalledAppsManager.getDangerousPermissions(mockContext, "com.test");

		assertArrayEquals(new String[]{"android.permission.CAMERA"}, result);
	}

	@Test
	public void getDangerousPermissions_filtersSignaturePermissions() throws Exception {
		final PackageInfo pkgInfo = new PackageInfo();
		pkgInfo.requestedPermissions = new String[]{"android.permission.BIND_ADMIN"};
		when(mockPm.getPackageInfo("com.test", PackageManager.GET_PERMISSIONS)).thenReturn(pkgInfo);

		final PermissionInfo sigInfo = new PermissionInfo();
		sigInfo.protectionLevel = PermissionInfo.PROTECTION_SIGNATURE;
		when(mockPm.getPermissionInfo("android.permission.BIND_ADMIN", 0)).thenReturn(sigInfo);

		final String[] result = PreinstalledAppsManager.getDangerousPermissions(mockContext, "com.test");

		assertEquals(0, result.length);
	}

	// ── autoGrantPermissions ────────────────────────────────────────────

	@Test
	public void autoGrantPermissions_grantsAllDangerousPermissions() throws Exception {
		final DevicePolicyManager mockDpm = mock(DevicePolicyManager.class);
		final ComponentName admin = new ComponentName("com.oasisfeng.island", "AdminReceiver");

		final PackageInfo pkgInfo = new PackageInfo();
		pkgInfo.requestedPermissions = new String[]{
				"android.permission.CAMERA",
				"android.permission.RECORD_AUDIO"
		};
		when(mockPm.getPackageInfo("com.test", PackageManager.GET_PERMISSIONS)).thenReturn(pkgInfo);

		final PermissionInfo cameraInfo = new PermissionInfo();
		cameraInfo.protectionLevel = PermissionInfo.PROTECTION_DANGEROUS;
		when(mockPm.getPermissionInfo("android.permission.CAMERA", 0)).thenReturn(cameraInfo);

		final PermissionInfo audioInfo = new PermissionInfo();
		audioInfo.protectionLevel = PermissionInfo.PROTECTION_DANGEROUS;
		when(mockPm.getPermissionInfo("android.permission.RECORD_AUDIO", 0)).thenReturn(audioInfo);

		when(mockDpm.setPermissionGrantState(eq(admin), eq("com.test"), anyString(), eq(PERMISSION_GRANT_STATE_GRANTED)))
				.thenReturn(true);

		PreinstalledAppsManager.autoGrantPermissions(mockContext, "com.test", mockDpm, admin);

		verify(mockDpm).setPermissionGrantState(admin, "com.test", "android.permission.CAMERA", PERMISSION_GRANT_STATE_GRANTED);
		verify(mockDpm).setPermissionGrantState(admin, "com.test", "android.permission.RECORD_AUDIO", PERMISSION_GRANT_STATE_GRANTED);
	}

	@Test
	public void autoGrantPermissions_doesNotCrash_whenDpmIsNull() {
		when(mockContext.getSystemService(Context.DEVICE_POLICY_SERVICE)).thenReturn(null);

		// Should not throw
		PreinstalledAppsManager.autoGrantPermissions(mockContext, "com.test");
	}

	@Test
	public void autoGrantPermissions_doesNotCrash_whenNoDangerousPermissions() throws Exception {
		final DevicePolicyManager mockDpm = mock(DevicePolicyManager.class);
		final ComponentName admin = new ComponentName("com.oasisfeng.island", "AdminReceiver");

		final PackageInfo pkgInfo = new PackageInfo();
		pkgInfo.requestedPermissions = new String[]{"android.permission.INTERNET"};
		when(mockPm.getPackageInfo("com.safe", PackageManager.GET_PERMISSIONS)).thenReturn(pkgInfo);

		final PermissionInfo normalInfo = new PermissionInfo();
		normalInfo.protectionLevel = PermissionInfo.PROTECTION_NORMAL;
		when(mockPm.getPermissionInfo("android.permission.INTERNET", 0)).thenReturn(normalInfo);

		PreinstalledAppsManager.autoGrantPermissions(mockContext, "com.safe", mockDpm, admin);

		// No setPermissionGrantState calls should be made
		verify(mockDpm, never()).setPermissionGrantState(any(), anyString(), anyString(), anyInt());
	}

	@Test
	public void autoGrantPermissions_doesNotCrash_whenPackageNotFound() throws Exception {
		final DevicePolicyManager mockDpm = mock(DevicePolicyManager.class);
		final ComponentName admin = new ComponentName("com.oasisfeng.island", "AdminReceiver");
		when(mockPm.getPackageInfo("com.gone", PackageManager.GET_PERMISSIONS))
				.thenThrow(new PackageManager.NameNotFoundException());

		PreinstalledAppsManager.autoGrantPermissions(mockContext, "com.gone", mockDpm, admin);

		verify(mockDpm, never()).setPermissionGrantState(any(), anyString(), anyString(), anyInt());
	}

	@Test
	public void autoGrantPermissions_handlesSecurityException() throws Exception {
		final DevicePolicyManager mockDpm = mock(DevicePolicyManager.class);
		final ComponentName admin = new ComponentName("com.oasisfeng.island", "AdminReceiver");

		final PackageInfo pkgInfo = new PackageInfo();
		pkgInfo.requestedPermissions = new String[]{"android.permission.CAMERA"};
		when(mockPm.getPackageInfo("com.test", PackageManager.GET_PERMISSIONS)).thenReturn(pkgInfo);

		final PermissionInfo cameraInfo = new PermissionInfo();
		cameraInfo.protectionLevel = PermissionInfo.PROTECTION_DANGEROUS;
		when(mockPm.getPermissionInfo("android.permission.CAMERA", 0)).thenReturn(cameraInfo);

		when(mockDpm.setPermissionGrantState(eq(admin), eq("com.test"), eq("android.permission.CAMERA"),
				eq(PERMISSION_GRANT_STATE_GRANTED))).thenThrow(new SecurityException("not owner"));

		// Should not throw — SecurityException is caught
		PreinstalledAppsManager.autoGrantPermissions(mockContext, "com.test", mockDpm, admin);
	}

	@Test
	public void autoGrantPermissions_handlesGrantFailure() throws Exception {
		final DevicePolicyManager mockDpm = mock(DevicePolicyManager.class);
		final ComponentName admin = new ComponentName("com.oasisfeng.island", "AdminReceiver");

		final PackageInfo pkgInfo = new PackageInfo();
		pkgInfo.requestedPermissions = new String[]{"android.permission.CAMERA"};
		when(mockPm.getPackageInfo("com.test", PackageManager.GET_PERMISSIONS)).thenReturn(pkgInfo);

		final PermissionInfo cameraInfo = new PermissionInfo();
		cameraInfo.protectionLevel = PermissionInfo.PROTECTION_DANGEROUS;
		when(mockPm.getPermissionInfo("android.permission.CAMERA", 0)).thenReturn(cameraInfo);

		when(mockDpm.setPermissionGrantState(eq(admin), eq("com.test"), eq("android.permission.CAMERA"),
				eq(PERMISSION_GRANT_STATE_GRANTED))).thenReturn(false);

		// Should not throw — just logs a warning
		PreinstalledAppsManager.autoGrantPermissions(mockContext, "com.test", mockDpm, admin);

		verify(mockDpm, times(1)).setPermissionGrantState(eq(admin), eq("com.test"),
				eq("android.permission.CAMERA"), eq(PERMISSION_GRANT_STATE_GRANTED));
	}
}
