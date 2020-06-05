package com.zyl.pdr;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.zyl.pdr.utils.CalculationUtils;
import com.zyl.pdr.utils.FileUtils;
import com.zyl.pdr.utils.RecordUtils;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;


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
    private float[] originalGyro = new float[3];    // 储存陀螺仪原始数据
    private long gyroTime;
    private long OriTime;
//    private float mInitialTimestamp = 0;    // 程序运行初始时间

    private float mAzimuth; // 相对世界坐标的航向角,弧度单位
    private float azimuth;  // v[0]
    private float pitch;    // v[1]
    private float roll;     // v[2]

    private long gyroDeltaT = 0;
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
    private Button btLog, btRecord;

    private int mStepCount = 0;
    private float mDistance;

    private SensorManager mSensorManager;   // 传感器相关
    private Pedometer mPedometer = new Pedometer();

    // 录音相关
    private boolean startRecord = false;
    private RecordUtils recordUtils;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i("PDR", "onCreate进入");


        // android高版本需要动态引入权限
        int hasWriteContactsPermission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (hasWriteContactsPermission != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    CODE_FOR_WRITE_PERMISSION);
        }

        int hasRecordPermission = checkSelfPermission(Manifest.permission.RECORD_AUDIO);
        if (hasRecordPermission != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO},
                    CODE_FOR_WRITE_PERMISSION);
        }


        // 创建文件夹
        FileUtils.makeRootDirectory(PATH_PDR);
        // 写文件优化

        mViewStepCount = findViewById(R.id.step_count_view);
        mViewStepLength = findViewById(R.id.step_length_view);
        mViewAzimuth = findViewById(R.id.azimuth_view);
        mViewDistance = findViewById(R.id.distance_view);
        mDrawingView = findViewById(R.id.drawing_view);



        btLog = findViewById(R.id.buttonLog);
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


        btRecord = findViewById(R.id.buttonRecord);
        btRecord.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!startRecord) {
                    btRecord.setBackgroundResource(R.drawable.doing);
                    Toast.makeText(MainActivity.this, "开始录音", Toast.LENGTH_SHORT).show();
                    recordUtils.startRecord();

                } else {
                    btRecord.setBackgroundResource(R.drawable.start);
                    Toast.makeText(MainActivity.this, "录音结束", Toast.LENGTH_SHORT).show();
                    recordUtils.stopRecord();
                }
                startRecord = !startRecord;
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

        recordUtils = RecordUtils.getInstance();

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

//            if (initTimePhone == 0){
//                // 不能保证这个时间就是传感器时间
//                initTimePhone = SystemClock.elapsedRealtimeNanos();
//                initTimeSensor = event.timestamp;
//            }

//            float time = (event.timestamp - mInitialTimestamp) * NS2S;

            // 为了和录音同步，用绝对时间
            long time = event.timestamp; // 纳秒

            originalAcc = event.values;

            // 坐标转换
            double accZ = CalculationUtils.coordinateTransAcc2Z(event.values, pitch, roll);
            mPedometer.addSample(accZ, time);

            if (mPedometer.IsStep()){
                mStepCount++;
                float mStrideLength =  mPedometer.getStrideLength();
                mDistance += mStrideLength;

                mDrawingView.pushValue(mStrideLength, mAzimuth);
                mDrawingView.invalidate();


                mViewStepCount.setText(String.format("步数: %d", mStepCount));
                mViewStepLength.setText( String.format("步长: %.2f m" , mStrideLength));
                mViewDistance.setText(String.format("路程: %.2f m", mDistance));
            }

            // 写log
            if(writeSwitch){

                FileUtils.writeTxtToFile(raf,
                        originalAcc[0]+" "+originalAcc[1]+" "+originalAcc[2]+" "+time
                        +" "+azimuth+" "+pitch+" "+roll +" " + OriTime
                        +" " + originalGyro[0]+" "+originalGyro[1]+" "+originalGyro[2]+" "+gyroTime
                                +" "+ (angFus == null ? 0 : angFus)
                        +"\r\n", dataLog);
            }

        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE){ // 陀螺仪
            // 从 x、y、z 轴的正向位置观看处于原始方位的设备，如果设备逆时针旋转，将会收到正值；否则，为负值
            originalGyro[0] = event.values[0];
            originalGyro[1] = event.values[1];
            originalGyro[2] = event.values[2];

            if(gyroTime != 0){
                gyroDeltaT = event.timestamp - gyroTime;
            }

            gyroTime = event.timestamp;

            // 获得了初始航向了
            if (!initAng) {
                if (angFus == null) {
                    angFus = (double)azimuthInit;
                } else {
                    angFus = angFus - originalGyro[2] * (gyroDeltaT*NS2S);
                    if(angFus < 0){
                        angFus += 2 * Math.PI;
                    } else if (angFus >= 2 * Math.PI) {
                        angFus -= (2 * Math.PI);
                    }
                }
                double tmp = angFus;
                mAzimuth = (float) tmp;   // 航向角作为方位
                mViewAzimuth.setText(String.format("方位: %.0f度", Math.toDegrees(angFus)));
            }

        } else if (event.sensor.getType() == Sensor.TYPE_ORIENTATION){
            azimuth = event.values[0];
            pitch = event.values[1];
            roll = event.values[2];

            OriTime = event.timestamp;

//            mViewAzimuth.setText(String.format("方位: %.0f度", azimuth));

            azimuth = (float) Math.toRadians(azimuth);
            pitch = (float) Math.toRadians(pitch);
            roll = (float) Math.toRadians(roll);

//            mAzimuth = azimuth;   // 航向角作为方位

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
                    initAngCache = null;
                }
            }

        }
    }
}
