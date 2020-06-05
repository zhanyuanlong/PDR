package com.zyl.pdr.utils;


import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.SystemClock;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;

public class RecordUtils {

    public static  final String FILE_PATH = "/sdcard/zylCollectData/";
    private String audioName;
    private File startTimeFile;
    private long startTime;
    private long endTime;


    // 设置音频采样率，48000是目前的标准，但是某些设备仍然支持22050，16000，11025
    static final int frequency = 48000;
    // 设置双声道
    static final int channelConfiguration = AudioFormat.CHANNEL_IN_STEREO; //双声道
    // 音频数据格式：每个样本16位
    static final int audioEncoding = AudioFormat.ENCODING_PCM_16BIT; //16bit



    int recBufSize;//录音最小buffer大小
    private AudioRecord audioRecord;

    private LinkedList<byte[]> write_data = new LinkedList<>();
    private volatile boolean isRecording = false;// 录音线程控制标记
    private volatile boolean isWriting = false;// 录音线程控制标记

    private volatile static RecordUtils recordUtils; // 单例对象
    private RecordUtils() throws InterruptedException {
        // 创建文件夹
        FileUtils.makeRootDirectory(FILE_PATH);

        //录音组件
        recBufSize = AudioRecord.getMinBufferSize(frequency,
                channelConfiguration, audioEncoding);
        //实例化音频
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency,
                channelConfiguration, audioEncoding, recBufSize);
    }

    // 单例双检锁
    public static RecordUtils getInstance() {
        if (recordUtils == null) {
            synchronized (RecordUtils.class){
                if (recordUtils == null) {
                    try {
                        recordUtils = new RecordUtils();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return recordUtils;
    }

    /*
    * 开始录音
    * **/
    public void startRecord() {
        String preName = new SimpleDateFormat("yyyyMMdd_hhmmss")
                .format(new Date(System.currentTimeMillis()));
        startTimeFile = FileUtils.makeFile(FILE_PATH, preName + ".txt");

        // 录音保存的wav名
        audioName = preName + ".wav";
        isRecording = true;
        isWriting = true;
        new Thread(new WriteRunnable()).start();
        new RecordTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }


    /**
     * 停止录音
     */
    public void stopRecord() {
        isRecording = false;  //共享变量
    }



    /**
     * 异步写pcm文件
     *
     */
    class WriteRunnable implements Runnable {
        @Override
        public void run() {
            String tempName = FILE_PATH + "temp.pcm";
            FileOutputStream fos = null;
            File file = new File(tempName);   // pcm文件
            if (file.exists()) {
                file.delete();
            }
            try {
                fos = new FileOutputStream(file);
                // 主要工作区，消费者，消费write_data
                while (isWriting || write_data.size() > 0) {
                    byte[] buffer = null;
                    synchronized (write_data) {
                        if(write_data.size() > 0){
                            buffer = write_data.get(0);
                            write_data.remove(0);
                        }
                    }
                    try {
                        if(buffer != null){
                            fos.write(buffer);
                            fos.flush();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                copyWaveFile(tempName, FILE_PATH + audioName);

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }finally {
                try {
                    if (fos != null) {
                        fos.close();// 关闭写入
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    class RecordTask extends AsyncTask<Object, Object, Object> {

        /*
        * 作用：接收输入参数、执行任务中的耗时操作、返回 线程任务执行的结果.
        * 必须复写，从而自定义线程任务
        * **/
        @Override
        protected Object doInBackground(Object... params) {
            try {
                byte[] buffer = new byte[recBufSize];

                startTime = SystemClock.elapsedRealtimeNanos();     // 获取录音开始的时间戳

                audioRecord.startRecording(); // 开始录音
                // 工作区，生产者，生产到write_data
                while (isRecording) {
                    // 从MIC保存数据到缓冲区
                    int readsize = audioRecord.read(buffer, 0, recBufSize);//读入的数据点
                    if (AudioRecord.ERROR_INVALID_OPERATION != readsize) {
                        synchronized (write_data) {
                            write_data.add(buffer);
                        }
                    }
                }
            } catch (Throwable t) {
            }finally {
                isWriting = false;
                audioRecord.stop();

                endTime = SystemClock.elapsedRealtimeNanos();   //系统开机的时间，和传感器的相似。认为是一个

                RandomAccessFile raf = FileUtils.getRandomAccessFile(startTimeFile);
                FileUtils.writeTxtToFile(raf, startTime +"\r\n" + endTime, startTimeFile);
                FileUtils.closeRandomAccessFile(raf);
            }
            return null;
        }
    }


    /*
    * pcm转wav这里得到可播放的音频文件
    * **/
    private void copyWaveFile(String inFilename, String outFilename) {
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen;
        long longSampleRate = frequency;
        int channels = 2;
        long byteRate = 16 * frequency * channels / 8;
        byte[] data = new byte[recBufSize];
        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;
            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);
            while (in.read(data) != -1) {
                out.write(data);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * 写文件头
     */
    private void WriteWaveFileHeader(FileOutputStream out, long totalAudioLen,
                                     long totalDataLen, long longSampleRate, int channels, long byteRate)
            throws IOException {
        byte[] header = new byte[44];
        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8); // block align
        header[33] = 0;
        header[34] = 16; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        out.write(header, 0, 44);
    }

}

