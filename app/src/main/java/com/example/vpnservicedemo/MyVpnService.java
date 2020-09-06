package com.example.vpnservicedemo;

import android.app.Service;
import android.content.Intent;
import android.net.VpnService;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class MyVpnService extends VpnService implements Runnable {
    private ParcelFileDescriptor m_VPNInterface;
    private final SocketAddress serverAddress = new InetSocketAddress("172.16.167.128", 9090);
    private static final int MAX_PACKET_SIZE = Short.MAX_VALUE;
    @Override
    public  void onCreate () {
        Log.i("MyVpnService", "create");
        super.onCreate();
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("MyVpnService", "onStartCommand");
        Thread m_VPNThread = new Thread(this, "MyVPNServiceThread");
        m_VPNThread.start();
        return START_STICKY;
//        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
//            disconnect();
//            return START_NOT_STICKY;
//        } else {
//            connect();
//            return START_STICKY;
//        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        Log.i("DemoLog", "TestService -> onBind, Thread ID: " + Thread.currentThread().getId());
        return null;
    }

    @Override
    public void run() {
        try {
            this.m_VPNInterface = this.entablishVPN();
            // 获得网卡的输入输出流
            FileOutputStream out = new FileOutputStream(this.m_VPNInterface.getFileDescriptor());
            FileInputStream in = new FileInputStream(this.m_VPNInterface.getFileDescriptor());

            DatagramChannel tunnel = DatagramChannel.open();

            if (!this.protect(tunnel.socket())) {
                throw new IllegalStateException("Cannot protect the tunnel");
            }

            tunnel.connect(serverAddress);

            tunnel.configureBlocking(false);

            // 存放每一个IP报文
            // byte[] m_IpPacket = new byte[20000];
            ByteBuffer packet = ByteBuffer.allocate(MAX_PACKET_SIZE);
            int size = 0;
            ByteBuffer buffer = ByteBuffer.allocate(MAX_PACKET_SIZE);
//            tunnel.write(buffer);
//            tunnel.close();
            while (size != -1) {
                while ((size = in.read(packet.array())) > 0) {
//                    packet.limit(size);
//                    int writeSize = tunnel.write(packet);
//                    Log.i("write size", Integer.toString(writeSize));
//                    packet.clear();
//                    size = tunnel.read(buffer);
//                    Log.i("size", size + "");
//                    break;
                    Log.i("read length", Integer.toString(size));
                    packet.limit(size);
                    int writeSize = tunnel.write(packet);
                    Log.i("write size", Integer.toString(writeSize));
                    packet.clear();
                    Thread.sleep(100);
                    size = tunnel.read(buffer);
                    Log.i("size", size + "");
                    if (size > 0) {
                        String s = new String(buffer.array(), 0, size, "utf-8");
                        Log.i("read s", s);
                        out.write(buffer.array(), 0, size);
                        buffer.clear();
                    }
                }
            }
            out.close();
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private ParcelFileDescriptor entablishVPN () {
        Builder builder = new Builder();
        ParcelFileDescriptor pfdDescriptor = builder
                .setSession(("MyVPNService"))
                .addAddress("10.0.0.2", 24)
                .addDnsServer("223.5.5.5")
                .addRoute("0.0.0.0", 0)
                .establish();
        return pfdDescriptor;
    }
}
