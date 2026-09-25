const std = @import("std");
const linux = std.os.linux;
const protocol = @import("protocol.zig");
const flow_mod = @import("flow.zig");
const socks5 = @import("socks5.zig");
const sys = @import("sys.zig");

const EPOLLRDHUP: u32 = 0x2000;
const O_NONBLOCK: usize = 0x800;

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
    stop_fd: sys.fd_t,
    running: bool,

    // Reusable I/O buffers (Single allocation, 0 dynamic malloc in fast path, aligned for IP/TCP headers)
    rx_packet_buf: [4096]u8 align(4),
    tx_packet_buf: [4096]u8 align(4),
    stream_buf: [4096]u8,
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
        self.stop_fd = stop_fd;
        self.running = true;
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

    pub fn run(self: *Engine) !void {
        var events: [64]linux.epoll_event = undefined;
        logCore(4, "Engine.run entering loop: epoll_fd={d}, tun_fd={d}, stop_fd={d}", .{ self.epoll_fd, self.cfg.tun_fd, self.stop_fd });

        while (self.running) {
            const num_events = linux.epoll_wait(self.epoll_fd, &events, 64, 100);
            if (num_events < 0) {
                const err = linux.errno(num_events);
                if (err == .INTR) continue;
                logCore(6, "Engine.run epoll_wait returned error: {}", .{err});
                return error.EpollWaitFailed;
            }

            const count: usize = @intCast(num_events);
            for (events[0..count]) |ev| {
                const fd = ev.data.fd;
                if (fd == self.stop_fd) {
                    logCore(4, "Engine.run stop_fd triggered with events=0x{x}", .{ev.events});
                    self.running = false;
                    return;
                } else if (fd == self.cfg.tun_fd) {
                    if ((ev.events & (linux.EPOLL.ERR | linux.EPOLL.HUP)) != 0) {
                        logCore(6, "Engine.run tun_fd received ERR/HUP: 0x{x}", .{ev.events});
                    }
                    self.handleTunRead();
                } else if (fd == self.udp_relay_fd) {
                    self.handleUdpRelayRead();
                } else if (fd == self.udp_ctrl_fd) {
                    // UDP Associate TCP control connection status
                    var should_close = false;
                    if ((ev.events & (linux.EPOLL.ERR | linux.EPOLL.HUP | EPOLLRDHUP)) != 0) {
                        should_close = true;
                    } else if ((ev.events & linux.EPOLL.IN) != 0) {
                        var dummy: [16]u8 = undefined;
                        const rn = sys.read(self.udp_ctrl_fd, &dummy) catch 0;
                        if (rn == 0) {
                            should_close = true;
                        }
                    }
                    if (should_close) {
                        logCore(4, "Engine.run udp_ctrl_fd disconnected (events=0x{x})", .{ev.events});
                        _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_DEL, self.udp_ctrl_fd, null);
                        sys.close(self.udp_ctrl_fd);
                        self.udp_ctrl_fd = -1;
                        self.udp_relay_port = 0;
                    }
                } else {
                    self.handleSocksEvent(fd, ev.events);
                }
            }
        }
        logCore(4, "Engine.run while loop terminated, self.running={}", .{self.running});
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

        _ = try sys.writeSocket(sock, &socks5.Greeting);

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
        _ = try sys.writeSocket(sock, req_buf[0..req_len]);

        _ = linux.poll(&pfd_in, 1, 500);
        var resp_buf: [10]u8 = undefined;
        const rn = try sys.read(sock, &resp_buf);
        if (rn < 10 or resp_buf[0] != 0x05 or resp_buf[1] != 0x00) {
            return error.Socks5UdpAssociateFailed;
        }

        const relay_port = (@as(u16, resp_buf[8]) << 8) | @as(u16, resp_buf[9]);
        self.udp_ctrl_fd = sock;
        self.udp_relay_port = relay_port;

        // Monitor UDP ctrl socket for disconnection (including graceful FIN)
        var ev = linux.epoll_event{
            .events = linux.EPOLL.IN | linux.EPOLL.ERR | linux.EPOLL.HUP | 0x2000,
            .data = .{ .fd = sock },
        };
        _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_ADD, sock, &ev);
    }

    fn handleTunRead(self: *Engine) void {
        const n = sys.read(self.cfg.tun_fd, &self.rx_packet_buf) catch return;
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

            // If DNS query (target port 53), extract DNS transaction ID (first 2 bytes of payload)
            if (udp_hdr.getDstPort() == 53 and payload.len >= 2) {
                const dns_tx_id = std.mem.readInt(u16, payload[0..2], .big);
                self.udp_table.recordDnsQuery(src_ip, dst_ip, src_port, dst_port, dns_tx_id);
            }

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
                self.handleNewSyn(src_ip, dst_ip, src_port, dst_port, seq);
            }
            return;
        }

        const f = flow.?;

        // Fast Recycle: If we receive a new SYN for a flow in tombstone, immediately reset and re-allocate!
        if ((flags & protocol.TcpHeader.FLAG_SYN) != 0 and (flags & protocol.TcpHeader.FLAG_ACK) == 0) {
            if (f.state == .tombstone) {
                f.reset();
                self.handleNewSyn(src_ip, dst_ip, src_port, dst_port, seq);
                return;
            } else if (f.state == .established) {
                self.sendTcpPacket(f, protocol.TcpHeader.FLAG_SYN | protocol.TcpHeader.FLAG_ACK, f.s_isn, f.rcv_nxt, 65535, null);
                return;
            }
        }

        if ((flags & protocol.TcpHeader.FLAG_RST) != 0) {
            self.table.markTombstone(f, 3000);
            return;
        }

        if (f.state == .established) {
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
                        const win: u16 = if (f.is_blocked) 0 else 65535;
                        self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, win, null);
                        return;
                    }
                } else if (seq != f.rcv_nxt) {
                    // Out-of-order packet: do not buffer, send ACK with expected rcv_nxt
                    const win: u16 = if (f.is_blocked) 0 else 65535;
                    self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, win, null);
                    return;
                }
            }

            // Normal payload forward
            if (effective_payload.len > 0) {
                const sent = sys.writeSocket(f.socks_fd, effective_payload) catch |err| {
                    if (err == error.WouldBlock) {
                        // Enter backpressure state
                        f.is_blocked = true;
                        const copy_len = @min(effective_payload.len, f.overflow_buf.len);
                        @memcpy(f.overflow_buf[0..copy_len], effective_payload[0..copy_len]);
                        f.overflow_len = @intCast(copy_len);
                        f.rcv_nxt +%= @intCast(copy_len);

                        // Arm EPOLLOUT to wake up when socket is writable again
                        var ev = linux.epoll_event{
                            .events = linux.EPOLL.IN | linux.EPOLL.OUT | linux.EPOLL.ERR,
                            .data = .{ .fd = f.socks_fd },
                        };
                        _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_MOD, f.socks_fd, &ev);

                        self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, 0, null);
                        return;
                    }
                    self.sendRst(f);
                    self.table.markTombstone(f, 3000);
                    return;
                };

                if (sent < effective_payload.len) {
                    f.is_blocked = true;
                    const rem = effective_payload.len - sent;
                    const copy_len = @min(rem, f.overflow_buf.len);
                    @memcpy(f.overflow_buf[0..copy_len], effective_payload[sent .. sent + copy_len]);
                    f.overflow_len = @intCast(copy_len);
                    f.rcv_nxt +%= @intCast(sent + copy_len);

                    var ev = linux.epoll_event{
                        .events = linux.EPOLL.IN | linux.EPOLL.OUT | linux.EPOLL.ERR,
                        .data = .{ .fd = f.socks_fd },
                    };
                    _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_MOD, f.socks_fd, &ev);

                    self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, 0, null);
                } else {
                    f.rcv_nxt +%= @intCast(effective_payload.len);
                    self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, 65535, null);
                }
            }

            // Handle client FIN (half-close)
            if ((flags & protocol.TcpHeader.FLAG_FIN) != 0) {
                f.rcv_nxt +%= 1;
                f.state = .client_close_1;
                const win: u16 = if (f.is_blocked) 0 else 65535;
                self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, win, null);
                sys.shutdown(f.socks_fd);
            }
        } else if (f.state == .client_close_1) {
            if ((flags & protocol.TcpHeader.FLAG_FIN) != 0) {
                const win: u16 = if (f.is_blocked) 0 else 65535;
                self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, win, null);
            }
        } else if (f.state == .upstream_closed) {
            if ((flags & protocol.TcpHeader.FLAG_FIN) != 0) {
                f.rcv_nxt +%= 1;
                const win: u16 = if (f.is_blocked) 0 else 65535;
                self.sendTcpPacket(f, protocol.TcpHeader.FLAG_ACK, f.snd_nxt, f.rcv_nxt, win, null);
                self.table.markTombstone(f, 3000);
            }
        }
    }

    fn handleNewSyn(self: *Engine, src_ip: u32, dst_ip: u32, src_port: u16, dst_port: u16, client_isn: u32) void {
        const flow = self.table.allocate(src_ip, dst_ip, src_port, dst_port) orelse return;

        flow.c_isn = client_isn;
        flow.rcv_nxt = client_isn +% 1;

        // Generate pseudorandom server ISN using linux.getrandom
        var rand_val: u32 = 0x12345678;
        _ = linux.getrandom(std.mem.asBytes(&rand_val).ptr, 4, 0);
        flow.s_isn = rand_val;
        flow.snd_nxt = rand_val +% 1;

        // Initiate non-blocking connect to local SOCKS5 inbound
        const sock = sys.createTcpSocket() catch {
            self.sendRst(flow);
            self.table.markTombstone(flow, 1000);
            return;
        };

        flow.socks_fd = sock;
        flow.state = .upstream_connect;

        sys.connect(sock, self.cfg.socks_ip, self.cfg.socks_port) catch |err| {
            if (err != error.ConnectionPending) {
                self.sendRst(flow);
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
            self.sendRst(flow);
            self.table.markTombstone(flow, 1000);
            return;
        }
    }

    fn handleSocksEvent(self: *Engine, fd: sys.fd_t, events: u32) void {
        const flow = self.table.findBySocksFd(fd) orelse return;

        if ((events & (linux.EPOLL.ERR | linux.EPOLL.HUP)) != 0) {
            self.sendRst(flow);
            self.table.markTombstone(flow, 3000);
            return;
        }

        switch (flow.state) {
            .upstream_connect => {
                if ((events & linux.EPOLL.OUT) != 0) {
                    _ = sys.writeSocket(fd, &socks5.Greeting) catch |err| {
                        if (err != error.WouldBlock) {
                            self.sendRst(flow);
                            self.table.markTombstone(flow, 3000);
                        }
                        return;
                    };
                    flow.state = .socks5_auth_wait;

                    // Crucial: Disarm EPOLLOUT to prevent busy-looping while waiting for auth response
                    var ev = linux.epoll_event{
                        .events = linux.EPOLL.IN | linux.EPOLL.ERR,
                        .data = .{ .fd = fd },
                    };
                    _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_MOD, fd, &ev);
                }
            },
            .socks5_auth_wait => {
                if ((events & linux.EPOLL.IN) != 0) {
                    var auth_resp: [2]u8 = undefined;
                    const n = sys.read(fd, &auth_resp) catch |err| {
                        if (err != error.WouldBlock) {
                            self.sendRst(flow);
                            self.table.markTombstone(flow, 3000);
                        }
                        return;
                    };
                    if (n == 2 and auth_resp[0] == 0x05 and auth_resp[1] == 0x00) {
                        var req_buf: [10]u8 = undefined;
                        const req_len = socks5.formatConnectRequest(&req_buf, flow.dst_ip, flow.dst_port);
                        _ = sys.writeSocket(fd, req_buf[0..req_len]) catch |err| {
                            if (err != error.WouldBlock) {
                                self.sendRst(flow);
                                self.table.markTombstone(flow, 3000);
                            }
                            return;
                        };
                        flow.state = .socks5_connect_wait;
                    } else if (n > 0 or n == 0) {
                        self.sendRst(flow);
                        self.table.markTombstone(flow, 3000);
                    }
                }
            },
            .socks5_connect_wait => {
                if ((events & linux.EPOLL.IN) != 0) {
                    var resp: [10]u8 = undefined;
                    const n = sys.read(fd, &resp) catch |err| {
                        if (err != error.WouldBlock) {
                            self.sendRst(flow);
                            self.table.markTombstone(flow, 3000);
                        }
                        return;
                    };
                    if (n >= 4 and resp[0] == 0x05 and resp[1] == 0x00) {
                        // SOCKS5 ready! Send conservative SYN-ACK
                        flow.state = .established;

                        // Switch to EPOLL.IN only (arm EPOLL.OUT only on EAGAIN backpressure)
                        var ev = linux.epoll_event{
                            .events = linux.EPOLL.IN | linux.EPOLL.ERR,
                            .data = .{ .fd = fd },
                        };
                        _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_MOD, fd, &ev);

                        self.sendTcpPacket(flow, protocol.TcpHeader.FLAG_SYN | protocol.TcpHeader.FLAG_ACK, flow.s_isn, flow.rcv_nxt, 65535, null);
                    } else if (n > 0 or n == 0) {
                        self.sendRst(flow);
                        self.table.markTombstone(flow, 3000);
                    }
                }
            },
            .established => {
                // Downstream data: SOCKS5 -> TUN
                if ((events & linux.EPOLL.IN) != 0) {
                    const n = sys.read(fd, &self.stream_buf) catch |err| {
                        if (err == error.WouldBlock) return;
                        self.sendRst(flow);
                        self.table.markTombstone(flow, 3000);
                        return;
                    };
                    if (n == 0) {
                        // Upstream EOF: send FIN-ACK, close and deregister socket from epoll
                        const win_to_announce: u16 = if (flow.is_blocked) 0 else 65535;
                        self.sendTcpPacket(flow, protocol.TcpHeader.FLAG_FIN | protocol.TcpHeader.FLAG_ACK, flow.snd_nxt, flow.rcv_nxt, win_to_announce, null);
                        flow.snd_nxt +%= 1;
                        flow.state = .upstream_closed;

                        _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_DEL, fd, null);
                        sys.close(fd);
                        flow.socks_fd = -1;
                        return;
                    }

                    const max_mss: usize = if (self.cfg.mtu > 40) self.cfg.mtu - 40 else 1460;
                    const win_to_announce: u16 = if (flow.is_blocked) 0 else 65535;
                    var offset: usize = 0;
                    while (offset < n) {
                        const chunk_len = @min(n - offset, max_mss);
                        const chunk = self.stream_buf[offset .. offset + chunk_len];
                        const is_last = (offset + chunk_len == n);
                        const psh_flag: u8 = if (is_last) protocol.TcpHeader.FLAG_PSH else 0;

                        self.sendTcpPacket(
                            flow,
                            protocol.TcpHeader.FLAG_ACK | psh_flag,
                            flow.snd_nxt,
                            flow.rcv_nxt,
                            win_to_announce,
                            chunk,
                        );
                        flow.snd_nxt +%= @intCast(chunk_len);
                        offset += chunk_len;
                    }
                }

                // If unblocked, flush overflow buffer and announce window reopen
                if ((events & linux.EPOLL.OUT) != 0 and flow.is_blocked) {
                    if (flow.overflow_len > 0) {
                        const written = sys.writeSocket(fd, flow.overflow_buf[0..flow.overflow_len]) catch |err| {
                            if (err == error.WouldBlock) return;
                            self.sendRst(flow);
                            self.table.markTombstone(flow, 3000);
                            return;
                        };
                        if (written < flow.overflow_len) {
                            const rem = flow.overflow_len - written;
                            std.mem.copyForwards(u8, flow.overflow_buf[0..rem], flow.overflow_buf[written .. flow.overflow_len]);
                            flow.overflow_len = @intCast(rem);
                        } else {
                            flow.overflow_len = 0;
                            flow.is_blocked = false;

                            // Disarm EPOLLOUT to prevent busy loop
                            var ev = linux.epoll_event{
                                .events = linux.EPOLL.IN | linux.EPOLL.ERR,
                                .data = .{ .fd = fd },
                            };
                            _ = linux.epoll_ctl(self.epoll_fd, linux.EPOLL.CTL_MOD, fd, &ev);

                            self.sendTcpPacket(flow, protocol.TcpHeader.FLAG_ACK, flow.snd_nxt, flow.rcv_nxt, 65535, null);
                        }
                    }
                }
            },
            .client_close_1 => {
                if ((events & linux.EPOLL.IN) != 0) {
                    const n = sys.read(fd, &self.stream_buf) catch 0;
                    if (n > 0) {
                        const max_mss: usize = if (self.cfg.mtu > 40) self.cfg.mtu - 40 else 1460;
                        var offset: usize = 0;
                        while (offset < n) {
                            const chunk_len = @min(n - offset, max_mss);
                            const chunk = self.stream_buf[offset .. offset + chunk_len];
                            const is_last = (offset + chunk_len == n);
                            const psh_flag: u8 = if (is_last) protocol.TcpHeader.FLAG_PSH else 0;

                            self.sendTcpPacket(
                                flow,
                                protocol.TcpHeader.FLAG_ACK | psh_flag,
                                flow.snd_nxt,
                                flow.rcv_nxt,
                                65535,
                                chunk,
                            );
                            flow.snd_nxt +%= @intCast(chunk_len);
                            offset += chunk_len;
                        }
                    } else {
                        self.sendTcpPacket(flow, protocol.TcpHeader.FLAG_FIN | protocol.TcpHeader.FLAG_ACK, flow.snd_nxt, flow.rcv_nxt, 65535, null);
                        self.table.markTombstone(flow, 3000);
                    }
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

    fn handleUdpRelayRead(self: *Engine) void {
        const n = sys.recvfrom(self.udp_relay_fd, &self.udp_scratch_buf) catch return;
        // SOCKS5 UDP response format:
        // [0..2]: RSV(0x00, 0x00), [2]: FRAG(0x00), [3]: ATYP
        // If ATYP=1 (IPv4): [4..8]: IP, [8..10]: Port, [10..n]: Payload
        if (n < 10) return;
        if (self.udp_scratch_buf[0] != 0 or self.udp_scratch_buf[1] != 0) return;
        if (self.udp_scratch_buf[2] != 0) return; // Discard fragmented packets
        if (self.udp_scratch_buf[3] != 0x01) return; // IPv4 only

        // Read IP and Port safely
        const remote_ip_raw = std.mem.readInt(u32, self.udp_scratch_buf[4..8], .native);
        const remote_port_raw = std.mem.readInt(u16, self.udp_scratch_buf[8..10], .native);
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

