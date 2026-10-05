# Configuration Overrides and Removals

**English** | **[中文](./configuration_CN.md)**

SimpleXray accepts complete Xray-core configurations, but it does not execute them unchanged. A one-way sanitizer runs when a JSON configuration is imported or saved, and once more immediately before the configuration is passed to Xray. YAML input is parsed, converted to JSON, and then sanitized.

At runtime the final configuration is always fed to Xray through stdin (`-config stdin:`), so a file named `config.json` in the working directory cannot shadow it.

The sanitized result is what the core actually receives. Keep an external copy of the original configuration if every field must be preserved.

## Top-Level Fields

| Field | Action |
| --- | --- |
| `geodata` | Removed entirely; Geo data updates are managed by the app. |
| `log` | Created if missing. |
| `api` | Replaced at runtime with the local `StatsService` API object. |
| `stats` | Replaced at runtime with an empty object. |
| `policy` | Replaced at runtime with a policy enabling outbound up/down statistics. |
| `inbounds` | Created if missing; see inbound rules below. |

## Log Block

| Field | Action |
| --- | --- |
| `log.error` | Removed. |
| `log.access` | Set to `"none"` when the app access log is disabled; removed when it is enabled. |
| `log.dnsLog` | Overwritten with the app DNS log preference. |
| `log.loglevel` | Overwritten with the app log level; when set to `Auto` and missing/empty, set to `"warning"`. |

## Inbounds

| Field | Action |
| --- | --- |
| `tun` inbound when native Xray TUN is not active | Removed. |
| `tun` inbound when native Xray TUN is active | Kept; a missing `settings.mtu` is set to the app tunnel MTU; `settings.name` defaults to `tun-inbound`; `settings.autoSystemRoutingTable` and `settings.autoOutboundsInterface` are removed; `sniffing` is created/updated and `fakedns` is added to `destOverride`. |
| Primary SOCKS inbound (tag `socks-in`, or first SOCKS inbound) | `port` is replaced with the app SOCKS port; `listen` with the app SOCKS address (loopback only); `settings.auth` and `settings.accounts` are replaced when both app SOCKS credentials are configured. In SimpleTUN mode authentication is stripped (the backend supports no-auth SOCKS5 only). |
| Other inbounds with `listen` missing, empty, `::` or `0.0.0.0` | `listen` is replaced with `127.0.0.1`. |
| Missing SOCKS inbound | A default `socks-in` inbound is injected using the app SOCKS address/port and UDP enabled; it is always no-auth in SimpleTUN mode even when app credentials are set. |
| Missing `tun` inbound when native Xray TUN is active | A default `tun-inbound` is injected with `tcp,udp`, the app tunnel MTU and default sniffing. |

## App SOCKS Settings

| Setting | Behavior |
| --- | --- |
| SOCKS inbound address | Only loopback literals are accepted (`127.0.0.0/8`, `::1`). The internal health probe and rule/update downloads connect to `127.0.0.1`, and startup fails with a visible error if a non-loopback value is persisted from an older version. |
| SOCKS username/password | Both must be set together or both left empty. Credentials containing line breaks are rejected. |
| Credentials imported from a config | When the selected config requires password auth on the primary SOCKS inbound and the app credentials are still empty, the first account is adopted into the app settings once per config. |
| Editing while the service is running | The value is saved, but the running core/backend keeps the old endpoint until the service is restarted; the UI shows a restart notice. |

## Routing Rules

| Field | Action |
| --- | --- |
| `routing.domainMatcher` | `mph` is changed to `hybrid`. |
| Rule `geosite` | Entries are moved into `domain` as `geosite:<entry>`; the original `geosite` key is removed. |
| Rule `geoip` | Entries are moved into `ip` as `geoip:<entry>`; the original `geoip` key is removed. |
| Rule `process` | Entries ending in `.exe` are removed; if nothing remains, `process` is removed. |
| Empty/invalid rules | Rules without any effective matching field are removed. |
| `geoip:private` with LAN bypass enabled | A top-priority `geoip:private` -> `direct` rule is injected if absent. |
| `geoip:private` with LAN bypass disabled | `geoip:private` entries are removed from direct rules; an empty `ip` array is removed, and rules that no longer have any effective matching field are removed entirely. |

## DNS and Outbounds

| Field | Action |
| --- | --- |
| `dns.hosts` | Created if missing. |
| `https://...alidns.com` DoH servers | Static hosts `223.5.5.5` and `223.6.6.6` are injected if absent. |
| Outbound `tlsSettings.echConfigList` starting with `http://` or `https://` | Removed. |
| TCP-based proxy outbounds (`vless`, `vmess`, `trojan`, `shadowsocks`, `socks`, `http`) | Missing `streamSettings.sockopt` values are injected: `tcpKeepAliveIdle=15`, `tcpKeepAliveInterval=3`, `tcpUserTimeout=15000`, plus custom sockopt `TCP_KEEPCNT=3` unless already present. Existing user values are not overwritten. |
| `observatory.probeTimeout` | Set to `"2s"` if missing. |

## Runtime Statistics Injection

| Field | Action |
| --- | --- |
| `api` | Replaced with `tag=api`, the app API listen address, and `services=["StatsService"]`. |
| `stats` | Replaced with an empty object. |
| `policy` | Replaced with a policy whose `system` enables `statsOutboundUplink` and `statsOutboundDownlink`. |
| HTTP inbound | When the app HTTP proxy is enabled, an existing HTTP inbound keeps its own endpoint and the system proxy follows its actual address/port; only when no HTTP inbound exists is `http-inbound` injected on `127.0.0.1:<httpPort>`. |

Fields not listed above are left unchanged.

---

[Back to project README](./README.md)
