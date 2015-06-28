package com.cherrydev.chirpcommsclient;

import java.util.Set;


public interface ChirpCommsServiceListener {
    void peerIdSet(int peerId);
    void peerConnected(int newPeerId);
    void peerDisconnected(int peerId);
    void connected();
    void ready();
    void disconnected();
    void receiveAudioData(AudioDataMessage message);
    void receivePeerList(Set<Integer> peerIds);
}
