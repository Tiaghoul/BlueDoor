package com.example.android.tesefirsttry;

/*
  Created by Tiago Martins on 06/02/2018.
 */
import android.Manifest;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class DeviceScanActivity extends AppCompatActivity {
    private final static String TAG = DeviceScanActivity.class.getSimpleName();

    private final ArrayList<BluetoothDevice> bleDevicesList = new ArrayList<>();
    private Intent gattServiceIntent;
    private TextView emptyTextView;

    private LocationManager mLocationManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private Handler mHandler;
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothDevice bledevice;
    private String connectedBleDeviceAddress;
    private BleService mBluetoothLeService;

    private BluetoothGattCharacteristic mCharactToWrite;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 5000;
    private static final int REQUEST_ENABLE_BT = 1;

    private final static UUID UUID_BLE_AC_SERVICE = UUID.fromString(GattAttributes.BLE_AC_SERVICE);

    private final static UUID UUID_BLE_OPEN_DOOR_CHARACT = UUID.fromString(GattAttributes.BLE_OPEN_DOOR_CHARACT);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ble_activity);
        mHandler = new Handler();

        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED){
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)){
                Toast.makeText(this, "The permission to get BLE location data is required", Toast.LENGTH_LONG).show();
            }else{
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            }
        }

//      Initializing Bluetooth services (manager, adapter, scanner)
        BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

//      Check if Bluetooth is enabled. If not, requests the user to enable it.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        else {
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        }

