# coding:utf-8
import socket
import fcntl
import os
import struct
import threading

TUNSETIFF = 0x400454ca
TUNSETOWNER = TUNSETIFF + 2
IFF_TUN = 0x0001
IFF_TAP = 0x0002
IFF_NO_PI = 0x1000

def udpListen():
  address = ('0.0.0.0', 9090)
  s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
  s.bind(address)

  # Open TUN device file.
  tun = open('/dev/net/tun', 'r+b')
  # Tall it we want a TUN device named tun0.
  ifr = struct.pack('16sH', 'tun0', IFF_TUN | IFF_NO_PI)
  fcntl.ioctl(tun, TUNSETIFF, ifr)
  # Optionally, we want it be accessed by the normal user.
  fcntl.ioctl(tun, TUNSETOWNER, 1000)
  #t = threading.Thread(target=recv, args=(s, tun))
  #t.start()
  recv(s, tun)
  #while True:
      # data, addr = s.recvfrom(2048)
      #if not data:
      #    print "client has exist"
      #    break
      # print "received:", len(data), "from", addr
      # os.write(tun.fileno(), data)
      #readData = os.read(tun.fileno(), 2048)
      #s.sendto(readData, addr)
      #s.sendto("asaasdfad", addr)

def recv(s, tun):
    while True:
        data, addr = s.recvfrom(2048)
        print "received:", len(data), "from", addr
        writeLength = os.write(tun.fileno(), data)
        print "write:", writeLength
        t = threading.Thread(target=updSend, args=(s, tun, addr))
        t.start()
        #readData = os.read(tun.fileno(), 2048)
        #print "readFile:", len(readData)
        #s.sendto(readData, addr)

def updSend(s, tun, addr):
    readData = os.read(tun.fileno(), 2048)
    print "readFile:", len(readData)
    writeFile = s.sendto(readData, addr)
    print "sendToAndroid:", writeFile

if __name__ == '__main__':
    udpListen()