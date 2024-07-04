package com.example.airplane_panel;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;
import android.os.Handler;
import java.util.Random;

import com.example.airplane_panel.remote.RemoteControllerListener;
import com.example.airplane_panel.remote.RemoteControllerService;

public class FlightInformationActivity extends AppCompatActivity implements SurfaceHolder.Callback, RemoteControllerListener {
    private boolean isBound = false;
    private RemoteControllerService.LocalBinder binder;
    private AudioManager audioManager;
    private MediaPlayer mediaPlayer;
    private SurfaceView surfaceView;
    private String flying_anim_filename = "flying.mp4";
    private Handler handler = new Handler();
    private Random random = new Random();

    TextView altitudeText = null;
    TextView speedText = null;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder serviceBinder) {
            try {
                binder = (RemoteControllerService.LocalBinder) serviceBinder;
                binder.registerListener(FlightInformationActivity.this);
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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flight_information);

        surfaceView = findViewById(R.id.flying_surface_view); // 确保您有一个 SurfaceView 在布局中
        surfaceView.getHolder().addCallback(this);

        altitudeText = findViewById(R.id.flight_altitude);
        speedText = findViewById(R.id.flight_speed);

        startUpdatingFlightInfo();

        // 初始化 AudioManager
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    private void startUpdatingFlightInfo() {
        handler.postDelayed(updateFlightInfoRunnable, 1000);  // 每隔1000毫秒（1秒）更新一次
    }

    private Runnable updateFlightInfoRunnable = new Runnable() {
        @Override
        public void run() {
            // 更新飞行信息
            int altitude = 10000 + random.nextInt(2001);  // 10000到12000之间的随机高度
            int speed = 750 + random.nextInt(141);  // 750到890之间的随机速度

            altitudeText.setText(String.valueOf(altitude));
            speedText.setText(String.valueOf(speed));

            // 继续定时更新
            handler.postDelayed(this, 10000);
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, RemoteControllerService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(updateFlightInfoRunnable);  // 确保在Activity停止时停止更新
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
        if(key==Key.Left) {
            super.onBackPressed();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            AssetManager assetManager = getAssets();
            AssetFileDescriptor afd = assetManager.openFd(flying_anim_filename);

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDisplay(holder);  // 设置 MediaPlayer 的显示到 SurfaceHolder
            mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            mediaPlayer.prepare(); // 同步准备 MediaPlayer
            mediaPlayer.setLooping(true);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                PlaybackParams params = new PlaybackParams();
                params.setSpeed(0.1f);  // 设置为十分之一的速度播放
                mediaPlayer.setPlaybackParams(params);
            }

            mediaPlayer.start(); // 开始播放
            afd.close(); // 关闭文件描述符
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // 当 Surface 尺寸等参数改变时的处理
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mediaPlayer != null) {
            mediaPlayer.release();  // 释放 MediaPlayer 资源
        }
    }

}