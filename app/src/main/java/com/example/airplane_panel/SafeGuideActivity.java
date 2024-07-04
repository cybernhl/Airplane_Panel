package com.example.airplane_panel;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

import com.example.airplane_panel.remote.RemoteControllerListener;
import com.example.airplane_panel.remote.RemoteControllerService;

public class SafeGuideActivity extends AppCompatActivity implements RemoteControllerListener {
    private boolean isBound = false;
    private RemoteControllerService.LocalBinder binder;
    private AudioManager audioManager;
    ImageButton safe_guide_img_btn = null;
    private int current_image = 0;

    private void switch_safe_guide_image() {
        boolean update = false;

        if (current_image == 0) {
            current_image = 1;
            update = true;
        } else if (current_image == 1) {
            current_image = 0;
            update = true;
        }

        if (update) {
            update_safe_guide_image();
        }
    }

    private void update_safe_guide_image()
    {
        Drawable safe_guide0_drawable = getResources().getDrawable(R.drawable.safe_guide_info_0, null);
        Drawable safe_guide1_drawable = getResources().getDrawable(R.drawable.safe_guide_info_1, null);

        switch (current_image)
        {
            case 0:
                safe_guide_img_btn.setBackground(safe_guide0_drawable);
                break;
            case 1:
                safe_guide_img_btn.setBackground(safe_guide1_drawable);
                break;
        }
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder serviceBinder) {
            try {
                binder = (RemoteControllerService.LocalBinder) serviceBinder;
                binder.registerListener(SafeGuideActivity.this);
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        current_image = 0;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safe_guide);

        safe_guide_img_btn = (ImageButton) findViewById(R.id.safe_guide_img_btn);
        safe_guide_img_btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {switch_safe_guide_image();}
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
        if(key==Key.OK || key==Key.Right) {
            boolean update = false;

            if (current_image == 0) {
                current_image = 1;
                update = true;
            } else if (current_image == 1) {
                current_image = 0;
                update = true;
            }

            if (update) {
                update_safe_guide_image();
            }
        }
        else
        {
            if(key==Key.Left) {
                super.onBackPressed();
            }
        }
    }
}