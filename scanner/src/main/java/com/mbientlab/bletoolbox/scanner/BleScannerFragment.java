/*
 * Copyright 2015 MbientLab Inc. All rights reserved.
 */

package com.mbientlab.bletoolbox.scanner;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;

/**
 * A {@link DialogFragment} that performs a Bluetooth LE scan and displays the results in a selectable list.  To use this
 * fragment, the {@link BluetoothAdapter} must be non-null and enabled and implement the {@link ScannerListener} interface i.e. <br>
 * <blockquote>
 * <pre>
 * public class ExampleActivity extends Activity implements BleScannerFragment.ScannerListener {
 *     &#64;Override
 *     protected void onCreate(Bundle savedInstanceState) {
 *         super.onCreate(savedInstanceState);
 *         BluetoothAdapter btAdapter=
 *                 ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
 *         assert(btAdapter != null && btAdapter.isEnabled());
 *     }
 *
 *     &#64;Override
 *     public void onDeviceSelected(BluetoothDevice device) {
 *         Toast.makeText(this, String.format(Locale.US, "Selected device: %s",
 *                 device.getAddress()), Toast.LENGTH_LONG).show();
 *     }
 * }
 * </pre>
 * </blockquote>
 * @author Eric Tsai
 */
public class BleScannerFragment extends DialogFragment {
    private static final int REQUEST_ENABLE_BT = 1;

    /**
     * Event listener for the {@link BleScannerFragment}
     * @author Eric Tsai
     */
    public interface ScannerListener {
        /**
         * Called when the user has selected a Bluetooth device from the device list
         * @param device Device the user selected
         */
        void onDeviceSelected(BluetoothDevice device);
    }

    /**
     * Bridge to transfer settings from a parent activity to the fragment.  This interface is only needed if the
     * fragment is instantiated directly from the layout file rather than calling DialogFragment.show()
     * @author Eric Tsai
     */
    public interface ScannerCommunicationBus {
        /**
         * Retrieve an array of allowed service UUIDs.  If no filtering should be done, return null.
         * @return Service UUIDs to scan for
         */
        UUID[] getFilterServiceUuids();

        /**
         * Retrieve how long to scan for Bluetooth LE devices
         * @return Bluetooth LE scan duration, in milliseconds
         */
        long getScanDuration();

        /**
         * Send a reference of this fragment to the calling activity
         * @param scannerFragment Reference to this class
         */
        void retrieveFragmentReference(BleScannerFragment scannerFragment);
    }

    private static final String KEY_SCAN_PERIOD=
            "com.mbientlab.bletoolbox.scanner.BleDeviceScannerFragment.KEY_SCAN_PERIOD";
    private static final String KEY_SERVICE_UUID=
            "com.mbientlab.bletoolbox.scanner.BleDeviceScannerFragment.KEY_SERVICE_UUID";

    private final static long DEFAULT_SCAN_PERIOD= 5000L;

    /**
     * Creates an instance of the fragment with default configuration
     * @return Scanner fragment that scans for 5000ms and shows all discovered devices
     */
    public static BleScannerFragment newInstance() {
        return BleScannerFragment.newInstance(DEFAULT_SCAN_PERIOD, new UUID[]{});
    }
    /**
     * Creates an instance of the fragment with a user specified scanning duration.
     * @param scanDuration How long to scan for Bluetooth LE devices
     * @return Scanner fragment that scans for a user specified duration and shows all discovered devices
     */
    public static BleScannerFragment newInstance(long scanDuration) {
        return BleScannerFragment.newInstance(scanDuration, new UUID[]{});
    }
    /**
     * Creates an instance of the fragment that only shows devices with one of the desired service UUIDs
     * @param filterServiceUuids Array of allowed service UUIDs
     * @return Scanner fragment that scans for 5000ms and filters the results
     */
    public static BleScannerFragment newInstance(UUID[] filterServiceUuids) {
        return BleScannerFragment.newInstance(DEFAULT_SCAN_PERIOD, filterServiceUuids);
    }
    /**
     * Creates an instance of the fragment that only shows devices with one of the desired service UUIDs found within
     * the given scanning duration.
     * @param scanDuration How long to scan for Bluetooth LE devices
     * @param filterServiceUuids Array of allowed service UUIDs
     * @return Scanner fragment that scans for a user specified duration and filters the results
     */
    public static BleScannerFragment newInstance(long scanDuration, UUID[] filterServiceUuids) {
        ParcelUuid[] filterServiceParcelUuids= new ParcelUuid[filterServiceUuids.length];
        for(int i= 0; i < filterServiceUuids.length; i++) {
            filterServiceParcelUuids[i]= new ParcelUuid(filterServiceUuids[i]);
        }

        Bundle bundle= new Bundle();
        bundle.putLong(BleScannerFragment.KEY_SCAN_PERIOD, scanDuration);
        bundle.putParcelableArray(BleScannerFragment.KEY_SERVICE_UUID, filterServiceParcelUuids);

        BleScannerFragment newFragment= new BleScannerFragment();
        newFragment.setArguments(bundle);

        return newFragment;
    }

