package com.example.airplane_panel.customview;

import android.animation.TypeEvaluator;
import android.graphics.Point;

public class BezierEvaluator implements TypeEvaluator<Point> {
    private Point controlPoint1;
    private Point controlPoint2;

    public BezierEvaluator(Point controlPoint1, Point controlPoint2) {
        this.controlPoint1 = controlPoint1;
        this.controlPoint2 = controlPoint2;
    }

    @Override
    public Point evaluate(float fraction, Point startValue, Point endValue) {
        float t = fraction;
        float oneMinusT = 1.0f - t;
        float x = startValue.x * oneMinusT * oneMinusT * oneMinusT +
                3f * controlPoint1.x * t * oneMinusT * oneMinusT +
                3f * controlPoint2.x * t * t * oneMinusT +
                endValue.x * t * t * t;
        float y = startValue.y * oneMinusT * oneMinusT * oneMinusT +
                3f * controlPoint1.y * t * oneMinusT * oneMinusT +
                3f * controlPoint2.y * t * t * oneMinusT +
                endValue.y * t * t * t;
        return new Point((int) x, (int) y);
    }
}
