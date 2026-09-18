package main

/*
#include <stdint.h>
*/
import "C"

import (
	"context"
	"fmt"
	"os"
	"sync"
	"sync/atomic"

	"github.com/metacubex/mipstack"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/protocol/socks"
	"golang.org/x/sys/unix"
)

type stats struct {
	txPackets atomic.Uint64
	txBytes   atomic.Uint64
	rxPackets atomic.Uint64
	rxBytes   atomic.Uint64
}

var (
	mu                 sync.Mutex
	running            atomic.Bool
	cancelFunc         context.CancelFunc
	activeStack        *mipstack.Stack
	activeTCPForwarder *mipstack.TCPForwarder
	activeUDPForwarder *mipstack.UDPForwarder
	activeTunFile      *os.File
	globalStats        stats
)

//export mipsTunStart
func mipsTunStart(
	tunFd C.int,
	socksHost *C.char,
	socksPort C.int,
	mtu C.int,
	username *C.char,
	password *C.char,
) C.int {
	mu.Lock()
	defer mu.Unlock()

	if running.Load() {
		logWarn("mipsTunStart: already running")
		return 0
	}

	host := C.GoString(socksHost)
	port := uint16(socksPort)
	user := C.GoString(username)
	pass := C.GoString(password)

	effectiveMtu := uint32(mtu)
	if effectiveMtu == 0 {
		effectiveMtu = 1500
	}

	logInfo(fmt.Sprintf("mipsTunStart: tunFd=%d, socks=%s:%d, mtu=%d", int(tunFd), host, port, effectiveMtu))

	dupFd, err := unix.Dup(int(tunFd))
	if err != nil {
		logError(fmt.Sprintf("mipsTunStart: unix.Dup failed: %v", err))
		return -1
	}

	tunFile := os.NewFile(uintptr(dupFd), "tun")

	ctx, cancel := context.WithCancel(context.Background())

	socksServerAddr := M.ParseSocksaddrHostPort(host, port)
	socksClient := socks.NewClient(N.SystemDialer, socksServerAddr, socks.Version5, user, pass)

	config := mipstack.Config{
		Promiscuous: true,
		MTU:         effectiveMtu,
		TCP: mipstack.TCPSocketDefaults{
			CongestionControl: mipstack.CongestionControlBBR3,
		},
	}

	stack, err := mipstack.New(config)
	if err != nil {
		logError(fmt.Sprintf("mipsTunStart: mipstack.New failed: %v", err))
		_ = tunFile.Close()
		cancel()
		return -2
	}

	tcpForwarder, err := mipstack.NewTCPForwarder(stack, mipstack.TCPForwarderOptions{}, func(request *mipstack.TCPForwarderRequest) {
		dest := request.Flow().Destination
		conn, acceptErr := request.Accept(context.Background())
		if acceptErr != nil {
			logError(fmt.Sprintf("tcpForwarder: Accept failed for %s: %v", dest, acceptErr))
			return
		}
		go handleTCP(ctx, socksClient, conn, dest)
	})
	if err != nil {
		logError(fmt.Sprintf("mipsTunStart: NewTCPForwarder failed: %v", err))
		_ = stack.Close()
		_ = tunFile.Close()
		cancel()
		return -3
	}

	udpForwarder, err := mipstack.NewUDPForwarder(stack, mipstack.UDPForwarderOptions{}, func(request *mipstack.UDPForwarderRequest) {
		dest := request.Flow().Destination
		conn, acceptErr := request.Accept()
		if acceptErr != nil {
			logError(fmt.Sprintf("udpForwarder: Accept failed for %s: %v", dest, acceptErr))
			return
		}
		go handleUDP(ctx, socksClient, conn, dest)
	})
	if err != nil {
		logError(fmt.Sprintf("mipsTunStart: NewUDPForwarder failed: %v", err))
		_ = tcpForwarder.Close()
		_ = stack.Close()
		_ = tunFile.Close()
		cancel()
		return -4
	}

	if err = stack.Start(); err != nil {
		logError(fmt.Sprintf("mipsTunStart: stack.Start failed: %v", err))
		_ = udpForwarder.Close()
		_ = tcpForwarder.Close()
		_ = stack.Close()
		_ = tunFile.Close()
		cancel()
		return -5
	}

	cancelFunc = cancel
	activeStack = stack
	activeTCPForwarder = tcpForwarder
	activeUDPForwarder = udpForwarder
	activeTunFile = tunFile
	running.Store(true)

	go runTunInbound(ctx, tunFile, stack, int(effectiveMtu), &globalStats)
	go runTunOutbound(ctx, tunFile, stack, int(effectiveMtu), &globalStats)

	logInfo("mipsTunStart: successfully started")
	return 0
}

//export mipsTunStop
func mipsTunStop() C.int {
	mu.Lock()
	defer mu.Unlock()

	if !running.Load() {
		return 0
	}

	logInfo("mipsTunStop: stopping...")

	if cancelFunc != nil {
		cancelFunc()
		cancelFunc = nil
	}

	if activeTunFile != nil {
		_ = activeTunFile.Close()
		activeTunFile = nil
	}

	if activeUDPForwarder != nil {
		_ = activeUDPForwarder.Close()
		activeUDPForwarder = nil
	}

	if activeTCPForwarder != nil {
		_ = activeTCPForwarder.Close()
		activeTCPForwarder = nil
	}

	if activeStack != nil {
		_ = activeStack.Close()
		activeStack = nil
	}

	running.Store(false)
	logInfo("mipsTunStop: stopped successfully")
	return 0
}

//export mipsTunIsRunning
func mipsTunIsRunning() C.int {
	if running.Load() {
		return 1
	}
	return 0
}

//export mipsTunGetStats
func mipsTunGetStats(txPkts *C.uint64_t, txBytes *C.uint64_t, rxPkts *C.uint64_t, rxBytes *C.uint64_t) {
	if txPkts != nil {
		*txPkts = C.uint64_t(globalStats.txPackets.Load())
	}
	if txBytes != nil {
		*txBytes = C.uint64_t(globalStats.txBytes.Load())
	}
	if rxPkts != nil {
		*rxPkts = C.uint64_t(globalStats.rxPackets.Load())
	}
	if rxBytes != nil {
		*rxBytes = C.uint64_t(globalStats.rxBytes.Load())
	}
}

func main() {}
