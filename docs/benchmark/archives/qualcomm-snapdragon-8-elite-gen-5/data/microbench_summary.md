# SimpleXray Standalone TUN Micro-Benchmark Summary (Scheme 2)

Generated at: 2026-09-20 00:19:07

Environment: Linux x86_64 Isolated User Namespace (Zero Android ART / Zero Framework Overhead)

### Standalone Pure Stack Memory Slope (3 Rounds Summary)

| Backend | Network | Base PSS (MB) | 1000 Conns PSS (MB) | R1 Slope | R2 Slope | R3 Slope | **Average Slope** |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **HEV** | TCP | 2.1 MB | 14.4 MB | 12.60 KiB | 12.58 KiB | 12.60 KiB | **12.59 KiB/conn** |
| **HEV** | UDP | 2.1 MB | 18.3 MB | 16.59 KiB | 16.58 KiB | 16.59 KiB | **16.59 KiB/conn** |
| **XRAY** | TCP | 37.7 MB | 98.5 MB | 62.33 KiB | 65.28 KiB | 63.01 KiB | **63.54 KiB/conn** |
| **XRAY** | UDP | 37.1 MB | 92.4 MB | 56.61 KiB | 59.45 KiB | 56.59 KiB | **57.55 KiB/conn** |
| **SING** | TCP | 9.5 MB | 62.6 MB | 54.41 KiB | 49.51 KiB | 52.50 KiB | **52.14 KiB/conn** |
| **SING** | UDP | 9.5 MB | 51.1 MB | 42.52 KiB | 46.97 KiB | 48.02 KiB | **45.84 KiB/conn** |
| **MIPS** | TCP | 13.0 MB | 61.9 MB | 50.01 KiB | 51.11 KiB | 56.85 KiB | **52.66 KiB/conn** |
| **MIPS** | UDP | 12.8 MB | 125.4 MB | 115.34 KiB | 110.00 KiB | 116.29 KiB | **113.88 KiB/conn** |

---
