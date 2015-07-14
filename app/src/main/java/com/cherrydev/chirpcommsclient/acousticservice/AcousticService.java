package com.cherrydev.chirpcommsclient.acousticservice;

import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.appfour.android.samplingprofiler.SamplingProfilerFacade;
import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.cherrydev.chirpcommsclient.chirpmodem.DummyAudioReceiver;
import com.cherrydev.chirpcommsclient.chirpmodem.NetworkAudioReceiver;
import com.cherrydev.chirpcommsclient.chirpmodem.NetworkAudioTransmitter;
import com.cherrydev.chirpcommsclient.chirpmodem.SpeakerAudioTransmitter;
import com.cherrydev.chirpcommsclient.chirpmodem.UsbAudioReceiver;
import com.cherrydev.chirpcommsclient.socketmessages.AudioDataMessage;
import com.cherrydev.chirpcommsclient.socketservice.BaseSocketServiceListener;
import com.cherrydev.chirpcommsclient.socketservice.SocketService;
import com.cherrydev.chirpcommsclient.socketservice.SocketServiceListener;
import com.cherrydev.chirpcommsclient.util.BaseService;
import com.cherrydev.chirpcommsclient.util.ServiceBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import ca.vectorharmony.chirpmodem.AudioReceiver;
import ca.vectorharmony.chirpmodem.AudioTransmitter;
import ca.vectorharmony.chirpmodem.PacketCodec;
import ca.vectorharmony.chirpmodem.util.SymbolSentListener;

public class AcousticService extends BaseService<AcousticServiceListener> implements SymbolSentListener {
    private static final String TAG_SERVICE = "AcousticService";
    private static final int SAMPLE_RATE = 22050;
    private HandlerThread acousticPumpThread;
    private Handler acousticPumpHandler;
    private Timer acousticPumpTimer;
    private SocketService socketService;
    private ServiceBinding<SocketServiceListener, SocketService> socketServiceBinding;
    private IntObjectHashMap<AcousticPackage> acousticPackages = new IntObjectHashMap<>();
    private volatile boolean pumpWorking = false;
    private volatile boolean timerWorking = false;
    private AcousticPackage leftSpeakerPackage;
    private AcousticPackage rightSpeakerPackage;
    private AcousticPackage dummyAcousticPackage; // This will be shared, just ignore the peerId



    private class AcousticPackage {
        AcousticPackage(PacketCodec codec, AudioReceiver receiver, AudioTransmitter transmitter, byte peerId) {
            this.codec = codec;
            this.receiver = receiver;
            this.transmitter = transmitter;
            this.peerId = peerId;
        }
        public void stop() {
            receiver.stop();
            transmitter.stop();
        }
        PacketCodec codec;
        AudioReceiver receiver;
        AudioTransmitter transmitter;
        byte peerId;
    }

    private class AcousticSocketPackage extends AcousticPackage {
        NetworkAudioReceiver receiver;
        NetworkAudioTransmitter transmitter;

        AcousticSocketPackage(PacketCodec codec, NetworkAudioReceiver receiver, NetworkAudioTransmitter transmitter, byte peerId) {
            super(codec, receiver, transmitter, peerId);
            this.receiver = receiver;
            this.transmitter = transmitter;
        }
    }

    public AcousticService() {
    }

    @Override
    public void symbolSent(int symbol) {
        forEachListener(l -> l.symbolSent(symbol));
    }


    public boolean sendMessage(byte to, byte[] message) {
        // Live dangerously, this isn't strictly thread safe
        if (! acousticPackages.containsKey(to)) return false;
        acousticPumpHandler.post(() -> {
            AcousticPackage p = acousticPackages.get(to);
            if (p != null) {
                Log.d(TAG_SERVICE, "Sending a message to the codec to " + to);
                p.codec.sendPacket(message);
            }
        });
        return true;
    }

    /**
     * Set up so that all audio goes over a single transmitter and no receiver
     * @param nodeId
     */
    public void setupDummyAcoustic(byte nodeId) {
        acousticPumpHandler.post(() -> {
            if (dummyAcousticPackage == null) {
                SpeakerAudioTransmitter t = new SpeakerAudioTransmitter();
                t.initOnThisThread(true, SAMPLE_RATE);
                AudioReceiver r = new DummyAudioReceiver();
                PacketCodec c = new PacketCodec(r, t);
                c.setSymbolSentListener(this);
                dummyAcousticPackage = new AcousticPackage(c, r, t, (byte) 0);
            }
            acousticPackages.put(nodeId, dummyAcousticPackage);
        });
    }

