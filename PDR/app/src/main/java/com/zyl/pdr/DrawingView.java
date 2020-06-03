package com.zyl.pdr;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public class DrawingView extends View {

    private Paint mPaintLine, mPaintPoint;
    private float mCenterX, mCenterY;
    public Path mPathLine, mPathPoint;
    private  float mX, mY;
    private float mPosX, mPosY;

    private ScaleGestureDetector mDetector;
    private float mScaleFactor = 1f;
    private float mLastTouchX, mLastTouchY;
    private int mActivePointerId = MotionEvent.INVALID_POINTER_ID;

    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPaintLine = new Paint();
        mPaintLine.setColor(Color.RED);
        mPaintLine.setStyle(Paint.Style.STROKE);//线条
        mPaintLine.setStrokeWidth(10); //宽度

        mPaintPoint = new Paint();
        mPaintPoint.setColor(Color.RED);
        mPathLine = new Path();
        mPathPoint = new Path();
        mPathPoint.addCircle(0, 0, 10, Path.Direction.CCW);
        mPosX = 0;
        mPosY = 0;

//        this.setImageResource(R.drawable.map);
        //放大手势
        mDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Let the ScaleGestureDetector inspect all events.
        mDetector.onTouchEvent(ev);

        final int action = ev.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                final int pointerIndex = ev.getActionIndex();
                final float x = ev.getX(pointerIndex);
                final float y = ev.getY(pointerIndex);

                // Remember where we started (for dragging)
                mLastTouchX = x;
                mLastTouchY = y;
                // Save the ID of this pointer (for dragging)
                mActivePointerId = ev.getPointerId(0);
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                // Find the index of the active pointer and fetch its position
                final int pointerIndex =
                        ev.findPointerIndex(mActivePointerId);

                final float x = ev.getX(pointerIndex);
                final float y = ev.getY(pointerIndex);

                // Calculate the distance moved
                final float dx = x - mLastTouchX;
                final float dy = y - mLastTouchY;

                mPosX += dx;
                mPosY += dy;

                invalidate();

                // Remember this touch position for the next move event
                mLastTouchX = x;
                mLastTouchY = y;

                break;
            }

            case MotionEvent.ACTION_UP: {
                mActivePointerId = MotionEvent.INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                mActivePointerId = MotionEvent.INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                final int pointerIndex = ev.getActionIndex();
                final int pointerId = ev.getPointerId(pointerIndex);

                if (pointerId == mActivePointerId) {
                    // This was our active pointer going up. Choose a new
                    // active pointer and adjust accordingly.
                    final int newPointerIndex = pointerIndex == 0 ? 1 :0;
                    mLastTouchX = ev.getX(newPointerIndex);
                    mLastTouchY = ev.getY(newPointerIndex);
                    mActivePointerId = ev.getPointerId(newPointerIndex);
                }
                break;
            }
        }
        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mCenterX = w / 2f;
        mCenterY = h / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();
        canvas.scale(mScaleFactor, mScaleFactor);
        canvas.translate(mCenterX / mScaleFactor, mCenterY / mScaleFactor);
        canvas.translate(mPosX / mScaleFactor, mPosY / mScaleFactor);
        canvas.drawPath(mPathLine, mPaintLine);
        canvas.drawCircle(0, 0, 20/mScaleFactor, mPaintPoint);
        canvas.restore();
    }

    protected void pushValue(float length, float azimuth){
        float rX = (float) (length * Math.cos(Math.PI/2-azimuth));
        float rY = (float) (-length * Math.sin(Math.PI/2-azimuth));
        mX += rX;
        mY += rY;
        final int A = 50;
        mPathLine.lineTo(mX*A, mY*A);
        mPathPoint.addCircle(mX*A, mY*A,20/mScaleFactor, Path.Direction.CCW);
    }

    private class ScaleListener
            extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mScaleFactor *= detector.getScaleFactor();

            // Don't let the object get too small or too large.
            mScaleFactor = Math.max(0.1f, Math.min(mScaleFactor, 5.0f));
            mPaintLine.setStrokeWidth(10/mScaleFactor);

            invalidate();
            return true;
        }
    }
}
