package com.example.vpnservicedemo;

import android.app.Service;
import android.content.Intent;
import android.net.VpnService;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;

public class MyVpnService extends VpnService {
    private ParcelFileDescriptor m_VPNInterface;

    @Override
    public  void onCreate () {
        Log.i("MyVpnService", "create");
        super.onCreate();
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("MyVpnService", "onStartCommand");
        try {
            this.runVPN();
        } catch (Exception e) {
            Log.i("MyVpnService", e.getMessage());
        }
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
    private void runVPN() throws Exception {
        this.m_VPNInterface = this.entablishVPN();
        // 获得网卡的输入输出流
        FileOutputStream out = new FileOutputStream(this.m_VPNInterface.getFileDescriptor());
        FileInputStream in = new FileInputStream(this.m_VPNInterface.getFileDescriptor());
        // 存放每一个IP报文
        byte[] m_IpPacket = new byte[20000];
        int size = 0;
        while (size != -1) {
            while ((size = in.read(m_IpPacket)) > 0) {
                Log.i("read length", Integer.toString(size));
                out.write(m_IpPacket, 0, size);
            }
            Thread.sleep(100);
        }
        out.close();
        in.close();
    }
    private ParcelFileDescriptor entablishVPN () {
        Builder builder = new Builder();
        ParcelFileDescriptor pfdDescriptor = builder
                .setSession(("MyVPNService"))
                .addAddress("192.168.0.1", 24)
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
                .establish();
        return pfdDescriptor;
    }
}
