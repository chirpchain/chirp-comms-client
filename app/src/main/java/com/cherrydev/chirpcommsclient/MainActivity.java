package com.cherrydev.chirpcommsclient;

import android.os.Handler;
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
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.cursors.IntLongCursor;
import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.github.nkzawa.socketio.parser.Binary;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends ActionBarActivity {

    private static final String TAG_SOCKET = "SocketIO";


    private static final String PEER_DISCONNECTED_EVENT = "peerDisconnected";
    private static final String AUDIO_DATA_EVENT = "audioData";
    private static final String ASSIGN_ME_AN_ID_EVENT = "assignMeAnId";
    private static final String SET_CLIENT_ID_EVENT = "setClientId";
    private static final String NEW_PEER_EVENT = "newPeer";

    private boolean isReady;
    private Socket socket;
    private int socketId;
    private Set<Integer> connectedPeers = new HashSet<>();
    private Handler handler = new Handler();
    private Timer timer = new Timer();
    private TimerTask sendAudioTask;
    private TimerTask displayStatsTask;
    private IntLongMap recievedDataStats = new IntLongHashMap();

    private TextView clientIdText;
    private ListView recievedStatsList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        clientIdText = (TextView) findViewById(R.id.clientIdText);
        recievedStatsList = (ListView) findViewById(R.id.recievedStatsList);
        updateStatsList();
        try{
            socket = IO.socket("http://10.0.2.2:3000");
            socket.on(Socket.EVENT_CONNECT, args -> {
                Log.d(TAG_SOCKET, "Connected!");
                onConnect();
            }).on(AUDIO_DATA_EVENT, args -> {
                AudioDataMessage message = new AudioDataMessage((JSONObject) args[0]);
                long length = message.getData().length;
                recievedDataStats.putOrAdd(Integer.parseInt(message.getFrom()), length, length);
                //Log.d("SocketIO", "Audio message was from: " + message.getFrom() + " of length " + message.getData().length);
            }).on(SET_CLIENT_ID_EVENT, args -> {
                socketId = getIntArg(args);
                onGotClientId(socketId);

            }).on(NEW_PEER_EVENT, args -> {
                int newPeerId = getIntArg(args);
                if (newPeerId == socketId) return;
                Log.d("SocketIO", "New peer ID:" + newPeerId);
                connectedPeers.add(newPeerId);
                checkReady();
                handler.post( () -> updateStatsList() );
            }).on(PEER_DISCONNECTED_EVENT, args -> {
                int disconnectedPeerId = getIntArg(args);
                Log.d("SocketIO", "Peer disconnected:" + disconnectedPeerId);
                connectedPeers.remove(disconnectedPeerId);
                handler.post( () -> updateStatsList() );
            }).on("listPeers", args -> {
                try {
                    JSONArray peerIdArray = ((JSONArray)args[0]);
                    connectedPeers.clear();
                    for (int i = 0; i < peerIdArray.length(); i++) {
                        connectedPeers.add(peerIdArray.getInt(i));
                    }
                    checkReady();
                    handler.post(() -> updateStatsList() );
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            });

            socket.on(Socket.EVENT_DISCONNECT, args -> onDisconnect());
            socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
                        Log.d("SocketIO", "Connection Error! " + args[0]);
                    });
            socket.on(Socket.EVENT_CONNECT_TIMEOUT, args -> Log.d("SocketIO", "Connection timeout!"));
            socket.connect();
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
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

    private boolean checkReady() {
        if(isReady) return true;
        if(socketId < 0) return false;
        connectedPeers.remove(socketId);
        if(connectedPeers.size() == 0) return false;
        isReady = true;
        onReady();
        return true;
    }

    private void onConnect() {
        socket.emit(ASSIGN_ME_AN_ID_EVENT, 0);
        socket.emit("listPeers", 0);
        checkReady();
    }

    private void onReady() {
        Log.i(TAG_SOCKET, "Ready now!");
        sendAudioTask = new SendAudioToConnectedPeersTimerTask();
        int packetsPerSecond = 48000 / 960 / 2; // 25
        timer.scheduleAtFixedRate(sendAudioTask, 0, 1000 / packetsPerSecond);
        displayStatsTask = new DisplayStatsTimeTask();
        timer.scheduleAtFixedRate(displayStatsTask, 0, 10000);
    }

    private void onDisconnect() {
        sendAudioTask.cancel();
        sendAudioTask = null;
        displayStatsTask.cancel();
        displayStatsTask = null;
        connectedPeers.clear();
        socketId = -1;
        isReady = false;
        Log.i(TAG_SOCKET, "Disconnected");
    }

    private void onGotClientId(final int id) {
        Log.d("SocketIO", "I was given a socket id of " + socketId);
        handler.post(() -> {
            clientIdText.setText(getResources().getString(R.string.client_id_string, id));
        });
        checkReady();
    }

    private void updateStatsList() {
        ArrayAdapter<Integer> adapter = (ArrayAdapter<Integer>) recievedStatsList.getAdapter();
        if (adapter == null) {
            adapter = new ArrayAdapter<Integer>(this, android.R.layout.simple_list_item_1, android.R.id.text1) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    if (convertView == null) convertView = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
                    TextView text = (TextView) convertView.findViewById(android.R.id.text1);
                    int clientId = getItem(position);
                    long received = recievedDataStats.getOrDefault(clientId, 0);
                    text.setText("Client id:" + clientId + " received:" + HumanReadableByteLength.humanReadableByteCount(received, false));
                    return convertView;
                }
            };
            recievedStatsList.setAdapter(adapter);
        }
        adapter.clear();
        for(int clientId : connectedPeers) {
            adapter.add(clientId);
        }
    }

    private int getIntArg(Object[] args) {
        Object arg = args[0];
        if (arg instanceof Integer) return (int) arg;
        else {
            return Integer.parseInt(arg.toString());
        }
    }

    private class SendAudioToConnectedPeersTimerTask extends TimerTask {
        private Random random = new Random();
        public void run() {
            byte[] data = new byte[960];
            random.nextBytes(data);
            for(int peerId : connectedPeers) {
                AudioDataMessage m = new AudioDataMessage("" + socketId, "" + peerId, data, 48000);
                if (! socket.connected()) return;
                socket.emit(AUDIO_DATA_EVENT, m.getJson(null));
            }
        }

    }

    private class DisplayStatsTimeTask extends TimerTask {

        public void run() {
            String statsMessage = "Recieve stats";
            for(IntLongCursor stat : recievedDataStats) {
                statsMessage += "\n\tClient " + stat.key + " received:" + HumanReadableByteLength.humanReadableByteCount(stat.value, false);
            }
            Log.d(TAG_SOCKET, statsMessage);
            handler.post(() -> ((BaseAdapter) recievedStatsList.getAdapter()).notifyDataSetChanged());
        }
    }
}
