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

public class MainMenuActivity extends AppCompatActivity implements RemoteControllerListener {
    private boolean isBound = false;
    private RemoteControllerService.LocalBinder binder;
    private AudioManager audioManager;

    private static final int NOME_BUTTON_SELECTED = 0;
    private static final int MUSIC_PLAYER_BUTTON_NO = 1;
    private static final int VIDEO_PLAYER_BUTTON_NO = 2;
    private static final int FLIGHT_INFO_BUTTON_NO = 3;
    private static final int SAFE_GUIDE_BUTTON_NO = 4;
    private static final int LIGHT_BUTTON_NO = 5;
    private static final int SERVICE_BUTTON_NO = 6;

    private static final int MUSIC_PLAYER_BUTTON_INDEX = MUSIC_PLAYER_BUTTON_NO - 1;
    private static final int VIDEO_PLAYER_BUTTON_INDEX = VIDEO_PLAYER_BUTTON_NO - 1;
    private static final int FLIGHT_INFO_BUTTON_INDEX = FLIGHT_INFO_BUTTON_NO - 1;
    private static final int SAFE_GUIDE_BUTTON_INDEX = SAFE_GUIDE_BUTTON_NO - 1;
    private static final int LIGHT_BUTTON_INDEX = LIGHT_BUTTON_NO - 1;
    private static final int SERVICE_BUTTON_INDEX = SERVICE_BUTTON_NO - 1;
    private int current_selected_button_no = NOME_BUTTON_SELECTED;
    ImageButton[] main_menu_btn_list = new ImageButton[6];
    RemoteControllerListener.Light light_status = Light.Off;
    RemoteControllerListener.ServiceBell service_status = ServiceBell.Off;


    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder serviceBinder) {
            try {
                binder = (RemoteControllerService.LocalBinder) serviceBinder;
                binder.registerListener(MainMenuActivity.this);
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

    private void go_to_musicplayer_activity()
    {
        Intent it = new Intent(this, MusicActivity.class);
        startActivity(it);
    }

    private void go_to_videoplayer_activity()
    {
        Intent it = new Intent(this, VideoActivity.class);
        startActivity(it);
    }

    private void go_to_flight_info_activity()
    {
        Intent it = new Intent(this, FlightInformationActivity.class);
        startActivity(it);
    }

    private void go_to_safe_guide_activity()
    {
        Intent it = new Intent(this, SafeGuideActivity.class);
        startActivity(it);
    }

    private void update_light_on_off()
    {
        Drawable light_off_drawable = getResources().getDrawable(R.drawable.light_off, null);
        Drawable light_on_drawable = getResources().getDrawable(R.drawable.light_on, null);

        if(light_status==Light.On)
        {
            main_menu_btn_list[LIGHT_BUTTON_INDEX].setBackground(light_on_drawable);
        }
        else if(light_status==Light.Off)
        {
            main_menu_btn_list[LIGHT_BUTTON_INDEX].setBackground(light_off_drawable);
        }
    }

    private void update_service_status()
    {
        Drawable not_in_service_drawable = getResources().getDrawable(R.drawable.not_in_service, null);
        Drawable in_service_drawable = getResources().getDrawable(R.drawable.in_service, null);

        if(service_status==ServiceBell.On)
        {
            main_menu_btn_list[SERVICE_BUTTON_INDEX].setBackground(in_service_drawable);
        }
        else if(service_status==ServiceBell.Off)
        {
            main_menu_btn_list[SERVICE_BUTTON_INDEX].setBackground(not_in_service_drawable);
        }
    }

    private void toggle_light_on_off()
    {
        boolean update = false;

        if(light_status==Light.On)
        {
            light_status = Light.Off;
            update = true;
        }
        else if(light_status==Light.Off)
        {
            light_status = Light.On;
            update = true;
        }

        if(update)
        {
            update_light_on_off();
        }
    }

    private void toggle_service_status()
    {
        boolean update = false;

        if(service_status==ServiceBell.On)
        {
            service_status = ServiceBell.Off;
            update = true;
        }
        else if(service_status==ServiceBell.Off)
        {
            service_status = ServiceBell.On;
            update = true;
        }

        if(update)
        {
            update_service_status();
        }
    }

    private void update_menu_button_status()
    {
        Drawable music_player_drawable = getResources().getDrawable(R.drawable.music_player, null);
        Drawable music_player_active_drawable = getResources().getDrawable(R.drawable.music_player_active, null);
        Drawable video_player_drawable = getResources().getDrawable(R.drawable.video_player, null);
        Drawable video_player_active_drawable = getResources().getDrawable(R.drawable.video_player_active, null);
        Drawable flight_info_drawable = getResources().getDrawable(R.drawable.flight_info, null);
        Drawable flight_info_active_drawable = getResources().getDrawable(R.drawable.flight_info_active, null);
        Drawable safe_guide_drawable = getResources().getDrawable(R.drawable.safe_guide, null);
        Drawable safe_guide_active_drawable = getResources().getDrawable(R.drawable.safe_guide_active, null);

        int current_selected_button_index = current_selected_button_no - 1;
        for(int i = 0; i<main_menu_btn_list.length; i++)
        {
            if(i==current_selected_button_index)
            {
                switch(i)
                {
                    case MUSIC_PLAYER_BUTTON_INDEX:
                        main_menu_btn_list[i].setBackground(music_player_active_drawable);
                        break;
                    case VIDEO_PLAYER_BUTTON_INDEX:
                        main_menu_btn_list[i].setBackground(video_player_active_drawable);
                        break;
                    case FLIGHT_INFO_BUTTON_INDEX:
                        main_menu_btn_list[i].setBackground(flight_info_active_drawable);
                        break;
                    case SAFE_GUIDE_BUTTON_INDEX:
                        main_menu_btn_list[i].setBackground(safe_guide_active_drawable);
                        break;
                }
            }
            else
            {
                switch(i)
                {
                    case MUSIC_PLAYER_BUTTON_INDEX:
                        main_menu_btn_list[i].setBackground(music_player_drawable);
                        break;
                    case VIDEO_PLAYER_BUTTON_INDEX:
                        main_menu_btn_list[i].setBackground(video_player_drawable);
                        break;
                    case FLIGHT_INFO_BUTTON_INDEX:
                        main_menu_btn_list[i].setBackground(flight_info_drawable);
                        break;
                    case SAFE_GUIDE_BUTTON_INDEX:
                        main_menu_btn_list[i].setBackground(safe_guide_drawable);
                        break;
                }
            }
        }
    }

    private void go_to_main_activity()
    {
        Intent it = new Intent(this, MainActivity.class);
        startActivity(it);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        main_menu_btn_list[MUSIC_PLAYER_BUTTON_INDEX] = findViewById(R.id.main_menu_music_player_btn);
        main_menu_btn_list[MUSIC_PLAYER_BUTTON_INDEX].setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                current_selected_button_no = MUSIC_PLAYER_BUTTON_NO;
                update_menu_button_status();
                go_to_musicplayer_activity();
            }
        });

        main_menu_btn_list[VIDEO_PLAYER_BUTTON_INDEX] = (ImageButton) findViewById(R.id.main_menu_video_player_btn);
        main_menu_btn_list[VIDEO_PLAYER_BUTTON_INDEX].setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                current_selected_button_no = VIDEO_PLAYER_BUTTON_NO;
                update_menu_button_status();
                go_to_videoplayer_activity();
            }
        });

        main_menu_btn_list[FLIGHT_INFO_BUTTON_INDEX] = (ImageButton) findViewById(R.id.main_menu_flight_info_btn);
        main_menu_btn_list[FLIGHT_INFO_BUTTON_INDEX].setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                current_selected_button_no = FLIGHT_INFO_BUTTON_NO;
                update_menu_button_status();
                go_to_flight_info_activity();
            }
        });

        main_menu_btn_list[SAFE_GUIDE_BUTTON_INDEX] = (ImageButton) findViewById(R.id.main_menu_safe_guide_btn);
        main_menu_btn_list[SAFE_GUIDE_BUTTON_INDEX].setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                current_selected_button_no = SAFE_GUIDE_BUTTON_NO;
                update_menu_button_status();
                go_to_safe_guide_activity();
            }
        });

        main_menu_btn_list[LIGHT_BUTTON_INDEX] = (ImageButton) findViewById(R.id.main_menu_light_btn);
        /*
        main_menu_btn_list[LIGHT_BUTTON_INDEX].setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                current_selected_button_no = LIGHT_BUTTON_NO;
                update_menu_button_status();
                toggle_light_on_off();
            }
        });
        */

        main_menu_btn_list[SERVICE_BUTTON_INDEX] = (ImageButton) findViewById(R.id.main_menu_service_btn);
        /*
        main_menu_btn_list[SERVICE_BUTTON_INDEX].setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                current_selected_button_no = SERVICE_BUTTON_NO;
                update_menu_button_status();
                toggle_service_status();
            }
        });
        */

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
        if(pwr==Power.Off)
        {
            go_to_main_activity();
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
        light_status = light;

        update_light_on_off();
    }

    @Override
    public void onSetServiceBell(ServiceBell bell) {
        service_status = bell;

        update_service_status();
    }

    @Override
    public void onKeyPressed(Key key) {
        if(key==Key.OK) {
            switch(current_selected_button_no) {
                case MUSIC_PLAYER_BUTTON_NO:
                    go_to_musicplayer_activity();
                    break;
                case VIDEO_PLAYER_BUTTON_NO:
                    go_to_videoplayer_activity();
                    break;
                case FLIGHT_INFO_BUTTON_NO:
                    go_to_flight_info_activity();
                    break;
                case SAFE_GUIDE_BUTTON_NO:
                    go_to_safe_guide_activity();
                    break;
            }
        }
        else
        {
            boolean key_updated = false;
            if(current_selected_button_no==NOME_BUTTON_SELECTED)
            {
                current_selected_button_no = MUSIC_PLAYER_BUTTON_NO;
                key_updated = true;
            }
            else
            {
                if(key==Key.Left) {
                    if(current_selected_button_no>MUSIC_PLAYER_BUTTON_NO)
                    {
                        current_selected_button_no -= 1;
                        key_updated = true;
                    }
                    else
                    {
                        current_selected_button_no = SAFE_GUIDE_BUTTON_NO;
                        key_updated = true;
                    }
                }
                else if(key==Key.Right) {
                    if(current_selected_button_no<SAFE_GUIDE_BUTTON_NO)
                    {
                        current_selected_button_no += 1;
                        key_updated = true;
                    }
                    else
                    {
                        current_selected_button_no = MUSIC_PLAYER_BUTTON_NO;
                        key_updated = true;
                    }
                }
            }

            if(key_updated)
            {
                update_menu_button_status();
            }
        }
    }
}