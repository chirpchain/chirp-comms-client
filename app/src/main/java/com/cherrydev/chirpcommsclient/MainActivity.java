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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.carrotsearch.hppc.IntLongHashMap;
import com.carrotsearch.hppc.IntLongMap;
import com.carrotsearch.hppc.cursors.IntLongCursor;
import com.cherrydev.chirpcommsclient.messages.ChirpMessage;
import com.cherrydev.chirpcommsclient.messages.MessageType;
import com.cherrydev.chirpcommsclient.socketmessages.ByteMessage;
import com.cherrydev.chirpcommsclient.socketmessages.ChirpSocketMessage;
import com.cherrydev.chirpcommsclient.socketservice.BaseSocketServiceListener;
import com.cherrydev.chirpcommsclient.socketservice.SocketServiceListener;
import com.cherrydev.chirpcommsclient.socketservice.SocketService;
import com.cherrydev.chirpcommsclient.socketmessages.AudioDataMessage;
import com.cherrydev.chirpcommsclient.util.HumanReadableByteLength;
import com.cherrydev.chirpcommsclient.util.ServiceBinding;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends ActionBarActivity {

    private static final String TAG_ACTIVITY = "MainActivity";

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private boolean mIsReady;

    private Handler mHandler = new Handler();
    private Timer mTime = new Timer();
    private TimerTask mSendAudioTask;
    private TimerTask mDisplayStatsTask;
    private IntLongMap mReceivedDataStats = new IntLongHashMap();

    private TextView mClientIdText;
    private ListView mReceivedStatsList;
    private EditText mMessageText;
    private Button mMessageSendButton;

    private SocketService mSocketService;
    private ServiceBinding<SocketServiceListener, SocketService> socketServiceBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mClientIdText = (TextView) findViewById(R.id.clientIdText);
        mReceivedStatsList = (ListView) findViewById(R.id.receivedStatsList);
        mMessageText = (EditText) findViewById(R.id.messageText);
        mMessageSendButton = (Button) findViewById(R.id.messageSendButton);

        mMessageSendButton.setOnClickListener(v -> {
            String text = mMessageText.getText().toString();
            for (byte peer : mSocketService.getConnectedPeers()) {
                ChirpMessage m = new ChirpMessage(mSocketService.getNodeId(), peer, "Joe", "Avi", EnumSet.noneOf(ChirpMessage.MessageFlags.class), text);
                mSocketService.sendByteData(new ByteMessage(mSocketService.getNodeId(), peer, m.toBytes()));
            }
            mMessageText.setText("");
        });

        updateStatsList();
    }

    @Override
    protected void onStart() {
        super.onStart();
        socketServiceBinding = new ServiceBinding<SocketServiceListener, SocketService>(this, SocketService.class) {
            @Override
            protected SocketServiceListener createListener() {
                return new BaseSocketServiceListener() {
                    @Override
                    public void ready() {
                        onReady();
                    }

                    @Override
                    public void disconnected() {
                        onDisconnect();
                    }

                    @Override
                    public void peerIdSet(byte peerId) {
                        onGotClientId(peerId);
                    }

                    @Override
                    public void peerConnected(byte newPeerId) {
                        updateStatsList();
                    }

                    @Override
                    public void peerDisconnected(byte peerId) {
                        updateStatsList();
                    }

                    @Override
                    public void receivePeerList(Set<Byte> peerIds) {
                        updateStatsList();
                    }

                    @Override
                    public void receiveAudioData(AudioDataMessage message) {
                        mReceivedDataStats.putOrAdd(message.getFrom(), message.getData().length, message.getData().length);
                    }

                    @Override
                    public void receiveChirpMessage(final ChirpSocketMessage message) {
                        Log.i(TAG_ACTIVITY, "Received chirp message: " + message.getMessage().getMessage());
                        mHandler.post(() -> Toast.makeText(MainActivity.this, message.getMessage().getMessage(), Toast.LENGTH_LONG).show());
                    }

                    @Override
                    public void receiveByteData(ByteMessage message) {
                        MessageType type = message.getType();
                        if (type == null) {
                            Log.w(TAG_ACTIVITY, "Received an unknown message");
                            return;
                        }
                        ChirpMessage m = new ChirpMessage(message.getBytes());
                        Log.i(TAG_ACTIVITY, "Received chirp byte message: " + m.getMessage());
                        mHandler.post(() -> Toast.makeText(MainActivity.this, m.getMessage(), Toast.LENGTH_LONG).show());
                    }
                };
            }
        };
        socketServiceBinding.setOnConnect(s -> mSocketService = s);
        socketServiceBinding.setOnDisconnect(() -> mSocketService = null);
        socketServiceBinding.connect();
    }

    @Override
    protected void onStop() {
        socketServiceBinding.disconnect();
        super.onStop();
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
        if (mSendAudioTask != null) {
            mSendAudioTask.cancel();
        }
        mSendAudioTask = new SendAudioToConnectedPeersTimerTask();
        int packetsPerSecond = 48000 / 960 / 2; // 25
        mTime.scheduleAtFixedRate(mSendAudioTask, 0, 1000 / packetsPerSecond);
        if (mDisplayStatsTask != null) {
            mDisplayStatsTask.cancel();
        }
        mDisplayStatsTask = new DisplayStatsTimeTask();
        mTime.scheduleAtFixedRate(mDisplayStatsTask, 0, 10000);
        updateStatsList();
    }

    private void onDisconnect() {
        if (mSendAudioTask != null) {
            mSendAudioTask.cancel();
            mSendAudioTask = null;
        }
        if (mDisplayStatsTask != null) {
            mDisplayStatsTask.cancel();
            mDisplayStatsTask = null;
        }
        mIsReady = false;
        Log.i(TAG_ACTIVITY, "Disconnected");
    }

    private void onGotClientId(final byte id) {
        Log.d(TAG_ACTIVITY, "I was given a socket id of " + mSocketService.getNodeId());
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
            if (mSocketService != null) {
                for (int clientId : mSocketService.getConnectedPeers()) {
                    adapter.add(clientId);
                }
            }
        });
    }

    private class SendAudioToConnectedPeersTimerTask extends TimerTask {
        private Random random = new Random();
        public void run() {
            byte[] data = new byte[960];
            random.nextBytes(data);
            for(byte peerId : mSocketService.getConnectedPeers()) {
                AudioDataMessage m = new AudioDataMessage(mSocketService.getNodeId(), peerId, data, 48000);
                if(mSocketService == null) return;
                mSocketService.sendAudioData(m);
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
