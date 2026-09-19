package main

import (
	"context"
	"fmt"
	"net"
	"net/netip"
	"sync"
	"time"

	"github.com/sagernet/sing/common/bufio"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/protocol/socks"
)

const (
	udpIdleTimeout    = 30 * time.Second
	udpDnsIdleTimeout = 5 * time.Second
)

var udpBufPool = sync.Pool{
	New: func() any {
		b := make([]byte, 65535)
		return &b
	},
}

func handleTCP(ctx context.Context, client *socks.Client, conn net.Conn, destination netip.AddrPort, stats *stats) {
	defer conn.Close()

	destStr := destination.String()
	logDebug(fmt.Sprintf("handleTCP: starting connection to %s", destStr))

	socksAddr := M.SocksaddrFromNetIP(destination)
	upstream, err := client.DialContext(ctx, "tcp", socksAddr)
	if err != nil {
		logError(fmt.Sprintf("handleTCP: DialContext failed for %s: %v", destStr, err))
		return
	}
	defer upstream.Close()

	logDebug(fmt.Sprintf("handleTCP: connected to %s via SOCKS5", destStr))

	var wg sync.WaitGroup
	wg.Add(2)

	// Upload: conn (from phone) -> upstream (to Xray SOCKS5)
	go func() {
		defer wg.Done()
		n, err := bufio.Copy(upstream, conn)
		if n > 0 && stats != nil {
			stats.txBytes.Add(uint64(n))
		}
		if err == nil {
			_ = N.CloseWrite(upstream)
		} else {
			_ = upstream.Close()
		}
	}()

	// Download: upstream (from Xray SOCKS5) -> conn (to phone)
	go func() {
		defer wg.Done()
		n, err := bufio.Copy(conn, upstream)
		if n > 0 && stats != nil {
			stats.rxBytes.Add(uint64(n))
		}
		if err == nil {
			_ = N.CloseWrite(conn)
		} else {
			_ = conn.Close()
		}
	}()

	// Monitor context cancellation
	stopDone := make(chan struct{})
	go func() {
		select {
		case <-ctx.Done():
			_ = conn.Close()
			_ = upstream.Close()
		case <-stopDone:
		}
	}()

	wg.Wait()
	close(stopDone)
	logDebug(fmt.Sprintf("handleTCP: closed connection to %s", destStr))
}

func handleUDP(ctx context.Context, client *socks.Client, conn net.Conn, destination netip.AddrPort, stats *stats) {
	defer conn.Close()

	destStr := destination.String()
	logDebug(fmt.Sprintf("handleUDP: starting flow to %s", destStr))

	socksAddr := M.SocksaddrFromNetIP(destination)
	remotePacketConn, err := client.ListenPacket(ctx, M.Socksaddr{})
	if err != nil {
		logError(fmt.Sprintf("handleUDP: ListenPacket failed for %s: %v", destStr, err))
		return
	}
	defer remotePacketConn.Close()

	// Short timeout for ephemeral DNS lookups to avoid connection/memory bloat
	idleTimeout := udpIdleTimeout
	if destination.Port() == 53 {
		idleTimeout = udpDnsIdleTimeout
	}

	var wg sync.WaitGroup
	wg.Add(2)

	targetUDPAddr := socksAddr.UDPAddr()

	// Upload: conn (from phone) -> remotePacketConn (to Xray SOCKS5 UDP)
	go func() {
		defer wg.Done()
		bufPtr := udpBufPool.Get().(*[]byte)
		defer udpBufPool.Put(bufPtr)
		buf := *bufPtr

		for {
			_ = conn.SetReadDeadline(time.Now().Add(idleTimeout))
			n, rerr := conn.Read(buf)
			if rerr != nil {
				break
			}
			if n > 0 {
				_, werr := remotePacketConn.WriteTo(buf[:n], targetUDPAddr)
				if werr != nil {
					logError(fmt.Sprintf("handleUDP: WriteTo failed for %s: %v", destStr, werr))
					break
				}
				if stats != nil {
					stats.txBytes.Add(uint64(n))
					stats.txPackets.Add(1)
				}
			}
		}
		_ = conn.Close()
		_ = remotePacketConn.Close()
	}()

	// Download: remotePacketConn (from Xray SOCKS5 UDP) -> conn (to phone)
	go func() {
		defer wg.Done()
		bufPtr := udpBufPool.Get().(*[]byte)
		defer udpBufPool.Put(bufPtr)
		buf := *bufPtr

		for {
			_ = remotePacketConn.SetReadDeadline(time.Now().Add(idleTimeout))
			n, _, rerr := remotePacketConn.ReadFrom(buf)
			if rerr != nil {
				break
			}
			if n > 0 {
				_, werr := conn.Write(buf[:n])
				if werr != nil {
					logError(fmt.Sprintf("handleUDP: write back to client failed for %s: %v", destStr, werr))
					break
				}
				if stats != nil {
					stats.rxBytes.Add(uint64(n))
					stats.rxPackets.Add(1)
				}
			}
		}
		_ = conn.Close()
		_ = remotePacketConn.Close()
	}()

	// Monitor context cancellation
	stopDone := make(chan struct{})
	go func() {
		select {
		case <-ctx.Done():
			_ = conn.Close()
			_ = remotePacketConn.Close()
		case <-stopDone:
		}
	}()

	wg.Wait()
	close(stopDone)
	logDebug(fmt.Sprintf("handleUDP: closed flow to %s", destStr))
}

