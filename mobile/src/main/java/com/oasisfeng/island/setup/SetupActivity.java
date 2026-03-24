package com.oasisfeng.island.setup;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.oasisfeng.island.mobile.R;

/**
 * Island setup — welcome screen with license, then provisioning splash.
 */
public class SetupActivity extends Activity {

	private static final String TAG = "Island.Setup";
	private boolean mShowingSplash;
	private boolean mProvisioningResultReceived;

	@Override protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
		getWindow().setStatusBarColor(0xFF1A237E);  // Match gradient top
		getWindow().setNavigationBarColor(0xFF3949AB);  // Match gradient bottom
		showWelcomeScreen();
	}

	private void showWelcomeScreen() {
		setContentView(com.oasisfeng.island.shared.R.layout.activity_welcome);
		findViewById(com.oasisfeng.island.shared.R.id.btn_accept).setOnClickListener(v -> onAcceptClicked());
	}

	private void onAcceptClicked() {
		// Check prerequisites and start provisioning
		final SetupViewModel result = SetupViewModel.checkManagedProvisioningPrerequisites(this, false);
		if (result != null) {
			// Show error in old wizard style
			setContentView(R.layout.activity_main);
			final SetupWizardFragment fragment = new SetupWizardFragment();
			final Bundle args = new Bundle();
			args.putParcelable(null, result);
			fragment.setArguments(args);
			getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
			return;
		}
		// Prerequisites OK — show splash and start provisioning
		showProvisioningSplash();
		SetupViewModel.startManagedProvisioningFromActivity(this);
	}

	/** Switch to provisioning splash. */
	void showProvisioningSplash() {
		mShowingSplash = true;
		setContentView(com.oasisfeng.island.shared.R.layout.activity_provisioning_splash);
	}

	void onProvisioningResultOk() {
		mProvisioningResultReceived = true;
	}

	boolean isShowingSplash() { return mShowingSplash; }

	@Override protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		// Forward to any fragments that might be showing (error case)
		final Fragment fragment = getFragmentManager().findFragmentById(R.id.container);
		if (fragment != null) {
			fragment.onActivityResult(requestCode, resultCode, data);
			return;
		}
		// Handle provisioning result directly (splash mode)
		final SetupViewModel next = SetupViewModel.onActivityResult(this, requestCode, resultCode);
		if (next != null) {
			// Error — switch back to welcome
			showWelcomeScreen();
		}
	}

	@Override protected void onStop() {
		super.onStop();
		if (mShowingSplash && mProvisioningResultReceived) finish();
	}
}
