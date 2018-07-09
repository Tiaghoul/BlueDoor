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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import de.frank_durr.ecdh_curve25519.ECDHCurve25519;


public class DeviceScanActivity extends AppCompatActivity {
//    private final static String TAG = DeviceScanActivity.class.getSimpleName();
    private final static String TAG = "mytag";
    private final static String secretMessage = "uXUi27eQpTCOaB8DfHgD";
    private static boolean isBonded = false;
    private final ArrayList<BluetoothDevice> bleDevicesList = new ArrayList<>();
    private Intent gattServiceIntent;
    private TextView emptyTextView;
    private ProgressBar progressBar;
    private ProgressBar progBarAfterClick;
    private ListView bleListView;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private Handler mHandler;
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothDevice bleDevice;
    private String connectedBleDeviceAddress;
    private static BleService mBluetoothLeService;
    private BluetoothGattCharacteristic mCharactToWrite;

    private SharedPreferences mPrefs;

	// Stops scanning after 3 seconds.
    private static final long SCAN_PERIOD = 3000;
    private static final int REQUEST_ENABLE_BT = 1;

    private final static UUID UUID_BLE_AC_SERVICE = UUID.fromString(GattAttributes.BLE_AC_SERVICE);
    private final static UUID UUID_BLE_OPEN_DOOR_CHARACT = UUID.fromString(GattAttributes.BLE_OPEN_DOOR_CHARACT);
    private final static UUID UUID_BLE_NOTIFY_CHARACT = UUID.fromString(GattAttributes.BLE_NOTIFY_CHARACT );

//    ECDH key generation values
    private static byte[] shared_secret_key;
    private static byte[] smartphone_secret_key;
	private static int pub_key_n_parts;
    private static String[] pub_key_parts;
    private static int sent_n_pk_parts = 0;
    private static boolean sent_all_pk_parts = false;

    private static String encoded_arduino_pub_key = "";
    private static int rec_n_arduino_key_parts = 0;
    private static boolean received_arduino_pub_key = false;
    private static byte[] arduino_public_key;

    private static int ciphered_msg_n_parts = 0;
    private static int sent_n_cm_parts = 0;
    private static String[] ciphered_msg_parts;
    private static boolean sent_all_cm_parts = false;


    static {
        // Load native library ECDH-Curve25519-Mobile implementing Diffie-Hellman key
        // exchange with elliptic curve 25519.
        try {
            System.loadLibrary("ecdhcurve25519");
            Log.i(TAG, "Loaded ecdhcurve25519 library.");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Error loading ecdhcurve25519 library: " + e.getMessage());
        }
    }


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
        else {mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();}

        mPrefs = getPreferences(MODE_PRIVATE);
		SharedPreferences.Editor prefsEditor = mPrefs.edit();
        prefsEditor.putString("D0:C4:FD:8E:D2:D9", "NANO 1");
        prefsEditor.putString("D7:D6:67:34:1C:59", "NANO 1");
        prefsEditor.apply();

//      Getting the listview and setting its adapter
        emptyTextView = findViewById(R.id.emptyView);
        progressBar = findViewById(R.id.progressBar);
        progBarAfterClick = findViewById(R.id.progBarAfterClick);
        bleListView = findViewById(R.id.list);
        mLeDeviceListAdapter = new LeDeviceListAdapter(this, bleDevicesList);
        bleListView.setAdapter(mLeDeviceListAdapter);

