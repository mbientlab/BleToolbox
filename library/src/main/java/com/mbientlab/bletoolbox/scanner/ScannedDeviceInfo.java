/*
 * Copyright 2015 MbientLab Inc. All rights reserved.
 */

package com.mbientlab.bletoolbox.scanner;

import android.bluetooth.BluetoothDevice;

/**
 * Created by etsai on 6/2/2015.
 */
public class ScannedDeviceInfo {
    public final BluetoothDevice btDevice;
    public int rssi;

    public ScannedDeviceInfo(BluetoothDevice btDevice, int rssi) {
        this.btDevice= btDevice;
        this.rssi= rssi;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj == this) ||
                ((obj instanceof ScannedDeviceInfo) && btDevice.equals(((ScannedDeviceInfo) obj).btDevice));
    }
}
