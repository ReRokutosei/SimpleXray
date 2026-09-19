package main

import (
	"context"
	"net"
	"net/netip"
	"sync"
	"sync/atomic"

	tun "github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common/bufio"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/protocol/socks"
)

type singTunHandler struct {
	client *socks.Client
	stats  *stats
}

func newSingTunHandler(client *socks.Client, stats *stats) *singTunHandler {
	return &singTunHandler{
		client: client,
		stats:  stats,
	}
}

func (h *singTunHandler) JudgeFlow(network uint8, source netip.AddrPort, destination netip.AddrPort, firstPacket []byte) tun.FlowVerdict {
	logInfo("JudgeFlow called: net=" + string('0'+network) + " src=" + source.String() + " dst=" + destination.String())
	return tun.FlowVerdict{Action: tun.ActionAccept}
}

func (h *singTunHandler) NewDNSPacket(payload []byte, source M.Socksaddr, destination M.Socksaddr, writer N.PacketWriter) {
	// DNS packets are processed by NewPacketConnectionEx under ActionAccept.
}

func (h *singTunHandler) NewConnectionEx(ctx context.Context, conn net.Conn, source M.Socksaddr, destination M.Socksaddr, onClose N.CloseHandlerFunc) {
	logInfo("NewConnectionEx called: src=" + source.String() + " dst=" + destination.String())
	go func() {
		defer func() {
			if onClose != nil {
				onClose(nil)
			}
		}()

		upstream, err := h.client.DialContext(ctx, "tcp", destination)
		if err != nil {
			logError("NewConnectionEx: DialContext failed: " + err.Error())
			conn.Close()
			return
		}
		defer upstream.Close()
		defer conn.Close()

		h.stats.txPackets.Add(1)

		var wg sync.WaitGroup
		wg.Add(2)

		// Upload: conn -> upstream
		go func() {
			defer wg.Done()
			n, err := bufio.Copy(upstream, conn)
			if n > 0 && h.stats != nil {
				h.stats.txBytes.Add(uint64(n))
			}
			if err == nil {
				_ = N.CloseWrite(upstream)
			} else {
				_ = upstream.Close()
			}
		}()

		// Download: upstream -> conn
		go func() {
			defer wg.Done()
			n, err := bufio.Copy(conn, upstream)
			if n > 0 && h.stats != nil {
				h.stats.rxBytes.Add(uint64(n))
			}
			if err == nil {
				_ = N.CloseWrite(conn)
			} else {
				_ = conn.Close()
			}
		}()

		wg.Wait()
	}()
}

func (h *singTunHandler) NewPacketConnectionEx(ctx context.Context, conn N.PacketConn, source M.Socksaddr, destination M.Socksaddr, onClose N.CloseHandlerFunc) {
	logInfo("NewPacketConnectionEx called: src=" + source.String() + " dst=" + destination.String())
	go func() {
		defer func() {
			if onClose != nil {
				onClose(nil)
			}
		}()

		remotePacketConn, err := h.client.ListenPacket(ctx, M.Socksaddr{})
		if err != nil {
			conn.Close()
			return
		}
		defer remotePacketConn.Close()
		defer conn.Close()

		h.stats.txPackets.Add(1)

		reader, okR := remotePacketConn.(N.PacketReader)
		writer, okW := remotePacketConn.(N.PacketWriter)
		if !okR || !okW {
			// Fallback: wrap standard net.PacketConn if not implementing N.PacketConn
			wrapped := bufio.NewPacketConn(remotePacketConn)
			reader = wrapped
			writer = wrapped
		}

		var done atomic.Bool
		var wg sync.WaitGroup
		wg.Add(2)

		// Upload: conn -> writer
		go func() {
			defer wg.Done()
			n, _ := bufio.CopyPacket(writer, conn)
			if n > 0 {
				h.stats.txBytes.Add(uint64(n))
			}
			if !done.Swap(true) {
				conn.Close()
				remotePacketConn.Close()
			}
		}()

		// Download: reader -> conn
		go func() {
			defer wg.Done()
			n, _ := bufio.CopyPacket(conn, reader)
			if n > 0 {
				h.stats.rxBytes.Add(uint64(n))
			}
			if !done.Swap(true) {
				conn.Close()
				remotePacketConn.Close()
			}
		}()

		wg.Wait()
	}()
}
