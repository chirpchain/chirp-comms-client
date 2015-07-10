package com.cherrydev.chirpcommsclient.routeservice;

import android.os.Handler;
import android.util.Log;

import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.cherrydev.chirpcommsclient.acousticservice.AcousticService;
import com.cherrydev.chirpcommsclient.acousticservice.AcousticServiceListener;
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
import com.cherrydev.chirpcommsclient.util.ChirpNode;
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
    private ServiceBinding<AcousticServiceListener, AcousticService> acousticServiceBinding;
    private AcousticService acousticService;
    private IntObjectMap<ChirpNode> nodes = new IntObjectHashMap<>();
    private ChirpNode nodeInfo;
    private Handler handler = new Handler();
    private boolean useDummyRouting = true;

    private static final int MAX_ROUTE_DISTANCE = 10;

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

                    @Override
                    public void setNodeInfo(ChirpNode nodeInfo) {
                        RouteService.this.nodeInfo = nodeInfo;
                        handler.post(() -> forEachListener(RouteServiceListener::configChanged));
                        Log.i(TAG_SERVICE, "Got my node info!");
                    }

                    @Override
                    public void receivePeerNodeInfos(ChirpNode[] newNodes) {
                        nodes.clear();
                        for (ChirpNode node : newNodes) {
                            nodes.put(node.getId(), node);
                        }
                        updateRouting();
                    }

                    @Override
                    public void peerConnected(byte newPeerId) {
                        handler.post(() -> forEachListener(RouteServiceListener::configChanged));
                    }

                    @Override
                    public void peerDisconnected(byte peerId) {
                        handler.post(() -> forEachListener(RouteServiceListener::configChanged));
                    }

                    @Override
                    public void receivePeerList(Set<Byte> peerIds) {
                        handler.post(() -> forEachListener(RouteServiceListener::configChanged));
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
        acousticServiceBinding = new ServiceBinding<AcousticServiceListener, AcousticService>(this, AcousticService.class) {
            @Override
            protected AcousticServiceListener createListener() {
                return null;
            }
        }.setOnConnect(s -> acousticService = s)
        .setOnDisconnect(() -> acousticService = null)
        .connect();
    }

    public ChirpNode getNodeInfo() {
        return nodeInfo;
    }

    public Set<Byte> getConnectedNodes() {
        if (socketService == null) //noinspection unchecked
            return Collections.EMPTY_SET;
        return socketService.getConnectedPeers();
    }

    public IntObjectMap<ChirpNode> getAllNodes() {
        return nodes;
    }

    /**
     * Submit a message originating from this node
     * @param m
     * @return
     */
    public boolean submitNewMessage(ChirpMessage m) {
        m.setFrom(getNodeInfo().getId());
        return sendChirpMessage(m);
    }

    public boolean sendChirpMessage(ChirpMessage m) {
        Log.i(TAG_SERVICE, "Got a message originating from " + m.getFrom() + " to " + (m.getTo() == nodeInfo.getId() ? "me" : m.getTo()));
        ChirpNode destNode = nodes.get(m.getTo());
        if (destNode == null) return false; // We don't know about this node
        if (! getConnectedNodes().contains(m.getTo())) return false; // This node isn't online
        ChirpNode nextNode = findRouteToNode(destNode);
        if (nextNode == null) {
            return false;
        }
        ChirpSocketMessage sm = new ChirpSocketMessage(nodeInfo.getId(), destNode.getId(), m);
        if (destNode.isAcoustic() && nodeInfo.isAcoustic()) {
            Log.i(TAG_SERVICE, "Delivering it acoustically because I have an acoustic path to it");
            byte[] messageBytes = new ChirpBinaryMessage(m).toBytes();
            Log.i(TAG_SERVICE, "Submitting " + messageBytes.length + " bytes to acoustic service");
            boolean sent = acousticService.sendMessage(destNode.getId(), messageBytes);
            if (!sent) return false;
            int fakeHackyDelay = messageBytes.length * 1900 + 3000; // 5s per byte + 3s for framing?  Sure.
            handler.postDelayed(() -> {
                Log.i(TAG_SERVICE, "Actually delivering message now!");
                socketService.sendChirpData(sm);
            },fakeHackyDelay);
            return true;
        }
        else {
            // Send it immediately
            Log.i(TAG_SERVICE, "Sending it " + (sm.getMessage().getTo() == sm.getTo() ? "directly" : "indirectly") + " via socket towards" + sm.getTo());
            return socketService.sendChirpData(sm);
        }
    }

    private ChirpNode findRouteToNode(ChirpNode n) {
        // If we're not both acoustic, go direct.
        if (! n.isAcoustic()) return n;
        if (! nodeInfo.isAcoustic()) return n;
        int forwardLength = 0;
        int backLength = 0;
        ChirpNode target = nodeInfo.getForwardAcousticNode();
        while (target != null && target != nodeInfo && forwardLength <= MAX_ROUTE_DISTANCE) {
            forwardLength++;
            if (n == target) break;
        }
        if (target != n) forwardLength = MAX_ROUTE_DISTANCE + 1; // not found forward
        target = nodeInfo.getBackwardAcousticNode();
        while (target != null && target != nodeInfo & backLength <= MAX_ROUTE_DISTANCE) {
            backLength++;
            if (n == target) break;
        }
        if (target != n) backLength = MAX_ROUTE_DISTANCE + 1;
        if (forwardLength > MAX_ROUTE_DISTANCE && backLength > MAX_ROUTE_DISTANCE) return n; // Go direct, no path
        if (backLength < forwardLength) return nodeInfo.getBackwardAcousticNode();
        return nodeInfo.getForwardAcousticNode();
    }

    private void updateRouting() {
        for(IntObjectCursor<ChirpNode> nodeC : nodes) {
            ChirpNode node = nodeC.value;
            if (node.getId() == nodeInfo.getId() && node != nodeInfo) {
                nodeInfo = node;
            }
            if (! node.isAcoustic()) {
                node.setBackwardAcousticNode(null);
                node.setForwardAcousticNode(null);
            }
            else {
                byte back = node.getBackwardAcousticNodeId();
                byte forward = node.getForwardAcousticNodeId();
                if (forward >=0) {
                    if (nodes.containsKey(forward)) {
                        node.setForwardAcousticNode(nodes.get(forward));
                    }
                    else {
                        node.setForwardAcousticNode(null);
                    }
                }
                if (back >=0) {
                    if (nodes.containsKey(back)) {
                        node.setBackwardAcousticNode(nodes.get(back));
                    }
                    else {
                        node.setBackwardAcousticNode(null);
                    }
                }
            }
        }
        if (useDummyRouting) {
            // Pretend we have an acoustic connection to every node!
            for(IntObjectCursor<ChirpNode> nodeC : nodes) {
                acousticService.setupDummyAcoustic(nodeC.value.getId());
            }
        }
        else if (nodeInfo.isAcoustic()) {
            if (nodeInfo.getForwardAcousticNode() != null) {
                acousticService.setupSpeaker(nodeInfo.getForwardAcousticNodeId(), false, true);
            }
            if (nodeInfo.getBackwardAcousticNode() != null) {
                acousticService.setupSpeaker(nodeInfo.getBackwardAcousticNodeId(), true, true);
            }
        }
    }

    @Override
    public void onDestroy() {
        if (socketServiceBinding != null) socketServiceBinding.disconnect();
        if (messageServiceBinding != null) messageServiceBinding.disconnect();
        if (acousticServiceBinding != null) acousticServiceBinding.disconnect();
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
        if (message.getTo() != nodeInfo.getId()) {
            sendChirpMessage(message);
        }
    }

    @Override
    protected void handleListenerException(Throwable e) {
        Log.e(TAG_SERVICE, "Exception calling listener", e);
    }

}
