package com.example.airplane_panel.customview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.appcompat.widget.AppCompatImageView;

import java.util.Random;
import android.animation.TypeEvaluator;
import android.graphics.Point;

public class FloatingAnimationView extends AppCompatImageView {
    private Point startPosition;
    private Point endPosition;

    private final Random random = new Random();

    public FloatingAnimationView(Context context) {
        super(context);
        init(null, 0);
    }

    public FloatingAnimationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public FloatingAnimationView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    private void init(AttributeSet attrs, int defStyle) {
        // Initialization code if needed
    }

    public void setStartPosition(Point startPosition) {
        this.startPosition = startPosition;
    }

    public void setEndPosition(Point endPosition) {
        this.endPosition = endPosition;
    }

    public void startAnimation() {
        int width = getScreenWidth();
        int height = getScreenHeight();

        Point endPointRandom = new Point(random.nextInt(width), endPosition.y);

        BezierEvaluator bezierTypeEvaluator = new BezierEvaluator(
                new Point(random.nextInt(width), random.nextInt(height)),
                new Point(random.nextInt(width / 2), random.nextInt(height / 2)));

        ValueAnimator animator = ValueAnimator.ofObject(bezierTypeEvaluator, startPosition, endPointRandom);
        animator.addUpdateListener(valueAnimator -> {
            Point point = (Point) valueAnimator.getAnimatedValue();
            float fraction = valueAnimator.getAnimatedFraction();

            setX(point.x);
            setY(point.y);
            setAlpha(1 - fraction);

            invalidate();
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                ViewGroup viewGroup = (ViewGroup) getParent();
                viewGroup.removeView(FloatingAnimationView.this);
            }
        });

        animator.setDuration(2000);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.start();
    }

    private int getScreenWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    private int getScreenHeight() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
    }
}
