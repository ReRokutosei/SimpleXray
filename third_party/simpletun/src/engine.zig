const std = @import("std");
const linux = std.os.linux;
const protocol = @import("protocol.zig");
const flow_mod = @import("flow.zig");
const socks5 = @import("socks5.zig");
const sys = @import("sys.zig");

const EPOLLRDHUP: u32 = 0x2000;
const O_NONBLOCK: usize = 0x800;
const FLOW_INDEX_OFFSET: u32 = 0x1000;

const CONNECT_TIMEOUT_MS: i64 = 10_000;
const IDLE_TIMEOUT_MS: i64 = 30 * 60 * 1000;
const TOMBSTONE_MS: i64 = 3_000;
const FIN_WAIT_TIMEOUT_MS: i64 = 30_000;
const SWEEP_INTERVAL_MS: i64 = 500;
const UDP_SESSION_IDLE_MS: i64 = 30_000;
const DNS_QUERY_TTL_MS: i64 = 10_000;
const UDP_ASSOC_TIMEOUT_MS: i64 = 5_000;
const UDP_PREWARM_MS: i64 = 10_000;

const HIGH_WATERMARK: usize = 820; // ~80% of CAPACITY (1024)
const CRITICAL_WATERMARK: usize = 940; // ~92% of CAPACITY (1024)
const ADAPTIVE_IDLE_HIGH_MS: i64 = 180 * 1000; // 3 minutes under high watermark
const ADAPTIVE_IDLE_CRITICAL_MS: i64 = 60 * 1000; // 1 minute under critical watermark
const ADAPTIVE_CONNECT_HIGH_MS: i64 = 3_000; // 3 seconds under high watermark
const ADAPTIVE_CONNECT_CRITICAL_MS: i64 = 1_500; // 1.5 seconds under critical watermark

fn logCore(prio: c_int, comptime fmt: []const u8, args: anytype) void {
    _ = prio;
    _ = fmt;
    _ = args;
}

pub const EngineConfig = struct {
    tun_fd: sys.fd_t,
    socks_ip: u32 = 0x7f000001, // 127.0.0.1
    socks_port: u16 = 10808,
    mtu: u16 = 1500,
};

