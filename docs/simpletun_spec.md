# SimpleTUN Architecture & State Machine Specification

**Version**: 0.3-perf  
**Target Scope**: Android `VpnService` TUN-to-SOCKS5 transparent proxy endpoint  
**Language Target**: Zig (C ABI export)  

---

## 1. System Overview & Scope Boundaries

SimpleTUN is a purpose-built user-space network endpoint designed strictly for Android `VpnService` file descriptors (`tun_fd`) routing egress traffic to a local loopback SOCKS5 inbound (`127.0.0.1:port`).

```
 +---------------------------------------------------------+
 | Android Kernel Network Stack (Applications)             |
 +---------------------------------------------------------+
       ^ (IP Packets)
       v
 [ tun_fd ] (L3 Virtual Interface, O_NONBLOCK)
       ^
       | read() / writev()
       v
 +---------------------------------------------------------+
 | SimpleTUN (User-space Protocol Shifter)                 |
 |  - IPv4 Packet Demux & RFC 793/1624 Checksum Fold       |
 |  - Conservative TCP State Machine & 4-Byte MSS Option   |
 |  - Per-flow Zero-Window Backpressure & Sliding Buffer   |
 |  - UDP ASSOCIATE & DNS Transaction ID Ring Tracker      |
 |  - Zero-Allocation Static Slab Session Table (<1.5MB)   |
 +---------------------------------------------------------+
       ^
       | non-blocking stream & datagram I/O (epoll)
       v
 [ socks_fd / udp_relay_fd ] (Local Sockets)
       ^
       v
 +---------------------------------------------------------+
 | Local SOCKS5 Inbound (Xray-core / 127.0.0.1)            |
 +---------------------------------------------------------+
```

### 1.1 In-Scope
- Single TUN file descriptor input (`tun_fd`), configured non-blocking (`O_NONBLOCK`).
- IPv4 protocol parsing, packet synthesis, and branchless 16-bit checksum fold.
- TCP stream translation to local SOCKS5 client (`NO AUTHENTICATION REQUIRED`, `CMD 0x01 CONNECT`).
- Conservative 3-way handshake with 4-byte TCP MSS Option (`Kind=2, Len=4, Value=MTU-40`, e.g. 1460).
- Level-triggered epoll safety: explicit disarming of `EPOLLOUT` during handshake to avoid 100% CPU spinning; immediate socket closure and mandatory `EPOLL_CTL_DEL` upon EOF/RST.
- O(1) SOCKS event dispatch via epoll union data (`flow_idx + 0x1000`), completely eliminating O(N) table searches.
- Direct downstream packet synthesis via `recv(MSG_PEEK)`: SOCKS data is consumed only after the synthesized packet has been accepted by the TUN device, so TUN `EAGAIN` never drops payload.
- Downstream client TCP flow control: `snd_una`/`peer_wnd` tracking prevents reading more from SOCKS than the client kernel can receive, including zero-window pause and window reopen.
- Incremental SOCKS5 handshake parser that consumes exactly the bytes of each response, even when authentication or CONNECT replies are split across TCP segments.
- Explicit per-flow upstream backpressure via standard TCP zero-window signaling (`Win=0`) and persist probe handling (0-byte and 1-byte probes). Unacknowledged bytes are retransmitted by the client TCP stack after the window reopens; no user-space payload buffer is required.
- Deterministic connection tear-down with explicit `upstream_fin` FIN wait, sequence-validated `RST`, and short-lived session tombstones with LRU/saturation eviction fallback under high CPS.
- Single sweep loop drives handshake/idle/FIN/tombstone/UDP/DNS deadlines without adding timer threads.
- Tombstones answer late FIN/data retransmissions with trailing ACKs instead of silently dropping them.
- SOCKS5 UDP ASSOCIATE relay negotiated through the same non-blocking incremental handshake parser, with a 2s failure backoff instead of blocking the TUN event loop.
- Dedicated 64-slot FIFO ring buffer (`DnsQueryTracker`) for concurrent DNS query matching, now with per-query TTL and sweep-based expiry.
- Ultra-dense memory footprint via statically pinned 80-byte Flow slab tables (< 100 KiB total BSS).

