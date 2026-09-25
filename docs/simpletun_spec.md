# SimpleTUN Architecture & State Machine Specification

**Version**: 0.1-draft  
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
 [ tun_fd ] (L3 Point-to-Point Virtual Interface)
       ^
       | read() / writev()
       v
 +---------------------------------------------------------+
 | SimpleTUN (User-space Protocol Shifter)                 |
 |  - IPv4 Packet Demux & Checksum Calculation             |
 |  - Conservative TCP State Machine & Flow Control        |
 |  - Per-flow Zero-Window Backpressure                    |
 |  - Zero-Allocation Static Slab Session Table            |
 +---------------------------------------------------------+
       ^
       | non-blocking stream I/O (epoll)
       v
 [ socks5_fd ] (Local TCP Socket)
       ^
       v
 +---------------------------------------------------------+
 | Local SOCKS5 Inbound (Xray-core / 127.0.0.1)            |
 +---------------------------------------------------------+
```

### 1.1 In-Scope (Phase 1)
- Single TUN file descriptor input (`tun_fd`).
- IPv4 protocol parsing and packet synthesis.
- TCP stream translation to local SOCKS5 client (`NO AUTHENTICATION REQUIRED`, `CMD 0x01 CONNECT`).
- Conservative 3-way handshake (local SOCKS5 upstream establishment before client `SYN-ACK` generation).
- Explicit per-flow backpressure via standard TCP zero-window signaling (`Win=0`) and persist probe handling.
- Deterministic connection tear-down (`FIN` half-close propagation, `RST` teardown, short-lived session tombstones).
- Bounded memory footprint via statically allocated slab allocators.

### 1.2 Out-of-Scope (Deferred to Future Phases)
- **IPv6 parsing & synthesis** (Deferred to Phase 2).
- **UDP ASSOCIATE & UDP NAT** (Deferred to Phase 3).
- **0-RTT SYN spoofing with speculative payload buffering** (Deferred to Phase 2).
- **ICMP processing**: All ICMP packets are silently dropped.
- **IP reassembly & fragmentation**: Path MTU discovery is assumed; fragmented IP packets are dropped.
- **General routing tables**: Destination IPv4 and port are extracted directly from headers and mapped to SOCKS5 targets.

---

## 2. Protocol & Header Handling

### 2.1 IPv4 Constraints
- Fixed 20-byte IPv4 header support. Packets with `IHL > 5` (IPv4 options) are dropped.
- `Total Length` validation against buffer read size.
- Standard 16-bit one's complement checksum verification on RX; recalculated on TX.

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
                         Create Flow in STATIC_SLAB
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
                          v         v
               [ CLIENT_CLOSE_1 ]   [ UPSTREAM_CLOSED ]
                          \         /
                   Both directions closed
                            |
                            v
                       [ TOMBSTONE ] (2~5 seconds)
                            |
                         Timeout
                            v
                        [ FREE ]
```

### 3.2 State Definitions

| State | Description | Kernel Interface (TUN) | SOCKS5 Interface |
| :--- | :--- | :--- | :--- |
| `FREE` | Unallocated slot in static slab. | Inactive | Inactive |
| `UPSTREAM_CONNECT` | Received `SYN`. Connecting to `127.0.0.1` and negotiating SOCKS5 handshake. | Do not send `SYN-ACK` yet. If client retransmits `SYN`, drop duplicate or ignore. | Non-blocking connect + SOCKS5 handshake in progress. |
| `ESTABLISHED` | SOCKS5 handshake succeeded. `SYN-ACK` sent, client `ACK` received. Bi-directional data transfer active. | Normal `ACK` progression. Backpressure signals evaluated per packet. | Bi-directional streaming via non-blocking read/write. |
| `CLIENT_CLOSE_1` | Client sent `FIN`. Half-close acknowledged. | Replied `ACK` (`seq = fin_seq + 1`). Further data rejected with `RST`. | Call `shutdown(socks_fd, SHUT_WR)`. Continues reading downstream data. |
| `UPSTREAM_CLOSED` | SOCKS5 socket hit `EOF`. Upstream closed sending. | Send `FIN` to client kernel. Await client final `ACK`. | Socket closed locally. |
| `TOMBSTONE` | Flow fully closed. 4-tuple locked for 2~5 seconds to absorb late-arriving packets. | Dropped or answered with current sequence `RST` if out of window. | Deallocated. |

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
  - If `write(socks_fd, payload)` returns `EAGAIN` / `EWOULDBLOCK`, the unwritten bytes must be temporarily preserved in a per-flow bounded scratch buffer (maximum 1 MTU).
  - The flow sets its internal flag `is_blocked = true`.
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
- Flush remaining scratch buffer bytes to `socks_fd`.
- Upon successful flush, clear `is_blocked = false`.
- Construct and transmit an unsolicited Window Update packet via `tun_fd`:
  - `Flags`: `ACK`
  - `Seq`: `snd_nxt`
  - `Ack`: `rcv_nxt`
  - `Window`: `65535`

