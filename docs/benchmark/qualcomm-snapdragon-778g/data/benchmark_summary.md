### Round 1

| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 467.26 Mbps | 530.95 Mbps | 1.2% | 0.9% | 4.0% | 160.6 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 627.38 Mbps | 476.31 Mbps | 0.3% | 0.9% | 4.0% | 157.1 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 566.84 Mbps | 497.7 Mbps | 68.7% | 92.5% | 107.0% | 163.0 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | 5GHz Wi-Fi | 659.53 Mbps | 555.78 Mbps | 55.8% | 86.7% | 100.0% | 165.8 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 602.63 Mbps | 277.32 Mbps | 62.5% | 82.8% | 138.0% | 165.1 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | 5GHz Wi-Fi | 636.3 Mbps | 663.62 Mbps | 36.9% | 102.8% | 119.0% | 165.6 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 564.83 Mbps | 199.2 Mbps | 126.1% | 120.7% | 170.3% | 159.7 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | 5GHz Wi-Fi | 571.5 Mbps | 199.82 Mbps | 129.6% | 116.3% | 169.7% | 161.3 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 641.81 Mbps | 554.42 Mbps | 146.3% | 412.0% | 445.0% | 164.8 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | 5GHz Wi-Fi | 639.63 Mbps | 565.11 Mbps | 141.3% | 412.2% | 439.0% | 162.5 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 577.82 Mbps | 151.82 Mbps | 74.4% | 152.3% | 160.2% | 129.4 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | 5GHz Wi-Fi | 634.0 Mbps | 561.97 Mbps | 61.9% | 153.3% | 159.0% | 131.0 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 581.99 Mbps | 622.73 Mbps | 121.6% | 367.1% | 444.0% | 131.8 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | 5GHz Wi-Fi | 643.8 Mbps | 692.61 Mbps | 61.7% | 220.0% | 234.0% | 134.2 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 488.47 Mbps | 228.46 Mbps | 106.5% | 207.3% | 217.0% | 129.5 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | 5GHz Wi-Fi | 607.41 Mbps | 634.11 Mbps | 79.8% | 194.7% | 207.0% | 131.0 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | 5GHz Wi-Fi | 617.91 Mbps | 122.94 Mbps | 128.9% | 184.4% | 287.0% | 132.3 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | 5GHz Wi-Fi | 614.26 Mbps | 567.21 Mbps | 73.4% | 223.1% | 281.4% | 134.4 MB |
| Zeptun (MTU 1500) [Single Stream] | zeptun | 1500 | 5GHz Wi-Fi | 456.23 Mbps | 548.27 Mbps | 49.0% | 93.1% | 98.0% | 173.3 MB |
| Zeptun (MTU 9000) [Single Stream] | zeptun | 9000 | 5GHz Wi-Fi | 459.96 Mbps | 495.02 Mbps | 47.1% | 88.9% | 96.0% | 173.3 MB |
| Zeptun (MTU 1500) [P=8] | zeptun | 1500 | 5GHz Wi-Fi | 457.37 Mbps | 520.91 Mbps | 64.1% | 112.4% | 153.4% | 174.7 MB |
| Zeptun (MTU 9000) [P=8] | zeptun | 9000 | 5GHz Wi-Fi | 459.84 Mbps | 445.43 Mbps | 48.5% | 114.2% | 133.3% | 174.9 MB |

#### Retained Connections vs Memory Growth (Idle Flows)

| Backend | Network | Conns Range | Baseline PSS | 1000 Conns PSS | Memory Slope |
| :--- | :---: | :---: | :---: | :---: | :---: |
| ZEPTUN | TCP | 0 -> 1000 | 173.4 MB | 174.7 MB | 1.33 KiB/conn |


### Round 2

| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 633.47 Mbps | 583.67 Mbps | 0.8% | 0.8% | 3.7% | 160.5 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 625.8 Mbps | 602.28 Mbps | 0.6% | 1.0% | 3.7% | 160.0 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 628.21 Mbps | 596.88 Mbps | 74.8% | 88.9% | 107.0% | 164.3 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | 5GHz Wi-Fi | 637.63 Mbps | 605.59 Mbps | 54.4% | 87.2% | 101.0% | 164.9 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 629.49 Mbps | 255.72 Mbps | 61.4% | 73.8% | 131.0% | 168.1 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | 5GHz Wi-Fi | 626.88 Mbps | 598.51 Mbps | 38.3% | 104.0% | 118.0% | 166.6 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 450.97 Mbps | 201.3 Mbps | 107.1% | 118.9% | 162.0% | 159.8 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | 5GHz Wi-Fi | 600.85 Mbps | 195.43 Mbps | 155.1% | 119.4% | 184.7% | 161.4 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 642.89 Mbps | 500.88 Mbps | 146.7% | 358.1% | 375.0% | 162.3 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | 5GHz Wi-Fi | 632.27 Mbps | 527.54 Mbps | 144.5% | 389.9% | 420.0% | 162.5 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 613.09 Mbps | 143.95 Mbps | 90.2% | 152.4% | 173.0% | 129.3 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | 5GHz Wi-Fi | 638.43 Mbps | 550.54 Mbps | 78.4% | 148.0% | 166.0% | 130.9 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 568.63 Mbps | 670.23 Mbps | 124.4% | 402.6% | 434.0% | 132.4 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | 5GHz Wi-Fi | 623.13 Mbps | 670.33 Mbps | 61.6% | 227.0% | 238.0% | 132.2 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 530.14 Mbps | 180.14 Mbps | 114.4% | 205.9% | 249.4% | 128.9 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | 5GHz Wi-Fi | 637.91 Mbps | 637.67 Mbps | 83.5% | 197.5% | 206.0% | 130.6 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | 5GHz Wi-Fi | 629.34 Mbps | 112.13 Mbps | 142.4% | 187.6% | 298.0% | 131.6 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | 5GHz Wi-Fi | 638.35 Mbps | 573.4 Mbps | 77.7% | 208.2% | 239.8% | 135.0 MB |
| Zeptun (MTU 1500) [Single Stream] | zeptun | 1500 | 5GHz Wi-Fi | 436.44 Mbps | 520.81 Mbps | 61.5% | 89.9% | 102.0% | 175.0 MB |
| Zeptun (MTU 9000) [Single Stream] | zeptun | 9000 | 5GHz Wi-Fi | 440.56 Mbps | 536.53 Mbps | 51.8% | 92.0% | 97.0% | 174.5 MB |
| Zeptun (MTU 1500) [P=8] | zeptun | 1500 | 5GHz Wi-Fi | 446.99 Mbps | 569.45 Mbps | 52.5% | 111.6% | 133.2% | 176.4 MB |
| Zeptun (MTU 9000) [P=8] | zeptun | 9000 | 5GHz Wi-Fi | 442.06 Mbps | 565.47 Mbps | 46.2% | 106.8% | 125.8% | 176.5 MB |

#### Retained Connections vs Memory Growth (Idle Flows)

| Backend | Network | Conns Range | Baseline PSS | 1000 Conns PSS | Memory Slope |
| :--- | :---: | :---: | :---: | :---: | :---: |
| ZEPTUN | TCP | 0 -> 1000 | 174.3 MB | 184.3 MB | 10.24 KiB/conn |


### Round 3

