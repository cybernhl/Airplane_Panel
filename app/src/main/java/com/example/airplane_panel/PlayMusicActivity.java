package com.example.airplane_panel;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.transition.ChangeBounds;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
import com.example.airplane_panel.media.BaseMusicActivity;

import com.example.airplane_panel.databinding.ActivityPlayMusicBinding;
import com.example.airplane_panel.remote.RemoteControllerListener;
import com.example.airplane_panel.remote.RemoteControllerService;

interface PlayMusicView extends BaseView {
    void updateMusicState(Media music, boolean isPlaying, int progress);
}

class PlayMusicPresenter extends BasePresenter<PlayMusicView> {
    private PlayerService player;
    private PlayMusicView context;

    public PlayMusicPresenter(PlayMusicView view) {
        super(view);
        context = view;
    }

    public void setPlayerManager(PlayerService player) {
        this.player = player;
        fetchMusicState();
    }

    public void fetchMusicState() {
        if (player.getMedia(false) != null) {
            context.updateMusicState(player.getMedia(false), player.isPlaying(), player.getProgress());
        }
    }

    public void onMusicPlay() {
        if (!player.isPlaying()) {
            player.play();
        } else {
            player.pause();
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

public class PlayMusicActivity extends BaseMusicActivity<PlayMusicPresenter> implements PlayMusicView, RemoteControllerListener {
    private RemoteControllerService service;
    private boolean isBound = false;
    private RemoteControllerService.LocalBinder binder;
    private AudioManager audioManager;

    private ActivityPlayMusicBinding viewBinding;
    private Animation wheelAnimation;
    private Animation scaleAnimation;
    private Runnable seekBarUpdateRunnable;
    private static final long seekBarUpdateDelayMillis = 1000;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder serviceBinder) {
            try {
                binder = (RemoteControllerService.LocalBinder) serviceBinder;
                service = binder.getService();
                binder.registerListener(PlayMusicActivity.this);
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
        viewBinding = ActivityPlayMusicBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());

        presenter = createPresenter();
        // Initialize animations and other UI elements
        initElementAnimation();
        initSeekBarUpdateRunnable();
        setListen();

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
        viewBinding.seekBar.removeCallbacks(seekBarUpdateRunnable);
        if (isBound) {
            binder.unregisterListener(this);
            unbindService(connection);
            isBound = false;
        }
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

        // Set up UI listeners
        setListen();
    }

    @Override
    public void updateState() {
        presenter.fetchMusicState();
    }

    @Override
    protected PlayMusicPresenter createPresenter() {
        return new PlayMusicPresenter(this);
    }

    @Override
    public void updateMusicState(Media music, boolean isPlaying, int progress) {
        viewBinding.seekBar.removeCallbacks(seekBarUpdateRunnable);

        long bar_progress = progress * 1000L;
        long bar_progress_minutes = bar_progress / 60000;
        long bar_progress_seconds = (bar_progress % 60000) / 1000;

        viewBinding.tvName.setText(music.getName());
        viewBinding.tvDuration.setText(music.toTimeString());
        viewBinding.seekBar.setMax((int) (music.getDuration() / 1000));
        viewBinding.seekBar.setProgress(progress);
        viewBinding.tvProgress.setText(String.format("%02d:%02d", bar_progress_minutes, bar_progress_seconds));

        Drawable drawable = ContextCompat.getDrawable(this, isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        drawable = DrawableCompat.wrap(drawable);
        viewBinding.imgPlay.setImageDrawable(drawable);

        // 更新封面图像
        Bitmap coverImageResBitmap = music.getCoverImageResourceBitmap();

        if (coverImageResBitmap != null) {
            viewBinding.flCover.clearAnimation();
            viewBinding.imgCover.setImageBitmap(coverImageResBitmap);
            viewBinding.imgCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            viewBinding.imgCover.setScaleType(ImageView.ScaleType.FIT_CENTER);
            // 使用默认封面
            viewBinding.imgCover.setImageResource(R.drawable.ic_disc);
            if (isPlaying) {
                viewBinding.flCover.startAnimation(wheelAnimation);
            } else {
                viewBinding.flCover.clearAnimation();
            }
        }

        if (isPlaying) {
            if(service!=null)
            {
                service.setDisplayName(music.getName());
            }
            viewBinding.seekBar.postDelayed(seekBarUpdateRunnable, seekBarUpdateDelayMillis);
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

    private void hideStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.statusBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void setBackground() {
        viewBinding.getRoot().setBackground(ContextCompat.getDrawable(this, R.drawable.background_media));
        viewBinding.getRoot().getBackground().setAlpha(30);
    }

    private void setScreenHigh() {
        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.getRoot(), (view, windowInsets) -> {
            int bottom = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
            layoutParams.bottomMargin = bottom;
            view.setLayoutParams(layoutParams);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void initWindowAnimations() {
        ChangeBounds enterTransition = new ChangeBounds();
        enterTransition.setDuration(1000);
        enterTransition.setInterpolator(new DecelerateInterpolator());
        getWindow().setSharedElementEnterTransition(enterTransition);
    }

    private void initElementAnimation() {
        wheelAnimation = AnimationUtils.loadAnimation(this, R.anim.rotation_wheel);
        wheelAnimation.setDuration(1000);
        wheelAnimation.setRepeatCount(Animation.INFINITE);

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

        viewBinding.imgPlay.setOnClickListener(v -> presenter.onMusicPlay());

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
            presenter.onMusicPlay();
        }
        else
        {
            if(key==Key.Down) {
                super.onBackPressed();
            }
            else if(key==Key.Left) {
                presenter.skipToPrevious();
                viewBinding.imgBackward.startAnimation(scaleAnimation);
            }
            else if(key==Key.Right) {
                presenter.skipToNext();
                viewBinding.imgForward.startAnimation(scaleAnimation);
            }
        }
    }
}
