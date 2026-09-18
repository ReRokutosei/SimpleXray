package main

import (
	"context"
	"fmt"
	"net"
	"net/netip"
	"sync"
	"sync/atomic"
	"time"

	"github.com/sagernet/sing/common/bufio"
	M "github.com/sagernet/sing/common/metadata"
	"github.com/sagernet/sing/protocol/socks"
)

const (
	udpIdleTimeout = 30 * time.Second
)

func handleTCP(ctx context.Context, client *socks.Client, conn net.Conn, destination netip.AddrPort) {
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

	var done atomic.Bool
	var wg sync.WaitGroup
	wg.Add(2)

	// Upload: conn (from phone) -> upstream (to Xray SOCKS5)
	go func() {
		defer wg.Done()
		n, err := bufio.Copy(upstream, conn)
		if err != nil {
			logDebug(fmt.Sprintf("handleTCP: upload copy ended for %s: %v (bytes=%d)", destStr, err, n))
		}
		if !done.Swap(true) {
			_ = conn.Close()
			_ = upstream.Close()
		}
	}()

	// Download: upstream (from Xray SOCKS5) -> conn (to phone)
	go func() {
		defer wg.Done()
		n, err := bufio.Copy(conn, upstream)
		if err != nil {
			logDebug(fmt.Sprintf("handleTCP: download copy ended for %s: %v (bytes=%d)", destStr, err, n))
		}
		if !done.Swap(true) {
			_ = conn.Close()
			_ = upstream.Close()
		}
	}()

	wg.Wait()
	logDebug(fmt.Sprintf("handleTCP: closed connection to %s", destStr))
}

func handleUDP(ctx context.Context, client *socks.Client, conn net.Conn, destination netip.AddrPort) {
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

	var done atomic.Bool
	var wg sync.WaitGroup
	wg.Add(2)

	targetUDPAddr := socksAddr.UDPAddr()

	// Upload: conn (from phone) -> remotePacketConn (to Xray SOCKS5 UDP)
	go func() {
		defer wg.Done()
		buf := make([]byte, 65535)
		for {
			_ = conn.SetReadDeadline(time.Now().Add(udpIdleTimeout))
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
			}
		}
		if !done.Swap(true) {
			_ = conn.Close()
			_ = remotePacketConn.Close()
		}
	}()

	// Download: remotePacketConn (from Xray SOCKS5 UDP) -> conn (to phone)
	go func() {
		defer wg.Done()
		buf := make([]byte, 65535)
		for {
			_ = remotePacketConn.SetReadDeadline(time.Now().Add(udpIdleTimeout))
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
			}
		}
		if !done.Swap(true) {
			_ = conn.Close()
			_ = remotePacketConn.Close()
		}
	}()

	wg.Wait()
	logDebug(fmt.Sprintf("handleUDP: closed flow to %s", destStr))
}
