package com.example.android.tesefirsttry;

/**
 * Created by Tiago Martins on 06/02/2018.
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
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static android.R.attr.data;


public class DeviceScanActivity extends AppCompatActivity {
    private final static String TAG = DeviceScanActivity.class.getSimpleName();

    private  ArrayList<BluetoothDevice> bleDevicesList = new ArrayList<>();
    private Intent gattServiceIntent;

    private LocationManager mLocationManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private BluetoothManager mBluetoothManager;
    private Handler mHandler;
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothDevice bledevice;
    private String connectedBleDeviceAddress;
    private BleService mBluetoothLeService;

    private BluetoothGattCharacteristic mCharactToWrite;
    private int value_to_send = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 5000;
    private static final int REQUEST_ENABLE_BT = 1;
    private boolean canScan = false;

    public final static UUID UUID_HEART_RATE_CONTROL_POINT =
            UUID.fromString(GattAttributes.HEART_RATE_CONTROL_POINT);

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
//        else{
//            Toast.makeText(this, "Location permissions already granted", Toast.LENGTH_SHORT).show();
//        }

//      Initializing Bluetooth services (manager, adapter, scanner)
        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
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
        final ListView bleListView = (ListView) findViewById(R.id.list);
        mLeDeviceListAdapter = new LeDeviceListAdapter(this, bleDevicesList);
        bleListView.setAdapter(mLeDeviceListAdapter);

        bleListView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                bledevice = mLeDeviceListAdapter.getItem(position);
                if(connectedBleDeviceAddress == null){
                    gattServiceIntent = new Intent(getApplicationContext(), BleService.class);
                    Log.d(TAG, "CLICKED ON " + bledevice.getName() + " | " + bledevice.getBluetoothClass().getDeviceClass());
                    getApplicationContext().bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
                }
                else if(connectedBleDeviceAddress == bledevice.getAddress().toString()){
                    Log.d(TAG, "ALREADY CONNECTED TO " + bledevice.getName() + " | " + bledevice.getBluetoothClass().getDeviceClass());
                }
            }
        });

        Button write_button = (Button) findViewById(R.id.button_write);
        write_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mCharactToWrite != null){
                    mBluetoothLeService.writeCharacteristic(mCharactToWrite, "deeznuts");
                    value_to_send++;
                }
            }
        }

        );

        canScan = checkIfCanScan();
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
            Toast.makeText(this, "Bluetooth needs to be turned on", Toast.LENGTH_LONG).show();
            return false;
        }
        if(mBluetoothLeScanner == null){
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
//            finish();
            return;
        }
        else if(requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK){
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
            android.os.SystemClock.sleep(1000);
            scanLeDevice(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == 1){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
//                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show();
            }
            else{
                Toast.makeText(this, "Location permission needed for app to work!!!", Toast.LENGTH_LONG).show();
//                finish();
            }
        }
    }

    // Device scan callback.
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult device) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(!bleDevicesList.contains(device.getDevice())) {
                        String device_name = device.getDevice().getName();
                        if(device_name != null){
//                            && device_name.contains("pistas")
                            Log.d(TAG, device_name);
                            mLeDeviceListAdapter.add(device.getDevice());
                            mLeDeviceListAdapter.notifyDataSetChanged();
                        }
                        else if (device_name == null){
//                            Log.d(TAG, "NULL NAME");
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
            // Automatically connects to the device upon successful start-up initialization.
            Log.i(TAG, "onServiceConnected calling connect()");
            Log.i(TAG, "ble address: " + bledevice.getAddress());
            Boolean connected = mBluetoothLeService.connect(bledevice.getAddress());
            if(connected){
                connectedBleDeviceAddress = bledevice.getAddress().toString();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.i(TAG, "onServiceDisconnected calling disConnect()");
            mBluetoothLeService.disconnect();
//            getApplicationContext().unbindService(mServiceConnection);
            stopService(gattServiceIntent);
            connectedBleDeviceAddress = null;
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
//                Log.i(TAG, "SERVICES DISCOVERD: " + mBluetoothLeService.getSupportedGattServices().toString());
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            }
            else if(BleService.ACTION_DATA_AVAILABLE.equals(action)){
                String value = intent.getStringExtra(mBluetoothLeService.EXTRA_DATA);
                Log.d(TAG, "Charact value: " + value.toString());
            }
        }
    };

    private void displayGattServices(List<BluetoothGattService> supportedGattServices) {
        if(supportedGattServices == null) { return;}
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);

        for(BluetoothGattService gattService : supportedGattServices){
            Log.d(TAG, "NEW SERVICE: ");
            String uuid = gattService.getUuid().toString();
            String service_name = GattAttributes.lookup(uuid, unknownServiceString);
            Log.d(TAG, "SERVICE NAME -> " + service_name + " || UUID -> " + uuid);

            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();

            for(BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics){
//                Log.d(TAG, "\tCharact from " + service_name + ": " + gattCharacteristic.toString());
                uuid = gattCharacteristic.getUuid().toString();
                String charac_name = GattAttributes.lookup(uuid,unknownCharaString);

                final int charaProp = gattCharacteristic.getProperties();
//                Log.d(TAG, "\t\tCharact Props: " + charaProp);
                if((charaProp & BluetoothGattCharacteristic.PROPERTY_READ) > 0){
                    Log.d(TAG, "\tCharact from " + service_name + ": Name -> " + charac_name + " || UUID: " + uuid + " HAS PROPERTY READ");
                    mBluetoothLeService.readCharacteristic(gattCharacteristic);
                }
                if((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0){
                    Log.d(TAG, "\tCharact from " + service_name + ": Name -> " + charac_name + " || UUID: " + uuid + " HAS PROPERTY NOTIFY");
                    mBluetoothLeService.setCharacteristicNotification(gattCharacteristic, true);
                }
                if((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0){
                    if(UUID_HEART_RATE_CONTROL_POINT.equals(gattCharacteristic.getUuid())){
                        Log.d(TAG, "\tCharact from " + service_name + ": Name -> " + charac_name + " || UUID: " + uuid + " HAS PROPERTY WRITE");
                        mCharactToWrite = gattCharacteristic;
                        mCharactToWrite.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    }
                }
            }
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BleService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}