### 1.2 Out-of-Scope (Deferred to Future Releases)
- **IPv6 parsing & synthesis** (Deferred).
- **0-RTT SYN spoofing with speculative payload buffering**.
- **ICMP processing**: All ICMP packets are silently dropped.
- **IP reassembly & fragmentation**: Path MTU discovery is assumed; fragmented IP packets are dropped.
- **General routing tables**: Destination IPv4 and port are extracted directly from headers and mapped to SOCKS5 targets.

---

## 2. Protocol & Header Handling

### 2.1 IPv4 Constraints
- Fixed 20-byte IPv4 header support. Packets with `IHL != 5` (IPv4 options) are dropped.
- `Total Length` must match the number of bytes read; truncated packets are dropped.
- Any IPv4 fragment (`MF` set or non-zero fragment offset) is dropped before protocol dispatch.
- RX transport checksum verification is intentionally not done because the TUN path may expose checksum-offload/partial checksums; all synthesized TX checksums are calculated in full.

### 2.2 TCP Options & Negotiation Parameters
When generating `SYN-ACK` to the client kernel:
- **MSS**: Clamped to `tun_mtu - 40` (default 1460 for MTU 1500).
- **Window Scale (RFC 7323)**: Disabled in Phase 1 (scale factor 0) or mirrored conservatively. Default window announcements use 16-bit raw values (e.g., 65535).
- **SACK Permitted (RFC 2018)**: Omitted. SimpleTUN relies purely on cumulative ACKs.
- **Timestamps (RFC 7323)**: Omitted to minimize header construction overhead.

---

## 3. TCP State Machine & Sequence Tracking

Each TCP flow is identified by a 4-tuple: `(src_ip, src_port, dst_ip, dst_port)`. SimpleTUN acts as the local TCP endpoint facing the kernel, and a standard non-blocking TCP client facing SOCKS5.

### 3.1 State Transitions

```
                             [ CLOSED / TOMBSTONE ]
                                       |
                                RX: Client SYN
                                       |
                         Allocate Flow in Static Slab
                         Open non-blocking SOCKS5 TCP
                                       |
                                       v
                              [ UPSTREAM_CONNECT ]
                                /              \
                  SOCKS5 Ready /                \ Connect Error / Timeout
                              v                  v
                   TX: SYN-ACK to TUN       TX: RST to TUN
                              |                  |
                        RX: Client ACK           v
                              |              [ CLOSED ]
                              v
                        [ ESTABLISHED ]
                          |         |
           Client sends FIN |         | Upstream Socket EOF
           (set client_fin) |         | (send FIN-ACK, closeFlow)
                          v         v
                   [ SHUT_WR ]  [ TOMBSTONE ] (3 seconds)
                          \         /
                    Both directions closed
                             |
                             v
                        [ TOMBSTONE ] (3 seconds)
                             |
                          Timeout / Fast-Recycle
                             v
                          [ FREE ]
```

### 3.2 State Definitions

| State | Description | Kernel Interface (TUN) | SOCKS5 Interface |
| :--- | :--- | :--- | :--- |
| `FREE` | Unallocated slot in static slab. | Inactive | Inactive |
| `UPSTREAM_CONNECT` | Received `SYN`. Connecting to `127.0.0.1` and negotiating SOCKS5 handshake. | Do not send `SYN-ACK` yet. If client retransmits `SYN`, drop duplicate or ignore. | Non-blocking connect + SOCKS5 handshake in progress. |
| `SOCKS5_AUTH_WAIT` | Sent SOCKS5 Greeting, awaiting authentication response `[0x05, 0x00]`. | Inactive | `EPOLLIN` monitored. |
| `SOCKS5_CONNECT_WAIT` | Sent SOCKS5 Connect Request, awaiting target connection response `[0x05, 0x00, ...]`. | Inactive | `EPOLLIN` monitored. |
| `ESTABLISHED` | SOCKS5 handshake succeeded. `SYN-ACK` sent, bi-directional data transfer active. Supports `client_fin` flag for half-close. | Normal `ACK` progression. Backpressure signals evaluated per packet. | Bi-directional streaming via non-blocking read/write (0-copy direct). |
| `TOMBSTONE` | Flow fully closed. 4-tuple locked for 3 seconds to absorb late-arriving packets or quick RFC 1122 fast recycle on new `SYN`. | Answered with identical trailing `ACK` or `RST`. | Socket closed and unmapped immediately from epoll. |

---

## 4. Handshake & Connection Lifecycles

### 4.1 Conservative 3-Way Handshake
To avoid speculative payload buffering and complex rollbacks, SimpleTUN employs a conservative handshake policy:

