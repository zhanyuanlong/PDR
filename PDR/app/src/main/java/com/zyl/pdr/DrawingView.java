package com.zyl.pdr;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;

@SuppressLint("AppCompatCustomView")
public class DrawingView extends ImageView {

    private Paint mPaintLine, mPaintPoint;
    private float mCenterX, mCenterY;
    public Path mPathLine, mPathPoint;
    private  float mX, mY;
    private float mPosX, mPosY;

    private ScaleGestureDetector mDetector;
    private float mScaleFactor = 1f;
    private float mLastTouchX, mLastTouchY;
    private int mActivePointerId = MotionEvent.INVALID_POINTER_ID;

    // 画图放大系数，不同地图下不同。实验室内是110？,外面用40
    private final int DRAW_COORDINATES_AMPL = 40;

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

        mPosX = 0;
        mPosY = 0;

//        this.setImageResource(R.drawable.map);
        //放大手势
        mDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    /*
    * 手指移动，将图移动
    * **/
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Let the ScaleGestureDetector inspect all events.
        mDetector.onTouchEvent(ev);

        final int action = ev.getActionMasked();

        switch (action) {
            //有按下
            case MotionEvent.ACTION_DOWN: {
                final int pointerIndex = ev.getActionIndex();
                // 获得指针索引的指针
                final float x = ev.getX(pointerIndex);
                final float y = ev.getY(pointerIndex);

                // Remember where we started (for dragging)
                mLastTouchX = x;
                mLastTouchY = y;
                // Save the ID of this pointer (for dragging)
                mActivePointerId = ev.getPointerId(0);
                break;
            }
            //正在移动
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
            // 抬手
            case MotionEvent.ACTION_UP: {
                mActivePointerId = MotionEvent.INVALID_POINTER_ID;
                break;
            }
            // 动作终止
            case MotionEvent.ACTION_CANCEL: {
                mActivePointerId = MotionEvent.INVALID_POINTER_ID;
                break;
            }
            // 额外的手指的离开操作
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

    /*
    * 进入时调用他，选定中心
    * **/
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mCenterX = w / 2f;
        mCenterY = h / 2f;
    }

    /**
     * 在此重绘
     * 最初绘制的视图
     在视图中调用 invalidate() 时被调用
     * */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // save当前画布的设置，画完了恢复回去
        canvas.save();
        // x,y方向上均缩放为mScaleFactor倍，使用默认基准点（原点 0，0）
        canvas.scale(mScaleFactor, mScaleFactor);
        // 画笔移动到中心
        canvas.translate(mCenterX / mScaleFactor, mCenterY / mScaleFactor);
        // 中心再移动mPosX,(是屏幕移动产生的新的mPos，通过屏幕中心+mPos代表画笔起点)
        canvas.translate(mPosX / mScaleFactor, mPosY / mScaleFactor);
        // 从之前画笔位置，画mPathLine。画line是画笔坐标下
        canvas.drawPath(mPathLine, mPaintLine);
        // 画当前位置圆
        canvas.drawCircle(mX * DRAW_COORDINATES_AMPL, mY * DRAW_COORDINATES_AMPL,
                20/mScaleFactor, mPaintPoint);
        mPaintPoint.setColor(Color.GREEN);

        // 画原点，相对画笔坐标的圆心(0,0)，半径20/mScaleFactor
        canvas.drawCircle(0, 0, 20/mScaleFactor, mPaintPoint);
        mPaintPoint.setColor(Color.RED);


        // 恢复save()状态，就是取消一系列平移
        canvas.restore();
    }

    protected void pushValue(float length, float azimuth){
        // 实验室地图下，房子不是正南正北，加偏角
//        float delt = (float) (-15*Math.PI/180);//-15
        float delt = 0;


        float rX = (float) (length * Math.sin(azimuth + delt));
        float rY = (float) (-length * Math.cos(azimuth + delt));


        // 实验室地图下，房子不是上北下南，坐标转换
//        float t = rX;
//        rX = rY;
//        rY = -t;


        mX += rX;
        mY += rY;

        // 设置line，没绘制。从当前轮廓点结束点的一条线段到mx，my
        mPathLine.lineTo(mX * DRAW_COORDINATES_AMPL, mY * DRAW_COORDINATES_AMPL);
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
