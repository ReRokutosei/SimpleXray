# 配置覆写与删除

**[English](./configuration.md)** | **中文**

SimpleXray 接受完整的 Xray-core 配置，但不会原样执行。

配置适配为单向处理，JSON 配置在导入或保存时执行一次，在传入 Xray 前再执行一次；YAML 配置会先解析并转换为 JSON，再进入同一处理流程。

Xray 实际接收到的是处理后的结果。如需完整保留原始字段，请自行保留外部备份。

## 顶层字段

| 字段 | 处理 |
| --- | --- |
| `geodata` | 整块删除；Geo 数据更新由应用管理。 |
| `log` | 缺失时创建。 |
| `api` | 运行时替换为本地 `StatsService` API 对象。 |
| `stats` | 运行时替换为空对象。 |
| `policy` | 运行时替换为启用出站上/下行统计的策略。 |
| `inbounds` | 缺失时创建；具体规则见下文。 |

## 日志块

| 字段 | 处理 |
| --- | --- |
| `log.error` | 删除。 |
| `log.access` | 应用关闭访问日志时写为 `"none"`；开启时删除该字段。 |
| `log.dnsLog` | 覆盖为应用中的 DNS 日志偏好。 |
| `log.loglevel` | 覆盖为应用日志级别；选择 `Auto` 且字段缺失或为空时写为 `"warning"`。 |

## Inbounds

| 字段 | 处理 |
| --- | --- |
| 非 Xray 原生 TUN 模式下的 `tun` inbound | 删除。 |
| Xray 原生 TUN 模式下的 `tun` inbound | 保留；`settings.name` 缺失时写为 `tun-inbound`；删除 `settings.autoSystemRoutingTable` 与 `settings.autoOutboundsInterface`；创建或更新 `sniffing`，并向 `destOverride` 追加 `fakedns`。 |
| 主 SOCKS inbound（tag 为 `socks-in`，否则取第一个 SOCKS inbound） | `port` 覆盖为应用 SOCKS 端口，`listen` 覆盖为应用 SOCKS 地址；配置了应用 SOCKS 凭据时，替换 `settings.auth` 与 `settings.accounts`。 |
| 其他 `listen` 为 `::` 或 `0.0.0.0` 的 inbound | `listen` 改为 `127.0.0.1`。 |
| 缺少 SOCKS inbound | 注入默认 `socks-in`，使用应用 SOCKS 地址与端口，并启用 UDP。 |
| Xray 原生 TUN 模式缺少 `tun` inbound | 注入默认 `tun-inbound`，网络为 `tcp,udp`，使用默认 sniffing。 |

## 路由规则

| 字段 | 处理 |
| --- | --- |
| `routing.domainMatcher` | `mph` 改为 `hybrid`。 |
| 规则 `geosite` | 条目迁移到 `domain` 并添加 `geosite:` 前缀，删除原 `geosite` 键。 |
| 规则 `geoip` | 条目迁移到 `ip` 并添加 `geoip:` 前缀，删除原 `geoip` 键。 |
| 规则 `process` | 删除以 `.exe` 结尾的条目；若全部删除，则移除 `process`。 |
| 空或无效规则 | 不含有效匹配字段的规则会被删除。 |
| 开启 LAN 绕过时的 `geoip:private` | 缺失时注入最高优先级的 `geoip:private` -> `direct` 规则。 |
| 关闭 LAN 绕过时的 `geoip:private` | 从直连规则中删除 `geoip:private` 条目；`ip` 数组为空时移除该字段。 |

## DNS 与 Outbounds

| 字段 | 处理 |
| --- | --- |
| `dns.hosts` | 缺失时创建。 |
| `https://...alidns.com` DoH 服务器 | 缺失时注入静态 hosts：`223.5.5.5`、`223.6.6.6`。 |
| 以 `http://` 或 `https://` 开头的 outbound `tlsSettings.echConfigList` | 删除。 |
| TCP 流式代理 outbound（`vless`、`vmess`、`trojan`、`shadowsocks`、`socks`、`http`） | 缺失时注入 `tcpKeepAliveIdle=15`、`tcpKeepAliveInterval=3`、`tcpUserTimeout=15000`，以及 custom sockopt `TCP_KEEPCNT=3`；已有用户值不覆盖。 |
| `observatory.probeTimeout` | 缺失时写为 `"2s"`。 |

## 运行时统计注入

| 字段 | 处理 |
| --- | --- |
| `api` | 替换为 `tag=api`、应用 API 监听地址和 `services=["StatsService"]`。 |
| `stats` | 替换为空对象。 |
| `policy` | 替换为启用 `system.statsOutboundUplink` 与 `system.statsOutboundDownlink` 的策略。 |
| HTTP inbound | 开启应用 HTTP 代理且不存在 HTTP inbound 时，在 `127.0.0.1:<httpPort>` 注入 `http-inbound`。 |

未列出的字段保持不变。

---

[返回项目 README](./README_CN.md)