1. **Step 1 (RX SYN)**:
   - Client sends `SYN (seq = c_isn)`.
   - SimpleTUN queries the session table. If no collision, allocate a `Flow` slot.
   - Flow state set to `UPSTREAM_CONNECT`.
   - Store `c_isn`. Generate random initial sequence number `s_isn`.
   - Immediately initiate asynchronous `connect()` to `127.0.0.1:socks_port`.

2. **Step 2 (SOCKS5 Handshake)**:
   - SOCKS5 socket emits writable event (`EPOLLOUT`).
   - Send Greeting: `[0x05, 0x01, 0x00]` (Version 5, 1 Auth Method, No Auth).
   - Read Response: Expect `[0x05, 0x00]`.
   - Send Request: `[0x05, 0x01, 0x00, 0x01, dst_ip (4B), dst_port (2B)]`.
   - Read Response: Expect `[0x05, 0x00, 0x00, 0x01, ...]`.
   - Replies are consumed by an incremental parser: each read requests exactly the number of bytes still missing, so partial TCP segments are reassembled and early application data is not consumed with the handshake.

3. **Step 3 (TX SYN-ACK)**:
   - Upon SOCKS5 status `0x00` (Success), construct TCP packet:
     - `Flags`: `SYN | ACK`
     - `Seq`: `s_isn`
     - `Ack`: `c_isn + 1`
     - `Window`: `65535`
     - `Options`: `MSS = tun_mtu - 40`
   - Write packet to `tun_fd`. Flow enters `ESTABLISHED`.

4. **Failure Branch (SOCKS5 Error / Timeout)**:
   - If SOCKS5 connection is refused or returns non-zero status:
   - Construct `RST` packet (`Seq = 0`, `Ack = c_isn + 1`, `Flags = RST | ACK`).
   - Write packet to `tun_fd`. Immediately free the flow slot.

---

## 5. Flow Control & Backpressure (Zero-Window Semantics)

Because SimpleTUN does not allocate unbounded intermediate buffers, stream flow control must be propagated synchronously between the TUN virtual interface and the upstream socket.

```
Upstream SOCKS5 Socket (EAGAIN on write)
                 |
                 v
   Flow enters BACKPRESSURE state
                 |
                 v
   Synthesize ACK to TUN:
     - Ack = rcv_nxt
     - Window = 0
                 |
                 v
   Client Kernel TCP enters Persist State (Freezes sending)
                 |
   Client sends Zero-Window Probes (1-byte payload, Seq = rcv_nxt - 1)
                 |
                 v
   SimpleTUN drops probe payload, replies:
     - Ack = rcv_nxt
     - Window = 0
                 |
   Upstream Socket becomes writable (EPOLLOUT)
                 |
                 v
   Synthesize Window Update ACK to TUN:
     - Ack = rcv_nxt
     - Window = 65535
                 |
                 v
   Client Kernel resumes standard transmission
```

### 5.1 Backpressure Triggers
- When forwarding payload from TUN to SOCKS5 socket:
  - If `write(socks_fd, payload)` returns `EAGAIN` / `EWOULDBLOCK`, no user-space buffering is performed. The payload is simply not acknowledged.
  - If the write was partial, only the bytes accepted by the SOCKS socket advance `rcv_nxt`; the remaining bytes stay unacknowledged.
  - The flow sets its internal flag `is_blocked = true` and arms `EPOLLOUT` on the SOCKS socket.
  - Immediately construct and transmit a pure TCP `ACK` packet through `tun_fd` with `Window = 0` and `Ack = rcv_nxt`.

### 5.2 Persist Probe Compliance
- While `Window = 0`, the client kernel will emit Zero-Window Probes at exponential backoff intervals (typically 1-byte payload with `seq = rcv_nxt - 1`).
- SimpleTUN **must** recognize this condition:
  - Do not increment `rcv_nxt`.
  - Do not forward the probe payload.
  - Immediately return an `ACK` packet with `Ack = rcv_nxt` and `Window = 0`.
  - Failure to acknowledge probes causes kernel connection timeouts.

### 5.3 Window Reopening
- SOCKS5 socket signals `EPOLLOUT`.
- Clear `is_blocked = false` and disarm `EPOLLOUT`.
- Construct and transmit an unsolicited Window Update packet via `tun_fd`:
  - `Flags`: `ACK`
  - `Seq`: `snd_nxt`
  - `Ack`: `rcv_nxt`
  - `Window`: `65535`
