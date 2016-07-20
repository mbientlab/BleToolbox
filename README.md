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
    compile 'com.mbientlab.bletoolbox:scanner:0.2.1'
}
```

# Bluetooth LE Scan
The classes in the scanner package scans for Bluetooth LE devices and displays the results in a ListView.  The scanner filters results to only return devices that advertise a valid service UUID and automatically stops scanning after a set duration.

## Embedded Fragment
You can embed the fragment in a layout file.  The activity containing the fragment will need to implement the BleScannerFragment.ScannerCommunicationBus interface, which is used to configure the scan.

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

public class ExampleEmbeddedScannerActivity extends AppCompatActivityi 
        implements ScannerCommunicationBus {
    @Override
    public void onDeviceSelected(BluetoothDevice device) {
        Toast.makeText(this, String.format(Locale.US, "Selected device: %s", 
                device.getAddress()), Toast.LENGTH_LONG).show();
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
}
```
