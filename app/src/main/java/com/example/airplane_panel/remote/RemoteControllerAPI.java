package com.example.airplane_panel.remote;

import java.nio.charset.StandardCharsets;
import android.content.Context;

public class RemoteControllerAPI {
    private static final int REMOTE_ID = 0xBA;
    private static final int HOST_ID = 0xAB;
    private static final int HOST_COMMAND_SEND_STATUS = 0x01;
    private static final int HOST_COMMAND_SEND_ACK = 0x02;
    private static final int HOST_COMMAND_SEND_FUNCTION = 0x03;
    private static final int HOST_COMMAND_SEND_NAME = 0x04;
    private static final int HOST_COMMAND_SEND_ACTIVE = 0x05;

    private static final int REMOTE_COMMAND_REQUEST_STATUS = 0x01;
    private static final int REMOTE_COMMAND_SET_POWER_ONOFF = 0x02;
    private static final int REMOTE_COMMAND_SET_VOLUME = 0x03;
    private static final int REMOTE_COMMAND_SET_LIGHT_ONOFF = 0x04;
    private static final int REMOTE_COMMAND_SERVICE_BELL_ONOFF = 0x05;
    private static final int REMOTE_COMMAND_KEY_PRESS = 0x06;

    private Serial serial_instance = null;
    private RemoteControllerListener remote_listener = null;
    public enum Function { Music, Movie, Flight_Info, Safe_Guige }
    public enum Active { Stop, Play, Pause }

    public RemoteControllerAPI(Serial instance, RemoteControllerListener listener) {
        serial_instance = instance;
        remote_listener = listener;
    }
    private void Send_Remote(byte comamnd, byte[] payload)
    {
        // Ensure that the commandData array has enough data to fill the placeholders.
        if (payload == null)
        {
            return;
        }

        // Construct the command according to the specified format.
        byte[] frame = new byte[payload.length+3];
        frame[0] = (byte) REMOTE_ID; // Send Status
        frame[1] = (byte) (payload.length+1);
        frame[2] = comamnd;

        System.arraycopy(payload, 0, frame, 3, payload.length);

        serial_instance.send(frame);
    }

    public void Send_Status(boolean light, boolean service, short volume, Function function, Active activate, String name)
    {
        if (name.length() > 20) {
            name = name.substring(0, 20);
        }
        byte[] name_bytes = name.getBytes(StandardCharsets.UTF_8);

        if(volume<0){volume = 0;}
        if(volume>100){volume = 100;}

        byte[] payload = new byte[25];

        if(light)
        {
            payload[0] = 1;
        }
        else
        {
            payload[0] = 0;
        }

        if(service)
        {
            payload[1] = 1;
        }
        else
        {
            payload[1] = 0;
        }

        payload[2] = (byte) volume;

        switch(function)
        {
            case Music:
                payload[3] = 1;
                break;
            case Movie:
                payload[3] = 2;
                break;
            case Flight_Info:
                payload[3] = 3;
                break;
            case Safe_Guige:
                payload[3] = 4;
                break;
        }

        switch(activate)
        {
            case Stop:
                payload[4] = 0;
                break;
            case Play:
                payload[4] = 1;
                break;
            case Pause:
                payload[4] = 2;
                break;
        }

        System.arraycopy(name_bytes, 0, payload, 5, name_bytes.length);
        if (name.length() < 20) {
            for(int i = 5 + name_bytes.length; i < (20-name_bytes.length); i++) {
                payload[i] = 0;
            }
        }

        Send_Remote((byte) HOST_COMMAND_SEND_STATUS, payload);
    }
    public void Send_ACK()
    {
        byte[] payload = new byte[1];

        payload[0] = 0x01;

        Send_Remote((byte) HOST_COMMAND_SEND_ACK, payload);
    }
    public void Send_Function(Function function)
    {
        byte[] payload = new byte[1];

        switch(function)
        {
            case Music:
                payload[0] = 1;
                break;
            case Movie:
                payload[0] = 2;
                break;
            case Flight_Info:
                payload[0] = 3;
                break;
            case Safe_Guige:
                payload[0] = 4;
                break;
        }

        Send_Remote((byte) HOST_COMMAND_SEND_FUNCTION, payload);
    }
    public void Send_Active(Active activate)
    {
        byte[] payload = new byte[1];

        switch(activate)
        {
            case Stop:
                payload[0] = 0;
                break;
            case Play:
                payload[0] = 1;
                break;
            case Pause:
                payload[0] = 2;
                break;
        }

        Send_Remote((byte) HOST_COMMAND_SEND_ACTIVE, payload);
    }

