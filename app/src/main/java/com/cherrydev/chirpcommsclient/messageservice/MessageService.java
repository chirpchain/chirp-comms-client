package com.cherrydev.chirpcommsclient.messageservice;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.cherrydev.chirpcommsclient.messages.ChirpMessage;
import com.cherrydev.chirpcommsclient.messages.MessageType;
import com.cherrydev.chirpcommsclient.socketmessages.ByteMessage;
import com.cherrydev.chirpcommsclient.socketservice.BaseSocketServiceListener;
import com.cherrydev.chirpcommsclient.socketservice.SocketService;
import com.cherrydev.chirpcommsclient.socketservice.SocketServiceListener;
import com.cherrydev.chirpcommsclient.util.BaseService;
import com.cherrydev.chirpcommsclient.util.ServiceBinding;

/**
 * This is a transport-agnostic messaging layer.  It's responsible for translating binary
 * messages to/from their object representation.  It primarily will be the intermediary between
 * the binary layer and the routing layer.
 */
public class MessageService extends BaseService<MessageServiceListener> {
    private static final String TAG_SERVICE = "MessageService";

    private SocketService socketService;
    private ServiceBinding<SocketServiceListener, SocketService> socketServiceBinding;

    public MessageService() {
    }

    @Override
    protected void onStartup() {
        socketServiceBinding = new ServiceBinding<SocketServiceListener, SocketService>(this, SocketService.class) {
            @Override
            protected SocketServiceListener createListener() {
                return new BaseSocketServiceListener() {
                    @Override
                    public void receiveByteData(ByteMessage message) {
                        onReceiveMessage(message.getFrom(), message.getBytes());
                    }
                };
            }
        };
        socketServiceBinding.setOnConnect(s -> socketService = s);
        socketServiceBinding.setOnDisconnect(() -> socketService = null);
        socketServiceBinding.connect();
    }

    @Override
    public void onDestroy() {
        socketServiceBinding.disconnect();
        super.onDestroy();
    }

    public boolean sendMessage(byte to, byte[] message) {
        // For now, pass it to the socketservice
        if (socketService == null) return false;
        return socketService.sendByteData(new ByteMessage(socketService.getNodeId(), to, message));
    }

    private void onReceiveMessage(byte from, byte[] message) {
        if (message.length == 0) {
            Log.w(TAG_SERVICE, "Got a zero-byte message from " + from + ", discarding.");
            return;
        }
        MessageType type = MessageType.ofTypeValue(message[0]);
        if (type == null) {
            Log.w(TAG_SERVICE, "Got a message of unknown type.  Type byte was " + message[0]);
            return;
        }
        switch (type) {
            case ChirpMessage:
                onReceiveChirpMessage(from, message);
                break;
            default:
                Log.w(TAG_SERVICE, "Got a message of known type " + type + " but don't know what to do with it!");
                return;
        }
    }

    private void onReceiveChirpMessage(byte from, byte[] message) {
        try {
            ChirpMessage m = new ChirpMessage(message);
            Log.d(TAG_SERVICE, "Received chirp message from " + from + ":\n" + m.toString());
            forEachListener(l -> l.receiveChirpMessage(m));
        }
        catch (Exception e) {
            handleReceiveException(e, from, message);
        }
    }

    private void handleReceiveException(Throwable e, byte from, byte[] message) {
        Log.w(TAG_SERVICE, "Some sort of error occurred while parsing a message.  It may have been malformed.", e);
    }

    @Override
    protected void handleListenerException(Throwable e) {

    }
}
