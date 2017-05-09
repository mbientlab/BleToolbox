/*
 * Copyright 2014-2015 MbientLab Inc. All rights reserved.
 *
 * IMPORTANT: Your use of this Software is limited to those specific rights granted under the terms of a software
 * license agreement between the user who downloaded the software, his/her employer (which must be your
 * employer) and MbientLab Inc, (the "License").  You may not use this Software unless you agree to abide by the
 * terms of the License which can be found at www.mbientlab.com/terms.  The License limits your use, and you
 * acknowledge, that the Software may be modified, copied, and distributed when used in conjunction with an
 * MbientLab Inc, product.  Other than for the foregoing purpose, you may not use, reproduce, copy, prepare
 * derivative works of, modify, distribute, perform, display or sell this Software and/or its documentation for any
 * purpose.
 *
 * YOU FURTHER ACKNOWLEDGE AND AGREE THAT THE SOFTWARE AND DOCUMENTATION ARE PROVIDED "AS IS" WITHOUT WARRANTY
 * OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING WITHOUT LIMITATION, ANY WARRANTY OF MERCHANTABILITY, TITLE,
 * NON-INFRINGEMENT AND FITNESS FOR A PARTICULAR PURPOSE. IN NO EVENT SHALL MBIENTLAB OR ITS LICENSORS BE LIABLE OR
 * OBLIGATED UNDER CONTRACT, NEGLIGENCE, STRICT LIABILITY, CONTRIBUTION, BREACH OF WARRANTY, OR OTHER LEGAL EQUITABLE
 * THEORY ANY DIRECT OR INDIRECT DAMAGES OR EXPENSES INCLUDING BUT NOT LIMITED TO ANY INCIDENTAL, SPECIAL, INDIRECT,
 * PUNITIVE OR CONSEQUENTIAL DAMAGES, LOST PROFITS OR LOST DATA, COST OF PROCUREMENT OF SUBSTITUTE GOODS, TECHNOLOGY,
 * SERVICES, OR ANY CLAIMS BY THIRD PARTIES (INCLUDING BUT NOT LIMITED TO ANY DEFENSE THEREOF), OR OTHER SIMILAR COSTS.
 *
 * Should you have any questions regarding your right to use this Software, contact MbientLab via email:
 * hello@mbientlab.com.
 */

package com.mbientlab.bletoolbox.androidbtle;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import bolts.Continuation;
import bolts.Task;
import bolts.TaskCompletionSource;

/**
 * Created by eric on 5/9/17.
 */

public final class BluetoothLeGattServer {
    interface NotificationListener {
        void onChange(BtleGattCharacteristic characteristic, byte[] value);
    }

    interface UnexpectedDisconnectHandler {
        void disconnected(int status);
    }

    public enum WriteType {
        WITHOUT_RESPONSE,
        DEFAULT
    }

    private interface GattTask {
        void execute();
        TaskCompletionSource<byte[]> taskCompletionSource();
    }

    private static UUID CHARACTERISTIC_CONFIG= UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final Queue<GattTask> pendingGattTasks = new ConcurrentLinkedQueue<>();
    private static void executeGattOperation(boolean ready) {
        if (pendingGattTasks.isEmpty() && (pendingGattTasks.size() == 1 || ready)) {
            pendingGattTasks.peek().execute();
        }
    }

    private static final Map<BluetoothDevice, NotificationListener> activeCharNotifyListeners = new ConcurrentHashMap<>();
    private static final Map<BluetoothDevice, BluetoothLeGattServer> activeObjects = new ConcurrentHashMap<>();
    private static final BluetoothGattCallback btleGattCallback= new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            final BluetoothLeGattServer server = activeObjects.get(gatt.getDevice());

            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    if (status != 0) {
                        server.destroy();
                        server.connectTaskSource.setError(new RuntimeException(String.format(Locale.US, "Non-zero connection changed status (%s)", status)));
                    } else {
                        gatt.discoverServices();
                    }
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    server.destroy();
                    if (server.disconnectTaskSource != null) {
                        activeCharNotifyListeners.remove(gatt.getDevice());
                        server.disconnectTaskSource.setResult(null);
                    } else if (server.unexpectedDcHandler != null) {
                        server.unexpectedDcHandler.disconnected(status);
                    }
                    break;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            final BluetoothLeGattServer server = activeObjects.get(gatt.getDevice());

            if (status != 0) {
                server.destroy();
                server.connectTaskSource.setError(new RuntimeException(String.format(Locale.US, "Non-zero service discovery status (%s)", status)));
            } else {
                server.connectTaskSource.setResult(server);
            }

