package com.cherrydev.chirpcommsclient;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.cherrydev.chirpcommsclient.acousticservice.AcousticService;
import com.cherrydev.chirpcommsclient.acousticservice.AcousticServiceListener;
import com.cherrydev.chirpcommsclient.acousticservice.BaseAcousticServiceListener;
import com.cherrydev.chirpcommsclient.messagestore.MessageStoreListener;
import com.cherrydev.chirpcommsclient.messagestore.MessageStoreService;
import com.cherrydev.chirpcommsclient.routeservice.BaseRouteServiceListener;
import com.cherrydev.chirpcommsclient.routeservice.RouteService;
import com.cherrydev.chirpcommsclient.routeservice.RouteServiceListener;
import com.cherrydev.chirpcommsclient.util.ChirpNode;
import com.cherrydev.chirpcommsclient.util.RunnableTimerTask;
import com.cherrydev.chirpcommsclient.util.ServiceBinding;

import org.ocpsoft.prettytime.PrettyTime;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;


public class BlinkyLightsFragment extends Fragment {
    private static final String TAG = "BlinkyLightsFragment";
    private static final PrettyTime prettyTime = new PrettyTime();

    private Handler handler = new Handler();
    private ChirpMainActivity activity;
    private RouteService routeService;
    private ServiceBinding<RouteServiceListener, RouteService> routeserviceBinding;
    private MessageStoreService messageStoreService;
    private ServiceBinding<MessageStoreListener, MessageStoreService> messageStoreBinding;
    private AcousticService acousticService;
    private ServiceBinding<AcousticServiceListener, AcousticService> acousticServiceBinding;
    private Timer timer;

    @Bind(R.id.node_name)
    TextView nodeName;

    @Bind(R.id.connected_count)
    TextView connectedCount;

    @Bind(R.id.last_symbol)
    TextView lastSymbol;

    @Bind(R.id.battery_percent)
    TextView batteryPercent;

