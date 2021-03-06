package com.example.vpnservicedemo;

import android.util.Log;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class LocalTcpServer implements Runnable {
    public short Port;
    Selector m_Selector;
    ServerSocketChannel m_ServerSocketChannel;
    Thread m_ServerThread;
    public LocalTcpServer(short port) {
        try {
            m_Selector = Selector.open();
            m_ServerSocketChannel = ServerSocketChannel.open();
            m_ServerSocketChannel.configureBlocking(false);
            m_ServerSocketChannel.socket().bind(new InetSocketAddress(port));
            // tcp服务的socket，只监听了accept事件
            m_ServerSocketChannel.register(m_Selector, SelectionKey.OP_ACCEPT);
            // 如果bind的port是0，会绑定一个随机的端口，这里获得实际绑定的端口
            this.Port = (short)m_ServerSocketChannel.socket().getLocalPort();
            System.out.printf("AsyncTcpServer listen on %s:%d success.\n", m_ServerSocketChannel.socket().getLocalSocketAddress(), this.Port&0xFFFF);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void start() {
        try {
            m_ServerThread = new Thread(this);
            m_ServerThread.setName("LocalTcpServerThread");
            m_ServerThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                // 监听selector的事件
//                System.out.printf("select run\n");
                m_Selector.select();
//                System.out.printf("select working\n");
                Iterator<SelectionKey> keyIterator = m_Selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    if (!key.isValid()) {
                        keyIterator.remove();
                        continue;
                    }
                    if (key.isAcceptable()) {
                        onAccepted();
                    } else if (key.isReadable()) {
                        // System.out.printf("isReadable\n");
                        ((ForwardObj)key.attachment()).readAndWrite(key);
                    } else if (key.isWritable()) {

                    } else if (key.isConnectable()) {
                        ((ForwardObj)key.attachment()).onConnectHandle();
                    }
                    keyIterator.remove();
                }
                Thread.sleep(100);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    InetSocketAddress getDestAddress (SocketChannel localChannel) {
        short portKey = (short)localChannel.socket().getPort();
        NatSession session = NatSessionManager.getSession(portKey);
        if (session != null) {
            return new InetSocketAddress(session.RemoteHost, session.RemotePort);
        }
        return null;
    }

    private class ForwardObj {
        public SocketChannel readChannel;
        public SocketChannel writeChannel;
        public Selector m_forwardSelector;
        public ForwardObj readForwardObj;

        public ByteBuffer GL_BUFFER = ByteBuffer.allocate(20000);

        public void onConnectHandle() {
            try {
                if (this.readChannel.finishConnect()) {
                    readForwardObj.readChannel.register(m_forwardSelector, SelectionKey.OP_READ, readForwardObj);
                    Log.i("localTcp", "target connect");
                } else {

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void readAndWrite(SelectionKey key) {
            try {
                ByteBuffer buffer = GL_BUFFER;
                int bytesRead = readChannel.read(buffer);
                if (bytesRead > 0) {
                    buffer.flip();
                    writeChannel.write(buffer);
                }
                if (bytesRead == -1) {
                    key.cancel();
                }
            } catch (Exception e) {
                e.printStackTrace();
                key.cancel();
                this.dispose();
            }
        }

        public void dispose() {
            try {
                readChannel.close();
                writeChannel.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    /**
     * 接受tun转发来的链接
     */
    void onAccepted() {
        try {
            SocketChannel localChannel = m_ServerSocketChannel.accept();
            localChannel.configureBlocking(false);
            final InetSocketAddress destAddress = getDestAddress(localChannel);
            Log.i("localTcpServer", destAddress.getAddress() + ":" + destAddress.getPort());
            final SocketChannel targetChannel = SocketChannel.open();
            targetChannel.configureBlocking(false);
            LocalVpnService.Instance.protect(targetChannel.socket());

            ForwardObj m_localForward = new ForwardObj();
            m_localForward.readChannel = localChannel;
            m_localForward.writeChannel = targetChannel;

            ForwardObj m_targetForward = new ForwardObj();
            m_targetForward.readChannel = targetChannel;
            m_targetForward.writeChannel = localChannel;
            m_targetForward.m_forwardSelector = m_Selector;
            m_targetForward.readForwardObj = m_localForward;

            // localChannel.register(m_Selector, SelectionKey.OP_READ, m_localForward);
            targetChannel.register(m_Selector, SelectionKey.OP_READ | SelectionKey.OP_CONNECT, m_targetForward);
            targetChannel.connect(destAddress);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