            server.gattTaskCompleted();
            executeGattOperation(true);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            GattTask task = pendingGattTasks.poll();
            if (status != 0) {
                task.taskCompletionSource().setError(new IllegalStateException("Non-zero status returned (" + status + ") for reading characteristic " + characteristic.toString()));
            } else {
                task.taskCompletionSource().setResult(characteristic.getValue());
            }

            activeObjects.get(gatt.getDevice()).gattTaskCompleted();
            executeGattOperation(true);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            GattTask task = pendingGattTasks.poll();
            if (status != 0) {
                task.taskCompletionSource().setError(new IllegalStateException("Non-zero status returned (" + status + ") for writing characteristic " + characteristic.toString()));
            } else {
                task.taskCompletionSource().setResult(null);
            }

            activeObjects.get(gatt.getDevice()).gattTaskCompleted();
            executeGattOperation(true);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            NotificationListener listener = activeCharNotifyListeners.get(gatt.getDevice());
            if (listener != null) {
                listener.onChange(new BtleGattCharacteristic(characteristic.getService().getUuid(), characteristic.getUuid()),
                        characteristic.getValue());
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            GattTask task = pendingGattTasks.poll();
            if (status != 0) {
                task.taskCompletionSource().setError(new IllegalStateException("Non-zero status returned (" + status + ") for writing descriptor for " + descriptor.getCharacteristic().toString()));
            } else {
                task.taskCompletionSource().setResult(null);
            }

            activeObjects.get(gatt.getDevice()).gattTaskCompleted();
            executeGattOperation(true);
        }
    };

    public static Task<BluetoothLeGattServer> connect(BluetoothDevice device, Context ctx, boolean autoConnect) {
        if (activeObjects.containsKey(device)) {
            return Task.forResult(activeObjects.get(device));
        }

        BluetoothLeGattServer newServerConn = new BluetoothLeGattServer(device, ctx, autoConnect);
        activeObjects.put(device, newServerConn);
        return newServerConn.connectTaskSource.getTask();
    }

    private UnexpectedDisconnectHandler unexpectedDcHandler;
    private final AtomicBoolean readyToClose = new AtomicBoolean();
    private final AtomicInteger gattOps = new AtomicInteger();
    private final AtomicReference<BluetoothGatt> gattRef = new AtomicReference<>();
    private TaskCompletionSource<BluetoothLeGattServer> connectTaskSource, disconnectTaskSource;

    private BluetoothLeGattServer(BluetoothDevice device, Context ctx, boolean autoConnect) {
        connectTaskSource = new TaskCompletionSource<>();

        gattRef.set(device.connectGatt(ctx, autoConnect, btleGattCallback));
        activeObjects.put(device, this);
    }

    public void onUnexpectedDisconnect(UnexpectedDisconnectHandler handler) {
        unexpectedDcHandler = handler;
    }

    public boolean isValid() {
        return gattRef.get() != null;
    }

    public boolean serviceExists(UUID gattService) {
        BluetoothGatt gatt = gattRef.get();
        return gatt != null && gatt.getService(gattService) != null;
    }

    public Task<Boolean> writeCharacteristic(final BtleGattCharacteristic characteristic, final byte[] value, final WriteType type) {
        final BluetoothGatt gatt = gattRef.get();

        if (gatt != null) {
            final TaskCompletionSource<Boolean> taskSource = new TaskCompletionSource<>();
            final TaskCompletionSource<byte[]> gattTaskSource = new TaskCompletionSource<>();
            gattTaskSource.getTask().continueWith(new Continuation<byte[], Void>() {
                @Override
                public Void then(Task<byte[]> task) throws Exception {
                    if (task.isFaulted()) {
                        taskSource.setError(task.getError());
                    } else if (task.isCancelled()) {
                        taskSource.setError(new CancellationException("Write characteristic task cancelled for " + characteristic.toString()));
                    } else {
                        taskSource.setResult(true);
                    }

                    return null;
                }
            });

            gattOps.incrementAndGet();

            pendingGattTasks.add(new GattTask() {
                @Override
                public void execute() {
                    BluetoothGattService service = gatt.getService(characteristic.serviceUuid);
                    BluetoothGattCharacteristic androidGattChar = service.getCharacteristic(characteristic.uuid);
                    androidGattChar.setWriteType(type == WriteType.WITHOUT_RESPONSE ?
                            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE :
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    );
                    androidGattChar.setValue(value);

                    gatt.writeCharacteristic(androidGattChar);
                }

                @Override
                public TaskCompletionSource<byte[]> taskCompletionSource() {
                    return gattTaskSource;
                }
            });
            executeGattOperation(false);
            return taskSource.getTask();
        }
        return Task.forError(new IllegalStateException("No longer connected to the BTLE gatt server"));
    }

    public Task<byte[]> readCharacteristic(final BtleGattCharacteristic characteristic) {
        final BluetoothGatt gatt = gattRef.get();
        final TaskCompletionSource<byte[]> taskSource = new TaskCompletionSource<>();

        if (gatt != null) {
            gattOps.incrementAndGet();

            pendingGattTasks.add(new GattTask() {
                @Override
                public void execute() {
                    gatt.readCharacteristic(gatt.getService(characteristic.serviceUuid).getCharacteristic(characteristic.uuid));
                }

                @Override
                public TaskCompletionSource<byte[]> taskCompletionSource() {
                    return taskSource;
                }
            });
            executeGattOperation(false);
            throw new UnsupportedOperationException("Not yet implemented");
        }
        return Task.forError(new IllegalStateException("No longer connected to the BTLE gatt server"));
    }

    private Task<Void> editNotifications(final BtleGattCharacteristic characteristic, final NotificationListener listener) {
        final BluetoothGatt gatt = gattRef.get();
        final TaskCompletionSource<Void> taskSource = new TaskCompletionSource<>();

        if (gatt != null) {
            BluetoothGattService service = gatt.getService(characteristic.serviceUuid);
            final BluetoothGattCharacteristic androidGattChar = service.getCharacteristic(characteristic.uuid);

            int charProps = androidGattChar.getProperties();
            if ((charProps & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                gattOps.incrementAndGet();

                final TaskCompletionSource<byte[]> gattTaskSource = new TaskCompletionSource<>();
                gattTaskSource.getTask().continueWith(new Continuation<byte[], Void>() {
                    @Override
                    public Void then(Task<byte[]> task) throws Exception {
                        if (task.isFaulted()) {
                            taskSource.setError(task.getError());
                        } else if (task.isCancelled()) {
                            taskSource.setError(new CancellationException((listener == null ? "Disable notifications task cancelled for " : "Enable notifications task cancelled for ") + characteristic.toString()));
                        } else {
                            if (listener == null) {
                                activeCharNotifyListeners.remove(gatt.getDevice());
                            } else {
                                activeCharNotifyListeners.put(gatt.getDevice(), listener);
                            }
                            taskSource.setResult(null);
                        }
                        return null;
                    }
                });

                pendingGattTasks.add(new GattTask() {
                    @Override
                    public void execute() {
                        gatt.setCharacteristicNotification(androidGattChar, true);
                        BluetoothGattDescriptor descriptor = androidGattChar.getDescriptor(CHARACTERISTIC_CONFIG);
                        descriptor.setValue(listener == null ? BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);

                    }

                    @Override
                    public TaskCompletionSource<byte[]> taskCompletionSource() {
                        return gattTaskSource;
                    }
                });
            } else {
                taskSource.setError(new IllegalStateException(("Characteristic does not have notify property enabled")));
            }

            executeGattOperation(false);
            return taskSource.getTask();
        }
        return Task.forError(new IllegalStateException("No longer connected to the BTLE gatt server"));
    }
    public Task<Void> enableNotifications(final BtleGattCharacteristic characteristic, final NotificationListener listener) {
        return editNotifications(characteristic, listener);
    }

    public Task<Void> disableNotifications(final BtleGattCharacteristic characteristic) {
        return editNotifications(characteristic, null);
    }

    public Task<Void> close() {
        BluetoothGatt gatt = gattRef.getAndSet(null);
        if (gatt != null) {
            if (gattOps.get() != 0) {
                readyToClose.set(true);
            } else {
                disconnectTaskSource = new TaskCompletionSource<>();
                gatt.disconnect();
            }
        }
        return Task.forError(new IllegalStateException("No longer connected to the BTLE gatt server"));
    }

    private void destroy() {
        BluetoothGatt gatt = gattRef.getAndSet(null);
        if (gatt != null) {
            activeObjects.remove(gatt.getDevice());

            try {
                gatt.getClass().getMethod("refresh").invoke(gatt);
            } catch (final Exception e) {
                Log.w("bletoolbox", "Error refreshing gattRef cache", e);
            } finally {
                gatt.close();
            }
        }
    }

    private void gattTaskCompleted() {
        int count = gattOps.decrementAndGet();
        if (count == 0 && readyToClose.get()) {
            Task.delay(1000).continueWith(new Continuation<Void, Void>() {
                @Override
                public Void then(Task<Void> task) throws Exception {
                    BluetoothGatt gatt = gattRef.getAndSet(null);
                    if (gatt != null) {
                        disconnectTaskSource = new TaskCompletionSource<>();
                        gatt.disconnect();
                    }
                    return null;
                }
            });
        }
    }
}
