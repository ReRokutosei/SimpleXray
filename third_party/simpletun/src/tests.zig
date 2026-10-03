// Test aggregator: Zig only discovers tests from the build-test root module.
// Import every module that carries unit tests so `zig build test` runs them.
test {
    _ = @import("c_api.zig");
    _ = @import("engine.zig");
    _ = @import("flow.zig");
    _ = @import("main.zig");
    _ = @import("protocol.zig");
    _ = @import("socks5.zig");
    _ = @import("sys.zig");
    _ = @import("udp_integration_test.zig");
}