        bleListView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                bleDevice = mLeDeviceListAdapter.getItem(position);
                if(connectedBleDeviceAddress == null){
                    bleListView.setClickable(false);
                    progBarAfterClick.setVisibility(View.VISIBLE);
                    gattServiceIntent = new Intent(getApplicationContext(), BleService.class);
                    Log.d(TAG, "CLICKED ON " + bleDevice.getName());
                    getApplicationContext().bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
                }
                else{
                    Log.d(TAG, "Still disconnecting..");
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
        if (mBluetoothLeService != null && bleDevice != null) {
            mBluetoothLeService.connect(bleDevice.getAddress());
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
            emptyTextView.setText(R.string.ble_enabled);
        }
        else if(requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK){
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
            android.os.SystemClock.sleep(200);
            if(checkIfCanScan()){
                scanLeDevice(true);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == 1){
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
                emptyTextView.setText(R.string.location_needed);
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
                        if(device_name == null){
                            return;
                        }
                        String device_address = device.getDevice().getAddress();
                        if(mPrefs.contains(device_address)){
                            String check_name = mPrefs.getString(device_address, "");
                            if(check_name.equals(device_name)){
                                Log.d(TAG, "Added device: " + device_name);
                                mLeDeviceListAdapter.add(device.getDevice());
                                mLeDeviceListAdapter.notifyDataSetChanged();
                            }
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
                    progressBar.setVisibility(View.GONE);
                    if (bleDevicesList.isEmpty()) {
                        emptyTextView.setVisibility(View.VISIBLE);
                        emptyTextView.setText(R.string.no_devices_found);
                    }
                    else{
                        bleListView.setVisibility(View.VISIBLE);
                    }
                }
            }, SCAN_PERIOD);
            bleListView.setVisibility(View.GONE);
            emptyTextView.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
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
            Boolean connected = mBluetoothLeService.connect(bleDevice.getAddress());
            if(connected){
                connectedBleDeviceAddress = bleDevice.getAddress();
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
            switch (action) {
                case BleService.ACTION_GATT_CONNECTED_BONDED:
                    Toast.makeText(getApplicationContext(), "CONNECTED/BONDED", Toast.LENGTH_SHORT).show();
                    mBluetoothLeService.discoverServices();
                    break;
                case BleService.ACTION_GATT_CONNECTED_NOTBONDED:
                    Toast.makeText(getApplicationContext(), "CONNECTED, BONDING..", Toast.LENGTH_SHORT).show();
                    break;
                case BleService.ACTION_GATT_DISCONNECTED:
                    getApplicationContext().unbindService(mServiceConnection);
                    Toast.makeText(getApplicationContext(), "DISCONNECTED", Toast.LENGTH_SHORT).show();
                    if(bleDevice != null){
                        dealWithDisconnect();
                    }
                    break;
                case BleService.ACTION_GATT_SERVICES_DISCOVERED:
                    dealWithServices(mBluetoothLeService.getSupportedGattServices());
                    if(isBonded){
                        generateECDHkeys();
                        sendPubKey();
                    }
                    else{
                        Log.d(TAG, "NOT BONDED");
                        dealWithDisconnect();
                    }
                    break;
                case BleService.ACTION_DISCONNECT:
                    if(bleDevice != null){
                        dealWithDisconnect();
                    }
                    break;
                case BleService.ACTION_WRITE_SUCCESS:
                    if(!sent_all_pk_parts){
                        sendPubKey();
                    }
					else if(!sent_all_cm_parts && received_arduino_pub_key){
						sendCipheredMsg();
					}
                    break;
                case BleService.ACTION_DATA_AVAILABLE:
                    String value = intent.getStringExtra(BleService.EXTRA_DATA);
                    Log.d(TAG, "Data received ->" + value.substring(0, value.length()-1));
                    if(value.substring(0, value.length()-1).contains("fail")){
                        dealWithDisconnect();
                        return;
                    }
                	if(sent_all_pk_parts && !received_arduino_pub_key){
                        encoded_arduino_pub_key += value;
                        rec_n_arduino_key_parts += 1;
                        if(rec_n_arduino_key_parts == 3){
                            received_arduino_pub_key = true;
                            Log.d(TAG, "ENCODED ARDUINO PUB KEY: " + encoded_arduino_pub_key);
                            getArduinoPublicKey(encoded_arduino_pub_key);
                            generateSharedSecret();
                            encryptAESGCM();
							sendCipheredMsg();
                        }
                    }
                    else if(sent_all_cm_parts){
                        Log.d(TAG, "NOTHING ELSE TO RECEIVE... ");
                        dealWithDisconnect();
					}
                    Log.d(TAG, "-------------------------------");
                    break;
            }
        }
    };

