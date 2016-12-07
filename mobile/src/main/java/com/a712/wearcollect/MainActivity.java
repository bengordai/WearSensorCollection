package com.a712.wearcollect;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
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
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements DataApi.DataListener {

    TextView txt;
    Button button;
    ListView listView;
    MyAdapter adapter;
    private GoogleApiClient mGoogleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txt = (TextView) findViewById(R.id.txt);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Wearable.DataApi.addListener(mGoogleApiClient, MainActivity.this);
                        Log.e("Ben", "onConnected: " + connectionHint);
                        // Now you can use the Data Layer API
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


        button = (Button) findViewById(R.id.button);
        listView = (ListView) findViewById(R.id.listview);
        adapter = new MyAdapter(this);
        listView.setAdapter(adapter);
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
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.e("Ben", "Received a data map!!!!!!..........................................");
        Log.e("Ben", String.format("Received at %s", (new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")).format(new Date())));
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                Log.e("Ben", "DataEvent = changed");
                DataItem item = event.getDataItem();
                Log.e("Ben", "DataPath = " + item.getUri().getPath());
                if (item.getUri().getPath().compareTo("/send_file") == 0) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    final String timestamp = dataMap.getString("timestamp");
                    Asset assetWav = dataMap.getAsset("wav");
                    Asset assetAcc = dataMap.getAsset("acc");
                    Asset assetGyro = dataMap.getAsset("gyro");
                    Asset assetLinearAcc = dataMap.getAsset("linearacc");
                    Log.e("Ben", "Received 3 Files at " + timestamp);
                    File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/SensorAudioRecords");
                    dir.mkdir();
                    final String wavFilename = String.format("Audio-%s.wav", timestamp);
                    final String accFilename = String.format("Accelerometer-%s.txt", timestamp);
                    final String gyroFilename = String.format("Gyroscope-%s.txt", timestamp);
                    final String linearAccFilename = String.format("LinearAcc-%s.txt", timestamp);
                    final long wavLength = saveFileFromAssetToDisk(assetWav, dir, wavFilename);
                    final long accLength = saveFileFromAssetToDisk(assetAcc, dir, accFilename);
                    final long gyroLength = saveFileFromAssetToDisk(assetGyro, dir, gyroFilename);
                    final long linearAccLength = saveFileFromAssetToDisk(assetLinearAcc, dir, linearAccFilename);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adapter.addItem(timestamp, Long.toString(wavLength), Long.toString(accLength), Long.toString(gyroLength),Long.toString(linearAccLength));
                        }
                    });

                }
            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                Log.e("Ben", "DataEvent = deleted");
            }
        }
    }

    public void onButtonClicked(View view) {
        if (mGoogleApiClient.isConnected()) {
            txt.setText("Connected!");
            PutDataMapRequest dataMap = PutDataMapRequest.create("/send_file");
            DataMap dm = dataMap.getDataMap();
            dm.putString("msg", "test!!!!!!!!!!!!!!");
            dm.putString("timestamp",new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date()));
//            dataMap.setUrgent();
            PutDataRequest request = dataMap.asPutDataRequest();
            PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(mGoogleApiClient, request);
            pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(DataApi.DataItemResult dataItemResult) {
                    String timestr = (new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")).format(new Date());
                    Log.e("Ben", "Sending status: " + dataItemResult.getStatus().isSuccess() + " at time " + timestr);
                }
            });

        } else {
            txt.setText("Not connected!");
        }
    }


    private long saveFileFromAssetToDisk(Asset asset, File dir, String filename) {
        if(asset == null){
            return -1;
        }
        ParcelFileDescriptor pfd = Wearable.DataApi.getFdForAsset(mGoogleApiClient, asset).await().getFd();
        FileDescriptor fd = pfd.getFileDescriptor();
        FileInputStream fis = new FileInputStream(fd);
        File output = new File(dir, filename);
        try {
            FileOutputStream fos = new FileOutputStream(output);
            byte[] buffer = new byte[1024];
            int readinLen = 0;
            while ((readinLen = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, readinLen);
            }
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return output.length();
    }
    private class MyAdapter extends BaseAdapter {

        private Context context;
        private LayoutInflater inflater;
        private ArrayList<String> mTimestamp;
        private ArrayList<String> mWav;
        private ArrayList<String> mAcc;
        private ArrayList<String> mGyro;
        private ArrayList<String> mLinearAcc;
        private String baseDir;
        MyAdapter(Context context) {
            super();
            this.context = context;
            inflater = LayoutInflater.from(context);
            mTimestamp = new ArrayList<>();
            mWav = new ArrayList<>();
            mAcc = new ArrayList<>();
            mGyro = new ArrayList<>();
            mLinearAcc = new ArrayList<>();
            baseDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/SensorAudioRecords/";
        }
        @Override
        public int getCount() {
            // TODO Auto-generated method stub
            return mTimestamp.size();
        }
        @Override
        public Object getItem(int arg0) {
            // TODO Auto-generated method stub
            return arg0;
        }
        @Override
        public long getItemId(int arg0) {
            // TODO Auto-generated method stub
            return arg0;
        }
        @Override
        public View getView(final int position, View view, ViewGroup arg2) {
            // TODO Auto-generated method stub
            if(view == null){
                view = inflater.inflate(R.layout.list_item, null);
            }
            int count = getCount();
            final TextView timestamp = (TextView) view.findViewById(R.id.timestamp);
            final TextView wav = (TextView) view.findViewById(R.id.wav);
            final TextView acc = (TextView) view.findViewById(R.id.acc);
            final TextView gyro = (TextView) view.findViewById(R.id.gyro);
            final TextView linearacc = (TextView) view.findViewById(R.id.linearacc);
            timestamp.setText(baseDir+mTimestamp.get(count-position-1));
            wav.setText(mWav.get(count-position-1));
            acc.setText(mAcc.get(count-position-1));
            gyro.setText(mGyro.get(count-position-1));
            linearacc.setText(mLinearAcc.get(count-position-1));
            return view;
        }
        void addItem(String timestamp,String wav,String acc,String gyro,String linearacc){
            mTimestamp.add(timestamp);
            mWav.add(wav);
            mAcc.add(acc);
            mGyro.add(gyro);
            mLinearAcc.add(linearacc);
            notifyDataSetChanged();
        }
    }
}