    /**
     * Required empty public constructor.  Users should use the static methods for instantiating this class.
     * @see #newInstance()
     * @see #newInstance(long)
     * @see #newInstance(java.util.UUID[])
     * @see #newInstance(long, java.util.UUID[])
     */
    public BleScannerFragment() {
        // Required empty public constructor
    }

    private ScannedDeviceInfoAdapter scannedDevicesAdapter;
    private Button scanControl;
    private Handler mHandler;
    private boolean isScanning= false;
    private BluetoothAdapter btAdapter= null;
    private long scanDuration;
    private HashSet<UUID> filterServiceUuids;
    private ArrayList<ScanFilter> api21ScanFilters;
    private boolean isScanReady;

    private ScannerListener listener;
    private ScannerCommunicationBus commBus= null;

    @Override
    public void onAttach(final Activity activity) {
        if (!(activity instanceof ScannerListener)) {
            throw new ClassCastException(String.format(Locale.US, "%s %s", activity.toString(),
                    activity.getString(R.string.error_scanner_listener)));
        }

        listener= (ScannerListener) activity;
        btAdapter= ((BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();

        if (btAdapter == null) {
            new AlertDialog.Builder(activity).setTitle(R.string.dialog_title_error)
                    .setMessage(R.string.error_no_bluetooth_adapter)
                    .setCancelable(false)
                    .setPositiveButton(R.string.no_bt_dialog_ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            activity.finish();
                        }
                    })
                    .create()
                    .show();
        } else if (!btAdapter.isEnabled()) {
            final Intent enableIntent= new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            isScanReady = true;
        }

        if (activity instanceof ScannerCommunicationBus) {
            commBus= (ScannerCommunicationBus) activity;
            commBus.retrieveFragmentReference(this);
        }


        super.onAttach(activity);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_CANCELED) {
                    getActivity().finish();
                } else {
                    startBleScan();
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        scannedDevicesAdapter= new ScannedDeviceInfoAdapter(getActivity(), R.id.blescan_entry_layout);
        scannedDevicesAdapter.setNotifyOnChange(true);
        mHandler = new Handler();
        return inflater.inflate(R.layout.blescan_device_list, container);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            filterServiceUuids = new HashSet<>();
        } else {
            api21ScanFilters= new ArrayList<>();
        }

