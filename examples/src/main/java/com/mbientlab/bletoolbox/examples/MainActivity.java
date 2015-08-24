/*
 * Copyright 2015 MbientLab Inc. All rights reserved.
 */

package com.mbientlab.bletoolbox.examples;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.mbientlab.bletoolbox.dfu.MetaWearDfuActivity;
import com.mbientlab.bletoolbox.scanner.BleScannerFragment;

import java.util.Locale;
import java.util.UUID;

public class MainActivity extends ActionBarActivity implements BleScannerFragment.ScannerListener {
    private final static int REQUEST_ENABLE_BT= 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BluetoothAdapter btAdapter= ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();

        if (btAdapter == null) {
            new AlertDialog.Builder(this).setTitle(R.string.error_title)
                    .setMessage(R.string.error_no_bluetooth)
                    .setCancelable(false)
                    .setPositiveButton(R.string.label_ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            MainActivity.this.finish();
                        }
                    })
                    .create()
                    .show();
        } else if (!btAdapter.isEnabled()) {
            final Intent enableIntent= new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_CANCELED) {
                    finish();
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void showBleScan(View v) {
        BleScannerFragment.newInstance(new UUID[]{UUID.fromString("326a9000-85cb-9195-d9dd-464cfbbae75a")})
                .show(getFragmentManager(), "ble_scanner_fragment");
    }

    private BluetoothDevice device;
    public void startDfu(View v) {
        Intent dfuIntent= new Intent(this, MetaWearDfuActivity.class);
        dfuIntent.putExtra(MetaWearDfuActivity.EXTRA_BLE_DEVICE, device);
        dfuIntent.putExtra(MetaWearDfuActivity.EXTRA_DEVICE_NAME, "MetaWear");
        dfuIntent.putExtra(MetaWearDfuActivity.EXTRA_MODEL_NUMBER, "1");
        startActivity(dfuIntent);
    }

    @Override
    public void btDeviceSelected(BluetoothDevice device) {
        this.device= device;
        Toast.makeText(this, String.format(Locale.US, "Selected device: %s", device.getAddress()), Toast.LENGTH_LONG).show();
    }
}
