package main

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"syscall"

	"github.com/metacubex/mipstack"
	"golang.org/x/sys/unix"
)

func runTunInbound(ctx context.Context, file *os.File, stack *mipstack.Stack, mtu int, stats *stats) {
	logInfo("runTunInbound: loop started")
	buf := make([]byte, 65535)

	for {
		select {
		case <-ctx.Done():
			logInfo("runTunInbound: ctx canceled, exiting")
			return
		default:
		}

		n, err := file.Read(buf)
		if err != nil {
			if errors.Is(err, os.ErrClosed) || errors.Is(err, io.EOF) {
				logInfo("runTunInbound: file closed, exiting")
				return
			}
			if errors.Is(err, syscall.EINTR) || errors.Is(err, syscall.EAGAIN) {
				continue
			}
			logError(fmt.Sprintf("runTunInbound: read error: %v", err))
			continue
		}
		if n <= 0 {
			continue
		}

		count, werr := stack.Write([][]byte{buf[:n]}, 0)
		if werr != nil {
			logError(fmt.Sprintf("runTunInbound: stack.Write error: %v", werr))
		}
		stats.txPackets.Add(uint64(count))
		stats.txBytes.Add(uint64(n))
	}
}

func runTunOutbound(ctx context.Context, file *os.File, stack *mipstack.Stack, mtu int, stats *stats) {
	logInfo("runTunOutbound: loop started")
	fd := int(file.Fd())
	const batchSize = 64
	buffers := make([][]byte, batchSize)
	for i := range buffers {
		buffers[i] = make([]byte, 65535)
	}
	sizes := make([]int, batchSize)

	for {
		select {
		case <-ctx.Done():
			logInfo("runTunOutbound: ctx canceled, exiting")
			return
		default:
		}

		count, err := stack.Read(buffers, sizes, 0)
		if err != nil {
			if errors.Is(err, os.ErrClosed) {
				logInfo("runTunOutbound: stack closed, exiting")
				return
			}
			continue
		}

		for i := 0; i < count; i++ {
			n := sizes[i]
			if n <= 0 {
				continue
			}
			_, werr := unix.Write(fd, buffers[i][:n])
			if werr != nil {
				if errors.Is(werr, syscall.EBADF) || errors.Is(werr, os.ErrClosed) {
					logInfo("runTunOutbound: file closed on write, exiting")
					return
				}
				if errors.Is(werr, syscall.EINTR) || errors.Is(werr, syscall.EAGAIN) {
					continue
				}
				logError(fmt.Sprintf("runTunOutbound: write error: %v", werr))
				continue
			}
			stats.rxPackets.Add(1)
			stats.rxBytes.Add(uint64(n))
		}
	}
}
