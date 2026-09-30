package main

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"math/big"
	"net/http"
	"os"
	"time"

	"github.com/quic-go/quic-go/http3"
)

const payloadSize = 10 << 20

func selfSignedCert() tls.Certificate {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		panic(err)
	}
	tmpl := &x509.Certificate{
		SerialNumber: big.NewInt(1),
		Subject:      pkix.Name{CommonName: "quic-smoke"},
		NotBefore:    time.Now().Add(-time.Hour),
		NotAfter:     time.Now().Add(24 * time.Hour),
	}
	der, err := x509.CreateCertificate(rand.Reader, tmpl, tmpl, &key.PublicKey, key)
	if err != nil {
		panic(err)
	}
	return tls.Certificate{Certificate: [][]byte{der}, PrivateKey: key}
}

func runServer(addr string) {
	mux := http.NewServeMux()
	mux.HandleFunc("/hello", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/plain")
		_, _ = w.Write([]byte("hello-quic"))
	})
	mux.HandleFunc("/payload", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/octet-stream")
		w.Header().Set("Content-Length", fmt.Sprint(payloadSize))
		buf := make([]byte, 32<<10)
		for i := range buf {
			buf[i] = byte(i)
		}
		remaining := payloadSize
		for remaining > 0 {
			n := len(buf)
			if n > remaining {
				n = remaining
			}
			if _, err := w.Write(buf[:n]); err != nil {
				return
			}
			remaining -= n
		}
	})

	server := &http3.Server{
		Addr: addr,
		TLSConfig: &tls.Config{
			Certificates: []tls.Certificate{selfSignedCert()},
			NextProtos:   []string{"h3"},
		},
		Handler: mux,
	}
	fmt.Fprintf(os.Stderr, "QUIC smoke server listening on %s\n", addr)
	if err := server.ListenAndServe(); err != nil {
		fmt.Fprintln(os.Stderr, "server error:", err)
		os.Exit(1)
	}
}

func runClient(rawURL string, timeout time.Duration) {
	transport := &http3.Transport{
		TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
	}
	defer transport.Close()
	client := &http.Client{Transport: transport, Timeout: timeout}

	start := time.Now()
	resp, err := client.Get(rawURL)
	if err != nil {
		writeResult(map[string]any{"ok": false, "url": rawURL, "error": err.Error()})
		os.Exit(1)
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		writeResult(map[string]any{"ok": false, "url": rawURL, "error": err.Error()})
		os.Exit(1)
	}
	elapsed := time.Since(start).Seconds()
	mbps := 0.0
	if elapsed > 0 {
		mbps = float64(len(body)) * 8 / 1e6 / elapsed
	}
	writeResult(map[string]any{
		"ok":      true,
		"url":     rawURL,
		"status":  resp.StatusCode,
		"proto":   resp.Proto,
		"bytes":   len(body),
		"seconds": elapsed,
		"mbps":    mbps,
	})
}

func writeResult(v map[string]any) {
	b, _ := json.Marshal(v)
	fmt.Println(string(b))
}

func main() {
	mode := flag.String("mode", "client", "server or client")
	addr := flag.String("addr", "0.0.0.0:4433", "server listen address")
	url := flag.String("url", "https://127.0.0.1:4433/hello", "client target URL")
	timeout := flag.Duration("timeout", 20*time.Second, "client timeout")
	flag.Parse()

	switch *mode {
	case "server":
		runServer(*addr)
	case "client":
		runClient(*url, *timeout)
	default:
		fmt.Fprintln(os.Stderr, "mode must be server or client")
		os.Exit(2)
	}
}
