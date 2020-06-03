package com.zyl.pdr.utils;


public class CalculationUtils {
    /*
    * 坐标转换，得到z轴新的acc
    * 思路按照《坐标转换在移动用户行为识别中的应用》北邮学报
    * **/
    public static double coordinateTransAcc2Z(float[] values, float pitch, float roll) {
        double tmp = Math.pow(Math.sin(pitch), 2)+Math.pow(Math.sin(roll),2);
        tmp = Math.pow(tmp, 0.5);
        if(tmp > 1){
            tmp = 1;
        }
        if(tmp < -1){
            tmp = -1;
        }

        double x_angle = Math.asin(tmp);
        double accZ = values[2]*Math.cos(x_angle)
                + values[0]*Math.sin(roll) - values[1]*Math.sin(pitch);

        return accZ;
    }
}