//      Getting the listview and setting its adapter
        final ListView bleListView = findViewById(R.id.list);
        mLeDeviceListAdapter = new LeDeviceListAdapter(this, bleDevicesList);
        bleListView.setAdapter(mLeDeviceListAdapter);
        emptyTextView = findViewById(R.id.emptyView);
        bleListView.setEmptyView(emptyTextView);

        bleListView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                bledevice = mLeDeviceListAdapter.getItem(position);
                if(connectedBleDeviceAddress == null){
                    gattServiceIntent = new Intent(getApplicationContext(), BleService.class);
                    Log.d(TAG, "CLICKED ON " + bledevice.getName() + " | " + bledevice.getBluetoothClass().getDeviceClass());
                    getApplicationContext().bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
                }
            }
        });

        boolean canScan = checkIfCanScan();
        if(canScan){
            scanLeDevice(true);
        }
    }

    private boolean checkIfCanScan() {
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED){
            Toast.makeText(this, "Location permission is necessary for scan to work", Toast.LENGTH_LONG).show();
            return false;
        }
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            return false;
        }
        if(mBluetoothLeScanner == null){
            emptyTextView.setText(null);
            emptyTextView.setVisibility(View.GONE);
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        }
        return true;

    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(bledevice.getAddress());
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getApplicationContext().unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_scan, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.re_scan_devices) {
            mLeDeviceListAdapter.clear();
            if(checkIfCanScan()){
                scanLeDevice(true);
            }
            return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            emptyTextView.setText("Bluetooth needs to be enabled");
//            finish();
        }
        else if(requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK){
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
            android.os.SystemClock.sleep(200);
            scanLeDevice(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == 1){
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
//                Toast.makeText(this, "Location permission needed for app to work.", Toast.LENGTH_LONG).show();
                emptyTextView.setText("Location permission needed for app to work");
            }
        }
    }

    // Device scan callback.
    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult device) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(!bleDevicesList.contains(device.getDevice())) {
                        String device_name = device.getDevice().getName();
                        if(device_name != null){
                            Log.d(TAG, device_name);
                            mLeDeviceListAdapter.add(device.getDevice());
                            mLeDeviceListAdapter.notifyDataSetChanged();
                        }
                    }
                }
            });
        }
    };

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothLeScanner.stopScan(leScanCallback);
                    Toast.makeText(getApplicationContext(), "Scan ended", Toast.LENGTH_SHORT).show();
                    if (bleDevicesList.isEmpty()) {
                        emptyTextView.setVisibility(View.VISIBLE);
                        emptyTextView.setText("No devices were found");
                    }
                }
            }, SCAN_PERIOD);
            mBluetoothLeScanner.startScan(leScanCallback);
        } else {
            mBluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BleService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            Log.i(TAG, "ble address: " + bledevice.getAddress());
            Boolean connected = mBluetoothLeService.connect(bledevice.getAddress());
            if(connected){
                connectedBleDeviceAddress = bledevice.getAddress();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.i(TAG, "onServiceDisconnected calling disConnect()");
            dealWithDisconnect();
        }
    };


    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if(BleService.ACTION_GATT_CONNECTED.equals(action)){
                Toast.makeText(getApplicationContext(), "CONNECTED", Toast.LENGTH_SHORT).show();
            }
            else if(BleService.ACTION_GATT_DISCONNECTED.equals(action)){
                getApplicationContext().unbindService(mServiceConnection);
                Toast.makeText(getApplicationContext(), "DISCONNECTED", Toast.LENGTH_SHORT).show();
            }
            else if(BleService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)){
                dealWithServices(mBluetoothLeService.getSupportedGattServices());
                openDoor();
            }
            else if(BleService.ACTION_DISCONNECT.equals(action)){
                dealWithDisconnect();
            }
            else if(BleService.ACTION_DATA_AVAILABLE.equals(action)){
                String value = intent.getStringExtra(mBluetoothLeService.EXTRA_DATA);
                Log.d(TAG, "Charact value: " + value);
                Log.d(TAG, "-------------------------------");
            }
        }
    };

    private void dealWithServices(List<BluetoothGattService> supportedGattServices) {
        if(supportedGattServices == null) { return;}
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);

        for(BluetoothGattService gattService : supportedGattServices){
            if(UUID_BLE_AC_SERVICE.equals(gattService.getUuid())){
                String uuid = gattService.getUuid().toString();
                String service_name = GattAttributes.lookup(uuid, unknownServiceString);
                Log.d(TAG, "SERVICE NAME -> " + service_name + " || UUID -> " + uuid);

                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();

                for(BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics){
                    uuid = gattCharacteristic.getUuid().toString();
                    String charac_name = GattAttributes.lookup(uuid,unknownCharaString);

                    final int charaProp = gattCharacteristic.getProperties();

                    if((charaProp & BluetoothGattCharacteristic.PROPERTY_READ) > 0){
                        Log.d(TAG, ": Name -> " + charac_name + " || UUID: " + uuid + " HAS PROPERTY READ");
//                        mBluetoothLeService.readCharacteristic(gattCharacteristic);
                    }
                    if((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0){
                        Log.d(TAG, ": Name -> " + charac_name + " || UUID: " + uuid + " HAS PROPERTY NOTIFY");
//                        mBluetoothLeService.setCharacteristicNotification(gattCharacteristic, true);
                    }
                    if((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0){
                        Log.d(TAG, ": Name -> " + charac_name + " || UUID: " + uuid + " HAS PROPERTY WRITE");
                        if(UUID_BLE_OPEN_DOOR_CHARACT.equals(gattCharacteristic.getUuid())){
                            mCharactToWrite = gattCharacteristic;
                            mCharactToWrite.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                        }
                    }
                }
            }
        }
    }

    private void openDoor(){
        if(mCharactToWrite != null){
            String string_to_send = "open";
            Log.d(TAG, "STRING : " + string_to_send);
            mBluetoothLeService.writeCharacteristic(mCharactToWrite, string_to_send);
        }
    }

    private void dealWithDisconnect(){
        mBluetoothLeService.disconnect();
        stopService(gattServiceIntent);
        connectedBleDeviceAddress = null;
        bledevice = null;
		mCharactToWrite = null;
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BleService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BleService.ACTION_DISCONNECT);
        return intentFilter;
    }
}