    private void dealWithServices(List<BluetoothGattService> supportedGattServices) {
        if(supportedGattServices == null) { return;}

        for(BluetoothGattService gattService : supportedGattServices){
            if(UUID_BLE_AC_SERVICE.equals(gattService.getUuid())){
                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();

                for(BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics){
                    final int charaProp = gattCharacteristic.getProperties();
                    if((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0){
                        if(UUID_BLE_NOTIFY_CHARACT.equals(gattCharacteristic.getUuid())){
                            mBluetoothLeService.setCharacteristicNotification(gattCharacteristic, true);
                        }
                    }
                    if((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0){
                        if(UUID_BLE_OPEN_DOOR_CHARACT.equals(gattCharacteristic.getUuid())){
                            mCharactToWrite = gattCharacteristic;
                            mCharactToWrite.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                        }
                    }
                }
            }
        }
    }

    private void dealWithDisconnect(){
        Log.d(TAG, "dealing with disconnect......");
        mBluetoothLeService.disconnect();
        stopService(gattServiceIntent);
        connectedBleDeviceAddress = null;
        bleDevice = null;
		mCharactToWrite = null;
        sent_n_pk_parts = 0;
        sent_all_pk_parts = false;
        pub_key_n_parts = 0;
        received_arduino_pub_key = false;
        rec_n_arduino_key_parts = 0;
        encoded_arduino_pub_key = "";
        ciphered_msg_n_parts = 0;
        sent_n_cm_parts = 0;
        sent_all_cm_parts = false;
        bleListView.setClickable(true);
        progBarAfterClick.setVisibility(View.GONE);
//		isBonded = false;
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.ACTION_GATT_CONNECTED_BONDED);
        intentFilter.addAction(BleService.ACTION_GATT_CONNECTED_NOTBONDED);
        intentFilter.addAction(BleService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BleService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BleService.ACTION_DISCONNECT);
        intentFilter.addAction(BleService.ACTION_WRITE_SUCCESS );
        return intentFilter;
    }

    public static class BondReceiver extends BroadcastReceiver{
        private static boolean firstBond = true;
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)){
                if(mBluetoothLeService == null){
                    return;
                }
                int bond_state = mBluetoothLeService.getBondState();
                if(bond_state == 0){
                    firstBond = true;
                    return;
                }
                Log.d(TAG, "new bond state: " + bond_state);
                if(bond_state == BluetoothDevice.BOND_BONDED){
                    if(firstBond){
                        firstBond = false;
					    isBonded = true;
					    mBluetoothLeService.discoverServices();
					}
				}
                else{
                    firstBond = true;
                    isBonded = false;
                }
            }
        }
    }

