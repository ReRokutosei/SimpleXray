const std = @import("std");
const sys = @import("sys.zig");
const socks5 = @import("socks5.zig");

pub const State = enum(u8) {
    free = 0,
    upstream_connect,
    socks5_auth_wait,
    socks5_connect_wait,
    established,
    tombstone,
};

pub const Flow = struct {
    // 4-Tuple identification (12B)
    src_ip: u32,
    dst_ip: u32,
    src_port: u16,
    dst_port: u16,

    // Sequence & Acknowledgment tracking
    rcv_nxt: u32,
    snd_nxt: u32,
    snd_una: u32,
    c_isn: u32,
    s_isn: u32,

    // SOCKS5 and I/O state
    socks_fd: i32,
    deadline_ms: i64,
    peer_wnd: u16,
    state: State,
    is_blocked: bool,
    client_fin: bool,
    upstream_fin: bool,
    tun_blocked: bool,
    hs: socks5.Handshake = .{},
    _pad: [1]u8 = [_]u8{0} ** 1,

    pub fn matches(self: *const Flow, s_ip: u32, d_ip: u32, s_port: u16, d_port: u16) bool {
        return self.src_ip == s_ip and
            self.dst_ip == d_ip and
            self.src_port == s_port and
            self.dst_port == d_port;
    }

    pub fn reset(self: *Flow) void {
        self.src_ip = 0;
        self.dst_ip = 0;
        self.src_port = 0;
        self.dst_port = 0;
        self.rcv_nxt = 0;
        self.snd_nxt = 0;
        self.snd_una = 0;
        self.c_isn = 0;
        self.s_isn = 0;
        self.socks_fd = -1;
        self.deadline_ms = 0;
        self.peer_wnd = 0;
        self.state = .free;
        self.is_blocked = false;
        self.client_fin = false;
        self.upstream_fin = false;
        self.tun_blocked = false;
        self.hs.reset();
    }
};

pub const FlowTable = struct {
    pub const CAPACITY: usize = 1024;

    flows: [CAPACITY]Flow,

    pub fn initInto(self: *FlowTable) void {
        for (&self.flows) |*flow| {
            flow.reset();
        }
    }

    pub fn init() FlowTable {
        var table: FlowTable = undefined;
        table.initInto();
        return table;
    }

    pub fn indexOf(self: *const FlowTable, flow: *const Flow) usize {
        const base = @intFromPtr(&self.flows[0]);
        const ptr = @intFromPtr(flow);
        return (ptr - base) / @sizeOf(Flow);
    }

    pub fn findFlow(self: *FlowTable, src_ip: u32, dst_ip: u32, src_port: u16, dst_port: u16) ?*Flow {
        for (&self.flows) |*flow| {
            if (flow.state != .free and flow.matches(src_ip, dst_ip, src_port, dst_port)) {
                return flow;
            }
        }
        return null;
    }

    pub fn activeCount(self: *const FlowTable) usize {
        var count: usize = 0;
        for (&self.flows) |*flow| {
            if (flow.state != .free) count += 1;
        }
        return count;
    }

    pub fn allocate(self: *FlowTable, src_ip: u32, dst_ip: u32, src_port: u16, dst_port: u16) ?*Flow {
        // First pass: try to find a completely free slot
        for (&self.flows) |*flow| {
            if (flow.state == .free) {
                flow.reset();
                flow.src_ip = src_ip;
                flow.dst_ip = dst_ip;
                flow.src_port = src_port;
                flow.dst_port = dst_port;
                flow.state = .upstream_connect;
                return flow;
            }
        }

        // Second pass: evict expired tombstones (with adaptive threshold under high watermark)
        const now = sys.monotonicMs();
        const active = self.activeCount();
        const tombstone_slack: i64 = if (active >= 940)
            2900 // under critical watermark (>=940/1024), tombstone kept for only 100ms
        else if (active >= 820)
            2500 // under high watermark (>=820/1024), tombstone kept for only 500ms
        else
            0;

        var oldest_tombstone: ?*Flow = null;
        var oldest_tombstone_time: i64 = std.math.maxInt(i64);

        for (&self.flows) |*flow| {
            if (flow.state == .tombstone) {
                if (now + tombstone_slack >= flow.deadline_ms) {
                    flow.reset();
                    flow.src_ip = src_ip;
                    flow.dst_ip = dst_ip;
                    flow.src_port = src_port;
                    flow.dst_port = dst_port;
                    flow.state = .upstream_connect;
                    return flow;
                }
                if (flow.deadline_ms < oldest_tombstone_time) {
                    oldest_tombstone_time = flow.deadline_ms;
                    oldest_tombstone = flow;
                }
            }
        }

        // Third pass: under high CPS pressure, forcibly evict the oldest tombstone
        if (oldest_tombstone) |flow| {
            flow.reset();
            flow.src_ip = src_ip;
            flow.dst_ip = dst_ip;
            flow.src_port = src_port;
            flow.dst_port = dst_port;
            flow.state = .upstream_connect;
            return flow;
        }

        // Fourth pass: table completely saturated with active flows; forcibly recycle oldest flow.
        // NOTE: socks_fd is intentionally NOT closed here because the caller (Engine) must first call
        // epoll_ctl(DEL) before close() to prevent stale epoll events firing against the recycled slot.
        // The caller is responsible for closing the returned evicted_socks_fd.
        var oldest_flow: ?*Flow = null;
        var oldest_time: i64 = std.math.maxInt(i64);
        for (&self.flows) |*flow| {
            if (flow.deadline_ms < oldest_time) {
                oldest_time = flow.deadline_ms;
                oldest_flow = flow;
            }
        }
        if (oldest_flow) |flow| {
            const old_fd = flow.socks_fd;
            flow.reset();
            flow.socks_fd = old_fd; // Preserved for Engine to epoll_ctl(DEL) and sys.close()
            flow.src_ip = src_ip;
            flow.dst_ip = dst_ip;
            flow.src_port = src_port;
            flow.dst_port = dst_port;
            flow.state = .upstream_connect;
            return flow;
        }

        return null;
    }

    pub fn markTombstone(self: *FlowTable, flow: *Flow, duration_ms: i64) void {
        _ = self;
        if (flow.socks_fd >= 0) {
            sys.close(flow.socks_fd);
            flow.socks_fd = -1;
        }
        flow.state = .tombstone;
        flow.deadline_ms = sys.monotonicMs() + duration_ms;
    }
};

