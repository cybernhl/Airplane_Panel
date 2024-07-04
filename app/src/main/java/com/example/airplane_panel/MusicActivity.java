package com.example.airplane_panel;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.util.SparseArray;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.airplane_panel.databinding.AdapterMusicListBinding;
import com.example.airplane_panel.media.BaseMusicActivity;
import com.example.airplane_panel.media.BaseView;
import com.example.airplane_panel.remote.RemoteControllerListener;
import com.example.airplane_panel.remote.RemoteControllerService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.beans.PropertyChangeEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.airplane_panel.media.Media;
import com.example.airplane_panel.media.BasePresenter;
import com.example.airplane_panel.media.PlayerManager;
import com.example.airplane_panel.media.PlayerService;

import com.example.airplane_panel.databinding.ActivityMusicBinding;
import com.example.airplane_panel.databinding.DialogLoadingBinding;

class MusicListAdapter extends ListAdapter<Media, MusicListAdapter.MusicHolder> {
    private int selectedPos = RecyclerView.NO_POSITION; // Track selected position

    private MusicListPresenter presenter;

    public int getSelectedPosition() {
        return selectedPos;
    }

    public void changeSelection(int newPosition) {
        notifyItemChanged(selectedPos); // Deselect old item
        selectedPos = newPosition;
        notifyItemChanged(selectedPos); // Select new item
        presenter.onSelectionChanged(selectedPos); // Notify presenter about the change
    }

    public MusicListAdapter(MusicListPresenter presenter) {
        super(new MusicItemCallback());
        this.presenter = presenter;
    }

    @NonNull
    @Override
    public MusicHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AdapterMusicListBinding viewBinding = AdapterMusicListBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new MusicHolder(viewBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull MusicHolder holder, @SuppressLint("RecyclerView") int position) {
        Media music = getItem(position);

        holder.viewBinding.tvName.setText(music.getName());
        holder.viewBinding.tvArtist.setText(music.getAuthor());
        holder.viewBinding.tvDuration.setText(music.toTimeString());

        // 檢查是否有專輯圖片，並設置對應的圖片
        // 更新封面图像
        Bitmap coverImageResBitmap = music.getCoverImageResourceBitmap();

        if (coverImageResBitmap != null) {
            holder.viewBinding.imageView.setImageBitmap(coverImageResBitmap);
            holder.viewBinding.imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }

        // Change background based on selection
        holder.itemView.setBackground(ContextCompat.getDrawable(holder.itemView.getContext(),
                position == selectedPos ? R.drawable.highlighted_background : R.drawable.normal_background));

        if(position>=0) {
            holder.itemView.setOnClickListener(v -> {
                notifyItemChanged(selectedPos); // Redraw the previously selected item
                selectedPos = position; // Update with new position
                notifyItemChanged(selectedPos); // Redraw the newly selected item
                presenter.onMusicClick(position);
            });
        }
    }

    class MusicHolder extends RecyclerView.ViewHolder {
        AdapterMusicListBinding viewBinding;

        MusicHolder(AdapterMusicListBinding viewBinding) {
            super(viewBinding.getRoot());
            this.viewBinding = viewBinding;
        }
    }

    public static class MusicItemCallback extends DiffUtil.ItemCallback<Media> {
        @Override
        public boolean areItemsTheSame(@NonNull Media oldItem, @NonNull Media newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Media oldItem, @NonNull Media newItem) {
            return oldItem.equals(newItem);
        }
    }
}

interface MusicListView extends BaseView {
    void scrollToPosition(int position);
    void showLoading();

    void stopLoading();

    void updateMusicState(Media music, boolean isPlaying);

    void onMusicClick();
}

class MusicListPresenter extends BasePresenter<MusicListView> {
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private PlayerService player;
    private MusicListView context;
    private MusicListAdapter adapter;
    private SparseArray<Media> filteredMusicList = new SparseArray<>();

    public void onSelectionChanged(int newPosition) {
        view.scrollToPosition(newPosition); // Inform the view to scroll to new position
    }

    public int getMaxPosition(){return filteredMusicList.size();}
    public int getMinPosition(){return 0;}

    public int getSelectedPosition() {
        return adapter.getSelectedPosition();
    }

    public void changeSelection(int newPosition) {
        adapter.changeSelection(newPosition);
    }

    public MusicListPresenter(MusicListView view) {
        super(view);
        context = view;
    }

    public void setPlayerManager(PlayerService player) {
        this.player = player;
        loadMusicList();
    }

