# SimpleXray Benchmark Summary
Generated at: 2026-09-20 17:33:10

### Round 1 Results

| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 312.47 Mbps | 763.9 Mbps | 0.3% | 0.8% | 3.7% | 146.6 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 820.93 Mbps | 843.11 Mbps | 0.2% | 0.5% | 1.0% | 145.4 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 612.93 Mbps | 638.3 Mbps | 60.6% | 88.3% | 103.0% | 171.9 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 657.0 Mbps | 198.78 Mbps | 168.2% | 118.4% | 201.0% | 172.7 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 642.67 Mbps | 255.72 Mbps | 65.4% | 121.4% | 134.0% | 188.8 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 521.6 Mbps | 236.83 Mbps | 117.2% | 201.1% | 235.4% | 128.6 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | 5GHz Wi-Fi | 597.87 Mbps | 807.2 Mbps | 41.4% | 109.0% | 125.0% | 97.3 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | 5GHz Wi-Fi | 254.93 Mbps | 60.18 Mbps | 167.9% | 106.8% | 191.7% | 97.8 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | 5GHz Wi-Fi | 607.7 Mbps | 566.48 Mbps | 76.7% | 147.6% | 159.0% | 111.3 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | 5GHz Wi-Fi | 584.01 Mbps | 788.71 Mbps | 73.0% | 202.6% | 211.0% | 127.6 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 759.9 Mbps | 279.57 Mbps | 76.3% | 78.9% | 153.0% | 98.3 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 364.69 Mbps | 184.68 Mbps | 239.8% | 186.9% | 255.5% | 99.2 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 715.49 Mbps | 766.58 Mbps | 159.8% | 349.0% | 437.0% | 115.7 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | 5GHz Wi-Fi | 823.23 Mbps | 135.88 Mbps | 145.4% | 190.0% | 295.7% | 148.8 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | 5GHz Wi-Fi | 833.04 Mbps | 890.24 Mbps | 43.3% | 137.5% | 162.0% | 97.6 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | 5GHz Wi-Fi | 359.58 Mbps | 156.59 Mbps | 235.2% | 178.0% | 244.0% | 98.6 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | 5GHz Wi-Fi | 807.74 Mbps | 900.51 Mbps | 93.4% | 237.1% | 256.0% | 147.2 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | 5GHz Wi-Fi | 837.81 Mbps | 685.38 Mbps | 84.1% | 212.4% | 262.6% | 145.7 MB |
| Wi-Fi Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 199.69 Mbps | 200.02 Mbps | 0.0% | 0.0% | 0.0% | 126.0 MB |
| Wi-Fi Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | 5GHz Wi-Fi | 199.77 Mbps | 200.08 Mbps | 0.1% | 0.0% | 1.0% | 125.3 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 130.63 Mbps (25.6% loss) | 114.89 Mbps (32.9% loss) | 287.8% | 266.8% | 349.0% | 137.0 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 149.8 Mbps (24.2% loss) | 103.22 Mbps (37.9% loss) | 270.2% | 83.5% | 302.0% | 137.0 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 167.05 Mbps (8.5% loss) | 115.18 Mbps (33.7% loss) | 373.7% | 305.4% | 387.0% | 130.5 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 159.2 Mbps (11.9% loss) | 125.99 Mbps (28.0% loss) | 377.1% | 328.7% | 386.0% | 133.4 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | 5GHz Wi-Fi | 199.83 Mbps | 200.37 Mbps | 258.2% | 240.2% | 355.0% | 103.4 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | 5GHz Wi-Fi | 198.94 Mbps (0.4% loss) | 200.36 Mbps | 158.7% | 117.8% | 210.0% | 102.0 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | 5GHz Wi-Fi | 169.68 Mbps | 200.19 Mbps | 410.0% | 380.6% | 434.0% | 116.1 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | 5GHz Wi-Fi | 199.57 Mbps | 187.22 Mbps (6.5% loss) | 391.4% | 374.7% | 420.0% | 138.5 MB |
| USB Baseline (No VPN) [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 710.53 Mbps | 371.32 Mbps | 0.0% | 0.0% | 0.0% | 179.8 MB |
| USB Baseline (No VPN) [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 757.01 Mbps | 441.76 Mbps | 0.0% | 0.0% | 0.0% | 179.2 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 648.05 Mbps | 303.58 Mbps | 46.0% | 56.9% | 85.0% | 96.8 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 317.75 Mbps | 61.12 Mbps | 94.2% | 49.5% | 196.0% | 98.2 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 619.41 Mbps | 178.84 Mbps | 58.7% | 87.7% | 140.0% | 180.1 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | USB 3.2 / 4.0 | 423.69 Mbps | 246.97 Mbps | 66.2% | 70.2% | 186.0% | 109.4 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | USB 3.2 / 4.0 | 656.02 Mbps | 351.8 Mbps | 35.1% | 49.2% | 69.0% | 96.6 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | USB 3.2 / 4.0 | 318.74 Mbps | 56.2 Mbps | 89.9% | 52.7% | 182.0% | 98.0 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | USB 3.2 / 4.0 | 617.76 Mbps | 373.61 Mbps | 38.4% | 85.4% | 118.0% | 179.7 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | USB 3.2 / 4.0 | 611.87 Mbps | 364.8 Mbps | 41.1% | 68.9% | 149.0% | 107.8 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | USB 3.2 / 4.0 | 552.68 Mbps | 294.48 Mbps | 36.3% | 40.9% | 102.0% | 109.7 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | USB 3.2 / 4.0 | 358.52 Mbps | 157.32 Mbps | 110.6% | 83.1% | 229.0% | 98.9 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | USB 3.2 / 4.0 | 275.39 Mbps | 386.9 Mbps | 73.0% | 132.7% | 203.0% | 180.3 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | USB 3.2 / 4.0 | 666.66 Mbps | 113.53 Mbps | 62.3% | 47.0% | 240.0% | 128.6 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | USB 3.2 / 4.0 | 687.44 Mbps | 379.15 Mbps | 36.2% | 57.5% | 87.0% | 97.2 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | USB 3.2 / 4.0 | 363.04 Mbps | 156.53 Mbps | 111.5% | 81.7% | 232.0% | 98.9 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | USB 3.2 / 4.0 | 679.28 Mbps | 407.48 Mbps | 52.0% | 100.0% | 137.0% | 182.6 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | USB 3.2 / 4.0 | 677.54 Mbps | 386.73 Mbps | 47.4% | 81.0% | 200.0% | 125.9 MB |
| USB Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 199.83 Mbps | 199.97 Mbps | 0.0% | 0.0% | 0.0% | 181.9 MB |
| USB Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 199.66 Mbps | 200.05 Mbps | 0.0% | 0.0% | 0.0% | 181.6 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 183.34 Mbps (0.3% loss) | 85.97 Mbps (29.5% loss) | 87.4% | 70.7% | 184.0% | 134.1 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 199.7 Mbps | 99.01 Mbps (38.7% loss) | 95.0% | 40.5% | 207.0% | 101.5 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 198.12 Mbps (0.7% loss) | 112.71 Mbps (23.0% loss) | 201.1% | 163.9% | 254.0% | 189.1 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | USB 3.2 / 4.0 | 197.35 Mbps | 120.22 Mbps (24.6% loss) | 195.3% | 157.4% | 244.0% | 163.9 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | USB 3.2 / 4.0 | 199.8 Mbps | 200.1 Mbps | 85.3% | 76.6% | 194.0% | 120.6 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | USB 3.2 / 4.0 | 199.79 Mbps | 200.19 Mbps | 77.3% | 59.7% | 202.0% | 102.4 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | USB 3.2 / 4.0 | 199.85 Mbps | 200.11 Mbps | 206.5% | 185.4% | 247.0% | 242.3 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | USB 3.2 / 4.0 | 199.62 Mbps | 194.46 Mbps (2.9% loss) | 165.1% | 164.9% | 303.0% | 117.3 MB |
| Loopback Baseline (No VPN) [Single Stream] | direct_none | 0 | On-Device Loopback | 20.46 Gbps | 20.46 Gbps | 0.0% | 0.0% | 0.0% | 99.9 MB |
| Loopback Baseline (No VPN) [P=8] | direct_none | 0 | On-Device Loopback | 18.41 Gbps | 18.41 Gbps | 0.2% | 0.2% | 2.0% | 99.9 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | On-Device Loopback | 20.97 Gbps | 20.97 Gbps | 2.3% | 2.3% | 18.5% | 103.2 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | On-Device Loopback | 20.8 Gbps | 20.8 Gbps | 1.0% | 1.0% | 7.4% | 97.4 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | On-Device Loopback | 21.14 Gbps | 21.14 Gbps | 1.0% | 1.0% | 10.0% | 99.1 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | On-Device Loopback | 20.98 Gbps | 20.98 Gbps | 2.5% | 2.5% | 14.0% | 122.9 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | On-Device Loopback | 18.46 Gbps | 18.46 Gbps | 1.2% | 1.2% | 13.7% | 95.6 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | On-Device Loopback | 18.18 Gbps | 18.18 Gbps | 0.1% | 0.1% | 1.0% | 97.3 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | On-Device Loopback | 18.12 Gbps | 18.12 Gbps | 2.1% | 2.1% | 33.3% | 100.4 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | On-Device Loopback | 18.43 Gbps | 18.43 Gbps | 0.9% | 0.9% | 16.1% | 103.3 MB |

#### Retained Connections vs Memory Growth (Idle Flows)

| Backend | Network | Conns Range | Baseline PSS | 1000 Conns PSS | Memory Slope |
| :--- | :---: | :---: | :---: | :---: | :---: |
| HEV | TCP | 0 -> 1000 | 98.8 MB | 115.1 MB | 16.69 KiB/conn |
| HEV | UDP | 0 -> 1000 | 100.6 MB | 117.9 MB | 17.71 KiB/conn |
| XRAY | TCP | 0 -> 1000 | 98.4 MB | 100.9 MB | 2.56 KiB/conn |
| XRAY | UDP | 0 -> 1000 | 98.9 MB | 103.6 MB | 4.81 KiB/conn |
| SING | TCP | 0 -> 1000 | 241.3 MB | 244.9 MB | 3.69 KiB/conn |
| SING | UDP | 0 -> 1000 | 192.6 MB | 194.1 MB | 1.54 KiB/conn |
| MIPS | TCP | 0 -> 1000 | 104.7 MB | 134.1 MB | -107.21 KiB/conn |
| MIPS | UDP | 0 -> 1000 | 124.6 MB | 158.3 MB | 34.51 KiB/conn |

### Round 2 Results

| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 817.99 Mbps | 783.93 Mbps | 0.6% | 0.0% | 6.0% | 0.0 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 833.06 Mbps | 893.18 Mbps | 0.0% | 0.0% | 0.0% | 0.0 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 803.21 Mbps | 493.5 Mbps | 91.1% | 95.3% | 137.0% | 118.7 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 252.95 Mbps | 79.37 Mbps | 167.6% | 100.8% | 196.1% | 98.2 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 795.5 Mbps | 148.78 Mbps | 105.3% | 148.7% | 196.0% | 110.8 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 778.62 Mbps | 236.22 Mbps | 157.3% | 200.7% | 232.8% | 130.1 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | 5GHz Wi-Fi | 801.45 Mbps | 787.28 Mbps | 93.8% | 107.8% | 127.0% | 112.4 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | 5GHz Wi-Fi | 256.03 Mbps | 61.95 Mbps | 169.4% | 109.1% | 214.4% | 112.2 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | 5GHz Wi-Fi | 794.11 Mbps | 543.94 Mbps | 87.3% | 139.7% | 162.8% | 124.4 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | 5GHz Wi-Fi | 795.02 Mbps | 786.34 Mbps | 102.8% | 198.3% | 216.0% | 131.4 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 827.01 Mbps | 289.45 Mbps | 76.4% | 79.0% | 146.0% | 98.6 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 364.97 Mbps | 156.06 Mbps | 234.0% | 176.2% | 241.0% | 99.4 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 619.72 Mbps | 885.37 Mbps | 138.7% | 407.8% | 428.0% | 119.4 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | 5GHz Wi-Fi | 825.6 Mbps | 114.29 Mbps | 141.6% | 167.6% | 281.1% | 147.2 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | 5GHz Wi-Fi | 809.54 Mbps | 898.35 Mbps | 52.6% | 129.9% | 162.0% | 129.9 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | 5GHz Wi-Fi | 355.85 Mbps | 182.01 Mbps | 235.0% | 186.8% | 248.0% | 129.2 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | 5GHz Wi-Fi | 820.65 Mbps | 897.1 Mbps | 60.0% | 236.2% | 246.0% | 149.9 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | 5GHz Wi-Fi | 831.63 Mbps | 691.35 Mbps | 78.2% | 211.0% | 292.0% | 148.9 MB |
| Wi-Fi Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 199.77 Mbps | 199.9 Mbps | 0.0% | 0.0% | 0.0% | 128.0 MB |
| Wi-Fi Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | 5GHz Wi-Fi | 199.87 Mbps | 200.06 Mbps | 0.0% | 0.0% | 0.0% | 128.0 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 157.84 Mbps (13.1% loss) | 122.82 Mbps (30.7% loss) | 307.3% | 261.8% | 349.0% | 139.0 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 133.22 Mbps (32.5% loss) | 105.15 Mbps (36.4% loss) | 262.1% | 80.4% | 298.0% | 139.3 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 160.58 Mbps (12.3% loss) | 121.32 Mbps (31.6% loss) | 375.7% | 307.0% | 387.0% | 131.9 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 171.65 Mbps (5.6% loss) | 124.38 Mbps (29.9% loss) | 378.2% | 328.4% | 393.0% | 136.7 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | 5GHz Wi-Fi | 199.69 Mbps | 200.23 Mbps | 253.6% | 239.3% | 353.0% | 105.0 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | 5GHz Wi-Fi | 199.27 Mbps (0.1% loss) | 200.18 Mbps | 157.7% | 117.8% | 212.0% | 103.6 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | 5GHz Wi-Fi | 169.73 Mbps (0.3% loss) | 200.0 Mbps | 404.7% | 373.9% | 438.0% | 114.7 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | 5GHz Wi-Fi | 199.63 Mbps | 187.98 Mbps (5.9% loss) | 394.0% | 358.1% | 418.0% | 131.7 MB |
| USB Baseline (No VPN) [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 659.85 Mbps | 387.05 Mbps | 0.0% | 0.0% | 0.0% | 196.7 MB |
| USB Baseline (No VPN) [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 736.16 Mbps | 442.06 Mbps | 0.0% | 0.0% | 0.0% | 196.6 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 632.62 Mbps | 297.31 Mbps | 46.4% | 54.7% | 86.0% | 96.5 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 573.5 Mbps | 55.15 Mbps | 98.0% | 60.0% | 202.0% | 97.9 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 640.62 Mbps | 168.57 Mbps | 54.1% | 85.1% | 139.0% | 197.6 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | USB 3.2 / 4.0 | 554.09 Mbps | 234.41 Mbps | 77.9% | 68.8% | 210.0% | 110.2 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | USB 3.2 / 4.0 | 638.04 Mbps | 355.81 Mbps | 22.9% | 34.2% | 70.0% | 110.8 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | USB 3.2 / 4.0 | 322.51 Mbps | 63.22 Mbps | 93.4% | 52.8% | 193.0% | 97.9 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | USB 3.2 / 4.0 | 621.32 Mbps | 351.83 Mbps | 36.9% | 85.1% | 111.0% | 197.3 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | USB 3.2 / 4.0 | 612.43 Mbps | 363.35 Mbps | 32.2% | 50.4% | 149.0% | 115.0 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | USB 3.2 / 4.0 | 675.07 Mbps | 290.27 Mbps | 50.0% | 62.0% | 97.0% | 97.8 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | USB 3.2 / 4.0 | 363.85 Mbps | 156.38 Mbps | 101.3% | 83.7% | 220.0% | 99.0 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | USB 3.2 / 4.0 | 259.69 Mbps | 403.91 Mbps | 71.5% | 137.7% | 226.0% | 197.6 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | USB 3.2 / 4.0 | 653.49 Mbps | 167.21 Mbps | 124.0% | 105.0% | 251.0% | 123.4 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | USB 3.2 / 4.0 | 757.63 Mbps | 434.75 Mbps | 0.8% | 0.0% | 4.0% | 95.2 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | USB 3.2 / 4.0 | 362.95 Mbps | 127.33 Mbps | 102.2% | 79.9% | 212.0% | 98.4 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | USB 3.2 / 4.0 | 685.6 Mbps | 355.91 Mbps | 50.0% | 92.6% | 137.0% | 143.3 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | USB 3.2 / 4.0 | 669.01 Mbps | 385.68 Mbps | 48.1% | 79.9% | 207.0% | 130.5 MB |
| USB Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 199.8 Mbps | 200.04 Mbps | 1.0% | 0.0% | 5.0% | 143.0 MB |
| USB Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 199.87 Mbps | 200.06 Mbps | 1.2% | 0.0% | 5.0% | 142.9 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 199.14 Mbps (0.3% loss) | 120.52 Mbps (18.0% loss) | 134.0% | 116.8% | 190.0% | 155.4 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 199.76 Mbps | 99.68 Mbps (37.1% loss) | 94.7% | 40.8% | 205.0% | 101.4 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 140.61 Mbps (11.4% loss) | 84.17 Mbps (28.6% loss) | 183.2% | 145.2% | 222.0% | 152.4 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | USB 3.2 / 4.0 | 112.53 Mbps (29.1% loss) | 74.54 Mbps (27.5% loss) | 99.2% | 101.3% | 226.0% | 121.4 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | USB 3.2 / 4.0 | 199.2 Mbps | 196.73 Mbps | 78.4% | 73.4% | 265.0% | 123.5 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | USB 3.2 / 4.0 | 199.57 Mbps | 200.09 Mbps | 77.2% | 57.8% | 204.0% | 101.8 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | USB 3.2 / 4.0 | 199.22 Mbps | 200.0 Mbps | 208.3% | 185.2% | 222.0% | 151.1 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | USB 3.2 / 4.0 | 199.81 Mbps | 169.78 Mbps (15.2% loss) | 124.4% | 123.2% | 261.0% | 125.4 MB |
| Loopback Baseline (No VPN) [Single Stream] | direct_none | 0 | On-Device Loopback | 21.01 Gbps | 21.01 Gbps | 0.0% | 0.0% | 0.0% | 107.3 MB |
| Loopback Baseline (No VPN) [P=8] | direct_none | 0 | On-Device Loopback | 18.36 Gbps | 18.36 Gbps | 0.0% | 0.0% | 0.0% | 107.3 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | On-Device Loopback | 20.65 Gbps | 20.65 Gbps | 0.8% | 0.8% | 7.4% | 108.1 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | On-Device Loopback | 20.62 Gbps | 20.62 Gbps | 0.1% | 0.1% | 1.0% | 108.1 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | On-Device Loopback | 20.63 Gbps | 20.63 Gbps | 0.8% | 0.8% | 7.4% | 108.8 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | On-Device Loopback | 20.86 Gbps | 20.86 Gbps | 1.4% | 1.4% | 10.7% | 113.9 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | On-Device Loopback | 18.31 Gbps | 18.31 Gbps | 4.9% | 4.9% | 20.6% | 95.8 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | On-Device Loopback | 17.88 Gbps | 17.88 Gbps | 0.2% | 0.2% | 3.0% | 96.8 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | On-Device Loopback | 18.45 Gbps | 18.45 Gbps | 0.3% | 0.3% | 3.7% | 97.5 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | On-Device Loopback | 18.53 Gbps | 18.53 Gbps | 0.4% | 0.4% | 3.5% | 101.3 MB |

#### Retained Connections vs Memory Growth (Idle Flows)

| Backend | Network | Conns Range | Baseline PSS | 1000 Conns PSS | Memory Slope |
| :--- | :---: | :---: | :---: | :---: | :---: |
| HEV | TCP | 0 -> 1000 | 120.9 MB | 120.9 MB | -6.25 KiB/conn |
| HEV | UDP | 0 -> 1000 | 98.7 MB | 117.0 MB | 18.74 KiB/conn |
| XRAY | TCP | 0 -> 1000 | 98.2 MB | 100.8 MB | 2.66 KiB/conn |
| XRAY | UDP | 0 -> 1000 | 98.8 MB | 103.7 MB | 5.02 KiB/conn |
| SING | TCP | 0 -> 1000 | 147.8 MB | 162.4 MB | 14.95 KiB/conn |
| SING | UDP | 0 -> 1000 | 162.1 MB | 172.6 MB | 10.75 KiB/conn |
| MIPS | TCP | 0 -> 1000 | 107.1 MB | 160.9 MB | 55.09 KiB/conn |
| MIPS | UDP | 0 -> 1000 | 123.4 MB | 155.1 MB | 32.46 KiB/conn |

### Round 3 Results

| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 637.69 Mbps | 726.16 Mbps | 0.0% | 0.1% | 1.0% | 98.4 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 695.23 Mbps | 578.06 Mbps | 0.0% | 0.0% | 0.0% | 98.4 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 678.84 Mbps | 381.11 Mbps | 74.6% | 86.7% | 119.0% | 101.4 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 256.17 Mbps | 56.93 Mbps | 170.3% | 108.8% | 217.8% | 101.4 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 673.95 Mbps | 154.44 Mbps | 121.7% | 149.4% | 181.0% | 110.5 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 666.77 Mbps | 242.61 Mbps | 145.9% | 204.2% | 265.0% | 127.9 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | 5GHz Wi-Fi | 676.92 Mbps | 700.22 Mbps | 59.9% | 109.6% | 133.0% | 97.5 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | 5GHz Wi-Fi | 565.43 Mbps | 57.66 Mbps | 163.6% | 118.3% | 217.7% | 98.4 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | 5GHz Wi-Fi | 679.07 Mbps | 569.1 Mbps | 107.1% | 146.3% | 165.0% | 110.9 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | 5GHz Wi-Fi | 672.29 Mbps | 707.29 Mbps | 87.7% | 196.5% | 209.0% | 127.8 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 617.67 Mbps | 275.85 Mbps | 95.0% | 80.2% | 145.0% | 114.8 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 353.62 Mbps | 159.73 Mbps | 231.2% | 161.5% | 251.1% | 114.8 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 606.39 Mbps | 778.48 Mbps | 110.2% | 406.2% | 449.0% | 153.8 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | 5GHz Wi-Fi | 688.44 Mbps | 126.02 Mbps | 126.1% | 180.1% | 253.0% | 145.9 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | 5GHz Wi-Fi | 699.63 Mbps | 769.93 Mbps | 50.3% | 138.0% | 165.0% | 128.2 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | 5GHz Wi-Fi | 369.44 Mbps | 181.65 Mbps | 238.7% | 183.0% | 245.0% | 127.8 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | 5GHz Wi-Fi | 699.65 Mbps | 789.91 Mbps | 61.6% | 239.2% | 260.0% | 155.3 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | 5GHz Wi-Fi | 713.41 Mbps | 712.41 Mbps | 77.1% | 221.7% | 260.1% | 0.0 MB |
| Wi-Fi Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 199.54 Mbps (0.1% loss) | 200.04 Mbps | 0.0% | 0.0% | 0.0% | 0.0 MB |
| Wi-Fi Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | 5GHz Wi-Fi | 199.82 Mbps | 200.08 Mbps | 0.0% | 0.0% | 0.0% | 0.0 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 158.5 Mbps (12.7% loss) | 120.38 Mbps (31.4% loss) | 306.0% | 262.1% | 351.0% | 135.6 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 199.41 Mbps (0.1% loss) | 158.52 Mbps (14.1% loss) | 144.3% | 102.5% | 251.0% | 120.6 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 116.36 Mbps (30.3% loss) | 112.85 Mbps (34.3% loss) | 367.2% | 308.2% | 377.0% | 107.8 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 160.3 Mbps (11.1% loss) | 121.85 Mbps (30.6% loss) | 377.3% | 326.9% | 392.0% | 129.9 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | 5GHz Wi-Fi | 172.65 Mbps | 200.19 Mbps | 246.4% | 249.7% | 383.0% | 117.5 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | 5GHz Wi-Fi | 199.73 Mbps | 200.22 Mbps | 131.6% | 152.1% | 210.0% | 117.7 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | 5GHz Wi-Fi | 199.66 Mbps | 200.56 Mbps | 390.9% | 370.7% | 418.0% | 129.6 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | 5GHz Wi-Fi | 199.88 Mbps | 185.23 Mbps (7.5% loss) | 413.9% | 382.0% | 442.0% | 134.5 MB |
| USB Baseline (No VPN) [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 647.61 Mbps | 369.84 Mbps | 0.0% | 0.0% | 0.0% | 110.4 MB |
| USB Baseline (No VPN) [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 753.39 Mbps | 437.06 Mbps | 0.6% | 0.0% | 3.0% | 108.0 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 631.08 Mbps | 295.06 Mbps | 48.3% | 57.0% | 88.0% | 96.8 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 445.62 Mbps | 58.13 Mbps | 96.1% | 54.75% | 199.0% | 98.05 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 594.6 Mbps | 160.81 Mbps | 56.0% | 83.2% | 143.0% | 110.5 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | USB 3.2 / 4.0 | 582.89 Mbps | 273.39 Mbps | 109.9% | 120.9% | 210.0% | 130.2 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | USB 3.2 / 4.0 | 622.73 Mbps | 351.4 Mbps | 35.6% | 49.0% | 68.0% | 109.7 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | USB 3.2 / 4.0 | 320.62 Mbps | 59.71 Mbps | 91.65% | 52.75% | 187.5% | 97.95 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | USB 3.2 / 4.0 | 625.19 Mbps | 364.19 Mbps | 37.2% | 84.4% | 111.0% | 111.3 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | USB 3.2 / 4.0 | 615.12 Mbps | 330.0 Mbps | 44.2% | 72.8% | 161.0% | 109.0 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | USB 3.2 / 4.0 | 705.16 Mbps | 302.76 Mbps | 49.3% | 59.7% | 99.0% | 98.3 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | USB 3.2 / 4.0 | 361.19 Mbps | 156.85 Mbps | 105.95% | 83.4% | 224.5% | 98.95 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | USB 3.2 / 4.0 | 221.29 Mbps | 363.65 Mbps | 63.1% | 125.1% | 187.0% | 115.6 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | USB 3.2 / 4.0 | 511.82 Mbps | 193.11 Mbps | 75.3% | 76.1% | 214.0% | 128.3 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | USB 3.2 / 4.0 | 675.46 Mbps | 421.72 Mbps | 22.2% | 39.7% | 61.0% | 129.3 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | USB 3.2 / 4.0 | 363.0 Mbps | 141.93 Mbps | 106.85% | 80.8% | 222.0% | 98.65 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | USB 3.2 / 4.0 | 682.42 Mbps | 394.1 Mbps | 48.8% | 98.8% | 148.0% | 152.1 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | USB 3.2 / 4.0 | 674.57 Mbps | 388.71 Mbps | 38.5% | 58.1% | 171.0% | 137.2 MB |
| USB Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 199.92 Mbps | 200.02 Mbps | 0.0% | 0.0% | 0.0% | 150.1 MB |
| USB Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 199.9 Mbps | 200.02 Mbps | 0.0% | 0.0% | 0.0% | 150.0 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 199.74 Mbps | 121.32 Mbps (16.8% loss) | 133.2% | 115.3% | 184.0% | 160.8 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 199.73 Mbps | 99.34 Mbps (37.9% loss) | 94.85% | 40.65% | 206.0% | 101.45 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 197.93 Mbps (0.7% loss) | 117.64 Mbps (22.5% loss) | 188.0% | 153.0% | 224.0% | 158.8 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | USB 3.2 / 4.0 | 150.68 Mbps (6.6% loss) | 72.89 Mbps (27.8% loss) | 125.7% | 97.8% | 228.0% | 117.4 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | USB 3.2 / 4.0 | 199.15 Mbps | 193.24 Mbps | 77.3% | 72.6% | 262.0% | 118.5 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | USB 3.2 / 4.0 | 199.68 Mbps | 200.14 Mbps | 77.25% | 58.75% | 203.0% | 102.1 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | USB 3.2 / 4.0 | 198.13 Mbps | 198.89 Mbps | 201.5% | 184.4% | 225.0% | 159.4 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | USB 3.2 / 4.0 | 199.73 Mbps | 182.43 Mbps (8.6% loss) | 127.5% | 122.9% | 285.0% | 120.7 MB |
| Loopback Baseline (No VPN) [Single Stream] | direct_none | 0 | On-Device Loopback | 20.51 Gbps | 20.51 Gbps | 0.0% | 0.0% | 0.0% | 103.6 MB |
| Loopback Baseline (No VPN) [P=8] | direct_none | 0 | On-Device Loopback | 18.26 Gbps | 18.26 Gbps | 0.2% | 0.2% | 2.0% | 103.6 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | On-Device Loopback | 20.67 Gbps | 20.67 Gbps | 1.55% | 1.55% | 12.95% | 105.65 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | On-Device Loopback | 20.72 Gbps | 20.72 Gbps | 0.55% | 0.55% | 4.2% | 102.75 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | On-Device Loopback | 20.74 Gbps | 20.74 Gbps | 0.9% | 0.9% | 8.7% | 103.95 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | On-Device Loopback | 21.16 Gbps | 21.16 Gbps | 1.95% | 1.95% | 12.35% | 118.4 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | On-Device Loopback | 18.45 Gbps | 18.45 Gbps | 3.05% | 3.05% | 17.15% | 95.7 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | On-Device Loopback | 18.74 Gbps | 18.74 Gbps | 0.15% | 0.15% | 2.0% | 97.05 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | On-Device Loopback | 18.46 Gbps | 18.46 Gbps | 1.2% | 1.2% | 18.5% | 98.95 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | On-Device Loopback | 18.49 Gbps | 18.49 Gbps | 0.65% | 0.65% | 9.8% | 102.3 MB |

#### Retained Connections vs Memory Growth (Idle Flows)

| Backend | Network | Conns Range | Baseline PSS | 1000 Conns PSS | Memory Slope |
| :--- | :---: | :---: | :---: | :---: | :---: |
| HEV | TCP | 0 -> 1000 | 120.1 MB | 137.3 MB | 17.61 KiB/conn |
| HEV | UDP | 0 -> 1000 | 120.8 MB | 137.4 MB | 17.00 KiB/conn |
| XRAY | TCP | 0 -> 1000 | 99.5 MB | 100.7 MB | 1.23 KiB/conn |
| XRAY | UDP | 0 -> 1000 | 99.8 MB | 99.7 MB | -0.10 KiB/conn |
| SING | TCP | 0 -> 1000 | 156.0 MB | 165.5 MB | 9.73 KiB/conn |
| SING | UDP | 0 -> 1000 | 166.6 MB | 169.9 MB | 3.38 KiB/conn |
| MIPS | TCP | 0 -> 1000 | 123.3 MB | 171.7 MB | 49.56 KiB/conn |
| MIPS | UDP | 0 -> 1000 | 124.7 MB | 153.9 MB | 29.90 KiB/conn |
