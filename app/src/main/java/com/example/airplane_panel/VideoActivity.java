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
import android.view.SurfaceHolder;
import android.view.SurfaceView;
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

import com.example.airplane_panel.databinding.AdapterVideoListBinding;
import com.example.airplane_panel.media.BaseVideoActivity;
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

import com.example.airplane_panel.databinding.ActivityVideoBinding;
import com.example.airplane_panel.databinding.DialogLoadingBinding;

class VideoListAdapter extends ListAdapter<Media, VideoListAdapter.VideoHolder> {
    private int selectedPos = RecyclerView.NO_POSITION; // Track selected position

    private VideoListPresenter presenter;

    public int getSelectedPosition() {
        return selectedPos;
    }

    public void changeSelection(int newPosition) {
        notifyItemChanged(selectedPos); // Deselect old item
        selectedPos = newPosition;
        notifyItemChanged(selectedPos); // Select new item
        presenter.onSelectionChanged(selectedPos); // Notify presenter about the change
    }

    public VideoListAdapter(VideoListPresenter presenter) {
        super(new VideoItemCallback());
        this.presenter = presenter;
    }

    @NonNull
    @Override
    public VideoHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AdapterVideoListBinding viewBinding = AdapterVideoListBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new VideoHolder(viewBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoHolder holder, @SuppressLint("RecyclerView") int position) {
        Media video = getItem(position);

        holder.viewBinding.tvName.setText(video.getName());
        holder.viewBinding.tvArtist.setText(video.getAuthor());
        holder.viewBinding.tvDuration.setText(video.toTimeString());

        // 檢查是否有專輯圖片，並設置對應的圖片
        // 更新封面图像
        Bitmap coverImageResBitmap = video.getCoverImageResourceBitmap();

        if (coverImageResBitmap != null) {
            holder.viewBinding.imageView.setImageBitmap(coverImageResBitmap);
            holder.viewBinding.imageView.setScaleType(ImageView.ScaleType.CENTER);
        }

        // Change background based on selection
        holder.itemView.setBackground(ContextCompat.getDrawable(holder.itemView.getContext(),
                position == selectedPos ? R.drawable.highlighted_background : R.drawable.normal_background));

        if(position>=0) {
            holder.itemView.setOnClickListener(v -> {
                notifyItemChanged(selectedPos); // Redraw the previously selected item
                selectedPos = position; // Update with new position
                notifyItemChanged(selectedPos); // Redraw the newly selected item
                presenter.onVideoClick(position);
            });
        }
    }

    class VideoHolder extends RecyclerView.ViewHolder {
        AdapterVideoListBinding viewBinding;

        VideoHolder(AdapterVideoListBinding viewBinding) {
            super(viewBinding.getRoot());
            this.viewBinding = viewBinding;
        }
    }

    public static class VideoItemCallback extends DiffUtil.ItemCallback<Media> {
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

interface VideoListView extends BaseView {
    void scrollToPosition(int position);
    void showLoading();

    void stopLoading();

    void onVideoClick(int position);
}

class VideoListPresenter extends BasePresenter<VideoListView> {
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private PlayerService player;
    private VideoListView context;
    private VideoListAdapter adapter;
    private SparseArray<Media> filteredVideoList = new SparseArray<>();

    public void onSelectionChanged(int newPosition) {
        view.scrollToPosition(newPosition); // Inform the view to scroll to new position
    }

    public int getMaxPosition(){return filteredVideoList.size();}
    public int getMinPosition(){return 0;}

    public int getSelectedPosition() {
        return adapter.getSelectedPosition();
    }

    public void changeSelection(int newPosition) {
        adapter.changeSelection(newPosition);
    }

    public VideoListPresenter(VideoListView view) {
        super(view);
        context = view;
    }

    public void setPlayerManager(PlayerService player) {
        this.player = player;
        loadVideoList();
    }

    public void setAdapter(VideoListAdapter adapter) {
        this.adapter = adapter;
    }

