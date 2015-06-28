package com.cherrydev.chirpcommsclient;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.carrotsearch.hppc.IntLongHashMap;
import com.carrotsearch.hppc.IntLongMap;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


public class ChirpNetworkCommsService extends Service {

    private static final String TAG_SOCKET = "SocketIO";

    private static final String PEER_DISCONNECTED_EVENT = "peerDisconnected";
    private static final String AUDIO_DATA_EVENT = "audioData";
    private static final String ASSIGN_ME_AN_ID_EVENT = "assignMeAnId";
    private static final String SET_CLIENT_ID_EVENT = "setClientId";
    private static final String NEW_PEER_EVENT = "newPeer";

    private Set<ChirpCommsServiceListener> mListeners = new HashSet<>();

    private boolean mIsReady;
    private Socket mSocket;
    private int mPeerId;
    private Set<Integer> mConnectedPeers = new HashSet<>();
    private IntLongMap mRecievedDataStats = new IntLongHashMap();


    private final IBinder mBinder = new LocalBinder();

    public ChirpNetworkCommsService() {
    }

    public class LocalBinder extends Binder {
        ChirpNetworkCommsService getService() {
            return ChirpNetworkCommsService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(runSocketServerTask, "Socket Server Startup Thread").start();
        return START_STICKY;
    }

    public void addListener(ChirpCommsServiceListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(ChirpCommsServiceListener listener) {
        mListeners.remove(listener);
    }

    public boolean sendAudioData(AudioDataMessage message) {
        if (! mConnectedPeers.contains(message.getTo())) return false;
        message.setFrom(mPeerId);
        mSocket.emit(AUDIO_DATA_EVENT, message.getJson(null));
        return true;
    }

    private Runnable runSocketServerTask = () -> {
        try{
            mSocket = IO.socket("http://10.0.2.2:3000");
            mSocket.on(Socket.EVENT_CONNECT, args -> {
                Log.d(TAG_SOCKET, "Connected!");
                onConnect();
            });

            mSocket.on(AUDIO_DATA_EVENT, args -> {
                AudioDataMessage message = new AudioDataMessage((JSONObject) args[0]);
                long length = message.getData().length;
                mRecievedDataStats.putOrAdd(message.getFrom(), length, length);
                forEachListener(l -> l.receiveAudioData(message));
            });

            mSocket.on(SET_CLIENT_ID_EVENT, args -> {
                mPeerId = getIntArg(args);
                onGotClientId(mPeerId);
            });

            mSocket.on(NEW_PEER_EVENT, args -> {
                int newPeerId = getIntArg(args);
                if (newPeerId == mPeerId) return;
                Log.d("SocketIO", "New peer ID:" + newPeerId);
                mConnectedPeers.add(newPeerId);
                forEachListener(l -> l.peerConnected(newPeerId));
                checkReady();
            });

            mSocket.on(PEER_DISCONNECTED_EVENT, args -> {
                int disconnectedPeerId = getIntArg(args);
                Log.d("SocketIO", "Peer disconnected:" + disconnectedPeerId);
                mConnectedPeers.remove(disconnectedPeerId);
                forEachListener(l -> l.peerDisconnected(disconnectedPeerId));
            });

            mSocket.on("listPeers", args -> {
                try {
                    JSONArray peerIdArray = ((JSONArray) args[0]);
                    mConnectedPeers.clear();
                    for (int i = 0; i < peerIdArray.length(); i++) {
                        int peerId = peerIdArray.getInt(i);
                        if (peerId == mPeerId) continue;
                        mConnectedPeers.add(peerId);
                    }
                    forEachListener(l -> l.receivePeerList(mConnectedPeers));
                    checkReady();
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            });

            mSocket.on(Socket.EVENT_DISCONNECT, args -> onDisconnect());
            mSocket.on(Socket.EVENT_CONNECT_ERROR, args -> Log.d("SocketIO", "Connection Error! " + args[0]));
            mSocket.on(Socket.EVENT_CONNECT_TIMEOUT, args -> Log.d("SocketIO", "Connection timeout!"));
            mSocket.on(Socket.EVENT_ERROR, args -> {
                Throwable e = args.length >= 0 && args[0] instanceof Throwable ? ((Throwable)args[0]) : null;
                if (e != null) {
                    Log.e(TAG_SOCKET, "Socket error", e);
                }
                else {
                    Log.e(TAG_SOCKET, "Socket error, but no throwable! Args were:" + (args.length == 1 ? args[0].toString() : Arrays.toString(args)));
                }
            });
            mSocket.connect();
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    };

    private boolean checkReady() {
        if(mIsReady) return true;
        if(mPeerId < 0) return false;
        mConnectedPeers.remove(mPeerId);
        if(mConnectedPeers.size() == 0) return false;
        mIsReady = true;
        onReady();
        return true;
    }

    private void onConnect() {
        mSocket.emit(ASSIGN_ME_AN_ID_EVENT, 0);
        mSocket.emit("listPeers", 0);
        forEachListener(ChirpCommsServiceListener::connected);
        checkReady();
    }

    private void onReady() {
        Log.i(TAG_SOCKET, "Ready now!");
        forEachListener(ChirpCommsServiceListener::ready);
    }

    private void onDisconnect() {
        mConnectedPeers.clear();
        mPeerId = -1;
        mIsReady = false;
        Log.i(TAG_SOCKET, "Disconnected");
        forEachListener(ChirpCommsServiceListener::disconnected);
    }

    private void onGotClientId(final int id) {
        mPeerId = id;
        Log.d("SocketIO", "I was given a socket id of " + mPeerId);
        forEachListener(l -> l.peerIdSet(id));
        checkReady();
    }

    private int getIntArg(Object[] args) {
        Object arg = args[0];
        if (arg instanceof Integer) return (int) arg;
        else {
            return Integer.parseInt(arg.toString());
        }
    }

    private void handleListenerException(Throwable e) {
        Log.w(TAG_SOCKET, "Unhandled exception in listener", e);
    }

    private void forEachListener(EachListenerAction action) {
        for(ChirpCommsServiceListener listener : mListeners) {
            try {
                action.eachListener(listener);
            }
            catch (Exception e) {
                handleListenerException(e);
            }
        }
    }

    private interface EachListenerAction {
        void eachListener(ChirpCommsServiceListener listener);
    }
}
