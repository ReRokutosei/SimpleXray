const std = @import("std");
const sys = @import("sys.zig");

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

    // Sequence & Acknowledgment tracking (16B)
    rcv_nxt: u32,
    snd_nxt: u32,
    c_isn: u32,
    s_isn: u32,

    // SOCKS5 and I/O state
    socks_fd: i32,
    tombstone_until_ms: i64,
    overflow_len: u16,
    overflow_pool_idx: u8,
    state: State,
    is_blocked: bool,
    client_fin: bool,
    _pad: [2]u8 = [_]u8{0} ** 2,

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
        self.c_isn = 0;
        self.s_isn = 0;
        self.socks_fd = -1;
        self.tombstone_until_ms = 0;
        self.overflow_len = 0;
        self.overflow_pool_idx = 0xff;
        self.state = .free;
        self.is_blocked = false;
        self.client_fin = false;
    }
};

pub const FlowTable = struct {
    pub const CAPACITY: usize = 1024;
    pub const OVERFLOW_POOL_SIZE: usize = 16;

    flows: [CAPACITY]Flow,
    overflow_pool: [OVERFLOW_POOL_SIZE][2048]u8,
    overflow_pool_used: [OVERFLOW_POOL_SIZE]bool,

    pub fn initInto(self: *FlowTable) void {
        for (&self.flows) |*flow| {
            flow.reset();
        }
        for (&self.overflow_pool_used) |*used| {
            used.* = false;
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

    pub fn getOverflowBuf(self: *FlowTable, flow: *Flow) ?[]u8 {
        if (flow.overflow_pool_idx < OVERFLOW_POOL_SIZE) {
            return &self.overflow_pool[flow.overflow_pool_idx];
        }
        for (&self.overflow_pool_used, 0..) |*used, idx| {
            if (!used.*) {
                used.* = true;
                flow.overflow_pool_idx = @intCast(idx);
                return &self.overflow_pool[idx];
            }
        }
        return null;
    }

    pub fn releaseOverflowBuf(self: *FlowTable, flow: *Flow) void {
        if (flow.overflow_pool_idx < OVERFLOW_POOL_SIZE) {
            self.overflow_pool_used[flow.overflow_pool_idx] = false;
            flow.overflow_pool_idx = 0xff;
            flow.overflow_len = 0;
        }
    }

    pub fn findFlow(self: *FlowTable, src_ip: u32, dst_ip: u32, src_port: u16, dst_port: u16) ?*Flow {
        for (&self.flows) |*flow| {
            if (flow.state != .free and flow.matches(src_ip, dst_ip, src_port, dst_port)) {
                return flow;
            }
        }
        return null;
    }

    pub fn allocate(self: *FlowTable, src_ip: u32, dst_ip: u32, src_port: u16, dst_port: u16) ?*Flow {
        // First pass: try to find a completely free slot
        for (&self.flows) |*flow| {
            if (flow.state == .free) {
                self.releaseOverflowBuf(flow);
                flow.reset();
                flow.src_ip = src_ip;
                flow.dst_ip = dst_ip;
                flow.src_port = src_port;
                flow.dst_port = dst_port;
                flow.state = .upstream_connect;
                return flow;
            }
        }

        // Second pass: evict expired tombstones
        const now = sys.monotonicMs();
        var oldest_tombstone: ?*Flow = null;
        var oldest_tombstone_time: i64 = std.math.maxInt(i64);

        for (&self.flows) |*flow| {
            if (flow.state == .tombstone) {
                if (now >= flow.tombstone_until_ms) {
                    self.releaseOverflowBuf(flow);
                    flow.reset();
                    flow.src_ip = src_ip;
                    flow.dst_ip = dst_ip;
                    flow.src_port = src_port;
                    flow.dst_port = dst_port;
                    flow.state = .upstream_connect;
                    return flow;
                }
                if (flow.tombstone_until_ms < oldest_tombstone_time) {
                    oldest_tombstone_time = flow.tombstone_until_ms;
                    oldest_tombstone = flow;
                }
            }
        }

        // Third pass: under high CPS pressure, forcibly evict the oldest tombstone
        if (oldest_tombstone) |flow| {
            self.releaseOverflowBuf(flow);
            flow.reset();
            flow.src_ip = src_ip;
            flow.dst_ip = dst_ip;
            flow.src_port = src_port;
            flow.dst_port = dst_port;
            flow.state = .upstream_connect;
            return flow;
        }

        // Fourth pass: table completely saturated with active flows; forcibly recycle oldest flow to guarantee zero deadlocks
        var oldest_flow: ?*Flow = null;
        var oldest_time: i64 = std.math.maxInt(i64);
        for (&self.flows) |*flow| {
            if (flow.tombstone_until_ms < oldest_time) {
                oldest_time = flow.tombstone_until_ms;
                oldest_flow = flow;
            }
        }
        if (oldest_flow) |flow| {
            self.releaseOverflowBuf(flow);
            if (flow.socks_fd >= 0) {
                sys.close(flow.socks_fd);
                flow.socks_fd = -1;
            }
            flow.reset();
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
        self.releaseOverflowBuf(flow);
        if (flow.socks_fd >= 0) {
            sys.close(flow.socks_fd);
            flow.socks_fd = -1;
        }
        flow.state = .tombstone;
        flow.tombstone_until_ms = sys.monotonicMs() + duration_ms;
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

    pub fn recordDnsQuery(self: *UdpTable, src_ip: u32, dst_ip: u32, src_port: u16, dst_port: u16, dns_tx_id: u16) void {
        const slot = &self.dns_queries[self.dns_query_head];
        slot.src_ip = src_ip;
        slot.dst_ip = dst_ip;
        slot.src_port = src_port;
        slot.dst_port = dst_port;
        slot.dns_tx_id = dns_tx_id;
        slot.active = true;
        self.dns_query_head = (self.dns_query_head + 1) % DNS_QUERY_CAPACITY;
    }

    pub fn findDnsQuery(self: *UdpTable, dst_ip: u32, dst_port: u16, dns_tx_id: u16) ?DnsQuery {
        var i: usize = 0;
        while (i < DNS_QUERY_CAPACITY) : (i += 1) {
            const idx = (self.dns_query_head + DNS_QUERY_CAPACITY - 1 - i) % DNS_QUERY_CAPACITY;
            const q = &self.dns_queries[idx];
            if (q.active and q.dst_ip == dst_ip and q.dst_port == dst_port and q.dns_tx_id == dns_tx_id) {
                q.active = false; // Consumed
                return q.*;
            }
        }
        return null;
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
