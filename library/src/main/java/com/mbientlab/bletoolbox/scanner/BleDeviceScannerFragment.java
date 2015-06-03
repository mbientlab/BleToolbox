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
import android.util.Log;
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;

/**
 * A simple {@link DialogFragment} subclass.
 * @author Eric Tsai
 */
public class BleDeviceScannerFragment extends DialogFragment {
    private static final String KEY_SCAN_PERIOD=
            "com.mbientlab.bletoolbox.scanner.BleDeviceScannerFragment.KEY_SCAN_PERIOD";
    private static final String KEY_SERVICE_UUID=
            "com.mbientlab.bletoolbox.scanner.BleDeviceScannerFragment.KEY_SERVICE_UUID";

    public interface ScannerListener {
        public void btDeviceSelected(BluetoothDevice device);
    }

    private final static int RSSI_BAR_LEVELS= 5;
    private final static int RSSI_BAR_SCALE= 100 / RSSI_BAR_LEVELS;
    private final static long DEFAULT_SCAN_PERIOD= 5000;

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

    public static BleDeviceScannerFragment newInstance() {
        return BleDeviceScannerFragment.newInstance(DEFAULT_SCAN_PERIOD, new UUID[] { });
    }

    public static BleDeviceScannerFragment newInstance(long period) {
        return BleDeviceScannerFragment.newInstance(period, new UUID[] { });
    }

    public static BleDeviceScannerFragment newInstance(UUID[] filterServiceUuids) {
        return BleDeviceScannerFragment.newInstance(DEFAULT_SCAN_PERIOD, filterServiceUuids);
    }

    public static BleDeviceScannerFragment newInstance(long scanPeriod, UUID[] filterServiceUuids) {
        ParcelUuid[] filterServiceParcelUuids= new ParcelUuid[filterServiceUuids.length];
        for(int i= 0; i < filterServiceUuids.length; i++) {
            filterServiceParcelUuids[i]= new ParcelUuid(filterServiceUuids[i]);
        }

        Bundle bundle= new Bundle();
        bundle.putLong(BleDeviceScannerFragment.KEY_SCAN_PERIOD, scanPeriod);
        bundle.putParcelableArray(BleDeviceScannerFragment.KEY_SERVICE_UUID, filterServiceParcelUuids);

        BleDeviceScannerFragment newFragment= new BleDeviceScannerFragment();
        newFragment.setArguments(bundle);

        return newFragment;
    }

    public BleDeviceScannerFragment() {
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
        @Override
        public void onLeScan(final BluetoothDevice bluetoothDevice, final int rssi, byte[] scanRecord) {
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
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        scannedDevicesAdapter.update(new ScannedDeviceInfo(bluetoothDevice, rssi));
                                    }
                                });
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
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scannedDevicesAdapter.update(new ScannedDeviceInfo(bluetoothDevice, rssi));
                    }
                });
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
