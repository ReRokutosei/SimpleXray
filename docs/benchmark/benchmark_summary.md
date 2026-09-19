# SimpleXray Benchmark Summary
Merged at: 2026-09-18T20:37:50.541307

### Round 1 Results

| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 225.46 Mbps | 292.06 Mbps | 0.0% | 0.0% | 0.0% | 182.7 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 287.19 Mbps | 307.49 Mbps | 0.0% | 0.0% | 0.0% | 177.8 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 298.92 Mbps | 257.47 Mbps | 21.6% | 49.1% | 71.0% | 97.9 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 379.66 Mbps | 60.5 Mbps | 50.3% | 61.9% | 171.0% | 100.7 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 355.41 Mbps | 173.38 Mbps | 34.1% | 83.7% | 139.0% | 179.1 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 341.5 Mbps | 231.26 Mbps | 40.2% | 67.2% | 186.0% | 110.8 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | 5GHz Wi-Fi | 328.51 Mbps | 290.18 Mbps | 14.4% | 27.9% | 69.0% | 111.6 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | 5GHz Wi-Fi | 246.97 Mbps | 61.96 Mbps | 83.2% | 55.4% | 177.0% | 97.9 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | 5GHz Wi-Fi | 329.74 Mbps | 319.95 Mbps | 28.8% | 61.6% | 91.0% | 179.6 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | 5GHz Wi-Fi | 206.48 Mbps | 288.51 Mbps | 14.6% | 37.2% | 104.0% | 115.0 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 351.57 Mbps | 259.99 Mbps | 29.7% | 51.6% | 92.0% | 98.4 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 349.04 Mbps | 175.93 Mbps | 116.8% | 91.9% | 238.0% | 99.3 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 358.21 Mbps | 366.61 Mbps | 45.8% | 119.8% | 212.0% | 180.0 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | 5GHz Wi-Fi | 336.87 Mbps | 159.56 Mbps | 45.3% | 65.5% | 210.0% | 125.5 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | 5GHz Wi-Fi | 376.8 Mbps | 337.77 Mbps | 17.6% | 51.3% | 90.0% | 97.2 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | 5GHz Wi-Fi | 359.05 Mbps | 182.37 Mbps | 118.3% | 93.6% | 245.0% | 98.3 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | 5GHz Wi-Fi | 302.61 Mbps | 390.26 Mbps | 37.0% | 75.1% | 151.0% | 180.2 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | 5GHz Wi-Fi | 319.13 Mbps | 335.15 Mbps | 36.7% | 60.2% | 170.0% | 127.6 MB |
| Wi-Fi Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 196.79 Mbps (1.4% loss) | 200.14 Mbps | 0.0% | 0.0% | 0.0% | 178.4 MB |
| Wi-Fi Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | 5GHz Wi-Fi | 199.52 Mbps | 200.08 Mbps | 0.0% | 0.0% | 0.0% | 178.3 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 149.93 Mbps (6.0% loss) | 125.23 Mbps (19.8% loss) | 135.3% | 114.9% | 185.0% | 109.0 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 127.15 Mbps (35.6% loss) | 105.33 Mbps (37.2% loss) | 129.9% | 42.3% | 212.0% | 103.5 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 159.68 Mbps (1.8% loss) | 119.56 Mbps (22.4% loss) | 189.0% | 164.0% | 248.0% | 184.5 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 146.31 Mbps (9.1% loss) | 119.8 Mbps (23.2% loss) | 120.9% | 106.9% | 223.0% | 113.8 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | 5GHz Wi-Fi | 198.72 Mbps | 199.95 Mbps | 69.2% | 33.8% | 262.0% | 119.5 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | 5GHz Wi-Fi | 196.39 Mbps (0.3% loss) | 199.76 Mbps | 80.3% | 56.0% | 220.0% | 102.3 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | 5GHz Wi-Fi | 159.4 Mbps | 199.96 Mbps | 175.6% | 184.4% | 250.0% | 188.4 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | 5GHz Wi-Fi | 138.83 Mbps (13.0% loss) | 170.79 Mbps (0.3% loss) | 97.3% | 120.4% | 336.0% | 124.1 MB |
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
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 334.42 Mbps | 206.93 Mbps | 0.0% | 0.0% | 0.0% | 192.2 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 351.71 Mbps | 427.08 Mbps | 0.0% | 0.0% | 0.0% | 191.7 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 290.28 Mbps | 261.66 Mbps | 24.4% | 43.6% | 70.0% | 156.9 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 246.56 Mbps | 66.16 Mbps | 80.4% | 55.2% | 173.0% | 103.9 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 368.18 Mbps | 157.67 Mbps | 36.4% | 80.6% | 139.0% | 193.0 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 331.93 Mbps | 229.79 Mbps | 38.8% | 67.6% | 189.0% | 109.5 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | 5GHz Wi-Fi | 359.29 Mbps | 327.92 Mbps | 0.5% | 0.0% | 5.0% | 95.6 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | 5GHz Wi-Fi | 249.79 Mbps | 64.06 Mbps | 84.7% | 55.3% | 196.0% | 98.0 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | 5GHz Wi-Fi | 370.88 Mbps | 359.15 Mbps | 31.3% | 66.8% | 89.0% | 193.2 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | 5GHz Wi-Fi | 389.54 Mbps | 369.01 Mbps | 19.9% | 38.1% | 139.0% | 107.9 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 415.63 Mbps | 302.44 Mbps | 15.1% | 30.4% | 91.0% | 109.4 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 312.72 Mbps | 133.84 Mbps | 103.1% | 79.0% | 239.0% | 98.8 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 355.59 Mbps | 435.58 Mbps | 58.5% | 131.0% | 229.0% | 197.3 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | 5GHz Wi-Fi | 403.44 Mbps | 153.19 Mbps | 26.8% | 39.4% | 214.0% | 130.1 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | 5GHz Wi-Fi | 368.38 Mbps | 419.86 Mbps | 19.1% | 52.1% | 91.0% | 97.1 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | 5GHz Wi-Fi | 288.25 Mbps | 178.14 Mbps | 87.5% | 92.9% | 244.0% | 190.4 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | 5GHz Wi-Fi | 391.93 Mbps | 428.45 Mbps | 39.1% | 81.5% | 133.0% | 197.2 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | 5GHz Wi-Fi | 410.92 Mbps | 417.9 Mbps | 32.9% | 63.0% | 207.0% | 129.3 MB |
| Wi-Fi Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 196.48 Mbps (1.5% loss) | 199.89 Mbps | 0.0% | 0.0% | 0.0% | 195.9 MB |
| Wi-Fi Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | 5GHz Wi-Fi | 199.56 Mbps | 200.09 Mbps | 0.0% | 0.0% | 0.0% | 195.6 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 157.7 Mbps (5.2% loss) | 130.19 Mbps (21.3% loss) | 135.4% | 115.9% | 188.0% | 106.2 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 168.95 Mbps (14.7% loss) | 155.26 Mbps (14.8% loss) | 117.9% | 48.8% | 223.0% | 102.7 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 89.34 Mbps (32.4% loss) | 108.5 Mbps (26.5% loss) | 154.2% | 162.5% | 250.0% | 205.4 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 117.59 Mbps (20.4% loss) | 102.28 Mbps (28.7% loss) | 122.1% | 104.1% | 226.0% | 114.1 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | 5GHz Wi-Fi | 199.32 Mbps | 199.67 Mbps | 81.6% | 66.4% | 192.0% | 103.4 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | 5GHz Wi-Fi | 198.43 Mbps (0.5% loss) | 199.46 Mbps | 79.8% | 55.5% | 214.0% | 110.8 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | 5GHz Wi-Fi | 163.1 Mbps (0.1% loss) | 199.99 Mbps | 206.2% | 185.6% | 247.0% | 206.3 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | 5GHz Wi-Fi | 169.7 Mbps | 190.81 Mbps (0.1% loss) | 157.0% | 149.4% | 299.0% | 118.0 MB |
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
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 369.4 Mbps | 336.09 Mbps | 0.0% | 0.0% | 0.0% | 169.0 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 381.54 Mbps | 418.91 Mbps | 0.0% | 0.0% | 0.0% | 0.0 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 370.8 Mbps | 263.76 Mbps | 28.0% | 44.4% | 65.0% | 159.1 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 313.11 Mbps | 63.33 Mbps | 65.35% | 58.55% | 172.0% | 102.3 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 376.29 Mbps | 192.68 Mbps | 40.0% | 82.5% | 140.0% | 169.8 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 292.46 Mbps | 228.57 Mbps | 38.7% | 65.3% | 196.0% | 109.8 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | 5GHz Wi-Fi | 270.21 Mbps | 327.92 Mbps | 0.4% | 0.0% | 4.0% | 96.0 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | 5GHz Wi-Fi | 248.38 Mbps | 63.01 Mbps | 83.95% | 55.35% | 186.5% | 97.95 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | 5GHz Wi-Fi | 365.37 Mbps | 342.8 Mbps | 26.7% | 64.2% | 90.0% | 169.3 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | 5GHz Wi-Fi | 376.05 Mbps | 327.92 Mbps | 21.8% | 36.9% | 139.0% | 107.8 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 394.72 Mbps | 304.43 Mbps | 27.8% | 53.5% | 94.0% | 118.3 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 330.88 Mbps | 154.88 Mbps | 109.95% | 85.45% | 238.5% | 99.05 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 369.98 Mbps | 405.39 Mbps | 59.0% | 122.0% | 204.0% | 172.3 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | 5GHz Wi-Fi | 357.66 Mbps | 143.96 Mbps | 43.2% | 64.6% | 200.0% | 126.4 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | 5GHz Wi-Fi | 366.82 Mbps | 373.73 Mbps | 20.8% | 50.1% | 101.0% | 97.5 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | 5GHz Wi-Fi | 323.65 Mbps | 180.25 Mbps | 102.9% | 93.25% | 244.5% | 144.35 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | 5GHz Wi-Fi | 382.67 Mbps | 409.48 Mbps | 36.8% | 78.6% | 100.0% | 172.9 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | 5GHz Wi-Fi | 362.15 Mbps | 340.4 Mbps | 32.7% | 57.1% | 207.0% | 130.7 MB |
| Wi-Fi Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 196.68 Mbps (1.4% loss) | 199.98 Mbps | 0.0% | 0.0% | 0.0% | 0.0 MB |
| Wi-Fi Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | 5GHz Wi-Fi | 195.1 Mbps | 200.03 Mbps | 1.2% | 0.2% | 5.0% | 91.8 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 145.01 Mbps (10.5% loss) | 129.75 Mbps (19.7% loss) | 135.0% | 116.3% | 183.0% | 107.3 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 148.05 Mbps (25.2% loss) | 130.29 Mbps (26.0% loss) | 123.9% | 45.55% | 217.5% | 103.1 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 163.83 Mbps (2.9% loss) | 136.93 Mbps (14.6% loss) | 181.7% | 157.4% | 225.0% | 114.2 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 149.37 Mbps (10.2% loss) | 132.98 Mbps (16.6% loss) | 120.6% | 105.2% | 225.0% | 111.6 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | 5GHz Wi-Fi | 198.82 Mbps (0.1% loss) | 193.18 Mbps | 53.7% | 44.8% | 174.0% | 122.4 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | 5GHz Wi-Fi | 197.41 Mbps (0.4% loss) | 199.61 Mbps | 80.05% | 55.75% | 217.0% | 106.55 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | 5GHz Wi-Fi | 132.81 Mbps (0.9% loss) | 199.98 Mbps | 139.4% | 179.2% | 216.0% | 116.2 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | 5GHz Wi-Fi | 142.01 Mbps (28.8% loss) | 194.75 Mbps (2.5% loss) | 74.0% | 117.4% | 315.0% | 125.9 MB |
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
