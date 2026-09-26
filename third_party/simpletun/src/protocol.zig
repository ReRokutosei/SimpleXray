const std = @import("std");

pub const Ipv4Header = extern struct {
    ihl_version: u8,
    tos: u8,
    total_len: u16,
    id: u16,
    flags_fragment: u16,
    ttl: u8,
    protocol: u8,
    checksum: u16,
    src_ip: u32,
    dst_ip: u32,

    pub fn version(self: *const Ipv4Header) u4 {
        return @intCast(self.ihl_version >> 4);
    }

    pub fn ihl(self: *const Ipv4Header) u4 {
        return @intCast(self.ihl_version & 0x0f);
    }

    pub fn headerLen(self: *const Ipv4Header) usize {
        return @as(usize, self.ihl()) * 4;
    }

    pub fn getTotalLen(self: *const Ipv4Header) u16 {
        return std.mem.bigToNative(u16, self.total_len);
    }

    pub fn setTotalLen(self: *Ipv4Header, len: u16) void {
        self.total_len = std.mem.nativeToBig(u16, len);
    }
};

pub const TcpHeader = extern struct {
    src_port: u16,
    dst_port: u16,
    seq: u32,
    ack: u32,
    data_offset_reserved: u8,
    flags: u8,
    window: u16,
    checksum: u16,
    urgent_ptr: u16,

    pub const FLAG_FIN: u8 = 0x01;
    pub const FLAG_SYN: u8 = 0x02;
    pub const FLAG_RST: u8 = 0x04;
    pub const FLAG_PSH: u8 = 0x08;
    pub const FLAG_ACK: u8 = 0x10;
    pub const FLAG_URG: u8 = 0x20;

    pub fn dataOffset(self: *const TcpHeader) u4 {
        return @intCast(self.data_offset_reserved >> 4);
    }

    pub fn headerLen(self: *const TcpHeader) usize {
        return @as(usize, self.dataOffset()) * 4;
    }

    pub fn getSeq(self: *const TcpHeader) u32 {
        return std.mem.bigToNative(u32, self.seq);
    }

    pub fn getAck(self: *const TcpHeader) u32 {
        return std.mem.bigToNative(u32, self.ack);
    }

    pub fn getWindow(self: *const TcpHeader) u16 {
        return std.mem.bigToNative(u16, self.window);
    }

    pub fn setSeq(self: *TcpHeader, val: u32) void {
        self.seq = std.mem.nativeToBig(u32, val);
    }

    pub fn setAck(self: *TcpHeader, val: u32) void {
        self.ack = std.mem.nativeToBig(u32, val);
    }

    pub fn setWindow(self: *TcpHeader, val: u16) void {
        self.window = std.mem.nativeToBig(u16, val);
    }
};

pub fn calculateIpv4Checksum(header_bytes: []const u8) u16 {
    var sum: u32 = 0;
    var i: usize = 0;
    while (i + 1 < header_bytes.len) : (i += 2) {
        // Skip checksum field itself at byte offset 10..12
        if (i == 10) continue;
        const w = (@as(u32, header_bytes[i]) << 8) | @as(u32, header_bytes[i + 1]);
        sum += w;
    }
    if (i < header_bytes.len) {
        sum += @as(u32, header_bytes[i]) << 8;
    }
    while ((sum >> 16) != 0) {
        sum = (sum & 0xffff) + (sum >> 16);
    }
    return std.mem.nativeToBig(u16, ~@as(u16, @intCast(sum)));
}

