package com.example.Exciteometer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.*;

import java.io.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Main extends Activity {

    private static final String LOG_TAG = "EXCITE_ME_MAIN";

    private static final int RECORDER_BPP = 16;
    private static final String AUDIO_RECORDER_FILE_EXT_WAV = ".wav";
    private static final String AUDIO_RECORDER_FOLDER = "Exciteometer";
    private static final String AUDIO_RECORDER_TEMP_FILE = "record_temp.raw";
    private static final int RECORDER_SAMPLERATE = 8000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord recorder = null;
    private int bufferSize = 0;
    private Thread recordingThread = null;
    private boolean isRecording = false;

    private List<Double> currentDecibels = new ArrayList<Double>();

    private List entries = new ArrayList();
    private Entry currentEntry;

    private static final DecimalFormat twoDForm = new DecimalFormat("#.00");

    private TableLayout resultsTable;

    private Button newSession;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        resultsTable = (TableLayout) findViewById(R.id.resultstable);
        newSession = (Button) findViewById(R.id.newsession);

        setButtonHandlers();
        enableButtons(false);

        bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
    }

    private void setButtonHandlers() {
        ((Button) findViewById(R.id.btnStart)).setOnClickListener(btnClick);
        ((Button) findViewById(R.id.btnStop)).setOnClickListener(btnClick);
        newSession.setOnClickListener(newSessionClick);
    }

    private void enableButton(int id, boolean isEnable) {
        ((Button) findViewById(id)).setEnabled(isEnable);
    }

    private void enableButtons(boolean isRecording) {
        enableButton(R.id.btnStart, !isRecording);
        enableButton(R.id.btnStop, isRecording);
    }

    private String getFilename() {
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath, AUDIO_RECORDER_FOLDER);

        if (!file.exists()) {
            file.mkdirs();
        }

        return (file.getAbsolutePath() + "/" + System.currentTimeMillis() + AUDIO_RECORDER_FILE_EXT_WAV);
    }

    private String getTempFilename() {
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath, AUDIO_RECORDER_FOLDER);

        if (!file.exists()) {
            file.mkdirs();
        }

        File tempFile = new File(filepath, AUDIO_RECORDER_TEMP_FILE);

        if (tempFile.exists())
            tempFile.delete();

        return (file.getAbsolutePath() + "/" + AUDIO_RECORDER_TEMP_FILE);
    }

    private void startRecording() {

        currentDecibels = new ArrayList<Double>();

        recorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE,
                RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING,
                bufferSize
        );

        recorder.startRecording();

        isRecording = true;

        recordingThread = new Thread(new Runnable() {

            @Override
            public void run() {
                writeAudioDataToFile();
            }
        }, "Exciteometer Thread");

        recordingThread.start();

        ((Button) findViewById(R.id.btnStop)).setVisibility(View.VISIBLE);
        final Animation animation = new AlphaAnimation(1, 0); // Change alpha from fully visible to invisible
        animation.setDuration(500); // duration - half a second
        animation.setInterpolator(new LinearInterpolator()); // do not alter animation rate
        animation.setRepeatCount(Animation.INFINITE); // Repeat animation infinitely
        animation.setRepeatMode(Animation.REVERSE); // Reverse animation at the end so the button will fade back in
        ((Button) findViewById(R.id.btnStop)).startAnimation(animation);

    }

    private void writeAudioDataToFile() {
        byte data[] = new byte[bufferSize];
        String filename = getTempFilename();
        FileOutputStream os = null;

        try {
            os = new FileOutputStream(filename);
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        int read = 0;

        if (null != os) {
            while (isRecording) {
                read = recorder.read(data, 0, bufferSize);


                // see http://stackoverflow.com/questions/2917762/android-pcm-bytes
                double p2 = data[data.length - 1];
                double decibel;
                if (p2 == 0) {
                    Log.i(LOG_TAG, "No audio value recieved, going to infinity");
                } else {
                    decibel = 20.0 * Math.log10(p2 / 32768);

                    if (!Double.isNaN(decibel)) {
                        Log.i(LOG_TAG, "Decibel: " + decibel );
                        currentDecibels.add(decibel);
                    }
                }

                if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                    try {
                        os.write(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void stopRecording() {

        String fileName = getFilename();

        if (null != recorder) {
            isRecording = false;

            double sum = 0;
            for (Double aDouble : currentDecibels)
                sum += aDouble;

            double mean = sum / currentDecibels.size();

            currentEntry.setFinalResult(mean);
            currentEntry.setFile(fileName);
            entries.add(new Entry(currentEntry));
            buildResultTable();

            recorder.stop();
            recorder.release();

            recorder = null;
            recordingThread = null;
        }

        copyWaveFile(getTempFilename(), fileName);
        deleteTempFile();



        findViewById(R.id.btnStop).setVisibility(View.GONE);
        findViewById(R.id.app_info).setVisibility(View.GONE);

    }

    private void buildResultTable() {
        resultsTable.removeAllViews();
        sort(entries);
        for (Object entry1 : entries) {
            Entry entry = (Entry) entry1;
            addResultEntry(entry);
        }
    }


    private void addResultEntry(Entry entry) {

        TableRow row = new TableRow(this);

        TextView nameText = new TextView(this);
        nameText.setText(entry.getName());
        nameText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);

        TextView valueText = new TextView(this);
        valueText.setText(twoDForm.format(entry.getFinalResult()) + " uNiT");
        valueText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);

        row.addView(nameText);
        row.addView(valueText);

        TableRow.LayoutParams params = (TableRow.LayoutParams) nameText.getLayoutParams();
        params.column = 1;
        params.span = 1;
        params.setMargins(2, 2, 2, 2);
        params.width = TableRow.LayoutParams.FILL_PARENT;
        params.height = TableRow.LayoutParams.WRAP_CONTENT;
        nameText.setPadding(2, 2, 2, 2);
        nameText.setLayoutParams(params);

        resultsTable.addView(row,
                new TableLayout.LayoutParams
                        (TableLayout.LayoutParams.WRAP_CONTENT,
                                TableLayout.LayoutParams.WRAP_CONTENT));
    }

    private void deleteTempFile() {
        File file = new File(getTempFilename());

        file.delete();
    }

    private void copyWaveFile(String inFilename, String outFilename) {
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = RECORDER_SAMPLERATE;
        int channels = 2;
        long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels / 8;

        byte[] data = new byte[bufferSize];

        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;

            Log.i(LOG_TAG, "File size: " + totalDataLen);

            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);

            while (in.read(data) != -1) {
                out.write(data);
            }


            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void WriteWaveFileHeader(
            FileOutputStream out, long totalAudioLen,
            long totalDataLen, long longSampleRate, int channels,
            long byteRate) throws IOException {

        byte[] header = new byte[44];

        header[0] = 'R';  // RIFF/WAVE header
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
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
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
        header[32] = (byte) (2 * 16 / 8);  // block align
        header[33] = 0;
        header[34] = RECORDER_BPP;  // bits per sample
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

    private View.OnClickListener newSessionClick = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            entries = new ArrayList();
            resultsTable.removeAllViews();
            buildResultTable();
            findViewById(R.id.app_info).setVisibility(View.VISIBLE);
        }
    };

    private View.OnClickListener btnClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btnStart: {

                    AlertDialog.Builder alert = new AlertDialog.Builder(Main.this);

                    alert.setTitle("New entry");
                    alert.setMessage("Add a new entry");

                    final EditText entryinput = new EditText(Main.this);
                    entryinput.setText("Entry " + (entries.size() + 1));
                    alert.setView(entryinput);

                    alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            enableButtons(true);
                            String value = entryinput.getText().toString();
                            currentEntry = new Entry(value, Double.NEGATIVE_INFINITY);
                            Log.i(LOG_TAG, "Start Recording for " + value);
                            startRecording();
                        }
                    });

                    alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // Canceled.
                        }
                    });

                    alert.show();

                    break;
                }
                case R.id.btnStop: {

                    Log.i(LOG_TAG, "Stop Recording");

                    v.clearAnimation();

                    enableButtons(false);
                    stopRecording();

                    break;
                }
            }
        }
    };

    public class Entry {
        private String name;
        private double finalResult;
        private String file;

        public Entry(String name, double finalResult) {
            this.name = name;
            this.finalResult = finalResult;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public double getFinalResult() {
            return finalResult;
        }

        public void setFinalResult(double finalResult) {
            this.finalResult = finalResult;
        }

        public Entry(Entry another) {
            this.name = another.name;
            this.finalResult = another.finalResult;
        }

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }
    }

    public void sort(List<Entry> entries) {
        Collections.sort(entries, new Comparator<Entry>() {
            @Override
            public int compare(Entry p1, Entry p2) {
                if (p1.getFinalResult() > p2.getFinalResult()) return -1;
                if (p1.getFinalResult() < p2.getFinalResult()) return 1;
                return 0;
            }
        });
    }

}