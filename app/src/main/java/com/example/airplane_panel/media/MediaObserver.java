package com.example.airplane_panel.media;

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

public class MediaObserver extends ContentObserver {
    private final Handler handler;

    public MediaObserver(Handler handler) {
        super(handler);
        this.handler = handler;
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        super.onChange(selfChange, uri);

        if (selfChange) return;

        if (uri != null) {
            String lastPathSegment = uri.getLastPathSegment();
            if (lastPathSegment != null) {
                Bundle bundle = new Bundle();
                bundle.putString("mediaID", lastPathSegment);

                Message msg = Message.obtain();
                msg.setData(bundle);

                handler.sendMessage(msg);
            }
        }
    }

    // If you're targeting Android API level 29 or above, you should also override the onChange method without the Uri parameter.
    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);
        // This method is left empty or can be implemented similarly to the one with the Uri parameter
        // if your logic requires handling changes without a specific Uri.
    }
}