    public void setupSpeaker(byte nodeId, boolean rightChannel, boolean dummyReceiver) {
        acousticPumpHandler.post(() -> {
            if (rightChannel) {
                if (rightSpeakerPackage != null) {
                    if (rightSpeakerPackage.peerId == nodeId) return; // already set up
                    rightSpeakerPackage.stop();
                    acousticPackages.remove(nodeId);
                }
                rightSpeakerPackage = makeSpeakerAcousticPackage(nodeId, true, dummyReceiver);
                if (rightSpeakerPackage != null) {
                    acousticPackages.put(nodeId, rightSpeakerPackage);
                }
            }
            else {
                if (leftSpeakerPackage != null) {
                    if (leftSpeakerPackage.peerId == nodeId) return; // already set up
                    leftSpeakerPackage.stop();
                    acousticPackages.remove(nodeId);
                }
                leftSpeakerPackage = makeSpeakerAcousticPackage(nodeId, false, dummyReceiver);
                if (leftSpeakerPackage != null) {
                    acousticPackages.put(nodeId, leftSpeakerPackage);
                }
            }
        });
    }

    private AcousticPackage makeSpeakerAcousticPackage(byte nodeId, boolean rightChannel, boolean dummyReceiver) {
        SpeakerAudioTransmitter t = new SpeakerAudioTransmitter();
        t.initOnThisThread(rightChannel, SAMPLE_RATE);
        AudioReceiver r;
        if (! dummyReceiver) {
            UsbAudioReceiver.initUsbAudioDevices();
            UsbAudioReceiver usbReceiver = new UsbAudioReceiver();
            int samplesPerRead = SAMPLE_RATE;
            if (!usbReceiver.initOnThisThread(rightChannel, samplesPerRead)) {
                Log.w(TAG_SERVICE, "Couldn't init USB audio.");
                return null;
            }
            r = usbReceiver;
        }
        else {
            r = new DummyAudioReceiver();
        }
        PacketCodec c = new PacketCodec(r, t);
        return new AcousticPackage(c, r, t, nodeId);
    }


    @Override
    protected void onStartup() {
        startAcousticPump();
        socketServiceBinding = new ServiceBinding<SocketServiceListener, SocketService>(this, SocketService.class) {
            @Override
            protected SocketServiceListener createListener() {
                return new BaseSocketServiceListener() {
                    // This was for testing only...
                };
            }
        }
        .setOnConnect(s -> socketService = s)
        .setOnDisconnect(() -> socketService = null)
        .connect();
    }

    private void onSocketPeerConnect(final byte peerId) {
        /*
        acousticPumpHandler.post(() -> {
            if (socketPackages.containsKey((int) peerId)) return;
            doPeerConnect(peerId);
        });
        */
    }

    private void onSocketPeerDisconnect(final byte peerId) {
        /*
        acousticPumpHandler.post(() -> {
            doPeerDisconnect(peerId);
        });
        */
    }

    private void onSocketPeerList(Set<Byte> peers) {
        /*
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
        */
    }

    /**
     * Call only from handler!
     */
    private void doPeerConnect(final byte peerId) {
        Log.d(TAG_SERVICE, "Connecting peer " + peerId);

    }

    private void configureSpeakerAcoustic(final byte peerId) {
        SpeakerAudioTransmitter t = new SpeakerAudioTransmitter();
        t.initOnThisThread(true, SAMPLE_RATE);
        PacketCodec codec = new PacketCodec(new NetworkAudioReceiver(SAMPLE_RATE), t);

    }

    private void configureNetworkAcoustic(final byte peerId) {
        NetworkAudioTransmitter t = new NetworkAudioTransmitter();
        t.initOnThisThread(SAMPLE_RATE, (samples) -> {
            ByteBuffer bb = ByteBuffer.allocateDirect(samples.length * 2);
            bb.asShortBuffer().put(samples);
            byte[] bytes = Arrays.copyOf(bb.array(), samples.length * 2);
            AudioDataMessage m = new AudioDataMessage(socketService.getNodeId(), peerId, bytes, SAMPLE_RATE);
            //Log.d(TAG_SERVICE, "Sending " + bytes.length + " bytes");
            socketService.sendAudioData(m);
        }, false);
        NetworkAudioReceiver r = new NetworkAudioReceiver(SAMPLE_RATE);
        PacketCodec codec = new PacketCodec(r, t);
        AcousticSocketPackage p = new AcousticSocketPackage(codec, r, t, peerId);
        Log.d(TAG_SERVICE, "Adding new AcousticSocketPackage for peer " + peerId);
        acousticPackages.put(peerId, p);
    }

