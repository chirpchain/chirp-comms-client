package com.cherrydev.chirpcommsclient.routeservice;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.cherrydev.chirpcommsclient.messageservice.BaseMessasgeServiceListener;
import com.cherrydev.chirpcommsclient.messageservice.MessageService;
import com.cherrydev.chirpcommsclient.messageservice.MessageServiceListener;
import com.cherrydev.chirpcommsclient.socketservice.BaseSocketServiceListener;
import com.cherrydev.chirpcommsclient.socketservice.SocketService;
import com.cherrydev.chirpcommsclient.socketservice.SocketServiceListener;
import com.cherrydev.chirpcommsclient.util.BaseService;
import com.cherrydev.chirpcommsclient.util.ServiceBinding;

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

    public RouteService() {
    }

    @Override
    protected void onStartup() {
        socketServiceBinding = new ServiceBinding<SocketServiceListener, SocketService>(this, SocketService.class) {
            @Override
            protected SocketServiceListener createListener() {
                return new BaseSocketServiceListener(){

                };
            }
        };
        socketServiceBinding.setOnConnect(s -> socketService = s);
        socketServiceBinding.setOnDisconnect(() -> socketService = null);
        socketServiceBinding.connect();

        messageServiceBinding = new ServiceBinding<MessageServiceListener, MessageService>(this, MessageService.class) {
            @Override
            protected MessageServiceListener createListener() {
                return new BaseMessasgeServiceListener() {

                };
            }
        };
        messageServiceBinding.setOnConnect(s -> messageService = s);
        messageServiceBinding.setOnDisconnect(() -> messageService = null);
        messageServiceBinding.connect();
    }

    @Override
    public void onDestroy() {
        if (socketServiceBinding != null) socketServiceBinding.disconnect();
        if (messageServiceBinding != null) messageServiceBinding.disconnect();
        super.onDestroy();
    }

    @Override
    protected void handleListenerException(Throwable e) {

    }

}
