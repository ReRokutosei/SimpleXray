# QUIC / HTTP3 helper

Small self-contained Go helper used by `tools/suites/quic.py`:

* `-mode server`: runs a self-signed HTTP/3 server (`/hello`, `/payload`)
* `-mode client`: performs an HTTP/3 request and prints one JSON result line

The client is cross-compiled for Android arm64 and pushed to the device by the
benchmark suite. It is test tooling only and is not part of the app/SDK.
