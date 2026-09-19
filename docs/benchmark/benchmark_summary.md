# SimpleXray Benchmark Summary
Generated at: 2026-09-19 13:09:13

### Round 1 Results

| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 386.05 Mbps | 380.8 Mbps | 0.0% | 0.0% | 0.0% | 99.5 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 418.34 Mbps | 425.36 Mbps | 0.0% | 0.0% | 0.0% | 99.4 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 395.82 Mbps | 304.79 Mbps | 33.2% | 45.2% | 78.0% | 100.7 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 379.66 Mbps | 60.5 Mbps | 50.3% | 61.9% | 171.0% | 100.7 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 397.2 Mbps | 167.23 Mbps | 32.7% | 82.3% | 141.0% | 109.1 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 391.45 Mbps | 247.86 Mbps | 65.1% | 106.5% | 202.0% | 0.0 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | 5GHz Wi-Fi | 393.12 Mbps | 373.35 Mbps | 17.7% | 47.6% | 78.0% | 97.1 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | 5GHz Wi-Fi | 246.97 Mbps | 61.96 Mbps | 83.2% | 55.4% | 177.0% | 97.9 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | 5GHz Wi-Fi | 394.03 Mbps | 279.41 Mbps | 27.8% | 51.5% | 103.0% | 111.2 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | 5GHz Wi-Fi | 397.32 Mbps | 397.26 Mbps | 35.6% | 80.8% | 128.0% | 0.0 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 420.59 Mbps | 300.59 Mbps | 25.9% | 50.2% | 97.0% | 98.5 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 349.04 Mbps | 175.93 Mbps | 116.8% | 91.9% | 238.0% | 99.3 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 387.99 Mbps | 453.56 Mbps | 43.0% | 132.1% | 232.0% | 0.0 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | 5GHz Wi-Fi | 422.61 Mbps | 138.55 Mbps | 45.4% | 90.9% | 174.0% | 152.6 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | 5GHz Wi-Fi | 424.31 Mbps | 450.68 Mbps | 17.5% | 57.6% | 96.0% | 97.6 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | 5GHz Wi-Fi | 359.05 Mbps | 182.37 Mbps | 118.3% | 93.6% | 245.0% | 98.3 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | 5GHz Wi-Fi | 422.73 Mbps | 454.03 Mbps | 27.2% | 81.8% | 114.0% | 0.0 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | 5GHz Wi-Fi | 419.63 Mbps | 381.63 Mbps | 41.6% | 84.6% | 159.0% | 185.7 MB |
| Wi-Fi Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 196.28 Mbps (1.7% loss) | 199.97 Mbps | 0.9% | 0.0% | 5.0% | 92.6 MB |
| Wi-Fi Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | 5GHz Wi-Fi | 199.75 Mbps | 200.02 Mbps | 0.0% | 0.2% | 2.0% | 91.6 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 151.46 Mbps (16.2% loss) | 127.36 Mbps (28.4% loss) | 155.3% | 131.5% | 180.0% | 104.7 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 127.15 Mbps (35.6% loss) | 105.33 Mbps (37.2% loss) | 129.9% | 42.3% | 212.0% | 103.5 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 159.59 Mbps (9.8% loss) | 122.41 Mbps (27.9% loss) | 181.2% | 152.7% | 222.0% | 117.2 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 144.88 Mbps (17.2% loss) | 121.69 Mbps (28.1% loss) | 187.4% | 159.7% | 230.0% | 134.1 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | 5GHz Wi-Fi | 199.69 Mbps | 200.23 Mbps | 128.8% | 109.2% | 197.0% | 106.5 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | 5GHz Wi-Fi | 196.39 Mbps (0.3% loss) | 199.76 Mbps | 80.3% | 56.0% | 220.0% | 102.3 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | 5GHz Wi-Fi | 199.66 Mbps | 200.03 Mbps | 195.4% | 144.5% | 238.0% | 115.1 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | 5GHz Wi-Fi | 166.03 Mbps | 199.83 Mbps (0.3% loss) | 201.9% | 188.2% | 230.0% | 130.1 MB |
| USB Baseline (No VPN) [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 702.5 Mbps | 381.32 Mbps | 0.8% | 0.0% | 4.0% | 92.3 MB |
| USB Baseline (No VPN) [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 768.2 Mbps | 422.16 Mbps | 0.0% | 0.3% | 3.0% | 91.8 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 642.11 Mbps | 306.04 Mbps | 48.2% | 61.1% | 82.0% | 100.5 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 317.75 Mbps | 61.12 Mbps | 94.2% | 49.5% | 196.0% | 98.2 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 654.91 Mbps | 183.49 Mbps | 55.0% | 84.4% | 143.0% | 111.6 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | USB 3.2 / 4.0 | 621.59 Mbps | 228.25 Mbps | 110.6% | 103.0% | 200.0% | 132.6 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | USB 3.2 / 4.0 | 646.64 Mbps | 359.29 Mbps | 39.1% | 54.3% | 70.0% | 97.4 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | USB 3.2 / 4.0 | 318.74 Mbps | 56.2 Mbps | 89.9% | 52.7% | 182.0% | 98.0 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | USB 3.2 / 4.0 | 646.48 Mbps | 367.48 Mbps | 35.8% | 85.8% | 118.0% | 111.9 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | USB 3.2 / 4.0 | 632.57 Mbps | 362.13 Mbps | 58.1% | 101.5% | 150.0% | 127.8 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | USB 3.2 / 4.0 | 703.66 Mbps | 301.85 Mbps | 48.6% | 65.0% | 98.0% | 98.2 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | USB 3.2 / 4.0 | 358.52 Mbps | 157.32 Mbps | 110.6% | 83.1% | 229.0% | 98.9 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | USB 3.2 / 4.0 | 218.33 Mbps | 397.99 Mbps | 65.9% | 134.9% | 227.0% | 117.5 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | USB 3.2 / 4.0 | 663.89 Mbps | 156.01 Mbps | 119.8% | 100.1% | 251.0% | 150.8 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | USB 3.2 / 4.0 | 688.48 Mbps | 364.76 Mbps | 38.3% | 61.4% | 90.0% | 98.8 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | USB 3.2 / 4.0 | 363.04 Mbps | 156.53 Mbps | 111.5% | 81.7% | 232.0% | 98.9 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | USB 3.2 / 4.0 | 686.21 Mbps | 372.31 Mbps | 41.4% | 93.7% | 122.0% | 156.4 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | USB 3.2 / 4.0 | 685.29 Mbps | 393.28 Mbps | 66.0% | 113.1% | 165.0% | 198.8 MB |
| USB Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 199.81 Mbps | 200.01 Mbps | 0.8% | 0.0% | 5.0% | 92.0 MB |
| USB Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 199.94 Mbps | 199.99 Mbps | 0.0% | 0.2% | 2.0% | 91.5 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 199.74 Mbps | 153.26 Mbps (12.4% loss) | 158.9% | 128.8% | 193.0% | 106.2 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 199.7 Mbps | 99.01 Mbps (38.7% loss) | 95.0% | 40.5% | 207.0% | 101.5 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 198.09 Mbps (0.9% loss) | 117.06 Mbps (29.5% loss) | 190.2% | 150.2% | 236.0% | 111.5 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | USB 3.2 / 4.0 | 148.51 Mbps (15.0% loss) | 71.11 Mbps (42.4% loss) | 189.4% | 146.4% | 225.0% | 124.0 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | USB 3.2 / 4.0 | 199.88 Mbps | 200.72 Mbps | 135.5% | 122.4% | 203.0% | 104.3 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | USB 3.2 / 4.0 | 199.79 Mbps | 200.19 Mbps | 77.3% | 59.7% | 202.0% | 102.4 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | USB 3.2 / 4.0 | 199.92 Mbps | 200.5 Mbps | 202.2% | 192.5% | 238.0% | 111.7 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | USB 3.2 / 4.0 | 185.51 Mbps (7.0% loss) | 199.29 Mbps | 187.2% | 195.6% | 227.0% | 128.0 MB |
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
| HEV | TCP | 0 -> 1000 | 95.6 MB | 113.7 MB | 18.53 KiB/conn |
| HEV | UDP | 0 -> 1000 | 98.2 MB | 115.5 MB | 17.71 KiB/conn |
| XRAY | TCP | 0 -> 1000 | 98.4 MB | 100.9 MB | 2.56 KiB/conn |
| XRAY | UDP | 0 -> 1000 | 98.9 MB | 103.6 MB | 4.81 KiB/conn |
| SING | TCP | 0 -> 1000 | 103.3 MB | 149.3 MB | 47.10 KiB/conn |
| SING | UDP | 0 -> 1000 | 121.4 MB | 127.9 MB | 6.66 KiB/conn |
| MIPS | TCP | 0 -> 1000 | 103.6 MB | 155.5 MB | 53.15 KiB/conn |
| MIPS | UDP | 0 -> 1000 | 99.9 MB | 163.9 MB | 65.54 KiB/conn |

### Round 2 Results

| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 396.48 Mbps | 312.23 Mbps | 0.4% | 0.3% | 4.0% | 100.7 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 350.63 Mbps | 365.86 Mbps | 0.0% | 0.0% | 0.0% | 100.6 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 311.02 Mbps | 249.64 Mbps | 21.8% | 44.2% | 69.0% | 103.8 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 246.56 Mbps | 66.16 Mbps | 80.4% | 55.2% | 173.0% | 103.9 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 323.57 Mbps | 173.51 Mbps | 24.1% | 82.6% | 144.0% | 109.4 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 321.29 Mbps | 235.9 Mbps | 45.0% | 101.4% | 200.0% | 136.6 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | 5GHz Wi-Fi | 350.53 Mbps | 349.45 Mbps | 16.9% | 46.0% | 71.0% | 97.2 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | 5GHz Wi-Fi | 249.79 Mbps | 64.06 Mbps | 84.7% | 55.3% | 196.0% | 98.0 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | 5GHz Wi-Fi | 375.18 Mbps | 275.65 Mbps | 26.3% | 58.9% | 92.5% | 110.8 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | 5GHz Wi-Fi | 289.05 Mbps | 309.92 Mbps | 32.7% | 71.5% | 122.0% | 126.5 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 311.54 Mbps | 298.18 Mbps | 21.8% | 53.8% | 91.0% | 98.4 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 312.72 Mbps | 133.84 Mbps | 103.1% | 79.0% | 239.0% | 98.8 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 300.67 Mbps | 405.02 Mbps | 53.7% | 123.6% | 220.0% | 155.3 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | 5GHz Wi-Fi | 344.3 Mbps | 144.33 Mbps | 51.8% | 95.6% | 185.0% | 190.0 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | 5GHz Wi-Fi | 331.39 Mbps | 371.73 Mbps | 14.5% | 51.3% | 88.0% | 191.4 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | 5GHz Wi-Fi | 288.25 Mbps | 178.14 Mbps | 87.5% | 92.9% | 244.0% | 190.4 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | 5GHz Wi-Fi | 270.95 Mbps | 347.2 Mbps | 24.6% | 70.6% | 118.0% | 191.0 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | 5GHz Wi-Fi | 277.31 Mbps | 372.36 Mbps | 30.0% | 78.6% | 200.0% | 155.6 MB |
| Wi-Fi Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 194.86 Mbps (1.4% loss) | 199.97 Mbps | 0.8% | 0.1% | 4.0% | 92.9 MB |
| Wi-Fi Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | 5GHz Wi-Fi | 199.17 Mbps | 199.98 Mbps | 0.0% | 0.3% | 3.0% | 91.8 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 145.53 Mbps (19.2% loss) | 120.15 Mbps (30.8% loss) | 153.6% | 130.9% | 185.0% | 105.8 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 168.95 Mbps (14.7% loss) | 155.26 Mbps (14.8% loss) | 117.9% | 48.8% | 223.0% | 102.7 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 159.59 Mbps (9.8% loss) | 122.41 Mbps (27.9% loss) | 181.2% | 152.7% | 222.0% | 117.2 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 144.88 Mbps (17.2% loss) | 121.69 Mbps (28.1% loss) | 187.4% | 159.7% | 230.0% | 134.1 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | 5GHz Wi-Fi | 174.09 Mbps | 200.02 Mbps | 135.2% | 119.2% | 220.0% | 114.8 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | 5GHz Wi-Fi | 198.43 Mbps (0.5% loss) | 199.46 Mbps | 79.8% | 55.5% | 214.0% | 110.8 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | 5GHz Wi-Fi | 199.66 Mbps | 200.03 Mbps | 195.4% | 144.5% | 238.0% | 115.1 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | 5GHz Wi-Fi | 166.03 Mbps | 199.83 Mbps (0.3% loss) | 201.9% | 188.2% | 230.0% | 130.1 MB |
| USB Baseline (No VPN) [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 681.51 Mbps | 381.64 Mbps | 0.8% | 0.0% | 4.0% | 92.1 MB |
| USB Baseline (No VPN) [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 748.75 Mbps | 427.67 Mbps | 0.0% | 0.2% | 2.0% | 91.5 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 634.67 Mbps | 299.98 Mbps | 48.7% | 59.0% | 84.0% | 100.4 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 573.5 Mbps | 55.15 Mbps | 98.0% | 60.0% | 202.0% | 97.9 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 632.86 Mbps | 171.01 Mbps | 54.2% | 83.4% | 145.0% | 111.3 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | USB 3.2 / 4.0 | 595.64 Mbps | 236.55 Mbps | 113.1% | 101.5% | 210.0% | 127.1 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | USB 3.2 / 4.0 | 648.14 Mbps | 357.31 Mbps | 38.2% | 54.8% | 70.0% | 97.3 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | USB 3.2 / 4.0 | 322.51 Mbps | 63.22 Mbps | 93.4% | 52.8% | 193.0% | 97.9 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | USB 3.2 / 4.0 | 333.84 Mbps | 356.27 Mbps | 27.3% | 83.5% | 114.0% | 111.4 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | USB 3.2 / 4.0 | 353.2 Mbps | 285.81 Mbps | 42.3% | 94.4% | 150.0% | 124.9 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | USB 3.2 / 4.0 | 325.38 Mbps | 302.05 Mbps | 35.9% | 64.5% | 89.0% | 98.2 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | USB 3.2 / 4.0 | 363.85 Mbps | 156.38 Mbps | 101.3% | 83.7% | 220.0% | 99.0 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | USB 3.2 / 4.0 | 223.21 Mbps | 393.51 Mbps | 62.5% | 135.7% | 218.0% | 106.1 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | USB 3.2 / 4.0 | 344.44 Mbps | 161.88 Mbps | 94.3% | 98.4% | 192.0% | 0.0 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | USB 3.2 / 4.0 | 339.46 Mbps | 360.44 Mbps | 29.4% | 62.0% | 94.0% | 97.7 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | USB 3.2 / 4.0 | 362.95 Mbps | 127.33 Mbps | 102.2% | 79.9% | 212.0% | 98.4 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | USB 3.2 / 4.0 | 325.38 Mbps | 391.13 Mbps | 33.1% | 98.2% | 151.0% | 148.0 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | USB 3.2 / 4.0 | 335.02 Mbps | 392.83 Mbps | 45.7% | 111.7% | 203.0% | 183.3 MB |
| USB Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 199.96 Mbps | 200.02 Mbps | 0.7% | 0.0% | 3.7% | 92.9 MB |
| USB Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 199.86 Mbps | 200.0 Mbps | 0.0% | 0.2% | 2.0% | 91.8 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 199.4 Mbps | 120.99 Mbps (29.1% loss) | 159.6% | 130.9% | 191.0% | 106.6 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 199.76 Mbps | 99.68 Mbps (37.1% loss) | 94.7% | 40.8% | 205.0% | 101.4 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 198.09 Mbps (0.9% loss) | 117.06 Mbps (29.5% loss) | 190.2% | 150.2% | 236.0% | 111.5 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | USB 3.2 / 4.0 | 148.51 Mbps (15.0% loss) | 71.11 Mbps (42.4% loss) | 189.4% | 146.4% | 225.0% | 124.0 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | USB 3.2 / 4.0 | 199.92 Mbps | 200.13 Mbps | 135.1% | 123.0% | 204.0% | 104.9 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | USB 3.2 / 4.0 | 199.57 Mbps | 200.09 Mbps | 77.2% | 57.8% | 204.0% | 101.8 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | USB 3.2 / 4.0 | 199.92 Mbps | 200.5 Mbps | 202.2% | 192.5% | 238.0% | 111.7 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | USB 3.2 / 4.0 | 185.51 Mbps (7.0% loss) | 199.29 Mbps | 187.2% | 195.6% | 227.0% | 128.0 MB |
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
| HEV | TCP | 0 -> 1000 | 98.3 MB | 114.1 MB | 16.18 KiB/conn |
| HEV | UDP | 0 -> 1000 | 120.1 MB | 114.9 MB | -5.33 KiB/conn |
| XRAY | TCP | 0 -> 1000 | 98.2 MB | 100.8 MB | 2.66 KiB/conn |
| XRAY | UDP | 0 -> 1000 | 98.8 MB | 103.7 MB | 5.02 KiB/conn |
| SING | TCP | 0 -> 1000 | 102.9 MB | 151.4 MB | 49.66 KiB/conn |
| SING | UDP | 0 -> 1000 | 99.2 MB | 123.4 MB | 24.78 KiB/conn |
| MIPS | TCP | 0 -> 1000 | 123.7 MB | 155.7 MB | 32.77 KiB/conn |
| MIPS | UDP | 0 -> 1000 | 101.5 MB | 160.8 MB | 60.72 KiB/conn |

### Round 3 Results

| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 391.26 Mbps | 346.51 Mbps | 0.4% | 0.3% | 4.0% | 100.1 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 384.49 Mbps | 395.61 Mbps | 0.0% | 0.0% | 0.0% | 100.0 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 353.42 Mbps | 277.22 Mbps | 27.5% | 44.7% | 73.5% | 102.25 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 313.11 Mbps | 63.33 Mbps | 65.35% | 58.55% | 172.0% | 102.3 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 360.38 Mbps | 170.37 Mbps | 28.4% | 82.45% | 142.5% | 109.25 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 356.37 Mbps | 241.88 Mbps | 55.05% | 103.95% | 201.0% | 136.6 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | 5GHz Wi-Fi | 371.82 Mbps | 361.4 Mbps | 17.3% | 46.8% | 74.5% | 97.15 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | 5GHz Wi-Fi | 248.38 Mbps | 63.01 Mbps | 83.95% | 55.35% | 186.5% | 97.95 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | 5GHz Wi-Fi | 384.61 Mbps | 277.53 Mbps | 27.05% | 55.2% | 97.75% | 111.0 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | 5GHz Wi-Fi | 343.19 Mbps | 353.59 Mbps | 34.15% | 76.15% | 125.0% | 126.5 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 366.06 Mbps | 299.38 Mbps | 23.85% | 52.0% | 94.0% | 98.45 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 330.88 Mbps | 154.88 Mbps | 109.95% | 85.45% | 238.5% | 99.05 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 344.33 Mbps | 429.29 Mbps | 48.35% | 127.85% | 226.0% | 155.3 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | 5GHz Wi-Fi | 383.46 Mbps | 141.44 Mbps | 48.6% | 93.25% | 179.5% | 171.3 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | 5GHz Wi-Fi | 377.85 Mbps | 411.21 Mbps | 16.0% | 54.45% | 92.0% | 144.5 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | 5GHz Wi-Fi | 323.65 Mbps | 180.25 Mbps | 102.9% | 93.25% | 244.5% | 144.35 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | 5GHz Wi-Fi | 346.84 Mbps | 400.62 Mbps | 25.9% | 76.2% | 116.0% | 191.0 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | 5GHz Wi-Fi | 348.47 Mbps | 377.0 Mbps | 35.8% | 81.6% | 179.5% | 170.65 MB |
| Wi-Fi Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 195.57 Mbps (1.6% loss) | 199.97 Mbps | 0.85% | 0.1% | 4.5% | 92.75 MB |
| Wi-Fi Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | 5GHz Wi-Fi | 199.46 Mbps | 200.0 Mbps | 0.0% | 0.25% | 2.5% | 91.7 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 148.5 Mbps (17.7% loss) | 123.75 Mbps (29.6% loss) | 154.45% | 131.2% | 182.5% | 105.25 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 148.05 Mbps (25.2% loss) | 130.29 Mbps (26.0% loss) | 123.9% | 45.55% | 217.5% | 103.1 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 159.59 Mbps (9.8% loss) | 122.41 Mbps (27.9% loss) | 181.2% | 152.7% | 222.0% | 117.2 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 144.88 Mbps (17.1% loss) | 121.69 Mbps (28.1% loss) | 187.4% | 159.7% | 230.0% | 134.1 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | 5GHz Wi-Fi | 186.89 Mbps | 200.12 Mbps | 132.0% | 114.2% | 208.5% | 110.65 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | 5GHz Wi-Fi | 197.41 Mbps (0.4% loss) | 199.61 Mbps | 80.05% | 55.75% | 217.0% | 106.55 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | 5GHz Wi-Fi | 199.66 Mbps | 200.03 Mbps | 195.4% | 144.5% | 238.0% | 115.1 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | 5GHz Wi-Fi | 166.03 Mbps | 199.83 Mbps (0.2% loss) | 201.9% | 188.2% | 230.0% | 130.1 MB |
| USB Baseline (No VPN) [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 692.0 Mbps | 381.48 Mbps | 0.8% | 0.0% | 4.0% | 92.2 MB |
| USB Baseline (No VPN) [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 758.48 Mbps | 424.92 Mbps | 0.0% | 0.25% | 2.5% | 91.65 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 638.39 Mbps | 303.01 Mbps | 48.45% | 60.05% | 83.0% | 100.45 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 445.62 Mbps | 58.13 Mbps | 96.1% | 54.75% | 199.0% | 98.05 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 643.88 Mbps | 177.25 Mbps | 54.6% | 83.9% | 144.0% | 111.45 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | USB 3.2 / 4.0 | 608.62 Mbps | 232.4 Mbps | 111.85% | 102.25% | 205.0% | 129.85 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | USB 3.2 / 4.0 | 647.39 Mbps | 358.3 Mbps | 38.65% | 54.55% | 70.0% | 97.35 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | USB 3.2 / 4.0 | 320.62 Mbps | 59.71 Mbps | 91.65% | 52.75% | 187.5% | 97.95 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | USB 3.2 / 4.0 | 490.16 Mbps | 361.88 Mbps | 31.55% | 84.65% | 116.0% | 111.65 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | USB 3.2 / 4.0 | 492.88 Mbps | 323.97 Mbps | 50.2% | 97.95% | 150.0% | 126.35 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | USB 3.2 / 4.0 | 514.52 Mbps | 301.95 Mbps | 42.25% | 64.75% | 93.5% | 98.2 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | USB 3.2 / 4.0 | 361.19 Mbps | 156.85 Mbps | 105.95% | 83.4% | 224.5% | 98.95 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | USB 3.2 / 4.0 | 220.77 Mbps | 395.75 Mbps | 64.2% | 135.3% | 222.5% | 111.8 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | USB 3.2 / 4.0 | 504.16 Mbps | 158.94 Mbps | 107.05% | 99.25% | 221.5% | 150.8 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | USB 3.2 / 4.0 | 513.97 Mbps | 362.6 Mbps | 33.85% | 61.7% | 92.0% | 98.25 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | USB 3.2 / 4.0 | 363.0 Mbps | 141.93 Mbps | 106.85% | 80.8% | 222.0% | 98.65 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | USB 3.2 / 4.0 | 505.8 Mbps | 381.72 Mbps | 37.25% | 95.95% | 136.5% | 152.2 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | USB 3.2 / 4.0 | 510.15 Mbps | 393.05 Mbps | 55.85% | 112.4% | 184.0% | 191.05 MB |
| USB Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 199.88 Mbps | 200.01 Mbps | 0.75% | 0.0% | 4.35% | 92.45 MB |
| USB Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 199.9 Mbps | 200.0 Mbps | 0.0% | 0.2% | 2.0% | 91.65 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 199.57 Mbps | 137.12 Mbps (20.8% loss) | 159.25% | 129.85% | 192.0% | 106.4 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 199.73 Mbps | 99.34 Mbps (37.9% loss) | 94.85% | 40.65% | 206.0% | 101.45 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 198.09 Mbps (0.9% loss) | 117.06 Mbps (29.5% loss) | 190.2% | 150.2% | 236.0% | 111.5 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | USB 3.2 / 4.0 | 148.51 Mbps (15.0% loss) | 71.11 Mbps (42.4% loss) | 189.4% | 146.4% | 225.0% | 124.0 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | USB 3.2 / 4.0 | 199.9 Mbps | 200.43 Mbps | 135.3% | 122.7% | 203.5% | 104.6 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | USB 3.2 / 4.0 | 199.68 Mbps | 200.14 Mbps | 77.25% | 58.75% | 203.0% | 102.1 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | USB 3.2 / 4.0 | 199.92 Mbps | 200.5 Mbps | 202.2% | 192.5% | 238.0% | 111.7 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | USB 3.2 / 4.0 | 185.51 Mbps (7.0% loss) | 199.29 Mbps | 187.2% | 195.6% | 227.0% | 128.0 MB |
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
| HEV | TCP | 0 -> 1000 | 97.6 MB | 114.1 MB | 16.90 KiB/conn |
| HEV | UDP | 0 -> 1000 | 99.2 MB | 115.9 MB | 17.10 KiB/conn |
| XRAY | TCP | 0 -> 1000 | 99.5 MB | 100.7 MB | 1.23 KiB/conn |
| XRAY | UDP | 0 -> 1000 | 99.8 MB | 99.7 MB | -0.10 KiB/conn |
| SING | TCP | 0 -> 1000 | 104.6 MB | 150.2 MB | 46.69 KiB/conn |
| SING | UDP | 0 -> 1000 | 100.9 MB | 122.1 MB | 21.71 KiB/conn |
| MIPS | TCP | 0 -> 1000 | 104.1 MB | 156.0 MB | 53.15 KiB/conn |
| MIPS | UDP | 0 -> 1000 | 102.0 MB | 162.7 MB | 62.16 KiB/conn |
