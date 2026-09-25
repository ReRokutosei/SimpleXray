const std = @import("std");
const sys = @import("sys.zig");

pub const State = enum(u8) {
    free = 0,
    upstream_connect,
    socks5_auth_wait,
    socks5_connect_wait,
    established,
    client_close_1,
    upstream_closed,
    tombstone,
};

pub const Flow = struct {
    // 4-Tuple identification
    src_ip: u32,
    dst_ip: u32,
    src_port: u16,
    dst_port: u16,

    // Sequence & Acknowledgment tracking
    rcv_nxt: u32,
    snd_nxt: u32,
    c_isn: u32,
    s_isn: u32,

    // SOCKS5 and I/O state
    socks_fd: i32,
    state: State,
    is_blocked: bool,
    tombstone_until_ms: i64,

    // Bounded overflow buffer for zero-window backpressure (max 1 MTU)
    overflow_len: u16,
    overflow_buf: [2048]u8,

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
        self.state = .free;
        self.is_blocked = false;
        self.tombstone_until_ms = 0;
        self.overflow_len = 0;
    }
};

pub const FlowTable = struct {
    pub const CAPACITY: usize = 512;
    flows: [CAPACITY]Flow,

    pub fn init() FlowTable {
        var table: FlowTable = undefined;
        for (&table.flows) |*flow| {
            flow.reset();
        }
        return table;
    }

    pub fn findFlow(self: *FlowTable, src_ip: u32, dst_ip: u32, src_port: u16, dst_port: u16) ?*Flow {
        for (&self.flows) |*flow| {
            if (flow.state != .free and flow.matches(src_ip, dst_ip, src_port, dst_port)) {
                return flow;
            }
        }
        return null;
    }

    pub fn findBySocksFd(self: *FlowTable, fd: i32) ?*Flow {
        if (fd < 0) return null;
        for (&self.flows) |*flow| {
            if (flow.state != .free and flow.socks_fd == fd) {
                return flow;
            }
        }
        return null;
    }

    pub fn allocate(self: *FlowTable, src_ip: u32, dst_ip: u32, src_port: u16, dst_port: u16) ?*Flow {
        // First try to find a completely free slot
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

        // Second pass: evict expired tombstones
        const now = sys.monotonicMs();
        for (&self.flows) |*flow| {
            if (flow.state == .tombstone and now >= flow.tombstone_until_ms) {
                flow.reset();
                flow.src_ip = src_ip;
                flow.dst_ip = dst_ip;
                flow.src_port = src_port;
                flow.dst_port = dst_port;
                flow.state = .upstream_connect;
                return flow;
            }
        }

        return null; // Table full
    }

    pub fn markTombstone(self: *FlowTable, flow: *Flow, duration_ms: i64) void {
        _ = self;
        if (flow.socks_fd >= 0) {
            sys.close(flow.socks_fd);
            flow.socks_fd = -1;
        }
        flow.state = .tombstone;
        flow.tombstone_until_ms = sys.monotonicMs() + duration_ms;
    }
};

pub const UdpSession = struct {
    src_ip: u32,
    dst_ip: u32,
    src_port: u16,
    dst_port: u16,
    last_active_ms: i64,
    active: bool,

    pub fn matches(self: *const UdpSession, s_ip: u32, d_ip: u32, s_port: u16, d_port: u16) bool {
        return self.active and
            self.src_ip == s_ip and
            self.dst_ip == d_ip and
            self.src_port == s_port and
            self.dst_port == d_port;
    }
};

pub const UdpTable = struct {
    pub const CAPACITY: usize = 256;
    sessions: [CAPACITY]UdpSession,

    pub fn init() UdpTable {
        var table: UdpTable = undefined;
        for (&table.sessions) |*s| {
            s.active = false;
        }
        return table;
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
        for (&self.sessions) |*s| {
            if (s.active and s.dst_ip == dst_ip and s.dst_port == dst_port) {
                return s;
            }
        }
        return null;
    }
};

test "FlowTable allocation and tombstone eviction" {
    var table = FlowTable.init();
    const flow1 = table.allocate(1, 2, 3, 4);
    try std.testing.expect(flow1 != null);
    try std.testing.expectEqual(State.upstream_connect, flow1.?.state);

    const lookup = table.findFlow(1, 2, 3, 4);
    try std.testing.expectEqual(flow1, lookup);

    table.markTombstone(flow1.?, -10); // already expired
    const flow2 = table.allocate(5, 6, 7, 8);
    try std.testing.expect(flow2 != null);
}
