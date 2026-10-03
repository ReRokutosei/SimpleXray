const std = @import("std");
const linux = std.os.linux;
const protocol = @import("protocol.zig");
const flow_mod = @import("flow.zig");
const socks5 = @import("socks5.zig");
const sys = @import("sys.zig");

const EPOLLRDHUP: u32 = 0x2000;
const O_NONBLOCK: usize = 0x800;
const FLOW_INDEX_OFFSET: u32 = 0x10_000;
const UDP_CTRL_INDEX_OFFSET: u32 = 0x20_000;
const UDP_RELAY_INDEX_OFFSET: u32 = 0x30_000;

const CONNECT_TIMEOUT_MS: i64 = 10_000;
const IDLE_TIMEOUT_MS: i64 = 30 * 60 * 1000;
const TOMBSTONE_MS: i64 = 3_000;
const FIN_WAIT_TIMEOUT_MS: i64 = 30_000;
const SWEEP_INTERVAL_MS: i64 = 500;
const UDP_SESSION_IDLE_MS: i64 = 30_000;
const UDP_ASSOC_TIMEOUT_MS: i64 = 5_000;
const UDP_RETRY_BACKOFF_MS: i64 = 2_000;
const UDP_RELAY_BATCH: usize = 16;
const UDP_PENDING_CAPACITY: usize = 32;
const UDP_PENDING_PAYLOAD_MAX: usize = 1500;
const UDP_PENDING_NONE: u16 = std.math.maxInt(u16);

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

