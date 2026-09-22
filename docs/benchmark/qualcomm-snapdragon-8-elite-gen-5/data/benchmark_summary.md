### Round 1 Results

| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 613.85 Mbps | 724.74 Mbps | 0.9% | 0.4% | 3.5% | 159.4 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 663.34 Mbps | 779.41 Mbps | 0.4% | 0.9% | 3.5% | 158.0 MB |
| Wi-Fi Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 199.97 Mbps | 200.0 Mbps (0.1% loss) | 0.4% | 0.2% | 1.0% | 158.1 MB |
| Wi-Fi Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | 5GHz Wi-Fi | 24.96 Mbps | 25.0 Mbps | 0.2% | 0.2% | 1.0% | 158.1 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 615.78 Mbps | 776.68 Mbps | 28.1% | 40.4% | 52.0% | 163.0 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | 5GHz Wi-Fi | 623.4 Mbps | 745.92 Mbps | 19.8% | 40.4% | 51.0% | 163.7 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 618.62 Mbps | 777.37 Mbps | 36.1% | 52.1% | 89.0% | 164.2 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | 5GHz Wi-Fi | 611.39 Mbps | 813.1 Mbps | 23.3% | 57.0% | 77.0% | 165.4 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 199.97 Mbps | 200.0 Mbps (0.8% loss) | 79.8% | 86.6% | 118.0% | 164.5 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | 5GHz Wi-Fi | 25.0 Mbps | 25.0 Mbps | 44.9% | 28.1% | 59.0% | 164.1 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 612.89 Mbps | 487.94 Mbps | 49.5% | 66.4% | 81.4% | 152.3 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | 5GHz Wi-Fi | 643.01 Mbps | 817.08 Mbps | 31.2% | 61.6% | 74.0% | 130.4 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 632.29 Mbps | 779.42 Mbps | 71.5% | 109.1% | 114.7% | 130.8 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | 5GHz Wi-Fi | 610.63 Mbps | 575.37 Mbps | 48.3% | 75.0% | 117.7% | 130.6 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 199.97 Mbps | 200.0 Mbps (0.7% loss) | 137.1% | 116.7% | 148.0% | 130.9 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | 5GHz Wi-Fi | 25.0 Mbps | 25.0 Mbps | 70.0% | 41.3% | 74.0% | 130.7 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 616.18 Mbps | 717.77 Mbps | 70.7% | 95.2% | 118.5% | 152.6 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | 5GHz Wi-Fi | 631.55 Mbps | 788.93 Mbps | 45.9% | 82.2% | 96.0% | 130.0 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | 5GHz Wi-Fi | 589.16 Mbps | 814.09 Mbps | 105.6% | 136.6% | 259.2% | 130.3 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | 5GHz Wi-Fi | 612.71 Mbps | 787.76 Mbps | 72.0% | 125.3% | 235.2% | 130.4 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 199.96 Mbps | 200.0 Mbps (0.9% loss) | 115.5% | 128.2% | 140.0% | 130.3 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | 5GHz Wi-Fi | 25.0 Mbps | 25.0 Mbps | 76.1% | 46.0% | 79.0% | 130.3 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 610.56 Mbps | 797.17 Mbps | 94.4% | 119.9% | 136.1% | 182.0 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | 5GHz Wi-Fi | 631.15 Mbps | 727.32 Mbps | 91.5% | 111.9% | 125.0% | 159.3 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 627.5 Mbps | 732.6 Mbps | 158.7% | 135.5% | 380.7% | 159.9 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | 5GHz Wi-Fi | 598.75 Mbps | 617.26 Mbps | 151.7% | 98.3% | 363.7% | 160.0 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 199.97 Mbps | 200.0 Mbps (0.7% loss) | 97.8% | 46.4% | 124.0% | 160.1 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | 5GHz Wi-Fi | 25.0 Mbps | 25.0 Mbps | 38.8% | 14.1% | 49.0% | 160.3 MB |
| USB Baseline (No VPN) [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 1479.9 Mbps | 1596.03 Mbps | 0.0% | 0.2% | 1.0% | 158.4 MB |
| USB Baseline (No VPN) [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 1502.32 Mbps | 1167.45 Mbps | 0.2% | 0.2% | 1.0% | 158.1 MB |
| USB Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 199.97 Mbps | 200.0 Mbps | 0.4% | 0.4% | 2.0% | 158.1 MB |
| USB Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 24.99 Mbps | 25.0 Mbps | 0.4% | 0.2% | 1.0% | 158.0 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 1610.66 Mbps | 1507.91 Mbps | 36.9% | 73.4% | 100.0% | 162.7 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | USB 3.2 / 4.0 | 1561.44 Mbps | 1534.22 Mbps | 27.0% | 56.3% | 88.0% | 163.0 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | USB 3.2 / 4.0 | 1692.21 Mbps | 1790.34 Mbps | 45.3% | 85.7% | 112.0% | 163.7 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | USB 3.2 / 4.0 | 1673.94 Mbps | 1763.73 Mbps | 31.0% | 65.9% | 89.0% | 164.5 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 199.98 Mbps | 200.0 Mbps | 76.9% | 79.8% | 109.0% | 163.9 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | USB 3.2 / 4.0 | 25.0 Mbps | 25.0 Mbps | 41.5% | 34.4% | 52.0% | 163.5 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 1812.32 Mbps | 1796.58 Mbps | 61.0% | 117.5% | 123.0% | 152.5 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | USB 3.2 / 4.0 | 1619.07 Mbps | 1693.07 Mbps | 33.9% | 59.0% | 66.0% | 130.2 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | USB 3.2 / 4.0 | 1792.64 Mbps | 1778.58 Mbps | 78.5% | 148.4% | 158.8% | 130.5 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | USB 3.2 / 4.0 | 1849.77 Mbps | 1785.33 Mbps | 51.3% | 106.5% | 129.5% | 130.4 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 199.97 Mbps | 200.0 Mbps | 112.8% | 102.1% | 122.1% | 130.8 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | USB 3.2 / 4.0 | 25.0 Mbps | 25.0 Mbps | 72.0% | 52.3% | 85.1% | 131.0 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | USB 3.2 / 4.0 | 1766.29 Mbps | 1797.34 Mbps | 118.8% | 156.1% | 188.2% | 152.5 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | USB 3.2 / 4.0 | 1616.72 Mbps | 1585.92 Mbps | 52.7% | 113.9% | 128.0% | 129.8 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | USB 3.2 / 4.0 | 1809.26 Mbps | 1794.7 Mbps | 155.7% | 224.3% | 302.7% | 129.9 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | USB 3.2 / 4.0 | 1738.16 Mbps | 1795.59 Mbps | 87.6% | 136.9% | 247.9% | 130.1 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | USB 3.2 / 4.0 | 199.96 Mbps | 200.0 Mbps | 114.5% | 129.6% | 136.0% | 130.0 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | USB 3.2 / 4.0 | 25.0 Mbps | 25.0 Mbps | 73.5% | 61.2% | 78.4% | 130.2 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 1850.44 Mbps | 1053.93 Mbps | 205.4% | 161.6% | 240.1% | 183.2 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | USB 3.2 / 4.0 | 1571.94 Mbps | 1029.64 Mbps | 147.4% | 151.2% | 164.0% | 160.6 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | USB 3.2 / 4.0 | 1911.99 Mbps | 1804.63 Mbps | 242.6% | 267.4% | 381.0% | 160.6 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | USB 3.2 / 4.0 | 1902.99 Mbps | 1795.15 Mbps | 239.2% | 262.6% | 370.0% | 160.8 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 199.97 Mbps | 200.0 Mbps | 89.7% | 45.0% | 106.0% | 160.4 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | USB 3.2 / 4.0 | 25.0 Mbps | 25.0 Mbps | 38.4% | 14.7% | 46.0% | 161.0 MB |
| Loopback Baseline (No VPN) [Single Stream] | direct_none | 0 | On-Device Loopback | 22.31 Gbps | 22.31 Gbps | 0.7% | 0.7% | 3.7% | 158.9 MB |
| Loopback Baseline (No VPN) [P=8] | direct_none | 0 | On-Device Loopback | 28.39 Gbps | 28.39 Gbps | 0.2% | 0.2% | 1.0% | 158.3 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | On-Device Loopback | 20.11 Gbps | 20.11 Gbps | 7.2% | 7.2% | 14.8% | 163.1 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | On-Device Loopback | 19.8 Gbps | 19.8 Gbps | 8.3% | 8.3% | 18.5% | 151.8 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | On-Device Loopback | 20.03 Gbps | 20.03 Gbps | 7.5% | 7.5% | 18.5% | 152.2 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | On-Device Loopback | 19.84 Gbps | 19.84 Gbps | 7.6% | 7.6% | 14.8% | 182.7 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | On-Device Loopback | 52.3 Gbps | 52.3 Gbps | 6.2% | 6.2% | 11.1% | 185.1 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | On-Device Loopback | 52.58 Gbps | 52.58 Gbps | 4.2% | 4.2% | 6.0% | 153.5 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | On-Device Loopback | 37.97 Gbps | 37.97 Gbps | 8.1% | 8.1% | 18.5% | 153.1 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | On-Device Loopback | 52.39 Gbps | 52.39 Gbps | 7.2% | 7.2% | 14.8% | 183.3 MB |

#### Retained Connections vs Memory Growth (Idle Flows)

| Backend | Network | Conns Range | Baseline PSS | 1000 Conns PSS | Memory Slope |
| :--- | :---: | :---: | :---: | :---: | :---: |
| HEV | TCP | 0 -> 1000 | 161.8 MB | 175.5 MB | 14.03 KiB/conn |
| HEV | UDP | 0 -> 1000 | 163.4 MB | 181.5 MB | 18.53 KiB/conn |
| SING | TCP | 0 -> 1000 | 134.5 MB | 132.7 MB | -1.84 KiB/conn |
| SING | UDP | 0 -> 1000 | 133.5 MB | 134.3 MB | 0.82 KiB/conn |
| MIPS | TCP | 0 -> 1000 | 134.3 MB | 132.8 MB | -1.54 KiB/conn |
| MIPS | UDP | 0 -> 1000 | 133.6 MB | 134.1 MB | 0.51 KiB/conn |
| XRAY | TCP | 0 -> 1000 | 164.2 MB | 162.3 MB | -1.95 KiB/conn |
| XRAY | UDP | 0 -> 1000 | 164.0 MB | 163.2 MB | -0.82 KiB/conn |


---

### Round 2 Results

| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 640.14 Mbps | 798.27 Mbps | 0.4% | 0.2% | 1.0% | 162.5 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 663.11 Mbps | 808.94 Mbps | 0.4% | 0.9% | 3.5% | 161.5 MB |
| Wi-Fi Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 199.97 Mbps | 200.0 Mbps | 0.4% | 0.4% | 1.0% | 161.4 MB |
| Wi-Fi Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | 5GHz Wi-Fi | 25.0 Mbps | 25.0 Mbps | 0.4% | 0.2% | 1.0% | 161.5 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 579.8 Mbps | 764.52 Mbps | 25.1% | 39.3% | 52.0% | 164.3 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | 5GHz Wi-Fi | 659.55 Mbps | 806.72 Mbps | 19.1% | 38.9% | 52.0% | 164.6 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 609.63 Mbps | 804.57 Mbps | 37.1% | 55.3% | 93.0% | 165.7 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | 5GHz Wi-Fi | 643.01 Mbps | 811.96 Mbps | 24.8% | 52.3% | 69.0% | 165.7 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 199.98 Mbps (0.1% loss) | 200.0 Mbps (0.9% loss) | 80.2% | 82.3% | 114.0% | 165.9 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | 5GHz Wi-Fi | 25.0 Mbps | 25.0 Mbps | 45.3% | 27.5% | 57.0% | 165.1 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 573.26 Mbps | 763.42 Mbps | 45.0% | 74.7% | 77.7% | 152.4 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | 5GHz Wi-Fi | 653.64 Mbps | 810.44 Mbps | 32.7% | 60.1% | 66.6% | 130.8 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 618.31 Mbps | 755.07 Mbps | 71.3% | 108.7% | 122.0% | 130.7 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | 5GHz Wi-Fi | 597.23 Mbps | 783.55 Mbps | 53.2% | 93.1% | 159.0% | 130.9 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 199.96 Mbps (1.0% loss) | 200.0 Mbps (0.7% loss) | 127.6% | 111.4% | 137.0% | 131.1 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | 5GHz Wi-Fi | 25.0 Mbps | 25.0 Mbps | 70.3% | 43.2% | 73.0% | 131.2 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 616.91 Mbps | 694.11 Mbps | 74.6% | 95.1% | 131.9% | 152.0 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | 5GHz Wi-Fi | 564.81 Mbps | 702.16 Mbps | 44.9% | 93.3% | 110.0% | 130.1 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | 5GHz Wi-Fi | 596.52 Mbps | 767.1 Mbps | 110.0% | 133.5% | 281.2% | 130.7 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | 5GHz Wi-Fi | 598.74 Mbps | 512.61 Mbps | 70.0% | 121.7% | 196.2% | 130.7 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 199.96 Mbps | 200.0 Mbps (0.7% loss) | 115.9% | 132.6% | 137.0% | 131.0 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | 5GHz Wi-Fi | 25.0 Mbps | 25.0 Mbps | 74.3% | 46.6% | 81.0% | 130.7 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 599.05 Mbps | 720.08 Mbps | 92.1% | 111.5% | 132.7% | 183.4 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | 5GHz Wi-Fi | 596.9 Mbps | 760.37 Mbps | 91.4% | 122.2% | 142.0% | 160.1 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 613.46 Mbps | 774.55 Mbps | 144.4% | 127.9% | 332.1% | 160.9 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | 5GHz Wi-Fi | 614.24 Mbps | 737.74 Mbps | 144.7% | 122.6% | 314.7% | 160.6 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 199.97 Mbps | 200.0 Mbps (0.5% loss) | 93.6% | 45.3% | 112.0% | 161.3 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | 5GHz Wi-Fi | 25.0 Mbps | 25.0 Mbps | 40.1% | 14.5% | 47.0% | 160.8 MB |
| USB Baseline (No VPN) [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 1505.34 Mbps | 1535.83 Mbps | 0.2% | 0.4% | 1.0% | 159.3 MB |
| USB Baseline (No VPN) [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 1492.73 Mbps | 1202.2 Mbps | 0.2% | 0.2% | 1.0% | 158.9 MB |
| USB Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 199.97 Mbps | 200.0 Mbps | 0.4% | 0.9% | 3.5% | 158.9 MB |
| USB Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 25.0 Mbps | 25.0 Mbps | 0.4% | 0.2% | 1.0% | 158.9 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 1662.2 Mbps | 1796.08 Mbps | 39.9% | 52.8% | 69.0% | 163.2 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | USB 3.2 / 4.0 | 1515.66 Mbps | 1494.81 Mbps | 22.4% | 57.3% | 86.0% | 163.5 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | USB 3.2 / 4.0 | 1721.36 Mbps | 1786.33 Mbps | 46.6% | 89.3% | 115.0% | 164.7 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | USB 3.2 / 4.0 | 1629.07 Mbps | 1786.32 Mbps | 26.9% | 66.2% | 86.0% | 165.4 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 199.99 Mbps | 200.0 Mbps | 76.9% | 83.4% | 113.0% | 164.9 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | USB 3.2 / 4.0 | 25.0 Mbps | 25.0 Mbps | 44.3% | 37.3% | 52.0% | 164.7 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 1763.82 Mbps | 1799.8 Mbps | 55.9% | 123.4% | 129.0% | 152.2 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | USB 3.2 / 4.0 | 1664.0 Mbps | 1727.84 Mbps | 35.1% | 58.7% | 66.6% | 130.1 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | USB 3.2 / 4.0 | 1848.83 Mbps | 1784.99 Mbps | 81.5% | 146.2% | 153.0% | 130.5 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | USB 3.2 / 4.0 | 1854.08 Mbps | 1776.02 Mbps | 49.0% | 102.6% | 117.8% | 130.5 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 199.97 Mbps | 200.0 Mbps | 114.2% | 104.5% | 130.0% | 130.3 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | USB 3.2 / 4.0 | 25.0 Mbps | 25.0 Mbps | 70.3% | 53.2% | 81.4% | 131.0 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | USB 3.2 / 4.0 | 1830.78 Mbps | 1569.16 Mbps | 124.1% | 167.5% | 195.5% | 152.9 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | USB 3.2 / 4.0 | 1660.24 Mbps | 1583.09 Mbps | 55.0% | 120.7% | 130.0% | 130.3 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | USB 3.2 / 4.0 | 1867.52 Mbps | 1786.84 Mbps | 161.6% | 213.3% | 314.1% | 130.8 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | USB 3.2 / 4.0 | 1769.72 Mbps | 1792.61 Mbps | 82.2% | 136.9% | 209.8% | 130.8 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | USB 3.2 / 4.0 | 199.97 Mbps | 200.0 Mbps | 111.6% | 130.1% | 134.0% | 130.8 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | USB 3.2 / 4.0 | 25.0 Mbps | 25.0 Mbps | 73.5% | 62.8% | 81.4% | 130.9 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 1805.93 Mbps | 1014.81 Mbps | 196.3% | 155.9% | 225.4% | 182.7 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | USB 3.2 / 4.0 | 1865.3 Mbps | 1013.74 Mbps | 203.2% | 153.2% | 240.0% | 159.6 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | USB 3.2 / 4.0 | 1910.35 Mbps | 1778.22 Mbps | 243.9% | 249.0% | 373.4% | 160.3 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | USB 3.2 / 4.0 | 1899.18 Mbps | 1803.37 Mbps | 262.8% | 262.9% | 407.0% | 160.1 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 199.96 Mbps | 200.0 Mbps | 92.9% | 44.3% | 112.0% | 160.3 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | USB 3.2 / 4.0 | 25.0 Mbps | 25.0 Mbps | 38.1% | 14.3% | 45.0% | 160.5 MB |
| Loopback Baseline (No VPN) [Single Stream] | direct_none | 0 | On-Device Loopback | 22.18 Gbps | 22.18 Gbps | 0.2% | 0.2% | 1.0% | 158.5 MB |
| Loopback Baseline (No VPN) [P=8] | direct_none | 0 | On-Device Loopback | 30.94 Gbps | 30.94 Gbps | 0.4% | 0.4% | 1.0% | 158.0 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | On-Device Loopback | 19.91 Gbps | 19.91 Gbps | 7.3% | 7.3% | 18.5% | 162.4 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | On-Device Loopback | 20.37 Gbps | 20.37 Gbps | 7.0% | 7.0% | 14.8% | 152.0 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | On-Device Loopback | 19.68 Gbps | 19.68 Gbps | 7.4% | 7.4% | 14.8% | 153.0 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | On-Device Loopback | 19.43 Gbps | 19.43 Gbps | 8.2% | 8.2% | 22.2% | 182.3 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | On-Device Loopback | 38.37 Gbps | 38.37 Gbps | 7.3% | 7.3% | 18.5% | 185.1 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | On-Device Loopback | 50.32 Gbps | 50.32 Gbps | 6.0% | 6.0% | 11.1% | 152.0 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | On-Device Loopback | 28.82 Gbps | 28.82 Gbps | 6.8% | 6.8% | 14.8% | 152.6 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | On-Device Loopback | 51.48 Gbps | 51.48 Gbps | 7.9% | 7.9% | 18.5% | 182.8 MB |

#### Retained Connections vs Memory Growth (Idle Flows)

| Backend | Network | Conns Range | Baseline PSS | 1000 Conns PSS | Memory Slope |
| :--- | :---: | :---: | :---: | :---: | :---: |
| HEV | TCP | 0 -> 1000 | 161.5 MB | 175.5 MB | 14.34 KiB/conn |
| HEV | UDP | 0 -> 1000 | 162.6 MB | 181.3 MB | 19.15 KiB/conn |
| SING | TCP | 0 -> 1000 | 133.7 MB | 132.9 MB | -0.82 KiB/conn |
| SING | UDP | 0 -> 1000 | 133.5 MB | 134.5 MB | 1.02 KiB/conn |
| MIPS | TCP | 0 -> 1000 | 134.4 MB | 132.9 MB | -1.54 KiB/conn |
| MIPS | UDP | 0 -> 1000 | 134.0 MB | 134.5 MB | 0.51 KiB/conn |
| XRAY | TCP | 0 -> 1000 | 164.5 MB | 162.6 MB | -1.95 KiB/conn |
| XRAY | UDP | 0 -> 1000 | 164.0 MB | 163.2 MB | -0.82 KiB/conn |


---

### Round 3 Results

| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 660.35 Mbps | 746.17 Mbps | 0.2% | 0.4% | 1.0% | 162.2 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 648.53 Mbps | 835.25 Mbps | 0.4% | 0.4% | 1.0% | 161.6 MB |
| Wi-Fi Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 199.96 Mbps | 200.0 Mbps (0.2% loss) | 0.4% | 0.4% | 1.0% | 161.7 MB |
| Wi-Fi Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | 5GHz Wi-Fi | 25.0 Mbps | 25.0 Mbps | 0.2% | 0.4% | 1.0% | 161.7 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 616.71 Mbps | 601.15 Mbps | 31.4% | 28.8% | 51.0% | 164.2 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | 5GHz Wi-Fi | 628.32 Mbps | 734.08 Mbps | 21.3% | 36.3% | 48.0% | 164.8 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 604.8 Mbps | 809.2 Mbps | 37.5% | 54.5% | 81.0% | 165.5 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | 5GHz Wi-Fi | 620.24 Mbps | 820.94 Mbps | 20.7% | 49.4% | 62.0% | 166.3 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 199.97 Mbps | 200.0 Mbps (0.5% loss) | 85.3% | 81.4% | 113.0% | 165.7 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | 5GHz Wi-Fi | 25.0 Mbps | 25.0 Mbps | 40.8% | 29.4% | 58.0% | 165.1 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 619.15 Mbps | 790.46 Mbps | 48.9% | 74.0% | 78.0% | 152.3 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | 5GHz Wi-Fi | 634.86 Mbps | 765.75 Mbps | 31.3% | 66.4% | 70.0% | 130.3 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 636.24 Mbps | 825.78 Mbps | 76.3% | 105.8% | 118.4% | 130.7 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | 5GHz Wi-Fi | 614.04 Mbps | 829.1 Mbps | 48.2% | 89.6% | 142.2% | 130.9 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 199.97 Mbps (0.1% loss) | 200.0 Mbps (0.8% loss) | 128.8% | 113.4% | 137.0% | 130.9 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | 5GHz Wi-Fi | 25.0 Mbps | 25.0 Mbps | 67.3% | 44.5% | 72.0% | 130.6 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 593.46 Mbps | 740.58 Mbps | 65.9% | 95.5% | 104.0% | 152.3 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | 5GHz Wi-Fi | 631.61 Mbps | 774.61 Mbps | 48.7% | 75.5% | 83.0% | 130.1 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | 5GHz Wi-Fi | 611.78 Mbps | 835.49 Mbps | 107.8% | 134.0% | 274.1% | 130.6 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | 5GHz Wi-Fi | 632.85 Mbps | 822.37 Mbps | 72.1% | 115.4% | 221.3% | 130.9 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 199.97 Mbps (0.2% loss) | 200.0 Mbps (0.8% loss) | 113.6% | 132.9% | 144.0% | 130.9 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | 5GHz Wi-Fi | 25.0 Mbps | 25.0 Mbps | 71.1% | 51.7% | 75.0% | 130.8 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 599.6 Mbps | 614.73 Mbps | 91.0% | 95.7% | 139.0% | 182.9 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | 5GHz Wi-Fi | 630.76 Mbps | 769.49 Mbps | 102.8% | 115.7% | 140.0% | 159.9 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 619.31 Mbps | 761.41 Mbps | 149.6% | 123.9% | 355.0% | 160.6 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | 5GHz Wi-Fi | 610.69 Mbps | 834.02 Mbps | 153.6% | 120.9% | 362.8% | 160.4 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 199.97 Mbps | 200.0 Mbps (0.6% loss) | 94.3% | 40.9% | 121.0% | 160.7 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | 5GHz Wi-Fi | 25.0 Mbps | 25.0 Mbps | 40.0% | 14.7% | 49.0% | 160.5 MB |
| USB Baseline (No VPN) [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 1435.6 Mbps | 1380.08 Mbps | 0.0% | 0.8% | 2.0% | 158.8 MB |
| USB Baseline (No VPN) [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 1447.35 Mbps | 1177.87 Mbps | 0.4% | 0.2% | 1.0% | 158.5 MB |
| USB Baseline (No VPN) [UDP] [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 199.97 Mbps | 200.0 Mbps | 0.2% | 0.6% | 2.0% | 158.5 MB |
| USB Baseline (No VPN) [UDP] [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 25.0 Mbps | 25.0 Mbps | 0.2% | 0.2% | 1.0% | 158.6 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 1591.42 Mbps | 1470.66 Mbps | 37.0% | 67.0% | 98.0% | 163.0 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | USB 3.2 / 4.0 | 1495.92 Mbps | 1568.68 Mbps | 24.7% | 43.0% | 69.0% | 163.3 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | USB 3.2 / 4.0 | 1676.93 Mbps | 1786.29 Mbps | 50.2% | 87.6% | 121.0% | 163.8 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | USB 3.2 / 4.0 | 1627.2 Mbps | 1776.24 Mbps | 28.4% | 68.8% | 88.0% | 164.8 MB |
| Hev (MTU 1500) [UDP] [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 199.97 Mbps | 200.0 Mbps | 78.7% | 79.5% | 104.0% | 164.5 MB |
| Hev (MTU 1500) [UDP] [P=8] | hev | 1500 | USB 3.2 / 4.0 | 25.0 Mbps | 25.0 Mbps | 44.2% | 35.2% | 52.0% | 164.2 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 1669.79 Mbps | 1800.36 Mbps | 57.9% | 118.2% | 121.0% | 152.9 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | USB 3.2 / 4.0 | 1551.31 Mbps | 1474.65 Mbps | 32.2% | 84.9% | 98.0% | 130.5 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | USB 3.2 / 4.0 | 1755.22 Mbps | 1757.77 Mbps | 84.4% | 136.1% | 140.6% | 130.9 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | USB 3.2 / 4.0 | 1631.08 Mbps | 1763.16 Mbps | 48.8% | 105.2% | 133.2% | 131.0 MB |
| SingTUN (MTU 1500) [UDP] [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 199.97 Mbps | 200.0 Mbps | 112.4% | 101.1% | 120.0% | 131.1 MB |
| SingTUN (MTU 1500) [UDP] [P=8] | sing | 1500 | USB 3.2 / 4.0 | 25.0 Mbps | 25.0 Mbps | 68.6% | 52.4% | 76.0% | 131.6 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | USB 3.2 / 4.0 | 1714.91 Mbps | 1485.14 Mbps | 117.3% | 153.9% | 184.3% | 152.4 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | USB 3.2 / 4.0 | 1588.95 Mbps | 1790.66 Mbps | 58.7% | 90.2% | 96.2% | 130.2 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | USB 3.2 / 4.0 | 1773.85 Mbps | 1795.36 Mbps | 162.3% | 257.5% | 329.5% | 130.7 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | USB 3.2 / 4.0 | 1800.02 Mbps | 1783.29 Mbps | 91.5% | 134.7% | 269.6% | 130.3 MB |
| MipsTUN (MTU 1500) [UDP] [Single Stream] | mips | 1500 | USB 3.2 / 4.0 | 199.97 Mbps | 200.0 Mbps | 115.0% | 130.5% | 134.0% | 130.3 MB |
| MipsTUN (MTU 1500) [UDP] [P=8] | mips | 1500 | USB 3.2 / 4.0 | 25.0 Mbps | 25.0 Mbps | 72.1% | 59.6% | 77.7% | 130.8 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 1727.36 Mbps | 997.81 Mbps | 186.0% | 153.7% | 223.0% | 182.9 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | USB 3.2 / 4.0 | 1749.17 Mbps | 1009.57 Mbps | 186.2% | 151.6% | 196.0% | 159.5 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | USB 3.2 / 4.0 | 1857.52 Mbps | 1805.68 Mbps | 286.0% | 248.8% | 422.0% | 160.2 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | USB 3.2 / 4.0 | 1846.29 Mbps | 1803.78 Mbps | 255.7% | 254.7% | 428.7% | 160.4 MB |
| Xray TUN (MTU 1500) [UDP] [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 199.97 Mbps | 200.0 Mbps | 93.9% | 45.0% | 118.0% | 160.5 MB |
| Xray TUN (MTU 1500) [UDP] [P=8] | xray | 1500 | USB 3.2 / 4.0 | 25.0 Mbps | 25.0 Mbps | 39.9% | 15.4% | 47.0% | 160.8 MB |
| Loopback Baseline (No VPN) [Single Stream] | direct_none | 0 | On-Device Loopback | 24.0 Gbps | 24.0 Gbps | 0.2% | 0.2% | 1.0% | 158.9 MB |
| Loopback Baseline (No VPN) [P=8] | direct_none | 0 | On-Device Loopback | 30.13 Gbps | 30.13 Gbps | 0.2% | 0.2% | 1.0% | 158.3 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | On-Device Loopback | 20.35 Gbps | 20.35 Gbps | 5.8% | 5.8% | 11.1% | 163.0 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | On-Device Loopback | 18.92 Gbps | 18.92 Gbps | 8.1% | 8.1% | 18.5% | 152.3 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | On-Device Loopback | 19.42 Gbps | 19.42 Gbps | 7.5% | 7.5% | 18.5% | 152.5 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | On-Device Loopback | 18.74 Gbps | 18.74 Gbps | 7.0% | 7.0% | 14.8% | 182.2 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | On-Device Loopback | 50.29 Gbps | 50.29 Gbps | 6.4% | 6.4% | 14.8% | 185.0 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | On-Device Loopback | 26.16 Gbps | 26.16 Gbps | 4.2% | 4.2% | 7.0% | 152.2 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | On-Device Loopback | 27.46 Gbps | 27.46 Gbps | 8.8% | 8.8% | 22.2% | 152.9 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | On-Device Loopback | 25.54 Gbps | 25.54 Gbps | 5.3% | 5.3% | 7.4% | 182.9 MB |

#### Retained Connections vs Memory Growth (Idle Flows)

| Backend | Network | Conns Range | Baseline PSS | 1000 Conns PSS | Memory Slope |
| :--- | :---: | :---: | :---: | :---: | :---: |
| HEV | TCP | 0 -> 1000 | 161.6 MB | 175.3 MB | 14.03 KiB/conn |
| HEV | UDP | 0 -> 1000 | 162.8 MB | 181.0 MB | 18.64 KiB/conn |
| SING | TCP | 0 -> 1000 | 133.9 MB | 132.1 MB | -1.84 KiB/conn |
| SING | UDP | 0 -> 1000 | 133.3 MB | 133.6 MB | 0.31 KiB/conn |
| MIPS | TCP | 0 -> 1000 | 133.7 MB | 133.2 MB | -0.51 KiB/conn |
| MIPS | UDP | 0 -> 1000 | 133.5 MB | 134.4 MB | 0.92 KiB/conn |
| XRAY | TCP | 0 -> 1000 | 164.2 MB | 162.6 MB | -1.64 KiB/conn |
| XRAY | UDP | 0 -> 1000 | 164.1 MB | 163.3 MB | -0.82 KiB/conn |
