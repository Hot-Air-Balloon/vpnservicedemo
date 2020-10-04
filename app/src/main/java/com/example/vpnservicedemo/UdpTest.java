package com.example.vpnservicedemo;

import android.util.Log;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

public class UdpTest implements Runnable {
    private final SocketAddress serverAddress = new InetSocketAddress("192.168.1.40", 9527);

    public void runThread () {
        Thread m_udpThread = new Thread(this, "UdpTest");
        m_udpThread.start();
    }
    @Override
    public void run() {
        try {
//            DatagramChannel tunnel = DatagramChannel.open();
//            tunnel.connect(serverAddress);
//            tunnel.configureBlocking(true);
//            ByteBuffer buffer = ByteBuffer.wrap("Content of the String".getBytes("utf-8"));
//            tunnel.write(buffer);
//            buffer.clear();
//            int readSize = tunnel.read(buffer);
//            Log.i("udpTest", readSize + "");
////            Charset charset = Charset.forName("utf-8");
////            CharsetDecoder decoder = charset.newDecoder();
////            CharBuffer charBuffer = decoder.decode(buffer);
//            String s = new String(buffer.array(), 0, readSize, "utf-8");
//            Log.i("udpTest", s);
//            InetAddress testDomain = InetAddress.getByName("facebook.com");
//            Log.i("udpTest", testDomain.getHostAddress());
//            tunnel.close();
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.connect(new InetSocketAddress("192.168.1.40", 9527));
            String newData = "New String to write to file..." + System.currentTimeMillis();

            ByteBuffer buf = ByteBuffer.allocate(48);
            buf.clear();
            buf.put(newData.getBytes());

            buf.flip();

            while(buf.hasRemaining()) {
                socketChannel.write(buf);
            }
            buf.clear();
            int bytesRead = socketChannel.read(buf);
            Log.i("udpTest", Integer.toString(bytesRead));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
