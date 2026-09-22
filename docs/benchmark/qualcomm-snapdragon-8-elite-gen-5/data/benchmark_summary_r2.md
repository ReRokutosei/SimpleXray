### Round 1 Results

| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 550.13 Mbps | 574.55 Mbps | 0.4% | 0.4% | 1.0% | 163.1 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 525.37 Mbps | 473.76 Mbps | 0.4% | 0.4% | 1.0% | 155.2 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 571.31 Mbps | 658.26 Mbps | 28.6% | 44.8% | 52.0% | 171.7 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | 5GHz Wi-Fi | 570.53 Mbps | 673.53 Mbps | 20.6% | 39.6% | 46.0% | 172.0 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 551.53 Mbps | 690.66 Mbps | 29.5% | 60.0% | 74.0% | 172.6 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | 5GHz Wi-Fi | 597.26 Mbps | 700.23 Mbps | 21.5% | 56.3% | 68.0% | 173.5 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 562.11 Mbps | 703.18 Mbps | 87.9% | 116.1% | 156.8% | 189.2 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | 5GHz Wi-Fi | 510.56 Mbps | 693.46 Mbps | 74.4% | 110.0% | 127.0% | 164.5 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 570.73 Mbps | 710.79 Mbps | 112.5% | 110.9% | 285.5% | 164.8 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | 5GHz Wi-Fi | 565.74 Mbps | 698.28 Mbps | 117.2% | 118.4% | 365.7% | 165.3 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 612.82 Mbps | 735.08 Mbps | 43.5% | 78.3% | 88.8% | 160.4 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | 5GHz Wi-Fi | 541.36 Mbps | 660.73 Mbps | 25.1% | 57.3% | 65.0% | 134.7 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 605.33 Mbps | 744.79 Mbps | 53.9% | 111.0% | 117.0% | 135.5 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | 5GHz Wi-Fi | 555.64 Mbps | 739.32 Mbps | 36.9% | 93.5% | 132.1% | 135.5 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 481.84 Mbps | 728.79 Mbps | 51.5% | 98.2% | 108.0% | 133.3 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | 5GHz Wi-Fi | 504.44 Mbps | 720.21 Mbps | 38.6% | 94.3% | 104.0% | 134.5 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | 5GHz Wi-Fi | 595.33 Mbps | 741.69 Mbps | 82.5% | 127.0% | 292.3% | 135.3 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | 5GHz Wi-Fi | 588.18 Mbps | 724.97 Mbps | 49.5% | 124.5% | 188.7% | 135.3 MB |
