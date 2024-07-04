package com.example.airplane_panel;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.transition.ChangeBounds;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.SeekBar;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.beans.PropertyChangeEvent;

import com.example.airplane_panel.media.BaseView;
import com.example.airplane_panel.media.PlayerManager;
import com.example.airplane_panel.media.Media;
import com.example.airplane_panel.media.BasePresenter;
import com.example.airplane_panel.media.PlayerService;
import com.example.airplane_panel.media.BaseVideoActivity;

import com.example.airplane_panel.databinding.ActivityPlayVideoBinding;
import com.example.airplane_panel.remote.RemoteControllerListener;
import com.example.airplane_panel.remote.RemoteControllerService;

interface PlayVideoView extends BaseView {
    void updateVideoState(Media video, boolean isPlaying, int progress);
    void keepScreenOn(boolean on);
}

class PlayVideoPresenter extends BasePresenter<PlayVideoView> {
    private PlayerService player;
    private PlayVideoView context;

    public PlayVideoPresenter(PlayVideoView view) {
        super(view);
        context = view;
    }

    public void setPlayerManager(PlayerService player) {
        this.player = player;
        fetchVideoState();
    }

    public void fetchVideoState() {
        if (player.getMedia(true) != null) {
            context.updateVideoState(player.getMedia(true), player.isPlaying(), player.getProgress());
        }
    }
    public void onVideoPlay(SurfaceHolder holder) {
        if (!player.isPlaying()) {
            player.play(holder);
            context.keepScreenOn(true);  // Ask the activity to keep the screen on
        } else {
            player.pause();
            context.keepScreenOn(false);  // Ask the activity to allow the screen to turn off
        }
    }

    public void pauseVideo() {
        if (player.isPlaying()) {
            player.pause();
            context.keepScreenOn(false);  // Allow the screen to turn off when paused
        }
    }

    public void skipToNext() {
        player.skipToNext();
    }

    public void skipToPrevious() {
        player.skipToPrevious();
    }

    public void seekTo(int duration) {
        player.seekTo(duration);
    }

}

public class PlayVideoActivity extends BaseVideoActivity<PlayVideoPresenter> implements PlayVideoView, RemoteControllerListener {
    private RemoteControllerService service;
    private boolean isBound = false;
    private RemoteControllerService.LocalBinder binder;
    private AudioManager audioManager;

    private ActivityPlayVideoBinding viewBinding;
    private Animation scaleAnimation;
    private Runnable seekBarUpdateRunnable;
    private static final long seekBarUpdateDelayMillis = 1000;