pub const Engine = struct {
    cfg: EngineConfig,
    epoll_fd: sys.fd_t,
    table: flow_mod.FlowTable,
    udp_table: flow_mod.UdpTable,
    udp_ctrl_fd: sys.fd_t,
    udp_relay_fd: sys.fd_t,
    udp_relay_port: u16,
    last_udp_associate_fail_ms: i64,
    udp_hs: socks5.Handshake,
    udp_assoc_pending: bool,
    udp_assoc_deadline_ms: i64,
    udp_retry_until_ms: i64,
    stop_fd: sys.fd_t,
    running: bool,
    tun_out_armed: bool,
    last_sweep_ms: i64,

    // Reusable I/O buffers (Single allocation, 0 dynamic malloc in fast path, aligned for IP/TCP headers)
    rx_packet_buf: [4096]u8 align(4),
    tx_packet_buf: [4096]u8 align(4),
    udp_scratch_buf: [4096]u8 align(4),

    pub fn initInto(self: *Engine, cfg: EngineConfig) !void {
        // Ensure tun_fd is non-blocking to prevent thread lockup if buffers fill up
        const tun_flags = linux.fcntl(cfg.tun_fd, linux.F.GETFL, 0);
        _ = linux.fcntl(cfg.tun_fd, linux.F.SETFL, tun_flags | O_NONBLOCK);

        const ep_rc = linux.epoll_create1(linux.EPOLL.CLOEXEC);
        if (linux.errno(ep_rc) != .SUCCESS) return error.EpollCreationFailed;
        const epoll_fd: sys.fd_t = @intCast(ep_rc);
        errdefer sys.close(epoll_fd);

        var event = linux.epoll_event{
            .events = linux.EPOLL.IN,
            .data = .{ .fd = cfg.tun_fd },
        };
        const ctl_rc = linux.epoll_ctl(epoll_fd, linux.EPOLL.CTL_ADD, cfg.tun_fd, &event);
        if (linux.errno(ctl_rc) != .SUCCESS) return error.EpollCtlFailed;

        // Create local UDP socket for relay exchange
        const udp_relay_fd = try sys.createUdpSocket();
        errdefer sys.close(udp_relay_fd);

        var udp_ev = linux.epoll_event{
            .events = linux.EPOLL.IN,
            .data = .{ .fd = udp_relay_fd },
        };
        _ = linux.epoll_ctl(epoll_fd, linux.EPOLL.CTL_ADD, udp_relay_fd, &udp_ev);

        // Create stop eventfd
        const stop_fd = try sys.createEventFd();
        errdefer sys.close(stop_fd);

        var stop_ev = linux.epoll_event{
            .events = linux.EPOLL.IN,
            .data = .{ .fd = stop_fd },
        };
        _ = linux.epoll_ctl(epoll_fd, linux.EPOLL.CTL_ADD, stop_fd, &stop_ev);

        self.cfg = cfg;
        self.epoll_fd = epoll_fd;
        self.table.initInto();
        self.udp_table.initInto();
        self.udp_ctrl_fd = -1;
        self.udp_relay_fd = udp_relay_fd;
        self.udp_relay_port = 0;
        self.last_udp_associate_fail_ms = 0;
        self.udp_hs.reset();
        self.udp_assoc_pending = false;
        self.udp_assoc_deadline_ms = 0;
        self.udp_retry_until_ms = sys.monotonicMs() + UDP_PREWARM_MS;
        self.stop_fd = stop_fd;
        self.running = true;
        self.tun_out_armed = false;
        self.last_sweep_ms = 0;
        self.beginUdpAssociate();
    }

    pub fn init(cfg: EngineConfig) !Engine {
        var eng: Engine = undefined;
        try eng.initInto(cfg);
        return eng;
    }

    pub fn deinit(self: *Engine) void {
        sys.close(self.epoll_fd);
        if (self.stop_fd >= 0) sys.close(self.stop_fd);
        if (self.udp_ctrl_fd >= 0) sys.close(self.udp_ctrl_fd);
        if (self.udp_relay_fd >= 0) sys.close(self.udp_relay_fd);
        for (&self.table.flows) |*flow| {
            if (flow.socks_fd >= 0) {
                sys.close(flow.socks_fd);
                flow.socks_fd = -1;
            }
        }
    }

    pub fn stop(self: *Engine) void {
        logCore(4, "Engine.stop() called! setting self.running = false, stop_fd={d}", .{self.stop_fd});
        self.running = false;
        if (self.stop_fd >= 0) {
            sys.signalEventFd(self.stop_fd);
        }
    }

    pub fn closeFlow(self: *Engine, flow: *flow_mod.Flow, tombstone_ms: i64) void {
        if (flow.tun_blocked) {
            flow.tun_blocked = false;
            self.armTunEvents();
        }
        if (flow.socks_fd >= 0) {
            _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_DEL, flow.socks_fd, null);
            sys.close(flow.socks_fd);
            flow.socks_fd = -1;
        }
        const active = self.table.activeCount();
        const effective_tombstone: i64 = if (active >= CRITICAL_WATERMARK)
            100
        else if (active >= HIGH_WATERMARK)
            @min(tombstone_ms, 500)
        else
            tombstone_ms;
        self.table.markTombstone(flow, effective_tombstone);
    }

    fn anyTunBlocked(self: *const Engine) bool {
        for (&self.table.flows) |*flow| {
            if (flow.tun_blocked) return true;
        }
        return false;
    }

    fn armTunEvents(self: *Engine) void {
        const want_out = self.anyTunBlocked();
        if (want_out == self.tun_out_armed) return;
        var ev = linux.epoll_event{
            .events = if (want_out) (linux.EPOLL.IN | linux.EPOLL.OUT) else linux.EPOLL.IN,
            .data = .{ .fd = self.cfg.tun_fd },
        };
        _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_MOD, self.cfg.tun_fd, &ev);
        self.tun_out_armed = want_out;
    }

    fn canSendDownstream(flow: *const flow_mod.Flow) bool {
        if (flow.tun_blocked) return false;
        const inflight = flow.snd_nxt -% flow.snd_una;
        return inflight < @as(u32, flow.peer_wnd);
    }

    fn refreshSocksEvents(self: *Engine, flow: *flow_mod.Flow) void {
        var events: u32 = linux.EPOLL.ERR;
        if (canSendDownstream(flow)) events |= linux.EPOLL.IN;
        if (flow.is_blocked) events |= linux.EPOLL.OUT;
        self.armSocksEvents(flow, events);
    }

    fn markTunBlocked(self: *Engine, flow: *flow_mod.Flow) void {
        if (flow.tun_blocked) return;
        flow.tun_blocked = true;
        self.refreshSocksEvents(flow);
        self.armTunEvents();
    }

    fn clearTunBlocked(self: *Engine, flow: *flow_mod.Flow) void {
        if (!flow.tun_blocked) return;
        flow.tun_blocked = false;
        self.refreshSocksEvents(flow);
        self.armTunEvents();
    }

    fn flushTunBlocked(self: *Engine) void {
        for (&self.table.flows) |*flow| {
            if (flow.state == .established and flow.tun_blocked) {
                self.clearTunBlocked(flow);
                self.pumpDownstream(flow);
            }
        }
    }

    fn closeSocksFdOnly(self: *Engine, flow: *flow_mod.Flow) void {
        if (flow.tun_blocked) {
            flow.tun_blocked = false;
            self.armTunEvents();
        }
        flow.is_blocked = false;
        if (flow.socks_fd >= 0) {
            _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_DEL, flow.socks_fd, null);
            sys.close(flow.socks_fd);
            flow.socks_fd = -1;
        }
    }

    fn sendUpstreamFin(self: *Engine, flow: *flow_mod.Flow) void {
        if (flow.upstream_fin) return;
        const win_to_announce: u16 = if (flow.is_blocked) 0 else 65535;
        self.sendTcpPacket(flow, protocol.TcpHeader.FLAG_FIN | protocol.TcpHeader.FLAG_ACK, flow.snd_nxt, flow.rcv_nxt, win_to_announce, null);
        flow.snd_nxt +%= 1;
        flow.upstream_fin = true;
        flow.deadline_ms = sys.monotonicMs() + FIN_WAIT_TIMEOUT_MS;
        self.closeSocksFdOnly(flow);
        if (flow.client_fin) {
            self.closeFlow(flow, TOMBSTONE_MS);
        }
    }

    fn touchFlow(self: *Engine, flow: *flow_mod.Flow) void {
        _ = self;
        if (!flow.upstream_fin) {
            flow.deadline_ms = sys.monotonicMs() + IDLE_TIMEOUT_MS;
        }
    }

    fn sweepTimers(self: *Engine) void {
        const now = sys.monotonicMs();
        if (now - self.last_sweep_ms < SWEEP_INTERVAL_MS) return;
        self.last_sweep_ms = now;

        const active = self.table.activeCount();
        const is_critical = active >= CRITICAL_WATERMARK;
        const is_high = active >= HIGH_WATERMARK;

        for (&self.table.flows) |*flow| {
            switch (flow.state) {
                .free => {},
                .tombstone => {
                    const tombstone_slack: i64 = if (is_critical)
                        2900
                    else if (is_high)
                        2500
                    else
                        0;

                    if (now + tombstone_slack >= flow.deadline_ms) {
                        if (flow.tun_blocked) {
                            flow.tun_blocked = false;
                            self.armTunEvents();
                        }
                        self.table.resetFlow(flow);
                    }
                },
                .upstream_connect, .socks5_auth_wait, .socks5_connect_wait => {
                    const connect_limit = if (is_critical)
                        ADAPTIVE_CONNECT_CRITICAL_MS
                    else if (is_high)
                        ADAPTIVE_CONNECT_HIGH_MS
                    else
                        CONNECT_TIMEOUT_MS;

                    const elapsed = now - (flow.deadline_ms - CONNECT_TIMEOUT_MS);
                    if (now >= flow.deadline_ms or elapsed >= connect_limit) {
                        self.sendRst(flow);
                        self.closeFlow(flow, if (is_high) 100 else TOMBSTONE_MS);
                    }
                },
                .established => {
                    const idle_limit = if (is_critical)
                        ADAPTIVE_IDLE_CRITICAL_MS
                    else if (is_high)
                        ADAPTIVE_IDLE_HIGH_MS
                    else
                        IDLE_TIMEOUT_MS;

                    const idle_elapsed = now - (flow.deadline_ms - IDLE_TIMEOUT_MS);
                    if (now >= flow.deadline_ms or idle_elapsed >= idle_limit) {
                        self.sendRst(flow);
                        self.closeFlow(flow, if (is_high) 100 else TOMBSTONE_MS);
                    }
                },
            }
        }

        if (self.udp_assoc_pending and now >= self.udp_assoc_deadline_ms) {
            self.abortUdpAssociate(now);
        }
        if (self.udp_relay_port == 0 and !self.udp_assoc_pending and now <= self.udp_retry_until_ms) {
            self.beginUdpAssociate();
        }

        self.udp_table.sweep(now, UDP_SESSION_IDLE_MS);
    }

    fn updateSendWindow(self: *Engine, flow: *flow_mod.Flow, ack: u32, window: u16) void {
        _ = self;
        if (protocol.seqBetween(flow.snd_una, ack, flow.snd_nxt)) {
            flow.snd_una = ack;
            flow.peer_wnd = window;
        }
    }

    fn rstSeqValid(flow: *const flow_mod.Flow, seq: u32) bool {
        return protocol.seqBetween(flow.rcv_nxt -% 65535, seq, flow.rcv_nxt);
    }

    fn handleUpstreamFinPacket(self: *Engine, flow: *flow_mod.Flow, flags: u8, seq: u32, payload: []const u8, ack: u32, window: u16) void {
        if ((flags & protocol.TcpHeader.FLAG_ACK) != 0) {
            self.updateSendWindow(flow, ack, window);
        }

        if ((flags & protocol.TcpHeader.FLAG_FIN) != 0) {
            if (!flow.client_fin) {
                const payload_len: u32 = @intCast(payload.len);
                const seq_delta = @as(i32, @bitCast(seq -% flow.rcv_nxt));
                if (seq_delta <= 0) {
                    const already_received = flow.rcv_nxt -% seq;
                    if (already_received < payload_len) {
                        // Consume the payload that accompanies FIN, including
                        // the unseen tail of a partially retransmitted segment.
                        flow.rcv_nxt +%= payload_len - already_received;
                    }
                    // FIN consumes the next sequence number after the payload.
                    // This is true for the initial in-order segment and for a
                    // retransmitted FIN after its payload was already consumed.
                    if (seq +% payload_len == flow.rcv_nxt) {
                        flow.rcv_nxt +%= 1;
                        flow.client_fin = true;
                    }
                }
            }
            flow.deadline_ms = sys.monotonicMs() + FIN_WAIT_TIMEOUT_MS;
            self.sendTcpPacket(flow, protocol.TcpHeader.FLAG_ACK, flow.snd_nxt, flow.rcv_nxt, 65535, null);
            if (flow.snd_una == flow.snd_nxt) {
                self.closeFlow(flow, TOMBSTONE_MS);
            }
            return;
        }

        if (flow.snd_una == flow.snd_nxt) {
            self.closeFlow(flow, TOMBSTONE_MS);
            return;
        }

        if (payload.len > 0) {
            if (seq == flow.rcv_nxt) flow.rcv_nxt +%= @intCast(payload.len);
            flow.deadline_ms = sys.monotonicMs() + FIN_WAIT_TIMEOUT_MS;
            self.sendTcpPacket(flow, protocol.TcpHeader.FLAG_ACK, flow.snd_nxt, flow.rcv_nxt, 65535, null);
        }
    }

    fn pumpDownstream(self: *Engine, flow: *flow_mod.Flow) void {
        if (flow.state != .established) return;
        const fd = flow.socks_fd;
        const max_mss: usize = @min(
            if (self.cfg.mtu > 40) self.cfg.mtu - 40 else 1460,
            self.tx_packet_buf.len - 40,
        );

        var batch: usize = 0;
        while (canSendDownstream(flow)) : (batch += 1) {
            if (batch >= 16) break;
            const inflight = flow.snd_nxt -% flow.snd_una;
            const available = @as(u32, flow.peer_wnd) - inflight;
            const want: usize = @min(max_mss, @as(usize, available));
            if (want == 0) break;

            const n = sys.recvPeek(fd, self.tx_packet_buf[40 .. 40 + want]) catch |err| {
                if (err == error.WouldBlock) break;
                self.sendRst(flow);
                self.closeFlow(flow, TOMBSTONE_MS);
                return;
            };
            if (n == 0) {
                self.sendUpstreamFin(flow);
                return;
            }

            self.sendTcpPacketDirect(flow, protocol.TcpHeader.FLAG_ACK | protocol.TcpHeader.FLAG_PSH, flow.snd_nxt, flow.rcv_nxt, flow.peer_wnd, n) catch |err| {
                if (err == error.WouldBlock) {
                    self.markTunBlocked(flow);
                    return;
                }
                self.sendRst(flow);
                self.closeFlow(flow, TOMBSTONE_MS);
                return;
            };

            const consumed = sys.read(fd, self.tx_packet_buf[40 .. 40 + n]) catch {
                self.sendRst(flow);
                self.closeFlow(flow, TOMBSTONE_MS);
                return;
            };
            if (consumed != n) {
                self.sendRst(flow);
                self.closeFlow(flow, TOMBSTONE_MS);
                return;
            }
            flow.snd_nxt +%= @intCast(n);
            self.touchFlow(flow);

            if (n < want) break; // socket receive buffer drained
        }

        self.refreshSocksEvents(flow);
    }

    pub fn run(self: *Engine) !void {
        var events: [64]linux.epoll_event = undefined;
        logCore(4, "Engine.run entering loop: epoll_fd={d}, tun_fd={d}, stop_fd={d}", .{ self.epoll_fd, self.cfg.tun_fd, self.stop_fd });

        while (self.running) {
            const ep_rc = linux.epoll_wait(self.epoll_fd, &events, 64, 100);
            const err = linux.errno(ep_rc);
            if (err != .SUCCESS) {
                if (err == .INTR) continue;
                logCore(6, "Engine.run epoll_wait returned error: {}", .{err});
                return error.EpollWaitFailed;
            }

            const count: usize = ep_rc;
            for (events[0..count]) |ev| {
                const raw_data = ev.data.u32;
                if (raw_data >= FLOW_INDEX_OFFSET) {
                    // O(1) SOCKS Flow Event dispatch
                    const flow_idx = raw_data - FLOW_INDEX_OFFSET;
                    if (flow_idx < flow_mod.FlowTable.CAPACITY) {
                        const flow = &self.table.flows[flow_idx];
                        if (flow.state != .free and flow.socks_fd >= 0) {
                            self.handleSocksEvent(flow, ev.events);
                        }
                    }
                    continue;
                }

                const fd: sys.fd_t = @intCast(raw_data);
                if (fd == self.stop_fd) {
                    logCore(4, "Engine.run stop_fd triggered with events=0x{x}", .{ev.events});
                    self.running = false;
                    return;
                } else if (fd == self.cfg.tun_fd) {
                    if ((ev.events & linux.EPOLL.OUT) != 0) self.flushTunBlocked();
                    if ((ev.events & linux.EPOLL.IN) != 0) self.handleTunRead();
                } else if (fd == self.udp_relay_fd) {
                    self.handleUdpRelayRead();
                } else if (fd == self.udp_ctrl_fd) {
                    self.handleUdpCtrlEvent(ev.events);
                }
            }

            self.sweepTimers();
        }
        logCore(4, "Engine.run while loop terminated, self.running={}", .{self.running});
    }

    fn armUdpCtrlEvents(self: *Engine, events: u32) void {
        if (self.udp_ctrl_fd < 0) return;
        var ev = linux.epoll_event{
            .events = events,
            .data = .{ .fd = self.udp_ctrl_fd },
        };
        _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_MOD, self.udp_ctrl_fd, &ev);
    }

    fn abortUdpAssociate(self: *Engine, now: i64) void {
        if (self.udp_ctrl_fd >= 0) {
            _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_DEL, self.udp_ctrl_fd, null);
            sys.close(self.udp_ctrl_fd);
            self.udp_ctrl_fd = -1;
        }
        self.udp_assoc_pending = false;
        self.udp_relay_port = 0;
        self.last_udp_associate_fail_ms = now;
    }

    fn beginUdpAssociate(self: *Engine) void {
        if (self.udp_ctrl_fd >= 0 or self.udp_assoc_pending) return;
        const now = sys.monotonicMs();
        if (now - self.last_udp_associate_fail_ms < 2000) return;

        const sock = sys.createTcpSocket() catch {
            self.last_udp_associate_fail_ms = now;
            return;
        };
        self.udp_ctrl_fd = sock;
        self.udp_assoc_pending = true;
        self.udp_assoc_deadline_ms = now + UDP_ASSOC_TIMEOUT_MS;
        self.udp_hs.beginGreeting();

        sys.connect(sock, self.cfg.socks_ip, self.cfg.socks_port) catch |err| {
            if (err != error.ConnectionPending) {
                self.abortUdpAssociate(now);
                return;
            }
        };

        var ev = linux.epoll_event{
            .events = linux.EPOLL.OUT | linux.EPOLL.IN | linux.EPOLL.ERR,
            .data = .{ .fd = sock },
        };
        const ctl_rc = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_ADD, sock, &ev);
        if (linux.errno(ctl_rc) != .SUCCESS) {
            self.abortUdpAssociate(now);
        }
    }

    fn udpAssociateSucceeded(self: *Engine, relay_port: u16) void {
        self.udp_assoc_pending = false;
        self.udp_relay_port = relay_port;
        self.last_udp_associate_fail_ms = 0;
        self.armUdpCtrlEvents(linux.EPOLL.IN | linux.EPOLL.ERR | linux.EPOLL.HUP | EPOLLRDHUP);
    }

    fn ensureUdpAssociate(self: *Engine) void {
        if (self.udp_relay_port != 0 or self.udp_assoc_pending) return;
        self.udp_retry_until_ms = sys.monotonicMs() + UDP_PREWARM_MS;
        // A successful SOCKS5 TCP handshake proves the local inbound is ready.
        // Force an immediate UDP ASSOCIATE attempt instead of waiting for backoff.
        self.last_udp_associate_fail_ms = 0;
        self.beginUdpAssociate();
    }

    fn handleUdpAssociateEvent(self: *Engine, events: u32) void {
        const fd = self.udp_ctrl_fd;
        if ((events & (linux.EPOLL.ERR | linux.EPOLL.HUP | EPOLLRDHUP)) != 0) {
            self.abortUdpAssociate(sys.monotonicMs());
            return;
        }

        if (self.udp_hs.phase == .greeting_tx or self.udp_hs.phase == .request_tx) {
            if ((events & linux.EPOLL.OUT) == 0) return;
            self.driveHandshakeTx(fd, &self.udp_hs) catch |err| {
                if (err == error.WouldBlock) return;
                self.abortUdpAssociate(sys.monotonicMs());
                return;
            };
            if (self.udp_hs.phase == .greeting_tx) {
                self.udp_hs.beginAuth();
                self.armUdpCtrlEvents(linux.EPOLL.IN | linux.EPOLL.ERR);
            } else {
                self.udp_hs.beginReply();
                self.armUdpCtrlEvents(linux.EPOLL.IN | linux.EPOLL.ERR);
            }
            return;
        }

        if ((events & linux.EPOLL.IN) == 0) return;

        if (self.udp_hs.phase == .auth_rx) {
            self.driveHandshakeRx(fd, &self.udp_hs) catch |err| {
                if (err == error.WouldBlock) return;
                self.abortUdpAssociate(sys.monotonicMs());
                return;
            };
            const resp = self.udp_hs.incoming();
            if (resp.len != 2 or resp[0] != 0x05 or resp[1] != 0x00) {
                self.abortUdpAssociate(sys.monotonicMs());
                return;
            }
            self.udp_hs.beginUdpAssociateRequest();
            self.armUdpCtrlEvents(linux.EPOLL.OUT | linux.EPOLL.IN | linux.EPOLL.ERR);
            self.driveHandshakeTx(fd, &self.udp_hs) catch |err| {
                if (err == error.WouldBlock) return;
                self.abortUdpAssociate(sys.monotonicMs());
                return;
            };
            self.udp_hs.beginReply();
            self.armUdpCtrlEvents(linux.EPOLL.IN | linux.EPOLL.ERR);
        } else if (self.udp_hs.phase == .reply_rx) {
            self.driveHandshakeRx(fd, &self.udp_hs) catch |err| {
                if (err == error.WouldBlock) return;
                self.abortUdpAssociate(sys.monotonicMs());
                return;
            };
            const resp = self.udp_hs.incoming();
            if (resp.len != 10 or resp[0] != 0x05 or resp[1] != 0x00 or resp[3] != 0x01) {
                self.abortUdpAssociate(sys.monotonicMs());
                return;
            }
            const relay_port = (@as(u16, resp[8]) << 8) | @as(u16, resp[9]);
            self.udpAssociateSucceeded(relay_port);
        } else {
            self.abortUdpAssociate(sys.monotonicMs());
        }
    }

    fn handleUdpCtrlEvent(self: *Engine, events: u32) void {
        if (self.udp_ctrl_fd < 0) return;
        if (self.udp_assoc_pending) {
            self.handleUdpAssociateEvent(events);
            return;
        }

        var should_close = false;
        if ((events & (linux.EPOLL.ERR | linux.EPOLL.HUP | EPOLLRDHUP)) != 0) {
            should_close = true;
        } else if ((events & linux.EPOLL.IN) != 0) {
            var dummy: [16]u8 = undefined;
            const rn = sys.read(self.udp_ctrl_fd, &dummy) catch |err| blk: {
                if (err == error.WouldBlock) return;
                should_close = true;
                break :blk 0;
            };
            if (rn == 0) should_close = true;
        }
        if (should_close) {
            logCore(4, "Engine.run udp_ctrl_fd disconnected (events=0x{x})", .{events});
            _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_DEL, self.udp_ctrl_fd, null);
            sys.close(self.udp_ctrl_fd);
            self.udp_ctrl_fd = -1;
            self.udp_relay_port = 0;
            self.udp_assoc_pending = false;
        }
    }

    fn handleTunRead(self: *Engine) void {
        var batch: usize = 0;
        while (batch < 32) : (batch += 1) {
            const n = sys.read(self.cfg.tun_fd, &self.rx_packet_buf) catch |err| {
                if (err == error.WouldBlock) return;
                return;
            };
            if (n < 20) continue; // M-3: skip malformed/short packet, do not abort the entire read batch
            self.processTunPacket(n);
        }
    }

    fn processTunPacket(self: *Engine, n: usize) void {
        const ip_hdr: *const protocol.Ipv4Header = @ptrCast(@alignCast(&self.rx_packet_buf[0]));
        if (ip_hdr.version() != 4 or ip_hdr.ihl() != 5) return; // Fixed 20-byte IPv4 header only

        const ip_hlen = ip_hdr.headerLen();
        const ip_total_len: usize = ip_hdr.getTotalLen();
        if (n < ip_total_len or ip_total_len < ip_hlen) return;
        const fragment = std.mem.bigToNative(u16, ip_hdr.flags_fragment);
        if ((fragment & 0x3fff) != 0) return; // Drop every IPv4 fragment
        const valid_len: usize = ip_total_len;

        if (ip_hdr.protocol == 17) {
            // UDP Packet Forwarding
            const ip_payload_len: usize = ip_total_len - ip_hlen;
            if (ip_payload_len < 8) return;
            const udp_hdr: *const protocol.UdpHeader = @ptrCast(@alignCast(&self.rx_packet_buf[ip_hlen]));
            const total_hlen = ip_hlen + 8;
            if (valid_len < total_hlen) return;
            const udp_len = std.mem.bigToNative(u16, udp_hdr.length);
            if (@as(usize, udp_len) != ip_payload_len) return;

            const payload = self.rx_packet_buf[total_hlen..valid_len];
            const src_ip = ip_hdr.src_ip;
            const dst_ip = ip_hdr.dst_ip;
            const src_port = udp_hdr.src_port;
            const dst_port = udp_hdr.dst_port;

            // If DNS query (target port 53), extract DNS transaction ID (first 2 bytes of payload)
            const dst_port_native = std.mem.bigToNative(u16, dst_port);
            if (dst_port_native == 53 and payload.len >= 2) {
                const dns_tx_id = std.mem.readInt(u16, payload[0..2], .big);
                self.udp_table.recordDnsQuery(src_ip, dst_ip, src_port, dst_port, dns_tx_id, DNS_QUERY_TTL_MS);
            }

            // Touch or allocate UDP session in table
            _ = self.udp_table.touchOrAllocate(src_ip, dst_ip, src_port, dst_port);

            if (self.udp_relay_port == 0) {
                const now = sys.monotonicMs();
                self.udp_retry_until_ms = @max(self.udp_retry_until_ms, now + UDP_PREWARM_MS);
                self.beginUdpAssociate();
                return; // Drop until the asynchronous relay handshake completes.
            }

            // Pack SOCKS5 UDP header (10 bytes) + payload
            var socks5_udp_hdr: [10]u8 = undefined;
            _ = socks5.formatUdpHeader(&socks5_udp_hdr, dst_ip, dst_port);

            const send_len = 10 + payload.len;
            if (send_len <= self.udp_scratch_buf.len) {
                @memcpy(self.udp_scratch_buf[0..10], &socks5_udp_hdr);
                @memcpy(self.udp_scratch_buf[10..send_len], payload);

                _ = sys.sendto(
                    self.udp_relay_fd,
                    self.udp_scratch_buf[0..send_len],
                    self.cfg.socks_ip,
                    self.udp_relay_port,
                ) catch {};
            }
            return;
        }

        if (ip_hdr.protocol != 6) return; // TCP only below
        if (valid_len < ip_hlen + 20) return;

        const tcp_hdr: *const protocol.TcpHeader = @ptrCast(@alignCast(&self.rx_packet_buf[ip_hlen]));
        if (tcp_hdr.dataOffset() < 5) return;
        const tcp_hlen = tcp_hdr.headerLen();
        const total_hlen = ip_hlen + tcp_hlen;
        if (valid_len < total_hlen) return;

        const payload = self.rx_packet_buf[total_hlen..valid_len];
        const src_ip = ip_hdr.src_ip;
        const dst_ip = ip_hdr.dst_ip;
        const src_port = tcp_hdr.src_port;
        const dst_port = tcp_hdr.dst_port;
        const flags = tcp_hdr.flags;
        const seq = tcp_hdr.getSeq();

        const flow = self.table.findFlow(src_ip, dst_ip, src_port, dst_port);

        if (flow == null) {
            // New connection attempt
            if ((flags & protocol.TcpHeader.FLAG_SYN) != 0 and (flags & protocol.TcpHeader.FLAG_ACK) == 0) {
                self.handleNewSyn(src_ip, dst_ip, src_port, dst_port, seq);
            }
            return;
        }

        const f = flow.?;

        if (f.state == .tombstone) {
            if ((flags & protocol.TcpHeader.FLAG_SYN) != 0 and (flags & protocol.TcpHeader.FLAG_ACK) == 0) {
                self.table.resetFlow(f);
                self.handleNewSyn(src_ip, dst_ip, src_port, dst_port, seq);
                return;
            }
            if ((flags & protocol.TcpHeader.FLAG_RST) != 0) {
                self.table.resetFlow(f);
                return;
            }
            // Trailing ACK for late data/FIN retransmissions.
            self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, 0, null);
            return;
        }

        if ((flags & protocol.TcpHeader.FLAG_SYN) != 0 and (flags & protocol.TcpHeader.FLAG_ACK) == 0) {
            if (f.state == .established) {
                self.sendTcpPacket(f, protocol.TcpHeader.FLAG_SYN | protocol.TcpHeader.FLAG_ACK, f.s_isn, f.rcv_nxt, 65535, null);
                return;
            }
        }

        if ((flags & protocol.TcpHeader.FLAG_RST) != 0) {
            if (rstSeqValid(f, seq)) {
                self.closeFlow(f, TOMBSTONE_MS);
            }
            return;
        }

        if (f.state == .established) {
            if (f.upstream_fin) {
                self.handleUpstreamFinPacket(f, flags, seq, payload, tcp_hdr.getAck(), tcp_hdr.getWindow());
                return;
            }

            self.touchFlow(f);
            if ((flags & protocol.TcpHeader.FLAG_ACK) != 0) {
                self.updateSendWindow(f, tcp_hdr.getAck(), tcp_hdr.getWindow());
                self.pumpDownstream(f);
                if (f.state != .established) return;
            }

            // Check for Persist Probe (seq == rcv_nxt or seq == rcv_nxt - 1, payload len <= 1)
            const is_probe = (payload.len <= 1 and (seq == f.rcv_nxt or seq == f.rcv_nxt -% 1));
            if (f.is_blocked and is_probe) {
                self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, 0, null);
                return;
            }

            // If currently blocked by backpressure, refuse new payload without advancing rcv_nxt
            if (f.is_blocked and payload.len > 0) {
                self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, 0, null);
                return;
            }

            // Check overlap / merge retransmissions
            var effective_payload = payload;
            if (payload.len > 0) {
                const dist = @as(i32, @bitCast(seq -% f.rcv_nxt));
                if (dist < 0) {
                    const overlap = f.rcv_nxt -% seq;
                    if (overlap < payload.len) {
                        effective_payload = payload[overlap..];
                    } else {
                        // Fully duplicate packet
                        if ((flags & protocol.TcpHeader.FLAG_FIN) == 0) {
                            const win: u16 = if (f.is_blocked) 0 else 65535;
                            self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, win, null);
                            return;
                        }
                        effective_payload = payload[0..0];
                    }
                } else if (seq != f.rcv_nxt) {
                    // Out-of-order packet: do not buffer, send ACK with expected rcv_nxt
                    const win: u16 = if (f.is_blocked) 0 else 65535;
                    self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, win, null);
                    return;
                }
            }

            // Normal payload forward. On upstream backpressure we deliberately
            // do not buffer: only bytes accepted by the SOCKS socket are ACKed,
            // and the client TCP stack retransmits the rest after window reopen.
            if (effective_payload.len > 0) {
                const sent = sys.writeSocket(f.socks_fd, effective_payload) catch |err| {
                    if (err == error.WouldBlock) {
                        f.is_blocked = true;
                        self.refreshSocksEvents(f);
                        self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, 0, null);
                        return;
                    }
                    self.sendRst(f);
                    self.closeFlow(f, TOMBSTONE_MS);
                    return;
                };

                if (sent < effective_payload.len) {
                    f.is_blocked = true;
                    f.rcv_nxt +%= @intCast(sent);
                    self.refreshSocksEvents(f);
                    self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, 0, null);
                } else {
                    f.rcv_nxt +%= @intCast(effective_payload.len);
                    if ((flags & protocol.TcpHeader.FLAG_FIN) == 0) {
                        self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, 65535, null);
                    }
                }
            }

            // Handle client FIN with in-order sequence verification.
            if ((flags & protocol.TcpHeader.FLAG_FIN) != 0) {
                if (seq +% @as(u32, @intCast(payload.len)) == f.rcv_nxt) {
                    if (!f.client_fin) {
                        f.rcv_nxt +%= 1;
                        f.client_fin = true;
                        f.deadline_ms = sys.monotonicMs() + FIN_WAIT_TIMEOUT_MS;
                        sys.shutdown(f.socks_fd);
                    }
                }
                const win: u16 = if (f.is_blocked) 0 else 65535;
                self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, win, null);
            }
        }
    }

    fn handleNewSyn(self: *Engine, src_ip: u32, dst_ip: u32, src_port: u16, dst_port: u16, client_isn: u32) void {
        const flow = self.table.allocate(src_ip, dst_ip, src_port, dst_port) orelse return;
        self.armTunEvents();

        // C-2: If fourth-pass recycling returned a slot that still has a live socks_fd (flow.zig
        // intentionally did NOT close it so we can call epoll_ctl(DEL) here first), deregister and close it now.
        if (flow.socks_fd >= 0) {
            _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_DEL, flow.socks_fd, null);
            sys.close(flow.socks_fd);
            flow.socks_fd = -1;
        }

        flow.c_isn = client_isn;
        flow.rcv_nxt = client_isn +% 1;

        // Generate pseudorandom server ISN using linux.getrandom
        var rand_val: u32 = 0x12345678;
        _ = linux.getrandom(std.mem.asBytes(&rand_val).ptr, 4, 0);
        flow.s_isn = rand_val;
        flow.snd_nxt = rand_val +% 1;
        flow.snd_una = rand_val;
        flow.peer_wnd = 0;
        flow.deadline_ms = sys.monotonicMs() + CONNECT_TIMEOUT_MS;

        // Initiate non-blocking connect to local SOCKS5 inbound
        const sock = sys.createTcpSocket() catch {
            self.sendRst(flow);
            self.closeFlow(flow, 1000);
            return;
        };

        flow.socks_fd = sock;
        flow.state = .upstream_connect;
        flow.hs.beginGreeting();

        sys.connect(sock, self.cfg.socks_ip, self.cfg.socks_port) catch |err| {
            if (err != error.ConnectionPending) {
                self.sendRst(flow);
                self.closeFlow(flow, 1000);
                return;
            }
        };

        const flow_idx: u32 = @intCast(self.table.indexOf(flow));
        var event = linux.epoll_event{
            .events = linux.EPOLL.OUT | linux.EPOLL.IN | linux.EPOLL.ERR,
            .data = .{ .u32 = flow_idx + FLOW_INDEX_OFFSET },
        };
        const ctl_rc = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_ADD, sock, &event);
        if (linux.errno(ctl_rc) != .SUCCESS) {
            self.sendRst(flow);
            self.closeFlow(flow, 1000);
            return;
        }
    }

    fn armSocksEvents(self: *Engine, flow: *flow_mod.Flow, events: u32) void {
        const flow_idx: u32 = @intCast(self.table.indexOf(flow));
        var ev = linux.epoll_event{
            .events = events,
            .data = .{ .u32 = flow_idx + FLOW_INDEX_OFFSET },
        };
        _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_MOD, flow.socks_fd, &ev);
    }

    fn failSocksFlow(self: *Engine, flow: *flow_mod.Flow) void {
        self.sendRst(flow);
        self.closeFlow(flow, TOMBSTONE_MS);
    }

    fn driveHandshakeTx(self: *Engine, fd: sys.fd_t, hs: *socks5.Handshake) !void {
        _ = self;
        while (!hs.complete()) {
            const offset: usize = hs.have;
            const chunk = hs.outgoing()[offset..];
            const n = try sys.writeSocket(fd, chunk);
            if (n == 0) return error.Socks5WriteFailed;
            hs.commitSent(n);
        }
    }

    fn driveHandshakeRx(self: *Engine, fd: sys.fd_t, hs: *socks5.Handshake) !void {
        _ = self;
        var scratch: [22]u8 = undefined;
        while (!hs.complete()) {
            const want = hs.remaining();
            if (want == 0) return error.Socks5Malformed;
            const n = try sys.read(fd, scratch[0..want]);
            if (n == 0) return error.Socks5Eof;
            for (scratch[0..n]) |byte| {
                hs.consume(byte);
                if (hs.complete()) break;
            }
        }
    }

    fn handleSocksEvent(self: *Engine, flow: *flow_mod.Flow, events: u32) void {
        const fd = flow.socks_fd;

        const is_fatal_err = (events & linux.EPOLL.ERR) != 0;
        const is_fatal_hup = (events & linux.EPOLL.HUP) != 0 and (events & linux.EPOLL.IN) == 0;
        if (is_fatal_err or is_fatal_hup) {
            self.failSocksFlow(flow);
            return;
        }

        switch (flow.state) {
            .upstream_connect => {
                if ((events & linux.EPOLL.OUT) != 0) {
                    self.driveHandshakeTx(fd, &flow.hs) catch |err| {
                        if (err == error.WouldBlock) return;
                        self.failSocksFlow(flow);
                        return;
                    };
                    flow.hs.beginAuth();
                    flow.state = .socks5_auth_wait;
                    self.armSocksEvents(flow, linux.EPOLL.IN | linux.EPOLL.ERR);
                }
            },
            .socks5_auth_wait => {
                if ((events & linux.EPOLL.IN) != 0) {
                    self.driveHandshakeRx(fd, &flow.hs) catch |err| {
                        if (err == error.WouldBlock) return;
                        self.failSocksFlow(flow);
                        return;
                    };
                    const resp = flow.hs.incoming();
                    if (resp.len != 2 or resp[0] != 0x05 or resp[1] != 0x00) {
                        self.failSocksFlow(flow);
                        return;
                    }

                    flow.hs.beginConnectRequest(flow.dst_ip, flow.dst_port);
                    flow.state = .socks5_connect_wait;
                    self.armSocksEvents(flow, linux.EPOLL.IN | linux.EPOLL.OUT | linux.EPOLL.ERR);
                    self.driveHandshakeTx(fd, &flow.hs) catch |err| {
                        if (err == error.WouldBlock) return;
                        self.failSocksFlow(flow);
                        return;
                    };
                    flow.hs.beginReply();
                    self.armSocksEvents(flow, linux.EPOLL.IN | linux.EPOLL.ERR);
                }
            },
            .socks5_connect_wait => {
                if (flow.hs.phase == .request_tx) {
                    if ((events & linux.EPOLL.OUT) != 0) {
                        self.driveHandshakeTx(fd, &flow.hs) catch |err| {
                            if (err == error.WouldBlock) return;
                            self.failSocksFlow(flow);
                            return;
                        };
                        flow.hs.beginReply();
                        self.armSocksEvents(flow, linux.EPOLL.IN | linux.EPOLL.ERR);
                    }
                } else if (flow.hs.phase == .reply_rx) {
                    if ((events & linux.EPOLL.IN) != 0) {
                        self.driveHandshakeRx(fd, &flow.hs) catch |err| {
                            if (err == error.WouldBlock) return;
                            self.failSocksFlow(flow);
                            return;
                        };
                        const resp = flow.hs.incoming();
                        if (resp.len < 4 or resp[0] != 0x05 or resp[1] != 0x00 or (resp[3] != 0x01 and resp[3] != 0x04)) {
                            self.failSocksFlow(flow);
                            return;
                        }

                        flow.state = .established;
                        self.refreshSocksEvents(flow);
                        self.sendTcpPacket(flow, protocol.TcpHeader.FLAG_SYN | protocol.TcpHeader.FLAG_ACK, flow.s_isn, flow.rcv_nxt, 65535, null);
                        self.ensureUdpAssociate();
                    }
                } else {
                    self.failSocksFlow(flow);
                }
            },
            .established => {
                // Downstream data: peek first, consume only after a successful TUN write.
                if ((events & linux.EPOLL.IN) != 0) {
                    self.pumpDownstream(flow);
                    if (flow.state != .established) return;
                }

                // Upstream socket writable again: reopen the client window and
                // let TCP retransmission deliver the unacknowledged bytes.
                if ((events & linux.EPOLL.OUT) != 0 and flow.is_blocked) {
                    flow.is_blocked = false;
                    if (flow.client_fin) {
                        sys.shutdown(fd);
                    }
                    self.refreshSocksEvents(flow);
                    self.sendTcpPacket(flow, protocol.TcpHeader.FLAG_ACK, flow.snd_nxt, flow.rcv_nxt, 65535, null);
                }
            },
            else => {},
        }
    }

    fn sendRst(self: *Engine, flow: *flow_mod.Flow) void {
        self.sendTcpPacket(flow, protocol.TcpHeader.FLAG_RST | protocol.TcpHeader.FLAG_ACK, flow.snd_nxt, flow.rcv_nxt, 0, null);
    }

    fn sendTcpPacket(self: *Engine, flow: *flow_mod.Flow, flags: u8, seq: u32, ack: u32, window: u16, payload: ?[]const u8) void {
        const payload_len = if (payload) |p| p.len else 0;
        const is_syn = (flags & protocol.TcpHeader.FLAG_SYN) != 0;
        const tcp_hlen: u16 = if (is_syn) 24 else 20;
        const total_len: u16 = @intCast(20 + tcp_hlen + payload_len);
        if (total_len > self.tx_packet_buf.len) return; // Prevent buffer overrun

        var ip_hdr: *protocol.Ipv4Header = @ptrCast(@alignCast(&self.tx_packet_buf[0]));
        ip_hdr.ihl_version = 0x45;
        ip_hdr.tos = 0;
        ip_hdr.setTotalLen(total_len);
        ip_hdr.id = 0;
        ip_hdr.flags_fragment = std.mem.nativeToBig(u16, 0x4000); // DF
        ip_hdr.ttl = 64;
        ip_hdr.protocol = 6;
        ip_hdr.checksum = 0;
        ip_hdr.src_ip = flow.dst_ip;
        ip_hdr.dst_ip = flow.src_ip;
        ip_hdr.checksum = protocol.calculateIpv4Checksum(self.tx_packet_buf[0..20]);

        var tcp_hdr: *protocol.TcpHeader = @ptrCast(@alignCast(&self.tx_packet_buf[20]));
        tcp_hdr.src_port = flow.dst_port;
        tcp_hdr.dst_port = flow.src_port;
        tcp_hdr.setSeq(seq);
        tcp_hdr.setAck(ack);
        tcp_hdr.flags = flags;
        tcp_hdr.setWindow(window);
        tcp_hdr.checksum = 0;
        tcp_hdr.urgent_ptr = 0;

        if (is_syn) {
            tcp_hdr.data_offset_reserved = 0x60; // 6 * 4 = 24 bytes
            const mss: u16 = if (self.cfg.mtu > 40) self.cfg.mtu - 40 else 1460;
            self.tx_packet_buf[40] = 0x02; // Option Kind: MSS
            self.tx_packet_buf[41] = 0x04; // Option Length: 4
            self.tx_packet_buf[42] = @intCast((mss >> 8) & 0xff);
            self.tx_packet_buf[43] = @intCast(mss & 0xff);
            if (payload) |p| {
                @memcpy(self.tx_packet_buf[44 .. 44 + payload_len], p);
            }
        } else {
            tcp_hdr.data_offset_reserved = 0x50; // 5 * 4 = 20 bytes
            if (payload) |p| {
                @memcpy(self.tx_packet_buf[40 .. 40 + payload_len], p);
            }
        }

        const tcp_full_len: u16 = @intCast(tcp_hlen + payload_len);
        tcp_hdr.checksum = protocol.calculateTcpChecksum(
            flow.dst_ip,
            flow.src_ip,
            tcp_full_len,
            self.tx_packet_buf[20..total_len],
        );

        _ = sys.writeTun(self.cfg.tun_fd, self.tx_packet_buf[0..total_len]) catch {};
    }

    // Direct 0-copy fast path: tx_packet_buf[40 .. 40 + payload_len] already holds data from sys.recvPeek().
    fn sendTcpPacketDirect(self: *Engine, flow: *flow_mod.Flow, flags: u8, seq: u32, ack: u32, window: u16, payload_len: usize) !void {
        const tcp_hlen: u16 = 20;
        const total_len: u16 = @intCast(20 + tcp_hlen + payload_len);
        if (total_len > self.tx_packet_buf.len) return error.PacketTooLarge;

        var ip_hdr: *protocol.Ipv4Header = @ptrCast(@alignCast(&self.tx_packet_buf[0]));
        ip_hdr.ihl_version = 0x45;
        ip_hdr.tos = 0;
        ip_hdr.setTotalLen(total_len);
        ip_hdr.id = 0;
        ip_hdr.flags_fragment = std.mem.nativeToBig(u16, 0x4000); // DF
        ip_hdr.ttl = 64;
        ip_hdr.protocol = 6;
        ip_hdr.checksum = 0;
        ip_hdr.src_ip = flow.dst_ip;
        ip_hdr.dst_ip = flow.src_ip;
        ip_hdr.checksum = protocol.calculateIpv4Checksum(self.tx_packet_buf[0..20]);

        var tcp_hdr: *protocol.TcpHeader = @ptrCast(@alignCast(&self.tx_packet_buf[20]));
        tcp_hdr.src_port = flow.dst_port;
        tcp_hdr.dst_port = flow.src_port;
        tcp_hdr.setSeq(seq);
        tcp_hdr.setAck(ack);
        tcp_hdr.flags = flags;
        tcp_hdr.setWindow(window);
        tcp_hdr.checksum = 0;
        tcp_hdr.urgent_ptr = 0;
        tcp_hdr.data_offset_reserved = 0x50; // 5 * 4 = 20 bytes

        const tcp_full_len: u16 = @intCast(tcp_hlen + payload_len);
        tcp_hdr.checksum = protocol.calculateTcpChecksum(
            flow.dst_ip,
            flow.src_ip,
            tcp_full_len,
            self.tx_packet_buf[20..total_len],
        );

        _ = try sys.writeTun(self.cfg.tun_fd, self.tx_packet_buf[0..total_len]);
    }

    fn handleUdpRelayRead(self: *Engine) void {
        const n = sys.recvfrom(self.udp_relay_fd, &self.udp_scratch_buf) catch return;
        // SOCKS5 UDP response format:
        // [0..2]: RSV(0x00, 0x00), [2]: FRAG(0x00), [3]: ATYP
        // If ATYP=1 (IPv4): [4..8]: IP, [8..10]: Port, [10..n]: Payload
        if (n < 10) return;
        if (self.udp_scratch_buf[0] != 0 or self.udp_scratch_buf[1] != 0) return;
        if (self.udp_scratch_buf[2] != 0) return; // Discard fragmented packets
        if (self.udp_scratch_buf[3] != 0x01) return; // IPv4 only

        // Read IP and Port as raw bytes — UdpTable stores ip_hdr.src_ip/dst_ip and udp_hdr.src_port/dst_port
        // directly from the extern struct fields without any byte-swap, so they are in network byte order.
        // Use @bitCast to read the same way (no endian conversion) so findDnsQuery/findByTarget match.
        const remote_ip_raw = @as(u32, @bitCast(self.udp_scratch_buf[4..8][0..4].*));
        const remote_port_raw = @as(u16, @bitCast(self.udp_scratch_buf[8..10][0..2].*));
        const remote_port_native = std.mem.bigToNative(u16, remote_port_raw);
        const payload = self.udp_scratch_buf[10..n];

        // Find corresponding session to map back to original client
        if (remote_port_native == 53 and payload.len >= 2) {
            const dns_tx_id = std.mem.readInt(u16, payload[0..2], .big);
            if (self.udp_table.findDnsQuery(remote_ip_raw, remote_port_raw, dns_tx_id)) |q| {
                self.sendUdpPacket(q.dst_ip, q.src_ip, q.dst_port, q.src_port, payload);
                return;
            }
        }

        const session = self.udp_table.findByTarget(remote_ip_raw, remote_port_raw);
        const s = session orelse return;

        self.sendUdpPacket(s.dst_ip, s.src_ip, s.dst_port, s.src_port, payload);
    }

    fn sendUdpPacket(self: *Engine, src_ip: u32, dst_ip: u32, src_port: u16, dst_port: u16, payload: []const u8) void {
        const total_len: u16 = @intCast(20 + 8 + payload.len);
        if (total_len > self.tx_packet_buf.len) return;

        var ip_hdr: *protocol.Ipv4Header = @ptrCast(@alignCast(&self.tx_packet_buf[0]));
        ip_hdr.ihl_version = 0x45;
        ip_hdr.tos = 0;
        ip_hdr.setTotalLen(total_len);
        ip_hdr.id = 0;
        ip_hdr.flags_fragment = std.mem.nativeToBig(u16, 0x4000); // DF
        ip_hdr.ttl = 64;
        ip_hdr.protocol = 17; // UDP
        ip_hdr.checksum = 0;
        ip_hdr.src_ip = src_ip;
        ip_hdr.dst_ip = dst_ip;
        ip_hdr.checksum = protocol.calculateIpv4Checksum(self.tx_packet_buf[0..20]);

        var udp_hdr: *protocol.UdpHeader = @ptrCast(@alignCast(&self.tx_packet_buf[20]));
        udp_hdr.src_port = src_port;
        udp_hdr.dst_port = dst_port;
        udp_hdr.setLength(@intCast(8 + payload.len));
        udp_hdr.checksum = 0;

        @memcpy(self.tx_packet_buf[28 .. 28 + payload.len], payload);

        const udp_full_len: u16 = @intCast(8 + payload.len);
        udp_hdr.checksum = protocol.calculateUdpChecksum(
            src_ip,
            dst_ip,
            udp_full_len,
            self.tx_packet_buf[20..total_len],
        );

        _ = sys.writeTun(self.cfg.tun_fd, self.tx_packet_buf[0..total_len]) catch {};
    }
};

