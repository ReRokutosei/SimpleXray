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
    running: bool,

    // Reusable I/O buffers (Single allocation, 0 dynamic malloc in fast path, aligned for IP/TCP headers)
    rx_packet_buf: [4096]u8 align(4),
    tx_packet_buf: [4096]u8 align(4),
    stream_buf: [4096]u8,

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

        return Engine{
            .cfg = cfg,
            .epoll_fd = epoll_fd,
            .table = flow_mod.FlowTable.init(),
            .running = true,
            .rx_packet_buf = undefined,
            .tx_packet_buf = undefined,
            .stream_buf = undefined,
        };
    }

    pub fn deinit(self: *Engine) void {
        sys.close(self.epoll_fd);
        for (&self.table.flows) |*flow| {
            if (flow.socks_fd >= 0) {
                sys.close(flow.socks_fd);
                flow.socks_fd = -1;
            }
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
                if (fd == self.cfg.tun_fd) {
                    try self.handleTunRead();
                } else {
                    try self.handleSocksEvent(fd, ev.events);
                }
            }
        }
    }

    fn handleTunRead(self: *Engine) !void {
        const n = sys.read(self.cfg.tun_fd, &self.rx_packet_buf) catch |err| {
            if (err == error.WouldBlock) return;
            return err;
        };
        if (n < 40) return; // Minimum IPv4 + TCP length

        const ip_hdr: *const protocol.Ipv4Header = @ptrCast(@alignCast(&self.rx_packet_buf[0]));
        if (ip_hdr.version() != 4) return; // IPv4 only in Phase 1
        if (ip_hdr.protocol != 6) return; // TCP only in Phase 1

        const ip_hlen = ip_hdr.headerLen();
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

            // Normal payload forward
            if (payload.len > 0 and seq == f.rcv_nxt) {
                f.rcv_nxt +%= @intCast(payload.len);

                // Forward payload to SOCKS5 socket
                const sent = sys.write(f.socks_fd, payload) catch |err| {
                    if (err == error.WouldBlock) {
                        // Enter backpressure state
                        f.is_blocked = true;
                        @memcpy(f.overflow_buf[0..payload.len], payload);
                        f.overflow_len = @intCast(payload.len);
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
                    @memcpy(f.overflow_buf[0..rem], payload[sent..]);
                    f.overflow_len = @intCast(rem);
                    try self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, 0, null);
                } else {
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
};
