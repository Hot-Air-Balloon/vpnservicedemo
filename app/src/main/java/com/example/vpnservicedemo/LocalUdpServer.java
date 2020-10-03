package com.example.vpnservicedemo;

import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.channels.DatagramChannel;

public class LocalUdpServer implements Runnable {
    private DatagramSocket mSocket;
    public int Port;
    public LocalUdpServer(int port) {
        try {
            mSocket = new DatagramSocket(port);
            Port = mSocket.getLocalPort();
            HandleThread handleThread = new HandleThread(mSocket);
            handleThread.start();
            Log.i("udp", "upd server listen " + mSocket.getLocalAddress() + ":" + Integer.toString(Port));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class HandleThread extends Thread {
        private DatagramSocket mSocket;
        public HandleThread(DatagramSocket socket) {
            mSocket = socket;
        }
        @Override
        public void run() {
            try {
                while (true) {
                    byte[] receiveData = new byte[2000];
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    mSocket.receive(receivePacket);
                    Log.i("udp", receivePacket.getAddress() + ":" + String.valueOf(receivePacket.getPort()));
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                mSocket.close();
            }
        }
    }

    public void start() {

    }

    @Override
    public void run() {

    }
}
