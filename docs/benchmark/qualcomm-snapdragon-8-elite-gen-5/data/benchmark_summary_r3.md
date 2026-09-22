### Round 1 Results

| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 328.44 Mbps | 727.95 Mbps | 0.6% | 0.5% | 3.0% | 163.6 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 662.08 Mbps | 728.79 Mbps | 0.3% | 1.0% | 10.0% | 162.3 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 633.59 Mbps | 731.87 Mbps | 37.4% | 43.8% | 51.0% | 139.5 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | 5GHz Wi-Fi | 622.98 Mbps | 742.36 Mbps | 21.5% | 41.3% | 50.0% | 139.8 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 669.68 Mbps | 720.68 Mbps | 32.4% | 61.0% | 82.0% | 140.1 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | 5GHz Wi-Fi | 644.39 Mbps | 719.66 Mbps | 20.3% | 64.5% | 75.0% | 140.8 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 623.07 Mbps | 731.15 Mbps | 84.9% | 105.1% | 158.7% | 149.6 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | 5GHz Wi-Fi | 624.68 Mbps | 433.85 Mbps | 82.4% | 67.7% | 122.0% | 131.7 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 538.68 Mbps | 713.67 Mbps | 109.5% | 98.2% | 203.5% | 132.2 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | 5GHz Wi-Fi | 657.44 Mbps | 705.72 Mbps | 112.3% | 101.8% | 303.0% | 132.3 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 625.07 Mbps | 717.97 Mbps | 43.0% | 72.0% | 88.3% | 119.9 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | 5GHz Wi-Fi | 622.22 Mbps | 713.31 Mbps | 25.7% | 56.2% | 61.0% | 102.3 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 649.69 Mbps | 694.39 Mbps | 57.4% | 102.4% | 129.0% | 103.0 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | 5GHz Wi-Fi | 650.4 Mbps | 708.32 Mbps | 32.7% | 90.7% | 132.6% | 103.0 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 582.44 Mbps | 684.51 Mbps | 68.6% | 95.6% | 142.2% | 135.3 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | 5GHz Wi-Fi | 665.27 Mbps | 849.5 Mbps | 47.2% | 90.8% | 103.0% | 135.1 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | 5GHz Wi-Fi | 666.96 Mbps | 691.11 Mbps | 81.1% | 122.5% | 280.9% | 135.3 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | 5GHz Wi-Fi | 569.03 Mbps | 456.9 Mbps | 43.3% | 108.6% | 182.0% | 135.7 MB |
