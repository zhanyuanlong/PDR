package com.zyl.pdr.utils;


import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileUtils {

    // 将字符串写入到文本文件中
    public static void writeTxtToFile(RandomAccessFile raf, String str, File file) {
        try {
            raf.seek(file.length());
            raf.write(str.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // 将字符串写入到文本文件中
    public static RandomAccessFile getRandomAccessFile(File file) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "rwd");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return raf;
    }

    public static void closeRandomAccessFile(RandomAccessFile raf) {
        if (raf != null) {
            try {
                raf.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    // 生成文件
    public static File makeFileNamedTime(String path) {
        File file = null;
        try {
            String time = new SimpleDateFormat("yyyyMMdd_hhmmss")
                    .format(new Date(System.currentTimeMillis()));
            file = new File(path + "data" + time + ".txt");
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file;
    }

    // 生成文件夹
    public static void makeRootDirectory(String filePath) {
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
