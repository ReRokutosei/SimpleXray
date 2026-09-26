const std = @import("std");

pub const Greeting = [_]u8{ 0x05, 0x01, 0x00 };

pub const HandshakePhase = enum(u8) {
    idle = 0,
    greeting_tx,
    auth_rx,
    request_tx,
    reply_rx,
};

pub const Handshake = struct {
    phase: HandshakePhase = .idle,
    have: u8 = 0,
    buf: [22]u8 = [_]u8{0} ** 22,

    pub fn beginGreeting(self: *Handshake) void {
        self.phase = .greeting_tx;
        self.have = 0;
        @memcpy(self.buf[0..Greeting.len], &Greeting);
    }

    pub fn beginAuth(self: *Handshake) void {
        self.phase = .auth_rx;
        self.have = 0;
    }

    pub fn beginConnectRequest(self: *Handshake, dst_ip: u32, dst_port: u16) void {
        self.phase = .request_tx;
        self.have = 0;
        _ = formatConnectRequest(self.buf[0..10], dst_ip, dst_port);
    }

    pub fn beginReply(self: *Handshake) void {
        self.phase = .reply_rx;
        self.have = 0;
    }

    pub fn expected(self: *const Handshake) usize {
        return switch (self.phase) {
            .idle => 0,
            .greeting_tx => Greeting.len,
            .auth_rx => 2,
            .request_tx => 10,
            .reply_rx => blk: {
                if (self.have < 4) break :blk 4;
                break :blk switch (self.buf[3]) {
                    0x01 => 10,
                    0x04 => 22,
                    else => 4,
                };
            },
        };
    }

    pub fn complete(self: *const Handshake) bool {
        return self.have == self.expected();
    }

    pub fn remaining(self: *const Handshake) usize {
        const want = self.expected();
        return if (self.have >= want) 0 else want - self.have;
    }

    pub fn outgoing(self: *const Handshake) []const u8 {
        return self.buf[0..self.expected()];
    }

    pub fn commitSent(self: *Handshake, n: usize) void {
        self.have += @intCast(n);
    }

    pub fn consume(self: *Handshake, byte: u8) void {
        if (self.have < self.buf.len) {
            self.buf[self.have] = byte;
            self.have += 1;
        }
    }

    pub fn incoming(self: *const Handshake) []const u8 {
        return self.buf[0..self.have];
    }

    pub fn reset(self: *Handshake) void {
        self.phase = .idle;
        self.have = 0;
    }
};

pub fn formatConnectRequest(buf: []u8, dst_ip: u32, dst_port: u16) usize {
    std.debug.assert(buf.len >= 10);
    buf[0] = 0x05; // SOCKS version 5
    buf[1] = 0x01; // CMD: CONNECT
    buf[2] = 0x00; // RSV
    buf[3] = 0x01; // ATYP: IPv4

    // dst_ip is in network byte order in raw packets, copy directly
    const ip_bytes: *const [4]u8 = @ptrCast(&dst_ip);
    @memcpy(buf[4..8], ip_bytes);

    // dst_port is in network byte order in raw packets, copy directly
    const port_bytes: *const [2]u8 = @ptrCast(&dst_port);
    @memcpy(buf[8..10], port_bytes);

    return 10;
}

pub const UdpAssociateReq = [_]u8{ 0x05, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };

pub fn formatUdpAssociateRequest(buf: *[10]u8) usize {
    @memcpy(buf[0..10], &UdpAssociateReq);
    return 10;
}

pub fn formatUdpHeader(buf: *[10]u8, dst_ip: u32, dst_port: u16) usize {
    buf[0] = 0x00; // RSV (2B)
    buf[1] = 0x00;
    buf[2] = 0x00; // FRAG (0x00 = standalone packet)
    buf[3] = 0x01; // ATYP: IPv4

    const ip_bytes: *const [4]u8 = @ptrCast(&dst_ip);
    @memcpy(buf[4..8], ip_bytes);

    const port_bytes: *const [2]u8 = @ptrCast(&dst_port);
    @memcpy(buf[8..10], port_bytes);

    return 10;
}

test "SOCKS5 formatConnectRequest and UdpAssociateRequest" {
    var buf: [10]u8 = undefined;
    const ip = std.mem.nativeToBig(u32, 0x7f000001); // 127.0.0.1
    const port = std.mem.nativeToBig(u16, 8080);
    const len = formatConnectRequest(buf[0..], ip, port);
    try std.testing.expectEqual(@as(usize, 10), len);
    try std.testing.expectEqual(@as(u8, 0x05), buf[0]);
    try std.testing.expectEqual(@as(u8, 0x01), buf[1]);
    try std.testing.expectEqual(@as(u8, 0x01), buf[3]);

    const assoc_len = formatUdpAssociateRequest(&buf);
    try std.testing.expectEqual(@as(usize, 10), assoc_len);
    try std.testing.expectEqual(@as(u8, 0x03), buf[1]);
}

test "SOCKS5 handshake parser handles split responses" {
    var hs = Handshake{};
    hs.beginGreeting();
    try std.testing.expect(!hs.complete());
    try std.testing.expectEqualSlices(u8, &Greeting, hs.outgoing());
    hs.commitSent(Greeting.len);
    try std.testing.expect(hs.complete());

    hs.beginAuth();
    try std.testing.expectEqual(@as(usize, 2), hs.remaining());
    hs.consume(0x05);
    try std.testing.expect(!hs.complete());
    hs.consume(0x00);
    try std.testing.expect(hs.complete());
    try std.testing.expectEqualSlices(u8, &[_]u8{ 0x05, 0x00 }, hs.incoming());

    const ip = std.mem.nativeToBig(u32, 0x08080808);
    const port = std.mem.nativeToBig(u16, 443);
    hs.beginConnectRequest(ip, port);
    try std.testing.expect(!hs.complete());
    hs.commitSent(4);
    try std.testing.expect(!hs.complete());
    hs.commitSent(6);
    try std.testing.expect(hs.complete());

    hs.beginReply();
    hs.consume(0x05);
    hs.consume(0x00);
    hs.consume(0x00);
    hs.consume(0x01);
    try std.testing.expect(!hs.complete());
    for (0..6) |_| hs.consume(0x00);
    try std.testing.expect(hs.complete());
    try std.testing.expectEqual(@as(usize, 10), hs.incoming().len);

    hs.beginReply();
    hs.consume(0x05);
    hs.consume(0x00);
    hs.consume(0x00);
    hs.consume(0x04);
    try std.testing.expect(!hs.complete());
    for (0..18) |_| hs.consume(0x00);
    try std.testing.expect(hs.complete());
    try std.testing.expectEqual(@as(usize, 22), hs.incoming().len);
}

test "SOCKS5 handshake parser rejects unsupported reply ATYP" {
    var hs = Handshake{};
    hs.beginReply();
    hs.consume(0x05);
    hs.consume(0x00);
    hs.consume(0x00);
    hs.consume(0x03);
    try std.testing.expect(hs.complete());
    try std.testing.expectEqual(@as(usize, 4), hs.incoming().len);
}
