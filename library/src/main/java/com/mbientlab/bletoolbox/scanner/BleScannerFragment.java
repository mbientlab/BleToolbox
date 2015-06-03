/*
 * Copyright 2015 MbientLab Inc. All rights reserved.
 */

package com.mbientlab.bletoolbox.scanner;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Bundle;
import android.app.DialogFragment;
import android.os.Handler;
import android.os.ParcelUuid;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;

import com.mbientlab.bletoolbox.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.UUID;

/**
 * A simple {@link DialogFragment} subclass.
 * @author Eric Tsai
 */
public class BleScannerFragment extends DialogFragment {
    public interface ScannerListener {
        public void btDeviceSelected(BluetoothDevice device);
    }

    private static final String KEY_SCAN_PERIOD=
            "com.mbientlab.bletoolbox.scanner.BleDeviceScannerFragment.KEY_SCAN_PERIOD";
    private static final String KEY_SERVICE_UUID=
            "com.mbientlab.bletoolbox.scanner.BleDeviceScannerFragment.KEY_SERVICE_UUID";

    private final static long DEFAULT_SCAN_PERIOD= 5000;
    private ScannedDeviceInfoAdapter scannedDevicesAdapter;

    public static BleScannerFragment newInstance() {
        return BleScannerFragment.newInstance(DEFAULT_SCAN_PERIOD, new UUID[]{});
    }
    public static BleScannerFragment newInstance(long period) {
        return BleScannerFragment.newInstance(period, new UUID[]{});
    }
    public static BleScannerFragment newInstance(UUID[] filterServiceUuids) {
        return BleScannerFragment.newInstance(DEFAULT_SCAN_PERIOD, filterServiceUuids);
    }
    public static BleScannerFragment newInstance(long scanPeriod, UUID[] filterServiceUuids) {
        ParcelUuid[] filterServiceParcelUuids= new ParcelUuid[filterServiceUuids.length];
        for(int i= 0; i < filterServiceUuids.length; i++) {
            filterServiceParcelUuids[i]= new ParcelUuid(filterServiceUuids[i]);
        }

        Bundle bundle= new Bundle();
        bundle.putLong(BleScannerFragment.KEY_SCAN_PERIOD, scanPeriod);
        bundle.putParcelableArray(BleScannerFragment.KEY_SERVICE_UUID, filterServiceParcelUuids);

        BleScannerFragment newFragment= new BleScannerFragment();
        newFragment.setArguments(bundle);

        return newFragment;
    }

    public BleScannerFragment() {
        // Required empty public constructor
    }

    private Button scanControl;
    private ScannerListener listener;
    private Handler scannerHandler;
    private boolean isScanning= false;
    private BluetoothAdapter btAdapter= null;
    private long scanPeriod;
    private HashSet<UUID> filterServiceUuids;

    @Override
    public void onAttach(Activity activity) {
        if (!(activity instanceof ScannerListener)) {
            throw new ClassCastException(activity.toString() + " must implement ScannerListener");
        }

        listener= (ScannerListener) activity;
        btAdapter= ((BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        super.onAttach(activity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        scannedDevicesAdapter= new ScannedDeviceInfoAdapter(getActivity(), R.id.blescan_entry_layout);
        scannedDevicesAdapter.setNotifyOnChange(true);
        scannerHandler= new Handler();
        return inflater.inflate(R.layout.blescan_device_list, container);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        scanPeriod= getArguments().getLong(KEY_SCAN_PERIOD, DEFAULT_SCAN_PERIOD);
        ParcelUuid[] filterParcelUuids= (ParcelUuid[]) getArguments().getParcelableArray(KEY_SERVICE_UUID);

        if (filterParcelUuids != null) {
            filterServiceUuids= new HashSet<>();
            for (ParcelUuid pUuid : filterParcelUuids) {
                filterServiceUuids.add(pUuid.getUuid());
            }
        }

        getDialog().setTitle(R.string.title_scanned_devices);

        ListView scannedDevices= (ListView) view.findViewById(R.id.blescan_devices);
        scannedDevices.setAdapter(scannedDevicesAdapter);
        scannedDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (isScanning) {
                    stopBleScan();
                }
                listener.btDeviceSelected(scannedDevicesAdapter.getItem(i).btDevice);
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

        startBleScan();
    }

    @Override
    public void onDestroyView() {
        stopBleScan();
        super.onDestroyView();
    }

    private final BluetoothAdapter.LeScanCallback scanCallback= new BluetoothAdapter.LeScanCallback() {
        private void foundDevice(final BluetoothDevice btDevice, final int rssi) {
            getActivity().runOnUiThread(new Runnable() {
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

    private void startBleScan() {
        scannedDevicesAdapter.clear();
        isScanning= true;
        scanControl.setText(R.string.ble_scan_cancel);
        scannerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
             stopBleScan();
            }
        }, scanPeriod);
        btAdapter.startLeScan(scanCallback);
    }

    private void stopBleScan() {
        if (isScanning) {
            btAdapter.stopLeScan(scanCallback);

            isScanning= false;
            scanControl.setText(R.string.ble_scan);
        }
    }
}
