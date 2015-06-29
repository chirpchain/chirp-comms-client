package com.cherrydev.chirpcommsclient.socketservice;

import com.cherrydev.chirpcommsclient.socketmessages.AudioDataMessage;
import com.cherrydev.chirpcommsclient.socketmessages.ByteMessage;
import com.cherrydev.chirpcommsclient.messages.ChirpMessage;
import com.cherrydev.chirpcommsclient.socketmessages.ChirpSocketMessage;

import java.util.Set;


public interface SocketServiceListener {
    void peerIdSet(byte peerId);
    void peerConnected(byte newPeerId);
    void peerDisconnected(byte peerId);
    void connected();
    void ready();
    void disconnected();
    void receiveAudioData(AudioDataMessage message);
    void receivePeerList(Set<Byte> peerIds);
    void receiveByteData(ByteMessage message);
    void receiveChirpMessage(ChirpSocketMessage message);
    void clientError(String errorMessage);
}
