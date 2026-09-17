# SimpleXray Benchmark Summary

### Round 1 Results

| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 323.47 Mbps | 339.59 Mbps | 0.0% | 0.0% | 0.0% | 97.5 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 392.41 Mbps | 375.58 Mbps | 0.0% | 0.1% | 1.0% | 97.4 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 327.67 Mbps | 299.44 Mbps | 25.5% | 40.7% | 82.0% | 98.0 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 260.27 Mbps | 59.24 Mbps | 83.6% | 51.6% | 203.0% | 98.1 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 332.83 Mbps | 148.46 Mbps | 33.4% | 71.1% | 139.0% | 106.8 MB |
| Hev (MTU 8500) [Single Stream] | hev | 8500 | 5GHz Wi-Fi | 339.83 Mbps | 350.18 Mbps | 14.0% | 39.9% | 56.0% | 103.4 MB |
| Xray TUN (MTU 8500) [Single Stream] | xray | 8500 | 5GHz Wi-Fi | 321.27 Mbps | 95.31 Mbps | 47.9% | 58.5% | 181.0% | 103.2 MB |
| SingTUN (MTU 8500) [Single Stream] | sing | 8500 | 5GHz Wi-Fi | 275.27 Mbps | 330.58 Mbps | 21.5% | 41.7% | 85.0% | 109.0 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 330.22 Mbps | 252.1 Mbps | 40.3% | 37.5% | 99.0% | 106.2 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 359.64 Mbps | 136.25 Mbps | 101.4% | 63.3% | 244.0% | 106.2 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 353.77 Mbps | 405.6 Mbps | 39.6% | 124.5% | 239.0% | 142.0 MB |
| Hev (MTU 8500) [P=8] | hev | 8500 | 5GHz Wi-Fi | 376.35 Mbps | 382.75 Mbps | 22.1% | 41.6% | 69.0% | 110.4 MB |
| Xray TUN (MTU 8500) [P=8] | xray | 8500 | 5GHz Wi-Fi | 344.78 Mbps | 179.04 Mbps | 101.5% | 91.2% | 238.0% | 110.2 MB |
| SingTUN (MTU 8500) [P=8] | sing | 8500 | 5GHz Wi-Fi | 422.87 Mbps | 453.14 Mbps | 29.0% | 75.7% | 130.0% | 142.9 MB |
| USB Baseline (No VPN) [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 314.25 Mbps | 398.6 Mbps | 0.0% | 0.0% | 0.0% | 115.7 MB |
| USB Baseline (No VPN) [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 283.86 Mbps | 429.13 Mbps | 0.0% | 0.0% | 0.0% | 115.7 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 337.91 Mbps | 267.57 Mbps | 37.6% | 51.5% | 75.0% | 116.2 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 324.92 Mbps | 60.5 Mbps | 90.0% | 52.4% | 196.0% | 115.8 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 353.91 Mbps | 168.59 Mbps | 42.0% | 83.2% | 142.0% | 119.8 MB |
| Hev (MTU 8500) [Single Stream] | hev | 8500 | USB 3.2 / 4.0 | 357.52 Mbps | 341.51 Mbps | 27.9% | 53.1% | 69.0% | 94.2 MB |
| Xray TUN (MTU 8500) [Single Stream] | xray | 8500 | USB 3.2 / 4.0 | 324.61 Mbps | 61.75 Mbps | 89.5% | 52.7% | 200.0% | 93.3 MB |
| SingTUN (MTU 8500) [Single Stream] | sing | 8500 | USB 3.2 / 4.0 | 326.37 Mbps | 359.52 Mbps | 26.4% | 82.7% | 118.0% | 106.6 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | USB 3.2 / 4.0 | 309.69 Mbps | 263.11 Mbps | 35.6% | 58.1% | 84.0% | 103.7 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | USB 3.2 / 4.0 | 352.91 Mbps | 336.82 Mbps | 96.8% | 125.2% | 354.0% | 103.7 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | USB 3.2 / 4.0 | 217.63 Mbps | 372.66 Mbps | 65.2% | 126.9% | 189.0% | 115.3 MB |
| Hev (MTU 8500) [P=8] | hev | 8500 | USB 3.2 / 4.0 | 338.78 Mbps | 404.33 Mbps | 29.0% | 61.1% | 97.0% | 115.3 MB |
| Xray TUN (MTU 8500) [P=8] | xray | 8500 | USB 3.2 / 4.0 | 363.18 Mbps | 157.63 Mbps | 99.9% | 82.8% | 218.0% | 114.7 MB |
| SingTUN (MTU 8500) [P=8] | sing | 8500 | USB 3.2 / 4.0 | 321.48 Mbps | 396.78 Mbps | 30.7% | 98.1% | 135.0% | 144.6 MB |
| Loopback Baseline (No VPN) [Single Stream] | direct_none | 0 | On-Device Loopback | 20.22 Gbps | 20.22 Gbps | 0.0% | 0.0% | 0.0% | 110.5 MB |
| Loopback Baseline (No VPN) [P=8] | direct_none | 0 | On-Device Loopback | 17.78 Gbps | 17.78 Gbps | 0.0% | 0.0% | 0.0% | 110.5 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | On-Device Loopback | 20.72 Gbps | 20.72 Gbps | 1.0% | 1.0% | 7.4% | 111.1 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | On-Device Loopback | 20.61 Gbps | 20.61 Gbps | 0.3% | 0.3% | 2.0% | 111.1 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | On-Device Loopback | 20.93 Gbps | 20.93 Gbps | 0.6% | 0.6% | 3.7% | 111.2 MB |
| Hev (MTU 8500) [Single Stream] | hev | 8500 | On-Device Loopback | 20.58 Gbps | 20.58 Gbps | 0.6% | 0.6% | 7.4% | 111.3 MB |
| Xray TUN (MTU 8500) [Single Stream] | xray | 8500 | On-Device Loopback | 20.84 Gbps | 20.84 Gbps | 0.1% | 0.1% | 2.0% | 113.6 MB |
| SingTUN (MTU 8500) [Single Stream] | sing | 8500 | On-Device Loopback | 21.02 Gbps | 21.02 Gbps | 0.6% | 0.6% | 3.7% | 111.3 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | On-Device Loopback | 17.78 Gbps | 17.78 Gbps | 0.6% | 0.6% | 7.1% | 112.7 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | On-Device Loopback | 17.92 Gbps | 17.92 Gbps | 0.1% | 0.1% | 2.0% | 111.5 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | On-Device Loopback | 18.02 Gbps | 18.02 Gbps | 0.3% | 0.3% | 3.5% | 111.5 MB |
| Hev (MTU 8500) [P=8] | hev | 8500 | On-Device Loopback | 18.19 Gbps | 18.19 Gbps | 0.1% | 0.1% | 1.0% | 111.6 MB |
| Xray TUN (MTU 8500) [P=8] | xray | 8500 | On-Device Loopback | 18.26 Gbps | 18.26 Gbps | 0.1% | 0.1% | 1.0% | 111.6 MB |
| SingTUN (MTU 8500) [P=8] | sing | 8500 | On-Device Loopback | 18.13 Gbps | 18.13 Gbps | 0.7% | 0.7% | 7.1% | 111.6 MB |

### Round 2 Results

| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 396.34 Mbps | 417.81 Mbps | 0.0% | 0.0% | 0.0% | 87.7 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 425.65 Mbps | 462.63 Mbps | 0.3% | 0.0% | 3.0% | 87.8 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 395.24 Mbps | 345.05 Mbps | 23.7% | 41.2% | 84.0% | 95.9 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 250.02 Mbps | 57.98 Mbps | 81.1% | 53.3% | 171.0% | 93.4 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 386.69 Mbps | 150.66 Mbps | 31.0% | 70.6% | 149.0% | 106.3 MB |
| Hev (MTU 8500) [Single Stream] | hev | 8500 | 5GHz Wi-Fi | 396.12 Mbps | 377.95 Mbps | 15.6% | 39.0% | 55.0% | 103.0 MB |
| Xray TUN (MTU 8500) [Single Stream] | xray | 8500 | 5GHz Wi-Fi | 241.69 Mbps | 65.0 Mbps | 80.6% | 50.0% | 167.0% | 102.7 MB |
| SingTUN (MTU 8500) [Single Stream] | sing | 8500 | 5GHz Wi-Fi | 395.86 Mbps | 392.43 Mbps | 27.0% | 62.7% | 112.0% | 107.1 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 423.99 Mbps | 249.53 Mbps | 23.7% | 35.9% | 80.0% | 104.2 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 354.71 Mbps | 181.18 Mbps | 115.6% | 92.3% | 241.0% | 104.2 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 393.9 Mbps | 463.68 Mbps | 42.1% | 127.2% | 238.0% | 142.7 MB |
| Hev (MTU 8500) [P=8] | hev | 8500 | 5GHz Wi-Fi | 424.58 Mbps | 468.92 Mbps | 18.6% | 45.5% | 54.0% | 111.6 MB |
| Xray TUN (MTU 8500) [P=8] | xray | 8500 | 5GHz Wi-Fi | 356.63 Mbps | 178.6 Mbps | 116.9% | 90.1% | 246.0% | 110.9 MB |
| SingTUN (MTU 8500) [P=8] | sing | 8500 | 5GHz Wi-Fi | 423.79 Mbps | 461.89 Mbps | 28.2% | 73.9% | 119.0% | 145.2 MB |
| USB Baseline (No VPN) [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 308.07 Mbps | 399.15 Mbps | 0.0% | 0.0% | 0.0% | 117.7 MB |
| USB Baseline (No VPN) [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 300.68 Mbps | 440.82 Mbps | 0.0% | 0.0% | 0.0% | 117.6 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 342.99 Mbps | 250.79 Mbps | 37.2% | 56.3% | 75.0% | 118.1 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 328.58 Mbps | 61.75 Mbps | 85.2% | 52.7% | 176.0% | 117.8 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 360.33 Mbps | 162.62 Mbps | 41.8% | 79.0% | 134.0% | 122.0 MB |
| Hev (MTU 8500) [Single Stream] | hev | 8500 | USB 3.2 / 4.0 | 350.81 Mbps | 329.84 Mbps | 27.1% | 53.6% | 72.0% | 118.4 MB |
| Xray TUN (MTU 8500) [Single Stream] | xray | 8500 | USB 3.2 / 4.0 | 328.09 Mbps | 64.69 Mbps | 90.8% | 49.4% | 200.0% | 118.2 MB |
| SingTUN (MTU 8500) [Single Stream] | sing | 8500 | USB 3.2 / 4.0 | 332.26 Mbps | 339.27 Mbps | 25.8% | 82.7% | 111.0% | 123.6 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | USB 3.2 / 4.0 | 313.86 Mbps | 260.8 Mbps | 38.6% | 58.2% | 97.0% | 120.4 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | USB 3.2 / 4.0 | 352.73 Mbps | 327.51 Mbps | 99.0% | 122.8% | 310.0% | 120.3 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | USB 3.2 / 4.0 | 213.85 Mbps | 399.46 Mbps | 62.8% | 133.8% | 218.0% | 124.3 MB |
| Hev (MTU 8500) [P=8] | hev | 8500 | USB 3.2 / 4.0 | 336.48 Mbps | 362.02 Mbps | 26.4% | 61.3% | 96.0% | 123.9 MB |
| Xray TUN (MTU 8500) [P=8] | xray | 8500 | USB 3.2 / 4.0 | 362.49 Mbps | 143.48 Mbps | 100.5% | 71.9% | 218.0% | 123.3 MB |
| SingTUN (MTU 8500) [P=8] | sing | 8500 | USB 3.2 / 4.0 | 318.54 Mbps | 399.1 Mbps | 31.0% | 98.4% | 137.0% | 153.0 MB |
| Loopback Baseline (No VPN) [Single Stream] | direct_none | 0 | On-Device Loopback | 19.93 Gbps | 19.93 Gbps | 0.0% | 0.0% | 0.0% | 112.9 MB |
| Loopback Baseline (No VPN) [P=8] | direct_none | 0 | On-Device Loopback | 17.87 Gbps | 17.87 Gbps | 0.0% | 0.0% | 0.0% | 112.8 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | On-Device Loopback | 18.55 Gbps | 18.55 Gbps | 0.4% | 0.4% | 3.7% | 113.2 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | On-Device Loopback | 17.11 Gbps | 17.11 Gbps | 0.1% | 0.1% | 2.0% | 113.3 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | On-Device Loopback | 16.39 Gbps | 16.39 Gbps | 0.7% | 0.7% | 3.5% | 113.4 MB |
| Hev (MTU 8500) [Single Stream] | hev | 8500 | On-Device Loopback | 16.51 Gbps | 16.51 Gbps | 0.4% | 0.4% | 3.5% | 113.6 MB |
| Xray TUN (MTU 8500) [Single Stream] | xray | 8500 | On-Device Loopback | 16.15 Gbps | 16.15 Gbps | 0.3% | 0.3% | 3.5% | 113.5 MB |
| SingTUN (MTU 8500) [Single Stream] | sing | 8500 | On-Device Loopback | 16.32 Gbps | 16.32 Gbps | 0.9% | 0.9% | 7.1% | 113.6 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | On-Device Loopback | 18.22 Gbps | 18.22 Gbps | 0.0% | 0.0% | 0.0% | 113.7 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | On-Device Loopback | 18.19 Gbps | 18.19 Gbps | 0.1% | 0.1% | 1.0% | 113.8 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | On-Device Loopback | 18.29 Gbps | 18.29 Gbps | 0.4% | 0.4% | 3.5% | 113.8 MB |
| Hev (MTU 8500) [P=8] | hev | 8500 | On-Device Loopback | 18.31 Gbps | 18.31 Gbps | 0.2% | 0.2% | 2.0% | 114.0 MB |
| Xray TUN (MTU 8500) [P=8] | xray | 8500 | On-Device Loopback | 18.21 Gbps | 18.21 Gbps | 0.1% | 0.1% | 1.0% | 113.9 MB |
| SingTUN (MTU 8500) [P=8] | sing | 8500 | On-Device Loopback | 17.93 Gbps | 17.93 Gbps | 0.4% | 0.4% | 3.5% | 113.9 MB |

### Round 3 Results

| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 396.44 Mbps | 404.28 Mbps | 0.0% | 0.0% | 0.0% | 113.6 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 425.4 Mbps | 463.2 Mbps | 0.0% | 0.1% | 1.0% | 113.5 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 318.05 Mbps | 344.01 Mbps | 22.5% | 43.1% | 80.0% | 114.0 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 244.65 Mbps | 57.03 Mbps | 82.5% | 52.6% | 175.0% | 113.7 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 382.44 Mbps | 145.83 Mbps | 32.1% | 69.6% | 143.0% | 118.8 MB |
| Hev (MTU 8500) [Single Stream] | hev | 8500 | 5GHz Wi-Fi | 397.42 Mbps | 408.47 Mbps | 21.4% | 40.5% | 57.0% | 115.0 MB |
| Xray TUN (MTU 8500) [Single Stream] | xray | 8500 | 5GHz Wi-Fi | 246.17 Mbps | 63.53 Mbps | 83.2% | 48.0% | 170.0% | 114.9 MB |
| SingTUN (MTU 8500) [Single Stream] | sing | 8500 | 5GHz Wi-Fi | 397.09 Mbps | 407.53 Mbps | 22.0% | 64.9% | 115.0% | 118.9 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 423.49 Mbps | 251.21 Mbps | 26.9% | 36.6% | 81.0% | 115.5 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 358.54 Mbps | 173.1 Mbps | 117.4% | 88.7% | 243.0% | 115.4 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 382.75 Mbps | 469.13 Mbps | 44.5% | 133.3% | 250.0% | 147.7 MB |
| Hev (MTU 8500) [P=8] | hev | 8500 | 5GHz Wi-Fi | 391.79 Mbps | 473.74 Mbps | 15.5% | 43.8% | 59.0% | 116.1 MB |
| Xray TUN (MTU 8500) [P=8] | xray | 8500 | 5GHz Wi-Fi | 353.28 Mbps | 180.96 Mbps | 116.1% | 91.0% | 245.0% | 115.7 MB |
| SingTUN (MTU 8500) [P=8] | sing | 8500 | 5GHz Wi-Fi | 423.91 Mbps | 461.69 Mbps | 23.8% | 76.1% | 128.0% | 150.5 MB |
| USB Baseline (No VPN) [Single Stream] | direct_none | 0 | USB 3.2 / 4.0 | 305.89 Mbps | 392.75 Mbps | 0.0% | 0.0% | 0.0% | 124.0 MB |
| USB Baseline (No VPN) [P=8] | direct_none | 0 | USB 3.2 / 4.0 | 297.07 Mbps | 418.41 Mbps | 0.0% | 0.0% | 0.0% | 123.9 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | USB 3.2 / 4.0 | 337.04 Mbps | 268.08 Mbps | 37.2% | 56.4% | 72.0% | 124.4 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | USB 3.2 / 4.0 | 349.61 Mbps | 54.62 Mbps | 92.8% | 59.9% | 200.0% | 124.2 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | USB 3.2 / 4.0 | 354.99 Mbps | 164.92 Mbps | 41.4% | 80.3% | 138.0% | 128.3 MB |
| Hev (MTU 8500) [Single Stream] | hev | 8500 | USB 3.2 / 4.0 | 358.92 Mbps | 368.15 Mbps | 26.8% | 56.5% | 69.0% | 124.5 MB |
| Xray TUN (MTU 8500) [Single Stream] | xray | 8500 | USB 3.2 / 4.0 | 323.32 Mbps | 60.6 Mbps | 89.0% | 50.8% | 185.0% | 124.3 MB |
| SingTUN (MTU 8500) [Single Stream] | sing | 8500 | USB 3.2 / 4.0 | 324.2 Mbps | 335.09 Mbps | 27.5% | 80.4% | 95.0% | 128.2 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | USB 3.2 / 4.0 | 315.01 Mbps | 264.94 Mbps | 34.2% | 60.9% | 87.0% | 125.0 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | USB 3.2 / 4.0 | 353.36 Mbps | 337.23 Mbps | 97.9% | 127.6% | 374.0% | 124.8 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | USB 3.2 / 4.0 | 214.89 Mbps | 399.74 Mbps | 61.7% | 141.3% | 237.0% | 125.7 MB |
| Hev (MTU 8500) [P=8] | hev | 8500 | USB 3.2 / 4.0 | 338.52 Mbps | 239.47 Mbps | 27.3% | 41.5% | 80.0% | 125.5 MB |
| Xray TUN (MTU 8500) [P=8] | xray | 8500 | USB 3.2 / 4.0 | 362.19 Mbps | 155.91 Mbps | 99.3% | 73.6% | 213.0% | 124.9 MB |
| SingTUN (MTU 8500) [P=8] | sing | 8500 | USB 3.2 / 4.0 | 320.31 Mbps | 379.51 Mbps | 31.6% | 87.9% | 116.0% | 155.4 MB |
| Loopback Baseline (No VPN) [Single Stream] | direct_none | 0 | On-Device Loopback | 21.29 Gbps | 21.29 Gbps | 0.0% | 0.0% | 0.0% | 118.1 MB |
| Loopback Baseline (No VPN) [P=8] | direct_none | 0 | On-Device Loopback | 17.99 Gbps | 17.99 Gbps | 0.0% | 0.0% | 0.0% | 115.7 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | On-Device Loopback | 16.76 Gbps | 16.76 Gbps | 0.4% | 0.4% | 3.7% | 116.1 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | On-Device Loopback | 14.04 Gbps | 14.04 Gbps | 0.1% | 0.1% | 1.0% | 116.2 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | On-Device Loopback | 15.28 Gbps | 15.28 Gbps | 0.5% | 0.5% | 4.0% | 116.2 MB |
| Hev (MTU 8500) [Single Stream] | hev | 8500 | On-Device Loopback | 16.22 Gbps | 16.22 Gbps | 0.8% | 0.8% | 7.1% | 116.3 MB |
| Xray TUN (MTU 8500) [Single Stream] | xray | 8500 | On-Device Loopback | 16.5 Gbps | 16.5 Gbps | 0.3% | 0.3% | 1.0% | 116.1 MB |
| SingTUN (MTU 8500) [Single Stream] | sing | 8500 | On-Device Loopback | 16.28 Gbps | 16.28 Gbps | 0.4% | 0.4% | 3.5% | 116.1 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | On-Device Loopback | 18.2 Gbps | 18.2 Gbps | 0.3% | 0.3% | 3.2% | 116.2 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | On-Device Loopback | 18.33 Gbps | 18.33 Gbps | 0.1% | 0.1% | 1.0% | 116.1 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | On-Device Loopback | 18.21 Gbps | 18.21 Gbps | 0.5% | 0.5% | 3.4% | 116.1 MB |
| Hev (MTU 8500) [P=8] | hev | 8500 | On-Device Loopback | 18.25 Gbps | 18.25 Gbps | 0.5% | 0.5% | 7.1% | 116.2 MB |
| Xray TUN (MTU 8500) [P=8] | xray | 8500 | On-Device Loopback | 18.12 Gbps | 18.12 Gbps | 0.2% | 0.2% | 2.0% | 116.1 MB |
| SingTUN (MTU 8500) [P=8] | sing | 8500 | On-Device Loopback | 18.19 Gbps | 18.19 Gbps | 0.4% | 0.4% | 3.5% | 116.2 MB |
