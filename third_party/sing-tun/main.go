package main

/*
#include <stdint.h>
*/
import "C"

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net/netip"
	"os"
	"os/signal"
	"runtime"
	"runtime/debug"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	tun "github.com/sagernet/sing-tun"
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
	mu          sync.Mutex
	running     atomic.Bool
	cancelFunc  context.CancelFunc
	activeTun   tun.Tun
	activeStack tun.Stack
	globalStats stats
)

//export singTunStart
func singTunStart(
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
		return 0
	}

	runtime.GOMAXPROCS(4)
	debug.SetGCPercent(100)

	host := C.GoString(socksHost)
	port := uint16(socksPort)
	user := C.GoString(username)
	pass := C.GoString(password)

	effectiveMtu := uint32(mtu)
	if effectiveMtu == 0 {
		effectiveMtu = 1500
	}

	ctx, cancel := context.WithCancel(context.Background())

	socksServerAddr := M.ParseSocksaddrHostPort(host, port)
	socksClient := socks.NewClient(N.SystemDialer, socksServerAddr, socks.Version5, user, pass)

	dupFd, err := unix.Dup(int(tunFd))
	if err != nil {
		logError(fmt.Sprintf("singTunStart: unix.Dup failed: %v", err))
		cancel()
		return -1
	}

	tunOptions := tun.Options{
		FileDescriptor:            dupFd,
		MTU:                       effectiveMtu,
		AutoRoute:                 false,
		StrictRoute:               false,
		EXP_ExternalConfiguration: true,
		Inet4Address:              []netip.Prefix{netip.MustParsePrefix("198.18.0.1/30")},
		Inet6Address:              []netip.Prefix{netip.MustParsePrefix("fc00::1/126")},
	}

	tunDev, err := tun.New(tunOptions)
	if err != nil {
		logError(fmt.Sprintf("singTunStart: tun.New failed: %v", err))
		_ = unix.Close(dupFd)
		cancel()
		return -1
	}

	tunName, _ := tunDev.Name()
	if tunName == "" {
		tunName = "tun0"
	}
	tunOptions.Name = tunName

	h := newSingTunHandler(socksClient, &globalStats)

	stackOptions := tun.StackOptions{
		Context:                ctx,
		Tun:                    tunDev,
		TunOptions:             tunOptions,
		UDPTimeout:             30 * time.Second,
		ICMPTimeout:            10 * time.Second,
		Handler:     h,
		Logger:      &singTunLogger{},
	}

	stack, err := tun.NewStack("", stackOptions)
	if err != nil {
		logError(fmt.Sprintf("singTunStart: tun.NewStack failed: %v", err))
		_ = tunDev.Close()
		cancel()
		return -2
	}

	if err = stack.Start(); err != nil {
		logError(fmt.Sprintf("singTunStart: stack.Start failed: %v", err))
		_ = stack.Close()
		_ = tunDev.Close()
		cancel()
		return -3
	}

	if err = tunDev.Start(); err != nil {
		logError(fmt.Sprintf("singTunStart: tunDev.Start failed: %v", err))
		_ = stack.Close()
		_ = tunDev.Close()
		cancel()
		return -4
	}

	logInfo(fmt.Sprintf("singTunStart: started successfully on fd=%d (dup=%d), socks=%s:%d, mtu=%d", int(tunFd), dupFd, host, port, effectiveMtu))

	cancelFunc = cancel
	activeTun = tunDev
	activeStack = stack
	running.Store(true)

	return 0
}

//export singTunStop
func singTunStop() C.int {
	mu.Lock()
	defer mu.Unlock()

	if !running.Load() {
		return 0
	}

	if cancelFunc != nil {
		cancelFunc()
		cancelFunc = nil
	}

	if activeStack != nil {
		_ = activeStack.Close()
		activeStack = nil
	}

	if activeTun != nil {
		_ = activeTun.Close()
		activeTun = nil
	}

	running.Store(false)
	debug.FreeOSMemory()
	return 0
}

//export singTunIsRunning
func singTunIsRunning() C.int {
	if running.Load() {
		return 1
	}
	return 0
}

//export singTunGetStats
func singTunGetStats(txPkts *C.uint64_t, txBytes *C.uint64_t, rxPkts *C.uint64_t, rxBytes *C.uint64_t) {
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

func runCLI() {
	tunName := flag.String("tun", "tun0", "TUN interface name")
	socksHost := flag.String("socks-host", "127.0.0.1", "SOCKS5 server host")
	socksPort := flag.Int("socks-port", 10800, "SOCKS5 server port")
	mtu := flag.Int("mtu", 1500, "MTU")
	flag.Parse()

	tunDev, err := tun.New(tun.Options{
		Name:                      *tunName,
		MTU:                       uint32(*mtu),
		AutoRoute:                 false,
		StrictRoute:               false,
		EXP_ExternalConfiguration: true,
		Inet4Address:              []netip.Prefix{netip.MustParsePrefix("198.18.0.1/30")},
		Inet6Address:              []netip.Prefix{netip.MustParsePrefix("fc00::1/126")},
	})
	if err != nil {
		log.Fatalf("tun.New error: %v", err)
	}
	defer tunDev.Close()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	socksServerAddr := M.ParseSocksaddrHostPort(*socksHost, uint16(*socksPort))
	socksClient := socks.NewClient(N.SystemDialer, socksServerAddr, socks.Version5, "", "")

	h := newSingTunHandler(socksClient, &globalStats)

	stack, err := tun.NewStack("", tun.StackOptions{
		Context:     ctx,
		Tun:         tunDev,
		TunOptions:  tun.Options{MTU: uint32(*mtu), Inet4Address: []netip.Prefix{netip.MustParsePrefix("198.18.0.1/30")}},
		UDPTimeout:  30 * time.Second,
		ICMPTimeout: 10 * time.Second,
		Handler:     h,
	})
	if err != nil {
		log.Fatalf("tun.NewStack error: %v", err)
	}
	defer stack.Close()

	if err := stack.Start(); err != nil {
		log.Fatalf("stack.Start error: %v", err)
	}
	if err := tunDev.Start(); err != nil {
		log.Fatalf("tunDev.Start error: %v", err)
	}

	fmt.Printf("SingTUN running on %s -> %s:%d\n", *tunName, *socksHost, *socksPort)

	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	<-sigChan
	fmt.Println("SingTUN stopping...")
}

func main() {
	if len(os.Args) > 1 {
		runCLI()
	}
}