//    DH WITH ECC KEY GENERATION/SHARING FUNCTIONS

    private static void generateECDHkeys(){
        Log.d(TAG, "generating ECDH KEYS");
        SecureRandom random = new SecureRandom();
        smartphone_secret_key = ECDHCurve25519.generate_secret_key(random);
		byte[] smartphone_public_key = ECDHCurve25519.generate_public_key(smartphone_secret_key);

        String base64 = Base64.encodeToString(smartphone_public_key, Base64.DEFAULT);
        Log.d(TAG, "SIGNED PUB KEY: " + Arrays.toString(smartphone_public_key));
        Log.d(TAG, "ENCODED IN BASE 64: " + base64 +  " || " + base64.length());
        if((base64.length()+1 ) % 20 == 0){
            pub_key_n_parts = base64.length()/ 20;
        }
        else{
            pub_key_n_parts = (base64.length() / 20) + 1;
        }
        base64 = String.valueOf(pub_key_n_parts) + base64;
        pub_key_parts = new String[pub_key_n_parts];
        for(int k = 0; k < pub_key_n_parts-1; k++){
            String substring = base64.substring(20*k, 20*(k+1));
            pub_key_parts[k] = substring;
        }
        String last_part = base64.substring(20*(pub_key_n_parts-1), base64.length());
        if(last_part.endsWith("\n")){
            pub_key_parts[pub_key_n_parts-1] = last_part.substring(0, last_part.length()-1);
        }
        else{
            pub_key_parts[pub_key_n_parts-1] = last_part;
        }
        Log.d(TAG, "ARRAY WITH KEY PARTS: " + Arrays.toString(pub_key_parts));

    }

    private void sendPubKey(){
        Log.d(TAG, "SENDING PART "+ sent_n_pk_parts + ": " + pub_key_parts[sent_n_pk_parts]);
        mBluetoothLeService.writeCharacteristic(mCharactToWrite, pub_key_parts[sent_n_pk_parts]);
        sent_n_pk_parts++;
        if(sent_n_pk_parts == pub_key_n_parts){
            Log.d(TAG, "SENT EVERYTHING!!");
            sent_all_pk_parts = true;
        }
    }

    private static void getArduinoPublicKey(String encoded_pub_key){
        arduino_public_key = Base64.decode(encoded_pub_key.getBytes(), Base64.DEFAULT);
        Log.d(TAG, "ARDUINO PUBLIC KEY: " +  Arrays.toString(arduino_public_key));
    }

    private static void generateSharedSecret(){
        shared_secret_key = ECDHCurve25519.generate_shared_secret(smartphone_secret_key, arduino_public_key);
        Log.d(TAG, "SHARED SECRET: " +  Arrays.toString(shared_secret_key));
    }

    private void sendCipheredMsg(){
        Log.d(TAG, "SENDING CIPHERED PART "+ sent_n_cm_parts + ": " + ciphered_msg_parts[sent_n_cm_parts]);
        mBluetoothLeService.writeCharacteristic(mCharactToWrite, ciphered_msg_parts[sent_n_cm_parts]);
        sent_n_cm_parts++;
        if(sent_n_cm_parts == ciphered_msg_n_parts){
            Log.d(TAG, "SENT EVERYTHING CIPHERED!!");
            sent_all_cm_parts = true;
        }
    }

    private static void encryptAESGCM(){
        SecureRandom secureRandom = new SecureRandom();
        SecretKey secretKey = new SecretKeySpec(shared_secret_key, "AES");
        byte[] initVector = new byte[12];
        secureRandom.nextBytes(initVector);

        try {
            final Cipher cipher = Cipher.getInstance("AES/gcm/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, initVector);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            byte[] secretMessageBytes = secretMessage.getBytes();
            byte[] cipheredMessage = cipher.doFinal(secretMessageBytes);
            ByteBuffer byteBuffer = ByteBuffer.allocate(initVector.length + cipheredMessage.length);
            byteBuffer.put(initVector);
            byteBuffer.put(cipheredMessage);
            byte[] cipheredBuffer = byteBuffer.array();
            String encodedBuffer = Base64.encodeToString(cipheredBuffer, Base64.DEFAULT);
            if((encodedBuffer.length()+1 ) % 20 == 0){
                ciphered_msg_n_parts = encodedBuffer.length()/ 20;
            }
            else{
                ciphered_msg_n_parts = (encodedBuffer.length() / 20) + 1;
            }
            encodedBuffer = String.valueOf(ciphered_msg_n_parts) + encodedBuffer;
            ciphered_msg_parts = new String[ciphered_msg_n_parts];
            for(int i = 0; i < ciphered_msg_n_parts-1; i++){
                String substring = encodedBuffer.substring(20*i, 20*(i+1));
                ciphered_msg_parts[i] = substring;
            }
            String last_part = encodedBuffer.substring(20*(ciphered_msg_n_parts-1), encodedBuffer.length());
            if(last_part.endsWith("\n")){
                ciphered_msg_parts[ciphered_msg_n_parts-1] = last_part.substring(0, last_part.length()-1);
            }
            else{
                ciphered_msg_parts[ciphered_msg_n_parts-1] = last_part;
            }
            Log.d(TAG, "ARRAY WITH CIPHERED PARTS: " + Arrays.toString(ciphered_msg_parts));
        } catch (Exception e){
            Log.d(TAG, "EXCEPTION: ", e);
            e.printStackTrace();
        }
    }
}