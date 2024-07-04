package com.example.airplane_panel.media;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.FileDescriptor;

public class PlayerManager {
    public static final String ACTION_COMPLETE = "action.COMPLETE";
    public static final String ACTION_PLAY = "action.PLAY";
    public static final String ACTION_PAUSE = "action.PAUSE";
    public static final String ACTION_STOP = "action.STOP";

    private final MediaPlayer mediaPlayer = new MediaPlayer();
    private final PropertyChangeSupport support;
    private int playerProgress = 0;
    private boolean isVideo = false;

    public PlayerManager() {
        support = new PropertyChangeSupport(true);
        setListen();
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }

    public void setChangedNotify(String event) {
        Log.i("PlayerManager", "setChangedNotify  " + event);
        support.firePropertyChange(event, null, event);
    }

    public void play(FileDescriptor fd) {
        this.isVideo = false;
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
            mediaPlayer.setDataSource(fd);
            mediaPlayer.setDisplay(null);
            mediaPlayer.prepareAsync(); // 异步准备MediaPlayer
        } catch (Exception e) {
            Log.e("PlayerManager", "Error setting data source", e);
        }
    }

    public void play(FileDescriptor fd, SurfaceHolder holder) {
        this.isVideo = true;
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
            mediaPlayer.setDataSource(fd);
            mediaPlayer.setDisplay(holder);
            mediaPlayer.prepareAsync(); // 异步准备MediaPlayer
        } catch (Exception e) {
            Log.e("PlayerManager", "Error setting data source", e);
        }
    }
    public boolean isVideoMedia() { return this.isVideo; }

    public int getPlayerProgress() {
        if (mediaPlayer.isPlaying()) {
            return mediaPlayer.getCurrentPosition() / 1000;
        } else {
            return playerProgress / 1000;
        }
    }

    public void setPlayerProgress(int value) {
        playerProgress = value * 1000;
    }

    public void seekTo(int progress) {
        setPlayerProgress(progress);
        mediaPlayer.seekTo(playerProgress);
    }

    public void pause() {
        playerProgress = mediaPlayer.getCurrentPosition();

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }

        setChangedNotify(ACTION_PAUSE);
    }

    public void stop() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
        mediaPlayer.release();
    }

    private void setListen() {
        mediaPlayer.setOnPreparedListener(mp -> {
            mediaPlayer.seekTo(playerProgress);
            mediaPlayer.start();

            setChangedNotify(ACTION_PLAY);
        });

        mediaPlayer.setOnCompletionListener(mp -> setChangedNotify(ACTION_COMPLETE));

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.e("PlayerManager", "MediaPlayer error type:" + what + ", code:" + extra + ", currentPosition:" + mp.getCurrentPosition());
            return false;
        });
    }
}
