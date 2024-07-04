package com.example.airplane_panel.remote;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.github.lzyzsd.circleprogress.BuildConfig;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class Serial implements SerialInputOutputManager.Listener{

    private enum UsbPermission { Unknown, Requested, Granted, Denied }
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;
    private final BroadcastReceiver broadcastReceiver;
    private final Handler mainLooper;
    private SerialInputOutputManager usbIoManager;
    private UsbSerialPort usbSerialPort;
    private UsbPermission usbPermission = UsbPermission.Unknown;
    private boolean connected = false;
    private int portNum = 0;
    Context context = null;
    private int baudRate = 9600;
    private OnReceiveListener receiveListener = null;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                    if (!connected) {
                        open();  // Attempt to connect when USB is reattached
                    }
                    break;
                case UsbManager.ACTION_USB_DEVICE_DETACHED:
                    if (connected) {
                        close();  // Disconnect properly when USB is detached
                    }
                    break;
                case INTENT_ACTION_GRANT_USB:
                    boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    usbPermission = granted ? UsbPermission.Granted : UsbPermission.Denied;
                    if (granted) {
                        open();  // Reconnect with new permission
                    }
                    break;
            }
        }
    };

    public Serial(Context current_context, int baud, OnReceiveListener listener) {

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                    usbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            ? UsbPermission.Granted : UsbPermission.Denied;
                }
            }
        };

        context = current_context;
        baudRate = baud;
        receiveListener = listener;

        mainLooper = new Handler(Looper.getMainLooper());

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(INTENT_ACTION_GRANT_USB);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(usbReceiver, filter);
        }
    }
    /*
     * Serial + UI
     */
    public int open() {
        // Find all available drivers from attached devices.
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (availableDrivers.isEmpty()) {
            return -1;
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        if(driver == null) {
            return -1;
        }

        usbSerialPort = driver.getPorts().get(portNum);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if(usbConnection == null && usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(driver.getDevice())) {
            usbPermission = UsbPermission.Requested;
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_MUTABLE : 0;
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(INTENT_ACTION_GRANT_USB), flags);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return -1;
        }
        if(usbConnection == null) {
            return -1;
        }

        try {
            usbSerialPort.open(usbConnection);
            try{
                usbSerialPort.setParameters(baudRate, 8, 1, UsbSerialPort.PARITY_NONE);
            }catch (UnsupportedOperationException e){
                return -1;
            }
            usbIoManager = new SerialInputOutputManager(usbSerialPort, this);
            usbIoManager.start();
            connected = true;
        } catch (Exception e) {
            close();
            return -1;
        }

        return 0;
    }

    public void close() {
        connected = false;
        if(usbIoManager!=null) {
            usbIoManager.setListener(null);
            usbIoManager.stop();
            usbIoManager = null;
            try {
                usbSerialPort.close();
            } catch (IOException ignored) {
            }
            usbSerialPort = null;
        }
    }

    public int send(byte[] data)
    {
        if(!connected) {
            return -1;
        }
        try {
            usbSerialPort.write(data, WRITE_WAIT_MILLIS);
            return data.length;
        } catch (Exception e) {
            onRunError(e);
            return -1;
        }
    }

    public byte[] recv() {
        if(!connected) {
            return null;
        }
        try {
            byte[] buffer = new byte[8192];
            int len = usbSerialPort.read(buffer, READ_WAIT_MILLIS);
            if(len <= 0)
            {
                return null;
            }
            return Arrays.copyOf(buffer, len);
        } catch (IOException e) {
            // when using read with timeout, USB bulkTransfer returns -1 on timeout _and_ errors
            // like connection loss, so there is typically no exception thrown here on error
            close();
            return null;
        }
    }

    /*
     * Serial
     */
    @Override
    public void onNewData(byte[] data) {
        mainLooper.post(() -> {
            if(data.length > 0) {
                if (receiveListener != null) {
                    receiveListener.onReceive(data);
                }
            }
        });
    }

    @Override
    public void onRunError(Exception e) {
        mainLooper.post(() -> {
            close();
        });
    }

}
