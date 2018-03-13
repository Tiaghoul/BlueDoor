package com.example.android.tesefirsttry;

import java.util.HashMap;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class GattAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

//	    MY SERVICE/CHARACTERISTICS:
	public static String BLE_AC_SERVICE = "713d0000-503e-4c75-ba94-3148f18d941e";
	public static String BLE_OPEN_DOOR_CHARACT = "713d0002-503e-4c75-ba94-3148f18d941e";
	public static String BLE_READ_CHARACT = "713d0003-503e-4c75-ba94-3148f18d941e";
	public static String BLE_NOTIFY_CHARACT = "713d0004-503e-4c75-ba94-3148f18d941e";

    static {
        // Sample Services.
        // Sample Characteristics.
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
//        MY SERVICE/CHARACTERISTICS:
		attributes.put(BLE_AC_SERVICE, "BLE ACCESS CONTROL SERVICE");
		attributes.put(BLE_OPEN_DOOR_CHARACT, "BLE OPEN DOOR CHARACT");
		attributes.put(BLE_READ_CHARACT, "BLE READ CHARACT");
		attributes.put(BLE_NOTIFY_CHARACT, "BLE NOTIFICATION CHARACT");

    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}