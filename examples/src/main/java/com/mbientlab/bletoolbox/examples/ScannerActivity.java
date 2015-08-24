/*
 * Copyright 2015 MbientLab Inc. All rights reserved.
 */

package com.mbientlab.bletoolbox.examples;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.mbientlab.bletoolbox.scanner.BleScannerFragment;

import java.util.Locale;
import java.util.UUID;

public class ScannerActivity extends ActionBarActivity implements BleScannerFragment.ScannerListener, BleScannerFragment.ScannerCommunicationBus {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scanner, menu);
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

    @Override
    public void onDeviceSelected(BluetoothDevice device) {
        Toast.makeText(this, String.format(Locale.US, "Selected device: %s", device.getAddress()), Toast.LENGTH_LONG).show();
    }

    @Override
    public UUID[] getFilterServiceUuids() {
        return new UUID[]{UUID.fromString("326a9000-85cb-9195-d9dd-464cfbbae75a")};
    }

    @Override
    public long getScanDuration() {
        return 10000;
    }

    @Override
    public void retrieveFragmentReference(BleScannerFragment scannerFragment) {

    }
}
