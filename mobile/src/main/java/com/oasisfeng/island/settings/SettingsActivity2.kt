package com.oasisfeng.island.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Build.VERSION_CODES.P
import android.os.Build.VERSION_CODES.Q
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.widget.Toolbar
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.oasisfeng.island.appops.AppOpsCompat
import com.oasisfeng.island.appops.AppOpsCompat.GET_APP_OPS_STATS
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.setup.IslandSetup
import com.oasisfeng.island.shortcut.IslandAppShortcut
import com.oasisfeng.island.util.DPM
import com.oasisfeng.island.util.DevicePolicies
import com.oasisfeng.island.util.Modules
import com.oasisfeng.island.util.Permissions
import com.oasisfeng.island.util.Users
import eu.chainfire.libsuperuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "Island.Settings2"

/**
 * Modern flat settings screen. All settings visible on one scrollable page.
 * Each setting delegates to the same APIs as the original SettingsActivity.
 */
class SettingsActivity2 : Activity() {

	private var settings: IslandSettings? = null
	private var policies: DevicePolicies? = null
	private var isOwner = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_settings2)
		findViewById<Toolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

		try { policies = DevicePolicies(this) } catch (e: Exception) { Log.w(TAG, "DPM unavailable", e) }
		try { settings = IslandSettings(this) } catch (e: Exception) { Log.w(TAG, "IslandSettings unavailable", e) }
		isOwner = try { policies?.isProfileOrDeviceOwnerOnCallingUser == true } catch (e: Exception) { false }

		setupGeneralSection()
		setupPrivacySection()
		setupCrossProfileSection()
		setupNotificationsSection()
		setupSetupSection()
		setupAboutSection()
	}

	override fun onResume() {
		super.onResume()
		// Refresh notification toggle states when returning from system channel settings
		refreshNotificationSwitches()
	}

	// ── General ─────────────────────────────────────────────────────────

	private fun setupGeneralSection() {
		// Preserve App Ops
		if (SDK_INT < P) {
			findViewById<View>(R.id.pref_preserve_app_ops).visibility = View.GONE
		} else {
			val view = findViewById<View>(R.id.pref_preserve_app_ops)
			val toggle = view.findViewById<SwitchCompat>(R.id.toggle)
			view.findViewById<TextView>(R.id.title).setText(R.string.pref_preserve_app_ops)
			view.findViewById<TextView>(R.id.summary).apply { setText(R.string.pref_preserve_app_ops_description); visibility = View.VISIBLE }
			if (Permissions.has(this, GET_APP_OPS_STATS)) {
				toggle.isChecked = true; toggle.isEnabled = false; view.isEnabled = false
			} else {
				toggle.isChecked = false
				val triggerGrant = {
					CoroutineScope(Dispatchers.Main).launch {
						val cmd = "pm grant ${Modules.MODULE_ENGINE} $GET_APP_OPS_STATS"
						launch(Dispatchers.IO) { Shell.SU.run(cmd) }.join()
						if (Permissions.has(this@SettingsActivity2, GET_APP_OPS_STATS)) {
							toggle.isChecked = true; toggle.isEnabled = false; view.isEnabled = false
						} else {
							AlertDialog.Builder(this@SettingsActivity2)
								.setMessage(getString(R.string.prompt_adb_app_ops_command) + "\n\n" + cmd)
								.setPositiveButton(android.R.string.ok) { _, _ ->
									if (Permissions.has(this@SettingsActivity2, GET_APP_OPS_STATS)) {
										toggle.isChecked = true; toggle.isEnabled = false
									}
								}
								.setNeutralButton(R.string.action_copy) { _, _ ->
									(getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
										?.setPrimaryClip(ClipData.newPlainText(null, cmd))
								}.show()
						}
					}; Unit
				}
				view.setOnClickListener { triggerGrant() }
				toggle.setOnCheckedChangeListener { _, _ -> triggerGrant() }
			}
		}

		// Show admin message — DPM API
		if (!isOwner) {
			findViewById<View>(R.id.pref_show_admin_message).visibility = View.GONE
		} else {
			val view = findViewById<View>(R.id.pref_show_admin_message)
			val toggle = view.findViewById<SwitchCompat>(R.id.toggle)
			view.findViewById<TextView>(R.id.title).setText(R.string.pref_show_admin_message_title)
			view.findViewById<TextView>(R.id.summary).apply { setText(R.string.pref_show_admin_message_summary); visibility = View.VISIBLE }
			toggle.isChecked = try { policies!!.invoke(DPM::getShortSupportMessage) != null } catch (e: Exception) { false }
			toggle.setOnCheckedChangeListener { _, checked ->
				try {
					val shortMsg = if (checked) getText(com.oasisfeng.island.shared.R.string.device_admin_support_message_short) else null
					val longMsg = if (checked) getText(com.oasisfeng.island.shared.R.string.device_admin_support_message_long) else null
					policies!!.execute(DPM::setShortSupportMessage, shortMsg)
					policies!!.execute(DPM::setLongSupportMessage, longMsg)
				} catch (e: Exception) { Log.e(TAG, "Failed to set admin message", e) }
			}
			view.setOnClickListener { toggle.toggle() }
		}

		// Launch shortcut prefix — hidden on O+
		if (SDK_INT >= O) {
			findViewById<View>(R.id.pref_launch_shortcut_prefix).visibility = View.GONE
		} else {
			val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this)
			setupClickItem(R.id.pref_launch_shortcut_prefix, R.string.pref_launch_shortcut_prefix_title, 0) {
				val current = prefs.getString("launch_shortcut_prefix", "") ?: ""
				showEditDialog(R.string.pref_launch_shortcut_prefix_title, current) { value ->
					prefs.edit().putString("launch_shortcut_prefix", value).apply()
					updateClickSummary(R.id.pref_launch_shortcut_prefix, value)
				}
			}
			updateClickSummary(R.id.pref_launch_shortcut_prefix, prefs.getString("launch_shortcut_prefix", "") ?: "")
		}

		// Dynamic shortcut label
		safeSetupIslandSwitch(R.id.pref_dynamic_shortcut_label,
			R.string.pref_dynamic_shortcut_label_title, R.string.pref_dynamic_shortcut_label_summary,
			{ settings?.DynamicShortcutLabel() }, visible = SDK_INT >= O) {
			Toast.makeText(this, R.string.prompt_updating_shortcuts, Toast.LENGTH_LONG).show()
			if (SDK_INT >= O) Handler().post { IslandAppShortcut.updateAllInActiveProfiles(this) }
		}

		// App settings helper — component enable/disable (can throw if service not found)
		safeSetupIslandSwitch(R.id.pref_app_settings_helper,
			R.string.pref_app_settings_title, R.string.pref_app_settings_summary,
			{ settings?.AppSettingsHelper() })

		// App settings helper extended
		safeSetupIslandSwitch(R.id.pref_app_settings_helper_extended,
			R.string.pref_app_settings_extended_title, R.string.pref_app_settings_extended_summary,
			{ settings?.AppSettingsHelperExtended() })
	}

	// ── Privacy (AppOps) ────────────────────────────────────────────────

	private fun setupPrivacySection() {
		val showPrivacy = SDK_INT in P..Q && isOwner
		setupOpsClickItem(R.id.pref_read_phone_state,
			R.string.pref_privacy_read_phone_state_title, R.string.pref_privacy_read_phone_state_summary,
			android.Manifest.permission.READ_PHONE_STATE, AppOpsCompat.OP_READ_PHONE_STATE,
			visible = showPrivacy && SDK_INT <= P)

		setupOpsClickItem(R.id.pref_read_sms,
			R.string.pref_privacy_read_sms_title, 0,
			android.Manifest.permission.READ_SMS, AppOpsCompat.OP_READ_SMS,
			visible = showPrivacy)

		setupOpsClickItem(R.id.pref_location,
			R.string.pref_privacy_location_title, R.string.pref_privacy_location_summary,
			android.Manifest.permission.ACCESS_COARSE_LOCATION, AppOpsCompat.OP_COARSE_LOCATION,
			visible = showPrivacy)

		setupOpsClickItem(R.id.pref_storage,
			R.string.pref_privacy_storage_title, R.string.pref_privacy_storage_summary,
			android.Manifest.permission.READ_EXTERNAL_STORAGE, AppOpsCompat.OP_READ_EXTERNAL_STORAGE,
			visible = showPrivacy && android.provider.Settings.Global.getInt(
				contentResolver, android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0)
	}

	// ── Cross-profile ───────────────────────────────────────────────────

	private fun setupCrossProfileSection() {
		val show = SDK_INT > Q && isOwner
		if (!show) {
			findViewById<View>(R.id.pref_cross_profile).visibility = View.GONE
			findViewById<View>(R.id.pref_cross_profile_widgets).visibility = View.GONE
			return
		}
		setupClickItem(R.id.pref_cross_profile,
			R.string.pref_manage_cross_profile_title, R.string.pref_manage_cross_profile_summary) {
			// Delegate to the original Island settings which has the full DPM dialog logic
			SettingsActivity.startWithPreference(this, IslandSettingsFragment::class.java)
		}
		setupClickItem(R.id.pref_cross_profile_widgets,
			R.string.pref_manage_cross_profile_widgets_title, R.string.pref_manage_cross_profile_widgets_summary) {
			SettingsActivity.startWithPreference(this, IslandSettingsFragment::class.java)
		}
	}

	// ── Notifications ───────────────────────────────────────────────────

	private fun setupNotificationsSection() {
		setupNotificationSwitch(R.id.pref_island_watcher,
			com.oasisfeng.island.shared.R.string.notification_channel_island_watcher,
			R.string.pref_island_watcher_summary,
			com.oasisfeng.island.notification.NotificationIds.IslandWatcher,
			visible = SDK_INT >= P && !Users.isParentProfile())

		setupNotificationSwitch(R.id.pref_app_watcher,
			com.oasisfeng.island.shared.R.string.notification_channel_app_watcher,
			R.string.pref_app_watcher_summary,
			com.oasisfeng.island.notification.NotificationIds.IslandAppWatcher,
			visible = SDK_INT >= O)
	}

	private fun refreshNotificationSwitches() {
		refreshNotificationToggle(R.id.pref_island_watcher, com.oasisfeng.island.notification.NotificationIds.IslandWatcher)
		refreshNotificationToggle(R.id.pref_app_watcher, com.oasisfeng.island.notification.NotificationIds.IslandAppWatcher)
	}

	private fun refreshNotificationToggle(viewId: Int, notificationId: com.oasisfeng.island.notification.NotificationIds) {
		val view = findViewById<View>(viewId)
		if (view.visibility != View.VISIBLE) return
		val toggle = view.findViewById<SwitchCompat>(R.id.toggle)
		val newState = try { !notificationId.isBlocked(this) } catch (e: Exception) { return }
		if (toggle.isChecked != newState) {
			toggle.setOnCheckedChangeListener(null)  // prevent triggering settings open
			toggle.isChecked = newState
			toggle.setOnCheckedChangeListener { _, _ ->
				startActivity(notificationId.buildChannelSettingsIntent(this))
			}
		}
	}

	// ── Setup ───────────────────────────────────────────────────────────

	@SuppressLint("InlinedApi")
	private fun setupSetupSection() {
		// Repair — always show, delegates to engine service
		setupClickItem(R.id.pref_repair, R.string.pref_reprovision_title, R.string.pref_reprovision_summary) {
			try {
				val action = if (policies?.isActiveDeviceOwner == true)
					android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE
				else android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE
				ContextCompat.startForegroundService(this, Intent(action).setPackage(Modules.MODULE_ENGINE))
			} catch (e: Exception) {
				Log.e(TAG, "Failed to start repair", e)
				Toast.makeText(this, "Repair failed: ${e.message}", Toast.LENGTH_LONG).show()
			}
		}

		// Destroy — always show
		setupClickItem(R.id.pref_destroy, R.string.pref_destroy_title, R.string.pref_destroy_summary) {
			try {
				if (Users.isParentProfile()) IslandSetup.requestDeviceOrProfileOwnerDeactivation(this)
				else IslandSetup.requestProfileRemoval(this)
			} catch (e: Exception) {
				Log.e(TAG, "Failed to destroy", e)
				Toast.makeText(this, "Operation failed: ${e.message}", Toast.LENGTH_LONG).show()
			}
		}
	}

	// ── About ───────────────────────────────────────────────────────────

	private fun setupAboutSection() {
		val versionSummary = try {
			packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
		} catch (e: Exception) { "unknown" }

		setupClickItem(R.id.pref_version, R.string.pref_about_version_title, 0, null)
		updateClickSummary(R.id.pref_version, versionSummary)

		setupClickItem(R.id.pref_faq, R.string.pref_about_faq_title, R.string.pref_about_faq_summary) {
			startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://island.oasisfeng.com/faq")))
		}

		setupClickItem(R.id.pref_open_source, R.string.pref_about_open_source_title, R.string.pref_about_open_source_summary) {
			startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://gitlab.com/secure-system/Insular")))
		}

		setupClickItem(R.id.pref_licenses, R.string.pref_about_licenses_title, 0) {
			SettingsActivity.startWithPreference(this, SettingsActivity.AboutFragment::class.java)
		}
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	/** Safe wrapper that catches exceptions from IslandSettings (e.g., service not found) */
	private fun safeSetupIslandSwitch(viewId: Int, titleRes: Int, summaryRes: Int,
			settingProvider: () -> IslandSettings.BooleanSetting?, visible: Boolean = true,
			onChanged: (() -> Unit)? = null) {
		val view = findViewById<View>(viewId)
		if (!visible) { view.visibility = View.GONE; return }
		try {
			val setting = settingProvider() ?: throw IllegalStateException("Setting unavailable")
			view.findViewById<TextView>(R.id.title).setText(titleRes)
			if (summaryRes != 0) view.findViewById<TextView>(R.id.summary).apply { setText(summaryRes); visibility = View.VISIBLE }
			val toggle = view.findViewById<SwitchCompat>(R.id.toggle)
			toggle.isChecked = setting.enabled
			toggle.setOnCheckedChangeListener { _, checked ->
				try { setting.set(checked) } catch (e: Exception) { Log.e(TAG, "Failed to set $titleRes", e) }
				onChanged?.invoke()
			}
			view.setOnClickListener { toggle.toggle() }
		} catch (e: Exception) {
			Log.w(TAG, "Setting unavailable, hiding: $titleRes", e)
			view.visibility = View.GONE
		}
	}

	private fun setupOpsClickItem(viewId: Int, titleRes: Int, summaryRes: Int,
			permission: String, op: Int, visible: Boolean) {
		val view = findViewById<View>(viewId)
		if (!visible) { view.visibility = View.GONE; return }
		view.findViewById<TextView>(R.id.title).setText(titleRes)
		if (summaryRes != 0) view.findViewById<TextView>(R.id.summary).apply { setText(summaryRes); visibility = View.VISIBLE }
		view.setOnClickListener {
			try { OpsManager(this, permission, op).startOpsManager(titleRes) }
			catch (e: Exception) { Log.e(TAG, "OpsManager failed", e) }
		}
	}

	private fun setupNotificationSwitch(viewId: Int, titleRes: Int, summaryRes: Int,
			notificationId: com.oasisfeng.island.notification.NotificationIds, visible: Boolean) {
		val view = findViewById<View>(viewId)
		if (!visible) { view.visibility = View.GONE; return }
		view.findViewById<TextView>(R.id.title).setText(titleRes)
		if (summaryRes != 0) view.findViewById<TextView>(R.id.summary).apply { setText(summaryRes); visibility = View.VISIBLE }
		val toggle = view.findViewById<SwitchCompat>(R.id.toggle)
		toggle.isChecked = try { !notificationId.isBlocked(this) } catch (e: Exception) { false }
		toggle.setOnCheckedChangeListener { _, _ ->
			try { startActivity(notificationId.buildChannelSettingsIntent(this)) }
			catch (e: Exception) { Log.e(TAG, "Failed to open channel settings", e) }
		}
		view.setOnClickListener { toggle.toggle() }
	}

	private fun setupClickItem(viewId: Int, titleRes: Int, summaryRes: Int, onClick: (() -> Unit)? = null) {
		val view = findViewById<View>(viewId)
		view.findViewById<TextView>(R.id.title).setText(titleRes)
		if (summaryRes != 0) view.findViewById<TextView>(R.id.summary).apply { setText(summaryRes); visibility = View.VISIBLE }
		if (onClick != null) view.setOnClickListener { onClick() }
	}

	private fun updateClickSummary(viewId: Int, text: String) {
		if (text.isEmpty()) return
		findViewById<View>(viewId).findViewById<TextView>(R.id.summary).apply {
			this.text = text; visibility = View.VISIBLE
		}
	}

	private fun showEditDialog(titleRes: Int, currentValue: String, onResult: (String) -> Unit) {
		val editText = EditText(this).apply { setText(currentValue); selectAll(); setPadding(48, 32, 48, 16) }
		AlertDialog.Builder(this).setTitle(titleRes).setView(editText)
			.setPositiveButton(android.R.string.ok) { _, _ -> onResult(editText.text.toString()) }
			.setNegativeButton(android.R.string.cancel, null).show()
	}
}