    public void Send_Name(String name)
    {
        if (name.length() > 20) {
            name = name.substring(0, 20);
        }

        byte[] payload = new byte[20];

        byte[] name_bytes = name.getBytes(StandardCharsets.UTF_8);

        System.arraycopy(name_bytes, 0, payload, 0, name_bytes.length);

        if (name.length() < 20) {
            for(int i = 5 + name_bytes.length; i < (20-name_bytes.length); i++) {
                payload[i] = 0;
            }
        }

        Send_Remote((byte) HOST_COMMAND_SEND_NAME, payload);
    }

    public void Handle_Incoming_Msg(byte[] data)
    {
        if(remote_listener==null)
        {
            return;
        }

        int ID = data[0] & 0xFF;

        if(ID!=HOST_ID)
        {
            return;
        }

        if(data.length<2)
        {
            return;
        }

        int length = data[1] & 0xFF;

        if(data.length<(length+2))
        {
            return;
        }

        int command = data[2] & 0xFF;

        switch(command)
        {
            case REMOTE_COMMAND_REQUEST_STATUS:
                //Not Implement
                break;
            case REMOTE_COMMAND_SET_POWER_ONOFF:
                int pwr_onoff = data[3] & 0xFF;
                if(pwr_onoff==0x00)
                {
                    remote_listener.onSetPower(RemoteControllerListener.Power.Off);
                }
                else if(pwr_onoff==0x01)
                {
                    remote_listener.onSetPower(RemoteControllerListener.Power.On);
                }
                break;
            case REMOTE_COMMAND_SET_VOLUME:
                int volume = data[3] & 0xFF;
                remote_listener.onSetVolume(volume*2);
                break;
            case REMOTE_COMMAND_SET_LIGHT_ONOFF:
                int light_onoff = data[3] & 0xFF;
                if(light_onoff==0x00)
                {
                    remote_listener.onSetLight(RemoteControllerListener.Light.Off);
                }
                else if(light_onoff==0x01)
                {
                    remote_listener.onSetLight(RemoteControllerListener.Light.On);
                }
                break;
            case REMOTE_COMMAND_SERVICE_BELL_ONOFF:
                int bell_onoff = data[3] & 0xFF;
                if(bell_onoff==0x00)
                {
                    remote_listener.onSetServiceBell(RemoteControllerListener.ServiceBell.Off);
                }
                else if(bell_onoff==0x01)
                {
                    remote_listener.onSetServiceBell(RemoteControllerListener.ServiceBell.On);
                }
                break;
            case REMOTE_COMMAND_KEY_PRESS:
                int key_press = data[3] & 0xFF;
                if(key_press==0x01)
                {
                    remote_listener.onKeyPressed(RemoteControllerListener.Key.Up);
                }
                else if(key_press==0x02)
                {
                    remote_listener.onKeyPressed(RemoteControllerListener.Key.Down);
                }
                else if(key_press==0x03)
                {
                    remote_listener.onKeyPressed(RemoteControllerListener.Key.Left);
                }
                else if(key_press==0x04)
                {
                    remote_listener.onKeyPressed(RemoteControllerListener.Key.Right);
                }
                else if(key_press==0x05)
                {
                    remote_listener.onKeyPressed(RemoteControllerListener.Key.OK);
                }
                else if(key_press==0x06)
                {
                    remote_listener.onKeyPressed(RemoteControllerListener.Key.Fast_Forward);
                }
                else if(key_press==0x07)
                {
                    remote_listener.onKeyPressed(RemoteControllerListener.Key.Back_Forward);
                }
                break;
        }

    }
}