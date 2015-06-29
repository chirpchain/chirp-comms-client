package com.cherrydev.chirpcommsclient.socketservice;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import com.carrotsearch.hppc.IntLongHashMap;
import com.carrotsearch.hppc.IntLongMap;
import com.cherrydev.chirpcommsclient.socketmessages.AddressableMessage;
import com.cherrydev.chirpcommsclient.socketmessages.AudioDataMessage;
import com.cherrydev.chirpcommsclient.socketmessages.ByteMessage;
import com.cherrydev.chirpcommsclient.socketmessages.ChirpSocketMessage;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.jdeferred.DeferredManager;
import org.jdeferred.impl.DefaultDeferredManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


public class SocketService extends Service {


    private static final String TAG_SOCKET = "SocketIO";

    private static final String PEER_DISCONNECTED_EVENT = "peerDisconnected";
    private static final String AUDIO_DATA_EVENT = "audioData";
    private static final String ASSIGN_ME_AN_ID_EVENT = "assignMeAnId";
    private static final String SET_CLIENT_ID_EVENT = "setClientId";
    private static final String NEW_PEER_EVENT = "newPeer";
    private static final String BYTE_DATA_EVENT = "byteData";
    private static final String CHIRP_MESSAGE_DATA_EVENT = "chirpData";
    private static final String CLIENT_ERROR_EVENT = "clientError";
    private static final String LOGIN_EVENT = "login";
    public static final String LIST_PEERS_EVENT = "listPeers";

    private Set<SocketServiceListener> mListeners = new HashSet<>();
    private boolean hasStarted;
    private boolean ready;
    private Socket mSocket;
    private byte mNodeId = -1;
    private Set<Byte> connectedPeers = new HashSet<>();
    private IntLongMap recievedDataStats = new IntLongHashMap();

    private String serverUrl;

    private final IBinder mBinder = new LocalBinder();

    public SocketService() {
    }

