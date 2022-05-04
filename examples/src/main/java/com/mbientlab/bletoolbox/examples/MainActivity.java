/*
 * Copyright 2015 MbientLab Inc. All rights reserved.
 */

package com.mbientlab.bletoolbox.examples;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.mbientlab.bletoolbox.androidbtle.BluetoothLeGattServer;
import com.mbientlab.bletoolbox.scanner.MacAddressEntryDialogFragment;

import java.util.Arrays;
import java.util.UUID;

import bolts.Continuation;
import bolts.Task;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_BLE_DEVICE = "com.mbientlab.bletoolbox.examples.MainActivity.EXTRA_BLE_DEVICE";
    private final static int REQUEST_ENABLE_BT = 0, SCAN_DEVICE = 1, PERMISSION_REQUEST_BLUETOOTH = 2;

    private static UUID METAWEAR_GATT_SERVICE = UUID.fromString("326a9000-85cb-9195-d9dd-464cfbbae75a"),
            METAWEAR_CMD_CHAR = UUID.fromString("326A9001-85CB-9195-D9DD-464CFBBAE75A");

    private BluetoothAdapter btAdapter = null;
    private BluetoothDevice device;
    private BluetoothLeGattServer gattServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();

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
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT},
                    PERMISSION_REQUEST_BLUETOOTH);
        } else if (!btAdapter.isEnabled()) {
            final Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_CANCELED) {
                    finish();
                }
                break;
            case SCAN_DEVICE:
                if (data != null) {
                    device = data.getParcelableExtra(MainActivity.EXTRA_BLE_DEVICE);

                    BluetoothLeGattServer.connect(device, this, false, 5000L)
                            .onSuccessTask(new Continuation<BluetoothLeGattServer, Task<Integer>>() {
                                @Override
                                public Task<Integer> then(Task<BluetoothLeGattServer> task) throws Exception {
                                    Toast.makeText(MainActivity.this, "Device Connected: " + device.getAddress(), Toast.LENGTH_LONG).show();

                                    gattServer = task.getResult();
                                    return gattServer.readRssiAsync();
                                }
                            }, Task.UI_THREAD_EXECUTOR)
                            .onSuccessTask(new Continuation<Integer, Task<byte[][]>>() {
                                @Override
                                public Task<byte[][]> then(Task<Integer> task) throws Exception {
                                    Log.i("bletoolbox", "RSSI = " + task.getResult());

                                    return gattServer.readCharacteristicAsync(new UUID[][]{
                                            {UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb"), UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")},
                                            {UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb"), UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")},
                                            {UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb"), UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb")},
                                            {UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb"), UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")},
                                            {UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb"), UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb")}
                                    });
                                }
                            })
                            .onSuccessTask(new Continuation<byte[][], Task<Void>>() {
                                @Override
                                public Task<Void> then(Task<byte[][]> task) throws Exception {
                                    for (byte[] it : task.getResult()) {
                                        Log.i("bletoolbox", new String(it));
                                    }
                                    return gattServer.enableNotificationsAsync(METAWEAR_GATT_SERVICE, UUID.fromString("326A9006-85CB-9195-D9DD-464CFBBAE75A"),
                                            new BluetoothLeGattServer.NotificationListener() {
                                                @Override
                                                public void onChange(byte[] value) {
                                                    Log.i("bletoolbox", Arrays.toString(value));
                                                }
                                            });
                                }
                            }).onSuccessTask(new Continuation<Void, Task<Void>>() {
                        @Override
                        public Task<Void> then(Task<Void> task) throws Exception {
                            return gattServer.writeCharacteristicAsync(METAWEAR_GATT_SERVICE, METAWEAR_CMD_CHAR,
                                    BluetoothLeGattServer.WriteType.WITHOUT_RESPONSE, new byte[][]{
                                            {3, 3, 37, 12},
                                            {0x3, 0x4, 0x1},
                                            {0x3, 0x2, 0x1},
                                            {0x3, 0x1, 0x1}
                                    });
                        }
                    }).continueWith(new Continuation<Void, Void>() {
                        @Override
                        public Void then(Task<Void> task) throws Exception {
                            if (task.isFaulted()) {
                                Log.w("bletoolbox", "Error setting up btle device", task.getError());
                            }
                            return null;
                        }
                    });
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

    public void startBleScanActivity(View v) {
        Intent bleScanIntent = new Intent(this, ScannerActivity.class);
        startActivityForResult(bleScanIntent, SCAN_DEVICE);
    }

    public void disconnect(View v) {
        if (gattServer != null) {
            gattServer.onDisconnect(new BluetoothLeGattServer.DisconnectHandler() {
                @Override
                public void onDisconnect() {
                    Log.i("bletoolbox", "Disconnected");
                }

                @Override
                public void onUnexpectedDisconnect(int status) {
                    Log.i("bletoolbox", "Unexpectedly lost connection = " + status);
                }
            });
            gattServer.writeCharacteristicAsync(METAWEAR_GATT_SERVICE, METAWEAR_CMD_CHAR,
                    BluetoothLeGattServer.WriteType.WITHOUT_RESPONSE, new byte[][]{
                            {0x3, 0x1, 0x0},
                            {0x3, 0x2, 0x0},
                            {0x3, 0x4, 0x0},
                            {(byte) 0xfe, 0x6}
                    });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_BLUETOOTH: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (!btAdapter.isEnabled()) {
                        final Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                    }
                }
            }
        }
    }
}
