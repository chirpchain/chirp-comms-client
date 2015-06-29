package com.cherrydev.chirpcommsclient.socketservice;

import com.cherrydev.chirpcommsclient.messages.ChirpMessage;
import com.cherrydev.chirpcommsclient.socketmessages.AudioDataMessage;
import com.cherrydev.chirpcommsclient.socketmessages.ByteMessage;
import com.cherrydev.chirpcommsclient.socketmessages.ChirpSocketMessage;

import java.util.Set;

public class BaseSocketServiceListener implements SocketServiceListener {
    @Override
    public void peerIdSet(byte peerId) {

    }

    @Override
    public void peerConnected(byte newPeerId) {

    }

    @Override
    public void peerDisconnected(byte peerId) {

    }

    @Override
    public void connected() {

    }

    @Override
    public void ready() {

    }

    @Override
    public void disconnected() {

    }

    @Override
    public void receiveAudioData(AudioDataMessage message) {

    }

    @Override
    public void receivePeerList(Set<Byte> peerIds) {

    }

    @Override
    public void receiveByteData(ByteMessage message) {

    }

    @Override
    public void receiveChirpMessage(ChirpSocketMessage message) {

    }

    @Override
    public void clientError(String errorMessage) {

    }
}