    private SurfaceHolder displaySurfaceHolder;
    private int startPosition = 0;
    private PlayerService player_service = null;
    private boolean shouldStartPlayback = false;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder serviceBinder) {
            try {
                binder = (RemoteControllerService.LocalBinder) serviceBinder;
                service = binder.getService();
                binder.registerListener(PlayVideoActivity.this);
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

        startPosition = getIntent().getIntExtra("video_position", 0);

        viewBinding = ActivityPlayVideoBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());

        presenter = createPresenter();
        // Initialize animations and other UI elements
        initElementAnimation();
        initSeekBarUpdateRunnable();

        SurfaceView surfaceView = findViewById(R.id.surface_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder)
            {
                displaySurfaceHolder = holder;
                if (shouldStartPlayback && player_service != null) {
                    player_service.play(startPosition, displaySurfaceHolder);
                    keepScreenOn(true);
                    shouldStartPlayback = false;  // Reset the flag after starting playback
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                displaySurfaceHolder = holder;
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                displaySurfaceHolder = holder;
            }
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
        presenter.pauseVideo();
        viewBinding.seekBar.removeCallbacks(seekBarUpdateRunnable);
        if (isBound) {
            binder.unregisterListener(this);
            unbindService(connection);
            isBound = false;
        }
        keepScreenOn(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void playerBound(PlayerService player) {
        // Initialize animations
        initElementAnimation();

        // Initialize runnable for updating the seek bar
        initSeekBarUpdateRunnable();

        // Pass the player instance to the presenter
        presenter.setPlayerManager(player);

        player_service = player;

        // Determine if the surface is ready; if not, defer playback
        if (displaySurfaceHolder != null) {
            player.play(startPosition, displaySurfaceHolder);
            keepScreenOn(true);
        } else {
            shouldStartPlayback = true;
        }

        // Set up UI listeners
        setListen();
    }

    @Override
    public void updateState() {
        presenter.fetchVideoState();
    }

    @Override
    protected PlayVideoPresenter createPresenter() {
        return new PlayVideoPresenter(this);
    }

    @Override
    public void updateVideoState(Media video, boolean isPlaying, int progress) {
        viewBinding.seekBar.removeCallbacks(seekBarUpdateRunnable);

        long bar_progress = progress * 1000L;
        long bar_progress_minutes = bar_progress / 60000;
        long bar_progress_seconds = (bar_progress % 60000) / 1000;

        viewBinding.tvName.setText(video.getName());
        viewBinding.tvDuration.setText(video.toTimeString());
        viewBinding.seekBar.setMax((int) (video.getDuration() / 1000));
        viewBinding.seekBar.setProgress(progress);
        viewBinding.tvProgress.setText(String.format("%02d:%02d", bar_progress_minutes, bar_progress_seconds));

        Drawable drawable = ContextCompat.getDrawable(this, isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        drawable = DrawableCompat.wrap(drawable);
        viewBinding.imgPlay.setImageDrawable(drawable);

        if (isPlaying) {
            if(service!=null)
            {
                service.setDisplayName(video.getName());
            }
            viewBinding.seekBar.postDelayed(seekBarUpdateRunnable, seekBarUpdateDelayMillis);
        }
    }
    @Override
    public void keepScreenOn(boolean on) {
        if (on) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }
    @Override
    public void propertyChange(PropertyChangeEvent event) {
        switch (event.getPropertyName()) {
            case PlayerManager.ACTION_PLAY:
            case PlayerManager.ACTION_PAUSE:
                updateState();
                break;
        }
    }

    private void initElementAnimation() {
        scaleAnimation = AnimationUtils.loadAnimation(this, R.anim.zoom_in);
        scaleAnimation.setDuration(200);
        scaleAnimation.setRepeatCount(1);
        scaleAnimation.setRepeatMode(Animation.REVERSE);
    }

    private void initSeekBarUpdateRunnable() {
        seekBarUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                int progress = viewBinding.seekBar.getProgress() + 1;
                viewBinding.seekBar.setProgress(progress);
                viewBinding.seekBar.postDelayed(seekBarUpdateRunnable, seekBarUpdateDelayMillis);
            }
        };
    }

    private void setListen() {

        viewBinding.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    viewBinding.seekBar.removeCallbacks(seekBarUpdateRunnable);
                }

                long duration = progress * 1000L;
                long minutes = duration / 60000;
                long seconds = (duration % 60000) / 1000;

                viewBinding.tvProgress.setText(String.format("%02d:%02d", minutes, seconds));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Optional implementation
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                viewBinding.seekBar.removeCallbacks(seekBarUpdateRunnable);
                presenter.seekTo(seekBar.getProgress());
                viewBinding.seekBar.postDelayed(seekBarUpdateRunnable, seekBarUpdateDelayMillis);
            }
        });

        viewBinding.imgBackward.setOnClickListener(v -> {
            presenter.skipToPrevious();
            v.startAnimation(scaleAnimation);
        });

        viewBinding.imgPlay.setOnClickListener(v -> presenter.onVideoPlay(displaySurfaceHolder));

        viewBinding.imgForward.setOnClickListener(v -> {
            presenter.skipToNext();
            v.startAnimation(scaleAnimation);
        });
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
        if(key==Key.OK) {
            presenter.onVideoPlay(displaySurfaceHolder);
        }
        else {
            if (key == Key.Down) {
                super.onBackPressed();
            } else if (key == Key.Left) {
                presenter.skipToPrevious();
                viewBinding.imgBackward.startAnimation(scaleAnimation);
            } else if (key == Key.Right) {
                presenter.skipToNext();
                viewBinding.imgForward.startAnimation(scaleAnimation);
            }
        }
    }
}