    public class LocalBinder extends Binder {
        public SocketService getService() {
            return SocketService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (! hasStarted) {
            hasStarted = true;
            DeferredManager dm = new DefaultDeferredManager();
            dm.when(() -> {
                // Set default...
                serverUrl = "http://10.0.2.2:3000";
                File f = new File(Environment.getExternalStorageDirectory() + "/chirpconfig.json");
                if (!f.canRead()) {
                    Log.w(TAG_SOCKET, "No config file found");
                    return;
                }
                try {
                    byte[] fileBytes = new byte[(int) f.length()];
                    new FileInputStream(f).read(fileBytes);
                    String fileString = new String(fileBytes);
                    JSONObject config = new JSONObject(fileString);
                    String serverUrl = config.optString("serverUrl");
                    if (serverUrl != null) this.serverUrl = serverUrl;
                    byte nodeId = (byte) config.optInt("nodeId", -1);
                    if (nodeId >= 0) this.mNodeId = nodeId;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).then((x) -> {
                runSocketServerTask.run();
            });
            //new Thread(runSocketServerTask, "Socket Server Startup Thread").start();
        }
        return START_STICKY;
    }

    public Set<Byte> getConnectedPeers() {
        return connectedPeers;
    }

    public byte getNodeId() {
        return mNodeId;
    }

    public boolean isReady() {
        return ready;
    }

    public void addListener(SocketServiceListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(SocketServiceListener listener) {
        mListeners.remove(listener);
    }

    public boolean sendAudioData(AudioDataMessage message) {
        return sendMessage(AUDIO_DATA_EVENT, message);
    }

    public boolean sendByteData(ByteMessage message) {
        return sendMessage(BYTE_DATA_EVENT, message);
    }

    public boolean sendChirpData(ChirpSocketMessage message) {
        return sendMessage(CHIRP_MESSAGE_DATA_EVENT, message);
    }

    private boolean sendMessage(String event, AddressableMessage message) {
        if (! connectedPeers.contains(message.getTo())) {
            Log.w(TAG_SOCKET, "Got asked to send a message to " + message.getTo() + " but it wasn't connected");
            return false;
        }
        message.setFrom(mNodeId);
        mSocket.emit(event, message.getJson(null));
        return true;
    }

    private Runnable runSocketServerTask = () -> {
        try{
            mSocket = IO.socket(serverUrl);
            mSocket.on(Socket.EVENT_CONNECT, args -> {
                Log.d(TAG_SOCKET, "Connected!");
                onConnect();
            });

            mSocket.on(AUDIO_DATA_EVENT, args -> {
                AudioDataMessage message;
                try {
                    message = new AudioDataMessage((JSONObject) args[0]);
                }
                catch (JSONException e) {
                    handleRecieveException(e);
                    return;
                }
                noticeMessageReceived(message);
                forEachListener(l -> l.receiveAudioData(message));
            });
            mSocket.on(CHIRP_MESSAGE_DATA_EVENT, args -> {
                ChirpSocketMessage message;
                try {
                    message = new ChirpSocketMessage((JSONObject) args[0]);
                }
                catch (JSONException e) {
                    handleRecieveException(e);
                    return;
                }
                noticeMessageReceived(message);
                forEachListener(l -> l.receiveChirpMessage(message));
            });
            mSocket.on(BYTE_DATA_EVENT, args -> {
                ByteMessage message;
                try {
                    message = new ByteMessage((JSONObject) args[0]);
                }
                catch (JSONException e) {
                    handleRecieveException(e);
                    return;
                }
                noticeMessageReceived(message);
                forEachListener(l -> l.receiveByteData(message));
            });

            mSocket.on(SET_CLIENT_ID_EVENT, args -> {
                mNodeId = (byte) getIntArg(args);
                onGotClientId(mNodeId);
            });

            mSocket.on(NEW_PEER_EVENT, args -> {
                byte newPeerId = (byte) getIntArg(args);
                if (newPeerId == mNodeId) return;
                Log.d(TAG_SOCKET, "New peer ID:" + newPeerId);
                connectedPeers.add(newPeerId);
                forEachListener(l -> l.peerConnected(newPeerId));
                checkReady();
            });

            mSocket.on(PEER_DISCONNECTED_EVENT, args -> {
                byte disconnectedPeerId = (byte) getIntArg(args);
                Log.d(TAG_SOCKET, "Peer disconnected:" + disconnectedPeerId);
                connectedPeers.remove(disconnectedPeerId);
                forEachListener(l -> l.peerDisconnected(disconnectedPeerId));
            });

            mSocket.on(LIST_PEERS_EVENT, args -> {
                try {
                    JSONArray peerIdArray = ((JSONArray) args[0]);
                    connectedPeers.clear();
                    for (int i = 0; i < peerIdArray.length(); i++) {
                        byte peerId = (byte) peerIdArray.getInt(i);
                        if (peerId == mNodeId) continue;
                        connectedPeers.add(peerId);
                    }
                    forEachListener(l -> l.receivePeerList(connectedPeers));
                    checkReady();
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            });
            mSocket.on(CLIENT_ERROR_EVENT, args -> {
                String message;
                if (args.length >= 1 && args[0] instanceof String) {
                    message = (String) args[0];
                }
                else {
                    message = "Unknown client error!!";
                }
                forEachListener(l -> l.clientError(message));
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
        if(ready) return true;
        if(mNodeId < 0) return false;
        connectedPeers.remove(mNodeId);
        if(connectedPeers.size() == 0) return false;
        ready = true;
        onReady();
        return true;
    }

    private void onConnect() {
        Log.i(TAG_SOCKET, "Asking for an ID!");
        mSocket.emit(ASSIGN_ME_AN_ID_EVENT, 0);
        mSocket.emit(LIST_PEERS_EVENT, 0);
        forEachListener(SocketServiceListener::connected);
        checkReady();
    }

    private void onReady() {
        Log.i(TAG_SOCKET, "Ready now!");
        forEachListener(SocketServiceListener::ready);
    }

    private void onDisconnect() {
        connectedPeers.clear();
        mNodeId = -1;
        ready = false;
        Log.i(TAG_SOCKET, "Disconnected");
        forEachListener(SocketServiceListener::disconnected);
    }

    private void onGotClientId(final byte id) {
        mNodeId = id;
        Log.d(TAG_SOCKET, "I was given a socket id of " + mNodeId);
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

    private void handleRecieveException(Throwable e) {
        Log.w(TAG_SOCKET, "Unhandled exception receiving a message!", e);
    }

    private void noticeMessageReceived(AddressableMessage message) {
        long length;
        if (message instanceof ByteMessage) {
            length = ((ByteMessage)message).getBytes().length;
        }
        else if (message instanceof AudioDataMessage) {
            length = ((AudioDataMessage)message).getData().length;
        }
        else if (message instanceof ChirpSocketMessage) {
            // Inefficient, but messages don't happen too often.
            length = ((ChirpSocketMessage)message).getMessage().toBytes().length;
        }
        else {
            length = 0;
        }
        recievedDataStats.putOrAdd(message.getFrom(), length, length);
    }

    private void forEachListener(EachListenerAction action) {
        for(SocketServiceListener listener : mListeners) {
            try {
                action.eachListener(listener);
            }
            catch (Exception e) {
                handleListenerException(e);
            }
        }
    }

    private interface EachListenerAction {
        void eachListener(SocketServiceListener listener);
    }
}