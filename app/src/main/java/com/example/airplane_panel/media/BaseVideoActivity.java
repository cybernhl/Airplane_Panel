package com.example.airplane_panel.media;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;

import com.example.airplane_panel.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseVideoActivity<P extends BasePresenter<?>> extends BaseActivity<P> implements PropertyChangeListener {
    private final int REQUEST_WRITE_EXTERNAL_STORAGE = 10;
    private final int REQUEST_READ_MEDIA_AUDIO = 11;
    private final int REQUEST_READ_MEDIA_VIDEO = 12;

    private PlayerService player;
    private boolean isBound = false;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            PlayerService.LocalBinder localBinder = (PlayerService.LocalBinder) binder;
            player = localBinder.getService();
            if (player != null) {
                player.addPlayerObserver(BaseVideoActivity.this);
                isBound = true;
                playerBound(player);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkPermission();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, PlayerService.class);
        startService(intent);
        if ((hasPermission(Manifest.permission.READ_MEDIA_AUDIO) || hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) && !isBound) {
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (isBound) {
            player.addPlayerObserver(this);
            updateState();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            player.deletePlayerObserver(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(mConnection);
            isBound = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0) return;
        switch (requestCode) {
            case REQUEST_WRITE_EXTERNAL_STORAGE:
            case REQUEST_READ_MEDIA_AUDIO:
            case REQUEST_READ_MEDIA_VIDEO:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Intent intent = new Intent(this, PlayerService.class);
                    bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
                } else {
                    showNeedPermissionDialog();
                }
                break;
        }
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasPermission(Manifest.permission.READ_MEDIA_AUDIO)) {
                requestPermission(REQUEST_READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_AUDIO);
            }
            if (!hasPermission(Manifest.permission.READ_MEDIA_VIDEO)) {
                requestPermission(REQUEST_READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VIDEO);
            }
        } else if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            requestPermission(REQUEST_WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    private void showNeedPermissionDialog() {
        new MaterialAlertDialogBuilder(this, R.style.AnimationDialog)
                .setTitle(getString(R.string.permission_requirement))
                .setMessage(getString(R.string.need_permission_to_access_video))
                .setPositiveButton(getString(R.string.agree), (dialog, which) -> {
                    dialog.dismiss();
                    openAPPSettings();
                })
                .setNegativeButton(getString(R.string.disagree), (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .show();
    }

    private void openAPPSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    private void requestPermission(int requestCode, String permission) {
        requestPermissions(new String[]{permission}, requestCode);
    }

    public abstract void playerBound(PlayerService player);

    public abstract void updateState();
}