---

## 6. Teardown, RST Handling, and Session Tombstones

### 6.1 Clean Teardown (FIN Handshake)
1. **Client Closes First**:
   - Client sends `FIN (seq = f_seq)`.
   - Advance `rcv_nxt = f_seq + 1`. Send immediate `ACK (ack = rcv_nxt)`.
   - Call `shutdown(socks_fd, SHUT_WR)`. Transition to `CLIENT_CLOSE_1`.
   - Continue reading remaining downstream data from `socks_fd` and writing to `tun_fd`.
   - When `read(socks_fd)` returns 0 (`EOF`), send `FIN` to `tun_fd`.
   - Transition to `TOMBSTONE`.

2. **Upstream Closes First**:
   - `read(socks_fd)` returns 0.
   - Send `FIN` to `tun_fd` (`seq = snd_nxt`). Advance `snd_nxt += 1`.
   - Transition to `UPSTREAM_CLOSED`.
   - Client replies `ACK`. Transition to `TOMBSTONE`.

### 6.2 Abrupt Teardown (RST)
- If client sends `RST`: Validate sequence number within current window. If valid, close `socks_fd` immediately and mark slot `FREE`.
- If upstream socket produces `ECONNRESET` or unrecoverable error: Send `RST` to `tun_fd` (`Seq = snd_nxt`), close `socks_fd`, transition to `TOMBSTONE`.

### 6.3 Tombstone Slot Preservation
- When a flow reaches termination, releasing the 4-tuple immediately introduces collision risks if delayed packets reside in the kernel or TUN queues.
- Terminated flows transition to `TOMBSTONE` for a fixed duration of **3 seconds**.
- While in `TOMBSTONE`:
  - Any duplicate `FIN` or data retransmissions are answered with identical trailing `ACK` or `RST`.
  - New `SYN` packets with the exact same 4-tuple matching higher sequence numbers may preemptively reclaim the slot (RFC 1122 fast recycle).
- An intrusive timer wheel or sweep loop evicts expired tombstones back to `FREE`.

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

    // Sequence & Acknowledgment Tracking (16 Bytes)
    rcv_nxt: u32,
    snd_nxt: u32,
    s_isn: u32,
    c_isn: u32,

    // File Descriptor & State (12 Bytes)
    socks_fd: i32,
    state: State,
    is_blocked: bool,
    tombstone_until_ms: u64,

    // Single-Packet Overflow Buffer for Zero-Window Backpressure (2048 Bytes)
    overflow_len: u16,
    overflow_buf: [2048]u8,
};
```

### 7.2 Memory Budget Sizing (512 Concurrent Flows)

| Component | Sizing Formula | Static Footprint |
| :--- | :--- | :--- |
| **Flow Slab Table** | 512 entries × ~2,100 bytes/entry | **~1,075 KB (1.05 MB)** |
| **Lookup Hash Index** | 1024 bucket heads (u16 index) | **2 KB** |
| **I/O Packet Buffer (RX/TX)** | 2 buffers × 2048 bytes (Scratch) | **4 KB** |
| **Epoll Event Array** | 512 `struct epoll_event` (64B) | **32 KB** |
| **Total Resident Memory (PSS)** | Statically pinned / BSS | **~1.12 MB** |

> **Conclusion**: The entire state machine, including per-flow backpressure scratch buffers, strictly fits within **< 1.5 MB PSS**, completely immune to unbounded allocation spikes.

---

## 8. Verification & Test Harness Plan

Verification follows a multi-stage deterministic test pipeline:

```
[ Stage 1: Linux User-Namespace Harness (unshare -r -n) ]
    ├── Automated curl GET/POST against local SOCKS5
    ├── iperf3 single-stream & 16-worker multi-stream saturation
    ├── Zero-Window verification via iptables/tc simulated upstream throttling
    └── Valgrind / AddressSanitizer leak & bounds verification

[ Stage 2: Abnormal Network Boundary Suite ]
    ├── Upstream SOCKS5 abrupt termination (SIGKILL) -> Assert clean client RST
    ├── Client RST injection -> Assert socks_fd closed without leaks
    ├── Half-close validation (curl --limit-rate upload/download asymmetric close)
    └── Port reuse churn (10,000 rapid sequential connections) -> Tombstone collision check
```
