package com.zyl.pdr;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;


import com.zyl.pdr.utils.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;


public class MainActivity extends Activity implements SensorEventListener{

    private static final String TAG = "PDR";
    public static final int CODE_FOR_WRITE_PERMISSION = 1;  // 自定义权限获取后返回码

    // 文件相关
    public static final String PATH_PDR = "/sdcard/pdr/";   // 数据存储位置
    private File dataLog; // 指向当前的输出文件
    private RandomAccessFile raf; // 指向当前的输出文件的访问句柄
    private boolean writeSwitch = false;



    // 数据用
    private float[] originalAcc = new float[3]; // 储存原始传感器acc
    private MovingAverage[] mAverageLinearAcceleration = new MovingAverage[3]; // 平均单元
    private float[] originalGyro = new float[3];    // 储存陀螺仪原始数据
    private double gyroTime;
    private double OriTime;
    private float mInitialTimestamp = 0;    // 程序运行初始时间
    private float mAzimuth; // 相对世界坐标的航向角
    private float azimuth;  // v[0]
    private float pitch;    // v[1]
    private float roll;     // v[2]

    private double gyroDeltaT = 0;
    private Double angFus = null;

    // 初始航向
    public static final int initAngCacheNum = 200;  // LinkedList保存200个最新航向，后100均值作为初始航向
    private ArrayList<Float> initAngCache = new ArrayList<>(initAngCacheNum);
    private boolean initAng = true;
    private float azimuthInit;

    private static final float NS2S = 1.0f / 1000000000.0f; // 时间戳转秒

    // 控件
    private TextView mViewStepCount, mViewStepLength, mViewAzimuth, mViewDistance;
    private DrawingView mDrawingView;
    private Button btLog;

    private int mStepCount = 0;
    private float mDistance;

    private SensorManager mSensorManager;   // 传感器相关
    private Pedometer mPedometer = new Pedometer();




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // android高版本需要动态引入权限
        int hasWriteContactsPermission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (hasWriteContactsPermission != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    CODE_FOR_WRITE_PERMISSION);
        }

        // 创建文件夹
        FileUtils.makeRootDirectory(PATH_PDR);
        // 写文件优化

        mViewStepCount = (TextView) findViewById(R.id.step_count_view);
        mViewStepLength = (TextView) findViewById(R.id.step_length_view);
        mViewAzimuth = (TextView) findViewById(R.id.azimuth_view);
        mViewDistance = (TextView) findViewById(R.id.distance_view);
        mDrawingView = (DrawingView) findViewById(R.id.drawing_view);


        btLog = (Button) findViewById(R.id.button1);
        btLog.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                writeSwitch = !writeSwitch;
                Toast.makeText(MainActivity.this, ""+writeSwitch, Toast.LENGTH_SHORT).show();
                // 每次打开记录建立新文件
                if (writeSwitch) {
                    FileUtils.closeRandomAccessFile(raf); // 关闭旧的访问句柄
                    dataLog = FileUtils.makeFileNamedTime(PATH_PDR);
                    raf = FileUtils.getRandomAccessFile(dataLog);
                }
            }
        });

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        Sensor mLinearAcceleration, gyroscope, mRotation;
        // 线性加速度传感器，单位是m/s2，该传感器是获取加速度传感器去除重力的影响得到的数据
        mLinearAcceleration = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        // 旋转矢量传感器，旋转矢量代表设备的方向
