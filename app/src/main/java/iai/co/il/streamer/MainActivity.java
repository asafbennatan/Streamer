/*
 * Copyright (C) 2012,2013 Qianliang Zhang, Shawn Van Every, Samuel Audet
 *
 * IMPORTANT - Make sure the AndroidManifest.xml file looks like this:
 *
 * <?xml version="1.0" encoding="utf-8"?>
 * <manifest xmlns:android="http://schemas.android.com/apk/res/android"
 *     package="org.bytedeco.javacv.recordactivity"
 *     android:versionCode="1"
 *     android:versionName="1.0" >
 *     <uses-sdk android:minSdkVersion="4" />
 *     <uses-permission android:name="android.permission.CAMERA" />
 *     <uses-permission android:name="android.permission.INTERNET"/>
 *     <uses-permission android:name="android.permission.RECORD_AUDIO"/>
 *     <uses-permission android:name="android.permission.WAKE_LOCK"/>
 *     <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
 *     <uses-feature android:name="android.hardware.camera" />
 *     <application android:label="@string/app_name">
 *         <activity
 *             android:name="RecordActivity"
 *             android:label="@string/app_name"
 *             android:screenOrientation="landscape">
 *             <intent-filter>
 *                 <action android:name="android.intent.action.MAIN" />
 *                 <category android:name="android.intent.category.LAUNCHER" />
 *             </intent-filter>
 *         </activity>
 *     </application>
 * </manifest>
 *
 * And the res/layout/main.xml file like this:
 *
 * <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
 *     xmlns:tools="http://schemas.android.com/tools"
 *     android:id="@+id/record_layout"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent" >
 * 
 *     <TextView
 *         android:id="@+id/textView1"
 *         android:layout_width="wrap_content"
 *         android:layout_height="wrap_content"
 *         android:layout_centerHorizontal="true"
 *         android:layout_centerVertical="true"
 *         android:padding="8dp"
 *         android:text="@string/app_name"
 *         tools:context=".RecordActivity" />
 *
 *     <Button
 *         android:id="@+id/recorder_control"
 *         android:layout_width="wrap_content"
 *         android:layout_height="wrap_content"
 *         android:layout_above="@+id/textView1"
 *         android:layout_alignRight="@+id/textView1"
 *         android:layout_marginRight="70dp"
 *         android:text="Button" />
 *
 * </LinearLayout>
 */

package iai.co.il.streamer;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FFmpegFrameRecorder;


public class MainActivity extends AppCompatActivity {

    private final static String CLASS_LABEL = "RecordActivity";
    private final static String LOG_TAG = CLASS_LABEL;

    private PowerManager.WakeLock mWakeLock;

    private String url = "10.0.0.2";
    private int port=9999;
    long startTime = 0;
    boolean recording = false;

    private FFmpegFrameRecorder recorder;

    private boolean isPreviewOn = false;

    private int sampleAudioRateInHz = 44100;
    private int imageWidth = 1080;
    private int imageHeight = 720;
    private int frameRate = 30;
    private String format="mpegts";
    private static final String MY_PREFS_NAME="il.co.iai.streamer";
    private ListView listView=null;
    ArrayAdapter arrayAdapter=null;
    RequestQueue queue;


    /* audio data getting thread */
    private AudioRecord audioRecord;
    private AudioRecordRunnable audioRecordRunnable;
    private Thread audioThread;
    volatile boolean runAudioThread = true;

    /* video data getting thread */
    private Camera cameraDevice;
    private CameraView cameraView;

