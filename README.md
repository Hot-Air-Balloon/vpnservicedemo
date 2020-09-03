## 第一节

启动了android项目。
启动了VpnService，需要在AndroidManifest.xml文件里声明服务所在的类。
目前对android里对Service和Intent还比较陌生。

计划下一步继续完善MyVpnService类。要学习还很多，每天坚持。

## 第二节

可以建立VPN通道了，读写tun0的数据，想网上说的，读取的数据如果再写回去，又会回到VPNService里来。
VPN建立以后，核心在于establish返回的ParcelFileDescriptor对象，可以提供输入输出流。
google的DEMO里实现了UDP的隧道VPN，建立一个UDP链接，并把这个链接设置为protect，这样数据流才能流出设备。
在VPNServer端解析这个UDP数据。
设置UDP链接用到了DatagramChannel类，接下来估计是了解这个类。并用python重写goolge demo里的Server端。
这样就能完成一个demo了。

## 第三节

google的demo里，server端新建了一个tun设备，把数据从写入tun设备。利用iptable对写入tun设备对数据做SNET转发。
```
// # Enable IP forwarding
echo 1 > /proc/sys/net/ipv4/ip_forward

// # 在nat表的postrouting链建立规则，从10.0.0.0/8原地址来的数据给eth0
iptables -t nat -A POSTROUTING -s 10.0.0.0/8 -o eth0 -j MASQUERADE

// # 新建一个tun设备
ip tuntap add dev tun0 mode tun

// # tun设备绑定ip,且和10.0.0.2建立点对点，使中建传输的数据不再被监听获取。这里为什么这样做呢
// # 是因为10.0.0.2正是客户端的ip,所有写入tun设备的ip报文源IP地址都会是10.0.0.2，？我是这样理解的。
ifconfig tun0 10.0.0.1 dstaddr 10.0.0.2 up
```

接下来就是在客服端建立UDP链接，保护起来，和服务端同学。google的demo里是使用CPP写的，这里我们来使用python3实现这个功能。
功能就是把从客户端UDP链接收到的报文写入tun设备，然后从tun设备读取报文再写回UDP链接。