        if (commBus == null) {
            scanDuration = getArguments().getLong(KEY_SCAN_PERIOD, DEFAULT_SCAN_PERIOD);
            ParcelUuid[] filterParcelUuids= (ParcelUuid[]) getArguments().getParcelableArray(KEY_SERVICE_UUID);

            if (filterParcelUuids != null) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    for (ParcelUuid pUuid : filterParcelUuids) {
                        filterServiceUuids.add(pUuid.getUuid());
                    }
                } else {
                    for (ParcelUuid pUuid : filterParcelUuids) {
                        api21ScanFilters.add(new ScanFilter.Builder().setServiceUuid(pUuid).build());
                    }
                }
            }
        } else {
            scanDuration = commBus.getScanDuration();
            UUID[] filterUuids= commBus.getFilterServiceUuids();

            if (filterUuids != null) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    filterServiceUuids.addAll(Arrays.asList(filterUuids));
                } else {
                    for (UUID uuid : filterUuids) {
                        api21ScanFilters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(uuid)).build());
                    }
                }
            }
        }

        if (getDialog() != null) {
            getDialog().setTitle(R.string.title_scanned_devices);
        }

        ListView scannedDevices= (ListView) view.findViewById(R.id.blescan_devices);
        scannedDevices.setAdapter(scannedDevicesAdapter);
        scannedDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                stopBleScan();

                listener.onDeviceSelected(scannedDevicesAdapter.getItem(i).btDevice);
                dismiss();
            }
        });

        scanControl= (Button) view.findViewById(R.id.blescan_control);
        scanControl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isScanning) {
                    stopBleScan();
                } else {
                    startBleScan();
                }
            }
        });

        if (isScanReady) {
            startBleScan();
        }
    }

    @Override
    public void onDestroyView() {
        stopBleScan();
        super.onDestroyView();
    }

    private BluetoothAdapter.LeScanCallback deprecatedScanCallback= null;
    private ScanCallback api21ScallCallback= null;

    @TargetApi(22)
    public void startBleScan() {
        scannedDevicesAdapter.clear();
        isScanning= true;
        scanControl.setText(R.string.ble_scan_cancel);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopBleScan();
            }
        }, scanDuration);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            ///< TODO: Use startScan method instead from API 21
            deprecatedScanCallback= new BluetoothAdapter.LeScanCallback() {
                private void foundDevice(final BluetoothDevice btDevice, final int rssi) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            scannedDevicesAdapter.update(new ScannedDeviceInfo(btDevice, rssi));
                        }
                    });
                }
                @Override
                public void onLeScan(BluetoothDevice bluetoothDevice, int rssi, byte[] scanRecord) {
                    ///< Service UUID parsing code taking from stack overflow= http://stackoverflow.com/a/24539704

                    ByteBuffer buffer= ByteBuffer.wrap(scanRecord).order(ByteOrder.LITTLE_ENDIAN);
                    boolean stop= false;
                    while (!stop && buffer.remaining() > 2) {
                        byte length = buffer.get();
                        if (length == 0) break;

                        byte type = buffer.get();
                        switch (type) {
                            case 0x02: // Partial list of 16-bit UUIDs
                            case 0x03: // Complete list of 16-bit UUIDs
                                while (length >= 2) {
                                    UUID serviceUUID= UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", buffer.getShort()));
                                    stop= filterServiceUuids.isEmpty() || filterServiceUuids.contains(serviceUUID);
                                    if (stop) {
                                        foundDevice(bluetoothDevice, rssi);
                                    }

                                    length -= 2;
                                }
                                break;

                            case 0x06: // Partial list of 128-bit UUIDs
                            case 0x07: // Complete list of 128-bit UUIDs
                                while (!stop && length >= 16) {
                                    long lsb= buffer.getLong(), msb= buffer.getLong();
                                    stop= filterServiceUuids.isEmpty() || filterServiceUuids.contains(new UUID(msb, lsb));
                                    if (stop) {
                                        foundDevice(bluetoothDevice, rssi);
                                    }
                                    length -= 16;
                                }
                                break;

                            default:
                                buffer.position(buffer.position() + length - 1);
                                break;
                        }
                    }

                    if (!stop && filterServiceUuids.isEmpty()) {
                        foundDevice(bluetoothDevice, rssi);
                    }
                }
            };
            btAdapter.startLeScan(deprecatedScanCallback);
        } else {
            api21ScallCallback= new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, final ScanResult result) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            scannedDevicesAdapter.update(new ScannedDeviceInfo(result.getDevice(), result.getRssi()));
                        }
                    });

                    super.onScanResult(callbackType, result);
                }
            };
            btAdapter.getBluetoothLeScanner().startScan(api21ScanFilters, new ScanSettings.Builder().build(), api21ScallCallback);
        }
    }

    public void stopBleScan() {
        if (isScanning) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                ///< TODO: Use stopScan method instead from API 21
                btAdapter.stopLeScan(deprecatedScanCallback);
            } else {
                btAdapter.getBluetoothLeScanner().stopScan(api21ScallCallback);
            }

            isScanning= false;
            scanControl.setText(R.string.ble_scan);
        }
    }
}
