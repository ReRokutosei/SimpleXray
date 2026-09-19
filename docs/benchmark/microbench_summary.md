# SimpleXray Standalone TUN Micro-Benchmark Summary (Scheme 2)

Generated at: 2026-09-19 13:45:38

Environment: Linux x86_64 Isolated User Namespace (Zero Android ART / Zero Framework Overhead)

### Standalone Pure Stack Memory Slope (3 Rounds Summary)

| Backend | Network | Base PSS (MB) | 1000 Conns PSS (MB) | R1 Slope | R2 Slope | R3 Slope | **Average Slope** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **HEV** | TCP | 2.1 MB | 14.4 MB | 12.60 KiB | 12.60 KiB | 12.58 KiB | **12.59 KiB/conn** |
| **HEV** | UDP | 2.1 MB | 18.3 MB | 16.58 KiB | 16.59 KiB | 16.58 KiB | **16.58 KiB/conn** |
| **XRAY** | TCP | 35.1 MB | 100.2 MB | 66.61 KiB | 65.06 KiB | 61.04 KiB | **64.24 KiB/conn** |
| **XRAY** | UDP | 36.5 MB | 87.5 MB | 52.18 KiB | 54.44 KiB | 54.69 KiB | **53.77 KiB/conn** |
| **SING** | TCP | 9.8 MB | 56.9 MB | 48.28 KiB | 50.19 KiB | 51.55 KiB | **50.01 KiB/conn** |
| **SING** | UDP | 9.8 MB | 53.9 MB | 45.11 KiB | 45.82 KiB | 46.90 KiB | **45.94 KiB/conn** |
| **MIPS** | TCP | 12.9 MB | 61.4 MB | 49.72 KiB | 50.90 KiB | 52.53 KiB | **51.05 KiB/conn** |
| **MIPS** | UDP | 13.0 MB | 86.3 MB | 75.03 KiB | 81.06 KiB | 78.59 KiB | **78.23 KiB/conn** |

---