const UdpPending = struct {
    session_idx: u16 = UDP_PENDING_NONE,
    dst_ip: u32 = 0,
    dst_port: u16 = 0,
    payload_len: u16 = 0,
    payload: [UDP_PENDING_PAYLOAD_MAX]u8 = undefined,
};

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
    udp_pending: [UDP_PENDING_CAPACITY]UdpPending,
    udp_pending_next: usize,
    udp_retry_after_ms: i64,
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
        for (&self.udp_pending) |*pending| {
            pending.session_idx = UDP_PENDING_NONE;
        }
        self.udp_pending_next = 0;
        self.udp_retry_after_ms = 0;
        self.stop_fd = stop_fd;
        self.running = true;
        self.tun_out_armed = false;
        self.last_sweep_ms = 0;
    }

    pub fn init(cfg: EngineConfig) !Engine {
        var eng: Engine = undefined;
        try eng.initInto(cfg);
        return eng;
    }

    pub fn deinit(self: *Engine) void {
        sys.close(self.epoll_fd);
        if (self.stop_fd >= 0) sys.close(self.stop_fd);
        for (&self.table.flows) |*flow| {
            if (flow.socks_fd >= 0) {
                sys.close(flow.socks_fd);
                flow.socks_fd = -1;
            }
        }
        for (&self.udp_table.sessions) |*session| {
            if (session.ctrl_fd >= 0) sys.close(session.ctrl_fd);
            if (session.relay_fd >= 0) sys.close(session.relay_fd);
            session.reset();
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

        self.sweepUdpSessions(now);
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
                if (raw_data >= UDP_RELAY_INDEX_OFFSET) {
                    const session_idx = raw_data - UDP_RELAY_INDEX_OFFSET;
                    if (session_idx < flow_mod.UdpTable.CAPACITY) {
                        const session = &self.udp_table.sessions[session_idx];
                        if (session.state == .established and session.relay_fd >= 0) {
                            self.handleUdpSessionRelay(session, ev.events);
                        }
                    }
                    continue;
                }
                if (raw_data >= UDP_CTRL_INDEX_OFFSET) {
                    const session_idx = raw_data - UDP_CTRL_INDEX_OFFSET;
                    if (session_idx < flow_mod.UdpTable.CAPACITY) {
                        const session = &self.udp_table.sessions[session_idx];
                        if (session.ctrl_fd >= 0 and (session.state == .associating or session.state == .established)) {
                            self.handleUdpSessionCtrlEvent(session, ev.events);
                        }
                    }
                    continue;
                }
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
                }
            }

            self.sweepTimers();
        }
        logCore(4, "Engine.run while loop terminated, self.running={}", .{self.running});
    }

    fn closeUdpSessionFds(self: *Engine, session: *flow_mod.UdpSession) void {
        if (session.ctrl_fd >= 0) {
            _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_DEL, session.ctrl_fd, null);
            sys.close(session.ctrl_fd);
            session.ctrl_fd = -1;
        }
        if (session.relay_fd >= 0) {
            _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_DEL, session.relay_fd, null);
            sys.close(session.relay_fd);
            session.relay_fd = -1;
        }
    }

    fn resetUdpSession(self: *Engine, session: *flow_mod.UdpSession) void {
        self.clearUdpPending(session);
        self.closeUdpSessionFds(session);
        session.reset();
    }

    fn failUdpSession(self: *Engine, session: *flow_mod.UdpSession, now: i64) void {
        self.closeUdpSessionFds(session);
        session.state = .waiting;
        session.relay_port = 0;
        session.deadline_ms = now + UDP_RETRY_BACKOFF_MS;
        session.hs.reset();
        self.udp_retry_after_ms = now + UDP_RETRY_BACKOFF_MS;
    }

    fn armUdpSessionEvents(
        self: *Engine,
        session: *flow_mod.UdpSession,
        fd: sys.fd_t,
        events: u32,
        offset: u32,
    ) void {
        const session_idx: u32 = @intCast(self.udp_table.indexOf(session));
        var ev = linux.epoll_event{
            .events = events,
            .data = .{ .u32 = session_idx + offset },
        };
        _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_MOD, fd, &ev);
    }

    fn beginUdpAssociate(self: *Engine, session: *flow_mod.UdpSession) void {
        if (session.state == .associating or session.state == .established) return;

        const now = sys.monotonicMs();
        if (now < self.udp_retry_after_ms) {
            session.state = .waiting;
            session.deadline_ms = self.udp_retry_after_ms;
            return;
        }

        const sock = sys.createTcpSocket() catch {
            self.failUdpSession(session, now);
            return;
        };
        session.ctrl_fd = sock;
        session.state = .associating;
        session.deadline_ms = now + UDP_ASSOC_TIMEOUT_MS;
        session.relay_port = 0;
        session.hs.beginGreeting();

        sys.connect(sock, self.cfg.socks_ip, self.cfg.socks_port) catch |err| {
            if (err != error.ConnectionPending) {
                self.failUdpSession(session, now);
                return;
            }
        };

        const session_idx: u32 = @intCast(self.udp_table.indexOf(session));
        var ev = linux.epoll_event{
            .events = linux.EPOLL.OUT | linux.EPOLL.IN | linux.EPOLL.ERR,
            .data = .{ .u32 = session_idx + UDP_CTRL_INDEX_OFFSET },
        };
        const ctl_rc = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_ADD, sock, &ev);
        if (linux.errno(ctl_rc) != .SUCCESS) {
            self.failUdpSession(session, now);
        }
    }

    fn udpAssociateSucceeded(self: *Engine, session: *flow_mod.UdpSession, relay_port: u16) void {
        const now = sys.monotonicMs();
        const relay_fd = sys.createUdpSocket() catch {
            self.failUdpSession(session, now);
            return;
        };

        session.hs.reset();
        session.relay_fd = relay_fd;
        session.relay_port = relay_port;
        session.state = .established;
        session.deadline_ms = 0;

        const session_idx: u32 = @intCast(self.udp_table.indexOf(session));
        var ev = linux.epoll_event{
            .events = linux.EPOLL.IN | linux.EPOLL.ERR,
            .data = .{ .u32 = session_idx + UDP_RELAY_INDEX_OFFSET },
        };
        const ctl_rc = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_ADD, relay_fd, &ev);
        if (linux.errno(ctl_rc) != .SUCCESS) {
            self.failUdpSession(session, now);
            return;
        }

        self.armUdpSessionEvents(
            session,
            session.ctrl_fd,
            linux.EPOLL.IN | linux.EPOLL.ERR | linux.EPOLL.HUP | EPOLLRDHUP,
            UDP_CTRL_INDEX_OFFSET,
        );
        self.flushUdpPending(session);
    }

    fn getOrCreateUdpSession(self: *Engine, src_ip: u32, src_port: u16, now: i64) ?*flow_mod.UdpSession {
        if (self.udp_table.findByClient(src_ip, src_port)) |session| {
            session.last_active_ms = now;
            return session;
        }

        var session = self.udp_table.claim(src_ip, src_port);
        if (session == null) {
            const victim = self.udp_table.oldest() orelse return null;
            self.resetUdpSession(victim);
            session = self.udp_table.claim(src_ip, src_port);
        }
        if (session) |claimed| {
            claimed.last_active_ms = now;
        }
        return session;
    }

    fn queueUdpPending(
        self: *Engine,
        session: *flow_mod.UdpSession,
        dst_ip: u32,
        dst_port: u16,
        payload: []const u8,
    ) void {
        if (session.pending_slot >= 0) return;
        if (payload.len == 0 or payload.len > UDP_PENDING_PAYLOAD_MAX) return;

        const start = self.udp_pending_next;
        var slot: ?usize = null;
        var i: usize = 0;
        while (i < UDP_PENDING_CAPACITY) : (i += 1) {
            const idx = (start + i) % UDP_PENDING_CAPACITY;
            if (self.udp_pending[idx].session_idx == UDP_PENDING_NONE) {
                slot = idx;
                break;
            }
        }
        const pending_index = slot orelse return;

        const session_idx: u16 = @intCast(self.udp_table.indexOf(session));
        const pending = &self.udp_pending[pending_index];
        pending.session_idx = session_idx;
        pending.dst_ip = dst_ip;
        pending.dst_port = dst_port;
        pending.payload_len = @intCast(payload.len);
        @memcpy(pending.payload[0..payload.len], payload);
        session.pending_slot = @intCast(pending_index);
        self.udp_pending_next = (pending_index + 1) % UDP_PENDING_CAPACITY;
    }

    fn clearUdpPending(self: *Engine, session: *flow_mod.UdpSession) void {
        if (session.pending_slot < 0) return;
        const slot: usize = @intCast(session.pending_slot);
        session.pending_slot = -1;
        if (slot >= UDP_PENDING_CAPACITY) return;

        const session_idx: u16 = @intCast(self.udp_table.indexOf(session));
        const pending = &self.udp_pending[slot];
        if (pending.session_idx == session_idx) {
            pending.session_idx = UDP_PENDING_NONE;
            pending.payload_len = 0;
        }
    }

    fn flushUdpPending(self: *Engine, session: *flow_mod.UdpSession) void {
        if (session.pending_slot < 0) return;
        const slot: usize = @intCast(session.pending_slot);
        session.pending_slot = -1;
        if (slot >= UDP_PENDING_CAPACITY) return;

        const session_idx: u16 = @intCast(self.udp_table.indexOf(session));
        const pending = &self.udp_pending[slot];
        if (pending.session_idx != session_idx) {
            pending.session_idx = UDP_PENDING_NONE;
            pending.payload_len = 0;
            return;
        }

        const payload_len = pending.payload_len;
        const dst_ip = pending.dst_ip;
        const dst_port = pending.dst_port;
        pending.session_idx = UDP_PENDING_NONE;
        pending.payload_len = 0;

        if (payload_len == 0) return;
        self.sendUdpRelayDatagram(session, dst_ip, dst_port, pending.payload[0..payload_len]);
    }

    fn sendUdpRelayDatagram(
        self: *Engine,
        session: *flow_mod.UdpSession,
        dst_ip: u32,
        dst_port: u16,
        payload: []const u8,
    ) void {
        var socks5_udp_hdr: [10]u8 = undefined;
        _ = socks5.formatUdpHeader(&socks5_udp_hdr, dst_ip, dst_port);
        const send_len = 10 + payload.len;
        if (send_len > self.udp_scratch_buf.len) return;

        @memcpy(self.udp_scratch_buf[0..10], &socks5_udp_hdr);
        @memcpy(self.udp_scratch_buf[10..send_len], payload);
        _ = sys.sendto(
            session.relay_fd,
            self.udp_scratch_buf[0..send_len],
            self.cfg.socks_ip,
            session.relay_port,
        ) catch {};
    }

    fn handleUdpUpstream(
        self: *Engine,
        src_ip: u32,
        dst_ip: u32,
        src_port: u16,
        dst_port: u16,
        payload: []const u8,
    ) void {
        const now = sys.monotonicMs();
        const session = self.getOrCreateUdpSession(src_ip, src_port, now) orelse return;
        session.last_active_ms = now;

        if (session.state != .established) {
            self.queueUdpPending(session, dst_ip, dst_port, payload);
            switch (session.state) {
                .free => self.beginUdpAssociate(session),
                .waiting => {
                    if (now >= session.deadline_ms) self.beginUdpAssociate(session);
                },
                .associating => {},
                .established => {},
            }
            return;
        }

        self.sendUdpRelayDatagram(session, dst_ip, dst_port, payload);
    }

    fn handleUdpSessionCtrlEvent(self: *Engine, session: *flow_mod.UdpSession, events: u32) void {
        const fd = session.ctrl_fd;
        if (fd < 0) return;

        if (session.state == .established) {
            var should_close = false;
            if ((events & (linux.EPOLL.ERR | linux.EPOLL.HUP | EPOLLRDHUP)) != 0) {
                should_close = true;
            } else if ((events & linux.EPOLL.IN) != 0) {
                var dummy: [16]u8 = undefined;
                const rn = sys.read(fd, &dummy) catch |err| blk: {
                    if (err == error.WouldBlock) return;
                    should_close = true;
                    break :blk 0;
                };
                if (rn == 0) should_close = true;
            }
            if (should_close) self.resetUdpSession(session);
            return;
        }

        if (session.state != .associating) return;
        if ((events & (linux.EPOLL.ERR | linux.EPOLL.HUP | EPOLLRDHUP)) != 0) {
            self.failUdpSession(session, sys.monotonicMs());
            return;
        }

        if (session.hs.phase == .greeting_tx or session.hs.phase == .request_tx) {
            if ((events & linux.EPOLL.OUT) == 0) return;
            self.driveHandshakeTx(fd, &session.hs) catch |err| {
                if (err == error.WouldBlock) return;
                self.failUdpSession(session, sys.monotonicMs());
                return;
            };
            if (session.hs.phase == .greeting_tx) {
                session.hs.beginAuth();
                self.armUdpSessionEvents(session, fd, linux.EPOLL.IN | linux.EPOLL.ERR, UDP_CTRL_INDEX_OFFSET);
            } else {
                session.hs.beginReply();
                self.armUdpSessionEvents(session, fd, linux.EPOLL.IN | linux.EPOLL.ERR, UDP_CTRL_INDEX_OFFSET);
            }
            return;
        }

        if ((events & linux.EPOLL.IN) == 0) return;

        if (session.hs.phase == .auth_rx) {
            self.driveHandshakeRx(fd, &session.hs) catch |err| {
                if (err == error.WouldBlock) return;
                self.failUdpSession(session, sys.monotonicMs());
                return;
            };
            const resp = session.hs.incoming();
            if (resp.len != 2 or resp[0] != 0x05 or resp[1] != 0x00) {
                self.failUdpSession(session, sys.monotonicMs());
                return;
            }
            session.hs.beginUdpAssociateRequest();
            self.armUdpSessionEvents(
                session,
                fd,
                linux.EPOLL.OUT | linux.EPOLL.IN | linux.EPOLL.ERR,
                UDP_CTRL_INDEX_OFFSET,
            );
            self.driveHandshakeTx(fd, &session.hs) catch |err| {
                if (err == error.WouldBlock) return;
                self.failUdpSession(session, sys.monotonicMs());
                return;
            };
            session.hs.beginReply();
            self.armUdpSessionEvents(session, fd, linux.EPOLL.IN | linux.EPOLL.ERR, UDP_CTRL_INDEX_OFFSET);
        } else if (session.hs.phase == .reply_rx) {
            self.driveHandshakeRx(fd, &session.hs) catch |err| {
                if (err == error.WouldBlock) return;
                self.failUdpSession(session, sys.monotonicMs());
                return;
            };
            const resp = session.hs.incoming();
            if (resp.len != 10 or resp[0] != 0x05 or resp[1] != 0x00 or resp[3] != 0x01) {
                self.failUdpSession(session, sys.monotonicMs());
                return;
            }
            const relay_port = (@as(u16, resp[8]) << 8) | @as(u16, resp[9]);
            if (relay_port == 0) {
                self.failUdpSession(session, sys.monotonicMs());
                return;
            }
            self.udpAssociateSucceeded(session, relay_port);
        } else {
            self.failUdpSession(session, sys.monotonicMs());
        }
    }

    fn handleUdpSessionRelay(self: *Engine, session: *flow_mod.UdpSession, events: u32) void {
        if (session.state != .established or session.relay_fd < 0) return;

        if ((events & (linux.EPOLL.ERR | linux.EPOLL.HUP | EPOLLRDHUP)) != 0) {
            self.resetUdpSession(session);
            return;
        }
        if ((events & linux.EPOLL.IN) == 0) return;

        var batch: usize = 0;
        while (batch < UDP_RELAY_BATCH) : (batch += 1) {
            const n = sys.recvfrom(session.relay_fd, &self.udp_scratch_buf) catch |err| {
                if (err == error.WouldBlock) return;
                self.resetUdpSession(session);
                return;
            };
            self.handleUdpRelayDatagram(session, n);
        }
    }

    fn handleUdpRelayDatagram(self: *Engine, session: *flow_mod.UdpSession, n: usize) void {
        if (n < 10) return;
        if (self.udp_scratch_buf[0] != 0 or self.udp_scratch_buf[1] != 0) return;
        if (self.udp_scratch_buf[2] != 0) return; // FRAG is always standalone
        if (self.udp_scratch_buf[3] != 0x01) return; // IPv4 only

        const remote_ip = @as(u32, @bitCast(self.udp_scratch_buf[4..8][0..4].*));
        const remote_port = @as(u16, @bitCast(self.udp_scratch_buf[8..10][0..2].*));
        const payload = self.udp_scratch_buf[10..n];

        session.last_active_ms = sys.monotonicMs();
        self.sendUdpPacket(remote_ip, session.src_ip, remote_port, session.src_port, payload);
    }

    fn sweepUdpSessions(self: *Engine, now: i64) void {
        for (&self.udp_table.sessions) |*session| {
            switch (session.state) {
                .free => {},
                .waiting => {
                    if (now >= session.deadline_ms) self.beginUdpAssociate(session);
                },
                .associating => {
                    if (now >= session.deadline_ms) self.failUdpSession(session, now);
                },
                .established => {
                    if (now - session.last_active_ms >= UDP_SESSION_IDLE_MS) {
                        self.resetUdpSession(session);
                    }
                },
            }
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
            self.handleUdpUpstream(
                ip_hdr.src_ip,
                ip_hdr.dst_ip,
                udp_hdr.src_port,
                udp_hdr.dst_port,
                payload,
            );
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
        var evicted: flow_mod.Eviction = .{};
        const flow = self.table.allocateTracked(src_ip, dst_ip, src_port, dst_port, &evicted) orelse return;
        self.armTunEvents();

        if (evicted.valid) {
            var old_flow: flow_mod.Flow = undefined;
            old_flow.reset();
            old_flow.src_ip = evicted.src_ip;
            old_flow.dst_ip = evicted.dst_ip;
            old_flow.src_port = evicted.src_port;
            old_flow.dst_port = evicted.dst_port;
            old_flow.snd_nxt = evicted.snd_nxt;
            old_flow.rcv_nxt = evicted.rcv_nxt;
            self.sendRst(&old_flow);
        }

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

test "UDP relay replies map to the originating client session" {
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

    var session_a: flow_mod.UdpSession = undefined;
    session_a.reset();
    session_a.state = .established;
    session_a.relay_fd = -1;
    session_a.src_ip = @bitCast([4]u8{ 10, 0, 0, 1 });
    session_a.src_port = @bitCast([2]u8{ 0x9c, 0x40 }); // 40000

    var session_b: flow_mod.UdpSession = undefined;
    session_b.reset();
    session_b.state = .established;
    session_b.relay_fd = -1;
    session_b.src_ip = @bitCast([4]u8{ 10, 0, 0, 1 });
    session_b.src_port = @bitCast([2]u8{ 0x9c, 0x41 }); // 40001

    const remote_ip = [4]u8{ 203, 0, 113, 7 };
    const remote_port = [2]u8{ 0, 53 };
    const payload_a = "pong-a";
    const payload_b = "pong-b";

    var response: [64]u8 = undefined;
    @memcpy(response[0..4], &[4]u8{ 0, 0, 0, 1 });
    @memcpy(response[4..8], &remote_ip);
    @memcpy(response[8..10], &remote_port);

    @memcpy(response[10 .. 10 + payload_a.len], payload_a);
    const len_a = 10 + payload_a.len;
    @memcpy(eng.udp_scratch_buf[0..len_a], response[0..len_a]);
    eng.handleUdpRelayDatagram(&session_a, len_a);

    var packet_a: [128]u8 = undefined;
    const packet_len_a = linux.read(tun_pair[1], &packet_a, packet_a.len);
    try std.testing.expectEqual(@as(usize, 20 + 8 + payload_a.len), packet_len_a);
    try std.testing.expectEqualSlices(u8, &remote_ip, packet_a[12..16]);
    try std.testing.expectEqualSlices(u8, &[4]u8{ 10, 0, 0, 1 }, packet_a[16..20]);
    try std.testing.expectEqualSlices(u8, &remote_port, packet_a[20..22]);
    try std.testing.expectEqualSlices(u8, &[2]u8{ 0x9c, 0x40 }, packet_a[22..24]);
    try std.testing.expectEqualSlices(u8, payload_a, packet_a[28 .. 28 + payload_a.len]);

    @memcpy(response[10 .. 10 + payload_b.len], payload_b);
    const len_b = 10 + payload_b.len;
    @memcpy(eng.udp_scratch_buf[0..len_b], response[0..len_b]);
    eng.handleUdpRelayDatagram(&session_b, len_b);

    var packet_b: [128]u8 = undefined;
    const packet_len_b = linux.read(tun_pair[1], &packet_b, packet_b.len);
    try std.testing.expectEqual(@as(usize, 20 + 8 + payload_b.len), packet_len_b);
    try std.testing.expectEqualSlices(u8, &remote_ip, packet_b[12..16]);
    try std.testing.expectEqualSlices(u8, &[4]u8{ 10, 0, 0, 1 }, packet_b[16..20]);
    try std.testing.expectEqualSlices(u8, &remote_port, packet_b[20..22]);
    try std.testing.expectEqualSlices(u8, &[2]u8{ 0x9c, 0x41 }, packet_b[22..24]);
    try std.testing.expectEqualSlices(u8, payload_b, packet_b[28 .. 28 + payload_b.len]);
}

test "UDP pending datagram survives association setup" {
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

    const receiver_fd = try sys.createUdpSocket();
    defer sys.close(receiver_fd);

    var bind_addr = sys.SockAddrIn{
        .sin_family = linux.AF.INET,
        .sin_port = 0,
        .sin_addr = std.mem.nativeToBig(u32, 0x7f000001),
    };
    const bind_rc = linux.bind(receiver_fd, @ptrCast(&bind_addr), @sizeOf(sys.SockAddrIn));
    try std.testing.expectEqual(linux.E.SUCCESS, linux.errno(bind_rc));

    var bound_addr: sys.SockAddrIn = undefined;
    var bound_len: linux.socklen_t = @sizeOf(sys.SockAddrIn);
    const name_rc = linux.getsockname(receiver_fd, @ptrCast(&bound_addr), &bound_len);
    try std.testing.expectEqual(linux.E.SUCCESS, linux.errno(name_rc));
    const relay_port = std.mem.bigToNative(u16, bound_addr.sin_port);

    const session = eng.udp_table.claim(@bitCast([4]u8{ 10, 0, 0, 1 }), @bitCast([2]u8{ 0x9c, 0x40 }));
    try std.testing.expect(session != null);
    const dst_ip = @as(u32, @bitCast([4]u8{ 203, 0, 113, 7 }));
    const dst_port = @as(u16, @bitCast([2]u8{ 0, 53 }));
    eng.queueUdpPending(session.?, dst_ip, dst_port, "hello");
    try std.testing.expect(session.?.pending_slot >= 0);

    session.?.relay_fd = try sys.createUdpSocket();
    session.?.relay_port = relay_port;
    session.?.state = .established;
    eng.flushUdpPending(session.?);
    try std.testing.expect(session.?.pending_slot < 0);

    var datagram: [64]u8 = undefined;
    const n = try sys.recvfrom(receiver_fd, &datagram);
    try std.testing.expectEqual(@as(usize, 15), n);
    try std.testing.expectEqualSlices(u8, &[10]u8{ 0, 0, 0, 1, 203, 0, 113, 7, 0, 53 }, datagram[0..10]);
    try std.testing.expectEqualSlices(u8, "hello", datagram[10..n]);
}

test "flow eviction sends RST for the oldest active flow" {
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

    const dst_port_raw: u16 = @bitCast([2]u8{ 0, 80 });
    var i: u16 = 0;
    while (i < flow_mod.FlowTable.CAPACITY) : (i += 1) {
        const port = 1000 + i;
        const src_port_raw: u16 = @bitCast([2]u8{ @intCast(port >> 8), @intCast(port & 0xff) });
        const flow = eng.table.allocate(0x0a000001, 0x0a000002, src_port_raw, dst_port_raw);
        try std.testing.expect(flow != null);
        flow.?.state = .established;
        flow.?.deadline_ms = 1000 + @as(i64, i);
        flow.?.snd_nxt = @as(u32, 0x1111_0000) + i;
        flow.?.rcv_nxt = @as(u32, 0x2222_0000) + i;
    }

    eng.handleNewSyn(0x0a0000ff, 0x0a0000fe, 50000, 443, 1234);

    var packet: [64]u8 = undefined;
    const packet_len = linux.read(tun_pair[1], &packet, packet.len);
    try std.testing.expectEqual(@as(usize, 40), packet_len);
    try std.testing.expect((packet[33] & protocol.TcpHeader.FLAG_RST) != 0);
    try std.testing.expect((packet[33] & protocol.TcpHeader.FLAG_ACK) != 0);
    try std.testing.expectEqualSlices(u8, &[2]u8{ 0, 80 }, packet[20..22]);
    try std.testing.expectEqualSlices(u8, &[2]u8{ 3, 0xE8 }, packet[22..24]);
    try std.testing.expectEqual(@as(u32, 0x1111_0000), std.mem.readInt(u32, packet[24..28], .big));
    try std.testing.expectEqual(@as(u32, 0x2222_0000), std.mem.readInt(u32, packet[28..32], .big));
}
