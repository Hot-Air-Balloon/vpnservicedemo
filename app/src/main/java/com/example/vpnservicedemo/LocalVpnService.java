package com.example.vpnservicedemo;

import android.content.Intent;
import android.net.VpnService;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.example.tcpip.CommonMethods;
import com.example.tcpip.IPHeader;
import com.example.tcpip.TCPHeader;
import com.example.tcpip.UDPHeader;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Enumeration;

public class LocalVpnService extends VpnService implements Runnable {
    private ParcelFileDescriptor m_VPNInterface;
    //    private final SocketAddress serverAddress = new InetSocketAddress("172.16.167.128", 9090);
    private static final int MAX_PACKET_SIZE = Short.MAX_VALUE;
    private byte[] m_Packet;
    private IPHeader m_IPHeader;
    private TCPHeader m_TCPHeader;
    private UDPHeader m_UDPHeader;
    private static int LOCAL_IP;

    private class ReadTunnel implements Runnable {
        private DatagramChannel readTunnel;
        private FileOutputStream writeOut;
        ReadTunnel (DatagramChannel tunnel, FileOutputStream out) {
            this.readTunnel = tunnel;
            this.writeOut = out;
        }
        @Override
        public void run() {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(MAX_PACKET_SIZE);
                int length = 0;
                while (length != -1) {
                    length = readTunnel.read(buffer);
                    Log.i("recive", length + "");
                    if (length > 0) {
                        writeOut.write(buffer.array(), 0, length);
                        buffer.clear();
                    } else {
                        Thread.sleep(100);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
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
        Log.i("LocalVpnService", "run");
        try {
            this.m_VPNInterface = this.entablishVPN();
            // 获得网卡的输入输出流
            FileOutputStream out = new FileOutputStream(this.m_VPNInterface.getFileDescriptor());
            FileInputStream in = new FileInputStream(this.m_VPNInterface.getFileDescriptor());
            m_Packet = new byte[20000];
            m_IPHeader = new IPHeader(m_Packet, 0);
            m_TCPHeader = new TCPHeader(m_Packet, 20);
            m_UDPHeader = new UDPHeader(m_Packet, 20);
            int size = 0;
            while (size != -1) {
                while ((size = in.read(m_Packet)) > 0) {
                    onIPPacketReceived(m_IPHeader, size);
                }
                Thread.sleep(100);
            }
            out.close();
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    void onIPPacketReceived(IPHeader ipHeader, int size) throws IOException {
        switch (ipHeader.getProtocol()) {
            case IPHeader.TCP:
                TCPHeader tcpHeader = m_TCPHeader;
                tcpHeader.m_Offset = ipHeader.getHeaderLength();
                int sourceIP = ipHeader.getSourceIP();
                if (sourceIP == LOCAL_IP) {
                    Log.i("LocalVpnService", CommonMethods.ipIntToString(sourceIP));
                    Log.i("LocalVpnService", Short.toString(tcpHeader.getSourcePort()));
                    Log.i("LocalVpnService", CommonMethods.ipIntToString(ipHeader.getDestinationIP()));
                    Log.i("LocalVpnService", Short.toString(tcpHeader.getDestinationPort()));
                }
                Log.i("LocalVpnService", CommonMethods.ipIntToString(sourceIP));
                break;
            case IPHeader.UDP:

                break;
        }
    }
    private ParcelFileDescriptor entablishVPN () {
        LOCAL_IP = CommonMethods.ipStringToInt("10.0.0.2");
        Builder builder = new Builder();
        ParcelFileDescriptor pfdDescriptor = builder
                .setSession(("MyVPNService"))
                .addAddress("10.0.0.2", 24)
                .addDnsServer("223.5.5.5")
                .addRoute("0.0.0.0", 0)
                .establish();
        return pfdDescriptor;
    }
    public static String getLocalIP(){
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();)
                {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && (inetAddress instanceof Inet4Address))
                    {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        }
        catch (SocketException ex){
            ex.printStackTrace();
        }
        return null;
    }
}
