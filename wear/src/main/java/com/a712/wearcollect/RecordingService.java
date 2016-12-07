package com.a712.wearcollect;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import java.util.Date;

public class RecordingService extends Service {
    public SimpleBinder simpleBinder;
    private CountingThread countingThread;
    private int current_time;
    private OnTimeUpdateListener onTimeUpdateListener;


    public RecordingService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        simpleBinder = new SimpleBinder();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return simpleBinder;
    }

    public void setOnTimeUpdateListener(OnTimeUpdateListener onTimeUpdateListener) {
        this.onTimeUpdateListener = onTimeUpdateListener;
    }

    private void startRecording(Date date) {
        AudioRecorder mRecorder = AudioRecorder.getInstance(this);
        mRecorder.startRecording(date);
    }

    private void stopRecording() {
        AudioRecorder mRecorder = AudioRecorder.getInstance(this);
        mRecorder.stopRecording();
    }

    private void startSensors(Date date) {
        SensorRecorder mRecorder = SensorRecorder.getInstance(this);
        mRecorder.start(date);
    }

    private void stopSensors() {
        SensorRecorder mRecorder = SensorRecorder.getInstance(this);
        mRecorder.stop();
    }

    public interface OnTimeUpdateListener {
        void onTimeUpdate(int time);
    }

    class SimpleBinder extends Binder {
        RecordingService getService() {
            return RecordingService.this;
        }

        void start(Date date, boolean needRecording) {
            if (needRecording) {
                startRecording(date);
            }
            startSensors(date);

            countingThread = new CountingThread();
            new Thread(countingThread).start();

        }

        void stop(boolean needRecording) {
            countingThread.stopThread();
            if (needRecording) {
                stopRecording();
            }
            stopSensors();

        }
    }

    private final class CountingThread implements Runnable {
        boolean vRun = true;

        void stopThread() {
            vRun = false;
        }

        public void run() {
            current_time = 0;
            while (vRun) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                current_time++;
                if (onTimeUpdateListener != null) {
                    onTimeUpdateListener.onTimeUpdate(current_time);
                }
            }

        }
    }
}