- The client TCP stack retransmits from `rcv_nxt`; SimpleTUN does not keep an upstream payload copy.

### 5.4 UDP Session Demultiplexing & Single Relay Socket Trade-Off
SimpleTUN is intentionally optimized for mobile VPN transparent proxy environments where the overwhelming majority (>99%) of UDP traffic consists of DNS queries:
- **Asynchronous ASSOCIATE with prewarm**: UDP relay setup is driven by the same non-blocking parser as TCP. Greeting/auth/request/reply progress on `EPOLLIN/EPOLLOUT`; a 5s handshake deadline and 2s failure backoff apply. The engine attempts prewarm at startup, retries during a bounded window, and forces an immediate attempt after any successful SOCKS5 TCP handshake proves the local inbound is ready. The triggering datagram is dropped while the relay is not yet ready, so DNS relies on its normal retry behavior.
- **DNS Queries (`dst_port == 53`)**: SimpleTUN utilizes a dedicated 64-entry FIFO ring buffer (`DnsQueryTracker`) keyed on `(DNS Transaction ID, target)`, with TTL-based expiry. This gives accurate matching for the common single-client DNS workload.
- **Non-DNS Datagrams**: To strictly adhere to the zero-heap and fixed file-descriptor budget (< 100 KiB resident BSS), SimpleTUN multiplexes all UDP traffic through a single SOCKS5 `UDP ASSOCIATE` relay socket (`udp_relay_fd`) rather than spawning an unbounded number of OS socket FDs per foreign destination. In an Android single-host VpnService environment where all traffic originates locally from the device itself with ephemeral client ports, inbound return datagrams for identical `(target_ip, target_port)` tuples are dispatched to the most recently active session (`findByTarget`). This is a deliberate, documented architectural trade-off prioritizing deterministic resource boundaries and zero FD leakage over symmetric NAT tracking for generic non-DNS UDP.

### 5.5 Downstream Flow Control & TUN Backpressure
- SimpleTUN tracks the client-side send state with `snd_una` and `peer_wnd`.
- Downstream data is only read while `snd_nxt - snd_una < peer_wnd`; a zero client window pauses all SOCKS reads.
- On client ACK / window update, the send window is advanced and the downstream pump resumes immediately.
- SOCKS data is peeked with `MSG_PEEK` before packet synthesis and consumed with `read()` only after `write(tun_fd)` succeeds.
- If the TUN device returns `EAGAIN`, the flow sets `tun_blocked`, pauses its SOCKS `EPOLLIN`, and arms `EPOLLOUT` on the TUN descriptor.
- When the TUN becomes writable again, blocked flows are retried; unacknowledged TCP bytes remain in the SOCKS socket/kernel buffers, so no user-space payload queue is introduced.

---

## 6. Teardown, RST Handling, and Session Tombstones

### 6.1 Clean Teardown (FIN Handshake)
1. **Client Closes First**:
   - Client sends `FIN (seq = f_seq)`.
   - Advance `rcv_nxt = f_seq + 1`. Send immediate `ACK (ack = rcv_nxt)`.
   - Call `shutdown(socks_fd, SHUT_WR)` and continue reading remaining downstream data.
   - When `read(socks_fd)` returns 0 (`EOF`), send `FIN` to `tun_fd`.
   - If both sides have sent FIN, transition directly to `TOMBSTONE`; otherwise wait for the client ACK with a finite FIN timeout.

2. **Upstream Closes First**:
   - `read(socks_fd)` returns 0.
   - Send `FIN` to `tun_fd` (`seq = snd_nxt`). Advance `snd_nxt += 1`.
   - Mark `upstream_fin = true` and close only `socks_fd`; keep the 4-tuple alive to absorb client ACK/FIN.
   - Once the client ACK covers the FIN, transition to `TOMBSTONE`.

### 6.2 Abrupt Teardown (RST)
- If client sends `RST`: accept only when its sequence number falls inside the current receive window. Valid RST closes the flow and enters `TOMBSTONE` for late packet absorption.
- If upstream socket produces `ECONNRESET` or an unrecoverable error: Send `RST` to `tun_fd` (`Seq = snd_nxt`), close `socks_fd`, transition to `TOMBSTONE`.