test "downstream send window respects inflight bytes and TUN blocking" {
    var flow: flow_mod.Flow = undefined;
    flow.reset();
    flow.snd_una = 0xffff_fff0;
    flow.snd_nxt = 0x0000_0000;
    flow.peer_wnd = 32;
    try std.testing.expect(Engine.canSendDownstream(&flow));

    flow.snd_nxt = 0x0000_0010;
    try std.testing.expect(!Engine.canSendDownstream(&flow));

    flow.snd_nxt = 0x0000_0000;
    flow.tun_blocked = true;
    try std.testing.expect(!Engine.canSendDownstream(&flow));
}

test "downstream pump consumes SOCKS data only after TUN write" {
    var tun_pair: [2]i32 = undefined;
    var rc = linux.socketpair(linux.AF.UNIX, linux.SOCK.STREAM, 0, &tun_pair);
    try std.testing.expectEqual(@as(usize, 0), rc);
    defer {
        _ = linux.close(tun_pair[0]);
        _ = linux.close(tun_pair[1]);
    }

    var socks_pair: [2]i32 = undefined;
    rc = linux.socketpair(linux.AF.UNIX, linux.SOCK.STREAM, 0, &socks_pair);
    try std.testing.expectEqual(@as(usize, 0), rc);
    defer {
        _ = linux.close(socks_pair[0]);
        _ = linux.close(socks_pair[1]);
    }
    const flags = linux.fcntl(socks_pair[0], linux.F.GETFL, 0);
    _ = linux.fcntl(socks_pair[0], linux.F.SETFL, flags | O_NONBLOCK);

    var eng: Engine = undefined;
    try eng.initInto(.{
        .tun_fd = tun_pair[0],
        .socks_ip = 0x7f000001,
        .socks_port = 10808,
        .mtu = 1500,
    });
    defer eng.deinit();

    var flow: flow_mod.Flow = undefined;
    flow.reset();
    flow.state = .established;
    flow.socks_fd = socks_pair[0];
    flow.snd_una = 0;
    flow.snd_nxt = 0;
    flow.peer_wnd = 65535;

    const payload = "hello";
    _ = linux.write(socks_pair[1], payload.ptr, payload.len);
    eng.pumpDownstream(&flow);

    try std.testing.expectEqual(@as(u32, payload.len), flow.snd_nxt);

    var packet: [128]u8 = undefined;
    const packet_len = linux.read(tun_pair[1], &packet, packet.len);
    try std.testing.expectEqual(@as(usize, 20 + 20 + payload.len), packet_len);
    try std.testing.expectEqualSlices(u8, payload, packet[40 .. 40 + payload.len]);

    var drained: [4]u8 = undefined;
    const drain_rc = linux.read(socks_pair[0], &drained, drained.len);
    try std.testing.expectEqual(linux.E.AGAIN, linux.errno(drain_rc));
}