pub const UdpSession = struct {
    last_active_ms: i64,
    src_ip: u32,
    dst_ip: u32,
    src_port: u16,
    dst_port: u16,
    active: bool,
    _pad: [3]u8 = [_]u8{0} ** 3,

    pub fn matches(self: *const UdpSession, s_ip: u32, d_ip: u32, s_port: u16, d_port: u16) bool {
        return self.active and
            self.src_ip == s_ip and
            self.dst_ip == d_ip and
            self.src_port == s_port and
            self.dst_port == d_port;
    }
};

pub const DnsQuery = struct {
    expires_at_ms: i64,
    src_ip: u32,
    dst_ip: u32,
    src_port: u16,
    dst_port: u16,
    dns_tx_id: u16,
    active: bool,
    _pad: u8 = 0,
};

pub const UdpTable = struct {
    pub const CAPACITY: usize = 256;
    pub const DNS_QUERY_CAPACITY: usize = 64;

    sessions: [CAPACITY]UdpSession,
    dns_queries: [DNS_QUERY_CAPACITY]DnsQuery,
    dns_query_head: usize,

    pub fn initInto(self: *UdpTable) void {
        for (&self.sessions) |*s| {
            s.active = false;
        }
        for (&self.dns_queries) |*q| {
            q.active = false;
        }
        self.dns_query_head = 0;
    }

    pub fn init() UdpTable {
        var table: UdpTable = undefined;
        table.initInto();
        return table;
    }

    pub fn recordDnsQuery(self: *UdpTable, src_ip: u32, dst_ip: u32, src_port: u16, dst_port: u16, dns_tx_id: u16, ttl_ms: i64) void {
        const now = sys.monotonicMs();
        const slot = &self.dns_queries[self.dns_query_head];
        slot.src_ip = src_ip;
        slot.dst_ip = dst_ip;
        slot.src_port = src_port;
        slot.dst_port = dst_port;
        slot.dns_tx_id = dns_tx_id;
        slot.expires_at_ms = now + ttl_ms;
        slot.active = true;
        self.dns_query_head = (self.dns_query_head + 1) % DNS_QUERY_CAPACITY;
    }

    pub fn findDnsQuery(self: *UdpTable, dst_ip: u32, dst_port: u16, dns_tx_id: u16) ?DnsQuery {
        const now = sys.monotonicMs();
        var i: usize = 0;
        while (i < DNS_QUERY_CAPACITY) : (i += 1) {
            const idx = (self.dns_query_head + DNS_QUERY_CAPACITY - 1 - i) % DNS_QUERY_CAPACITY;
            const q = &self.dns_queries[idx];
            if (!q.active or now >= q.expires_at_ms) continue;
            if (q.dst_ip == dst_ip and q.dst_port == dst_port and q.dns_tx_id == dns_tx_id) {
                q.active = false; // Consumed
                return q.*;
            }
        }
        return null;
    }

    pub fn sweep(self: *UdpTable, now: i64, session_idle_ms: i64) void {
        for (&self.sessions) |*s| {
            if (s.active and now - s.last_active_ms >= session_idle_ms) {
                s.active = false;
            }
        }
        for (&self.dns_queries) |*q| {
            if (q.active and now >= q.expires_at_ms) {
                q.active = false;
            }
        }
    }

    pub fn touchOrAllocate(self: *UdpTable, src_ip: u32, dst_ip: u32, src_port: u16, dst_port: u16) ?*UdpSession {
        const now = sys.monotonicMs();
        for (&self.sessions) |*s| {
            if (s.matches(src_ip, dst_ip, src_port, dst_port)) {
                s.last_active_ms = now;
                return s;
            }
        }

        // Find free slot
        for (&self.sessions) |*s| {
            if (!s.active) {
                s.src_ip = src_ip;
                s.dst_ip = dst_ip;
                s.src_port = src_port;
                s.dst_port = dst_port;
                s.last_active_ms = now;
                s.active = true;
                return s;
            }
        }

        // Evict oldest session (LRU-like eviction if table full)
        var oldest_idx: usize = 0;
        var oldest_time: i64 = self.sessions[0].last_active_ms;
        for (self.sessions[1..], 1..) |*s, idx| {
            if (s.last_active_ms < oldest_time) {
                oldest_time = s.last_active_ms;
                oldest_idx = idx;
            }
        }

        const s = &self.sessions[oldest_idx];
        s.src_ip = src_ip;
        s.dst_ip = dst_ip;
        s.src_port = src_port;
        s.dst_port = dst_port;
        s.last_active_ms = now;
        s.active = true;
        return s;
    }

    pub fn findByTarget(self: *UdpTable, dst_ip: u32, dst_port: u16) ?*UdpSession {
        var latest: ?*UdpSession = null;
        for (&self.sessions) |*s| {
            if (s.active and s.dst_ip == dst_ip and s.dst_port == dst_port) {
                if (latest == null or s.last_active_ms > latest.?.last_active_ms) {
                    latest = s;
                }
            }
        }
        return latest;
    }
};

