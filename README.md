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

## 第四节

数据终于可以链接起来跑了，从andorid到服务端，服务端写tun设备，再从tun设备读取数据返回给android，android再写数据回安卓端的tun设备。

目前的问题
1、数据正常跑起来了以后，android端实际还是不能上网，数据报文肯定有地方不对。
2、还有一个没弄明白的问题，如果多个客户端链接服务器，都走的udp，这些包都会给到tun，从tun读取这些包时需要根据这些包的目的地址做分发。
这需要在获得udp数据时在服务器维护每个udp链接信息，对不同对目的地址，发给不同的udp链接。
3、Android模拟器里有时接收不到服务器返回的udp包，需要重启模拟器以后才能获取到。
4、Android模拟器在切换WI-FI网络后，本地DNS服务会有问题，需要启动是时候设置为本机当前的DNS服务。
```
./emulator -avd Pixel_2_API_30 -dns-server 192.168.0.1
```
emulator的目录是android sdk所在目录，在环境变量里可以查看。

数据跑起来，浏览网页，提示DNS错误，我修改了DNS，改了一个阿里的。居然能打开网页了。
只是异常的慢，不知道是不是线程使用的不对。应该把接受和发送的线程分开。

## 第五节

客户端单独开了线程读取数据，速度还是不快。我在想，是不是iptables这个转发导致的速度慢。之前在服务器上使用ssh隧道+iptables转发就感知到速度慢这个问题。

## 第六节

准备在JAVA层实现分解IP包，把IP包转换为socket链接来转发流量。这样就不需要在服务器上配置iptables转发。
自己脑海里想象的流程是，先假设不走代理，本地的socket服务器直接链接网络。
1、开一个本地端口LP，接受从tun设备读取后转发过来的报文。
2、获得tun里的IP报文，解析报文里的数据是TCP还是UDP。
3、如果是TCP报文，需要分析TCP报文里的源地址、源端口。目的地址、目的端口。对于同一个源端口、目的地址和目的端口确定一个session。
4、如果这个TCP报文的IP报文的源端口不是LP，则修改这个IP报文的目的地址为local，TCP目的端口为LP。IP报文源地址为本来的目的地址（为了传递数据）。
5、在本地端口LP和读写tun设备的函数两本，采用了一个session来确认当前处理的报文如何传递。
6、当LP拿到一个链接的时候，可以获得链接的源IP和源端口。根据源端口和源IP在session里找到这个数据包本来应该发送的目的地址。
7、然后给这个链接和目的地址建立隧道。
8、当tun读取到从LP过来的报文时，需要根据目的端口找到session，根据session里的信息把报文的源地址和源端口修改为session的目的地址和目的端口。报文的目的地址和目的端口改为session的
源地址和源端口。
JAVA里没有无符号类型，所以端口号一旦大了，就会输出负数。Byte和Integer类都有相应都静态方法用来把有符号数转换为无符号数。

## 第七节

java TCP/IP里。有一个selector,可以在一个线程里管理多个socketChannel,核心操作就是把selector绑定到多个socket上。
把IP报文修改以后，再写回tun设备失败了，因为接收报文的socket并没有收到请求。
目前调试这个问题2个方向：
1、可能是IP报文的CRC不对。
2、可能tun设备设置的不对。
可以通过上一次直接转发给iptables的报文来调试是否是CRC不对。

最后不对的原因是因为我没有转发从socket端口回来的握手报文，改着改着就对了。
要根据seletor做两个socket的转发

## 第八节

使用seletor能转发数据了，但是访问域名的请求有问题，访问IP的就行。估计是没有转发UDP，导致DNS有问题。发现访问IP就能正常访问。
然后目前这个转发会让CPU使用率爆满，在selector的循环里加了等待就好了。
但是还发现一个问题，程序多启动几次以后。转发报文会失败。

还有如何停止这个VPNSrevice?

## 第九节

准备把UDP报文也转发到本地端口，进行处理。结果发现转发后，本地到端口收不到。
是不是UDP报文在接收的时候又啥独特的地方呢？ 又是重启的模拟器解决了，尼玛，这都是啥玩意呀。

## 第十节

加上UDP以后，单独解析域名是没有问题到。目前明确了几个问题：
1、模拟器可能突然不能转发正常，需要重启。重启以后可能没有网络，需要手动链接wifi.
2、目前单独转发tcp和udp测试链接没有问题，打开http，ip对应到网址没有问题。但是打开域名或者https就会卡住。

解决方案，多增加tcp和udp测试。可能某个地方有问题。