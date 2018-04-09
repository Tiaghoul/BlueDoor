package com.example.android.tesefirsttry;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by Tiago Martins on 15/02/2018.
 */

@SuppressWarnings("DefaultFileTemplate")
class LeDeviceListAdapter extends ArrayAdapter<BluetoothDevice> {
    public LeDeviceListAdapter(Activity context, ArrayList<BluetoothDevice> bleDevices){
        super(context, 0, bleDevices);
    }

    @NonNull
    @Override
    public View getView(int i, View view, @NonNull ViewGroup parent) {
        View just_a_view = view;
        if(just_a_view == null){
            just_a_view = LayoutInflater.from(getContext()).inflate(R.layout.ble_list_item, parent, false);
        }

        BluetoothDevice device = getItem(i);
        assert device != null;
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
