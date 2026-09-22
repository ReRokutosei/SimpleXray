### Round 1 Results

| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 432.81 Mbps | 418.14 Mbps | 0.4% | 0.4% | 1.0% | 162.6 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 612.07 Mbps | 417.57 Mbps | 0.4% | 0.4% | 2.0% | 162.5 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 503.46 Mbps | 466.4 Mbps | 33.8% | 45.2% | 53.0% | 187.3 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | 5GHz Wi-Fi | 520.25 Mbps | 544.58 Mbps | 18.8% | 45.4% | 56.0% | 187.8 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 514.38 Mbps | 502.6 Mbps | 32.0% | 62.5% | 83.0% | 188.5 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | 5GHz Wi-Fi | 527.42 Mbps | 562.3 Mbps | 21.8% | 65.1% | 78.0% | 189.5 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 518.0 Mbps | 570.2 Mbps | 74.5% | 87.5% | 118.0% | 164.0 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | 5GHz Wi-Fi | 526.69 Mbps | 445.09 Mbps | 80.4% | 89.3% | 137.0% | 165.4 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 525.83 Mbps | 508.17 Mbps | 101.7% | 106.7% | 258.7% | 165.8 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | 5GHz Wi-Fi | 475.23 Mbps | 492.11 Mbps | 104.2% | 115.4% | 324.5% | 166.2 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 546.1 Mbps | 563.6 Mbps | 40.2% | 70.8% | 74.0% | 134.1 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | 5GHz Wi-Fi | 523.93 Mbps | 511.26 Mbps | 26.0% | 53.9% | 62.0% | 135.8 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 502.65 Mbps | 493.34 Mbps | 50.8% | 120.2% | 128.0% | 135.7 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | 5GHz Wi-Fi | 569.34 Mbps | 571.6 Mbps | 37.8% | 91.9% | 136.6% | 136.1 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 602.19 Mbps | 620.62 Mbps | 63.1% | 98.2% | 117.8% | 134.0 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | 5GHz Wi-Fi | 577.28 Mbps | 546.5 Mbps | 42.9% | 78.0% | 95.0% | 135.9 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | 5GHz Wi-Fi | 606.06 Mbps | 656.44 Mbps | 79.7% | 127.2% | 270.2% | 135.9 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | 5GHz Wi-Fi | 626.14 Mbps | 552.19 Mbps | 51.3% | 125.8% | 162.3% | 136.2 MB |