| Test Case | Backend | MTU | Medium | Upload | Download | Up CPU | Down CPU | Peak CPU | Peak Memory |
| :--- | :--- | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Wi-Fi Baseline (No VPN) [Single Stream] | direct_none | 0 | 5GHz Wi-Fi | 634.75 Mbps | 618.47 Mbps | 0.5% | 0.6% | 2.0% | 160.1 MB |
| Wi-Fi Baseline (No VPN) [P=8] | direct_none | 0 | 5GHz Wi-Fi | 646.82 Mbps | 678.03 Mbps | 0.4% | 0.6% | 1.0% | 159.7 MB |
| Hev (MTU 1500) [Single Stream] | hev | 1500 | 5GHz Wi-Fi | 640.55 Mbps | 633.93 Mbps | 73.6% | 91.0% | 105.0% | 165.1 MB |
| Hev (MTU 9000) [Single Stream] | hev | 9000 | 5GHz Wi-Fi | 643.43 Mbps | 631.79 Mbps | 58.3% | 88.1% | 103.0% | 167.1 MB |
| Hev (MTU 1500) [P=8] | hev | 1500 | 5GHz Wi-Fi | 643.96 Mbps | 252.52 Mbps | 61.7% | 74.8% | 131.0% | 168.3 MB |
| Hev (MTU 9000) [P=8] | hev | 9000 | 5GHz Wi-Fi | 631.63 Mbps | 628.55 Mbps | 38.5% | 101.7% | 117.0% | 167.5 MB |
| Xray TUN (MTU 1500) [Single Stream] | xray | 1500 | 5GHz Wi-Fi | 592.98 Mbps | 195.64 Mbps | 131.5% | 117.1% | 177.0% | 159.5 MB |
| Xray TUN (MTU 9000) [Single Stream] | xray | 9000 | 5GHz Wi-Fi | 602.21 Mbps | 194.69 Mbps | 140.1% | 118.3% | 166.5% | 160.6 MB |
| Xray TUN (MTU 1500) [P=8] | xray | 1500 | 5GHz Wi-Fi | 652.14 Mbps | 528.42 Mbps | 154.0% | 391.5% | 414.0% | 160.6 MB |
| Xray TUN (MTU 9000) [P=8] | xray | 9000 | 5GHz Wi-Fi | 656.92 Mbps | 517.23 Mbps | 144.3% | 407.2% | 433.0% | 163.6 MB |
| SingTUN (MTU 1500) [Single Stream] | sing | 1500 | 5GHz Wi-Fi | 632.38 Mbps | 135.98 Mbps | 92.7% | 154.6% | 174.8% | 129.2 MB |
| SingTUN (MTU 9000) [Single Stream] | sing | 9000 | 5GHz Wi-Fi | 649.31 Mbps | 502.31 Mbps | 77.8% | 135.5% | 164.0% | 130.9 MB |
| SingTUN (MTU 1500) [P=8] | sing | 1500 | 5GHz Wi-Fi | 561.47 Mbps | 613.73 Mbps | 121.9% | 371.3% | 420.0% | 134.6 MB |
| SingTUN (MTU 9000) [P=8] | sing | 9000 | 5GHz Wi-Fi | 638.29 Mbps | 627.01 Mbps | 54.4% | 228.2% | 240.0% | 132.3 MB |
| MipsTUN (MTU 1500) [Single Stream] | mips | 1500 | 5GHz Wi-Fi | 566.47 Mbps | 182.64 Mbps | 125.1% | 206.3% | 248.0% | 129.7 MB |
| MipsTUN (MTU 9000) [Single Stream] | mips | 9000 | 5GHz Wi-Fi | 639.67 Mbps | 607.6 Mbps | 85.2% | 195.1% | 206.0% | 131.5 MB |
| MipsTUN (MTU 1500) [P=8] | mips | 1500 | 5GHz Wi-Fi | 641.72 Mbps | 115.7 Mbps | 136.4% | 190.3% | 283.0% | 132.3 MB |
| MipsTUN (MTU 9000) [P=8] | mips | 9000 | 5GHz Wi-Fi | 648.48 Mbps | 554.39 Mbps | 82.1% | 202.3% | 281.1% | 135.0 MB |
| Zeptun (MTU 1500) [Single Stream] | zeptun | 1500 | 5GHz Wi-Fi | 441.56 Mbps | 530.06 Mbps | 65.4% | 89.0% | 99.0% | 175.7 MB |
| Zeptun (MTU 9000) [Single Stream] | zeptun | 9000 | 5GHz Wi-Fi | 440.14 Mbps | 510.53 Mbps | 53.0% | 90.3% | 101.0% | 174.7 MB |
| Zeptun (MTU 1500) [P=8] | zeptun | 1500 | 5GHz Wi-Fi | 446.38 Mbps | 568.09 Mbps | 57.9% | 115.1% | 151.7% | 176.5 MB |
| Zeptun (MTU 9000) [P=8] | zeptun | 9000 | 5GHz Wi-Fi | 449.17 Mbps | 567.88 Mbps | 43.5% | 109.4% | 122.1% | 176.8 MB |

#### Retained Connections vs Memory Growth (Idle Flows)

| Backend | Network | Conns Range | Baseline PSS | 1000 Conns PSS | Memory Slope |
| :--- | :---: | :---: | :---: | :---: | :---: |
| ZEPTUN | TCP | 0 -> 1000 | 175.4 MB | 184.8 MB | 9.63 KiB/conn |

