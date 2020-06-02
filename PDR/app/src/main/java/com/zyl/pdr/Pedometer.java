package com.zyl.pdr;

import java.util.ArrayList;

public class Pedometer {
    private Float strideLength;
    private ArrayList<Float> accData;
    private float max, min, sum, avg, a1, a2, t1, t2;
    private float[] accWindow;
    private int index, totalCount;
    private float top, down;    // 窗口内最大最小值
    private final float walkFudge = 1.821f;
    private final int LEN = 50;

    public Pedometer() {
        accData = new ArrayList<Float>(LEN);
        max = -10000f;  //总体最大值
        min = 10000f;
        sum = 0f;       // 单元平均用的和
        avg = 0f;
        a1 = 0f;
        a2 = 0f;
        t1 = 0f;
        t2 = 0f;
        accWindow = new float[LEN];
        index = 0;
        totalCount = 0;
        strideLength = 0f;
    }

    /*
    * 刚刚走的一步的长度估计
    * **/
    public float getStrideLength() {
        avg = sum / accData.size();

        strideLength = walkFudge * (avg - min) / (max - min);

        if (strideLength.isInfinite() || strideLength.isNaN()) {
            strideLength = 0.6f;
        }

        return strideLength;
    }

    /*
    * 是否发生了一步行走
    * **/
    public boolean IsStep() {
        float thresh = (top + down)/2 + 1.5f; // 动态门限计算
        if (a2 > thresh && a1 < thresh && (t2 - t1) > 0.5) {
            t1 = t2;
            return true;
        }

        return false;
    }

    /*
    *
    * acc是移动平均后的z轴加速度，time是acc采集的时间戳
    *
    * **/
    public void getSample(float acc, float time) {
        accData.add(acc);
        t2 = time;
        max = Math.max(max, acc);
        min = Math.min(min, acc);
        sum += acc;
        accWindow[index] = acc;
        index = (index + 1)%LEN;
        totalCount++;
        //求窗口内极值
        if (totalCount >= LEN) {
            top = accWindow[0];
            down = accWindow[0];
            for (int i = 0; i < LEN; i++) {
                top = Math.max(top, accWindow[i]);
                down = Math.min(down, accWindow[i]);
            }
        }
        a1 = a2;
        a2 = acc;
    }

    public void clear() {
        accData.clear();
        max = -10000f;
        min = 10000f;
        sum = 0f;
        avg = 0f;
        strideLength = 0f;
    }
}
