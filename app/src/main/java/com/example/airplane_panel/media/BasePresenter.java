package com.example.airplane_panel.media;

public abstract class BasePresenter<V extends BaseView> {
    protected final V view;

    public BasePresenter(V view) {
        this.view = view;
    }
}