# BleToolbox
This project is a utility library that implements general Bluetooth LE operations on the Android platform.  The library will only be updated sporadically to address bugs or add features as they are needed.

# Adding Gradle Dependency
To add the library to your project, first, update the repositories closure to include the MbientLab Ivy Repo in the 
project's build.gradle file.

```groovy
repositories {
    ivy {
        url "http://ivyrep.mbientlab.com"
        layout "gradle"
    }
}
```

Then, add the compile element to the dependencies closure in the module's build.gradle file.
```groovy
dependencies {
    compile 'com.mbientlab:bletoolbox:0.1.0'
}
```

# Bluetooth LE Scan
The classes in the scanner package scans for Bluetooth LE devices and displays the results in a ListView.  The scanner filters results to only return devices that advertise a valid service UUID and automatically stops scanning after a set duration.  You can display the fragment as a popup or embed it into a layout.  

## Popup Dialog
To use the scanner fragment as a popup dialog, your activity must implement the BleScannerFragment.ScannerListener interface.  When you are ready open the dialog, use one of the newInstance methods to instantiate the class, and call [show](http://developer.android.com/reference/android/app/DialogFragment.html#show(android.app.FragmentManager,%20java.lang.String)) method to show the fragment.

```java
import com.mbientlab.bletoolbox.scanner.BleScannerFragment.ScannerListener;

public class ExampleDialogScannerActivity extends AppCompatActivity implements 
        ScannerListener {
    public void openBleScannerFragment() {
        ///< Scan for devices, return only MetaWear devices
        BleScannerFragment.newInstance(new UUID[] {
            UUID.fromString("326a9000-85cb-9195-d9dd-464cfbbae75a")
        }).show(getFragmentManager(), "ble_scanner_fragment");
    }
    
    @Override
    public void onDeviceSelected(BluetoothDevice device) {
        Toast.makeText(this, String.format(Locale.US, "Selected device: %s", device.getAddress()), 
            Toast.LENGTH_LONG).show();
    }
}
```

## Embedded Fragment
Alternatively, you can embed the fragment in a layout file.  When doing this, your activity will also need to implement the BleScannerFragment.ScannerCommunicationBus interface, which is used to configure the scan.

```xml
<?xml version="1.0" encoding="utf-8"?>

<!-- Embedding the scanner fragment in a layout -->
<fragment xmlns:tools="http://schemas.android.com/tools" android:id="@+id/fragment"
    android:name="com.mbientlab.bletoolbox.scanner.BleScannerFragment"
    tools:layout="@layout/blescan_device_list" android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

```java
import android.support.v7.app.AppCompatActivity;

import com.mbientlab.bletoolbox.scanner.BleScannerFragment;
import com.mbientlab.bletoolbox.scanner.BleScannerFragment.*;

public class ExampleEmbeddedScannerActivity extends AppCompatActivity implements 
        ScannerListener, ScannerCommunicationBus {
    private BleScannerFragment scannerFragment= null;
    
    @Override
    public void onDeviceSelected(BluetoothDevice device) {
        Toast.makeText(this, String.format(Locale.US, "Selected device: %s", device.getAddress()), 
            Toast.LENGTH_LONG).show();
    }
    
    @Override
    public UUID[] getFilterServiceUuids() {
        ///< Only return MetaWear boards in the scan
        return new UUID[] {UUID.fromString("326a9000-85cb-9195-d9dd-464cfbbae75a")};
    }

    @Override
    public long getScanDuration() {
        ///< Scan for 10000ms (10 seconds)
        return 10000;
    }

    @Override
    public void retrieveFragmentReference(BleScannerFragment scannerFragment) {
        ///< Hold onto a fragment reference if your activity wants to programmatically 
        ///< start a scan.  For some reason, using findFragmentById always returns null
        this.scannerFragment= scannerFragment;
    }
}
```

# Device Firmware Update
The DFU feature updates the firmware on a Nordic SOC and currently, only supports updating MetaWear boards.  To use the DFU classes, first, update your app's AndroidManifest.xml file to include the DFU service and activities.

```xml
<service android:name="com.mbientlab.bletoolbox.dfu.MetaWearDfuService" />

<activity
    android:name="com.mbientlab.bletoolbox.dfu.MetaWearDfuActivity"
    android:icon="@mipmap/ic_dfu_feature"
    android:label="@string/dfu_feature_title" >
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
    </intent-filter>
</activity>
<activity
    android:name="com.mbientlab.bletoolbox.dfu.settings.SettingsActivity"
    android:label="@string/dfu_settings_title" />
```

Then, start the MetaWear DFU activity with the required parameters: EXTRA_BLE_DEVICE and EXTRA_MODEL_NUMBER.

```java
import android.bluetooth.BluetoothDevice;
import android.support.v7.app.AppCompatActivity;

import com.mbientlab.bletoolbox.dfu.MetaWearDfuActivity;

public class ExampleDfuActivity extends AppCompatActivity {
    private BluetoothDevice device;
    
    public void startDfu(View v) {
        Intent dfuIntent= new Intent(this, MetaWearDfuActivity.class);
        
        ///< Pass in the BluetoothDevice object representing your selected device
        dfuIntent.putExtra(MetaWearDfuActivity.EXTRA_BLE_DEVICE, device);
        ///< MOdel Number characteristic from the Device Information Bluetooth service
        dfuIntent.putExtra(MetaWearDfuActivity.EXTRA_MODEL_NUMBER, "1");
        startActivity(dfuIntent);
    }
}
```
