const std = @import("std");
const linux = std.os.linux;
const protocol = @import("protocol.zig");
const flow_mod = @import("flow.zig");
const socks5 = @import("socks5.zig");
const sys = @import("sys.zig");

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
    stop_fd: sys.fd_t,
    running: bool,

    // Reusable I/O buffers (Single allocation, 0 dynamic malloc in fast path, aligned for IP/TCP headers)
    rx_packet_buf: [4096]u8 align(4),
    tx_packet_buf: [4096]u8 align(4),
    stream_buf: [4096]u8,
    udp_scratch_buf: [4096]u8,

    pub fn init(cfg: EngineConfig) !Engine {
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

        const eng = Engine{
            .cfg = cfg,
            .epoll_fd = epoll_fd,
            .table = flow_mod.FlowTable.init(),
            .udp_table = flow_mod.UdpTable.init(),
            .udp_ctrl_fd = -1,
            .udp_relay_fd = udp_relay_fd,
            .udp_relay_port = 0,
            .stop_fd = stop_fd,
            .running = true,
            .rx_packet_buf = undefined,
            .tx_packet_buf = undefined,
            .stream_buf = undefined,
            .udp_scratch_buf = undefined,
        };

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
        self.running = false;
        if (self.stop_fd >= 0) {
            sys.signalEventFd(self.stop_fd);
        }
    }

    pub fn run(self: *Engine) !void {
        var events: [64]linux.epoll_event = undefined;

        while (self.running) {
            const num_events = linux.epoll_wait(self.epoll_fd, &events, 64, 100);
            if (num_events < 0) {
                const err = linux.errno(num_events);
                if (err == .INTR) continue;
                return error.EpollWaitFailed;
            }

            const count: usize = @intCast(num_events);
            for (events[0..count]) |ev| {
                const fd = ev.data.fd;
                if (fd == self.stop_fd) {
                    self.running = false;
                    return;
                } else if (fd == self.cfg.tun_fd) {
                    try self.handleTunRead();
                } else if (fd == self.udp_relay_fd) {
                    try self.handleUdpRelayRead();
                } else if (fd == self.udp_ctrl_fd) {
                    // UDP Associate TCP control connection status
                    if ((ev.events & (linux.EPOLL.ERR | linux.EPOLL.HUP)) != 0) {
                        sys.close(self.udp_ctrl_fd);
                        self.udp_ctrl_fd = -1;
                        self.udp_relay_port = 0;
                    }
                } else {
                    try self.handleSocksEvent(fd, ev.events);
                }
            }
        }
    }

    pub fn initUdpAssociate(self: *Engine) !void {
        if (self.udp_ctrl_fd >= 0) return;

        const sock = try sys.createTcpSocket();
        errdefer sys.close(sock);

        sys.connect(sock, self.cfg.socks_ip, self.cfg.socks_port) catch |err| {
            if (err != error.ConnectionPending) return err;
        };

        // For control socket setup during init, we can briefly poll or wait with a short timeout
        var pfd = [_]linux.pollfd{.{
            .fd = sock,
            .events = linux.POLL.OUT,
            .revents = 0,
        }};
        _ = linux.poll(&pfd, 1, 500);

        _ = try sys.write(sock, &socks5.Greeting);

        var auth_resp: [2]u8 = undefined;
        var pfd_in = [_]linux.pollfd{.{
            .fd = sock,
            .events = linux.POLL.IN,
            .revents = 0,
        }};
        _ = linux.poll(&pfd_in, 1, 500);
        const an = try sys.read(sock, &auth_resp);
        if (an != 2 or auth_resp[0] != 0x05 or auth_resp[1] != 0x00) {
            return error.Socks5AuthFailed;
        }

        var req_buf: [10]u8 = undefined;
        const req_len = socks5.formatUdpAssociateRequest(&req_buf);
        _ = try sys.write(sock, req_buf[0..req_len]);

        _ = linux.poll(&pfd_in, 1, 500);
        var resp_buf: [10]u8 = undefined;
        const rn = try sys.read(sock, &resp_buf);
        if (rn < 10 or resp_buf[0] != 0x05 or resp_buf[1] != 0x00) {
            return error.Socks5UdpAssociateFailed;
        }

        const relay_port = (@as(u16, resp_buf[8]) << 8) | @as(u16, resp_buf[9]);
        self.udp_ctrl_fd = sock;
        self.udp_relay_port = relay_port;

        // Monitor UDP ctrl socket for disconnection
        var ev = linux.epoll_event{
            .events = linux.EPOLL.ERR | linux.EPOLL.HUP,
            .data = .{ .fd = sock },
        };
        _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_ADD, sock, &ev);
    }

    fn handleTunRead(self: *Engine) !void {
        const n = sys.read(self.cfg.tun_fd, &self.rx_packet_buf) catch |err| {
            if (err == error.WouldBlock) return;
            return err;
        };
        if (n < 20) return; // Minimum IPv4 header length

        const ip_hdr: *const protocol.Ipv4Header = @ptrCast(@alignCast(&self.rx_packet_buf[0]));
        if (ip_hdr.version() != 4) return; // IPv4 only

        const ip_hlen = ip_hdr.headerLen();
        if (n < ip_hlen) return;

        if (ip_hdr.protocol == 17) {
            // UDP Packet Forwarding
            if (n < ip_hlen + 8) return;
            const udp_hdr: *const protocol.UdpHeader = @ptrCast(@alignCast(&self.rx_packet_buf[ip_hlen]));
            const total_hlen = ip_hlen + 8;
            if (n < total_hlen) return;

            const payload = self.rx_packet_buf[total_hlen..n];
            const src_ip = ip_hdr.src_ip;
            const dst_ip = ip_hdr.dst_ip;
            const src_port = udp_hdr.src_port;
            const dst_port = udp_hdr.dst_port;

            // Touch or allocate UDP session in table
            _ = self.udp_table.touchOrAllocate(src_ip, dst_ip, src_port, dst_port);

            // Re-attempt UDP Associate if not established
            if (self.udp_ctrl_fd < 0 or self.udp_relay_port == 0) {
                self.initUdpAssociate() catch return;
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
        if (n < ip_hlen + 20) return;

        const tcp_hdr: *const protocol.TcpHeader = @ptrCast(@alignCast(&self.rx_packet_buf[ip_hlen]));
        const tcp_hlen = tcp_hdr.headerLen();
        const total_hlen = ip_hlen + tcp_hlen;
        if (n < total_hlen) return;

        const payload = self.rx_packet_buf[total_hlen..n];
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
                try self.handleNewSyn(src_ip, dst_ip, src_port, dst_port, seq);
            }
            return;
        }

        const f = flow.?;

        // Fast Recycle: If we receive a new SYN for a flow in tombstone, immediately reset and re-allocate!
        if ((flags & protocol.TcpHeader.FLAG_SYN) != 0 and (flags & protocol.TcpHeader.FLAG_ACK) == 0) {
            if (f.state == .tombstone) {
                f.reset();
                try self.handleNewSyn(src_ip, dst_ip, src_port, dst_port, seq);
                return;
            }
        }

        if ((flags & protocol.TcpHeader.FLAG_RST) != 0) {
            self.table.markTombstone(f, 3000);
            return;
        }

        if (f.state == .established) {
            // Check for Persist Probe (seq == rcv_nxt - 1, payload len <= 1)
            if (f.is_blocked and payload.len <= 1 and seq == f.rcv_nxt -% 1) {
                // Respond immediately with zero-window ACK without advancing rcv_nxt
                try self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, 0, null);
                return;
            }

            // If currently blocked by backpressure, refuse new payload without advancing rcv_nxt
            if (f.is_blocked and payload.len > 0) {
                // Drop packet and reinforce zero-window
                try self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, 0, null);
                return;
            }

            // Normal payload forward
            if (payload.len > 0 and seq == f.rcv_nxt) {
                // Forward payload to SOCKS5 socket
                const sent = sys.write(f.socks_fd, payload) catch |err| {
                    if (err == error.WouldBlock) {
                        // Enter backpressure state
                        f.is_blocked = true;
                        const copy_len = @min(payload.len, f.overflow_buf.len);
                        @memcpy(f.overflow_buf[0..copy_len], payload[0..copy_len]);
                        f.overflow_len = @intCast(copy_len);
                        f.rcv_nxt +%= @intCast(copy_len);

                        // Arm EPOLLOUT to wake up when socket is writable again
                        var ev = linux.epoll_event{
                            .events = linux.EPOLL.IN | linux.EPOLL.OUT | linux.EPOLL.ERR,
                            .data = .{ .fd = f.socks_fd },
                        };
                        _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_MOD, f.socks_fd, &ev);

                        try self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, 0, null);
                        return;
                    }
                    self.table.markTombstone(f, 3000);
                    try self.sendRst(f);
                    return;
                };

                if (sent < payload.len) {
                    f.is_blocked = true;
                    const rem = payload.len - sent;
                    const copy_len = @min(rem, f.overflow_buf.len);
                    @memcpy(f.overflow_buf[0..copy_len], payload[sent .. sent + copy_len]);
                    f.overflow_len = @intCast(copy_len);
                    f.rcv_nxt +%= @intCast(sent + copy_len);

                    var ev = linux.epoll_event{
                        .events = linux.EPOLL.IN | linux.EPOLL.OUT | linux.EPOLL.ERR,
                        .data = .{ .fd = f.socks_fd },
                    };
                    _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_MOD, f.socks_fd, &ev);

                    try self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, 0, null);
                } else {
                    f.rcv_nxt +%= @intCast(payload.len);
                    // Send normal cumulative ACK
                    try self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, 65535, null);
                }
            }

            // Handle client FIN (half-close)
            if ((flags & protocol.TcpHeader.FLAG_FIN) != 0) {
                f.rcv_nxt +%= 1;
                f.state = .client_close_1;
                try self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, 65535, null);
                sys.shutdown(f.socks_fd);
            }
        }
    }

    fn handleNewSyn(self: *Engine, src_ip: u32, dst_ip: u32, src_port: u16, dst_port: u16, client_isn: u32) !void {
        const flow = self.table.allocate(src_ip, dst_ip, src_port, dst_port) orelse return;

        flow.c_isn = client_isn;
        flow.rcv_nxt = client_isn +% 1;

        // Generate pseudorandom server ISN using linux.getrandom
        var rand_val: u32 = 0x12345678;
        _ = linux.getrandom(std.mem.asBytes(&rand_val).ptr, 4, 0);
        flow.s_isn = rand_val;
        flow.snd_nxt = rand_val +% 1;

        // Initiate non-blocking connect to local SOCKS5 inbound
        const sock = try sys.createTcpSocket();
        errdefer sys.close(sock);

        flow.socks_fd = sock;
        flow.state = .upstream_connect;

        sys.connect(sock, self.cfg.socks_ip, self.cfg.socks_port) catch |err| {
            if (err != error.ConnectionPending) {
                self.table.markTombstone(flow, 1000);
                return;
            }
        };

        var event = linux.epoll_event{
            .events = linux.EPOLL.OUT | linux.EPOLL.IN | linux.EPOLL.ERR,
            .data = .{ .fd = sock },
        };
        const ctl_rc = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_ADD, sock, &event);
        if (linux.errno(ctl_rc) != .SUCCESS) {
            self.table.markTombstone(flow, 1000);
            return error.EpollCtlFailed;
        }
    }

    fn handleSocksEvent(self: *Engine, fd: sys.fd_t, events: u32) !void {
        const flow = self.table.findBySocksFd(fd) orelse return;

        if ((events & linux.EPOLL.ERR) != 0 or (events & linux.EPOLL.HUP) != 0) {
            try self.sendRst(flow);
            self.table.markTombstone(flow, 3000);
            return;
        }

        switch (flow.state) {
            .upstream_connect => {
                if ((events & linux.EPOLL.OUT) != 0) {
                    _ = try sys.write(fd, &socks5.Greeting);
                    flow.state = .socks5_auth_wait;
                }
            },
            .socks5_auth_wait => {
                if ((events & linux.EPOLL.IN) != 0) {
                    var auth_resp: [2]u8 = undefined;
                    const n = try sys.read(fd, &auth_resp);
                    if (n == 2 and auth_resp[0] == 0x05 and auth_resp[1] == 0x00) {
                        var req_buf: [10]u8 = undefined;
                        const req_len = socks5.formatConnectRequest(&req_buf, flow.dst_ip, flow.dst_port);
                        _ = try sys.write(fd, req_buf[0..req_len]);
                        flow.state = .socks5_connect_wait;
                    } else {
                        try self.sendRst(flow);
                        self.table.markTombstone(flow, 3000);
                    }
                }
            },
            .socks5_connect_wait => {
                if ((events & linux.EPOLL.IN) != 0) {
                    var resp: [10]u8 = undefined;
                    const n = try sys.read(fd, &resp);
                    if (n >= 4 and resp[0] == 0x05 and resp[1] == 0x00) {
                        // SOCKS5 ready! Send conservative SYN-ACK
                        flow.state = .established;

                        // Switch to EPOLL.IN only (arm EPOLL.OUT only on EAGAIN backpressure)
                        var ev = linux.epoll_event{
                            .events = linux.EPOLL.IN | linux.EPOLL.ERR,
                            .data = .{ .fd = fd },
                        };
                        _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_MOD, fd, &ev);

                        try self.sendTcpPacket(flow, protocol.TcpHeader.FLAG_SYN | protocol.TcpHeader.FLAG_ACK, flow.s_isn, flow.rcv_nxt, 65535, null);
                    } else {
                        try self.sendRst(flow);
                        self.table.markTombstone(flow, 3000);
                    }
                }
            },
            .established => {
                // Downstream data: SOCKS5 -> TUN
                if ((events & linux.EPOLL.IN) != 0) {
                    const n = sys.read(fd, &self.stream_buf) catch |err| {
                        if (err == error.WouldBlock) return;
                        try self.sendRst(flow);
                        self.table.markTombstone(flow, 3000);
                        return;
                    };
                    if (n == 0) {
                        // Upstream EOF
                        try self.sendTcpPacket(flow, protocol.TcpHeader.FLAG_FIN | protocol.TcpHeader.FLAG_ACK, flow.snd_nxt, flow.rcv_nxt, 65535, null);
                        flow.snd_nxt +%= 1;
                        flow.state = .upstream_closed;
                        return;
                    }

                    try self.sendTcpPacket(flow, protocol.TcpHeader.FLAG_ACK | protocol.TcpHeader.FLAG_PSH, flow.snd_nxt, flow.rcv_nxt, 65535, self.stream_buf[0..n]);
                    flow.snd_nxt +%= @intCast(n);
                }

                // If unblocked, flush overflow buffer and announce window reopen
                if ((events & linux.EPOLL.OUT) != 0 and flow.is_blocked) {
                    if (flow.overflow_len > 0) {
                        const written = sys.write(fd, flow.overflow_buf[0..flow.overflow_len]) catch 0;
                        if (written == flow.overflow_len) {
                            flow.overflow_len = 0;
                            flow.is_blocked = false;

                            // Disarm EPOLLOUT to prevent busy loop
                            var ev = linux.epoll_event{
                                .events = linux.EPOLL.IN | linux.EPOLL.ERR,
                                .data = .{ .fd = fd },
                            };
                            _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_MOD, fd, &ev);

                            try self.sendTcpPacket(flow, protocol.TcpHeader.FLAG_ACK, flow.snd_nxt, flow.rcv_nxt, 65535, null);
                        }
                    }
                }
            },
            .client_close_1 => {
                if ((events & linux.EPOLL.IN) != 0) {
                    const n = sys.read(fd, &self.stream_buf) catch 0;
                    if (n > 0) {
                        try self.sendTcpPacket(flow, protocol.TcpHeader.FLAG_ACK | protocol.TcpHeader.FLAG_PSH, flow.snd_nxt, flow.rcv_nxt, 65535, self.stream_buf[0..n]);
                        flow.snd_nxt +%= @intCast(n);
                    } else {
                        try self.sendTcpPacket(flow, protocol.TcpHeader.FLAG_FIN | protocol.TcpHeader.FLAG_ACK, flow.snd_nxt, flow.rcv_nxt, 65535, null);
                        self.table.markTombstone(flow, 3000);
                    }
                }
            },
            else => {},
        }
    }

    fn sendRst(self: *Engine, flow: *flow_mod.Flow) !void {
        try self.sendTcpPacket(flow, protocol.TcpHeader.FLAG_RST | protocol.TcpHeader.FLAG_ACK, flow.snd_nxt, flow.rcv_nxt, 0, null);
    }

    fn sendTcpPacket(self: *Engine, flow: *flow_mod.Flow, flags: u8, seq: u32, ack: u32, window: u16, payload: ?[]const u8) !void {
        const payload_len = if (payload) |p| p.len else 0;
        const total_len: u16 = @intCast(40 + payload_len);

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
        tcp_hdr.data_offset_reserved = 0x50;
        tcp_hdr.flags = flags;
        tcp_hdr.setWindow(window);
        tcp_hdr.checksum = 0;
        tcp_hdr.urgent_ptr = 0;

        if (payload) |p| {
            @memcpy(self.tx_packet_buf[40 .. 40 + payload_len], p);
        }

        const tcp_full_len: u16 = @intCast(20 + payload_len);
        tcp_hdr.checksum = protocol.calculateTcpChecksum(
            flow.dst_ip,
            flow.src_ip,
            tcp_full_len,
            self.tx_packet_buf[20..total_len],
        );

        _ = sys.write(self.cfg.tun_fd, self.tx_packet_buf[0..total_len]) catch {};
    }

    fn handleUdpRelayRead(self: *Engine) !void {
        const n = sys.recvfrom(self.udp_relay_fd, &self.udp_scratch_buf) catch |err| {
            if (err == error.WouldBlock) return;
            return err;
        };
        // SOCKS5 UDP response format:
        // [0..2]: RSV(0x00, 0x00), [2]: FRAG(0x00), [3]: ATYP
        // If ATYP=1 (IPv4): [4..8]: IP, [8..10]: Port, [10..n]: Payload
        if (n < 10) return;
        if (self.udp_scratch_buf[0] != 0 or self.udp_scratch_buf[1] != 0) return;
        if (self.udp_scratch_buf[2] != 0) return; // Discard fragmented packets
        if (self.udp_scratch_buf[3] != 0x01) return; // IPv4 only

        const remote_ip = std.mem.readInt(u32, self.udp_scratch_buf[4..8], .big);
        const remote_port = std.mem.readInt(u16, self.udp_scratch_buf[8..10], .big);
        const payload = self.udp_scratch_buf[10..n];

        // Find corresponding session to map back to original client
        // In UdpSession, dst_ip and dst_port were stored as raw packet bytes (already network big-endian).
        const session = self.udp_table.findByTarget(std.mem.nativeToBig(u32, remote_ip), std.mem.nativeToBig(u16, remote_port)) orelse return;

        try self.sendUdpPacket(session.dst_ip, session.src_ip, session.dst_port, session.src_port, payload);
    }

    fn sendUdpPacket(self: *Engine, src_ip: u32, dst_ip: u32, src_port: u16, dst_port: u16, payload: []const u8) !void {
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

        _ = sys.write(self.cfg.tun_fd, self.tx_packet_buf[0..total_len]) catch {};
    }
};