    public void setAdapter(MusicListAdapter adapter) {
        this.adapter = adapter;
    }

    public void fetchMusicState() {
        Media music = player.getMedia(false);
        if (music != null) {
            context.updateMusicState(music, player.isPlaying());
        }
    }

    public void filterMusic(String key) {
        executorService.execute(() -> {
            filteredMusicList.clear();

            List<Media> list = new ArrayList<>();
            List<Media> allMusics = player.getMediaList(false);
            for (int index = 0; index < allMusics.size(); index++) {
                Media music = allMusics.get(index);
                if (music.getName().toLowerCase().contains(key.toLowerCase()) ||
                        music.getAuthor().toLowerCase().contains(key.toLowerCase())) {
                    filteredMusicList.put(index, music);
                    list.add(music);
                }
            }

            // Create a new Handler attached to the Main thread
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    // This code will now be executed on the main thread
                    adapter.submitList(list);
                }
            });
        });
    }

    public void onMusicPlay() {
        if (!player.isPlaying()) {
            player.play();
        } else {
            player.pause();
        }
    }

    public void onMusicClick(int index) {
        int position = -1;
        for(int i = 0; i<filteredMusicList.size(); i++)
        {
            int found_idx = filteredMusicList.keyAt(i);
            if(found_idx==index)
            {
                position = found_idx;
                break;
            }
        }
        if(position>=0) {
            context.onMusicClick();
            playMusic(position);
        }
    }

    public void loadMusicList() {
        new Thread(() -> {
            // Assuming showLoading and stopLoading are methods to manage loading UI. You might need to implement them.
            view.showLoading();

            player.readMedia(false);
            List<Media> musicList = player.getMediaList(false);
            // Run on UI thread
            new Handler(Looper.getMainLooper()).post(() -> {
                view.stopLoading();
                adapter.submitList(musicList);
                fetchMusicState();
            });
        }).start();
    }

    public void playMusic(int position) {
        player.play(position);
    }

}

public class MusicActivity extends BaseMusicActivity<MusicListPresenter> implements MusicListView, RemoteControllerListener {
    private RemoteControllerService service;
    private boolean isBound = false;
    private RemoteControllerService.LocalBinder binder;
    private AudioManager audioManager;
    private ActivityMusicBinding viewBinding;
    private AlertDialog loadingDialog = null;
    private Animation wheelAnimation;

