# 配置覆写与删除

**[English](./configuration.md)** | **中文**

SimpleXray 接受完整的 Xray-core 配置，但不会原样执行。

配置适配为单向处理，JSON 配置在导入或保存时执行一次，在传入 Xray 前再执行一次；YAML 配置会先解析并转换为 JSON，再进入同一处理流程。

运行时最终配置始终通过 stdin 传入 Xray（`-config stdin:`），因此工作目录下同名 `config.json` 不会覆盖它。

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
| Xray 原生 TUN 模式下的 `tun` inbound | 保留；`settings.mtu` 缺失时写为应用 TUN MTU；`settings.name` 缺失时写为 `tun-inbound`；删除 `settings.autoSystemRoutingTable` 与 `settings.autoOutboundsInterface`；创建或更新 `sniffing`，并向 `destOverride` 追加 `fakedns`。 |
| 主 SOCKS inbound（tag 为 `socks-in`，否则取第一个 SOCKS inbound） | `port` 覆盖为应用 SOCKS 端口，`listen` 覆盖为应用 SOCKS 地址（仅限回环）；用户名和密码都配置时替换 `settings.auth` 与 `settings.accounts`。SimpleTUN 模式下会剥离认证（其后端仅支持无认证 SOCKS5）。 |
| 其他 `listen` 缺失、为空、`::` 或 `0.0.0.0` 的 inbound | `listen` 改为 `127.0.0.1`。 |
| 缺少 SOCKS inbound | 注入默认 `socks-in`，使用应用 SOCKS 地址与端口，并启用 UDP；SimpleTUN 模式下即使应用配置了凭据也始终为 noauth。 |
| Xray 原生 TUN 模式缺少 `tun` inbound | 注入默认 `tun-inbound`，网络为 `tcp,udp`，MTU 使用应用设置，并使用默认 sniffing。 |

## 应用 SOCKS 设置

| 设置 | 行为 |
| --- | --- |
| SOCKS 入站地址 | 仅接受回环地址（`127.0.0.0/8`、`::1`）。内部健康探针与规则/更新下载都连接 `127.0.0.1`；若旧版本持久化了非回环值，启动会失败并显示提示。 |
| SOCKS 用户名/密码 | 必须同时填写或同时留空；包含换行符的凭据会被拒绝。 |
| 从配置同步凭据 | 当前配置的主 SOCKS inbound 需要密码认证且应用凭据为空时，每个配置会同步一次首个账号到设置页。 |
| 运行中修改 | 修改会保存，但运行中的内核/后端在重启服务前仍使用旧端点，界面会提示需要重启生效。 |

## 路由规则

| 字段 | 处理 |
| --- | --- |
| `routing.domainMatcher` | `mph` 改为 `hybrid`。 |
| 规则 `geosite` | 条目迁移到 `domain` 并添加 `geosite:` 前缀，删除原 `geosite` 键。 |
| 规则 `geoip` | 条目迁移到 `ip` 并添加 `geoip:` 前缀，删除原 `geoip` 键。 |
| 规则 `process` | 删除以 `.exe` 结尾的条目；若全部删除，则移除 `process`。 |
| 空或无效规则 | 不含有效匹配字段的规则会被删除。 |
| 开启 LAN 绕过时的 `geoip:private` | 缺失时注入最高优先级的 `geoip:private` -> `direct` 规则。 |
| 关闭 LAN 绕过时的 `geoip:private` | 从直连规则中删除 `geoip:private` 条目；`ip` 数组为空时移除该字段，若规则已无任何有效匹配字段则整条删除。 |

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
| HTTP inbound | 开启应用 HTTP 代理时，已有 HTTP inbound 保留自身端点，系统代理跟随其实际地址与端口；仅当不存在 HTTP inbound 时，才在 `127.0.0.1:<httpPort>` 注入 `http-inbound`。 |

未列出的字段保持不变。

---

[返回项目 README](./README_CN.md)
