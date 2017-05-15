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
import android.util.Pair;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    public interface NotificationListener {
        void onChange(byte[] value);
    }

    /**
     * Handler for disconnect events
     * @author Eric Tsai
     */
    public interface DisconnectHandler {
        /**
         * Called when the connection with the BLE device has been closed
         */
        void onDisconnect();
        /**
         * Similar to {@link #onDisconnect()} except this variant handles instances where the connection
         * was unexpectedly dropped i.e. not initiated by the API
         * @param status    Status code reported by the btle stack
         */
        void onUnexpectedDisconnect(int status);
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
        if (!pendingGattTasks.isEmpty() && (pendingGattTasks.size() == 1 || ready)) {
            pendingGattTasks.peek().execute();
        }
    }

    private static ScheduledFuture<?> gattTaskTimeoutFuture;
    private static final ScheduledExecutorService taskScheduler = Executors.newScheduledThreadPool(4);
    private static final Map<BluetoothDevice, Map<Pair<UUID, UUID>, NotificationListener>> activeCharNotifyListeners = new ConcurrentHashMap<>();
    private static final Map<BluetoothDevice, BluetoothLeGattServer> activeObjects = new ConcurrentHashMap<>();
    private static final BluetoothGattCallback btleGattCallback= new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            final BluetoothLeGattServer server = activeObjects.get(gatt.getDevice());

            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    if (status != 0) {
                        server.tearDownGatt(true);
                        server.setConnectTaskError(new IllegalStateException(String.format(Locale.US, "Non-zero connection changed status (%s)", status)));
                    } else {
                        gatt.discoverServices();
                    }
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    server.tearDownGatt(true);

                    if (server.connectTaskSource != null && status != 0) {
                        server.setConnectTaskError(new IllegalStateException(String.format(Locale.US, "Non-zero connection changed status (%s)", status)));
                    } else {
                        if (server.disconnectTaskSource == null) {
                            server.dcHandler.onUnexpectedDisconnect(status);
                        } else {
                            server.disconnectTaskSource.setResult(null);
                            server.dcHandler.onDisconnect();
                        }
                    }
                    break;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            executeGattOperation(true);

            final BluetoothLeGattServer server = activeObjects.get(gatt.getDevice());

            server.connTimeoutFuture.cancel(false);
            if (status != 0) {
                server.tearDownGatt(true);
                server.setConnectTaskError(new IllegalStateException(String.format(Locale.US, "Non-zero connection changed status (%s)", status)));
            } else {
                server.connectTaskSource.setResult(server);
                server.connectTaskSource = null;
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            gattTaskTimeoutFuture.cancel(false);

            GattTask task = pendingGattTasks.peek();
            if (status != 0) {
                task.taskCompletionSource().setError(new IllegalStateException("Non-zero status returned (" + status + ")"));
            } else {
                task.taskCompletionSource().setResult(characteristic.getValue());
            }

            activeObjects.get(gatt.getDevice()).gattTaskCompleted();

            pendingGattTasks.poll();
            executeGattOperation(true);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            GattTask task = pendingGattTasks.peek();
            if (status != 0) {
                task.taskCompletionSource().setError(new IllegalStateException("Non-zero status returned (" + status + ")"));
            } else {
                task.taskCompletionSource().setResult(characteristic.getValue());
            }

            activeObjects.get(gatt.getDevice()).gattTaskCompleted();

            pendingGattTasks.poll();
            executeGattOperation(true);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Map<Pair<UUID, UUID>, NotificationListener> listeners = activeCharNotifyListeners.get(gatt.getDevice());
            NotificationListener value;
            Pair<UUID, UUID> key = new Pair<>(characteristic.getService().getUuid(), characteristic.getUuid());

            if (listeners != null && (value = listeners.get(key)) != null) {
                value.onChange(characteristic.getValue());
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            GattTask task = pendingGattTasks.peek();
            if (status != 0) {
                task.taskCompletionSource().setError(new IllegalStateException("Non-zero status returned (" + status + ")"));
            } else {
                task.taskCompletionSource().setResult(null);
            }

            activeObjects.get(gatt.getDevice()).gattTaskCompleted();

            pendingGattTasks.poll();
            executeGattOperation(true);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            gattTaskTimeoutFuture.cancel(false);

            GattTask task = pendingGattTasks.peek();
            if (status != 0) {
                task.taskCompletionSource().setError(new IllegalStateException("Non-zero status returned (" + status + ")"));
            } else {
                task.taskCompletionSource().setResult(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(rssi).array());
            }

            activeObjects.get(gatt.getDevice()).gattTaskCompleted();

            pendingGattTasks.poll();
            executeGattOperation(true);
        }
    };

    public static Task<BluetoothLeGattServer> connect(BluetoothDevice device, Context ctx, boolean autoConnect, long timeout) {
        if (activeObjects.containsKey(device)) {
            return Task.forResult(activeObjects.get(device));
        }

        BluetoothLeGattServer newServerConn = new BluetoothLeGattServer(device, ctx, autoConnect, timeout);
        activeObjects.put(device, newServerConn);
        return newServerConn.connectTaskSource.getTask();
    }

    private ScheduledFuture<?> connTimeoutFuture;
    private DisconnectHandler dcHandler;
    private final AtomicBoolean readyToClose = new AtomicBoolean();
    private final AtomicInteger gattOps = new AtomicInteger();
    private final AtomicReference<BluetoothGatt> gattRef = new AtomicReference<>();
    private TaskCompletionSource<BluetoothLeGattServer> connectTaskSource;
    private TaskCompletionSource<Void> disconnectTaskSource;

    private BluetoothLeGattServer(BluetoothDevice device, Context ctx, boolean autoConnect, final long timeout) {
        connectTaskSource = new TaskCompletionSource<>();

        gattRef.set(device.connectGatt(ctx, autoConnect, btleGattCallback));
        activeObjects.put(device, this);

        connTimeoutFuture = taskScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                tearDownGatt(true);
                setConnectTaskError(new TimeoutException("Did not establish a connection within " + timeout + " milliseconds"));
            }
        }, timeout, TimeUnit.MILLISECONDS);
    }

    private void setConnectTaskError(Exception error) {
        connTimeoutFuture.cancel(false);

        if (connectTaskSource != null) {
            connectTaskSource.setError(error);
            connectTaskSource = null;
        }
    }

    public void onDisconnect(DisconnectHandler handler) {
        dcHandler = handler;
    }

    public boolean isValid() {
        return gattRef.get() != null;
    }

    public boolean serviceExists(UUID gattService) {
        BluetoothGatt gatt = gattRef.get();
        return gatt != null && gatt.getService(gattService) != null;
    }

    public Task<Void> writeCharacteristicAsync(final UUID gattService, final UUID gattChar, final WriteType type, final byte[][] values) {
        // Can use do this in parallel since internally, gatt operations are queued and only executed 1 by 1
        final ArrayList<Task<Void>> tasks = new ArrayList<>();
        for(final byte[] it: values) {
            tasks.add(writeCharacteristicAsync(gattService, gattChar, type, it));
        }

        return Task.whenAll(tasks);
    }

    public Task<Void> writeCharacteristicAsync(final UUID gattService, final UUID gattChar, final WriteType type, final byte[] value) {
        final BluetoothGatt gatt = gattRef.get();

        if (gatt != null) {
            final TaskCompletionSource<byte[]> taskSource = new TaskCompletionSource<>();

            gattOps.incrementAndGet();

            pendingGattTasks.add(new GattTask() {
                @Override
                public void execute() {
                    BluetoothGattService service = gatt.getService(gattService);
                    BluetoothGattCharacteristic androidGattChar = service.getCharacteristic(gattChar);
                    androidGattChar.setWriteType(type == WriteType.WITHOUT_RESPONSE ?
                            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE :
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    );
                    androidGattChar.setValue(value);

                    gatt.writeCharacteristic(androidGattChar);
                }

                @Override
                public TaskCompletionSource<byte[]> taskCompletionSource() {
                    return taskSource;
                }
            });

            executeGattOperation(false);
            return taskSource.getTask().onSuccessTask(new Continuation<byte[], Task<Void>>() {
                @Override
                public Task<Void> then(Task<byte[]> task) throws Exception {
                    return Task.forResult(null);
                }
            });
        }
        return Task.forError(new IllegalStateException("No longer connected to the BTLE gatt server"));
    }

    public Task<byte[][]> readCharacteristicAsync(final UUID[][] gattUuidPairs) {
        // Can use do this in parallel since internally, gatt operations are queued and only executed 1 by 1
        final ArrayList<Task<byte[]>> tasks = new ArrayList<>();
        for(UUID[] it: gattUuidPairs) {
            tasks.add(readCharacteristicAsync(it[0], it[1]));
        }

        return Task.whenAll(tasks).onSuccessTask(new Continuation<Void, Task<byte[][]>>() {
            @Override
            public Task<byte[][]> then(Task<Void> task) throws Exception {
                byte[][] valuesArray = new byte[tasks.size()][];
                for (int i = 0; i < valuesArray.length; i++) {
                    valuesArray[i] = tasks.get(i).getResult();
                }

                return Task.forResult(valuesArray);
            }
        });
    }

    public Task<byte[]> readCharacteristicAsync(final UUID gattService, final UUID gattChar) {
        final BluetoothGatt gatt = gattRef.get();

        if (gatt != null) {
            final TaskCompletionSource<byte[]> taskSource = new TaskCompletionSource<>();

            gattOps.incrementAndGet();

            pendingGattTasks.add(new GattTask() {
                @Override
                public void execute() {
                    gattTaskTimeoutFuture = taskScheduler.schedule(new Runnable() {
                        @Override
                        public void run() {
                            GattTask task = pendingGattTasks.peek();
                            task.taskCompletionSource().setError(new TimeoutException("Did not read gatt characteristic within 250ms"));

                            gattTaskCompleted();

                            pendingGattTasks.poll();
                            executeGattOperation(true);
                        }
                    }, 250L, TimeUnit.MILLISECONDS);
                    gatt.readCharacteristic(gatt.getService(gattService).getCharacteristic(gattChar));
                }

                @Override
                public TaskCompletionSource<byte[]> taskCompletionSource() {
                    return taskSource;
                }
            });

            executeGattOperation(false);
            return taskSource.getTask();
        }
        return Task.forError(new IllegalStateException("No longer connected to the BTLE gatt server"));
    }

    public Task<Integer> readRssiAsync() {
        final BluetoothGatt gatt = gattRef.get();

        if (gatt != null) {
            final TaskCompletionSource<Integer> taskSource = new TaskCompletionSource<>();
            final TaskCompletionSource<byte[]> gattTaskSource = new TaskCompletionSource<>();

            gattOps.incrementAndGet();

            gattTaskSource.getTask().continueWith(new Continuation<byte[], Void>() {
                @Override
                public Void then(Task<byte[]> task) throws Exception {
                    if (task.isFaulted()) {
                        taskSource.setError(task.getError());
                    } else if (task.isCancelled()) {
                        taskSource.setCancelled();
                    } else {
                        taskSource.setResult(ByteBuffer.wrap(task.getResult()).order(ByteOrder.LITTLE_ENDIAN).getInt(0));
                    }
                    return null;
                }
            });
            pendingGattTasks.add(new GattTask() {
                @Override
                public void execute() {
                    gattTaskTimeoutFuture = taskScheduler.schedule(new Runnable() {
                        @Override
                        public void run() {
                            GattTask task = pendingGattTasks.peek();
                            task.taskCompletionSource().setError(new TimeoutException("Did not read RSSI within 250ms"));

                            gattTaskCompleted();

                            pendingGattTasks.poll();
                            executeGattOperation(true);
                        }
                    }, 250L, TimeUnit.MILLISECONDS);

                    gatt.readRemoteRssi();
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

    private Task<Void> editNotifications(final UUID gattService, final UUID gattChar, final NotificationListener listener) {
        final BluetoothGatt gatt = gattRef.get();

        if (gatt != null) {
            BluetoothGattService service = gatt.getService(gattService);
            if (service == null) {
                return Task.forError(new IllegalStateException("Service \'" + gattService.toString() + "\' does not exist"));
            }

            final BluetoothGattCharacteristic androidGattChar = service.getCharacteristic(gattChar);
            if (androidGattChar == null) {
                return Task.forError(new IllegalStateException("Characteristic \'" + gattChar.toString() + "\' does not exist"));
            }

            Task<Void> task;
            int charProps = androidGattChar.getProperties();
            if ((charProps & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                final TaskCompletionSource<byte[]> taskSource = new TaskCompletionSource<>();
                task = taskSource.getTask().onSuccessTask(new Continuation<byte[], Task<Void>>() {
                    @Override
                    public Task<Void> then(Task<byte[]> task) throws Exception {
                        if (listener == null) {
                            Map<Pair<UUID, UUID>, NotificationListener> listeners;
                            if ((listeners = activeCharNotifyListeners.get(gatt.getDevice())) != null) {
                                listeners.remove(new Pair<>(gattService, gattChar));
                            }
                        } else {
                            if (!activeCharNotifyListeners.containsKey(gatt.getDevice())) {
                                activeCharNotifyListeners.put(gatt.getDevice(), new HashMap<Pair<UUID, UUID>, NotificationListener>());
                            }
                            activeCharNotifyListeners.get(gatt.getDevice()).put(new Pair<>(gattService, gattChar), listener);
                        }
                        return Task.forResult(null);
                    }
                });

                gattOps.incrementAndGet();

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
                        return taskSource;
                    }
                });
            } else {
                task = Task.forError(new IllegalStateException(("Characteristic does not have notify property enabled")));
            }

            executeGattOperation(false);
            return task;
        }
        return Task.forError(new IllegalStateException("No longer connected to the BTLE gatt server"));
    }
    public Task<Void> enableNotificationsAsync(UUID gattService, UUID gattChar, final NotificationListener listener) {
        return editNotifications(gattService, gattChar, listener);
    }

    public Task<Void> disableNotificationsAsync(UUID gattService, UUID gattChar) {
        return editNotifications(gattService, gattChar, null);
    }

    public Task<Void> closeAsync() {
        BluetoothGatt gatt = gattRef.get();
        if (gatt != null) {
            if (disconnectTaskSource == null) {
                disconnectTaskSource = new TaskCompletionSource<>();
                if (gattOps.get() != 0) {
                    readyToClose.set(true);
                } else {
                    gatt.disconnect();
                }
            }

            return disconnectTaskSource.getTask();
        }
        return Task.forResult(null);
    }

    private void tearDownGatt(boolean refresh) {
        BluetoothGatt gatt = gattRef.getAndSet(null);
        if (gatt != null) {
            activeObjects.remove(gatt.getDevice());
            activeCharNotifyListeners.remove(gatt.getDevice());

            try {
                if (refresh) {
                    gatt.getClass().getMethod("refresh").invoke(gatt);
                }
            } catch (final Exception e) {
                Log.w("bletoolbox", "Error refreshing gatt services cache", e);
            }

            gatt.close();
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
