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
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Enumeration;

public class LocalVpnService extends VpnService implements Runnable {
    public static LocalVpnService Instance;
    private ParcelFileDescriptor m_VPNInterface;
    //    private final SocketAddress serverAddress = new InetSocketAddress("172.16.167.128", 9090);
    private static final int MAX_PACKET_SIZE = Short.MAX_VALUE;
    private byte[] m_Packet;
    private IPHeader m_IPHeader;
    private TCPHeader m_TCPHeader;
    private UDPHeader m_UDPHeader;
    private static int LOCAL_IP;
    private FileOutputStream m_out;
    LocalTcpServer m_localTcpServer;

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
    public synchronized void run() {
        Log.i("LocalVpnService", "run");
        try {
            Instance = this;
            m_localTcpServer = new LocalTcpServer((short)0);
            m_localTcpServer.start();

            this.m_VPNInterface = this.entablishVPN();
            // 获得网卡的输入输出流
            m_out = new FileOutputStream(this.m_VPNInterface.getFileDescriptor());
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
            m_out.close();
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
                short sourcePort = tcpHeader.getSourcePort();
                int destinationIP = ipHeader.getDestinationIP();
                short destinationPort = tcpHeader.getDestinationPort();
                // 如果是从tun过来的报文，就修改报文发给本地socket端口
                if (destinationPort == m_localTcpServer.Port) {
                    System.out.printf("send loop");
                }
                if (sourceIP == LOCAL_IP) {
                    System.out.printf("%s:%d to %s:%d\n",
                            CommonMethods.ipIntToString(sourceIP),
                            sourcePort & 0xFFFF,
                            CommonMethods.ipIntToString(destinationIP),
                            destinationPort & 0xFFFF
                    );
//                     Log.i("LocalVpnService", CommonMethods.ipIntToString(sourceIP));
//                     Log.i("LocalVpnService", Short.toString(sourcePort));
//                     Log.i("LocalVpnService", CommonMethods.ipIntToString(destinationIP));
//                     Log.i("LocalVpnService", Short.toString(destinationPort));
                    if (sourcePort != m_localTcpServer.Port) {
                        // 如果不是从本地scoket服务来的报文，就把报文发给本地socket服务
                        // 如果会话是不存在，就创建会话
                        NatSession session = NatSessionManager.getSession(sourcePort);
                        if (session == null ||
                            session.RemoteIP != destinationIP ||
                            session.RemotePort != destinationPort) {
                            session = NatSessionManager.createSession(sourcePort, destinationIP, destinationPort);
                        }
                        session.PacketSent++;
                        // 一个小优化，不知道是否正确
                        int tcpDataSize = ipHeader.getDataLength() - tcpHeader.getHeaderLength();
                        // if (session.PacketSent == 2 && tcpDataSize == 0) {
                            // return; // 丢弃tcp握手的第二个ACK报文。因为客户端发数据的时候也会带上ACK，这样可以在服务器Accept之前分析出HOST信息。
                        // }

                        // 分析数据，找到Host
                        // if (session.BytesSent == 0 && tcpDataSize > 10) {
                        //    int dataOffset = tcpHeader.m_Offset + tcpHeader.getHeaderLength();
                        //    String host = HttpHostHeaderParser.parseHost(tcpHeader.m_Data, dataOffset, tcpDataSize);
                        //    if (host != null) {
                        //        session.RemoteHost = host;
                        //    }
                        // }

                        // 转发给本地tcp socket服务器
                        ipHeader.setSourceIP(destinationIP);
                        ipHeader.setDestinationIP(LOCAL_IP);
                        tcpHeader.setDestinationPort((short)(m_localTcpServer.Port & 0xFFFF));

                        CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);
                        // System.out.printf("%s:%d\n", CommonMethods.ipIntToString(ipHeader.getDestinationIP()), tcpHeader.getDestinationPort() & 0xFFFF);
                        m_out.write(ipHeader.m_Data, ipHeader.m_Offset, size);
//                        System.out.printf("%s\n", new String(ipHeader.m_Data));
                        // System.out.printf("write %d size data from %s:%d to %s:%d.\n", size, CommonMethods.ipIntToString(sourceIP), sourcePort & 0xFFFF, CommonMethods.ipIntToString(destinationIP), destinationPort & 0xFFFF);
                        session.BytesSent += tcpDataSize;
                    }
                }
                // if (sourceIP != LOCAL_IP) {
//                Socket client = new Socket(CommonMethods.ipIntToString(LOCAL_IP), m_localTcpServer.Port & 0xFFFF);
//                client.close();
                // }
                // Log.i("LocalVpnService", CommonMethods.ipIntToString(sourceIP));
                break;
            case IPHeader.UDP:
                break;
            default:
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
