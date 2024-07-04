package com.example.airplane_panel.media;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import java.io.IOException;
import java.util.Collections;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.example.airplane_panel.MusicActivity;
import com.example.airplane_panel.R;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FileDescriptor;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class PlayerService extends Service implements PropertyChangeListener {
    public static final String ACTION_COMPLETE = PlayerManager.ACTION_COMPLETE;
    public static final String ACTION_PLAY = PlayerManager.ACTION_PLAY;
    public static final String ACTION_PAUSE = PlayerManager.ACTION_PAUSE;
    public static final String ACTION_STOP = PlayerManager.ACTION_STOP;
    public static final String CHANNEL_ID_MUSIC = "app.MUSIC";
    public static final String CHANNEL_NAME_MUSIC = "Music";
    public static final int NOTIFICATION_ID_MUSIC = 101;

    public static final int BROADCAST_ID_MUSIC = 201;
    public static final String NOTIFICATION_PREVIOUS = "notification.PREVIOUS";
    public static final String NOTIFICATION_PLAY = "notification.PLAY";
    public static final String NOTIFICATION_NEXT = "notification.NEXT";
    public static final String NOTIFICATION_CANCEL = "notification.CANCEL";

    public static final String ACTION_FIND_NEW_MUSIC = "action.FIND_NEW_MUSIC";
    public static final String ACTION_FIND_NEW_VIDEO = "action.FIND_NEW_VIDEO";
    public static final String ACTION_NOT_FOUND = "action.NOT_FOUND";

    private RemoteViews smallRemoteView;
    private RemoteViews largeRemoteView;
    private PendingIntent intentPrevious, intentPlay, intentNext, intentCancel;

    private MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();

    private MediaObserver musicObserver = null;
    private MediaObserver videoObserver = null;

    private PlayerManager playerManager = null;
    private List<Media> musicList = new ArrayList<>();
    private List<Media> videoList = new ArrayList<>();
    private int playerPosition = 0;
    private boolean isPlaying = false;
    private SurfaceHolder display_holder = null;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case NOTIFICATION_PREVIOUS:
                    skipToPrevious();
                    break;
                case NOTIFICATION_PLAY:
                    if (isPlaying) {
                        pause();
                    } else {
                        if(display_holder==null)
                        {
                            play();
                        }
                        else
                        {
                            play(display_holder);
                        }
                    }
                    break;
                case NOTIFICATION_NEXT:
                    skipToNext();
                    break;
                case NOTIFICATION_CANCEL:
                    pause();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(Service.STOP_FOREGROUND_DETACH);
                    } else {
                        stopForeground(true);
                    }
                    stopSelf();
                    break;
            }
        }
    };
    private Handler musicHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            String id = msg.getData().getString("mediaID");

            Uri mediaUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);

            try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(mediaUri, "r")) {
                if (addMedia(pfd.getFileDescriptor(), id, getMediaTitle(mediaUri, false), false)) {
                    playerManager.setChangedNotify(ACTION_FIND_NEW_MUSIC);
                }
            } catch (Exception e) {
                e.printStackTrace(); // Handle exceptions appropriately.
            }
        }
    };
    private Handler videoHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            String id = msg.getData().getString("mediaID");

            Uri mediaUri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);

            try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(mediaUri, "r")) {
                if (addMedia(pfd.getFileDescriptor(), id, getMediaTitle(mediaUri, true), true)) {
                    playerManager.setChangedNotify(ACTION_FIND_NEW_VIDEO);
                }
            } catch (Exception e) {
                e.printStackTrace(); // Handle exceptions appropriately.
            }
        }
    };
    public class LocalBinder extends Binder {
        private WeakReference<PlayerService> serviceWeakReference;

        LocalBinder(PlayerService service) {
            serviceWeakReference = new WeakReference<>(service);
        }

        public PlayerService getService() {
            return serviceWeakReference.get();
        }
    }

    private IBinder binder = new LocalBinder(this);

    @Override
    public void onCreate() {
        super.onCreate();

        musicObserver = new MediaObserver(musicHandler);
        videoObserver = new MediaObserver(videoHandler);
        playerManager = new PlayerManager(); // 确保此时 this（Service）已经准备好

        createNotificationChannel();
        initRemoteView();

        getContentResolver().registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, musicObserver);
        getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, videoObserver);

        registerReceiver();
        addPlayerObserver(this);

        binder = new LocalBinder(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        binder = null;

        getContentResolver().unregisterContentObserver(musicObserver);
        getContentResolver().unregisterContentObserver(videoObserver);
        unregisterReceiver(receiver);
        try {
            metaRetriever.release();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        deletePlayerObserver(this);
        playerManager.stop();

        super.onDestroy();
    }

    @SuppressLint("ForegroundServiceType")
    @Override
    public void propertyChange(PropertyChangeEvent event) {
        boolean isVideo = playerManager.isVideoMedia();

        switch (event.getPropertyName()) {
            case ACTION_COMPLETE:
                playerManager.setPlayerProgress(0);

                skipToNext();
                break;

            case ACTION_PLAY:
            case ACTION_PAUSE:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID_MUSIC, createNotification(isVideo), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                } else {
                    startForeground(NOTIFICATION_ID_MUSIC, createNotification(isVideo));
                }
                break;

            case ACTION_STOP:
                isPlaying = false;
                break;
        }
    }

    private boolean addMedia(FileDescriptor fd, String id, String title, boolean isVideo) {
        try {
            if (fd == null || !fd.valid()) {
                return false;
            }

            metaRetriever.setDataSource(fd);

            String durationStr = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String artist = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String author = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR);

            if (durationStr == null || durationStr.isEmpty()) {
                return false;
            }

            long duration = Long.parseLong(durationStr);
            if (artist == null) {
                artist = author;
            }
            if (artist == null) {
                artist = getString(R.string.unknown);
            }

            // 提取封面图像
            if(isVideo) {
                Media m = null;

                Bitmap firstFrame = metaRetriever.getFrameAtTime();

                m = new Media(id, title, artist, duration, firstFrame);

                if (!videoList.contains(m)) {
                    videoList.add(m);
                }
            }
            else
            {
                byte[] artBytes = metaRetriever.getEmbeddedPicture();
                Bitmap coverImage = null;
                Media m = null;
                if (artBytes != null) {
                    coverImage = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length);
                    m = new Media(id, title, artist, duration, coverImage);
                }
                else{
                    m = new Media(id, title, artist, duration);
                }

                if (!musicList.contains(m)) {
                    musicList.add(m);
                }
            }

        } catch (Exception e) {
            Log.e("PlayerService", "Error adding media", e);
            return false;
        }
        return true;
    }

    public void readMedia(boolean isVideo) {
        Executors.newSingleThreadExecutor().execute(() -> {
            if(isVideo)
            {
                if (!videoList.isEmpty()) return;
            }
            else
            {
                if (!musicList.isEmpty()) return;
            }

            // 根据isVideo选择合适的URI
            Uri uri_ext = isVideo ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

            try (Cursor cursor = getContentResolver().query(uri_ext, null, null, null, null)) {
                if (cursor == null) return;

                int indexID;
                int indexTitle;

                if(isVideo)
                {
                    indexID = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                    indexTitle = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE);
                }
                else
                {
                    indexID = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                    indexTitle = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                }

                while (cursor.moveToNext()) {
                    String id = cursor.getString(indexID);
                    String title = cursor.getString(indexTitle);
                    Uri audioUri = Uri.withAppendedPath(uri_ext, id);

                    try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(audioUri, "r")) {
                        if (pfd != null && addMedia(pfd.getFileDescriptor(), id, title, isVideo)) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                    if(isVideo)
                                    {
                                        playerManager.setChangedNotify(ACTION_FIND_NEW_VIDEO);
                                    }
                                    else
                                    {
                                        playerManager.setChangedNotify(ACTION_FIND_NEW_MUSIC);
                                    }
                                }
                            );
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        // Handle exception
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                // Handle exception
            }
        });
    }

    public void addPlayerObserver(PropertyChangeListener listener) {
        playerManager.addPropertyChangeListener(listener);
    }

    public void deletePlayerObserver(PropertyChangeListener listener) {
        playerManager.removePropertyChangeListener(listener);
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public List<Media> getMediaList(boolean isVideo) {
        if(isVideo)
        {
            return Collections.unmodifiableList(videoList);
        }
        else
        {
            return Collections.unmodifiableList(musicList);
        }
    }

    public Media getMedia(boolean isVideo) {
        if(isVideo)
        {
            if (playerPosition >= 0 && playerPosition < videoList.size()) {
                return videoList.get(playerPosition);
            } else {
                return null; // Or handle index out of bounds as needed
            }
        }
        else
        {
            if (playerPosition >= 0 && playerPosition < musicList.size()) {
                return musicList.get(playerPosition);
            } else {
                return null; // Or handle index out of bounds as needed
            }
        }
    }

    public Media getMedia(int position, boolean isVideo) {
        if(isVideo)
        {
            if (position >= 0 && position < videoList.size()) {
                return videoList.get(position);
            } else {
                return null; // Or handle index out of bounds as needed
            }
        }
        else
        {
            if (position >= 0 && position < musicList.size()) {
                return musicList.get(position);
            } else {
                return null; // Or handle index out of bounds as needed
            }
        }
    }

    public int getProgress() {
        return playerManager.getPlayerProgress();
    }

    public void play() {
        play(playerPosition);
    }
    public void play(int position) {
        isPlaying = true;
        display_holder = null;

        if (musicList.isEmpty()) {
            playerManager.setChangedNotify(ACTION_NOT_FOUND);
            return;
        }

        // Is different song
        if (position != playerPosition || playerManager.isVideoMedia()) {
            playerManager.setPlayerProgress(0);
        }

        if (position >= musicList.size()) {
            position = 0;
        } else if (position < 0) {
            position = musicList.size() - 1;
        }

        playerPosition = position;

        Media currentMedia = musicList.get(playerPosition);

        Uri mediaUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, currentMedia.getId());

        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(mediaUri, "r")) {
            if (pfd != null) {
                playerManager.play(pfd.getFileDescriptor());
            } else {
                musicList.remove(playerPosition);

                playerManager.setChangedNotify(ACTION_NOT_FOUND);
                play(playerPosition); // Try next available song
            }
        } catch (Exception e) {
            e.printStackTrace(); // Handle exception appropriately
        }
    }

    public void play(SurfaceHolder holder) {
        play(playerPosition, holder);
    }
    public void play(int position, SurfaceHolder holder) {
        isPlaying = true;
        display_holder = holder;

        if (videoList.isEmpty()) {
            playerManager.setChangedNotify(ACTION_NOT_FOUND);
            return;
        }

        // Is different song
        if (position != playerPosition || !playerManager.isVideoMedia()) {
            playerManager.setPlayerProgress(0);
        }

        if (position >= videoList.size()) {
            position = 0;
        } else if (position < 0) {
            position = videoList.size() - 1;
        }

        playerPosition = position;

        Media currentMedia = videoList.get(playerPosition);

        Uri mediaUri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, currentMedia.getId());

        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(mediaUri, "r")) {
            if (pfd != null) {
                playerManager.play(pfd.getFileDescriptor(), display_holder);
            } else {
                videoList.remove(playerPosition);

                playerManager.setChangedNotify(ACTION_NOT_FOUND);
                play(playerPosition, display_holder); // Try next available song
            }
        } catch (Exception e) {
            e.printStackTrace(); // Handle exception appropriately
        }
    }

    public void pause() {
        isPlaying = false;
        playerManager.pause();
    }

    public void seekTo(int progress) {
        if (isPlaying) {
            playerManager.seekTo(progress);
        } else {
            playerManager.setPlayerProgress(progress);

            boolean isVideo = playerManager.isVideoMedia();
            if(isVideo)
            {
                play(playerPosition, display_holder);
            }
            else
            {
                play(playerPosition);
            }
        }
    }

    public void skipToNext() {
        boolean isVideo = playerManager.isVideoMedia();
        if(isVideo)
        {
            play(playerPosition + 1, display_holder);
        }
        else
        {
            play(playerPosition + 1);
        }
    }

    public void skipToPrevious() {
        boolean isVideo = playerManager.isVideoMedia();
        if(isVideo)
        {
            play(playerPosition - 1, display_holder);
        }
        else
        {
            play(playerPosition - 1);
        }
    }

    private String getMediaTitle(Uri uri, boolean isVideo) {
        String title = "";

        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                if(isVideo)
                {
                    title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE));
                }
                else
                {
                    title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
                }
            }
        } catch (Exception e) {
            // Handle any exceptions
        }

        return title;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel status = new NotificationChannel(
                    CHANNEL_ID_MUSIC, CHANNEL_NAME_MUSIC, NotificationManager.IMPORTANCE_LOW
            );
            status.setDescription("Music player");

            nm.createNotificationChannel(status);
        }
    }

    private void initRemoteView() {
        smallRemoteView = new RemoteViews(getPackageName(), R.layout.notification_small);
        largeRemoteView = new RemoteViews(getPackageName(), R.layout.notification_large);

        intentPrevious = PendingIntent.getBroadcast(
                this,
                BROADCAST_ID_MUSIC,
                new Intent(NOTIFICATION_PREVIOUS).setPackage(getPackageName()),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        intentPlay = PendingIntent.getBroadcast(
                this,
                BROADCAST_ID_MUSIC,
                new Intent(NOTIFICATION_PLAY).setPackage(getPackageName()),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        intentNext = PendingIntent.getBroadcast(
                this,
                BROADCAST_ID_MUSIC,
                new Intent(NOTIFICATION_NEXT).setPackage(getPackageName()),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        intentCancel = PendingIntent.getBroadcast(
                this,
                BROADCAST_ID_MUSIC,
                new Intent(NOTIFICATION_CANCEL).setPackage(getPackageName()),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void registerReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(NOTIFICATION_PREVIOUS);
        intentFilter.addAction(NOTIFICATION_PLAY);
        intentFilter.addAction(NOTIFICATION_NEXT);
        intentFilter.addAction(NOTIFICATION_CANCEL);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(receiver, intentFilter);
        }
    }

    private Notification createNotification(boolean isVideo) {
        Media media = getMedia(isVideo); // Assume you have a method to get the current media

        // Update RemoteViews as needed
        smallRemoteView.setTextViewText(R.id.tv_name, media.getName());
        // Repeat for other updates

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID_MUSIC)
                .setSmallIcon(R.drawable.ic_music)
                .setContentTitle(media.getName())
                .setContentText(media.getAuthor())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setContentIntent(createContentIntent())
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(smallRemoteView)
                .setCustomBigContentView(largeRemoteView); // Show full RemoteView

        return notificationBuilder.build();
    }

    private PendingIntent createContentIntent() {
        Intent intent = new Intent(this, MusicActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
