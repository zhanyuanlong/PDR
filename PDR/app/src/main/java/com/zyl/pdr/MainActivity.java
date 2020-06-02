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


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;


public class MainActivity extends Activity implements SensorEventListener{

    private static final String TAG = "PDR";
    public static final int CODE_FOR_WRITE_PERMISSION = 1;  // 自定义权限获取后返回码

    // 文件相关
    public static final String PATH_PDR = "/sdcard/pdr/";   // 数据存储位置
    private File dataLog; // 指向当前的输出文件
    private boolean writeSwitch = false;

    // 数据用
    private float[] originalAcc = new float[3]; // 储存原始传感器acc
    private MovingAverage[] mAverageLinearAcceleration = new MovingAverage[3]; // 平均单元
    private float[] originalGyro = new float[3];    // 储存陀螺仪原始数据
    private double gyroTime;
    private float mInitialTimestamp = 0;    // 程序运行初始时间
    float[] mOrientationAngles = new float[3];
    private float mAzimuth; // 相对世界坐标的航向角
    private float[] mOri = new float[3];


    private static final float NS2S = 1.0f / 1000000000.0f; // 时间戳转秒

    // 控件
    private TextView mViewStepCount, mViewStepLength, mViewAzimuth, mViewDistance;
    private DrawingView mDrawingView;
    private Button bt1;

    private int mStepCount = 0;
    private float mDistance;

    private SensorManager mSensorManager;   // 传感器相关
    private Pedometer mPedometer = new Pedometer();


    private float[] values, r, gravity, geomagnetic;
    private TextView tvAcc, tvAng0, tvAng1, tvAng2;

    private double megTime;
    private double OriTime;

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
        makeRootDirectory(PATH_PDR);
        // 写文件优化

        mViewStepCount = (TextView) findViewById(R.id.step_count_view);
        mViewStepLength = (TextView) findViewById(R.id.step_length_view);
        mViewAzimuth = (TextView) findViewById(R.id.azimuth_view);
        mViewDistance = (TextView) findViewById(R.id.distance_view);
        mDrawingView = (DrawingView) findViewById(R.id.drawing_view);



        tvAcc = (TextView) findViewById(R.id.accz);
        tvAng0 = (TextView) findViewById(R.id.angle_0);
        tvAng1 = (TextView) findViewById(R.id.angle_1);
        tvAng2 = (TextView) findViewById(R.id.angle_2);

        bt1 = (Button) findViewById(R.id.button1);
        bt1.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                writeSwitch = !writeSwitch;
                Toast.makeText(MainActivity.this, ""+writeSwitch, Toast.LENGTH_SHORT).show();
                // 每次打开记录建立新文件
                if (writeSwitch) {
                    dataLog = makeFile();
                }
            }
        });

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        Sensor mLinearAcceleration, mRotationVector, gyroscope;
        // 线性加速度传感器，单位是m/s2，该传感器是获取加速度传感器去除重力的影响得到的数据
        mLinearAcceleration = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        // 旋转矢量传感器，旋转矢量代表设备的方向
        mRotationVector = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        // 方向传感器，过期。
        mRotationVector = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        // 陀螺仪传感器，单位是rad/s，测量设备x、y、z三轴的角加速度
        gyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        //测试新的
        Sensor magneticSensor,accelerometerSensor;
        magneticSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        accelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // 监听传感器，级别为最高
        mSensorManager.registerListener(this, mLinearAcceleration, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mRotationVector, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);

        mSensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME);

        // acc平均单元初始化，平均5个
        for (int i = 0; i < 3; i++){
            mAverageLinearAcceleration[i] = new MovingAverage(5);
        }

        //初始化数组
        values = new float[3];//用来保存最终的结果
        gravity = new float[3];//用来保存加速度传感器的值
        r = new float[9];//
        geomagnetic = new float[3];//用来保存地磁传感器的值

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(this);
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

            for (int i = 0; i < 3; i++){
                // 存进平均单元，检测步态用
                mAverageLinearAcceleration[i].pushValue(event.values[i]);
                // 保存原始数据，打log用
                originalAcc[i] = event.values[i];
            }

            // 手机z，不是世界
            float acc = mAverageLinearAcceleration[2].getAvg();
            float time = (event.timestamp - mInitialTimestamp) * NS2S;


            String sign = acc >= 0 ? "+" :"-";
            tvAcc.setText(String.format("accz:\n "+sign + "%.1f" , Math.abs(acc)));

            mPedometer.getSample(acc, time);

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

                writeTxtToFile(originalAcc[0]+" "+originalAcc[1]+" "+originalAcc[2]+" "+time+" "

//                        +mOrientationAngles[0]+" "+mOrientationAngles[1]+" "+mOrientationAngles[2]+" "+
                        +values[0]+" "+values[1]+" "+values[2]+" "+

                        originalGyro[0]+" "+originalGyro[1]+" "+originalGyro[2]+" "+gyroTime

                        +" "+mOri[0]+" "+mOri[1]+" "+mOri[2]
                        +" "+OriTime + " " + megTime

                        +"\r\n", dataLog);
            }

        }

