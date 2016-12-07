package com.a712.wearcollect;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Date;


public class MainActivity extends WearableActivity implements DataApi.DataListener {

    private GoogleApiClient mGoogleApiClient;
    private boolean isRunning = false;
    private Button mButton;
    private TextView txt;
    private Date startTime;

    private boolean needAudioRecord = false;
    private CheckBox mAudioRecordCheckBox;

    private ServiceConnection serviceConnection;
    private RecordingService recordingService;
    private RecordingService.SimpleBinder simpleBinder;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setAmbientEnabled();
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mButton = (Button) stub.findViewById(R.id.button);
                txt = (TextView) stub.findViewById(R.id.txt);
                mAudioRecordCheckBox = (CheckBox) stub.findViewById(R.id.checkBox);
                if (mAudioRecordCheckBox != null) {
                    mAudioRecordCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            if (isRunning) {
                                buttonView.setChecked(needAudioRecord);
                            } else {
                                needAudioRecord = isChecked;
                            }
                        }
                    });
                }
            }
        });
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Log.e("Ben", "onConnected: " + connectionHint);
                        // Now you can use the Data Layer API
                        Wearable.DataApi.addListener(mGoogleApiClient, MainActivity.this);
                    }

                    @Override
                    public void onConnectionSuspended(int cause) {
                        Log.e("Ben", "onConnectionSuspended: " + cause);
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.e("Ben", "onConnectionFailed: " + result);
                    }
                })
                // Request access only to the Wearable API
                .addApi(Wearable.API)
                .build();

        handler = new Handler();


        File baseDir = getFilesDir();
        for (File each : baseDir.listFiles()) {
            if (each.isDirectory()) {
                Log.i("Ben", each.getAbsolutePath());
            } else {
                Log.i("Ben", each.getAbsolutePath());
                each.delete();
            }
        }

        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                simpleBinder = (RecordingService.SimpleBinder) service;
                recordingService = simpleBinder.getService();
                Log.i("Ben", "RecordingService connected");
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                simpleBinder = null;
                recordingService = null;
                Log.i("Ben", "RecordingService disconnected");
            }
        };
        Intent intent = new Intent(MainActivity.this, RecordingService.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(serviceConnection);
    }

    @Override
    public void onExitAmbient() {
        super.onExitAmbient();
        mButton.setEnabled(true);
        txt.setTextColor(Color.WHITE);
    }

    @Override
    public void onEnterAmbient(Bundle ambientDetails) {
        super.onEnterAmbient(ambientDetails);
        mButton.setEnabled(false);
        txt.setTextColor(Color.DKGRAY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        Log.e("Ben", "Received a data map!!!!!!..........................................");
        Log.e("Ben", String.format("Received at %s", (new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")).format(new Date())));
        for (DataEvent event : dataEventBuffer) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                Log.e("Ben", "DataEvent = changed");
                DataItem item = event.getDataItem();
                Log.e("Ben", "DataPath = " + item.getUri().getPath());
                if (item.getUri().getPath().compareTo("/send_msg") == 0) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    final String msg = dataMap.getString("msg");
                    Log.e("Ben", "Received A msg: " + msg);
                }
            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                Log.e("Ben", "DataEvent = deleted");
            }
        }
    }

    private void sendFiles(File wav, File acc, File gyro, File linearAcc, String timestamp) {
        if (!mGoogleApiClient.isConnected()) {
            Log.e("Ben", "Not Connected!!!!!!");
            return;
        }
        PutDataMapRequest dataMap = PutDataMapRequest.create("/send_file");
        Asset assetWav = null;
        Asset assetAcc = null;
        Asset assetGyro = null;
        Asset assetLinearAcc = null;
        try {
            if (needAudioRecord) {
                assetWav = Asset.createFromFd(ParcelFileDescriptor.open(wav, ParcelFileDescriptor.MODE_READ_ONLY));
            } else {
                assetWav = null;
            }
            assetAcc = Asset.createFromFd(ParcelFileDescriptor.open(acc, ParcelFileDescriptor.MODE_READ_ONLY));
            assetGyro = Asset.createFromFd(ParcelFileDescriptor.open(gyro, ParcelFileDescriptor.MODE_READ_ONLY));
            assetLinearAcc = Asset.createFromFd(ParcelFileDescriptor.open(linearAcc, ParcelFileDescriptor.MODE_READ_ONLY));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        DataMap dm = dataMap.getDataMap();
        dm.putString("timestamp", timestamp);
        dm.putAsset("wav", assetWav);
        dm.putAsset("acc", assetAcc);
        dm.putAsset("gyro", assetGyro);
        dm.putAsset("linearacc", assetLinearAcc);
        PutDataRequest request = dataMap.asPutDataRequest();
        PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(mGoogleApiClient, request);
        pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(DataApi.DataItemResult dataItemResult) {
                String timestr = (new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")).format(new Date());
                Log.e("Ben", "Sending status: " + dataItemResult.getStatus().isSuccess() + " at time " + timestr);
            }
        });
    }

    public void onButtonClicked(View view) {
        if (isRunning) {
            mButton.setText("Writing file");
            mButton.setEnabled(false);

            stop();

            sendFiles();

            mButton.setText("Suspended");
            mButton.setEnabled(true);
            isRunning = false;
        } else {
            mButton.setEnabled(false);

            startTime = new Date();

            start(startTime);

            mButton.setText("Running");
            mButton.setEnabled(true);
            isRunning = true;
        }
    }

    private void sendFiles() {
        String timeStr = (new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")).format(startTime);
        final String wavFilename = String.format("Audio-%s.wav", timeStr);
        final String accFilename = String.format("accelerometer-%s.txt", timeStr);
        final String gyroFilename = String.format("gyroscope-%s.txt", timeStr);
        final String linearAccFilename = String.format("linear_accelerometer-%s.txt", timeStr);
        File wav = new File(getFilesDir(), wavFilename);
        File acc = new File(getFilesDir(), accFilename);
        File gyro = new File(getFilesDir(), gyroFilename);
        File linearAcc = new File(getFilesDir(), linearAccFilename);
        final String text = String.format("wav:%d,acc:%d\ngyro:%d,linearacc:%d", wav.length(), acc.length(), gyro.length(), linearAcc.length());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                txt.setText(text);
            }
        }, 0);

        sendFiles(wav, acc, gyro, linearAcc, timeStr);
    }

    private void start(Date date) {
        simpleBinder.start(date, needAudioRecord);
        recordingService.setOnTimeUpdateListener(new RecordingService.OnTimeUpdateListener() {
            @Override
            public void onTimeUpdate(int current_time) {
//                String str = "正在录制：" + current_time + " s;";
//                if (current_time > 10) {
//                    str += Integer.toString((current_time - 11) % 6 + 1) + "s";
//                }
                final String str = "正在录制：" + current_time + " s;" + (current_time > 10 ? Integer.toString((current_time - 11) % 6 + 1) + "s" : "");
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        txt.setText(str);
                    }
                }, 0);

            }
        });
    }

    private void stop() {
        simpleBinder.stop(needAudioRecord);
        recordingService.setOnTimeUpdateListener(null);
    }

}
