package com.a712.wearcollect;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by ben on 2016/11/23.
 */

final class SensorRecorder {
    private static final int SENSOR_FREQUENCY = 100;
    private static SensorRecorder mInstance;
    private File baseDir;
    private ArrayList<SensorDataPoint> accelerometer_record;
    private ArrayList<SensorDataPoint> gyroscope_record;
    private ArrayList<SensorDataPoint> linear_accelerometer_record;
    private SensorManager sensor_manager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private Sensor linear_accelerometer;
    private SensorEventListener listener;
    private Date startTime;

    private SensorRecorder(Context context) {
        baseDir = context.getFilesDir();
        sensor_manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensor_manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer == null) Log.e("Ben", "No accelerometer detected");
        gyroscope = sensor_manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (gyroscope == null) Log.e("Ben", "No gyroscope detected");
        linear_accelerometer = sensor_manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if (linear_accelerometer == null) Log.e("Ben", "No linear accelerometer detected");

        listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                SensorDataPoint sdp = new SensorDataPoint(event.timestamp, event.values[0], event.values[1], event.values[2]);
                if (event.sensor == accelerometer) {
                    accelerometer_record.add(sdp);
                } else if (event.sensor == gyroscope) {
                    gyroscope_record.add(sdp);
                } else if (event.sensor == linear_accelerometer){
                    linear_accelerometer_record.add(sdp);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };
        List<Sensor> a = sensor_manager.getSensorList(Sensor.TYPE_ALL);
//        sensor_manager.getDynamicSensorList(Sensor.TYPE_ALL);
        for(Sensor each: a){
            Log.i("Ben",each.toString());
        }
    }

    synchronized static SensorRecorder getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new SensorRecorder(context);
        }
        return mInstance;
    }

    void start(Date startTime) {
        this.startTime = startTime;
        accelerometer_record = new ArrayList<>(100);
        gyroscope_record = new ArrayList<>(100);
        linear_accelerometer_record = new ArrayList<>(100);
        sensor_manager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);//(int) (1e6 / SENSOR_FREQUENCY));
        sensor_manager.registerListener(listener, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);//(int) (1e6 / SENSOR_FREQUENCY));
        sensor_manager.registerListener(listener, linear_accelerometer, SensorManager.SENSOR_DELAY_FASTEST);//(int) (1e6 / SENSOR_FREQUENCY));
    }

    void stop() {
        sensor_manager.unregisterListener(listener, accelerometer);
        sensor_manager.unregisterListener(listener, gyroscope);

        try {
            if (accelerometer_record.size() > 1) {
                String file_name = String.format("accelerometer-%s.txt", (new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")).format(startTime));
                File logFile = new File(baseDir, file_name);
                FileOutputStream logStream = new FileOutputStream(logFile);
                PrintWriter logPrintWriter = new PrintWriter(logStream);
                for (SensorDataPoint trace : accelerometer_record) {
                    logPrintWriter.println(trace.toString());
                }
                logPrintWriter.flush();
                logPrintWriter.close();
            }
            if (gyroscope_record.size() > 1) {
                String file_name = String.format("gyroscope-%s.txt", (new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")).format(startTime));
                File logFile = new File(baseDir, file_name);
                FileOutputStream logStream = new FileOutputStream(logFile);
                PrintWriter logPrintWriter = new PrintWriter(logStream);
                for (SensorDataPoint trace : gyroscope_record) {
                    logPrintWriter.println(trace.toString());
                }
                logPrintWriter.flush();
                logPrintWriter.close();
            }
            if (linear_accelerometer_record.size() > 1) {
                String file_name = String.format("linear_accelerometer-%s.txt", (new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")).format(startTime));
                File logFile = new File(baseDir, file_name);
                FileOutputStream logStream = new FileOutputStream(logFile);
                PrintWriter logPrintWriter = new PrintWriter(logStream);
                for (SensorDataPoint trace : linear_accelerometer_record) {
                    logPrintWriter.println(trace.toString());
                }
                logPrintWriter.flush();
                logPrintWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

final class SensorDataPoint {
    long t;
    float x, y, z;

    SensorDataPoint(long _t, float _x, float _y, float _z) {
        t = _t;
        x = _x;
        y = _y;
        z = _z;
    }

    SensorDataPoint(SensorDataPoint another) {
        t = another.t;
        x = another.x;
        y = another.y;
        z = another.z;
    }

    public String toString() {
        return String.format("%d, %f, %f, %f", t, x, y, z);
    }
}