package com.example.vpnservicedemo;

import android.util.Log;
import android.util.SparseArray;

import com.example.tcpip.CommonMethods;
import com.example.tcpip.IPHeader;
import com.example.tcpip.TCPHeader;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class LocalUdpServer {
    public int Port;

    private class UdpConnectedMap {
        public DatagramSocket udpSocket;
        public DatagramChannel tunnel;
        public SocketAddress remoteAddress;
        public int sourcePort;
    }
    private DatagramSocket mSocket;
    private SparseArray<UdpConnectedMap> m_ConnectedArray;

    public LocalUdpServer(int port) {
        try {
            mSocket = new DatagramSocket(port);
            Port = mSocket.getLocalPort();
            m_ConnectedArray = new SparseArray<>();
            LocalReceiveHandleThread localHandleThread = new LocalReceiveHandleThread(mSocket);
            localHandleThread.start();
            Log.i("udp", "upd server listen " + mSocket.getLocalAddress() + ":" + Integer.toString(Port));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class LocalReceiveHandleThread extends Thread {
        private DatagramSocket mSocket;
        public LocalReceiveHandleThread(DatagramSocket socket) {
            mSocket = socket;
        }
        @Override
        public void run() {
            try {
                while (true) {
                    byte[] receiveData = new byte[2000];
                    ByteBuffer dataBuffer= ByteBuffer.wrap(receiveData);
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    mSocket.receive(receivePacket);
                    dataBuffer.clear();
                    dataBuffer.limit(receivePacket.getLength());
                    Log.i("udp", receivePacket.getAddress() + ":" + String.valueOf(receivePacket.getPort()));
                    short sourcePort = (short)receivePacket.getPort();
                    // String mapKey = receivePacket.getAddress() + ":" + Integer.toString(receivePacket.getPort());
                    // 这里需要有一个查看是否已经和目的端口建立了链接，如果没有需要新建，如果有就拿到这个socket发消息，并接收消息。
                    UdpConnectedMap state = null;
                    state = m_ConnectedArray.get(sourcePort);
                    if (state == null) {
                        NatSession session = NatSessionManager.getSession(sourcePort);
                        if (session == null) {
                            continue;
                        }
                        DatagramChannel tunnel = DatagramChannel.open();
                        SocketAddress serverAddress = new InetSocketAddress(session.RemoteHost, session.RemotePort);
                        try {
                            LocalVpnService.Instance.protect(tunnel.socket());
                            tunnel.connect(serverAddress);
                            tunnel.configureBlocking(false);

                            state = new UdpConnectedMap();
                            state.udpSocket = mSocket;
                            state.tunnel = tunnel;
                            state.remoteAddress = receivePacket.getSocketAddress();
                            state.sourcePort = sourcePort;
                            int writeLength = tunnel.write(dataBuffer);
                            Log.i("udp write length", Integer.toString(writeLength));
                            RemoteHandleThread mRemoteHandleThread = new RemoteHandleThread(state);
                            mRemoteHandleThread.start();
                            m_ConnectedArray.put(sourcePort, state);
                        } catch (Exception e) {
                            tunnel.close();
                        }
                        // 启动一个线程监听链接到远程到

                    } else {
                        state.tunnel.write(dataBuffer);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                mSocket.close();
            }
        }
    }

    public class RemoteHandleThread extends Thread {
        private UdpConnectedMap mState;
        public RemoteHandleThread(UdpConnectedMap state) {
            mState = state;
        }
        @Override
        public void run() {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(Short.MAX_VALUE);
                int length = 0;
                while (length != -1) {
                    length = mState.tunnel.read(buffer);
                    if (length > 0) {
                        Log.i("upd remote recive", length + "");
                        // 收到远程到udp报文后，转发给本地到socket
                        DatagramPacket sendPacket = new DatagramPacket(buffer.array(), 0, length, mState.remoteAddress);
                        mState.udpSocket.send(sendPacket);
                        buffer.clear();
                    } else {
                        Thread.sleep(100);
                    }
                }
                mState.tunnel.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                m_ConnectedArray.remove(mState.sourcePort);
            }
        }
    }
    public void start() {

    }
}
