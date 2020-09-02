## 第一节

启动了android项目。
启动了VpnService，需要在AndroidManifest.xml文件里声明服务所在的类。
目前对android里对Service和Intent还比较陌生。

计划下一步继续完善MyVpnService类。要学习还很多，每天坚持。

## 第二接

可以建立VPN通道了，读写tun0的数据，想网上说的，读取的数据如果再写回去，又会回到VPNService里来。
VPN建立以后，核心在于establish返回的ParcelFileDescriptor对象，可以提供输入输出流。
google的DEMO里实现了UDP的隧道VPN，建立一个UDP链接，并把这个链接设置为protect，这样数据流才能流出设备。
在VPNServer端解析这个UDP数据。
设置UDP链接用到了DatagramChannel类，接下来估计是了解这个类。并用python重写goolge demo里的Server端。
这样就能完成一个demo了。