    private Frame yuvImage = null;
    private Toolbar toolbar;
    private MenuItem streamButton;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(R.layout.activity_main);
        toolbar=(Toolbar)findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
               if(item.getItemId()==R.id.action_settings){
                   openSettings();
                   return true;
               }
                if(item.getItemId()==R.id.action_play){
                    if(recording){
                        if(streamButton==null){
                            streamButton=item;
                        }
                        openCapture();

                    }
                    else{
                        item.setIcon(R.drawable.ic_stop);
                        stopRecording();
                    }

                    return true;
                }
                return false;
            }
        });

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL);
        mWakeLock.acquire();
        queue= Volley.newRequestQueue(this);
        initLayout();
        registerRecivers();
    }


    @Override
    protected void onResume() {
        super.onResume();

        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL);
            mWakeLock.acquire();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        recording = false;

        if (cameraView != null) {
            cameraView.stopPreview();
        }

        if(cameraDevice != null) {
            cameraDevice.stopPreview();
            cameraDevice.release();
            cameraDevice = null;
        }

        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }


    private void initLayout() {

        LinearLayout mainLayout = (LinearLayout) this.findViewById(R.id.record_layout);

        cameraDevice = Camera.open();

        Log.i(LOG_TAG, "cameara open");
        cameraView = new CameraView(this, cameraDevice);

        LinearLayout.LayoutParams layoutParam = new LinearLayout.LayoutParams(imageWidth, imageHeight);
        mainLayout.addView(cameraView, layoutParam);
        Log.v(LOG_TAG, "added cameraView to mainLayout");
    }



    //---------------------------------------
    // initialize ffmpeg_recorder
    //---------------------------------------
    private void initRecorder() {

        Log.w(LOG_TAG, "init recorder");

      if (yuvImage == null) {
            yuvImage = new Frame(imageWidth, imageHeight, Frame.DEPTH_UBYTE, 2);
            Log.i(LOG_TAG, "create yuvImage");
        }

        Log.i(LOG_TAG,"protocol: udp"+ "ffmpeg_url: " + url +" port: " + port);
        recorder = new FFmpegFrameRecorder("udp://"+url+":"+port, imageWidth, imageHeight, 1);
        recorder.setFormat(format);
        recorder.setSampleRate(sampleAudioRateInHz);
        // Set in the surface changed method
        recorder.setFrameRate(frameRate);
        recorder.setVideoQuality(0);
        recorder.setVideoOption("preset", "ultrafast");
        Log.i(LOG_TAG, "recorder initialize success");

        audioRecordRunnable = new AudioRecordRunnable();
        audioThread = new Thread(audioRecordRunnable);
        runAudioThread = true;
    }

    public void startRecording() {

        initRecorder();

        try {
            recorder.start();
            startTime = System.currentTimeMillis();
            recording = true;
            audioThread.start();

        } catch (FFmpegFrameRecorder.Exception e) {
            e.printStackTrace();
        }
    }

    public void stopRecording() {

        runAudioThread = false;
        try {
            audioThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        audioRecordRunnable = null;
        audioThread = null;

        if (recorder != null && recording) {


            recording = false;
            Log.v(LOG_TAG,"Finishing recording, calling stop and release on recorder");
            try {
                recorder.stop();
                recorder.release();
            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
            recorder = null;

        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (recording) {
                stopRecording();
            }

            finish();

            return true;
        }

        return super.onKeyDown(keyCode, event);
    }




    //---------------------------------------------
    // audio thread, gets and encodes audio data
    //---------------------------------------------
    class AudioRecordRunnable implements Runnable {

        @Override
        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            // Audio
            int bufferSize;
            ShortBuffer audioData;
            int bufferReadResult;

            bufferSize = AudioRecord.getMinBufferSize(sampleAudioRateInHz,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleAudioRateInHz,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);


            audioData = ShortBuffer.allocate(bufferSize);


            Log.d(LOG_TAG, "audioRecord.startRecording()");
            audioRecord.startRecording();

            /* ffmpeg_audio encoding loop */
            while (runAudioThread) {

                //Log.v(LOG_TAG,"recording? " + recording);
                bufferReadResult = audioRecord.read(audioData.array(), 0, audioData.capacity());
                audioData.limit(bufferReadResult);
                if (bufferReadResult > 0) {
                    Log.v(LOG_TAG, "bufferReadResult: " + bufferReadResult);

                    // If "recording" isn't true when start this thread, it never get's set according to this if statement...!!!
                    // Why?  Good question...
                    if (recording) {
                       try {
                            recorder.recordSamples(audioData);
                            //Log.v(LOG_TAG,"recording " + 1024*i + " to " + 1024*i+1024);
                        } catch (FFmpegFrameRecorder.Exception e) {
                            Log.v(LOG_TAG,e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }
            Log.v(LOG_TAG,"AudioThread Finished, release audioRecord");

            /* encoding finish, release recorder */
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
                Log.v(LOG_TAG,"audioRecord released");
            }
        }
    }

    //---------------------------------------------
    // camera thread, gets and encodes video data
    //---------------------------------------------
    class CameraView extends SurfaceView implements SurfaceHolder.Callback, PreviewCallback {

        private SurfaceHolder mHolder;
        private Camera mCamera;

        public CameraView(Context context, Camera camera) {
            super(context);
            Log.w("camera", "camera view");
            mCamera = camera;
            mHolder = getHolder();
            mHolder.addCallback(CameraView.this);
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            mCamera.setPreviewCallback(CameraView.this);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                stopPreview();
                mCamera.setPreviewDisplay(holder);
            } catch (IOException exception) {
                mCamera.release();
                mCamera = null;
            }
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Camera.Parameters camParams = mCamera.getParameters();
            List<Camera.Size> sizes = camParams.getSupportedPreviewSizes();
            // Sort the list in ascending order
            Collections.sort(sizes, new Comparator<Camera.Size>() {

                public int compare(final Camera.Size a, final Camera.Size b) {
                    return a.width * a.height - b.width * b.height;
                }
            });

            // Pick the first preview size that is equal or bigger, or pick the last (biggest) option if we cannot
            // reach the initial settings of imageWidth/imageHeight.
            for (int i = 0; i < sizes.size(); i++) {
                if ((sizes.get(i).width >= imageWidth && sizes.get(i).height >= imageHeight) || i == sizes.size() - 1) {
                    imageWidth = sizes.get(i).width;
                    imageHeight = sizes.get(i).height;
                    Log.v(LOG_TAG, "Changed to supported resolution: " + imageWidth + "x" + imageHeight);
                    break;
                }
            }
            camParams.setPreviewSize(imageWidth, imageHeight);


            Log.v(LOG_TAG, "Setting imageWidth: " + imageWidth + " imageHeight: " + imageHeight + " frameRate: " + frameRate);
            int selectedFps=frameRate;
            int gap=frameRate;
            for (Integer i:camParams.getSupportedPreviewFrameRates()){

                if(frameRate-i < gap){
                    gap=frameRate-i;
                    selectedFps=i;
                }
            }
            if(frameRate!=selectedFps){
                Log.w(CLASS_LABEL,"selected framerate is unsupported using " + selectedFps + " instead");
            }

            camParams.setPreviewFrameRate(selectedFps);
            Log.v(LOG_TAG,"Preview Framerate: " + camParams.getPreviewFrameRate());

            mCamera.setParameters(camParams);
            startPreview();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            try {
                mHolder.addCallback(null);
                mCamera.setPreviewCallback(null);
            } catch (RuntimeException e) {
                // The camera has probably just been released, ignore.
            }
        }

        public void startPreview() {
            if (!isPreviewOn && mCamera != null) {
                isPreviewOn = true;
                mCamera.startPreview();

            }
        }

        public void stopPreview() {
            if (isPreviewOn && mCamera != null) {
                isPreviewOn = false;
                mCamera.stopPreview();
            }
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (audioRecord == null || audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                startTime = System.currentTimeMillis();
                return;
            }
            long t = 1000 * (System.currentTimeMillis() - startTime);
            if (yuvImage != null && recording) {
                if (t > recorder.getTimestamp()) {
                    recorder.setTimestamp(t);
                }

                try {
                    Log.v(LOG_TAG,"Writing Frame");



                    ((ByteBuffer)yuvImage.image[0].position(0)).put(data);
                    recorder.record(yuvImage);

                } catch (FFmpegFrameRecorder.Exception e) {
                    Log.v(LOG_TAG,e.getMessage());
                    e.printStackTrace();
                }

            }


            }
        }


    private void registerRecivers(){
        BroadcastReceiver receiver= new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Serializable s=intent.getSerializableExtra("response");
                if(s instanceof  CategoryListHolder){
                    List<Category> cats= ((CategoryListHolder)s).getList();
                    for(Category category: cats){
                        if(!DataHolder.categories.contains(category)){
                            DataHolder.categories.add(category);
                        }
                    }
                }
            }
        };
        IntentFilter filter= new IntentFilter("il.co.iai.cats");



        BroadcastReceiver receiverSetCat= new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
              startRecording();
            }
        };
        IntentFilter filterSetCat= new IntentFilter("il.co.iai.cats.set");


        LocalBroadcastManager localBroadcastManager=LocalBroadcastManager.getInstance(this);
        localBroadcastManager.registerReceiver(receiver,filter);
        localBroadcastManager.registerReceiver(receiverSetCat, filterSetCat);
    }

    private void requestCategories(String serverUrl){
        Map<String,String> headers= new HashMap<>();
        String path=serverUrl+"/rest/categories";
        RequestListener<CategoryListHolder> listener= new RequestListener<>("il.co.iai.cats",this);
        ErrorListener errorListener = new ErrorListener("il.co.iai.cats.error",this);
        JsonRequest<CategoryListHolder> categoryListHolderJsonRequest = new JsonRequest<>(path,CategoryListHolder.class,headers,listener,errorListener);
        queue.add(categoryListHolderJsonRequest);
    }

    private void setCategory(String serverUrl){
        if(DataHolder.selected!=null){
            Map<String,String> headers= new HashMap<>();
            String path="http://"+serverUrl+"/rest/plugins/iai/stream/"+DataHolder.selected.getId();
            RequestListener<Serializable> listener= new RequestListener<>("il.co.iai.cats.set",this);
            ErrorListener errorListener = new ErrorListener("il.co.iai.cats.set.error",this);
            VoidJsonRequest setCat = new VoidJsonRequest(path,Serializable.class,headers,listener,errorListener);
            queue.add(setCat);
        }else{
            startRecording();
        }


    }


    public void openCapture(){




        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Get the layout inflater
        LayoutInflater inflater = getLayoutInflater();


        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        View v=inflater.inflate(R.layout.capture_dialog_box, null);
        if(listView==null){
            listView=(ListView)v.findViewById(R.id.listView);
            arrayAdapter =new ArrayAdapter<Category>(this,R.layout.item,DataHolder.categories);
            listView.setAdapter(arrayAdapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    Object o = arrayAdapter.getItem(position);
                    if (o instanceof Category) {
                        DataHolder.selected=(Category)o;
                    }
                }
            });


        }

        builder.setView(v)

                // Add action buttons
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        if (streamButton != null) {
                            streamButton.setIcon(R.drawable.ic_stop);
                        }
                        setCategory(url);

                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        builder.create().show();

    }


    public void openSettings(){



        SharedPreferences prefs = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE);
        final String urlP = prefs.getString("url", url);
        final int portP = prefs.getInt("port", port);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            // Get the layout inflater
            LayoutInflater inflater = getLayoutInflater();


            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
        View v=inflater.inflate(R.layout.settings_dialog_box, null);
        final EditText text = (EditText) v.findViewById(R.id.address);
        final EditText portV= (EditText) v.findViewById(R.id.port);
        text.setText(urlP);
        portV.setText(portP+"");
            builder.setView(v)

                    // Add action buttons
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {

                            SharedPreferences prefs = getSharedPreferences(MY_PREFS_NAME, MODE_PRIVATE);
                            url = text.getText().toString();
                            port = Integer.parseInt(portV.getText().toString());
                            prefs.edit().putString("url", url).putInt("port",port);
                            requestCategories("http://"+url+"/FlexiCore/");

                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
            builder.create().show();

    }


}
