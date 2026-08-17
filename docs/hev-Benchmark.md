## Hardware/Software

CPU: AMD Ryzen 9 7950X 16-Core Processor (Max 5.7GHz)

OS: Arch Linux (Linux 6.15.8)

Iperf3: 3.19.1

### Socks5 server

Repo: https://github.com/heiher/hev-socks5-server

Version: 2.9.0

### Topology

```
Namespace host                         Namespace guest
eth0:  192.168.0.8
veth0: 192.168.1.1                     veth1: 192.168.1.2
socks5 server(listen on: 192.168.1.1)  tun2socks(connect to: 192.168.1.1)
iperf3 server(listen on: 192.168.0.8)  iperf3 client(connect to: 192.168.0.8)
```

### MTU

eth0/veth0/veth1: 1500

tun0: 8500

## hev-socks5-tunnel

Repo: https://github.com/heiher/hev-socks5-tunnel

Version: 2.13.0

Command:

```
# Multi-queue on, 6 processes
hev-socks5-tunnel conf/main.yml
```

```
tunnel:
  name: tun0
  mtu: 8500
  multi-queue: true
  ipv4: 198.18.0.1
  ipv6: 'fc00::1'

socks5:
  port: 1080
  address: 192.168.1.1
  udp: 'udp'
```

### Upload

```
$ iperf3 -c 192.168.0.8
- - - - - - - - - - - - - - - - - - - - - - - - -
[  5]   0.00-10.00  sec  38.1 GBytes  32.8 Gbits/sec    0            sender
[  5]   0.00-10.04  sec  38.1 GBytes  32.6 Gbits/sec                 receiver
CPU usage: 76%
MEM usage: 4.0M
```

```
$ iperf3 -c 192.168.0.8 -P 10
- - - - - - - - - - - - - - - - - - - - - - - - -
[SUM]   0.00-10.00  sec   132 GBytes   113 Gbits/sec    0             sender
[SUM]   0.00-10.04  sec   132 GBytes   113 Gbits/sec                  receiver
CPU usage: 480%
MEM usage: 24.2M
```

### Download

```
$ iperf3 -c 192.168.0.8 -R
- - - - - - - - - - - - - - - - - - - - - - - - -
[  5]   0.00-10.04  sec  31.4 GBytes  26.9 Gbits/sec    0            sender
[  5]   0.00-10.00  sec  31.4 GBytes  27.0 Gbits/sec                 receiver
CPU usage: 83%
MEM usage: 4.0M
```

```
$ iperf3 -c 192.168.0.8 -R -P 10
- - - - - - - - - - - - - - - - - - - - - - - - -
[SUM]   0.00-10.04  sec   121 GBytes   104 Gbits/sec    0             sender
[SUM]   0.00-10.00  sec   121 GBytes   104 Gbits/sec                  receiver
CPU usage: 498%
MEM usage: 24.8M
```

## sing-box

Repo: https://github.com/SagerNet/sing-box

Version: 1.11.15

Command:

```
sing-box -c tun2socks.json run
```

```
{
  "inbounds": [
    {
        "type": "tun",
        "address": [
            "198.18.0.1/24"
        ],
        "mtu": 8500,
        "auto_route": false,
        "strict_route": false
    }
  ],
  "outbounds": [
    {
        "type": "socks",
        "tag": "socks-out",
        "server": "192.168.1.1",
        "server_port": 1080,
        "version": "5"
    }
  ]
}
```

### Upload

```
$ iperf3 -c 192.168.0.8
- - - - - - - - - - - - - - - - - - - - - - - - -
[  5]   0.00-10.00  sec  30.3 GBytes  26.0 Gbits/sec    0            sender
[  5]   0.00-10.04  sec  30.3 GBytes  25.9 Gbits/sec                 receiver
CPU usage: 161%
MEM usage: 34.3M
```

```
$ iperf3 -c 192.168.0.8 -P 10
- - - - - - - - - - - - - - - - - - - - - - - - -
[SUM]   0.00-10.00  sec  24.2 GBytes  20.7 Gbits/sec  6599            sender
[SUM]   0.00-10.04  sec  24.1 GBytes  20.6 Gbits/sec                  receiver
CPU usage: 155%
MEM usage: 35.8M
```

### Download

```
$ iperf3 -c 192.168.0.8 -R
- - - - - - - - - - - - - - - - - - - - - - - - -
[  5]   0.00-10.04  sec  26.8 GBytes  22.9 Gbits/sec    0            sender
[  5]   0.00-10.00  sec  26.8 GBytes  23.0 Gbits/sec                 receiver
CPU usage: 147%
MEM usage: 36.1M
```

```
$ iperf3 -c 192.168.0.8 -R -P 10
- - - - - - - - - - - - - - - - - - - - - - - - -
[SUM]   0.00-10.04  sec  19.0 GBytes  16.2 Gbits/sec  4776            sender
[SUM]   0.00-10.00  sec  18.8 GBytes  16.1 Gbits/sec                  receiver
CPU usage: 125%
MEM usage: 37.4M
```

## xray-tun2socks

Repo: https://github.com/XTLS/Xray-core

Version: 26.1.13

Command:

```
xray run -c tun2socks.json
```

```
{
  "inbounds": [
    {
        "tag": "tun",
        "port": 0,
        "protocol": "tun",
        "settings": {
            "name": "tun0",
            "MTU": 8500,
            "userLevel": 8
        }
    }
  ],
  "outbounds": [
    {
        "tag": "socks-out",
        "protocol": "socks",
        "settings": {
            "servers": [
                {
                    "address": "192.168.1.1",
                    "port": 1080
                }
            ]
        }
    }
  ]
}
```

### Upload

```
$ iperf3 -c 192.168.0.8
- - - - - - - - - - - - - - - - - - - - - - - - -
[  5]   0.00-10.00  sec  17.3 GBytes  14.8 Gbits/sec    0            sender
[  5]   0.00-10.04  sec  17.3 GBytes  14.8 Gbits/sec                 receiver
CPU usage: 177%
MEM usage: 67M
```

```
$ iperf3 -c 192.168.0.8 -P 10
- - - - - - - - - - - - - - - - - - - - - - - - -
[SUM]   0.00-10.00  sec  25.5 GBytes  21.9 Gbits/sec  12416          sender
[SUM]   0.00-10.04  sec  25.5 GBytes  21.8 Gbits/sec                 receiver
CPU usage: 310%
MEM usage: 68M
```

### Download

```
$ iperf3 -c 192.168.0.8 -R
- - - - - - - - - - - - - - - - - - - - - - - - -
[  5]   0.00-10.04  sec  12.8 GBytes  10.9 Gbits/sec    0            sender
[  5]   0.00-10.00  sec  12.7 GBytes  10.9 Gbits/sec                 receiver
CPU usage: 160%
MEM usage: 67M
```

```
$ iperf3 -c 192.168.0.8 -R -P 10
- - - - - - - - - - - - - - - - - - - - - - - - -
[SUM]   0.00-10.04  sec  36.4 GBytes  31.2 Gbits/sec  166            sender
[SUM]   0.00-10.00  sec  36.3 GBytes  31.2 Gbits/sec                 receiver
CPU usage: 610%
MEM usage: 68M
```

---

注：目前xray-core最新版本是v26.7.28，完整仓库克隆在`D:\Develop\repo\clone\XrayGUI\Xray-core`，最新commit距离v26.7.28仅多了8个新提交。最新commit是`7d214f8b `；该Benchmark使用的版本26.1.13对应commit为`9a121a489b01b1af46f43cfb060fe88c89f1570b`。建议使用`git log --oneline`快速查看有关tun的变更。

