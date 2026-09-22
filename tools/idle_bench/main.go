package main

import (
	"bufio"
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"net"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"time"
)

const (
	payloadSize = 64
	concurrency = 32
)

type StepNotification struct {
	Step        int    `json:"step"`
	Established int    `json:"established"`
	Network     string `json:"network"`
	Status      string `json:"status"`
	Error       string `json:"error,omitempty"`
}

func main() {
	if len(os.Args) < 2 {
		fmt.Println("Usage: idle_bench <server|client> [options]")
		os.Exit(1)
	}

	subcmd := os.Args[1]
	switch subcmd {
	case "server":
		runServer(os.Args[2:])
	case "client":
		runClient(os.Args[2:])
	case "rtt":
		runRTT(os.Args[2:])
	default:
		fmt.Printf("Unknown subcommand: %s (expected 'server' or 'client')\n", subcmd)
		os.Exit(1)
	}
}

func runRTT(args []string) {
	fs := flag.NewFlagSet("rtt", flag.ExitOnError)
	server := fs.String("server", "127.0.0.1:5301", "Target echo server host:port")
	count := fs.Int("count", 15, "Number of RTT samples")
	interval := fs.Duration("interval", 200*time.Millisecond, "Delay between samples")
	timeout := fs.Duration("timeout", time.Second, "Per-sample I/O timeout")
	_ = fs.Parse(args)

	conn, err := net.DialTimeout("tcp", *server, *timeout)
	if err != nil {
		fmt.Fprintf(os.Stderr, "RTT connect failed: %v\n", err)
		os.Exit(1)
	}
	defer conn.Close()

	payload := make([]byte, payloadSize)
	response := make([]byte, payloadSize)
	for i := 0; i < *count; i++ {
		if err := conn.SetDeadline(time.Now().Add(*timeout)); err != nil {
			os.Exit(1)
		}
		started := time.Now()
		if _, err := conn.Write(payload); err != nil {
			fmt.Fprintf(os.Stderr, "RTT write failed: %v\n", err)
			os.Exit(1)
		}
		if _, err := io.ReadFull(conn, response); err != nil {
			fmt.Fprintf(os.Stderr, "RTT read failed: %v\n", err)
			os.Exit(1)
		}
		fmt.Printf("{\"rtt_ms\":%.3f}\n", float64(time.Since(started).Microseconds())/1000.0)
		if i+1 < *count {
			time.Sleep(*interval)
		}
	}
}

func runServer(args []string) {
	fs := flag.NewFlagSet("server", flag.ExitOnError)
	port := fs.Int("port", 5301, "Port to listen on for TCP and UDP echo")
	_ = fs.Parse(args)

	addr := fmt.Sprintf("0.0.0.0:%d", *port)
	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	var wg sync.WaitGroup
	var activeConns atomic.Int64

	// TCP Listener
	tcpListener, err := net.Listen("tcp", addr)
	if err != nil {
		fmt.Fprintf(os.Stderr, "[Server] Failed to listen TCP %s: %v\n", addr, err)
		os.Exit(1)
	}
	defer tcpListener.Close()
	fmt.Printf("[Server] TCP echo listening on %s\n", addr)

	wg.Add(1)
	go func() {
		defer wg.Done()
		go func() {
			<-ctx.Done()
			_ = tcpListener.Close()
		}()
		for {
			conn, err := tcpListener.Accept()
			if err != nil {
				return
			}
			activeConns.Add(1)
			go func(c net.Conn) {
				defer c.Close()
				defer activeConns.Add(-1)
				buf := make([]byte, payloadSize)
				for {
					_, err := io.ReadFull(c, buf)
					if err != nil {
						return
					}
					_, err = c.Write(buf)
					if err != nil {
						return
					}
				}
			}(conn)
		}
	}()

	// UDP Listener
	udpConn, err := net.ListenPacket("udp", addr)
	if err != nil {
		fmt.Fprintf(os.Stderr, "[Server] Failed to listen UDP %s: %v\n", addr, err)
		os.Exit(1)
	}
	defer udpConn.Close()
	fmt.Printf("[Server] UDP echo listening on %s\n", addr)

	wg.Add(1)
	go func() {
		defer wg.Done()
		go func() {
			<-ctx.Done()
			_ = udpConn.Close()
		}()
		buf := make([]byte, 1500)
		for {
			n, rAddr, err := udpConn.ReadFrom(buf)
			if err != nil {
				return
			}
			_, _ = udpConn.WriteTo(buf[:n], rAddr)
		}
	}()

	fmt.Println("[Server] Ready. Press Ctrl+C to stop.")
	<-ctx.Done()
	fmt.Println("[Server] Shutting down...")
	_ = tcpListener.Close()
	_ = udpConn.Close()
	wg.Wait()
	fmt.Println("[Server] Stopped.")
}