//        mRotationVector = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        // 方向传感器，磁力计获得
        mRotation = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        // 陀螺仪传感器，单位是rad/s，测量设备x、y、z三轴的角加速度
        gyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);


        // 监听传感器，级别为最高
        mSensorManager.registerListener(this, mLinearAcceleration, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mRotation, SensorManager.SENSOR_DELAY_GAME);


        // acc平均单元初始化，平均5个
        for (int i = 0; i < 3; i++){
            mAverageLinearAcceleration[i] = new MovingAverage(5);
        }


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(this);
        FileUtils.closeRandomAccessFile(raf);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        // 加速度传感器
        if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            if (mInitialTimestamp == 0){
                mInitialTimestamp =event.timestamp;
            }

            // 坐标转换，得到z轴新的acc 《坐标转换在移动用户行为识别中的应用》
            double tmp = Math.pow(Math.sin(pitch), 2)+Math.pow(Math.sin(roll),2);
            tmp = Math.pow(tmp, 0.5);
            if(tmp > 1){
                tmp = 1;
            }
            if(tmp < -1){
                tmp = -1;
            }

            double x_angle = Math.asin(tmp);
            double accZ = event.values[2]*Math.cos(x_angle)
                    + event.values[0]*Math.sin(roll) - event.values[1]*Math.sin(pitch);


            for (int i = 0; i < 3; i++){
                // 存进平均单元，检测步态用
                mAverageLinearAcceleration[i].pushValue((float) accZ);
                // 保存原始数据，打log用
                originalAcc[i] = event.values[i];
            }


            float acczAvg = mAverageLinearAcceleration[2].getAvg();
            float time = (event.timestamp - mInitialTimestamp) * NS2S;


            mPedometer.getSample(acczAvg, time);

            if (mPedometer.IsStep()){
                mStepCount++;
                float mStrideLength =  mPedometer.getStrideLength();
                mPedometer.clear();
                mDistance += mStrideLength;
                mDrawingView.pushValue(mStrideLength, mAzimuth);
                mDrawingView.invalidate();
                mViewStepCount.setText(String.format("步数: %d", mStepCount));
                mViewStepLength.setText( String.format("步长: %.2f m" , mStrideLength));
                mViewDistance.setText(String.format("距离: %.1f m", mDistance));
            }

            // 写log
            if(writeSwitch){

                FileUtils.writeTxtToFile(raf,
                        originalAcc[0]+" "+originalAcc[1]+" "+originalAcc[2]+" "+time+" "
                        +" "+azimuth+" "+pitch+" "+roll +" " + OriTime
                        + originalGyro[0]+" "+originalGyro[1]+" "+originalGyro[2]+" "+gyroTime
                                +" "+ angFus
                        +"\r\n", dataLog);
            }

        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE){ // 陀螺仪
            // 从 x、y、z 轴的正向位置观看处于原始方位的设备，如果设备逆时针旋转，将会收到正值；否则，为负值
            originalGyro[0] = event.values[0];
            originalGyro[1] = event.values[1];
            originalGyro[2] = event.values[2];

            if(gyroTime != 0){
                gyroDeltaT = event.timestamp* NS2S - gyroTime;
            }

            gyroTime = event.timestamp * NS2S;

            // 获得了初始航向了
            if (!initAng) {
                if (angFus == null) {
                    angFus = (double)azimuthInit;
                } else {
                    angFus = angFus - originalGyro[2] * gyroDeltaT;
                    if(angFus < 0){
                        angFus += 2 * Math.PI;
                    } else if (angFus >= 2 * Math.PI) {
                        angFus -= (2 * Math.PI);
                    }
                }

                mViewAzimuth.setText(String.format("方位: %.0f度", Math.toDegrees(angFus)));
            }

        } else if (event.sensor.getType() == Sensor.TYPE_ORIENTATION){
            azimuth = event.values[0];
            pitch = event.values[1];
            roll = event.values[2];

            OriTime = event.timestamp * NS2S;

//            mViewAzimuth.setText(String.format("方位: %.0f度", azimuth));

            azimuth = (float) Math.toRadians(azimuth);
            pitch = (float) Math.toRadians(pitch);
            roll = (float) Math.toRadians(roll);

            mAzimuth = azimuth;   // 航向角作为方位

            // 需要计算初始航向
            if (initAng) {
                initAngCache.add(azimuth);
                if (initAngCache.size() >= initAngCacheNum) {
                    initAng = false;
                    int f = 0;
                    // 刚开始有误差，只用后面的
                    for (int i = initAngCacheNum / 2; i < initAngCacheNum; i++) {
                        azimuthInit += initAngCache.get(i);
                        f++;
                    }
                    azimuthInit /= f;
                }
            }

        }
    }
}