    public void filterVideo(String key) {
        executorService.execute(() -> {
            filteredVideoList.clear();

            List<Media> list = new ArrayList<>();
            List<Media> allVideos = player.getMediaList(true);
            for (int index = 0; index < allVideos.size(); index++) {
                Media video = allVideos.get(index);
                if (video.getName().toLowerCase().contains(key.toLowerCase()) ||
                        video.getAuthor().toLowerCase().contains(key.toLowerCase())) {
                    filteredVideoList.put(index, video);
                    list.add(video);
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

    public void onVideoClick(int index) {
        int position = -1;
        for(int i = 0; i<filteredVideoList.size(); i++)
        {
            int found_idx = filteredVideoList.keyAt(i);
            if(found_idx==index)
            {
                position = found_idx;
                break;
            }
        }
        if(position>=0) {
            context.onVideoClick(position);
        }
    }

    public void loadVideoList() {
        new Thread(() -> {
            // Assuming showLoading and stopLoading are methods to manage loading UI. You might need to implement them.
            view.showLoading();

            player.readMedia(true);
            List<Media> videoList = player.getMediaList(true);
            // Run on UI thread
            new Handler(Looper.getMainLooper()).post(() -> {
                view.stopLoading();
                adapter.submitList(videoList);
            });
        }).start();
    }
}

public class VideoActivity extends BaseVideoActivity<VideoListPresenter> implements VideoListView, RemoteControllerListener {
    private RemoteControllerService service;
    private boolean isBound = false;
    private RemoteControllerService.LocalBinder binder;
    private AudioManager audioManager;
    private ActivityVideoBinding viewBinding;
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
                binder.registerListener(VideoActivity.this);
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
        viewBinding = ActivityVideoBinding.inflate(getLayoutInflater());
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
        presenter.filterVideo(viewBinding.edName.getText().toString());
        presenter.setPlayerManager(player);
        setListen();
    }

    @Override
    public void updateState() {
        presenter.filterVideo(viewBinding.edName.getText().toString());
    }

    @Override
    public VideoListPresenter createPresenter() {
        return new VideoListPresenter(this);
    }

    @Override
    public void showLoading() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                DialogLoadingBinding loadViewBinding =
                        DialogLoadingBinding.inflate(LayoutInflater.from(VideoActivity.this));

                final ValueAnimator animator = ValueAnimator.ofInt(0, 8);
                animator.setDuration(750);
                animator.setInterpolator(new LinearInterpolator());
                animator.setRepeatCount(ValueAnimator.INFINITE);

                animator.addUpdateListener(animation -> {
                    loadViewBinding.imgLoad.setRotation((Integer) animation.getAnimatedValue() * 45f);
                    loadViewBinding.imgLoad.requestLayout();
                });

                loadingDialog = new MaterialAlertDialogBuilder(VideoActivity.this)
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
    public void propertyChange(PropertyChangeEvent event) {
        switch (event.getPropertyName()) {
            case PlayerService.ACTION_FIND_NEW_VIDEO:
            case PlayerService.ACTION_NOT_FOUND:
                presenter.filterVideo(viewBinding.edName.getText().toString());
                break;
        }
    }

    @Override
    public void onVideoClick(int position) {
        hideKeyBoard();

        Intent intent = new Intent(VideoActivity.this, PlayVideoActivity.class);
        intent.putExtra("video_position", position);

        startActivity(intent);
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
        VideoListAdapter adapter = new VideoListAdapter(presenter);
        viewBinding.recyclerView.setAdapter(adapter);
        presenter.setAdapter(adapter);

        LayoutAnimationController controller = new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.fade_in_from_bottom)
        );
        controller.setOrder(LayoutAnimationController.ORDER_NORMAL);
        controller.setDelay(0.3f);
        viewBinding.recyclerView.setLayoutAnimation(controller);
    }

    private void setListen() {
        viewBinding.edName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                presenter.filterVideo(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
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
            presenter.onVideoClick(presenter.getSelectedPosition());
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
