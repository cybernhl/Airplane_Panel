package com.example.airplane_panel.media;

import android.content.Context;
import android.view.View;

public interface BaseView {
    boolean isActive();

    Context context();

    void showToast(String msg);

    void showSnackBar(View v, String msg);
}