    @Override
    public void scrollToPosition(int position) {
        viewBinding.recyclerView.scrollToPosition(position); // Scroll RecyclerView to the position
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder serviceBinder) {
            try {
                binder = (RemoteControllerService.LocalBinder) serviceBinder;
                service = binder.getService();
                binder.registerListener(MusicActivity.this);
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

        //registerOnBackPress();
        viewBinding = ActivityMusicBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        setBackground();
        initElementAnimation();
        initRecyclerView();

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
    protected void onDestroy() {
        if (loadingDialog != null) {
            loadingDialog.dismiss();
            loadingDialog = null;
        }
        super.onDestroy();
    }

    @Override
    public void playerBound(PlayerService player) {
        presenter.filterMusic(viewBinding.edName.getText().toString());
        presenter.setPlayerManager(player);
        setListen();
    }

    @Override
    public void updateState() {
        presenter.filterMusic(viewBinding.edName.getText().toString());
        presenter.fetchMusicState();
    }

    @Override
    public MusicListPresenter createPresenter() {
        return new MusicListPresenter(this);
    }

    @Override
    public void showLoading() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                DialogLoadingBinding loadViewBinding =
                        DialogLoadingBinding.inflate(LayoutInflater.from(MusicActivity.this));

                final ValueAnimator animator = ValueAnimator.ofInt(0, 8);
                animator.setDuration(750);
                animator.setInterpolator(new LinearInterpolator());
                animator.setRepeatCount(ValueAnimator.INFINITE);

                animator.addUpdateListener(animation -> {
                    loadViewBinding.imgLoad.setRotation((Integer) animation.getAnimatedValue() * 45f);
                    loadViewBinding.imgLoad.requestLayout();
                });

                loadingDialog = new MaterialAlertDialogBuilder(MusicActivity.this)
                        .create();
                loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                loadingDialog.setView(loadViewBinding.getRoot());
                loadingDialog.setCancelable(false);
                loadingDialog.setOnDismissListener(dialogInterface -> {
                    animator.removeAllListeners();
                    animator.cancel();
                });
                loadingDialog.show();
                animator.start();
            }
        });
    }

    @Override
    public void stopLoading() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (loadingDialog != null) {
                    loadingDialog.dismiss();
                    loadingDialog = null;
                }
                viewBinding.recyclerView.scheduleLayoutAnimation();
            }
        });
    }

    @Override
    public void updateMusicState(Media music, boolean isPlaying) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                viewBinding.tvName.setText(music.getName());
                viewBinding.tvArtist.setText(music.getAuthor());
                viewBinding.btnPlay.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
                if (isPlaying) {
                    viewBinding.imgDisc.startAnimation(wheelAnimation);
                } else {
                    viewBinding.imgDisc.clearAnimation();
                }
                if(service!=null)
                {
                    service.setDisplayName(music.getName());
                }
            }
        });
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        switch (event.getPropertyName()) {
            case PlayerManager.ACTION_PLAY:
            case PlayerManager.ACTION_PAUSE:
                presenter.fetchMusicState();
                break;
            case PlayerService.ACTION_FIND_NEW_MUSIC:
            case PlayerService.ACTION_NOT_FOUND:
                presenter.filterMusic(viewBinding.edName.getText().toString());
                break;
        }
    }

    @Override
    public void onMusicClick() {
        hideKeyBoard();
        viewBinding.bottomAppBar.performShow();
    }

    private void setBackground() {
        viewBinding.getRoot().setBackground(ContextCompat.getDrawable(this, R.drawable.background_media));
        viewBinding.getRoot().getBackground().setAlpha(30);
    }

    private void initElementAnimation() {
        wheelAnimation = AnimationUtils.loadAnimation(this, R.anim.rotation_wheel);
        wheelAnimation.setDuration(1000);
        wheelAnimation.setRepeatCount(ValueAnimator.INFINITE);
    }

    private void initRecyclerView() {
        viewBinding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        MusicListAdapter adapter = new MusicListAdapter(presenter);
        viewBinding.recyclerView.setAdapter(adapter);
        presenter.setAdapter(adapter);

        LayoutAnimationController controller = new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.fade_in_from_bottom)
        );
        controller.setOrder(LayoutAnimationController.ORDER_NORMAL);
        controller.setDelay(0.3f);
        viewBinding.recyclerView.setLayoutAnimation(controller);
    }

    private void goto_play_music_activity() {
        if (!(viewBinding.tvName.getText().length() == 0) || !(viewBinding.tvArtist.getText().length() == 0)) {
            Pair<View, String> p1 = Pair.create(viewBinding.imgDisc, viewBinding.imgDisc.getTransitionName());
            Pair<View, String> p2 = Pair.create(viewBinding.tvName, viewBinding.tvName.getTransitionName());
            Pair<View, String> p3 = Pair.create(viewBinding.btnPlay, viewBinding.btnPlay.getTransitionName());

            ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(MusicActivity.this, p1, p2, p3);

            startActivity(new Intent(MusicActivity.this, PlayMusicActivity.class), options.toBundle());
        }
    }

    private void setListen() {
        viewBinding.edName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                presenter.filterMusic(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        viewBinding.btnPlay.setOnClickListener(v -> {
            presenter.onMusicPlay();
            viewBinding.bottomAppBar.performShow();
        });

        viewBinding.bottomAppBar.setOnClickListener(v -> goto_play_music_activity());
    }

    private void hideKeyBoard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(viewBinding.edName.getWindowToken(), 0);
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
        if(key==Key.OK){
            presenter.onMusicClick(presenter.getSelectedPosition());
            goto_play_music_activity();
        }
        else {
            if (key == Key.Up || key == Key.Down)
            {
                RecyclerView.LayoutManager layoutManager = viewBinding.recyclerView.getLayoutManager();

                int next_position;
                if (layoutManager instanceof LinearLayoutManager) {
                    switch (key) {
                        case Down:
                            next_position = presenter.getSelectedPosition() + 1;
                            if(next_position>presenter.getMaxPosition())
                            {
                                next_position = presenter.getMinPosition();
                            }
                            presenter.changeSelection(next_position);
                            break;
                        case Up:
                            next_position = presenter.getSelectedPosition() - 1;
                            if(next_position<presenter.getMinPosition())
                            {
                                next_position = presenter.getMaxPosition();
                            }
                            presenter.changeSelection(next_position);
                            break;
                    }
                }
            }
            else if (key == Key.Left)
            {
                super.onBackPressed();
            }
        }
    }
}