    public static BlinkyLightsFragment newInstance() {
        BlinkyLightsFragment fragment = new BlinkyLightsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_blinky_lights, container, false);
        ButterKnife.bind(this, v);
        return v;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = (ChirpMainActivity) activity;
        routeserviceBinding = new ServiceBinding<RouteServiceListener, RouteService>(activity, RouteService.class) {
            @Override
            protected RouteServiceListener createListener() {
                return new BaseRouteServiceListener() {
                    @Override
                    public void configChanged() {
                        updateConnectedCount();
                        updateRouteConfig();
                    }
                };
            }
        }.setOnConnect(s -> {
            routeService = s;
            updateRouteConfig();
            updateConnectedCount();
        })
        .setOnDisconnect(() -> routeService = null)
        .connect();
        messageStoreBinding = new ServiceBinding<MessageStoreListener, MessageStoreService>(activity, MessageStoreService.class) {

            @Override
            protected MessageStoreListener createListener() {
                return BlinkyLightsFragment.this::readLastMessage;
            }
        }.setOnConnect(s -> {
            messageStoreService = s;
            readLastMessage();
        })
        .setOnDisconnect(() -> messageStoreService = null)
        .connect();
        acousticServiceBinding = new ServiceBinding<AcousticServiceListener, AcousticService>(activity, AcousticService.class) {
            @Override
            protected AcousticServiceListener createListener() {
                return new BaseAcousticServiceListener() {
                    @Override
                    public void symbolSent(int symbol) {
                        handler.post(() -> updateLastSymbolSent(symbol));
                    }
                };
            }
        }
        .setOnConnect(s -> acousticService = s)
        .setOnDisconnect(() -> acousticService = null)
        .connect();
    }

    @Override
    public void onStart() {
        super.onStart();
        timer = new Timer("Blinky Lights Timer");
        timer.scheduleAtFixedRate(new RunnableTimerTask(() -> handler.post(() -> updatePeriodicUi())), 500, 30000);
    }

    @Override
    public void onStop() {
        super.onStop();
        timer.cancel();
        timer = null;
    }

    private static final int REQUEST_TAKE_PHOTO = 11;

    private void takePhoto() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            Log.w(TAG, "Couldn't make image file for photo!");
            return;
        }
        // Continue only if the File was successfully created
        if (photoFile != null) {
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                    Uri.fromFile(photoFile));
            startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
        }
        handler.postDelayed(checkCameraTimeoutTask = () -> checkForCameraTimeout(), CAMERA_TIMEOUT);
    }

    private static final int CAMERA_TIMEOUT = 120000;
    ComponentName cameraActivity;
    Runnable checkCameraTimeoutTask;

    private void checkForCameraTimeout() {
        ActivityManager am = (ActivityManager)activity.getSystemService(Context.ACTIVITY_SERVICE);
        ComponentName cn = am.getRunningTasks(1).get(0).topActivity;
        if (cameraActivity == null) {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraActivity = takePictureIntent.resolveActivity(activity.getPackageManager());
        }
        if (cn.equals(cameraActivity)) {
            Log.i(TAG, "Hey, the camera is still running!!");
            Intent mainIntent = new Intent(activity.getApplicationContext(), ChirpMainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.getApplication().startActivity(mainIntent);
        }
        else {
            Log.i(TAG, "Nope, camera was already closed");
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        return image;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == Activity.RESULT_OK) {
            if (checkCameraTimeoutTask != null) {
                // Do this so that it won't close a camera that had been opened a second time.
                handler.removeCallbacks(checkCameraTimeoutTask);
                checkCameraTimeoutTask = null;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onDetach() {
        if (routeserviceBinding != null) routeserviceBinding.disconnect();
        if (messageStoreBinding != null) messageStoreBinding.disconnect();
        if (acousticServiceBinding != null) acousticServiceBinding.disconnect();
        this.activity = null;
        super.onDetach();
    }

    private void updateConnectedCount() {
        int connected = routeService.getConnectedNodes().size();
        connectedCount.setText(getResources().getQuantityString(R.plurals.nodes_connected, connected, connected));
    }

    private void updateRouteConfig() {
        if (routeService == null) return;
        ChirpNode nodeInfo = routeService.getNodeInfo();
        if (nodeInfo != null) {
            nodeName.setText(String.format("%s (%d)", nodeInfo.getName(), nodeInfo.getId()));
        }
    }

    private void updateLastSymbolSent(int symbol) {
        lastSymbol.setText("Last symbol : " + symbol);
    }


    private void readLastMessage() {
        if (messageStoreService == null) return;
        MessageStoreService.MessageDisplay m = messageStoreService.getLastMessage();
        if (m != null) {
            // TODO: This is duplicate code from MessageListFragment
            setText(R.id.message_item_id, getView(), "Id: " + m.id);
            setText(R.id.message_item_recipient, getView(), "To: " + m.to);
            setText(R.id.message_item_sender, getView(), "From: " + m.from + " @ " + m.fromNodeName);
            setText(R.id.message_item_message, getView(), m.message);
            setText(R.id.message_item_date, getView(), "Date: " + prettyTime.format(m.date));
        }
    }

    private void setText(int resourceId, View view, String text) {
        ((TextView)view.findViewById(resourceId)).setText(text);
    }

    @OnClick(R.id.create_message_button)
    public void onClickCreateMessage() {
        activity.switchToCreateMessage();
    }

    @OnClick(R.id.show_messages_button)
    public void onClickShowMessages() {
        activity.switchToShowMessages();
    }

    @OnClick(R.id.take_photo_button)
    public void onTakePhoto() {takePhoto();}

    @Override
    public void onDestroyView() {
        ButterKnife.unbind(this);
        super.onDestroyView();
    }

    private void updatePeriodicUi() {
        if (batteryPercent != null) batteryPercent.setText(String.format("%.0f%%", getBatteryCharge() * 100));
        readLastMessage();
    }
    private float getBatteryCharge() {
        if (activity == null) return 0;
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = activity.registerReceiver(null, ifilter);
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            return level / (float) scale;
        }
        else {
            return 0;
        }
    }
}
