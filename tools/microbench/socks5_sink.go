package main

import (
	"encoding/binary"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"os/signal"
	"syscall"
)

func handleTCP(conn net.Conn) {
	defer conn.Close()

	// 1. Negotiation
	buf := make([]byte, 258)
	n, err := io.ReadFull(conn, buf[:2])
	if err != nil || n < 2 || buf[0] != 0x05 {
		return
	}
	nmethods := int(buf[1])
	n, err = io.ReadFull(conn, buf[:nmethods])
	if err != nil || n < nmethods {
		return
	}
	// Reply: version 5, method 0 (no auth)
	if _, err := conn.Write([]byte{0x05, 0x00}); err != nil {
		return
	}

	// 2. Request
	n, err = io.ReadFull(conn, buf[:4])
	if err != nil || n < 4 || buf[0] != 0x05 {
		return
	}
	cmd := buf[1]
	atyp := buf[3]

	var destAddr string
	switch atyp {
	case 0x01: // IPv4
		n, err = io.ReadFull(conn, buf[:4])
		if err != nil {
			return
		}
		destAddr = net.IP(buf[:4]).String()
	case 0x03: // Domain
		n, err = io.ReadFull(conn, buf[:1])
		if err != nil {
			return
		}
		dlen := int(buf[0])
		n, err = io.ReadFull(conn, buf[:dlen])
		if err != nil {
			return
		}
		destAddr = string(buf[:dlen])
	case 0x04: // IPv6
		n, err = io.ReadFull(conn, buf[:16])
		if err != nil {
			return
		}
		destAddr = net.IP(buf[:16]).String()
	default:
		return
	}

	// Port
	n, err = io.ReadFull(conn, buf[:2])
	if err != nil {
		return
	}
	port := binary.BigEndian.Uint16(buf[:2])
	_ = destAddr
	_ = port

	if cmd == 0x01 {
		// CONNECT: Send success reply
		reply := []byte{0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, byte(port >> 8), byte(port & 0xff)}
		if _, err := conn.Write(reply); err != nil {
			return
		}

		// Echo first payload packet
		buf := make([]byte, 4096)
		n, err := conn.Read(buf)
		if err == nil && n > 0 {
			_, _ = conn.Write(buf[:n])
		}

		// Hold connection open until closed by client
		for {
			n, err := conn.Read(buf)
			if err != nil {
				return
			}
			if n > 0 {
				_, _ = conn.Write(buf[:n])
			}
		}
	} else if cmd == 0x03 {
		// UDP ASSOCIATE
		uconn, err := net.ListenUDP("udp", &net.UDPAddr{IP: net.ParseIP("127.0.0.1"), Port: 0})
		if err != nil {
			conn.Write([]byte{0x05, 0x01, 0x00, 0x01, 0, 0, 0, 0, 0, 0})
			return
		}
		defer uconn.Close()

		laddr := uconn.LocalAddr().(*net.UDPAddr)
		reply := []byte{0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, byte(laddr.Port >> 8), byte(laddr.Port & 0xff)}
		if _, err := conn.Write(reply); err != nil {
			return
		}

		go func() {
			ubuf := make([]byte, 65535)
			for {
				n, raddr, uerr := uconn.ReadFrom(ubuf)
				if uerr != nil {
					return
				}
				// Echo UDP datagram back to proxy client
				_, _ = uconn.WriteTo(ubuf[:n], raddr)
			}
		}()

		// Keep TCP association alive
		discard := make([]byte, 256)
		for {
			_, err := conn.Read(discard)
			if err != nil {
				return
			}
		}
	} else {
		// Unsupported command
		conn.Write([]byte{0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0})
	}
}

func main() {
	listenAddr := flag.String("listen", "127.0.0.1:10800", "SOCKS5 listen address")
	flag.Parse()

	listener, err := net.Listen("tcp", *listenAddr)
	if err != nil {
		log.Fatalf("Failed to listen on %s: %v", *listenAddr, err)
	}
	defer listener.Close()

	fmt.Printf("SOCKS5 Sink Server listening on %s\n", *listenAddr)

	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		for {
			conn, err := listener.Accept()
			if err != nil {
				return
			}
			go handleTCP(conn)
		}
	}()

	<-sigChan
	fmt.Println("SOCKS5 Sink Server shutting down...")
}