pub fn calculateTcpChecksum(src_ip: u32, dst_ip: u32, tcp_len: u16, tcp_packet: []const u8) u16 {
    var sum: u32 = 0;

    // Pseudo-header:
    // src_ip (4B) + dst_ip (4B) + zero (1B) + protocol (1B=6) + tcp_len (2B)
    const src_bytes: *const [4]u8 = @ptrCast(&src_ip);
    const dst_bytes: *const [4]u8 = @ptrCast(&dst_ip);

    sum += (@as(u32, src_bytes[0]) << 8) | @as(u32, src_bytes[1]);
    sum += (@as(u32, src_bytes[2]) << 8) | @as(u32, src_bytes[3]);
    sum += (@as(u32, dst_bytes[0]) << 8) | @as(u32, dst_bytes[1]);
    sum += (@as(u32, dst_bytes[2]) << 8) | @as(u32, dst_bytes[3]);
    sum += 6; // IPPROTO_TCP
    sum += tcp_len;

    // TCP header and payload (checksum field at offset 16 is assumed 0 in prepared packet)
    var i: usize = 0;
    while (i + 1 < tcp_packet.len) : (i += 2) {
        const w = (@as(u32, tcp_packet[i]) << 8) | @as(u32, tcp_packet[i + 1]);
        sum += w;
    }
    if (i < tcp_packet.len) {
        sum += @as(u32, tcp_packet[i]) << 8;
    }

    while ((sum >> 16) != 0) {
        sum = (sum & 0xffff) + (sum >> 16);
    }
    const res = ~@as(u16, @intCast(sum));
    if (res == 0) return std.mem.nativeToBig(u16, 0xffff);
    return std.mem.nativeToBig(u16, res);
}

pub const UdpHeader = extern struct {
    src_port: u16,
    dst_port: u16,
    length: u16,
    checksum: u16,

    pub fn setLength(self: *UdpHeader, val: u16) void {
        self.length = std.mem.nativeToBig(u16, val);
    }
};

pub fn calculateUdpChecksum(src_ip: u32, dst_ip: u32, udp_len: u16, udp_packet: []const u8) u16 {
    var sum: u32 = 0;

    const src_bytes: *const [4]u8 = @ptrCast(&src_ip);
    const dst_bytes: *const [4]u8 = @ptrCast(&dst_ip);

    sum += (@as(u32, src_bytes[0]) << 8) | @as(u32, src_bytes[1]);
    sum += (@as(u32, src_bytes[2]) << 8) | @as(u32, src_bytes[3]);
    sum += (@as(u32, dst_bytes[0]) << 8) | @as(u32, dst_bytes[1]);
    sum += (@as(u32, dst_bytes[2]) << 8) | @as(u32, dst_bytes[3]);
    sum += 17; // IPPROTO_UDP
    sum += udp_len;

    // Checksum at byte offset 6 is assumed 0 in prepared packet
    var i: usize = 0;
    while (i + 1 < udp_packet.len) : (i += 2) {
        const w = (@as(u32, udp_packet[i]) << 8) | @as(u32, udp_packet[i + 1]);
        sum += w;
    }
    if (i < udp_packet.len) {
        sum += @as(u32, udp_packet[i]) << 8;
    }

    while ((sum >> 16) != 0) {
        sum = (sum & 0xffff) + (sum >> 16);
    }
    const res = ~@as(u16, @intCast(sum));
    if (res == 0) return std.mem.nativeToBig(u16, 0xffff);
    return std.mem.nativeToBig(u16, res);
}

test "IPv4 and TCP Checksum calculation" {
    // 20-byte standard IPv4 header
    var ip_hdr_bytes = [_]u8{
        0x45, 0x00, 0x00, 0x28, // IHL=5, Len=40
        0x12, 0x34, 0x40, 0x00, // ID=0x1234, Flags=DF
        0x40, 0x06, 0x00, 0x00, // TTL=64, TCP(6), Checksum=0
        192, 168, 1, 100, // Src
        10, 0, 0, 1, // Dst
    };

    const ip_csum = calculateIpv4Checksum(&ip_hdr_bytes);
    try std.testing.expect(ip_csum != 0);

    // Verify verifying: put calculated checksum in place and sum should fold to 0 / 0xffff
    ip_hdr_bytes[10] = @intCast((std.mem.bigToNative(u16, ip_csum) >> 8) & 0xff);
    ip_hdr_bytes[11] = @intCast(std.mem.bigToNative(u16, ip_csum) & 0xff);
    var verify_sum: u32 = 0;
    var i: usize = 0;
    while (i < ip_hdr_bytes.len) : (i += 2) {
        verify_sum += (@as(u32, ip_hdr_bytes[i]) << 8) | @as(u32, ip_hdr_bytes[i + 1]);
    }
    while ((verify_sum >> 16) != 0) {
        verify_sum = (verify_sum & 0xffff) + (verify_sum >> 16);
    }
    try std.testing.expectEqual(@as(u16, 0xffff), @as(u16, @intCast(verify_sum)));
}
