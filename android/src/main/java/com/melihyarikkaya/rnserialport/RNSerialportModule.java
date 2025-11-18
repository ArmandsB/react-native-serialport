package com.melihyarikkaya.rnserialport;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Base64;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.LifecycleEventListener;

import com.google.common.primitives.UnsignedBytes;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class RNSerialportModule extends NativeRNSerialportSpec implements LifecycleEventListener {

    private final ReactApplicationContext reactContext;

    private final String ACTION_USB_READY = "com.felhr.connectivityservices.USB_READY";
    private final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    private final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
    private final String ACTION_NO_USB = "com.felhr.usbservice.NO_USB";
    private final String ACTION_USB_PERMISSION_GRANTED = "com.felhr.usbservice.USB_PERMISSION_GRANTED";
    private final String ACTION_USB_PERMISSION_NOT_GRANTED = "com.felhr.usbservice.USB_PERMISSION_NOT_GRANTED";
    private final String ACTION_USB_DISCONNECTED = "com.felhr.usbservice.USB_DISCONNECTED";
    private final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private final String ACTION_USB_NOT_OPENED = "com.melihyarikkaya.rnserialport.USB_NOT_OPENED";
    private final String ACTION_USB_CONNECT = "com.melihyarikkaya.rnserialport.USB_CONNECT";

    //react-native events
    private final String onErrorEvent              = "onError";
    private final String onConnectedEvent          = "onConnected";
    private final String onDisconnectedEvent       = "onDisconnected";
    private final String onDeviceAttachedEvent     = "onDeviceAttached";
    private final String onDeviceDetachedEvent     = "onDeviceDetached";
    private final String onServiceStarted          = "onServiceStarted";
    private final String onServiceStopped          = "onServiceStopped";
    private final String onReadDataFromPort        = "onReadDataFromPort";
    private final String onUsbPermissionGranted    = "onUsbPermissionGranted";

    //SUPPORTED DRIVER LIST

    private List<String> driverList;

    private UsbManager usbManager;
    private UsbDevice device;
    private UsbDeviceConnection connection;
    private UsbSerialDevice serialPort;
    private boolean serialPortConnected;

    //Connection Settings
    private int DATA_BIT     = UsbSerialInterface.DATA_BITS_8;
    private int STOP_BIT     = UsbSerialInterface.STOP_BITS_1;
    private int PARITY       =  UsbSerialInterface.PARITY_NONE;
    private int FLOW_CONTROL = UsbSerialInterface.FLOW_CONTROL_OFF;
    private int BAUD_RATE = 9600;
    private int readBufferSize = 16 * 1024;


    private boolean autoConnect = false;
    private String autoConnectDeviceName;
    private int autoConnectBaudRate = 9600;
    private int portInterface = -1;
    private int returnedDataType = Definitions.RETURNED_DATA_TYPE_INTARRAY;
    private String driver = "AUTO";


    private boolean usbServiceStarted = false;


    public RNSerialportModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        reactContext.addLifecycleEventListener(this);
        fillDriverList();
    }

    private void fillDriverList() {
        driverList = new ArrayList<>();
        driverList.add("ftdi");
        driverList.add("cp210x");
        driverList.add("pl2303");
        driverList.add("ch34x");
        driverList.add("cdc");
    }

    private void eventEmit(String eventName, @Nullable Object data) {
        try {
            if (reactContext.hasActiveCatalystInstance()) {
                DeviceEventManagerModule.RCTDeviceEventEmitter emitter =
                        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class);
                if (emitter != null) {
                    emitter.emit(eventName, data);
                }
            }
        } catch (Exception ignored) {}
    }

    private WritableMap createError(int code, String message) {
        WritableMap map = Arguments.createMap();
        map.putBoolean("status", false);
        map.putInt("errorCode", code);
        map.putString("errorMessage", message);

        return map;
    }

    // === Broadcast receiver ===
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                switch (action) {
                    case ACTION_USB_CONNECT:
                        eventEmit(onConnectedEvent, null);
                        break;
                    case ACTION_USB_DISCONNECTED:
                        eventEmit(onDisconnectedEvent, null);
                        break;
                    case ACTION_USB_NOT_OPENED:
                        eventEmit(onErrorEvent, createError(Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT, Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT_MESSAGE));
                        break;
                    case ACTION_USB_ATTACHED:
                        eventEmit(onDeviceAttachedEvent, null);
                        if(autoConnect && chooseFirstDevice()) {
                            connectDevice(autoConnectDeviceName, autoConnectBaudRate);
                        }
                        break;
                    case ACTION_USB_DETACHED:
                        eventEmit(onDeviceDetachedEvent, null);
                        if(serialPortConnected) {
                            stopConnection();
                        }
                        break;
                    case ACTION_USB_PERMISSION :
                        boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                        startConnection(granted);
                        break;
                    case ACTION_USB_PERMISSION_GRANTED:
                        eventEmit(onUsbPermissionGranted, null);
                        break;
                    case ACTION_USB_PERMISSION_NOT_GRANTED:
                        eventEmit(onErrorEvent, createError(Definitions.ERROR_USER_DID_NOT_ALLOW_TO_CONNECT, Definitions.ERROR_USER_DID_NOT_ALLOW_TO_CONNECT_MESSAGE));
                        break;
                }
        }
    };

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(ACTION_NO_USB);
        filter.addAction(ACTION_USB_CONNECT);
        filter.addAction(ACTION_USB_DISCONNECTED);
        filter.addAction(ACTION_USB_PERMISSION_NOT_GRANTED);
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(ACTION_USB_ATTACHED);
        filter.addAction(ACTION_USB_DETACHED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && reactContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            reactContext.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            reactContext.registerReceiver(usbReceiver, filter);
        }
    }

    @Override
    public void onHostResume() {
        // optional: nothing needed
    }

    @Override
    public void onHostPause() {
        // optional: nothing needed
    }

    @Override
    public void onHostDestroy() {
        if (usbServiceStarted) {
             try {
                reactContext.unregisterReceiver(usbReceiver);
            } catch (IllegalArgumentException ignored) {}
            usbServiceStarted = false;
        }
        reactContext.removeLifecycleEventListener(this);
    }

    // === Service Control ===
    @Override
    public void startUsbService() {
        if(usbServiceStarted) {
            return;
        }
        setFilters();

        usbManager = (UsbManager) reactContext.getSystemService(Context.USB_SERVICE);

        usbServiceStarted = true;

        //Return usb status when service is started.
        WritableMap map = Arguments.createMap();

        map.putBoolean("deviceAttached", !usbManager.getDeviceList().isEmpty());

        eventEmit(onServiceStarted, map);

        checkAutoConnect();
    }

    @Override
    public void stopUsbService() {
        if(serialPortConnected) {
            eventEmit(onErrorEvent, createError(Definitions.ERROR_SERVICE_STOP_FAILED, Definitions.ERROR_SERVICE_STOP_FAILED_MESSAGE));
            return;
        }
        if(!usbServiceStarted) {
        return;
        }
        reactContext.unregisterReceiver(usbReceiver);
        usbServiceStarted = false;
        eventEmit(onServiceStopped, null);
    }

    // === Status ===
    @Override public void isOpen(Promise promise) { 
        promise.resolve(serialPortConnected); 
    }
    @Override public void isServiceStarted(Promise promise) {
         promise.resolve(usbServiceStarted); 
    }

    @Override public void isSupported(String deviceName, Promise promise) {
        if(!chooseDevice(deviceName)) {
            promise.reject(String.valueOf(Definitions.ERROR_DEVICE_NOT_FOUND), Definitions.ERROR_DEVICE_NOT_FOUND_MESSAGE);
        } else {
            promise.resolve(UsbSerialDevice.isSupported(device));
        }
    }

    // === Device List ===
    @Override
    public void getDeviceList(Promise promise) {
        if(!usbServiceStarted) {
            promise.reject(String.valueOf(Definitions.ERROR_USB_SERVICE_NOT_STARTED), Definitions.ERROR_USB_SERVICE_NOT_STARTED_MESSAGE);
            return;
        }

        UsbManager manager = (UsbManager) reactContext.getSystemService(Context.USB_SERVICE);

        HashMap<String, UsbDevice> devices = manager.getDeviceList();

        if(devices.isEmpty()) {
            promise.resolve(Arguments.createArray());
            return;
        }

        WritableArray deviceList = Arguments.createArray();
        for(Map.Entry<String, UsbDevice> entry: devices.entrySet()) {
            UsbDevice d = entry.getValue();

            WritableMap map = Arguments.createMap();
            map.putString("name", d.getDeviceName());
            map.putInt("vendorId", d.getVendorId());
            map.putInt("productId", d.getProductId());

            deviceList.pushMap(map);
        }

        promise.resolve(deviceList);
    }

    // === Connection ===
    @Override
    public void connectDevice(String deviceName, double baudRate) {
         try {
            if(!usbServiceStarted){
                eventEmit(onErrorEvent, createError(Definitions.ERROR_USB_SERVICE_NOT_STARTED, Definitions.ERROR_USB_SERVICE_NOT_STARTED_MESSAGE));
                return;
            }

            if(serialPortConnected) {
                eventEmit(onErrorEvent, createError(Definitions.ERROR_SERIALPORT_ALREADY_CONNECTED, Definitions.ERROR_SERIALPORT_ALREADY_CONNECTED_MESSAGE));
                return;
            }

            if(deviceName.isEmpty() || deviceName.length() < 0) {
                eventEmit(onErrorEvent, createError(Definitions.ERROR_CONNECT_DEVICE_NAME_INVALID, Definitions.ERROR_CONNECT_DEVICE_NAME_INVALID_MESSAGE));
                return;
            }

            if(baudRate < 1){
                eventEmit(onErrorEvent, createError(Definitions.ERROR_CONNECT_BAUDRATE_EMPTY, Definitions.ERROR_CONNECT_BAUDRATE_EMPTY_MESSAGE));
                return;
            }

            if(!autoConnect) {
                this.BAUD_RATE = (int) baudRate;
            }

            if(!chooseDevice(deviceName)) {
                eventEmit(onErrorEvent, createError(Definitions.ERROR_X_DEVICE_NOT_FOUND, Definitions.ERROR_X_DEVICE_NOT_FOUND_MESSAGE + deviceName));
                return;
            }

            requestUserPermission();

        } catch (Exception err) {
            eventEmit(onErrorEvent, createError(Definitions.ERROR_CONNECTION_FAILED, Definitions.ERROR_CONNECTION_FAILED_MESSAGE + " Catch Error Message:" + err.getMessage()));
        }
    }

    @Override
    public void disconnect() { 
        if(!usbServiceStarted){
            eventEmit(onErrorEvent, createError(Definitions.ERROR_USB_SERVICE_NOT_STARTED, Definitions.ERROR_USB_SERVICE_NOT_STARTED_MESSAGE));
            return;
        }

        if(!serialPortConnected) {
            eventEmit(onErrorEvent, createError(Definitions.ERROR_SERIALPORT_ALREADY_DISCONNECTED, Definitions.ERROR_SERIALPORT_ALREADY_DISCONNECTED_MESSAGE));
            return;
        }
        stopConnection();
     }

    // === Writing Methods ===
    @Override public void writeBytes(@Nonnull ReadableArray data) {
        if(!usbServiceStarted){
            eventEmit(onErrorEvent, createError(Definitions.ERROR_USB_SERVICE_NOT_STARTED, Definitions.ERROR_USB_SERVICE_NOT_STARTED_MESSAGE));
            return;
        }
        if(!serialPortConnected || serialPort == null) {
            eventEmit(onErrorEvent, createError(Definitions.ERROR_THERE_IS_NO_CONNECTION, Definitions.ERROR_THERE_IS_NO_CONNECTION_MESSAGE));
            return;
        }

        byte[] bytes = new byte[data.size()];
        for (int i = 0; i < data.size(); i++) bytes[i] = (byte) data.getInt(i);
        serialPort.write(bytes);
    }

    @Override public void writeString(String data) {
        if(!usbServiceStarted){
            eventEmit(onErrorEvent, createError(Definitions.ERROR_USB_SERVICE_NOT_STARTED, Definitions.ERROR_USB_SERVICE_NOT_STARTED_MESSAGE));
            return;
        }
        if(!serialPortConnected || serialPort == null) {
            eventEmit(onErrorEvent, createError(Definitions.ERROR_THERE_IS_NO_CONNECTION, Definitions.ERROR_THERE_IS_NO_CONNECTION_MESSAGE));
            return;
        }
        serialPort.write(data.getBytes());
    }

    @Override public void writeBase64(String data) {
        if(!usbServiceStarted){
            eventEmit(onErrorEvent, createError(Definitions.ERROR_USB_SERVICE_NOT_STARTED, Definitions.ERROR_USB_SERVICE_NOT_STARTED_MESSAGE));
            return;
        }
        if(!serialPortConnected || serialPort == null) {
            eventEmit(onErrorEvent, createError(Definitions.ERROR_THERE_IS_NO_CONNECTION, Definitions.ERROR_THERE_IS_NO_CONNECTION_MESSAGE));
            return;
        }

        byte [] bytes = Base64.decode(data, Base64.DEFAULT);
        serialPort.write(bytes);
    }

    @Override public void writeHexString(String data) {
        if(!usbServiceStarted){
            eventEmit(onErrorEvent, createError(Definitions.ERROR_USB_SERVICE_NOT_STARTED, Definitions.ERROR_USB_SERVICE_NOT_STARTED_MESSAGE));
            return;
        }
        if(!serialPortConnected || serialPort == null) {
            eventEmit(onErrorEvent, createError(Definitions.ERROR_THERE_IS_NO_CONNECTION, Definitions.ERROR_THERE_IS_NO_CONNECTION_MESSAGE));
            return;
        }

        if(data.length() < 1) {
            return;
        }

        byte[] bytes = new byte[data.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int index = i * 2;

            String hex = data.substring(index, index + 2);

            if(Definitions.hexChars.indexOf(hex.substring(0, 1)) == -1 || Definitions.hexChars.indexOf(hex.substring(1, 2)) == -1) {
                return;
            }

            int v = Integer.parseInt(hex, 16);
            bytes[i] = (byte) v;
        }
        serialPort.write(bytes);
    }

    // === Setters ===
    @Override public void setDataBit(double DATA_BIT) { this.DATA_BIT = (int) DATA_BIT; }
    @Override public void setStopBit(double STOP_BIT) { this.STOP_BIT = (int) STOP_BIT; }
    @Override public void setParity(double PARITY) { this.PARITY = (int) PARITY; }
    @Override public void setFlowControl(double FLOW_CONTROL) { this.FLOW_CONTROL = (int) FLOW_CONTROL; }
    @Override public void setAutoConnect(boolean status) { this.autoConnect = status; }
    @Override public void setAutoConnectBaudRate(double baudRate) { this.autoConnectBaudRate = (int) baudRate; }
    @Override public void setInterface(double iFace) { this.portInterface = (int) iFace; }
    @Override public void setReturnedDataType(double type) { 
        if((int)type == Definitions.RETURNED_DATA_TYPE_HEXSTRING || (int)type == Definitions.RETURNED_DATA_TYPE_INTARRAY) {
            this.returnedDataType = (int) type;
        }
     }
    @Override public void setDriver(String driver) { 
        if(driver.isEmpty() || !driverList.contains(driver.trim())) {
            eventEmit(onErrorEvent, createError(Definitions.ERROR_DRIVER_TYPE_NOT_FOUND, Definitions.ERROR_DRIVER_TYPE_NOT_FOUND_MESSAGE));
            return;
        }
        this.driver = driver;
    }
    @Override public void setReadBufferSize(double bufferSize) { this.readBufferSize = (int) bufferSize; }

    @Override public void loadDefaultConnectionSetting() {
        DATA_BIT     = UsbSerialInterface.DATA_BITS_8;
        STOP_BIT     = UsbSerialInterface.STOP_BITS_1;
        PARITY       =  UsbSerialInterface.PARITY_NONE;
        FLOW_CONTROL = UsbSerialInterface.FLOW_CONTROL_OFF;
    }

    @Override
    public String intArrayToUtf16(@Nonnull ReadableArray intArray) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < intArray.size(); i++) {
            str.append((char) intArray.getInt(i));
        }
        return str.toString();
    }

    @Override
    public String hexToUtf16(@Nonnull String hex) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < hex.length() - 1; i += 2) {
            String byteStr = hex.substring(i, i + 2);
            if (byteStr.equals("00")) break;
            str.append((char) Integer.parseInt(byteStr, 16));
        }
        return str.toString();
    }

    // === Internal Methods ===
    private void requestUserPermission() {
        if (device == null) {
            return;
        }

        Intent intent = new Intent(ACTION_USB_PERMISSION);
        intent.setPackage(reactContext.getPackageName());

        int flags;
        if (Build.VERSION.SDK_INT >= 34) {
          flags = PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT;
        } else if (Build.VERSION.SDK_INT >= 23) {
          flags = PendingIntent.FLAG_MUTABLE;
        } else {
          flags = 0;
        }
        PendingIntent pi = PendingIntent.getBroadcast(reactContext, 0, intent, flags);
        usbManager.requestPermission(device, pi);
    }

    private void startConnection(boolean granted) {
        if (granted) {
            eventEmit(onUsbPermissionGranted, null);

            connection = usbManager.openDevice(device);
            new Thread(() -> {
                try {
                    if (driver.equals("AUTO")) {
                        serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection, portInterface);
                    } else {
                        serialPort = UsbSerialDevice.createUsbSerialDevice(driver, device, connection, portInterface);
                    }

                    if (serialPort == null || !serialPort.open()) {
                        eventEmit(onErrorEvent, createError(
                                Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT,
                                Definitions.ERROR_COULD_NOT_OPEN_SERIALPORT_MESSAGE));
                        return;
                    }

                    serialPortConnected = true;
                    int baud = autoConnect ? autoConnectBaudRate : BAUD_RATE;
                    serialPort.setBaudRate(baud);
                    serialPort.setDataBits(DATA_BIT);
                    serialPort.setStopBits(STOP_BIT);
                    serialPort.setParity(PARITY);
                    serialPort.setFlowControl(FLOW_CONTROL);
                    serialPort.read(mCallback, readBufferSize);

                    eventEmit(onConnectedEvent, null);
                } catch (Exception e) {
                    eventEmit(onErrorEvent, createError(
                            Definitions.ERROR_CONNECTION_FAILED,
                            Definitions.ERROR_CONNECTION_FAILED_MESSAGE + " Exception: " + e.getMessage()));
                }
            }).start();
        } else {
            eventEmit(onErrorEvent, createError(
                    Definitions.ERROR_USER_DID_NOT_ALLOW_TO_CONNECT,
                    Definitions.ERROR_USER_DID_NOT_ALLOW_TO_CONNECT_MESSAGE));
            connection = null;
            device = null;
        }
    }

    private void stopConnection() {
        if (serialPortConnected) {
            serialPort.close();
            connection = null;
            device = null;
            serialPortConnected = false;
            eventEmit(onDisconnectedEvent, null);
        } else {
            eventEmit(onDeviceDetachedEvent, null);
        }
    }

    private boolean chooseDevice(String deviceName) {
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if(usbDevices.isEmpty()) {
            return false;
        }

        boolean selected = false;

        for (Map.Entry<String, UsbDevice> entry: usbDevices.entrySet()) {
            UsbDevice d = entry.getValue();

            if(d.getDeviceName().equals(deviceName)) {
                device = d;
                selected = true;
                break;
            }
        }

        return selected;
    }

    private boolean chooseFirstDevice() {
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if(usbDevices.isEmpty()) {
            return false;
        }

        boolean selected = false;

        for (Map.Entry<String, UsbDevice> entry: usbDevices.entrySet()) {
            UsbDevice d = entry.getValue();

            int deviceVID = d.getVendorId();
            int devicePID = d.getProductId();

            if (deviceVID != 0x1d6b && (devicePID != 0x0001 && devicePID != 0x0002 && devicePID != 0x0003) && deviceVID != 0x5c6 && devicePID != 0x904c)
            {
                device = d;
                autoConnectDeviceName = d.getDeviceName();
                selected = true;
                break;
            }
        }
        return selected;
    }

    private void checkAutoConnect() {
        if(!autoConnect || serialPortConnected) {
            return;
        }

        if(chooseFirstDevice()) {
            connectDevice(autoConnectDeviceName, autoConnectBaudRate);
        }
    }

    private UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] bytes) {
        try {

            String payloadKey = "payload";

            WritableMap params = Arguments.createMap();

            if(returnedDataType == Definitions.RETURNED_DATA_TYPE_INTARRAY) {

            WritableArray intArray = new WritableNativeArray();
            for(byte b: bytes) {
                intArray.pushInt(UnsignedBytes.toInt(b));
            }
            params.putArray(payloadKey, intArray);

            } else if(returnedDataType == Definitions.RETURNED_DATA_TYPE_HEXSTRING) {
            String hexString = Definitions.bytesToHex(bytes);
            params.putString(payloadKey, hexString);
            } else
            return;

            eventEmit(onReadDataFromPort, params);

        } catch (Exception err) {
            eventEmit(onErrorEvent, createError(Definitions.ERROR_NOT_READED_DATA, Definitions.ERROR_NOT_READED_DATA_MESSAGE + " System Message: " + err.getMessage()));
        }
        }
  };
}