### 6.3 Tombstone Slot Preservation
- When a flow reaches termination, releasing the 4-tuple immediately introduces collision risks if delayed packets reside in the kernel or TUN queues.
- Terminated flows transition to `TOMBSTONE` for a fixed duration of **3 seconds**.
- While in `TOMBSTONE`:
  - Any duplicate `FIN` or data retransmissions are answered with identical trailing `ACK` or `RST`.
  - New `SYN` packets with the exact same 4-tuple matching higher sequence numbers may preemptively reclaim the slot (RFC 1122 fast recycle).
- A single non-intrusive sweep loop evicts expired tombstones back to `FREE`.
- The same sweep enforces connect/handshake, idle, FIN-wait, UDP session, and DNS query deadlines.

---

## 7. Memory Budget & Layout (Zero-Allocation Slab)

To ensure predictable runtime memory and prevent PSS spikes on Android, dynamic heap allocations (`malloc` / `page_allocator`) are prohibited in the fast path.

### 7.1 Static Session Slab Architecture
All sessions reside in a pre-allocated fixed array:

```zig
pub const Flow = struct {
    // 4-Tuple Identification (12 Bytes)
    src_ip: u32,
    dst_ip: u32,
    src_port: u16,
    dst_port: u16,

    // Sequence & Acknowledgment Tracking
    rcv_nxt: u32,
    snd_nxt: u32,
    snd_una: u32,
    c_isn: u32,
    s_isn: u32,

    // File Descriptor, Window, Deadline, State & Handshake
    socks_fd: i32,               // 4B
    deadline_ms: i64,            // 8B (state-specific timeout)
    peer_wnd: u16,               // 2B
    state: State,                // 1B
    is_blocked: bool,            // 1B
    client_fin: bool,            // 1B
    upstream_fin: bool,          // 1B
    tun_blocked: bool,           // 1B
    hs: socks5.Handshake,        // 24B (phase, offset, 22B response buffer)
    _pad: [1]u8 = [_]u8{0} ** 1, // 1B
    // Total struct size: 80 bytes (fits in two 64B cache lines)
};
```

### 7.2 Memory Budget Sizing (1024 Concurrent Flows + 256 UDP Sessions)

| Component | Sizing Formula | Static Footprint |
| :--- | :--- | :--- |
| **TCP Flow Slab Table** | 1024 entries × 80 bytes/entry | **80 KiB (L2 D-Cache Resident)** |
| **Payload Buffers** | None; TCP retransmission and `MSG_PEEK` are the queues | **0 KiB** |
| **UDP Session Table** | 256 entries × 24 bytes/entry (Packed) | **6 KiB** |
| **DNS Query Tracker** | 64 entries × 24 bytes (TTL + FIFO ring) | **1.5 KiB** |
| **I/O Packet Buffers** | 3 buffers × 4096 bytes (RX / TX / UDP Relay) | **12 KiB** |
| **Total Resident Memory (BSS)** | Statically pinned in BSS (`initInto`) | **~99.5 KiB (< 0.1 MiB)** |

> **Conclusion**: The entire state machine, including the incremental handshake parser, lifecycle timers, and UDP/DNS tracking, strictly fits within **< 100 KiB total static memory** (actual Android app PSS growth is practically flat at 0 KiB / connection), completely immune to dynamic heap allocations and GC pauses.

---

## 8. Verification & Test Harness Plan

Verification follows a multi-stage deterministic test pipeline:

```
[ Stage 1: Linux User-Namespace Harness (unshare -r -n) ]
    ├── Automated curl GET/POST against local SOCKS5
    ├── iperf3 single-stream & 16-worker multi-stream saturation
    ├── Zero-Window verification via simulated upstream throttling
    └── AddressSanitizer leak & bounds verification

[ Stage 2: Abnormal Network Boundary Suite ]
    ├── Upstream SOCKS5 abrupt termination -> Assert clean client RST
    ├── Client RST injection -> Assert socks_fd closed without leaks
    ├── Half-close validation (asymmetric close)
    └── Port reuse churn (rapid sequential connections) -> LRU tombstone eviction check

[ Stage 3: Android Device Verification & Benchmark Suite ]
    ├── SimpleXray TProxyService integration via JNI bridge (libsimpletun.so)
    ├── End-to-end HTTP/HTTPS dual-stack validation (curl www.baidu.com)
    ├── CPU idle inspection (top shows 0.0% CPU suspended in epoll_wait)
    └── Standardized benchmark runner execution:
        python3 tools/benchmark.py --preset light --backends simpletun
```