    /**
     * Call only from handler!
     */
    private void doPeerDisconnect(final byte peerId) {
        Log.d(TAG_SERVICE, "Disconnecting peer " + peerId);
        AcousticPackage p = acousticPackages.get(peerId);
        if (p != null) {
            Log.d(TAG_SERVICE, "Removing new AcousticSocketPackage for peer " + peerId);
            p.transmitter.stop();
            acousticPackages.remove(peerId);
        }
    }

    private static final boolean DO_TRACING = false;
    private long didReceive = -1;
    private void onReceiveSocketAudio(AudioDataMessage message) {
        AcousticPackage basePackage = acousticPackages.get(message.getFrom());
        if (!(basePackage instanceof AcousticSocketPackage)) {
            Log.w(TAG_SERVICE, "Got socket audio for a non-socket acoustic package!");
            return;
        }
        AcousticSocketPackage p = (AcousticSocketPackage) basePackage;
        if (p != null) {
            acousticPumpHandler.post(() -> {
                if (DO_TRACING) {
                    if (didReceive == -1) {
                        Log.w(TAG_SERVICE, "Starting tracing");
                        didReceive = System.currentTimeMillis();
                        SamplingProfilerFacade.init(10, 10, Thread.currentThread());
                        SamplingProfilerFacade.startSampling();
                    }
                    else if (System.currentTimeMillis() - didReceive > 5000 && didReceive != -2) {
                        Log.w(TAG_SERVICE, "Ending tracing");
                        File outFile = new File(Environment.getExternalStorageDirectory(), "sampling.hprof");
                        try {
                            FileOutputStream outStream = new FileOutputStream(outFile);
                            try {
                                SamplingProfilerFacade.writeHprofDataAndShutdown(outStream);
                            } finally {
                                outStream.close();
                            }
                        } catch (IOException e) {
                            Log.e("Sampling", "I/O exception writing sampling profiler data", e);
                        }
                        didReceive = -2;
                    }
                }
                p.receiver.receiveAudioData(message.getData(), message.getSampleRate());
            });
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
        acousticPumpTimer.cancel();
        super.onDestroy();
    }

    private int listenerCount;
    private Thread acousticPumpTimerThread;

    private void startAcousticPump() {
        acousticPumpThread = new HandlerThread("AcousticPumpThread");
        acousticPumpThread.start();
        acousticPumpHandler = new Handler(acousticPumpThread.getLooper());
        acousticPumpTimer = new Timer(true);
        acousticPumpTimer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                if (acousticPumpTimerThread == null ) acousticPumpTimerThread = Thread.currentThread();
                timerWorking = true;
                acousticPumpHandler.post(() -> {
                    pumpWorking = true;
                    for(IntObjectCursor<AcousticPackage> p : acousticPackages) {
                        PacketCodec c = p.value.codec;
                        c.update();
                        listenerCount = 0;
                        forEachListener(l -> listenerCount++);
                        if (listenerCount == 0) {
                            Log.w(TAG_SERVICE, "Hey, I have no listeners!!");
                        }
                        if (c.hasNextValidReceivedPacket()) {
                            byte[] packet = c.nextValidReceivedPacket();
                            forEachListener(l -> l.receiveAcousticMessage(p.value.peerId, socketService.getNodeId(), packet));
                        }
                    }
                });
            }
        }, 0, 100);
        Timer pumpMonitor = new Timer();
        pumpMonitor.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (! pumpWorking) {
                    Log.w(TAG_SERVICE, "Pump isn't going and that thread is " + (acousticPumpThread.isAlive() ? " alive." : " dead."));
                    if (! timerWorking) {
                        Log.w(TAG_SERVICE, "Also, timer isn't goingand that thread is " + (acousticPumpTimerThread != null && acousticPumpTimerThread.isAlive() ? " alive." : " dead."));
                    }
                }
                pumpWorking = false;
                timerWorking = false;
            }
        }, 1000, 1000);
    }
}