func runClient(args []string) {
	var rLimit syscall.Rlimit
	if err := syscall.Getrlimit(syscall.RLIMIT_NOFILE, &rLimit); err == nil {
		rLimit.Cur = 65536
		rLimit.Max = 65536
		_ = syscall.Setrlimit(syscall.RLIMIT_NOFILE, &rLimit)
	}

	fs := flag.NewFlagSet("client", flag.ExitOnError)
	server := fs.String("server", "127.0.0.1:5301", "Target echo server host:port")
	network := fs.String("network", "tcp", "Network type ('tcp' or 'udp')")
	stepsStr := fs.String("steps", "0,250,500,750,1000", "Comma-separated target connections at each step")
	settle := fs.Duration("settle", 1*time.Second, "Settle duration after establishing connections")
	autoAdvance := fs.Bool("auto", false, "Auto advance between steps without waiting for stdin newline")
	_ = fs.Parse(args)

	stepParts := strings.Split(*stepsStr, ",")
	var steps []int
	for _, p := range stepParts {
		p = strings.TrimSpace(p)
		if p == "" {
			continue
		}
		val, err := strconv.Atoi(p)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Invalid step: %s\n", p)
			os.Exit(1)
		}
		steps = append(steps, val)
	}

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	var connections []net.Conn
	defer func() {
		for _, c := range connections {
			_ = c.Close()
		}
	}()

	stdinScanner := bufio.NewScanner(os.Stdin)

	for _, targetCount := range steps {
		if ctx.Err() != nil {
			break
		}

		currentCount := len(connections)
		needed := targetCount - currentCount

		if needed > 0 {
			var newConns = make([]net.Conn, needed)
			var nextIdx atomic.Int64
			var dialWg sync.WaitGroup
			var dialErr atomic.Pointer[error]

			actualConcurrency := concurrency
			if *network == "udp" {
				actualConcurrency = 16
			}
			workerCount := min(actualConcurrency, needed)
			for w := 0; w < workerCount; w++ {
				dialWg.Add(1)
				go func() {
					defer dialWg.Done()
					for {
						idx := int(nextIdx.Add(1) - 1)
						if idx >= needed || ctx.Err() != nil {
							return
						}
						if *network == "udp" {
							time.Sleep(2 * time.Millisecond)
						}
						dialer := net.Dialer{Timeout: 4 * time.Second, KeepAlive: -1}
						conn, err := dialer.DialContext(ctx, *network, *server)
						if err != nil {
							dialErr.Store(&err)
							return
						}

						// Send initial 64-byte handshake payload with retry
						payload := make([]byte, payloadSize)
						copy(payload, fmt.Sprintf("idle-bench-%d", idx))

						retries := 3
						if *network == "udp" {
							retries = 5
						}
						var rerr error
						for retry := 0; retry < retries; retry++ {
							_ = conn.SetDeadline(time.Now().Add(1500 * time.Millisecond))
							_, werr := conn.Write(payload)
							if werr != nil {
								rerr = werr
								time.Sleep(10 * time.Millisecond)
								continue
							}
							echoBuf := make([]byte, payloadSize)
							_, rerr = io.ReadFull(conn, echoBuf)
							if rerr == nil {
								break
							}
							time.Sleep(20 * time.Millisecond)
						}

						if rerr != nil {
							if *network == "tcp" {
								_ = conn.Close()
								dialErr.Store(&rerr)
								return
							}
							// For UDP, retain socket to preserve file descriptor and proxy NAT entry
						}
						_ = conn.SetDeadline(time.Time{}) // Clear deadline to keep connection retained
						newConns[idx] = conn
					}
				}()
			}
			dialWg.Wait()

			if pErr := dialErr.Load(); pErr != nil {
				notify(StepNotification{
					Step: targetCount, Established: len(connections),
					Network: *network, Status: "error", Error: (*pErr).Error(),
				})
				os.Exit(1)
			}

			for _, nc := range newConns {
				if nc != nil {
					connections = append(connections, nc)
				}
			}
		}

		time.Sleep(*settle)

		notify(StepNotification{
			Step:        targetCount,
			Established: len(connections),
			Network:     *network,
			Status:      "settled",
		})

		// Wait for next step command or auto-advance
		if !*autoAdvance && targetCount != steps[len(steps)-1] {
			if stdinScanner.Scan() {
				// Advanced
			}
		}
	}

	fmt.Println(`{"status": "completed"}`)
}

func notify(notif StepNotification) {
	data, _ := json.Marshal(notif)
	fmt.Println(string(data))
	_ = os.Stdout.Sync()
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}
