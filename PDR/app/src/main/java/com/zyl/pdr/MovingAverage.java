package com.zyl.pdr;

public class MovingAverage {
    private float circularBuffer[]; // 平均数组
    private float avg;          // 最近k个均值
    private int circularIndex;  // 指向当前该放的位置
    private boolean flag;   // 是否 不是首个
    private int len;

    public MovingAverage(int len){
        this.len = len;
        circularBuffer = new float[len];  // 移动平均数组大小k
        flag = false;
        circularIndex = 0;
        avg = 0;
    }

    public float getAvg(){
        return avg;
    }

    public float pushValue(float x){
        if (!flag){
            fillBuffer(x);
            flag = true;
        }
        float lastValue = circularBuffer[circularIndex]; // 要覆盖掉的值
        avg = avg + (x - lastValue) / len; //计算当前值覆盖后的avg
        circularBuffer[circularIndex] = x;
        circularIndex = nextIndex(circularIndex);   // 索引+1
        return avg;
    }

    /*
    * 初始状态数值小于k，用第一个值填满k
    * **/
    private void fillBuffer(float val){
        for (int i = 0; i < len; i++){
            circularBuffer[i] = val;
        }
        avg = val;
    }

    /*
    * 循环数组索引+1计算，每次+1，到头归零
    * **/
    private int nextIndex(int curIndex){
        if (curIndex + 1 >= len){
            return 0;
        }
        return curIndex + 1;
    }

}
