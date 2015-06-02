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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.mbientlab.bletoolbox.R;

import java.util.Locale;

/**
 * A simple {@link DialogFragment} subclass.
 * @author Eric Tsai
 */
public class BleDeviceScannerFragment extends DialogFragment {
    public static final String KEY_SCAN_PERIOD=
            "com.mbientlab.bletoolbox.scanner.BleDeviceScannerFragment.KEY_SCAN_PERIOD";

    public interface ScannerListener {
        public void btDeviceSelected(BluetoothDevice device);
    }

    private final static int RSSI_BAR_LEVELS= 5;
    private final static int RSSI_BAR_SCALE= 100 / RSSI_BAR_LEVELS;
    private final static long DEFAULT_SCAN_PERIOD= 10000;

    private ScannedDeviceInfoAdapter scannedDevicesAdapter;
    private class ScannedDeviceInfoAdapter extends ArrayAdapter<ScannedDeviceInfo> {
        public ScannedDeviceInfoAdapter(Context context, int resource) {
            super(context, resource);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;

            if (convertView == null) {
                convertView= LayoutInflater.from(getActivity()).inflate(R.layout.blescan_entry, parent, false);

                viewHolder= new ViewHolder();
                viewHolder.deviceAddress= (TextView) convertView.findViewById(R.id.ble_mac_address);
                viewHolder.deviceName= (TextView) convertView.findViewById(R.id.ble_device);
                viewHolder.deviceRSSI= (TextView) convertView.findViewById(R.id.ble_rssi_value);
                viewHolder.rssiChart= (ImageView) convertView.findViewById(R.id.ble_rssi_png);

                convertView.setTag(viewHolder);
            } else {
                viewHolder= (ViewHolder) convertView.getTag();
            }

            ScannedDeviceInfo deviceInfo= getItem(position);
            final String deviceName= deviceInfo.btDevice.getName();

            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.label_unknown_device);
            viewHolder.deviceAddress.setText(deviceInfo.btDevice.getAddress());
            viewHolder.deviceRSSI.setText(String.format(Locale.US, "%d dBm", deviceInfo.rssi));
            viewHolder.rssiChart.setImageLevel(Math.min(RSSI_BAR_LEVELS - 1, (127 + deviceInfo.rssi + 5) / RSSI_BAR_SCALE));

            return convertView;
        }

        private class ViewHolder {
            public TextView deviceAddress;
            public TextView deviceName;
            public TextView deviceRSSI;
            public ImageView rssiChart;
        }

        public void update(ScannedDeviceInfo newInfo) {
            int pos= getPosition(newInfo);
            if (pos == -1) {
                add(newInfo);
            } else {
                getItem(pos).rssi= newInfo.rssi;
                notifyDataSetChanged();
            }
        }
    };

    public BleDeviceScannerFragment() {
        // Required empty public constructor
    }

    private Button scanControl;
    private ScannerListener listener;
    private Handler scannerHandler;
    private boolean isScanning= false;
    private BluetoothAdapter btAdapter= null;
    private long scanPeriod;

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

    private BluetoothAdapter.LeScanCallback scanCallback= new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice bluetoothDevice, final int rssi, byte[] scanRecord) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ScannedDeviceInfo temp= new ScannedDeviceInfo(bluetoothDevice, rssi);
                    scannedDevicesAdapter.update(temp);
                }
            });
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