test "upstream FIN waits for client ACK before tombstone" {
    var tun_pair: [2]i32 = undefined;
    const rc = linux.socketpair(linux.AF.UNIX, linux.SOCK.STREAM, 0, &tun_pair);
    try std.testing.expectEqual(@as(usize, 0), rc);
    defer {
        _ = linux.close(tun_pair[0]);
        _ = linux.close(tun_pair[1]);
    }

    var eng: Engine = undefined;
    try eng.initInto(.{
        .tun_fd = tun_pair[0],
        .socks_ip = 0x7f000001,
        .socks_port = 10808,
        .mtu = 1500,
    });
    defer eng.deinit();

    var flow: flow_mod.Flow = undefined;
    flow.reset();
    flow.state = .established;
    flow.socks_fd = -1;
    flow.snd_una = 100;
    flow.snd_nxt = 100;
    flow.rcv_nxt = 200;
    flow.peer_wnd = 65535;

    eng.sendUpstreamFin(&flow);
    try std.testing.expect(flow.upstream_fin);
    try std.testing.expectEqual(@as(u32, 101), flow.snd_nxt);

    var packet: [128]u8 = undefined;
    const packet_len = linux.read(tun_pair[1], &packet, packet.len);
    try std.testing.expectEqual(@as(usize, 40), packet_len);
    try std.testing.expect((packet[33] & protocol.TcpHeader.FLAG_FIN) != 0);

    eng.handleUpstreamFinPacket(&flow, protocol.TcpHeader.FLAG_ACK, flow.rcv_nxt, &.{}, flow.snd_nxt, 65535);
    try std.testing.expectEqual(flow_mod.State.tombstone, flow.state);
}

