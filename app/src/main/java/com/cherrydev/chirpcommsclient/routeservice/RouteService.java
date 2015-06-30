package com.cherrydev.chirpcommsclient.routeservice;

import android.util.Log;

import com.cherrydev.chirpcommsclient.messages.ChirpBinaryMessage;
import com.cherrydev.chirpcommsclient.messages.ChirpMessage;
import com.cherrydev.chirpcommsclient.messageservice.BaseMessageServiceListener;
import com.cherrydev.chirpcommsclient.messageservice.MessageService;
import com.cherrydev.chirpcommsclient.messageservice.MessageServiceListener;
import com.cherrydev.chirpcommsclient.socketmessages.ChirpSocketMessage;
import com.cherrydev.chirpcommsclient.socketservice.BaseSocketServiceListener;
import com.cherrydev.chirpcommsclient.socketservice.SocketService;
import com.cherrydev.chirpcommsclient.socketservice.SocketServiceListener;
import com.cherrydev.chirpcommsclient.util.BaseService;
import com.cherrydev.chirpcommsclient.util.ServiceBinding;

import java.util.Collections;
import java.util.Set;

/**
 * This service is intended to route messages between nodes.  It is responsible for knowing
 * the network map and passing a message along to the next node in the route.  It should use
 * the telemetry provided by the SocketService to possibly bypass nodes that it knows are
 * unresponsive.  This is conceptually the top layer of the networking stack and all messages
 * 'proper' (not including telemetry, debugging, etc) should arrive and depart here from/to the
 * user interface.
 */
public class RouteService extends BaseService<RouteServiceListener> {

    private ServiceBinding<SocketServiceListener, SocketService> socketServiceBinding;
    private SocketService socketService;
    private ServiceBinding<MessageServiceListener, MessageService> messageServiceBinding;
    private MessageService messageService;

    private static final String TAG_SERVICE = "RouteService";

    public RouteService() {
    }

    @Override
    protected void onStartup() {
        socketServiceBinding = new ServiceBinding<SocketServiceListener, SocketService>(this, SocketService.class) {
            @Override
            protected SocketServiceListener createListener() {
                return new BaseSocketServiceListener(){
                    @Override
                    public void receiveChirpMessage(ChirpSocketMessage message) {
                        handleSocketChirpReceived(message);
                    }
                };
            }
        }
                .setOnConnect(s -> socketService = s)
                .setOnDisconnect(() -> socketService = null)
                .connect();

        messageServiceBinding = new ServiceBinding<MessageServiceListener, MessageService>(this, MessageService.class) {
            @Override
            protected MessageServiceListener createListener() {
                return new BaseMessageServiceListener() {
                    @Override
                    public void receiveChirpMessage(ChirpBinaryMessage m) {
                        handleBinaryChirpReceived(m);
                    }
                };
            }
        }
                .setOnConnect(s -> messageService = s)
                .setOnDisconnect(() -> messageService = null)
                .connect();
    }

    public Set<Byte> getConnectedNodes() {
        if (socketService == null) //noinspection unchecked
            return Collections.EMPTY_SET;
        return socketService.getConnectedPeers();
    }

    public boolean sendChirpMessage(ChirpMessage m) {
        if (messageService == null) return false;
        // Send direct only, for now, no routing.
        if (! getConnectedNodes().contains(m.getTo())) return false;
        return messageService.sendMessage(m.getTo(), new ChirpBinaryMessage(m).toBytes());
    }

    @Override
    public void onDestroy() {
        if (socketServiceBinding != null) socketServiceBinding.disconnect();
        if (messageServiceBinding != null) messageServiceBinding.disconnect();
        super.onDestroy();
    }

    private void handleSocketChirpReceived(ChirpSocketMessage message) {
        handleChirpReceived(message.getMessage());
    }

    private void handleBinaryChirpReceived(ChirpBinaryMessage message) {
        handleChirpReceived(message.getMessage());
    }

    private void handleChirpReceived(ChirpMessage message) {
        Log.d(TAG_SERVICE, "Route service received a chirp message from " + message.getFrom() + " to " + message.getTo());
        forEachListener(l -> l.chirpReceived(message));
    }

    @Override
    protected void handleListenerException(Throwable e) {
        Log.e(TAG_SERVICE, "Exception calling listener", e);
    }

}
