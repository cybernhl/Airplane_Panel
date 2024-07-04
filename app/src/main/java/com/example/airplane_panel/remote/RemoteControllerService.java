package com.example.airplane_panel.remote;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

public class RemoteControllerService extends Service {
    private RemoteControllerAPI controllerAPI;
    private final IBinder binder = new LocalBinder();
    private Serial serial; // Assuming Serial is a class that handles the serial communication
    private RemoteControllerListener listener = null;

    public class LocalBinder extends Binder {
        public RemoteControllerService getService() {
            return RemoteControllerService.this;
        }

        public void registerListener(RemoteControllerListener set_listener) {
            listener = set_listener;
        }

        public void unregisterListener(RemoteControllerListener set_listener) {
            if (listener == set_listener) {
                listener = null;
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize your Serial communication here
        serial = new Serial(this, 115200, new OnReceiveListener() {
            @Override
            public void onReceive(byte[] data) {
                controllerAPI.Handle_Incoming_Msg(data);
            }
        });
        serial.open();
        controllerAPI = new RemoteControllerAPI(serial, new RemoteControllerListener() {
            @Override
            public void onSetPower(Power pwr) {
                if (listener != null) {
                    listener.onSetPower(pwr);
                }
            }

            @Override
            public void onSetVolume(int vol) {
                if (listener != null) {
                    listener.onSetVolume(vol);
                }
            }

            @Override
            public void onSetLight(Light light) {
                if (listener != null) {
                    listener.onSetLight(light);
                }
            }

            @Override
            public void onSetServiceBell(ServiceBell bell) {
                if (listener != null) {
                    listener.onSetServiceBell(bell);
                }
            }

            @Override
            public void onKeyPressed(Key key) {
                if (listener != null) {
                    listener.onKeyPressed(key);
                }
            }

        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handle commands sent to the service
        /*
        if (intent != null) {
            String action = intent.getAction();
            switch (action) {
                case "SEND_STATUS":
                    boolean light = intent.getBooleanExtra("light", false);
                    boolean service = intent.getBooleanExtra("service", false);
                    short volume = intent.getShortExtra("volume", (short) 50);
                    RemoteControllerAPI.Function function = (RemoteControllerAPI.Function) intent.getSerializableExtra("function");
                    RemoteControllerAPI.Active active = (RemoteControllerAPI.Active) intent.getSerializableExtra("active");
                    String name = intent.getStringExtra("name");
                    controllerAPI.Send_Status(light, service, volume, function, active, name);
                    break;
                // Add more cases to handle other actions like SEND_ACK, SEND_VOLUME, etc.
            }
        }*/
        return START_STICKY; // If the system kills the service, restart it with a null intent
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up any resources such as closing serial connections
        serial.close(); // Placeholder for actual serial close method
    }

    public void setDisplayName(String name)
    {
        if (controllerAPI != null) {
            controllerAPI.Send_Name(name);
        }
    }
    public void setActive(RemoteControllerAPI.Active status)
    {
        if (controllerAPI != null) {
            controllerAPI.Send_Active(status);
        }
    }
}
