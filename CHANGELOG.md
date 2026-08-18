# Changelog

All notable changes to this project will be documented in this file. See [standard-version](https://github.com/conventional-changelog/standard-version) for commit guidelines.

## [1.4.0](https://github.com/ReRokutosei/SimpleXray/compare/v1.3.0...v1.4.0) (2026-08-18)


### Features

* **service:** enhance geo rule auto-update with timeout catch-up and status display ([549b85b](https://github.com/ReRokutosei/SimpleXray/commit/549b85bdb105e0eb9493d28c8017df20573dc29b))


### Bug Fixes

* **data:** strictly validate file extensions on config and rule imports ([c01e1f9](https://github.com/ReRokutosei/SimpleXray/commit/c01e1f94c5cd63f2d93ba51de53b89e725b6c65f))
* **prefs:** persist keepAwake across cold restarts and update state properly ([22fe5c6](https://github.com/ReRokutosei/SimpleXray/commit/22fe5c62d6e4c96a4acbfc9c9d5315fb85ae1830))
* **ui:** add bottom spacing to core control card on dashboard ([95a471a](https://github.com/ReRokutosei/SimpleXray/commit/95a471a9d1d85a6cac551ae33a61f2d8f81600ee))
* **ui:** request notification permission on service start and avoid repeated prompts ([b76e595](https://github.com/ReRokutosei/SimpleXray/commit/b76e5953b2319b4760bfd43eab482e466c79380d))

## [1.3.0](https://github.com/ReRokutosei/SimpleXray/compare/v1.2.0...v1.3.0) (2026-08-17)


### Features

* **service:** add keep CPU awake option with partial wake lock management ([be0ee6c](https://github.com/ReRokutosei/SimpleXray/commit/be0ee6c197d20f89d2dc451b7e55dedff355adde))
* **settings:** allow customizable MTU with range validation (1280-9000) ([111c4e4](https://github.com/ReRokutosei/SimpleXray/commit/111c4e4b00310557987e02aa47ec99eace30f74f))
* **tun:** add native Xray TUN mode ([f85ba31](https://github.com/ReRokutosei/SimpleXray/commit/f85ba3101d8f255780540dda494526a84c9c272d))
* **tunnel:** add selectable Xray TUN and Hev tunnel modes ([71f3752](https://github.com/ReRokutosei/SimpleXray/commit/71f3752385edded2a5dd542f144b4be8581c6920))
* **ui:** add gentle notification permission explanation dialog for Android 13+ ([4a6d022](https://github.com/ReRokutosei/SimpleXray/commit/4a6d02207aac57c299c59432cbac6819af53b974))


### Bug Fixes

* **deps:** update dependency androidx.compose:compose-bom to v2026 ([#16](https://github.com/ReRokutosei/SimpleXray/issues/16)) ([a302eec](https://github.com/ReRokutosei/SimpleXray/commit/a302eec4006aa0786fcc4eb79787abf660668271))
* **deps:** update dependency androidx.core:core-ktx to v1.19.0 ([#8](https://github.com/ReRokutosei/SimpleXray/issues/8)) ([50a556c](https://github.com/ReRokutosei/SimpleXray/commit/50a556c94b7c1a5f9c57006f0dfadafe8729b506))
* **deps:** update dependency androidx.datastore:datastore-preferences to v1.2.1 ([#9](https://github.com/ReRokutosei/SimpleXray/issues/9)) ([d0ca8c9](https://github.com/ReRokutosei/SimpleXray/commit/d0ca8c984aa4d315f91c08857a9f8abb0119dc27))
* **deps:** update dependency androidx.test:runner to v1.7.0 ([#10](https://github.com/ReRokutosei/SimpleXray/issues/10)) ([f72b9d2](https://github.com/ReRokutosei/SimpleXray/commit/f72b9d2e0ea58b039e8657ea9a6fdb7cf02a2ccf))
* **deps:** update dependency androidx.test.ext:junit to v1.3.0 ([#11](https://github.com/ReRokutosei/SimpleXray/issues/11)) ([33cea33](https://github.com/ReRokutosei/SimpleXray/commit/33cea3303610cee85fe2ed8c1b54fc270d33c3fd))
* **deps:** update dependency com.google.android.material:material to v1.14.0 ([#19](https://github.com/ReRokutosei/SimpleXray/issues/19)) ([fdc6d89](https://github.com/ReRokutosei/SimpleXray/commit/fdc6d896d3973a95ab1cf4c237d3676540fdb4d2))
* **deps:** update dependency com.squareup.okhttp3:okhttp to v5 ([#18](https://github.com/ReRokutosei/SimpleXray/issues/18)) ([0921448](https://github.com/ReRokutosei/SimpleXray/commit/0921448b82773dd3e2cd6083ab06428eba7754f3))
* **deps:** update dependency io.grpc:grpc-kotlin-stub to v1.5.0 ([#12](https://github.com/ReRokutosei/SimpleXray/issues/12)) ([db26112](https://github.com/ReRokutosei/SimpleXray/commit/db2611260097ec723373b47ec6c4d4ea8c0855a0))
* **deps:** update dependency org.jetbrains.kotlinx:kotlinx-serialization-json to v1.11.0 ([#13](https://github.com/ReRokutosei/SimpleXray/issues/13)) ([7899e79](https://github.com/ReRokutosei/SimpleXray/commit/7899e79522525daba338f8774b09decf521071de))
* **deps:** update dependency org.yaml:snakeyaml to v2.6 ([#14](https://github.com/ReRokutosei/SimpleXray/issues/14)) ([1ef1485](https://github.com/ReRokutosei/SimpleXray/commit/1ef14851c5f9a3f8d2eeb168e10f1f9d262a3ba0))
* **deps:** update grpc-java monorepo to v1.83.1 ([#15](https://github.com/ReRokutosei/SimpleXray/issues/15)) ([93a16c9](https://github.com/ReRokutosei/SimpleXray/commit/93a16c9c06b8249cc2085c692c83252f67142cfe))
* **i18n:** refine bilingual UI strings ([8615066](https://github.com/ReRokutosei/SimpleXray/commit/8615066b4c8a7690a78233fc4eafbade2e081e8a))
* **jni:** use pipe2 with O_CLOEXEC to prevent fd leakage ([6065236](https://github.com/ReRokutosei/SimpleXray/commit/6065236e284cf319ac45aa006c3e881cd3130b36))
* **proxy:** prune uninstalled app packages from per-app proxy list ([49359cb](https://github.com/ReRokutosei/SimpleXray/commit/49359cb15f5bda8e424298e34358273cf8bae2ed))
* remove redundant elvis operators on non-nullable ResponseBody ([c7abe78](https://github.com/ReRokutosei/SimpleXray/commit/c7abe78a3a766f98edc99f83b7b9f3731b9db894))
* **service:** restore TCP API and preserve logs ([82b8d9f](https://github.com/ReRokutosei/SimpleXray/commit/82b8d9f4de94789661c528434dc24e9f30b87241))
* **service:** serialize Xray startup ([30f38f4](https://github.com/ReRokutosei/SimpleXray/commit/30f38f4a63a4d7e51cb71ace7545e316035ff09b))
* **tun:** avoid Android interface enumeration ([6e7cb21](https://github.com/ReRokutosei/SimpleXray/commit/6e7cb21eaa6fc0bfdc901834c59f4fcbaeecf55f))
* **tun:** harden native reload and MTU handling ([598f8a0](https://github.com/ReRokutosei/SimpleXray/commit/598f8a0ee30e62a96143d228e1a4ef448888332d))

## [1.2.0](https://github.com/ReRokutosei/SimpleXray/compare/v1.1.0...v1.2.0) (2026-08-14)


### Bug Fixes

* **service:** gRPC-based startup detection (works with loglevel none); drop connectivity test; dynamic provider authorities ([fa77018](https://github.com/ReRokutosei/SimpleXray/commit/fa7701824d138e1b49b203eaec49df80be10ac00))
* **service:** use bitmap webp for notification small icon ([e2c86a6](https://github.com/ReRokutosei/SimpleXray/commit/e2c86a6e5961cc440751e12adf596e307da9f378))
* **ui:** resolve icon alias by full class name (debug suffix); move outbound nodes card after core control ([cd1ead7](https://github.com/ReRokutosei/SimpleXray/commit/cd1ead7a8205989fdd21c218a20296912e0e240d))

## [1.1.0](https://github.com/ReRokutosei/SimpleXray/compare/v1.0.0...v1.1.0) (2026-08-14)


### Features

* **dashboard:** show outbound nodes with latency ([191342a](https://github.com/ReRokutosei/SimpleXray/commit/191342a3c261330208bc8b7f083e35a21aa31f41))
* **ui:** add Origin icon as default, recents icon follows selection, lineal notification icon ([1164e18](https://github.com/ReRokutosei/SimpleXray/commit/1164e18f02a3a7f98edda80b580d8cbdd6cf4b46))
* **ui:** add runtime-switchable app icons ([c7c89f1](https://github.com/ReRokutosei/SimpleXray/commit/c7c89f179804c5b037542387787dc7ea40fba0c6))
* **ui:** hide log page when log level is none ([56409e7](https://github.com/ReRokutosei/SimpleXray/commit/56409e7417d3eb64cb8da34056b6800d39e31e18))
* **ui:** move app icon picker into General section as dropdown ([222a072](https://github.com/ReRokutosei/SimpleXray/commit/222a072b775f9ed6cf1ba180133eb7277a394049))


### Bug Fixes

* **config:** do not switch config on import while service is running ([40dc2fb](https://github.com/ReRokutosei/SimpleXray/commit/40dc2fbf43e354d4c5a97c0951f378ee11c34f13))
* **crash:** use 2-arg TaskDescription, drop theme-resolved colorPrimary (Android 16 opaque crash) ([affafe7](https://github.com/ReRokutosei/SimpleXray/commit/affafe7ef3e6abc9e302e31385b09fde0eb55641))
* **deps:** update dependency androidx.compose:compose-bom to v2025.12.01 ([#6](https://github.com/ReRokutosei/SimpleXray/issues/6)) ([cb007fc](https://github.com/ReRokutosei/SimpleXray/commit/cb007fca6d3d45c6a9b5d1260d33c55564573157))
* **deps:** update dependency androidx.navigation:navigation-compose-android to v2.9.8 ([#1](https://github.com/ReRokutosei/SimpleXray/issues/1)) ([6afc06e](https://github.com/ReRokutosei/SimpleXray/commit/6afc06ec9203d1e21b80e1a09a0de1c3bdc65649))
* **deps:** update dependency com.google.protobuf:protoc to v3.25.9 ([#2](https://github.com/ReRokutosei/SimpleXray/issues/2)) ([6243c79](https://github.com/ReRokutosei/SimpleXray/commit/6243c79ec90f8a553fa29730f602212a258f1d4e))
* **import:** sanitize file names from content URIs ([99f8f56](https://github.com/ReRokutosei/SimpleXray/commit/99f8f56b6d83f2ac913fddf169627b6a72da5874))
* **security:** skip private-address pings, redact config logs ([bdc8ce1](https://github.com/ReRokutosei/SimpleXray/commit/bdc8ce18a20a1ea63e8f54753b15a0a9d01062d0))
* **service:** retry xray start once and stop on repeated failure ([d30dc15](https://github.com/ReRokutosei/SimpleXray/commit/d30dc1552ac00a95bafec8d293f38b0689722960))
* **service:** stamp xray logs with device-local time ([d59d2db](https://github.com/ReRokutosei/SimpleXray/commit/d59d2dbcbfb39789e3e79f7ef324af6ec0654f75))
* **ui:** keep config editor snackbar above system nav bar ([1c40f06](https://github.com/ReRokutosei/SimpleXray/commit/1c40f0634b670b25c99b6c12a94e397929246ad1))
* **ui:** record random app icon on first launch without touching component states ([2c2aa5f](https://github.com/ReRokutosei/SimpleXray/commit/2c2aa5fc4ab8b05aee5f2f8914a3f328ce9f039a))
* **ui:** scale vector foreground content via group (108dp canvas, 58dp content) ([c8e1803](https://github.com/ReRokutosei/SimpleXray/commit/c8e1803537e0c3cb92c59b668a1ca2e844c3e35c))
* **ui:** show snackbar on AppListScreen ([cac2272](https://github.com/ReRokutosei/SimpleXray/commit/cac2272f16ab7016ab3c38126cbd497eeefff4eb))

## 1.0.0 (2026-08-11)


### Features

* complete Native TUN, loopback Socks/API, AGP 9.3.1 upgrade & profile sanitizer ([98b71e2](https://github.com/ReRokutosei/SimpleXray/commit/98b71e2c557cc1a22e89ceb049b6033d7511a908))
* **config:** sanitize and auto-inject SOCKS inbound to prevent port conflict ([e66365c](https://github.com/ReRokutosei/SimpleXray/commit/e66365ca762c8ce59ce741a247d9528e45cdf1c9))
* implement multi-format config, editor search, log optimization, and custom dat rules ([2558a8e](https://github.com/ReRokutosei/SimpleXray/commit/2558a8e3e6da7d198af48020668a8f2afcb74caf))
* **log:** support long-press text selection and one-tap log clearing ([81d0a25](https://github.com/ReRokutosei/SimpleXray/commit/81d0a25eaf5e1aa14f30205fe6025f4d63158921))
* **rule-files:** optimize third-party dat import & fix GEO update crash ([bff84be](https://github.com/ReRokutosei/SimpleXray/commit/bff84bed3759ed2b2bc936c35bb099f4d7d93079))
* **rules:** add geo rule files scheduled auto-update setting ([1a90357](https://github.com/ReRokutosei/SimpleXray/commit/1a903570209f43ab6445d826c4b629dc5b024d59))
* **security:** add geo dat shadow sandbox validation before overwriting rule files ([9b44604](https://github.com/ReRokutosei/SimpleXray/commit/9b44604d6743b9424cdc7eed1d60e3fee5f94417))
* **settings:** add configurable LogLevel with single-direction AST injection and UI selection ([53510b5](https://github.com/ReRokutosei/SimpleXray/commit/53510b5be201fc893cbd5929f92537ca0db8c78e))
* **settings:** add toggle for hiding app from Android recent tasks and fix UI toasts ([e2d6cb9](https://github.com/ReRokutosei/SimpleXray/commit/e2d6cb9546715e56d45b77726492ea715914a652))
* **ui:** adapt config action icons to theme colors ([c5ca255](https://github.com/ReRokutosei/SimpleXray/commit/c5ca255439688e7256a0fd48fa2c85261efb827f))
* **ui:** add empty state actions, plus card, system BackHandler, flash/refresh action, and i18n ([abd086b](https://github.com/ReRokutosei/SimpleXray/commit/abd086b07a752092247084a2d8c2159fcbe847c4))
* **ui:** add liquid glass effect to floating navigation bar ([47249f5](https://github.com/ReRokutosei/SimpleXray/commit/47249f5780be758c16f573e378c1b67b310981ce))
* **ui:** add responsive Miuix NavigationRail for wide screen and tablet layout ([cc0db6b](https://github.com/ReRokutosei/SimpleXray/commit/cc0db6bc662949ba5f18b8cdf08a37ba616ee0a9))
* **ui:** extract log actions to top bar icons, remove settings backup/restore, enable Monet dynamic colors on Android 12+ ([8789c24](https://github.com/ReRokutosei/SimpleXray/commit/8789c24e863c054ea9f0a6872d37c060a98c50e5))
* **ui:** implement fully penetrating floating navigation bar visual effect ([b687233](https://github.com/ReRokutosei/SimpleXray/commit/b6872338196863a6d94a6a1fd3a2d7111f091542))
* **ui:** implement Master-Detail split view for ConfigScreen on wide screens ([6994d93](https://github.com/ReRokutosei/SimpleXray/commit/6994d93a0a9ad8288864feeac01f183595777945))
* **ui:** implement wide-screen responsive grid and container width constraints ([92560a4](https://github.com/ReRokutosei/SimpleXray/commit/92560a4b0c1439f465d51a9a34baa399fcb07852))
* **ui:** make Core Control container transparent and add show no-internet apps filter ([3d8b515](https://github.com/ReRokutosei/SimpleXray/commit/3d8b5159fd76aff081a2838c5babe1a9b739d7fc))
* **ui:** refine master-detail breakpoint, remove embedded scaffold gap, and add fullscreen editor toggle ([ef7dc2f](https://github.com/ReRokutosei/SimpleXray/commit/ef7dc2f32e2ad15f6dabe05af72b1cf5dfc052f0))
* **ui:** refine TopAppBar layout, SnackbarHost binding, and config list tags ([9c7cdaf](https://github.com/ReRokutosei/SimpleXray/commit/9c7cdaf28a668e60e878eaf3220dcd1bcff3bd0e))
* **ui:** remove unused backup/restore codebase, replace config editor 3-dots menu with share icon, and Pangu-ify values-zh strings ([190efc8](https://github.com/ReRokutosei/SimpleXray/commit/190efc8bdd5d6a6040c88dfe3d4b2af1e7d65346))


### Bug Fixes

* **build:** resolve missing material icons class in release build ([96cf654](https://github.com/ReRokutosei/SimpleXray/commit/96cf654b511d191d4f99ba883cac36bbc3314c3a))
* **config:** implement smart inbound sanitization filtering desktop tun and converting listen addresses ([e261ff0](https://github.com/ReRokutosei/SimpleXray/commit/e261ff044a96dcf28b46680e29efce4fd4624179))
* **core:** integrate v2rayNG start locks, settling delay & robust rule block sanitizer ([ab8f69a](https://github.com/ReRokutosei/SimpleXray/commit/ab8f69a0ae35c3893f32315a7e5ddb371d9b4fad))
* **i18n:** localize Dashboard titles and dynamically resolve default rule file summary ([a00779a](https://github.com/ReRokutosei/SimpleXray/commit/a00779a5a1bfa43b1e6506133f1e78ebee9ddb9b))
* **jni:** fix PKGNAME macro, JNI signature return types and add Proguard keep rules for HevSocks5Tunnel ([ce493e9](https://github.com/ReRokutosei/SimpleXray/commit/ce493e9c7a87f9e9992ca89818bf02b8c6afc11e))
* **network:** disable default HTTP proxy, add independent 10809 HTTP port and update UI strings ([b4458db](https://github.com/ReRokutosei/SimpleXray/commit/b4458db5660d4060e36671ceca1d0501cc868522))
* remove unescaped quotes in strings.xml ([4c78901](https://github.com/ReRokutosei/SimpleXray/commit/4c7890114a46d33a8beb50c6e8b7404e45f874b6))
* **security:** replace xray sandbox validation with lightweight sanity check for dat files ([be223a3](https://github.com/ReRokutosei/SimpleXray/commit/be223a333592901fd7b2ffc8f5b63332ee10b7e6))
* **service:** replace nativeSpawnXray with ProcessBuilder to eliminate TUN socket deadlocks ([f7deb44](https://github.com/ReRokutosei/SimpleXray/commit/f7deb44182ed39e06670c2df1e81c50b2ff80889))
* **settings:** real-time update custom dat file list on import/delete/download ([0239663](https://github.com/ReRokutosei/SimpleXray/commit/023966334550bb24edf550c35e89a4f62027ebee))
* **ui:** pass parent = null to root navigation event dispatcher ([014efd4](https://github.com/ReRokutosei/SimpleXray/commit/014efd431e98d07909d7bfb921dbfc5424f5cb5a))
* **ui:** provide LocalNavigationEventDispatcherOwner to resolve popup menu expansion ([d682725](https://github.com/ReRokutosei/SimpleXray/commit/d6827251732b3b4c90e35e661382440a17375ce3))
* **ui:** resolve OverlayBottomSheet z-index ordering and compact layout padding ([f7c8255](https://github.com/ReRokutosei/SimpleXray/commit/f7c8255efa52a0622b653c5baabfd5d3f46b92d5))
* use START_NOT_STICKY and broadcast ACTION_START on core ready ([780e1b3](https://github.com/ReRokutosei/SimpleXray/commit/780e1b362aa2869f6fff4ee101034c426b4a5279))