test "upstream FIN consumes payload before advancing receive sequence" {
    var tun_pair: [2]i32 = undefined;
    const rc = linux.socketpair(linux.AF.UNIX, linux.SOCK.STREAM, 0, &tun_pair);
    try std.testing.expectEqual(@as(usize, 0), rc);
    defer {
        _ = linux.close(tun_pair[0]);
        _ = linux.close(tun_pair[1]);
    }

    var eng: Engine = undefined;
    try eng.initInto(.{
        .tun_fd = tun_pair[0],
        .socks_ip = 0x7f000001,
        .socks_port = 10808,
        .mtu = 1500,
    });
    defer eng.deinit();

    var flow: flow_mod.Flow = undefined;
    flow.reset();
    flow.state = .established;
    flow.socks_fd = -1;
    flow.snd_una = 100;
    flow.snd_nxt = 100;
    flow.rcv_nxt = 200;
    flow.peer_wnd = 65535;

    eng.sendUpstreamFin(&flow);
    try std.testing.expect(flow.upstream_fin);

    var fin_packet: [40]u8 = undefined;
    try std.testing.expectEqual(@as(usize, 40), linux.read(tun_pair[1], &fin_packet, fin_packet.len));

    const payload = "abc";
    eng.handleUpstreamFinPacket(&flow, protocol.TcpHeader.FLAG_FIN, 200, payload, 0, 65535);

    try std.testing.expect(flow.client_fin);
    // Payload (3 bytes) + FIN consume four sequence numbers.
    try std.testing.expectEqual(@as(u32, 204), flow.rcv_nxt);

    var ack_packet: [40]u8 = undefined;
    try std.testing.expectEqual(@as(usize, 40), linux.read(tun_pair[1], &ack_packet, ack_packet.len));
    try std.testing.expect((ack_packet[33] & protocol.TcpHeader.FLAG_ACK) != 0);
    try std.testing.expectEqual(@as(u32, 204), std.mem.readInt(u32, ack_packet[28..32], .big));

    // A retransmitted FIN+payload must be ACKed without advancing twice.
    eng.handleUpstreamFinPacket(&flow, protocol.TcpHeader.FLAG_FIN, 200, payload, 0, 65535);
    try std.testing.expectEqual(@as(u32, 204), flow.rcv_nxt);

    var retransmit_ack: [40]u8 = undefined;
    try std.testing.expectEqual(@as(usize, 40), linux.read(tun_pair[1], &retransmit_ack, retransmit_ack.len));
    try std.testing.expectEqual(@as(u32, 204), std.mem.readInt(u32, retransmit_ack[28..32], .big));
}

