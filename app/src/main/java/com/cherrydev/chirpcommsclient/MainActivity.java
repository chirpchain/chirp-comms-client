package com.cherrydev.chirpcommsclient;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.carrotsearch.hppc.IntLongHashMap;
import com.carrotsearch.hppc.IntLongMap;
import com.carrotsearch.hppc.cursors.IntLongCursor;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends ActionBarActivity {

    private static final String TAG_ACTIVITY = "MainActivity";

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private boolean mIsReady;

    private int mMyPeerId;
    private Set<Integer> mConnectedPeers = new HashSet<>();
    private Handler mHandler = new Handler();
    private Timer mTime = new Timer();
    private TimerTask mSendAudioTask;
    private TimerTask mDisplayStatsTask;
    private IntLongMap mReceivedDataStats = new IntLongHashMap();

    private TextView mClientIdText;
    private ListView mReceivedStatsList;
    private ChirpNetworkCommsService mCommsService;
    private ChirpCommsServiceListener mCommsListener;
    private ServiceConnection mCommsServiceConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mClientIdText = (TextView) findViewById(R.id.clientIdText);
        mReceivedStatsList = (ListView) findViewById(R.id.receivedStatsList);
        updateStatsList();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent serviceIntent = new Intent(this, ChirpNetworkCommsService.class);
        startService(serviceIntent);
        bindService(serviceIntent, mCommsServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mCommsService = ((ChirpNetworkCommsService.LocalBinder) service).getService();
                onBindToService();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mCommsService = null;
                onDisconnectedFromService();
            }
        }, 0);
    }

    @Override
    protected void onStop() {
        if (mCommsListener != null) {
            mCommsService.removeListener(mCommsListener);
            mCommsListener = null;
        }
        if (mCommsServiceConnection != null) {
            unbindService(mCommsServiceConnection);
            mCommsService = null;
        }
        super.onStop();
    }

    private void onBindToService() {
        mCommsListener = new BaseCommsServiceListener() {
            @Override
            public void ready() {
                onReady();
            }

            @Override
            public void disconnected() {
                onDisconnect();
            }

            @Override
            public void peerIdSet(int peerId) {
                onGotClientId(peerId);
            }

            @Override
            public void peerConnected(int newPeerId) {
                mConnectedPeers.add(newPeerId);
                updateStatsList();
            }

            @Override
            public void peerDisconnected(int peerId) {
                mConnectedPeers.remove(peerId);
                updateStatsList();
            }

            @Override
            public void receivePeerList(Set<Integer> peerIds) {
                mConnectedPeers.clear();
                mConnectedPeers.addAll(peerIds);
                updateStatsList();
            }

            @Override
            public void receiveAudioData(AudioDataMessage message) {
                mReceivedDataStats.putOrAdd(message.getFrom(), message.getData().length, message.getData().length);

            }
        };
        mCommsService.addListener(mCommsListener);
    }

    private void onDisconnectedFromService() {
        mCommsListener = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void onReady() {
        Log.i(TAG_ACTIVITY, "Ready now!");
        mSendAudioTask = new SendAudioToConnectedPeersTimerTask();
        int packetsPerSecond = 48000 / 960 / 2; // 25
        mTime.scheduleAtFixedRate(mSendAudioTask, 0, 1000 / packetsPerSecond);
        mDisplayStatsTask = new DisplayStatsTimeTask();
        mTime.scheduleAtFixedRate(mDisplayStatsTask, 0, 10000);
        updateStatsList();
    }

    private void onDisconnect() {
        mSendAudioTask.cancel();
        mSendAudioTask = null;
        mDisplayStatsTask.cancel();
        mDisplayStatsTask = null;
        mConnectedPeers.clear();
        mMyPeerId = -1;
        mIsReady = false;
        Log.i(TAG_ACTIVITY, "Disconnected");
    }

    private void onGotClientId(final int id) {
        Log.d("SocketIO", "I was given a socket id of " + mMyPeerId);
        mHandler.post(() -> mClientIdText.setText(getResources().getString(R.string.client_id_string, id)));
    }

    private void updateStatsList() {
        mHandler.post(() -> {
            @SuppressWarnings("unchecked")
            ArrayAdapter<Integer> adapter = (ArrayAdapter<Integer>) mReceivedStatsList.getAdapter();
            if (adapter == null) {
                adapter = new ArrayAdapter<Integer>(this, android.R.layout.simple_list_item_1, android.R.id.text1) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        if (convertView == null)
                            convertView = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
                        TextView text = (TextView) convertView.findViewById(android.R.id.text1);
                        int clientId = getItem(position);
                        long received = mReceivedDataStats.getOrDefault(clientId, 0);
                        text.setText("Client id:" + clientId + " received:" + HumanReadableByteLength.humanReadableByteCount(received, false));
                        return convertView;
                    }
                };
                mReceivedStatsList.setAdapter(adapter);
            }
            adapter.clear();
            for (int clientId : mConnectedPeers) {
                adapter.add(clientId);
            }
        });
    }

    private class SendAudioToConnectedPeersTimerTask extends TimerTask {
        private Random random = new Random();
        public void run() {
            byte[] data = new byte[960];
            random.nextBytes(data);
            for(int peerId : mConnectedPeers) {
                AudioDataMessage m = new AudioDataMessage(mMyPeerId, peerId, data, 48000);
                if(mCommsService == null) return;
                mCommsService.sendAudioData(m);
            }
        }

    }

    private class DisplayStatsTimeTask extends TimerTask {

        public void run() {
            String statsMessage = "Recieve stats";
            for(IntLongCursor stat : mReceivedDataStats) {
                statsMessage += "\n\tClient " + stat.key + " received:" + HumanReadableByteLength.humanReadableByteCount(stat.value, false);
            }
            Log.d(TAG_ACTIVITY, statsMessage);
            mHandler.post(((BaseAdapter) mReceivedStatsList.getAdapter())::notifyDataSetChanged);
        }
    }
}
