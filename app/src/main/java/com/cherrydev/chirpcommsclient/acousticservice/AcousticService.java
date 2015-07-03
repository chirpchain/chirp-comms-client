package com.cherrydev.chirpcommsclient.acousticservice;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.cherrydev.chirpcommsclient.chirpmodem.NetworkAudioReceiver;
import com.cherrydev.chirpcommsclient.chirpmodem.NetworkAudioTransmitter;
import com.cherrydev.chirpcommsclient.socketmessages.AudioDataMessage;
import com.cherrydev.chirpcommsclient.socketmessages.ByteMessage;
import com.cherrydev.chirpcommsclient.socketservice.BaseSocketServiceListener;
import com.cherrydev.chirpcommsclient.socketservice.SocketService;
import com.cherrydev.chirpcommsclient.socketservice.SocketServiceListener;
import com.cherrydev.chirpcommsclient.util.BaseService;
import com.cherrydev.chirpcommsclient.util.ServiceBinding;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import ca.vectorharmony.chirpmodem.PacketCodec;

public class AcousticService extends BaseService<AcousticServiceListener> {
    private static final String TAG_SERVICE = "AcousticService";

    private HandlerThread acousticPumpThread;
    private Handler acousticPumpHandler;
    private Timer acousticPumpTimer;
    private SocketService socketService;
    private ServiceBinding<SocketServiceListener, SocketService> socketServiceBinding;
    private IntObjectHashMap<AcousticSocketPackage> socketPackages = new IntObjectHashMap<>();

    private class AcousticSocketPackage {
        PacketCodec codec;
        NetworkAudioReceiver receiver;
        NetworkAudioTransmitter transmitter;
        byte peerId;

        AcousticSocketPackage(PacketCodec codec, NetworkAudioReceiver receiver, NetworkAudioTransmitter transmitter, byte peerId) {
            this.codec = codec;
            this.receiver = receiver;
            this.transmitter = transmitter;
            this.peerId = peerId;
        }
    }

    public AcousticService() {
    }

    public boolean sendMessage(byte to, byte[] message) {
        // Live dangerously, this isn't strictly thread safe
        if (! socketPackages.containsKey(to)) return false;
        acousticPumpHandler.post(() -> {
            AcousticSocketPackage p = socketPackages.get(to);
            if (p != null) {
                Log.d(TAG_SERVICE, "Sending a message to the codec to " + to);
                p.codec.sendPacket(message);
            }
        });
        return true;
    }


    @Override
    protected void onStartup() {
        startAcousticPump();
        socketServiceBinding = new ServiceBinding<SocketServiceListener, SocketService>(this, SocketService.class) {
            @Override
            protected SocketServiceListener createListener() {
                return new BaseSocketServiceListener() {
                    @Override
                    public void receiveAudioData(AudioDataMessage message) {
                        onReceiveSocketAudio(message);
                    }

                    @Override
                    public void peerConnected(byte newPeerId) {
                        onSocketPeerConnect(newPeerId);
                    }

                    @Override
                    public void peerDisconnected(byte peerId) {
                        onSocketPeerDisconnect(peerId);
                    }

                    @Override
                    public void receivePeerList(Set<Byte> peerIds) {
                        onSocketPeerList(peerIds);
                    }
                };
            }
        }
        .setOnConnect(s -> socketService = s)
        .setOnDisconnect(() -> socketService = null)
        .connect();
    }

    private void onSocketPeerConnect(final byte peerId) {
        acousticPumpHandler.post(() -> {
            if (socketPackages.containsKey((int) peerId)) return;
            doPeerConnect(peerId);
        });
    }

    private void onSocketPeerDisconnect(final byte peerId) {
        acousticPumpHandler.post(() -> {
            doPeerDisconnect(peerId);
        });
    }

    private void onSocketPeerList(Set<Byte> peers) {
        acousticPumpHandler.post(() -> {
            for(IntObjectCursor i : socketPackages) {
                if (! peers.contains((byte) i.key)) {
                    doPeerDisconnect((byte) i.key);
                }
            }
            for(Byte peer : peers) {
                if (! socketPackages.containsKey(peer)) {
                    doPeerConnect(peer);
                }
            }
        });
    }

    /**
     * Call only from handler!
     */
    private void doPeerConnect(final byte peerId) {
        Log.d(TAG_SERVICE, "Connecting peer " + peerId);
        NetworkAudioTransmitter t = new NetworkAudioTransmitter();
        t.initOnThisThread(48000, (samples) -> {
            ByteBuffer bb = ByteBuffer.allocate(samples.length * 2);
            bb.asShortBuffer().put(samples);
            bb.flip();
            byte[] bytes = new byte[bb.limit()];
            bb.get(bytes);
            ByteMessage m = new ByteMessage(socketService.getNodeId(), peerId, bytes);
            socketService.sendByteData(m);
        });
        NetworkAudioReceiver r = new NetworkAudioReceiver(48000);
        PacketCodec codec = new PacketCodec(r, t);
        AcousticSocketPackage p = new AcousticSocketPackage(codec, r, t, peerId);
        socketPackages.put(peerId, p);
    }

    /**
     * Call only from handler!
     */
    private void doPeerDisconnect(final byte peerId) {
        Log.d(TAG_SERVICE, "Disconnecting peer " + peerId);
        acousticPumpTimer.cancel();
        AcousticSocketPackage p = socketPackages.get(peerId);
        if (p != null) {
            p.transmitter.stop();
            socketPackages.remove(peerId);
        }
    }

    private void onReceiveSocketAudio(AudioDataMessage message) {
        Log.d(TAG_SERVICE, "Got some acoustic data from " + message.getFrom());
        AcousticSocketPackage p = socketPackages.get(message.getFrom());
        if (p != null) {
            acousticPumpHandler.post(() -> p.receiver.receiveAudioData(message.getData(), message.getSampleRate()));
        }
    }



    @Override
    protected void handleListenerException(Throwable e) {
        Log.w(TAG_SERVICE, "Listener exception", e);
    }

    @Override
    public void onDestroy() {
        socketServiceBinding.disconnect();
        if (acousticPumpThread != null) acousticPumpThread.quitSafely();
        super.onDestroy();
    }

    private void startAcousticPump() {
        acousticPumpThread = new HandlerThread("AcousticPumpThread");
        acousticPumpThread.start();
        acousticPumpHandler = new Handler(acousticPumpThread.getLooper());
        acousticPumpTimer = new Timer();
        acousticPumpTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                acousticPumpHandler.post(() -> {
                    for(IntObjectCursor<AcousticSocketPackage> p : socketPackages) {
                        PacketCodec c = p.value.codec;
                        c.update();
                        if (c.hasNextValidReceivedPacket()) {
                            byte[] packet = c.nextValidReceivedPacket();
                            forEachListener(l -> l.receiveAcousticMessage(p.value.peerId, socketService.getNodeId(), packet));
                        }
                    }
                });
            }
        }, 0, 100);
    }
}