test "adaptive high watermark sweeps long-idle established flows" {
    var tun_pair: [2]i32 = undefined;
    const rc = linux.socketpair(linux.AF.UNIX, linux.SOCK.STREAM, 0, &tun_pair);
    try std.testing.expectEqual(@as(usize, 0), rc);
    defer {
        _ = linux.close(tun_pair[0]);
        _ = linux.close(tun_pair[1]);
    }

    var eng: Engine = undefined;
    try eng.initInto(.{
        .tun_fd = tun_pair[0],
        .socks_ip = 0x7f000001,
        .socks_port = 10808,
        .mtu = 1500,
    });
    defer eng.deinit();

    // Fill table up to critical watermark (950 flows)
    var i: u16 = 0;
    while (i < 950) : (i += 1) {
        const f = eng.table.allocate(0x0a000001, 0x0a000002, 1000 + i, 80);
        try std.testing.expect(f != null);
        f.?.state = .established;
        // set deadline to standard 30 min idle
        f.?.deadline_ms = sys.monotonicMs() + IDLE_TIMEOUT_MS;
    }
    try std.testing.expect(eng.table.activeCount() >= CRITICAL_WATERMARK);

    // Make flow 0 idle for 65 seconds (exceeding critical 60s limit, but far below 30m)
    const now = sys.monotonicMs();
    eng.table.flows[0].deadline_ms = now + IDLE_TIMEOUT_MS - 65_000;

    // Run sweepTimers
    eng.last_sweep_ms = 0; // force sweep
    eng.sweepTimers();

    // Flow 0 should have been closed into tombstone because of critical watermark!
    try std.testing.expectEqual(flow_mod.State.tombstone, eng.table.flows[0].state);
}
