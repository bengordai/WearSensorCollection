package com.a712.wearcollect;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;


/**
 * 这是整个音频记录部分，记录为raw和wav(PCM)两个文件
 * 侦听的时候先记录为raw文件，结束的时候补上wav头部再写进wav文件
 * 单例模式，getInstance来获得单例
 * setRootPath设置一次在退出前有效
 * Created by ben on 2016/11/20.
 */

final class AudioRecorder {
    private final static int AUDIO_INPUT = MediaRecorder.AudioSource.MIC;
    private final static int AUDIO_SAMPLE_RATE = 44100;
    private final static int AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_DEFAULT;
    private final static int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static AudioRecorder mInstance;
    private int bufferSizeInBytes = 0;
    private File baseDir;
    private String rawFilename;
    private String wavFilename;
    private boolean isRecording = false;
    private AudioRecord audioRecord;

    private AudioRecorder(Context context) {
        baseDir = context.getFilesDir();
    }

    synchronized static AudioRecorder getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new AudioRecorder(context);
        }
        return mInstance;
    }

    boolean startRecording(Date date) {
        createAudioRecord(date);
        audioRecord.startRecording();
        isRecording = true;
        new Thread(new AudioRecordingThread()).start();
        return true;
    }

    boolean stopRecording() {
        if (audioRecord != null) {
            isRecording = false;
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            return true;
        } else {
            return false;
        }
    }


    private void createAudioRecord(Date date) {
        // 获取音频文件路径
        rawFilename = getRawFilename(date);
        wavFilename = getWavFilename(date);

        // 获得缓冲区字节大小
        bufferSizeInBytes = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, AUDIO_CHANNEL, AUDIO_ENCODING);

        // 创建AudioRecord对象
        audioRecord = new AudioRecord(AUDIO_INPUT, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL, AUDIO_ENCODING, bufferSizeInBytes);
    }

    private String getRawFilename(Date date) {
        return String.format("Audio-%s.raw", (new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")).format(date));
    }

    private String getWavFilename(Date date) {
        return String.format("Audio-%s.wav", (new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")).format(date));
    }


    private final class AudioRecordingThread implements Runnable {
        @Override
        public void run() {
            writeRawDataToFile();
            writeWavDataToFile();
        }

        void writeRawDataToFile() {
            byte[] audioData = new byte[bufferSizeInBytes];
            int readSize = 0;
            try {
                File rawFile = new File(baseDir, rawFilename);
                if (rawFile.exists()) {
                    rawFile.delete();
                }
                FileOutputStream fileOutputStream = new FileOutputStream(rawFile);
                while (isRecording) {
                    readSize = audioRecord.read(audioData, 0, bufferSizeInBytes);
                    if (readSize != AudioRecord.ERROR_INVALID_OPERATION) {
                        fileOutputStream.write(audioData);
                    }
                }
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        void writeWavDataToFile() {
            long byteRate = 16 * AUDIO_SAMPLE_RATE * AUDIO_CHANNEL / 8;
            try {
                File inFile = new File(baseDir, rawFilename);
                FileInputStream in = new FileInputStream(inFile);
                File outFile = new File(baseDir, wavFilename);
                FileOutputStream out = new FileOutputStream(outFile);
                long totalAudioLength = in.getChannel().size();
                long totalDataLength = totalAudioLength + 36;
                WriteWaveFileHeader(out, totalAudioLength, totalDataLength, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL, byteRate);
                byte[] data = new byte[bufferSizeInBytes];
                while (in.read(data) != -1) {
                    out.write(data);
                }
                in.close();
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        void WriteWaveFileHeader(FileOutputStream out, long totalAudioLen,
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
}
