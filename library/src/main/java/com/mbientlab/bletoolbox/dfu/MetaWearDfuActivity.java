/*******************************************************************************
 * Copyright (c) 2013 Nordic Semiconductor. All Rights Reserved.
 * 
 * The information contained herein is property of Nordic Semiconductor ASA. Terms and conditions of usage are described in detail in NORDIC SEMICONDUCTOR STANDARD SOFTWARE LICENSE AGREEMENT.
 * Licensees are granted free, non-transferable use of the information. NO WARRANTY of ANY KIND is provided. This heading must NOT be removed from the file.
 ******************************************************************************/

/*
 * NORDIC SEMICONDUTOR EXAMPLE CODE AND LICENSE AGREEMENT
 *
 * You are receiving this document because you have obtained example code ("Software") 
 * from Nordic Semiconductor ASA * ("Licensor"). The Software is protected by copyright 
 * laws and international treaties. All intellectual property rights related to the 
 * Software is the property of the Licensor. This document is a license agreement governing 
 * your rights and obligations regarding usage of the Software. Any variation to the terms 
 * of this Agreement shall only be valid if made in writing by the Licensor.
 * 
 * == Scope of license rights ==
 * 
 * You are hereby granted a limited, non-exclusive, perpetual right to use and modify the 
 * Software in order to create your own software. You are entitled to distribute the 
 * Software in original or modified form as part of your own software.
 *
 * If distributing your software in source code form, a copy of this license document shall 
 * follow with the distribution.
 *   
 * The Licensor can at any time terminate your rights under this license agreement.
 * 
 * == Restrictions on license rights ==
 * 
 * You are not allowed to distribute the Software on its own, without incorporating it into 
 * your own software.  
 * 
 * You are not allowed to remove, alter or destroy any proprietary, 
 * trademark or copyright markings or notices placed upon or contained with the Software.
 *     
 * You shall not use Licensor's name or trademarks without Licensor's prior consent.
 * 
 * == Disclaimer of warranties and limitation of liability ==
 * 
 * YOU EXPRESSLY ACKNOWLEDGE AND AGREE THAT USE OF THE SOFTWARE IS AT YOUR OWN RISK AND THAT THE 
 * SOFTWARE IS PROVIDED *AS IS" WITHOUT ANY WARRANTIES OR CONDITIONS WHATSOEVER. NORDIC SEMICONDUCTOR ASA 
 * DOES NOT WARRANT THAT THE FUNCTIONS OF THE SOFTWARE WILL MEET YOUR REQUIREMENTS OR THAT THE 
 * OPERATION OF THE SOFTWARE WILL BE UNINTERRUPTED OR ERROR FREE. YOU ASSUME RESPONSIBILITY FOR 
 * SELECTING THE SOFTWARE TO ACHIEVE YOUR INTENDED RESULTS, AND FOR THE *USE AND THE RESULTS 
 * OBTAINED FROM THE SOFTWARE.
 * 
 * NORDIC SEMICONDUCTOR ASA DISCLAIM ALL WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED 
 * TO WARRANTIES RELATED TO: NON-INFRINGEMENT, LACK OF VIRUSES, ACCURACY OR COMPLETENESS OF RESPONSES 
 * OR RESULTS, IMPLIED  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 * 
 * IN NO EVENT SHALL NORDIC SEMICONDUCTOR ASA BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL OR 
 * CONSEQUENTIAL DAMAGES OR FOR ANY DAMAGES WHATSOEVER (INCLUDING BUT NOT LIMITED TO DAMAGES FOR 
 * LOSS OF BUSINESS PROFITS, BUSINESS INTERRUPTION, LOSS OF BUSINESS INFORMATION, PERSONAL INJURY, 
 * LOSS OF PRIVACY OR OTHER PECUNIARY OR OTHER LOSS WHATSOEVER) ARISING OUT OF USE OR INABILITY TO 
 * USE THE SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 * 
 * REGARDLESS OF THE FORM OF ACTION, NORDIC SEMICONDUCTOR ASA AGGREGATE LIABILITY ARISING OUT OF 
 * OR RELATED TO THIS AGREEMENT SHALL NOT EXCEED THE TOTAL AMOUNT PAYABLE BY YOU UNDER THIS AGREEMENT. 
 * THE FOREGOING LIMITATIONS, EXCLUSIONS AND DISCLAIMERS SHALL APPLY TO THE MAXIMUM EXTENT ALLOWED BY 
 * APPLICABLE LAW.
 * 
 * == Dispute resolution and legal venue ==
 * 
 * Any and all disputes arising out of the rights and obligations in this license agreement shall be 
 * submitted to ordinary court proceedings. You accept the Oslo City Court as legal venue under this agreement.
 * 
 * This license agreement shall be governed by Norwegian law.
 * 
 * == Contact information ==
 * 
 * All requests regarding the Software or the API shall be directed to: 
 * Nordic Semiconductor ASA, P.O. Box 436, Skøyen, 0213 Oslo, Norway.
 * 
 * http://www.nordicsemi.com/eng/About-us/Contact-us
 */
