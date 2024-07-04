package com.example.airplane_panel;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.content.Intent;
import android.widget.ImageButton;

import com.example.airplane_panel.media.BaseMusicActivity;
import com.example.airplane_panel.media.PlayerService;
import com.example.airplane_panel.remote.RemoteControllerListener;
import com.example.airplane_panel.remote.RemoteControllerService;

public class MainActivity extends AppCompatActivity implements RemoteControllerListener {
    private boolean isBound = false;
    private RemoteControllerService.LocalBinder binder;
    private AudioManager audioManager;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder serviceBinder) {
            try {
                binder = (RemoteControllerService.LocalBinder) serviceBinder;
                binder.registerListener(MainActivity.this);
                isBound = true;
            } catch (Exception e) {
                Log.e("MainActivity", "Error connecting to service", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };

    private void go_to_mainmenu_activity()
    {
        Intent it = new Intent(this, MainMenuActivity.class);
        startActivity(it);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageButton main_menu_img_btn = (ImageButton) findViewById(R.id.main_menu_img_btn);
        main_menu_img_btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {go_to_mainmenu_activity();}
        });

        // 初始化 AudioManager
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, RemoteControllerService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            binder.unregisterListener(this);
            unbindService(connection);
            isBound = false;
        }
    }

    @Override
    public void onSetPower(Power pwr) {
        if(pwr==Power.On)
        {
            go_to_mainmenu_activity();
        }
    }

    @Override
    public void onSetVolume(int vol) {
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        int setVolume = vol;

        // 假设 Volume 类有一个 getLevel() 方法，返回一个 0 到 100 的值
        int newVolume = (int) (setVolume / 100.0 * maxVolume);

        // 设置音量，其中 flags 参数可以使用 AudioManager.FLAG_SHOW_UI 来显示系统音量变更的UI
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, AudioManager.FLAG_SHOW_UI);
    }

    @Override
    public void onSetLight(Light light) {

    }

    @Override
    public void onSetServiceBell(ServiceBell bell) {

    }

    @Override
    public void onKeyPressed(Key key) {
        if(key==Key.OK)
        {
            go_to_mainmenu_activity();
        }
    }
}