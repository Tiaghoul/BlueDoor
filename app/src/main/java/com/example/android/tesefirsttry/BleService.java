package com.example.android.tesefirsttry;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.UUID;
import java.util.List;


public class BleService extends Service {
//    private final static String TAG = BleService.class.getSimpleName();
    private final static String TAG = "mytag";

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
	private BluetoothDevice device = null;
    public final static String ACTION_GATT_CONNECTED_BONDED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED_BONDED";
    public final static String ACTION_GATT_CONNECTED_NOTBONDED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED_NOTBONDED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String ACTION_DISCONNECT =
            "com.example.bluetooth.le.ACTION_DISCONNECT";
    public final static String ACTION_WRITE_SUCCESS =
            "com.example.bluetooth.le.ACTION_WRITE_SUCCESS";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    private final static UUID CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final static UUID UUID_BLE_OPEN_DOOR_CHARACT =
            UUID.fromString(GattAttributes.BLE_OPEN_DOOR_CHARACT);
    private final static UUID UUID_BLE_NOTIFY_CHARACT =
            UUID.fromString(GattAttributes.BLE_NOTIFY_CHARACT);


    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction = ACTION_DISCONNECT;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Connected to GATT server.");
                if(getBondState() ==  BluetoothDevice.BOND_BONDED){
                    Log.d(TAG, "already bonded");
                    intentAction = ACTION_GATT_CONNECTED_BONDED;
                }
                else {
                    boolean bonded = device.createBond();
                    if (bonded) {
                        Log.i(TAG, "ENTROU NO CREATEBOND");
                        intentAction = ACTION_GATT_CONNECTED_NOTBONDED;
                    } else {
                        Log.i(TAG, "NAO ENTROU NO CREATEBOND");
                    }
                }
                broadcastUpdate(intentAction);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "onCharacteristicRead com " + characteristic.getUuid());
                broadcastUpdate(characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicChanged com -------> " + characteristic.getUuid());
            broadcastUpdate(characteristic);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if(UUID_BLE_OPEN_DOOR_CHARACT.equals(characteristic.getUuid())){
                if(status == BluetoothGatt.GATT_SUCCESS){
                    Log.d(TAG, "escreveu com sucesso " + characteristic.getUuid());
                    broadcastUpdate(ACTION_WRITE_SUCCESS);
                }
                else {
                    Log.d(TAG, "escreveu sem sucesso " + characteristic.getUuid());
                    broadcastUpdate(ACTION_DISCONNECT);
                }
            }
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(BleService.ACTION_DATA_AVAILABLE);
        if (UUID_BLE_NOTIFY_CHARACT.equals(characteristic.getUuid())) {
            String data_string = new String(characteristic.getValue());
            intent.putExtra(EXTRA_DATA, String.valueOf(data_string));
        } else {
            int final_value1 = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
//            Log.d(TAG, "Data received: " + final_value1);
            intent.putExtra(EXTRA_DATA, String.valueOf(final_value1));
        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BleService getService() {
            return BleService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mBluetoothGatt.close();
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    public boolean initialize() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }


    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.d(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            return mBluetoothGatt.connect();
        }

        device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.d(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        Log.d(TAG, "Device to connect: " + device + "\nTrying to create a new connection.\"");
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        mBluetoothDeviceAddress = address;
        return true;
    }


    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
        mBluetoothGatt.close();
        device = null;
    }


    private void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }


//    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
//        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
//            Log.w(TAG, "BluetoothAdapter not initialized");
//            return;
//        }
//        mBluetoothGatt.readCharacteristic(characteristic);
//    }

//    public void writeCharacteristic(BluetoothGattCharacteristic characteristic, int value){
//        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
//            Log.w(TAG, "BluetoothAdapter not initialized");
//            return;
//        }
//        characteristic.setValue(value, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
//        mBluetoothGatt.writeCharacteristic(characteristic);
//
//    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic, String value){
        Log.d(TAG, "WRITING CHARACT: " + value + " length: " + value.length());
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        byte[] messageBytes = new byte[0];
        try {
            messageBytes = value.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Failed to convert message string to byte array");
        }
        characteristic.setValue(messageBytes);
        boolean did_write = mBluetoothGatt.writeCharacteristic(characteristic);
        while(!did_write){
            did_write = mBluetoothGatt.writeCharacteristic(characteristic);
        }
    }


    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        final BluetoothGattDescriptor descriptor =
                characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);

        if(descriptor != null){
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            boolean connected = mBluetoothGatt.writeDescriptor(descriptor);
            while(!connected){
                connected = mBluetoothGatt.writeDescriptor(descriptor);
            }
        }
        else{
            Log.d(TAG, "Descriptor is null.");
        }
    }


    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;
        return mBluetoothGatt.getServices();
    }

    public int getBondState(){
        if(device != null){
            return device.getBondState();
        }
        return 0;
	}

	public void discoverServices(){
        mBluetoothGatt.discoverServices();
    }
}