package com.mbientlab.bletoolbox.dfu;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mbientlab.bletoolbox.R;
import com.mbientlab.bletoolbox.dfu.adapter.FileBrowserAppsAdapter;
import com.mbientlab.bletoolbox.dfu.fragment.UploadCancelFragment;
import com.mbientlab.bletoolbox.dfu.settings.SettingsActivity;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;

import no.nordicsemi.android.nrftoolbox.AppHelpFragment;
import no.nordicsemi.android.nrftoolbox.utility.GattError;

/**
 * DfuActivity is the main DFU activity It implements DFUManagerCallbacks to receive callbacks from DFUManager class It implements 
 * DeviceScannerFragment.OnDeviceSelectedListener callback to receive
 * callback when device is selected from scanning dialog The activity supports portrait and landscape orientations
 */
public class MetaWearDfuActivity extends ActionBarActivity implements LoaderCallbacks<Cursor>,
        UploadCancelFragment.CancelFragmetnListener, FirmwareVersionSelector.FirmwareConfiguration {
	public static final String EXTRA_BLE_DEVICE= "com.mbientlab.bletoolbox.dfu.DfuActivity.EXTRA_BLE_DEVICE";
	public static final String EXTRA_MODEL_NUMBER= "com.mbientlab.bletoolbox.dfu.DfuActivity.EXTRA_MODEL_NUMBER";

	private static final String TAG = "DfuActivity";
    private final static String METAWEAR_BUILD= "vanilla";

	private static final String PREFS_DEVICE_NAME = "no.nordicsemi.android.nrftoolbox.dfu.PREFS_DEVICE_NAME";
	private static final String PREFS_FILE_NAME = "no.nordicsemi.android.nrftoolbox.dfu.PREFS_FILE_NAME";
	private static final String PREFS_FILE_SIZE = "no.nordicsemi.android.nrftoolbox.dfu.PREFS_FILE_SIZE";

    private static final String DATA_MODEL_NUMBER= "device_model_number";
	private static final String DATA_FILE_PATH = "file_path";
	private static final String DATA_FILE_STREAM = "file_stream";
	private static final String DATA_STATUS = "status";

	public static final String EXTRA_DEVICE_ADDRESS = "EXTRA_DEVICE_ADDRESS";
	public static final String EXTRA_DEVICE_NAME = "EXTRA_DEVICE_NAME";
	public static final String EXTRA_PROGRESS = "EXTRA_PROGRESS";
	public static final String EXTRA_LOG_URI = "EXTRA_LOG_URI";

	private static final String EXTRA_URI = "uri";

	private static final int SELECT_FILE_REQ = 1;

	private TextView mFileNameView;
	private TextView mFileSizeView;
	private TextView mFileStatusView;
	private TextView mTextPercentage;
	private TextView mTextUploading;
	private ProgressBar mProgressBar;

	private Button mUploadButton;

	private BluetoothDevice mSelectedDevice;
	private String mFilePath, mModelNumber= null;
	private Uri mFileStreamUri;
	private boolean mStatusOk;

	private final BroadcastReceiver mDfuUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			// DFU is in progress or an error occurred 
			final String action = intent.getAction();

			if (MetaWearDfuService.BROADCAST_PROGRESS.equals(action)) {
				final int progress = intent.getIntExtra(MetaWearDfuService.EXTRA_DATA, 0);
				updateProgressBar(progress, false);
			} else if (MetaWearDfuService.BROADCAST_ERROR.equals(action)) {
				final int error = intent.getIntExtra(MetaWearDfuService.EXTRA_DATA, 0);
				updateProgressBar(error, true);

				// We have to wait a bit before canceling notification. This is called before DfuService creates the last notification.
				new Handler().postDelayed(new Runnable() {
					@Override
					public void run() {
						// if this activity is still open and upload process was completed, cancel the notification
						final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
						manager.cancel(MetaWearDfuService.NOTIFICATION_ID);
					}
				}, 200);
			}
		}
	};

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_feature_dfu);
		isBLESupported();
		setGUI();
		
		// restore saved state
		if (savedInstanceState != null) {
			mFilePath = savedInstanceState.getString(DATA_FILE_PATH);
			mFileStreamUri = savedInstanceState.getParcelable(DATA_FILE_STREAM);
			mStatusOk = savedInstanceState.getBoolean(DATA_STATUS);
			mUploadButton.setEnabled(mStatusOk);
            mModelNumber= savedInstanceState.getString(DATA_MODEL_NUMBER);
		} else {
			mModelNumber= getIntent().getStringExtra(EXTRA_MODEL_NUMBER);
		}

		mSelectedDevice= getIntent().getParcelableExtra(EXTRA_BLE_DEVICE);
		selectLatestVersion();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(DATA_FILE_PATH, mFilePath);
		outState.putParcelable(DATA_FILE_STREAM, mFileStreamUri);
		outState.putBoolean(DATA_STATUS, mStatusOk);
        outState.putString(DATA_MODEL_NUMBER, mModelNumber);
	}

	private void setGUI() {
		final ActionBar actionBar = getActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

		TextView mDeviceNameView = (TextView) findViewById(R.id.device_name);
		mFileNameView = (TextView) findViewById(R.id.file_name);
		mFileSizeView = (TextView) findViewById(R.id.file_size);
		mFileStatusView = (TextView) findViewById(R.id.file_status);

		mUploadButton = (Button) findViewById(R.id.action_upload);
		mTextPercentage = (TextView) findViewById(R.id.textviewProgress);
		mTextUploading = (TextView) findViewById(R.id.textviewUploading);
		mProgressBar = (ProgressBar) findViewById(R.id.progressbar_file);

		final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		final boolean uploadInProgress = preferences.getBoolean(MetaWearDfuService.PREFS_DFU_IN_PROGRESS, false);
		if (uploadInProgress) {
			// Restore image file information
			mDeviceNameView.setText(preferences.getString(PREFS_DEVICE_NAME, ""));
			mFileNameView.setText(preferences.getString(PREFS_FILE_NAME, ""));
			mFileSizeView.setText(preferences.getString(PREFS_FILE_SIZE, ""));
			mFileStatusView.setText(R.string.dfu_file_status_ok);
			showProgressBar();
		} else {
			mDeviceNameView.setText("MetaWear");
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		// We are using LocalBroadcastReceiver instead of normal BroadcastReceiver for optimization purposes
		final LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
		broadcastManager.registerReceiver(mDfuUpdateReceiver, makeDfuUpdateIntentFilter());
	}

	@Override
	protected void onPause() {
		super.onPause();

		final LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
		broadcastManager.unregisterReceiver(mDfuUpdateReceiver);
	}

	private static IntentFilter makeDfuUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(MetaWearDfuService.BROADCAST_PROGRESS);
		intentFilter.addAction(MetaWearDfuService.BROADCAST_ERROR);
		intentFilter.addAction(MetaWearDfuService.BROADCAST_LOG);
		return intentFilter;
	}

	private void isBLESupported() {
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			showToast(R.string.error_no_bluetooth_adapter);
			finish();
		}
	}

	private void showToast(final int messageResId) {
		Toast.makeText(this, messageResId, Toast.LENGTH_LONG).show();
	}

	private void showToast(final String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.dfu_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		int id= item.getItemId();

		if (id == android.R.id.home) {
			onBackPressed();
		}
		if (id == R.id.action_about) {
			final AppHelpFragment fragment = AppHelpFragment.getInstance(R.string.dfu_about_text);
			fragment.show(getSupportFragmentManager(), "help_fragment");
		} else if (id == R.id.action_settings) {
			final Intent intent = new Intent(this, SettingsActivity.class);
			startActivity(intent);
		} else if (id == R.id.action_select_latest) {
			selectLatestVersion();
		} else if (id == R.id.action_select_file) {
			onSelectFileClicked();
		} else if (id == R.id.action_select_version) {
			try {
				URL modulesXml= new URL("http://releases.mbientlab.com/metawear/info.json");
				new GetVersionsTask().execute(modulesXml);
			} catch (MalformedURLException e) {
				Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
			}
		}

		return true;
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		if (resultCode != RESULT_OK)
			return;

		switch (requestCode) {
		case SELECT_FILE_REQ:
			// clear previous data
			mFilePath = null;
			mFileStreamUri = null;

			// and read new one
			final Uri uri = data.getData();
			/*
			 * The URI returned from application may be in 'file' or 'content' schema.
			 * 'File' schema allows us to create a File object and read details from if directly.
			 * 
			 * Data from 'Content' schema must be read by Content Provider. To do that we are using a Loader.
			 */
			if (uri.getScheme().equals("file")) {
				// the direct path to the file has been returned
				final String path = uri.getPath();
				final File file = new File(path);
				mFilePath = path;

				mFileNameView.setText(file.getName());
				mFileSizeView.setText(getString(R.string.dfu_file_size_text, file.length()));
				final boolean isHexFile = mStatusOk = MimeTypeMap.getFileExtensionFromUrl(path).equalsIgnoreCase("HEX");
				mFileStatusView.setText(isHexFile ? R.string.dfu_file_status_ok : R.string.dfu_file_status_invalid);
				mUploadButton.setEnabled(mSelectedDevice != null && isHexFile);
			} else if (uri.getScheme().equals("content")) {
				// an Uri has been returned
				mFileStreamUri = uri;
				// if application returned Uri for streaming, let's us it. Does it works?
				// FIXME both Uris works with Google Drive app. Why both? What's the difference? How about other apps like DropBox?
				final Bundle extras = data.getExtras();
				if (extras != null && extras.containsKey(Intent.EXTRA_STREAM))
					mFileStreamUri = extras.getParcelable(Intent.EXTRA_STREAM);

				// file name and size must be obtained from Content Provider
				final Bundle bundle = new Bundle();
				bundle.putParcelable(EXTRA_URI, uri);
				getSupportLoaderManager().restartLoader(0, bundle, this);
			}
			break;
		default:
			break;
		}
	}

	@Override
	public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
		final Uri uri = args.getParcelable(EXTRA_URI);
		/*
		 * Some apps, f.e. Google Drive allow to select file that is not on the device. There is no "_data" column handled by that provider. Let's try to obtain all columns and than check
		 * which columns are present.
		 */
		//final String[] projection = new String[] { MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATA };
		return new CursorLoader(this, uri, null /*all columns, instead of projection*/, null, null, null);
	}

	@Override
	public void onLoaderReset(final Loader<Cursor> loader) {
		mFileNameView.setText(null);
		mFileSizeView.setText(null);
		mFileStreamUri = null;
		mStatusOk = false;

		selectLatestVersion();
	}

	@Override
	public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
		if (data.moveToNext()) {
			/*
			 * Here we have to check the column indexes by name as we have requested for all. The order may be different.
			 */
			final String fileName = data.getString(data.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)/* 0 DISPLAY_NAME */);
			final int fileSize = data.getInt(data.getColumnIndex(MediaStore.MediaColumns.SIZE) /* 1 SIZE */);
			String filePath = null;
			final int dataIndex = data.getColumnIndex(MediaStore.MediaColumns.DATA);
			if (dataIndex != -1)
				filePath = data.getString(dataIndex /*2 DATA */);
			if (!TextUtils.isEmpty(filePath))
				mFilePath = filePath;

			mFileNameView.setText(fileName);
			mFileSizeView.setText(getString(R.string.dfu_file_size_text, fileSize));
			final boolean isHexFile = mStatusOk = MimeTypeMap.getFileExtensionFromUrl(fileName).equalsIgnoreCase("HEX");
			mFileStatusView.setText(isHexFile ? R.string.dfu_file_status_ok : R.string.dfu_file_status_invalid);
			mUploadButton.setEnabled(mSelectedDevice != null && isHexFile);
		}
	}

	/**
	 * Called when the question mark was pressed
	 * 
	 * @param view
	 *            a button that was pressed
	 */
	public void onSelectFileHelpClicked(final View view) {
		new AlertDialog.Builder(this).setTitle(R.string.dfu_help_title).setMessage(R.string.dfu_help_message).setPositiveButton(android.R.string.ok, null).show();
	}

	private void selectLatestVersion() {
		mFileStreamUri= Uri.parse(String.format("http://releases.mbientlab.com/metawear/%s/%s/latest/firmware.hex", mModelNumber, METAWEAR_BUILD));
		new CheckFilesTask().execute(mFileStreamUri);
	}

	private void onSelectFileClicked() {
		final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("application/octet-stream");
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		if (intent.resolveActivity(getPackageManager()) != null) {
			// file browser has been found on the device
			startActivityForResult(intent, SELECT_FILE_REQ);
		} else {
			// there is no any file browser app, let's try to download one
			final View customView = getLayoutInflater().inflate(R.layout.app_file_browser, null);
			final ListView appsList = (ListView) customView.findViewById(android.R.id.list);
			appsList.setAdapter(new FileBrowserAppsAdapter(this));
			appsList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
			appsList.setItemChecked(0, true);
			new AlertDialog.Builder(this).setTitle(R.string.dfu_alert_no_filebrowser_title).setView(customView).setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(final DialogInterface dialog, final int which) {
					dialog.dismiss();
				}
			}).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(final DialogInterface dialog, final int which) {
					final int pos = appsList.getCheckedItemPosition();
					if (pos >= 0) {
						final String query = getResources().getStringArray(R.array.dfu_app_file_browser_action)[pos];
						final Intent storeIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(query));
						startActivity(storeIntent);
					}
				}
			}).show();
		}
	}

    private class CheckFilesTask extends AsyncTask<Uri, Integer, Long> {
	    private Uri uri;
        /* (non-Javadoc)
         * @see android.os.AsyncTask#doInBackground(java.lang.Object[])
         */
        @Override
        protected Long doInBackground(Uri... params) {
            long len;
            HttpResponse response = null;
            uri= params[0];
            try {
                HttpClient httpclient= new DefaultHttpClient();
                HttpGet httpget= new HttpGet(uri.toString());
                response= httpclient.execute(httpget);
                HttpEntity entity= response.getEntity();
                len= entity.getContentLength();
                mStatusOk= true;
            } catch (IOException e) {
                len= -1;
            }
            
            return len;
        }
        
        protected void onPostExecute(final Long result) {
            mFileNameView.setText(uri.toString());
            if (result != -1) {
                mFileSizeView.setText(getString(R.string.dfu_file_size_text, result));
                mFileStatusView.setText(R.string.dfu_file_status_ok);
                mUploadButton.setEnabled(mSelectedDevice != null);
            } else {
                mFileSizeView.setText("N/A");
                mFileStatusView.setText("File unavailable");
                mUploadButton.setEnabled(false);
            }
        }
	    
	}

	private String[] mVersions;
	private class GetVersionsTask extends AsyncTask<URL, Integer, String[]> {
	    @Override
        protected String[] doInBackground(URL... params) {
			try {
				StringBuilder response= new StringBuilder();

				HttpURLConnection conn = (HttpURLConnection) params[0].openConnection();
				if(conn.getResponseCode() == HttpURLConnection.HTTP_OK){
                    BufferedReader in= new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line;

                    while((line= in.readLine()) != null) {
                        response.append(line);
                    }

                    JSONObject releaseInfo= new JSONObject(response.toString());
                    JSONObject buildsInfo= releaseInfo.getJSONObject(mModelNumber);
                    JSONObject versionsInfo= buildsInfo.getJSONObject(METAWEAR_BUILD);

                    Iterator<String> versionKeys= versionsInfo.keys();
                    ArrayList<String> versions= new ArrayList<>();
                    while(versionKeys.hasNext()) {
                        versions.add(versionKeys.next());
                    }

                    String[] versionArray= new String[versions.size()];
                    versions.toArray(versionArray);
                    return versionArray;
				} else {
					return null;
				}
			} catch (IOException e) {
                return null;
			} catch (JSONException e) {
                return null;
            }
        }
	    
	    protected void onPostExecute(final String[] result) {
	        if (result != null) {
	            mVersions = result;
	            
	            final FragmentManager fm= getSupportFragmentManager();
	            final FirmwareVersionSelector dialog= new FirmwareVersionSelector();
	            dialog.show(fm, "firmware_version_selector");
	        }
	    }
	}

    @Override
    public void versionSelected(int index) {
        mFileStreamUri= Uri.parse(String.format("http://releases.mbientlab.com/metawear/%s/%s/%s/firmware.hex", mModelNumber, METAWEAR_BUILD, mVersions[index]));
        new CheckFilesTask().execute(mFileStreamUri);
    }

    @Override
    public String[] availableVersions() {
        return mVersions;
    }

	/**
	 * Callback of UPDATE/CANCEL button on DfuActivity
	 */
	public void onUploadClicked(final View view) {
		final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		final boolean dfuInProgress = preferences.getBoolean(MetaWearDfuService.PREFS_DFU_IN_PROGRESS, false);
		if (dfuInProgress) {
			showUploadCancelDialog();
			return;
		}

		// check whether the selected file is a HEX file (we are just checking the extension)
		if (!mStatusOk) {
			Toast.makeText(this, R.string.dfu_file_status_invalid_message, Toast.LENGTH_LONG).show();
			return;
		}

		// Save current state in order to restore it if user quit the Activity
		final SharedPreferences.Editor editor = preferences.edit();
		editor.putString(PREFS_DEVICE_NAME, mSelectedDevice.getName());
		editor.putString(PREFS_FILE_NAME, mFileNameView.getText().toString());
		editor.putString(PREFS_FILE_SIZE, mFileSizeView.getText().toString());
		editor.commit();

		showProgressBar();

		final Intent service = new Intent(this, MetaWearDfuService.class);
		service.putExtra(MetaWearDfuService.EXTRA_DEVICE_ADDRESS, mSelectedDevice.getAddress());
		service.putExtra(MetaWearDfuService.EXTRA_DEVICE_NAME, mSelectedDevice.getName());
		service.putExtra(MetaWearDfuService.EXTRA_FILE_PATH, mFilePath);
		service.putExtra(MetaWearDfuService.EXTRA_FILE_URI, mFileStreamUri);
		startService(service);
	}

	private void showUploadCancelDialog() {
		final LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
		final Intent pauseAction = new Intent(MetaWearDfuService.BROADCAST_ACTION);
		pauseAction.putExtra(MetaWearDfuService.EXTRA_ACTION, MetaWearDfuService.ACTION_PAUSE);
		manager.sendBroadcast(pauseAction);

		UploadCancelFragment fragment = UploadCancelFragment.getInstance();
		fragment.show(getFragmentManager(), TAG);
	}

	private void showProgressBar() {
		mProgressBar.setVisibility(View.VISIBLE);
		mTextPercentage.setVisibility(View.VISIBLE);
		mTextUploading.setVisibility(View.VISIBLE);
		    
		mUploadButton.setEnabled(true);
		mUploadButton.setText(R.string.dfu_action_upload_cancel);
	}

	private void updateProgressBar(final int progress, final boolean error) {
		switch (progress) {
		case MetaWearDfuService.PROGRESS_CONNECTING:
			mProgressBar.setIndeterminate(true);
			mTextPercentage.setText(R.string.dfu_status_connecting);
			break;
		case MetaWearDfuService.PROGRESS_STARTING:
			mProgressBar.setIndeterminate(true);
			mTextPercentage.setText(R.string.dfu_status_starting);
			break;
		case MetaWearDfuService.PROGRESS_VALIDATING:
			mProgressBar.setIndeterminate(true);
			mTextPercentage.setText(R.string.dfu_status_validating);
			break;
		case MetaWearDfuService.PROGRESS_DISCONNECTING:
			mProgressBar.setIndeterminate(true);
			mTextPercentage.setText(R.string.dfu_status_disconnecting);
			break;
		case MetaWearDfuService.PROGRESS_COMPLETED:
			mTextPercentage.setText(R.string.dfu_status_completed);
			// let's wait a bit until we reconnect to the device again. Mainly because of the notification. When canceled immediately it will be recreated by service again.
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					showFileTransferSuccessMessage();

					// if this activity is still open and upload process was completed, cancel the notification
					final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
					manager.cancel(MetaWearDfuService.NOTIFICATION_ID);
				}
			}, 200);
			break;
		default:
			mProgressBar.setIndeterminate(false);
			if (error) {
				showErrorMessage(progress);
			} else {
				mProgressBar.setProgress(progress);
				mTextPercentage.setText(getString(R.string.dfu_progress, progress));
			}
			break;
		}
	}

	private void showFileTransferSuccessMessage() {
		clearUI();
		showToast("Application has been transfered successfully.");
	}

	@Override
	public void onUploadCanceled() {
		clearUI();
		showToast("Uploading of the application has been canceled.");
	}

	private void showErrorMessage(final int code) {
		clearUI();
		showToast("Upload failed: " + GattError.parse(code) + " (" + code + ")");
	}

	private void clearUI() {
		mProgressBar.setVisibility(View.GONE);
		mTextPercentage.setVisibility(View.GONE);
		mTextUploading.setVisibility(View.GONE);

		mUploadButton.setText(R.string.dfu_action_upload);
	}

	@Override
	public void onBackPressed () {
		Intent result = new Intent();
		result.putExtra(EXTRA_BLE_DEVICE, mSelectedDevice);
		setResult(RESULT_OK, result);
		super.onBackPressed();
	}
}
