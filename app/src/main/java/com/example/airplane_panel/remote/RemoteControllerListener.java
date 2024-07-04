package com.example.airplane_panel.remote;

public interface RemoteControllerListener {
    public enum Power {Off, On}

    public enum Light {Off, On}

    public enum ServiceBell {Off, On}

    public enum Key {Up, Down, Left, Right, OK, Fast_Forward, Back_Forward}

    void onSetPower(Power pwr);

    void onSetVolume(int vol);

    void onSetLight(Light light);

    void onSetServiceBell(ServiceBell bell);

    void onKeyPressed(Key key);
}