//        else if(event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR){ // 旋转矢量
//            float[] mRotationMatrix = new float[9];
//            // 计算出旋转矩阵
//            SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);
//            // 求得设备的方向（航向角、俯仰角、横滚角）
//            SensorManager.getOrientation(mRotationMatrix, mOrientationAngles);
//
//            mAzimuth = mOrientationAngles[0];   // 航向角作为方位
//            mViewAzimuth.setText(String.format("方位: %.0f度" ,mOrientationAngles[0]*180/Math.PI));
//
//            //
////            tvAng0.setText(String.format("Ang0: \n%.1f" , mOrientationAngles[0]*180/Math.PI));
////
////            tvAng1.setText(String.format("Ang1: \n%.1f" , mOrientationAngles[1]*180/Math.PI));
////            tvAng2.setText(String.format("Ang2: \n%.1f" , mOrientationAngles[2]*180/Math.PI));
//
//
//        }


        else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE){ // 陀螺仪
            // 从 x、y、z 轴的正向位置观看处于原始方位的设备，如果设备逆时针旋转，将会收到正值；否则，为负值
            originalGyro[0] = event.values[0];
            originalGyro[1] = event.values[1];
            originalGyro[2] = event.values[2];
            gyroTime = event.timestamp * NS2S;

        }

        if (event.sensor.getType() == Sensor.TYPE_ORIENTATION){
            mOri = event.values;
//            tvAng0.setText(String.format("Ang0: \n%.1f" , mOri[0]));
//            tvAng1.setText(String.format("Ang1: \n%.1f" , mOri[1]));
//            tvAng2.setText(String.format("Ang2: \n%.1f" , mOri[2]));

            OriTime = event.timestamp * NS2S;

            mOri[0] = (float) Math.toRadians(mOri[0]);
            mOri[1] = (float) Math.toRadians(mOri[1]);
            mOri[2] = (float) Math.toRadians(mOri[2]);


        }


        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = event.values.clone();
        }


        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravity = event.values.clone();
            megTime = event.timestamp * NS2S;
            getValue();
        }
    }

    public void getValue() {
        // r从这里返回
        SensorManager.getRotationMatrix(r, null, gravity, geomagnetic);
        //values从这里返回
        SensorManager.getOrientation(r, values);

        tvAng0.setText(String.format("Ang0: \n%.1f" , values[0]*180/Math.PI));
        tvAng1.setText(String.format("Ang1: \n%.1f" , values[1]*180/Math.PI));
        tvAng2.setText(String.format("Ang2: \n%.1f" , values[2]*180/Math.PI));



//        //提取数据
//        double azimuth = Math.toDegrees(values[0]);
//        if (azimuth < 0) {
//            azimuth=azimuth+360;
//        }
//        double pitch = Math.toDegrees(values[1]);
//        double roll = Math.toDegrees(values[2]);
//
//        tv.invalidate();
//        tv.setText("Azimuth：" + (int)azimuth + "\nPitch：" + (int)pitch + "\nRoll：" + (int)roll);
    }


    // 将字符串写入到文本文件中
    public void writeTxtToFile(String str, File file) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "rwd");
            raf.seek(file.length());
            raf.write(str.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    // 生成文件
    public File makeFile() {
        File file = null;
        try {
            String time = new SimpleDateFormat("yyyyMMdd_hhmmss")
                    .format(new Date(System.currentTimeMillis()));
            file = new File(PATH_PDR + "data" + time + ".txt");
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file;
    }

    // 生成文件夹
    public void makeRootDirectory(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                file.mkdir();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
