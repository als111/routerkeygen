/*
 * Copyright 2012 Rui Araújo, Luís Fonseca
 *
 * This file is part of Router Keygen.
 *
 * Router Keygen is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Router Keygen is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Router Keygen.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.exobel.routerkeygen.ui;

import org.exobel.routerkeygen.R;
import org.exobel.routerkeygen.WiFiScanReceiver;
import org.exobel.routerkeygen.WiFiScanReceiver.OnScanListener;
import org.exobel.routerkeygen.WifiStateReceiver;
import org.exobel.routerkeygen.WirelessMatcher;
import org.exobel.routerkeygen.algorithms.Keygen;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.google.analytics.tracking.android.EasyTracker;

public class NetworksListActivity extends SherlockFragmentActivity implements
		NetworksListFragment.OnItemSelectionListener, OnScanListener {

	private boolean mTwoPane;

	private WirelessMatcher networkMatcher;
	private WifiManager wifi;
	private BroadcastReceiver scanFinished;
	private BroadcastReceiver stateChanged;
	private static final String donateScreenShownPref = "donateScreenShown";
	boolean welcomeScreenShown;

	private Handler mHandler = new Handler();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		EasyTracker.getInstance().setContext(getApplicationContext());
		setContentView(R.layout.activity_networks_list);

		final NetworksListFragment fragment = ((NetworksListFragment) getSupportFragmentManager()
				.findFragmentById(R.id.item_list));
		if (findViewById(R.id.item_detail_container) != null) {
			mTwoPane = true;
			fragment.setActivateOnItemClick(true);
		}
		networkMatcher = new WirelessMatcher(getResources().openRawResource(
				R.raw.alice));
		wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);

		wifi_state = wifi.getWifiState() == WifiManager.WIFI_STATE_ENABLED
				|| wifi.getWifiState() == WifiManager.WIFI_STATE_ENABLING;
		scanFinished = new WiFiScanReceiver(networkMatcher, wifi, fragment,
				this);
		stateChanged = new WifiStateReceiver(wifi);

		final SharedPreferences mPrefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		welcomeScreenShown = mPrefs.getBoolean(donateScreenShownPref, false);

		if (!welcomeScreenShown) {

			final String whatsNewTitle = getString(R.string.msg_welcome_title);
			final String whatsNewText = getString(R.string.msg_welcome_text);
			new AlertDialog.Builder(this)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setTitle(whatsNewTitle)
					.setMessage(whatsNewText)
					.setNegativeButton(android.R.string.ok,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									dialog.dismiss();
								}
							})
					.setNeutralButton(R.string.bt_paypal,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									final String donateLink = "https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=V3FFBTRTTV5DN";
									Uri uri = Uri.parse(donateLink);
									startActivity(new Intent(
											Intent.ACTION_VIEW, uri));
									dialog.dismiss();
								}
							})
					.setPositiveButton(R.string.bt_google_play,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									try {
										startActivity(new Intent(
												Intent.ACTION_VIEW,
												Uri.parse("market://details?id="
														+ Preferences.GOOGLE_PLAY_DOWNLOADER)));
									} catch (android.content.ActivityNotFoundException anfe) {
										startActivity(new Intent(
												Intent.ACTION_VIEW,
												Uri.parse("http://play.google.com/store/apps/details?id="
														+ Preferences.GOOGLE_PLAY_DOWNLOADER)));
									}
									dialog.dismiss();
								}
							}).show();
			final SharedPreferences.Editor editor = mPrefs.edit();
			editor.putBoolean(donateScreenShownPref, true);
			editor.commit();
		}
	}

	
	public void onItemSelected(Keygen keygen) {
		if (mTwoPane) {
			final Bundle arguments = new Bundle();
			arguments.putParcelable(NetworkFragment.NETWORK_ID, keygen);
			final NetworkFragment fragment = new NetworkFragment();
			fragment.setArguments(arguments);
			getSupportFragmentManager().beginTransaction()
					.replace(R.id.item_detail_container, fragment).commit();

		} else {
			if (!keygen.isSupported()) {
				Toast.makeText(this, R.string.msg_unspported,
						Toast.LENGTH_SHORT).show();
				return;
			}
			Intent detailIntent = new Intent(this, NetworkActivity.class);
			detailIntent.putExtra(NetworkFragment.NETWORK_ID, keygen);
			startActivity(detailIntent);
		}
	}

	public boolean onCreateOptionsMenu(Menu menu) {
		mOptionsMenu = menu;
		getSupportMenuInflater().inflate(R.menu.networks_list, menu);
		getSupportMenuInflater().inflate(R.menu.preferences, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.manual_input:
			ManualDialogFragment.newInstance(networkMatcher).show(
					getSupportFragmentManager(), "ManualInput");
			return true;
		case R.id.wifi_scan:
			scan();
			return true;
		case R.id.pref:
			startActivity(new Intent(this, Preferences.class));
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		EasyTracker.getInstance().activityStart(this); // Add this method.
		getPrefs();
		if (wifiOn) {
			if (!wifi.setWifiEnabled(true))
				Toast.makeText(this, R.string.msg_wifibroken,
						Toast.LENGTH_SHORT).show();
			else
				wifi_state = true;
		}
		if (autoScan) {
			mHandler.removeCallbacks(mAutoScanTask);
			mHandler.postDelayed(mAutoScanTask, autoScanInterval * 1000L);
		} else
			mHandler.removeCallbacks(mAutoScanTask);
		scan();
	}

	@Override
	public void onStop() {
		try {
			super.onStop();
			EasyTracker.getInstance().activityStop(this); // Add this method.
			unregisterReceiver(scanFinished);
			unregisterReceiver(stateChanged);
			mHandler.removeCallbacks(mAutoScanTask);
		} catch (Exception e) {
		}
	}

	private Menu mOptionsMenu;
	private View mRefreshIndeterminateProgressView = null;

	public void setRefreshActionItemState(boolean refreshing) {
		// On Honeycomb, we can set the state of the refresh button by giving it
		// a custom
		// action view.
		if (mOptionsMenu == null) {
			return;
		}

		final MenuItem refreshItem = mOptionsMenu.findItem(R.id.wifi_scan);
		if (refreshItem != null) {
			if (refreshing) {
				if (mRefreshIndeterminateProgressView == null) {
					LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					mRefreshIndeterminateProgressView = inflater.inflate(
							R.layout.actionbar_indeterminate_progress, null);
				}

				refreshItem.setActionView(mRefreshIndeterminateProgressView);
			} else {
				refreshItem.setActionView(null);
			}
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		getPrefs();
	}

	public void scan() {
		if (!wifi_state && !wifiOn) {
			Toast.makeText(this, R.string.msg_nowifi, Toast.LENGTH_SHORT)
					.show();
			return;
		}
		registerReceiver(scanFinished, new IntentFilter(
				WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
		if (wifi.getWifiState() == WifiManager.WIFI_STATE_ENABLING) {
			registerReceiver(stateChanged, new IntentFilter(
					WifiManager.WIFI_STATE_CHANGED_ACTION));
			Toast.makeText(this, R.string.msg_wifienabling, Toast.LENGTH_SHORT)
					.show();
		} else {
			if (wifi.startScan()) {
				setRefreshActionItemState(true);
			} else
				Toast.makeText(this, R.string.msg_scanfailed,
						Toast.LENGTH_SHORT).show();
		}
	}

	private Runnable mAutoScanTask = new Runnable() {
		public void run() {
			scan();
			mHandler.postDelayed(mAutoScanTask, autoScanInterval * 1000L);
		}
	};

	private boolean wifi_state;
	private boolean wifiOn;
	private boolean autoScan;
	private long autoScanInterval;

	private void getPrefs() {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(getBaseContext());
		wifiOn = prefs.getBoolean(Preferences.wifiOnPref, getResources()
				.getBoolean(R.bool.wifiOnDefault));
		autoScan = prefs.getBoolean(Preferences.autoScanPref, getResources()
				.getBoolean(R.bool.autoScanDefault));
		autoScanInterval = prefs.getInt(Preferences.autoScanIntervalPref,
				getResources().getInteger(R.integer.autoScanIntervalDefault));
	}

	
	public void onScanFinished(Keygen[] networks) {
		setRefreshActionItemState(false);
		if (!welcomeScreenShown) {
			Toast.makeText(this, R.string.msg_welcome_tip, Toast.LENGTH_LONG)
					.show();
			welcomeScreenShown = true;
		}
	}

	
	public void onItemSelected(String mac) {

		ManualDialogFragment.newInstance(networkMatcher, mac).show(
				getSupportFragmentManager(), "ManualInput");
	}

}
