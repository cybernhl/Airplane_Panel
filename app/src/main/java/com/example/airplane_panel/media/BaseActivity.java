package com.example.airplane_panel.media;

import static androidx.core.content.PermissionChecker.PERMISSION_GRANTED;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import com.google.android.material.snackbar.Snackbar;

public abstract class BaseActivity<P extends BasePresenter<?>> extends AppCompatActivity implements BaseView {

    protected P presenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        presenter = createPresenter();
    }

    @Override
    public boolean isActive() {
        return getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    }

    @Override
    public Context context() {
        return this;
    }

    @Override
    public void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showSnackBar(View v, String msg) {
        Snackbar.make(v, msg, Snackbar.LENGTH_SHORT).show();
    }

    protected boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PERMISSION_GRANTED;
    }

    protected void requestPermission(int requestCode, String... permissions) {
        ActivityCompat.requestPermissions(this, permissions, requestCode);
    }

    protected abstract P createPresenter();
}
