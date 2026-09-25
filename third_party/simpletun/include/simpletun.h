#ifndef SIMPLETUN_H
#define SIMPLETUN_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Starts SimpleTUN on the given TUN file descriptor and blocks the calling thread
 * until simpletun_stop() is called.
 * 
 * @param tun_fd File descriptor of the Android VPN tun device.
 * @param socks_ip IPv4 address of local SOCKS5 inbound (native or big-endian).
 * @param socks_port Port of local SOCKS5 inbound.
 * @return 0 on normal stop, non-zero on failure.
 */
int simpletun_start(int tun_fd, uint32_t socks_ip, uint16_t socks_port);

/**
 * Signals the running SimpleTUN engine to break its event loop and terminate cleanly.
 * 
 * @return 0 on success, -1 if no engine is currently running.
 */
int simpletun_stop(void);

/**
 * Returns the version string of SimpleTUN.
 */
const char *simpletun_version(void);

#ifdef __cplusplus
}
#endif

#endif // SIMPLETUN_H
