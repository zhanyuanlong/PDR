package com.zyl.pdr;

import java.util.LinkedList;

public class Pedometer {
    private LinkedList<Float> accData;   // zacc原始数据窗口
    private LinkedList<Float> avgAccData;   // zacc平均后的数据窗口
    private LinkedList<Float> accTime;  // zacc数据对应的时间

    // 参数相关
    private final float LOW_Threshold = 1.2f;
    private final float HIGH_Threshold = 6.5f;
    private final float TIME_INTER_Threshold = 0.4f;
    // 传感器大概50Hz，就用1s的数据
    private final int WINDOW_SIZE = 41;
    private final float FUDGE_FACTOR = 1.65f;

    // 平滑相关
    private MovingAverage movingAverage; // 平均单元
    private static final int SMOOTH_NUM = 5;

    private double lastStepTime = 0;
    private Float strideLength;
    private boolean isStep = false;

    public Pedometer() {
        accData = new LinkedList<>();
        avgAccData = new LinkedList<>();
        accTime = new LinkedList<>();
        // acc平均单元初始化
        movingAverage = new MovingAverage(SMOOTH_NUM);
    }

    /*
    * 获取上一次的步长，建议行走状态后立刻获取
    * **/
    public float getStrideLength() {    //获取步长
        return strideLength;
    }

    /*
    * 刚刚走的一步的长度估计,
    * 认为这一步的数据在accData中
    * **/
    public void calStrideLength() {
        float avg = 0;
        int N = accData.size();
        float max = -100, min = 100;
        for (Float data : accData) {
            avg += data;
            if (max < data) {
                max = data;
            }
            if (min > data) {
                min = data;
            }
        }
        avg /= N;
        Float strideLength = FUDGE_FACTOR * (avg - min) / (max - min);
        if (strideLength.isInfinite() || strideLength.isNaN()) {
            strideLength = 0.6f;	//默认60cm
        }

        this.strideLength = strideLength;
    }


    /*
    * 是否发生了一步行走
    * 读取到了true先清除
    * **/
    public boolean IsStep() {
        if (isStep) {
            isStep = false;
            return true;
        } else {
            return isStep;
        }
    }

    /*
    * 添加新的acc数据,并计算是否行走了
    * acc是移动平均后的z轴加速度，time是acc采集的时间戳
    *
    * **/
    public void addSample(double acc, float time) {
        movingAverage.pushValue((float) acc);
        float accAvg = movingAverage.getAvg();

        accData.addLast((float) acc);
        avgAccData.addLast(accAvg);
        accTime.addLast(time);

        if (avgAccData.size() > WINDOW_SIZE) {
            accData.removeFirst();
            avgAccData.removeFirst();
            accTime.removeFirst();

            //检测avgAccData[WINDOW_SIZE/2]是否为窗口最大值
            boolean flag = false;
            float anchor = avgAccData.get(WINDOW_SIZE/2);
            for (float data : avgAccData) {
                if (anchor < data) {
                    flag = true;
                    break;
                }
            }

            // 是窗口最大值
            if (!flag) {
                double tmpTime = accTime.get(WINDOW_SIZE/2);
                if (anchor > LOW_Threshold && anchor < HIGH_Threshold
                        && tmpTime > lastStepTime + TIME_INTER_Threshold) {
                    lastStepTime = tmpTime;
                    calStrideLength();
                    isStep = true;
                }
            }
        }
    }
}