comptime {
    std.debug.assert(@sizeOf(Flow) == 80);
    std.debug.assert(@sizeOf(UdpSession) == 24);
    std.debug.assert(@sizeOf(DnsQuery) == 24);
}

test "FlowTable allocation and tombstone eviction" {
    var table: FlowTable = undefined;
    table.initInto();
    const flow1 = table.allocate(1, 2, 3, 4);
    try std.testing.expect(flow1 != null);
    try std.testing.expectEqual(State.upstream_connect, flow1.?.state);

    const lookup = table.findFlow(1, 2, 3, 4);
    try std.testing.expectEqual(flow1, lookup);

    table.markTombstone(flow1.?, -10); // already expired
    const flow2 = table.allocate(5, 6, 7, 8);
    try std.testing.expect(flow2 != null);
}

test "UdpTable sweep expires sessions and dns queries" {
    var table: UdpTable = undefined;
    table.initInto();

    const session = table.touchOrAllocate(1, 2, 3, 4);
    try std.testing.expect(session != null);
    try std.testing.expect(table.findByTarget(2, 4) != null);

    const now = sys.monotonicMs();
    session.?.last_active_ms = now - 60_000;
    _ = table.recordDnsQuery(1, 2, 3, 4, 0x1234, -1);
    table.sweep(now, 30_000);

    try std.testing.expect(!session.?.active);
    try std.testing.expect(table.findDnsQuery(2, 4, 0x1234) == null);

    _ = table.recordDnsQuery(1, 2, 3, 4, 0x5678, 30_000);
    try std.testing.expect(table.findDnsQuery(2, 4, 0x5678) != null);
}

test "FlowTable adaptive high watermark tombstone eviction" {
    var table: FlowTable = undefined;
    table.initInto();

    try std.testing.expectEqual(@as(usize, 0), table.activeCount());

    // Fill table up to high watermark (850 flows)
    var i: u16 = 0;
    while (i < 850) : (i += 1) {
        const f = table.allocate(0x0a000001, 0x0a000002, 1000 + i, 80);
        try std.testing.expect(f != null);
        f.?.state = .established;
    }
    try std.testing.expectEqual(@as(usize, 850), table.activeCount());

    // Mark one flow as tombstone with 2000ms deadline (normally unexpired since TOMBSTONE_MS=3000)
    const victim = &table.flows[0];
    table.markTombstone(victim, 2000);

    // Because active >= 820, tombstone_slack is 2500ms, so 2000ms deadline is adaptively treated as expired!
    const new_flow = table.allocate(0x0a000001, 0x0a000002, 9999, 80);
    try std.testing.expect(new_flow != null);
}
