package com.example.android.tesefirsttry;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import static android.R.attr.permission;
import static com.example.android.tesefirsttry.R.id.ble_name;

/**
 * Created by Tiago Martins on 15/02/2018.
 */

public class LeDeviceListAdapter extends ArrayAdapter<BluetoothDevice> {
//    public Context context;
//    public ArrayList<BluetoothDevice> mLeDevices;
//    public LayoutInflater mInflator;

    public LeDeviceListAdapter(Activity context, ArrayList<BluetoothDevice> bleDevices){
        super(context, 0, bleDevices);
    }

    @Override
    public View getView(int i, View view, ViewGroup parent) {
        View just_a_view = view;
        if(just_a_view == null){
            just_a_view = LayoutInflater.from(getContext()).inflate(R.layout.ble_list_item, parent, false);
        }

        BluetoothDevice device = getItem(i);
        final String deviceName = device.getName();
        TextView ble_name = just_a_view.findViewById(R.id.ble_name);
        TextView ble_address = just_a_view.findViewById(R.id.ble_address);

        if (deviceName != null && deviceName.length() > 0) {
            ble_name.setText(deviceName);
        }
        else {
            ble_name.setText(R.string.unknown_device);
        }
        ble_address.setText(device.getAddress());
        return just_a_view;
    }
}
