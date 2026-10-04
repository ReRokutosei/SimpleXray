# Changelog

All notable changes to this project will be documented in this file. See [standard-version](https://github.com/conventional-changelog/standard-version) for commit guidelines.

## 2.3.0-alpha.6 (2026-10-04)


### ⚠ BREAKING CHANGES

* **build:** Minimum supported Android version is now Android 14 (API 34). Devices running Android 10-13 (API 29-33) are no longer supported.

### Features

* Add 'Disable VPN Interface' option ([df54986](https://github.com/ReRokutosei/SimpleXray/commit/df5498624b213c22ebd9c66e0120933fd490af60))
* Add a dashboard UI for monitoring and statistics ([56818cb](https://github.com/ReRokutosei/SimpleXray/commit/56818cb5bb7c2d590751636e20b7f7a4bedb7d8c))
* Add async debounced full-text search for log entries ([b684053](https://github.com/ReRokutosei/SimpleXray/commit/b684053d88491d00bafbb4238d9dadf93268e24c))
* Add auto-indent support to configuration editor ([1e2ca11](https://github.com/ReRokutosei/SimpleXray/commit/1e2ca11a3edd26a357d3ef15aa5788d8a06dc163))
* Add badges to README ([9ff06fc](https://github.com/ReRokutosei/SimpleXray/commit/9ff06fc3d156f433869103f0bee7b5ce81fabbbf))
* Add bracket matching highlight in config editor ([eb72a7a](https://github.com/ReRokutosei/SimpleXray/commit/eb72a7a8f90dc6960b5bba13dd6e9e744151cba4))
* Add clipboard export/import for app list ([2b391a5](https://github.com/ReRokutosei/SimpleXray/commit/2b391a53f09143a380f592008368a55332631ec4))
* Add confirmation for rule file deletion ([a1568ea](https://github.com/ReRokutosei/SimpleXray/commit/a1568ea13cb041f65a4ab588b7b34773b97fabe9))
* Add connectivity test with customizable target and timeout ([1c50daa](https://github.com/ReRokutosei/SimpleXray/commit/1c50daa9b054ff5d4c3995d6395f33f07792f309))
* Add empty state UI to Config and Log fragments ([b5d0bc4](https://github.com/ReRokutosei/SimpleXray/commit/b5d0bc4bc7b2ecc6e2c19d1621a4c77f9f34122d))
* Add Fastlane metadata for F-Droid ([35d580e](https://github.com/ReRokutosei/SimpleXray/commit/35d580e5e193e04704f47abeb069d8b6ec39e5bd))
* Add Indonesian localization ([7d300eb](https://github.com/ReRokutosei/SimpleXray/commit/7d300eb1adbd36b3ae6820e28c223d81c7ac19bf))
* add input field for socks address ([5e8623a](https://github.com/ReRokutosei/SimpleXray/commit/5e8623a00a2b0e5cf009c2a6efa0221a39e12569))
* Add Quick Settings Tile to toggle VPN service ([9e3117b](https://github.com/ReRokutosei/SimpleXray/commit/9e3117bb6656bef0f5cd3dd7f882f582571d2cee))
* Add Russian localization ([071cb03](https://github.com/ReRokutosei/SimpleXray/commit/071cb03f9b91a3438c395f94df9254da5edd66ff))
* Add scrollbar to app list ([84f2832](https://github.com/ReRokutosei/SimpleXray/commit/84f2832761f8aa274fdf4da12424f57053b0b4b5))
* Add Select All and Inverse Selection menu to App Proxy screen ([3588271](https://github.com/ReRokutosei/SimpleXray/commit/35882713a82c7e7d2710405d2ff3cd7c11c06a9d))
* add setMetered false config ([97c7ba4](https://github.com/ReRokutosei/SimpleXray/commit/97c7ba497fa962827dc7b4ee7034d154bb10f038))
* Add system app visibility and bypass options to AppList ([5e2abb3](https://github.com/ReRokutosei/SimpleXray/commit/5e2abb36eb127b1cc45427835df28151f8f5f388))
* Add theme settings ([5b151f8](https://github.com/ReRokutosei/SimpleXray/commit/5b151f8ca105aaa25275009339d0c8c73dba5fe7))
* Add update check functionality ([02b229e](https://github.com/ReRokutosei/SimpleXray/commit/02b229e2c4918d381d240308a7f73f02ba9a6574))
* add user and pass fields for SOCKS ([c8023da](https://github.com/ReRokutosei/SimpleXray/commit/c8023da5e3572c1504c2be866077937360f2f107))
* Add VLESS link support ([3e2a0a3](https://github.com/ReRokutosei/SimpleXray/commit/3e2a0a3acc5c772e133dcbfef01fd95efc1d41c0))
* Add x86_64 ABI support ([5c5215f](https://github.com/ReRokutosei/SimpleXray/commit/5c5215fa19005214fa3e7d91ee32862927055241))
* **benchmark:** introduce headless BenchmarkService and asynchronous service teardown ([67a41e6](https://github.com/ReRokutosei/SimpleXray/commit/67a41e63fd108869fe06015834eb1dec098e3627))
* complete Native TUN, loopback Socks/API, AGP 9.3.1 upgrade & profile sanitizer ([1b86c53](https://github.com/ReRokutosei/SimpleXray/commit/1b86c5332408c6c52bbfd3bd41bb0b40eea7f966))
* **config:** sanitize and auto-inject SOCKS inbound to prevent port conflict ([b85f841](https://github.com/ReRokutosei/SimpleXray/commit/b85f841677ca57b2686205d89c7a98b698c8132f))
* **dashboard:** show outbound nodes with latency ([af30ae3](https://github.com/ReRokutosei/SimpleXray/commit/af30ae3854759d550a86578de59397a7fad2ae3b))
* Disable save button when config is unchanged ([5210b99](https://github.com/ReRokutosei/SimpleXray/commit/5210b995f3a0977fd16249c75a40431ae26ec3dd))
* **editor,log:** auto-scroll to match in config editor and highlight matches with scroll reset in logs ([4379e09](https://github.com/ReRokutosei/SimpleXray/commit/4379e09279e29f6949e0248c2b3a1db5ce7be8dc))
* Enable minification in release builds ([2f90511](https://github.com/ReRokutosei/SimpleXray/commit/2f90511033c63d38b3d89cc63ecc48e8917c7af9))
* Handle screen rotation without activity recreation ([06bbf43](https://github.com/ReRokutosei/SimpleXray/commit/06bbf430b2ee23b82802b2822ec040ca93e9e451))
* Implement config import via share intent ([4fd83e8](https://github.com/ReRokutosei/SimpleXray/commit/4fd83e8063708e644549ba37f0edff57e1d5211d))
* Implement drag-and-drop for config reordering ([7e57bbd](https://github.com/ReRokutosei/SimpleXray/commit/7e57bbd96f0c51bfb5fcba0e27d0c2c840fd40fb))
* Implement geoip/geosite rule file import and restore ([73dcdc5](https://github.com/ReRokutosei/SimpleXray/commit/73dcdc566be36180b28fdedc5534b8d75bfb928e))
* implement multi-format config, editor search, log optimization, and custom dat rules ([b1b2940](https://github.com/ReRokutosei/SimpleXray/commit/b1b2940d73dc5fda8f1a57294df62830daa248f5))
* Implement network-based updates for geo-rule files ([d727aae](https://github.com/ReRokutosei/SimpleXray/commit/d727aaedd6c574957322fbdc7426c1ee95c0dadf))
* Implement simplexray:// URI for config sharing ([aa90778](https://github.com/ReRokutosei/SimpleXray/commit/aa90778641b844080c60854721f543ceae20acca))
* Implement ViewPager2 for smooth bottom nav transitions ([f44f420](https://github.com/ReRokutosei/SimpleXray/commit/f44f42059154384a98521c7562ac7c62725cb5a2))
* Implement Xray configuration hot reload ([e2705de](https://github.com/ReRokutosei/SimpleXray/commit/e2705ded8656ab6fee6038e325a2ab61cd48bdfe))
* **log:** decouple log control into Error Log, Access Log, and DNS Log, adjust log buffer limits and align help dialog text ([72ac402](https://github.com/ReRokutosei/SimpleXray/commit/72ac402575295477901bb36b7c1420c547a9722e))
* **log:** support long-press text selection and one-tap log clearing ([0f6e448](https://github.com/ReRokutosei/SimpleXray/commit/0f6e44841173ec68b54e86e0fcc4060a86c55fec))
* Migrate to single Activity architecture ([96e5ea8](https://github.com/ReRokutosei/SimpleXray/commit/96e5ea8499a94bded386fd2e3ed542660b1a359a))
* Move file I/O to background threads ([2543c45](https://github.com/ReRokutosei/SimpleXray/commit/2543c45afe76e2b622218217c85b39be7d5e5bf4))
* Prevent deleting active config ([8a61c6e](https://github.com/ReRokutosei/SimpleXray/commit/8a61c6e8d2c373b8f3a9bc43b55d6aab9310ec63))
* QS Tile long press to MainActivity ([13cac12](https://github.com/ReRokutosei/SimpleXray/commit/13cac12f32801371c6b3509ad31d2b616a6a4ac8))
* randomize xray api address ([2f1f55c](https://github.com/ReRokutosei/SimpleXray/commit/2f1f55c62a438676e992ee85247172e3561baced))
* Refine NDK Build Configuration ([52f906b](https://github.com/ReRokutosei/SimpleXray/commit/52f906ba72bcc24c6f3bca7748fb16c80d446089))
* Reintroduce scrollbar to Compose screens ([19c2797](https://github.com/ReRokutosei/SimpleXray/commit/19c2797d6b50f257596ec74ec2fe3dedf9604229))
* **rule-files:** optimize third-party dat import & fix GEO update crash ([13681c9](https://github.com/ReRokutosei/SimpleXray/commit/13681c906a7b8321438707b1e539b56439c26a3e))
* **rules:** add geo rule files scheduled auto-update setting ([69d7359](https://github.com/ReRokutosei/SimpleXray/commit/69d7359ef2318670ca1eba57ee9697dca3225cbf))
* **security:** add geo dat shadow sandbox validation before overwriting rule files ([695ecdd](https://github.com/ReRokutosei/SimpleXray/commit/695ecdd0937ca072150a143638088bba5e719036))
* **service:** add keep CPU awake option with partial wake lock management ([8a16684](https://github.com/ReRokutosei/SimpleXray/commit/8a16684fec81fd7919422327aa0f4edfc8c38ed0))
* **service:** enhance geo rule auto-update with timeout catch-up and status display ([5abdf75](https://github.com/ReRokutosei/SimpleXray/commit/5abdf7550d8cbff46c5cd036c57826bc2546795a))
* **settings-screen:** Add right-side icons to interactive items ([ccf8ac2](https://github.com/ReRokutosei/SimpleXray/commit/ccf8ac200a0ffd78ff653d887d608043f8b6e0d6))
* **settings:** add configurable LogLevel with single-direction AST injection and UI selection ([3a41fc0](https://github.com/ReRokutosei/SimpleXray/commit/3a41fc0be6dcc4a25958e50feac43134b9b071ba))
* **settings:** add toggle for hiding app from Android recent tasks and fix UI toasts ([0406269](https://github.com/ReRokutosei/SimpleXray/commit/0406269cc3556af24d7f0e176074b25397fb6b5d))
* **settings:** allow customizable MTU with range validation (1280-9000) ([d2bd8ab](https://github.com/ReRokutosei/SimpleXray/commit/d2bd8ab00bc94902b8e7c03ea265b43fc4bea2a8))
* **simpletun:** add adaptive high-watermark flow recycling ([12547b0](https://github.com/ReRokutosei/SimpleXray/commit/12547b003fd1ae153be8ab3d67a7c2cf3070c65a))
* **simpletun:** add lightweight ipv4 tun-to-socks5 backend ([bc75eb8](https://github.com/ReRokutosei/SimpleXray/commit/bc75eb8f60c95326d8a1c4062961cd6118ea9266))
* Template LAN direct connect ([59772d3](https://github.com/ReRokutosei/SimpleXray/commit/59772d3725f7f2fda1e5842d65df417101b3a59f))
* **tun:** add MipsTUN backend powered by Mihomo mipstack ([c150edb](https://github.com/ReRokutosei/SimpleXray/commit/c150edb67f322040794584f520435737b6a092e2))
* **tun:** add native Xray TUN mode ([a56e39a](https://github.com/ReRokutosei/SimpleXray/commit/a56e39a99e7da3061d6a80f28f96188e9d98faaf))
* **tun:** add SingTUN backend powered by sing-tun Go stack ([830cc03](https://github.com/ReRokutosei/SimpleXray/commit/830cc035bedba17184c8d36cf81163acc56883fa))
* **tun:** add ZepTUN backend powered by Noisemux ([17b46fb](https://github.com/ReRokutosei/SimpleXray/commit/17b46fbe55c321770e9f4cfa6bcf03a2d70a4809))
* **tunnel:** add selectable Xray TUN and Hev tunnel modes ([482e8be](https://github.com/ReRokutosei/SimpleXray/commit/482e8be2865e58a2d08134f1ed758a0b8e729c92))
* **ui:** adapt config action icons to theme colors ([c7071a9](https://github.com/ReRokutosei/SimpleXray/commit/c7071a94bc73fd4168bb293e282f8e146ed821d2))
* **ui:** add empty state actions, plus card, system BackHandler, flash/refresh action, and i18n ([dac98f3](https://github.com/ReRokutosei/SimpleXray/commit/dac98f3dc23d0809c0e54919bea190b037c32dbd))
* **ui:** add gentle notification permission explanation dialog for Android 13+ ([f329efb](https://github.com/ReRokutosei/SimpleXray/commit/f329efb7ce91e44bbd00259750f76c25741790c0))
* **ui:** add liquid glass effect to floating navigation bar ([02e9611](https://github.com/ReRokutosei/SimpleXray/commit/02e961135f683f13ae17e2ff2177a07680875019))
* **ui:** add Origin icon as default, recents icon follows selection, lineal notification icon ([25d597c](https://github.com/ReRokutosei/SimpleXray/commit/25d597c15d487c9714a133978ba3a97a9a248324))
* **ui:** add responsive Miuix NavigationRail for wide screen and tablet layout ([3af6f27](https://github.com/ReRokutosei/SimpleXray/commit/3af6f2771e632c04ad413091a8450368ba17a232))
* **ui:** add runtime-switchable app icons ([d726681](https://github.com/ReRokutosei/SimpleXray/commit/d7266812dd1addc8277cdef70e9b01c6d61967aa))
* **ui:** extract log actions to top bar icons, remove settings backup/restore, enable Monet dynamic colors on Android 12+ ([95adf81](https://github.com/ReRokutosei/SimpleXray/commit/95adf81e84247a60cdff07bc0571ef59c8d243d7))
* **ui:** hide log page when log level is none ([35e8370](https://github.com/ReRokutosei/SimpleXray/commit/35e837011cf4bbe4d1fb4b48b28e75a53cfcfe01))
* **ui:** implement fully penetrating floating navigation bar visual effect ([381fd9d](https://github.com/ReRokutosei/SimpleXray/commit/381fd9d6898c1bf38f623a9c7350b0ef7e3070ea))
* **ui:** implement Master-Detail split view for ConfigScreen on wide screens ([feb5931](https://github.com/ReRokutosei/SimpleXray/commit/feb59310caaa1f3e845538dbdd47387254296be3))
* **ui:** implement wide-screen responsive grid and container width constraints ([805999d](https://github.com/ReRokutosei/SimpleXray/commit/805999df3323e6081d7f11d19bb07dcbc84b73bc))
* **ui:** make Core Control container transparent and add show no-internet apps filter ([67adbf6](https://github.com/ReRokutosei/SimpleXray/commit/67adbf6a3f40d6a64d63e0ee380f3cdf33d21d16))
* **ui:** move app icon picker into General section as dropdown ([c8840f1](https://github.com/ReRokutosei/SimpleXray/commit/c8840f19b293d5e68b564d215b6992a225f21681))
* **ui:** refine master-detail breakpoint, remove embedded scaffold gap, and add fullscreen editor toggle ([4920200](https://github.com/ReRokutosei/SimpleXray/commit/492020021d5515635d86374fcc616dc80d697c37))
* **ui:** refine TopAppBar layout, SnackbarHost binding, and config list tags ([a6b2d68](https://github.com/ReRokutosei/SimpleXray/commit/a6b2d6862b205a448af68d6dd9e540c788f481e8))
* **ui:** remove unused backup/restore codebase, replace config editor 3-dots menu with share icon, and Pangu-ify values-zh strings ([aec7611](https://github.com/ReRokutosei/SimpleXray/commit/aec76117acf2637ce055a4f44c220219135d789f))
* Update app icon with adaptive support ([545de13](https://github.com/ReRokutosei/SimpleXray/commit/545de13f515244801567f110655a61eb18495cd8))
* Update TV banner icon ([9b3f477](https://github.com/ReRokutosei/SimpleXray/commit/9b3f47760da5c6139828a4f3a8e80e16df5352d3))
* Update Xray-core download workflow ([0e2979a](https://github.com/ReRokutosei/SimpleXray/commit/0e2979adc8e377aacbbc59b82fdb24270e40b6a2))
* **vpn:** update underlying networks via NetworkCallback during network handover ([2b5dfc3](https://github.com/ReRokutosei/SimpleXray/commit/2b5dfc3b1ffe1b0dbc9ffd4cd96099dccf6443f6))


### Bug Fixes

* Add googleapis.cn for Play Store downloads ([240bf88](https://github.com/ReRokutosei/SimpleXray/commit/240bf887765b40d65ae1c46451af323eb74da063))
* Add HTTPS support to connectivity test ([2a5a850](https://github.com/ReRokutosei/SimpleXray/commit/2a5a850e5827a720c3c25e04403aa33631f8dfc6))
* Add notif channel for xrayOnly mode ([bed794a](https://github.com/ReRokutosei/SimpleXray/commit/bed794a970e324cf2e9fc76f7a75a3b29c376444))
* Address panel invalidation after core restart ([beacb98](https://github.com/ReRokutosei/SimpleXray/commit/beacb981d5624c1f2c9ed1693d7de0aea4c8e9be))
* App list RecyclerView scrollbar positioning ([72c9c49](https://github.com/ReRokutosei/SimpleXray/commit/72c9c49cb5c26725a26767f880d0ede8dcb9549b))
* **arch:** decouple child ViewModels from MainViewModel to prevent restoration crash ([83688d4](https://github.com/ReRokutosei/SimpleXray/commit/83688d4a73cb2c3eddd547de8ecd8c64d02a2e8f))
* Autosave per-app proxy list changes ([be99af6](https://github.com/ReRokutosei/SimpleXray/commit/be99af6fd15db24458effdb4c7c373d4810d3b30))
* **build:** make prerelease version codes monotonic ([20eb216](https://github.com/ReRokutosei/SimpleXray/commit/20eb2168ec9ed2f188e50c745be3fb57bbd03222))
* **build:** resolve missing material icons class in release build ([5a8bb96](https://github.com/ReRokutosei/SimpleXray/commit/5a8bb96b83b0d4677ba101a8471555c037a2ee62))
* **config:** add placeholder proxy outbound to template to match routing rules ([041b782](https://github.com/ReRokutosei/SimpleXray/commit/041b782941ee1f62ee3f3f7f474bee1a860eaf04))
* **config:** align EFFECTIVE_MATCH_KEYS with Xray RawFieldRule and migrate legacy geosite/geoip ([dd0fc48](https://github.com/ReRokutosei/SimpleXray/commit/dd0fc48fab6d8441b9d5adda1bb4c9d3af917e24))
* **config:** do not switch config on import while service is running ([916cae4](https://github.com/ReRokutosei/SimpleXray/commit/916cae47f9dfb50a77549f40b37f3cc2951876b5))
* **config:** implement smart inbound sanitization filtering desktop tun and converting listen addresses ([8bbaa56](https://github.com/ReRokutosei/SimpleXray/commit/8bbaa566dfcdaecb6d0e37de14b129dcdd4905f1))
* **config:** shorten outbound transport timeouts ([c223eee](https://github.com/ReRokutosei/SimpleXray/commit/c223eee83873ee62d64ecc4e8806d36deaece96b))
* **config:** support flat outbound endpoints, hysteria naming, and primary socks inbound targeting ([9883c1d](https://github.com/ReRokutosei/SimpleXray/commit/9883c1d5ede12b2d5d9cd1b5203953cdeeba673f))
* **core:** inject sniffing block into TUN inbound to restore FakeDNS and domain routing ([ece49e1](https://github.com/ReRokutosei/SimpleXray/commit/ece49e17adcd5ba0e86ffc1144904a20d7b04e05))
* **core:** integrate v2rayNG start locks, settling delay & robust rule block sanitizer ([5ed0e81](https://github.com/ReRokutosei/SimpleXray/commit/5ed0e81a8b4d7695dc080d339233aa6930a4887e))
* **core:** remove PR_SET_PDEATHSIG and reap native child processes via waitpid ([ab31518](https://github.com/ReRokutosei/SimpleXray/commit/ab3151893130bbdfbb1e148f06dc84600b1b9099))
* Correct log display for short content ([b0c6999](https://github.com/ReRokutosei/SimpleXray/commit/b0c6999b12b71e1cb57bf4c5c3716e06e78c6b82))
* Correct start/stop button text update ([ecf2aa6](https://github.com/ReRokutosei/SimpleXray/commit/ecf2aa697a0430471d66dd0fbdda523aeadc7a34))
* Correct string resource for rule file feature ([1efa462](https://github.com/ReRokutosei/SimpleXray/commit/1efa462b44f884065c507c37b7ae874c52dedb32))
* Correct timeout input error message on settings page ([4fbcf3d](https://github.com/ReRokutosei/SimpleXray/commit/4fbcf3de903a785e79198e83d9ad312f25393d7d))
* Correct uptime hours overflow ([56c0642](https://github.com/ReRokutosei/SimpleXray/commit/56c064247e89325fa906d05f2110f09725a8642f))
* **crash:** use 2-arg TaskDescription, drop theme-resolved colorPrimary (Android 16 opaque crash) ([6be66dd](https://github.com/ReRokutosei/SimpleXray/commit/6be66ddfe6bad9f71d17d22a33620089fc8b53c1))
* **data:** strictly validate file extensions on config and rule imports ([3c0c960](https://github.com/ReRokutosei/SimpleXray/commit/3c0c960bf11e70e342e68c517aa2610abf73d7e9))
* Debounce navigation ([7a5fe9b](https://github.com/ReRokutosei/SimpleXray/commit/7a5fe9bd9ebaceae40841962744939aaaf3cef96))
* **deps:** update agp to v9.4.1 ([#39](https://github.com/ReRokutosei/SimpleXray/issues/39)) ([37a4fc7](https://github.com/ReRokutosei/SimpleXray/commit/37a4fc7572f7c8a31336d96e102cae64f9b1d577))
* **deps:** update dependency androidx.compose:compose-bom to v2025.12.01 ([#6](https://github.com/ReRokutosei/SimpleXray/issues/6)) ([de463ee](https://github.com/ReRokutosei/SimpleXray/commit/de463ee9b21304de072a292d43c2aba1bae20755))
* **deps:** update dependency androidx.compose:compose-bom to v2026 ([#16](https://github.com/ReRokutosei/SimpleXray/issues/16)) ([47c341a](https://github.com/ReRokutosei/SimpleXray/commit/47c341aae86990389f31318f2172a6af1b57ee94))
* **deps:** update dependency androidx.compose:compose-bom to v2026.09.00 ([#35](https://github.com/ReRokutosei/SimpleXray/issues/35)) ([75b07cb](https://github.com/ReRokutosei/SimpleXray/commit/75b07cbdc5b096dbd3a7f4e251aedc0dcf4aaa91))
* **deps:** update dependency androidx.core:core-ktx to v1.19.0 ([#8](https://github.com/ReRokutosei/SimpleXray/issues/8)) ([e854fa6](https://github.com/ReRokutosei/SimpleXray/commit/e854fa6a5efaef8c2b677f5686b13ab7ae780045))
* **deps:** update dependency androidx.core:core-ktx to v1.19.1 ([#40](https://github.com/ReRokutosei/SimpleXray/issues/40)) ([150fc17](https://github.com/ReRokutosei/SimpleXray/commit/150fc170ba5df7560ebd797e9b61b7797065a245))
* **deps:** update dependency androidx.datastore:datastore-preferences to v1.2.1 ([#9](https://github.com/ReRokutosei/SimpleXray/issues/9)) ([61acf1b](https://github.com/ReRokutosei/SimpleXray/commit/61acf1bac71357737d1c16521a8f76cfe47400a7))
* **deps:** update dependency androidx.navigation:navigation-compose-android to v2.10.0 ([#26](https://github.com/ReRokutosei/SimpleXray/issues/26)) ([fa97482](https://github.com/ReRokutosei/SimpleXray/commit/fa97482b3903386ea8b850e9704ba4ec3091c9f9))
* **deps:** update dependency androidx.navigation:navigation-compose-android to v2.10.1 ([#34](https://github.com/ReRokutosei/SimpleXray/issues/34)) ([a2f1f8f](https://github.com/ReRokutosei/SimpleXray/commit/a2f1f8fb9aa09f9aab74c32abe255b0a739758bb))
* **deps:** update dependency androidx.navigation:navigation-compose-android to v2.10.2 ([#41](https://github.com/ReRokutosei/SimpleXray/issues/41)) ([9f6be64](https://github.com/ReRokutosei/SimpleXray/commit/9f6be64d9be4498198f30a9a9cd6acda40a21d93))
* **deps:** update dependency androidx.navigation:navigation-compose-android to v2.9.8 ([#1](https://github.com/ReRokutosei/SimpleXray/issues/1)) ([80d838a](https://github.com/ReRokutosei/SimpleXray/commit/80d838ad3b52098bee10ed92abe953221c968170))
* **deps:** update dependency androidx.test:runner to v1.7.0 ([#10](https://github.com/ReRokutosei/SimpleXray/issues/10)) ([59cc85d](https://github.com/ReRokutosei/SimpleXray/commit/59cc85df419e1b26353b43c5bdfe662f817bcc04))
* **deps:** update dependency androidx.test.ext:junit to v1.3.0 ([#11](https://github.com/ReRokutosei/SimpleXray/issues/11)) ([bc059fc](https://github.com/ReRokutosei/SimpleXray/commit/bc059fc7f107bb9b23f2505da6e50c8786092199))
* **deps:** update dependency androidx.work:work-runtime-ktx to v2.11.2 ([#23](https://github.com/ReRokutosei/SimpleXray/issues/23)) ([8b54421](https://github.com/ReRokutosei/SimpleXray/commit/8b544211d40daa75ebabcf3fc595c46da228cc88))
* **deps:** update dependency androidx.work:work-runtime-ktx to v2.12.0 ([#42](https://github.com/ReRokutosei/SimpleXray/issues/42)) ([4b0b855](https://github.com/ReRokutosei/SimpleXray/commit/4b0b855a28191bb1dac0dc6cd58ac08fdb7ab684))
* **deps:** update dependency com.github.nanihadesuka:lazycolumnscrollbar to v3 ([#37](https://github.com/ReRokutosei/SimpleXray/issues/37)) ([8883cad](https://github.com/ReRokutosei/SimpleXray/commit/8883cadcbf62553df551451f51f89d46158734c3))
* **deps:** update dependency com.google.android.material:material to v1.14.0 ([#19](https://github.com/ReRokutosei/SimpleXray/issues/19)) ([8668178](https://github.com/ReRokutosei/SimpleXray/commit/86681789ddbc04fb0dbc25987917a32f38b035a1))
* **deps:** update dependency com.google.protobuf:protoc to v3.25.9 ([#2](https://github.com/ReRokutosei/SimpleXray/issues/2)) ([252b733](https://github.com/ReRokutosei/SimpleXray/commit/252b7331ffc1ea811d6471b28a4ade82fdf906b3))
* **deps:** update dependency com.squareup.okhttp3:okhttp to v5 ([#18](https://github.com/ReRokutosei/SimpleXray/issues/18)) ([dab75f6](https://github.com/ReRokutosei/SimpleXray/commit/dab75f6dceb72f6f2bfede581bc36dbd1ca9016a))
* **deps:** update dependency com.squareup.okhttp3:okhttp to v5.5.0 ([#20](https://github.com/ReRokutosei/SimpleXray/issues/20)) ([d5c118f](https://github.com/ReRokutosei/SimpleXray/commit/d5c118f68a312396bfaaa81a9f5f23ee73bb8b08))
* **deps:** update dependency io.grpc:grpc-kotlin-stub to v1.5.0 ([#12](https://github.com/ReRokutosei/SimpleXray/issues/12)) ([a082697](https://github.com/ReRokutosei/SimpleXray/commit/a0826975f94c7382a4108da0439d6f666a5fb483))
* **deps:** update dependency org.jetbrains.kotlinx:kotlinx-serialization-json to v1.11.0 ([#13](https://github.com/ReRokutosei/SimpleXray/issues/13)) ([30bd5a0](https://github.com/ReRokutosei/SimpleXray/commit/30bd5a0312fa063a1a892c43cf51bb83f6b856e3))
* **deps:** update dependency org.yaml:snakeyaml to v2.6 ([#14](https://github.com/ReRokutosei/SimpleXray/issues/14)) ([611d4fe](https://github.com/ReRokutosei/SimpleXray/commit/611d4fe35754c7b293115184ce562833f375cf20))
* **deps:** update dependency org.yaml:snakeyaml to v2.7 ([#28](https://github.com/ReRokutosei/SimpleXray/issues/28)) ([c92a137](https://github.com/ReRokutosei/SimpleXray/commit/c92a137c2da55314a1b0f0f6151026299df05704))
* **deps:** update grpc-java monorepo to v1.83.1 ([#15](https://github.com/ReRokutosei/SimpleXray/issues/15)) ([d3ea103](https://github.com/ReRokutosei/SimpleXray/commit/d3ea1037ea94d721ea767077bf8add8226754f98))
* **deps:** update grpc-java monorepo to v1.84.0 ([#31](https://github.com/ReRokutosei/SimpleXray/issues/31)) ([97cddaa](https://github.com/ReRokutosei/SimpleXray/commit/97cddaa02a065220b6962af1821532a2033b8364))
* **deps:** update kotlin monorepo to v2.4.20 ([#33](https://github.com/ReRokutosei/SimpleXray/issues/33)) ([856e698](https://github.com/ReRokutosei/SimpleXray/commit/856e69885309d94cd40288cae95ca7d1e69d4904))
* **deps:** update protobuf monorepo to v4.36.0 ([#22](https://github.com/ReRokutosei/SimpleXray/issues/22)) ([3dd77fa](https://github.com/ReRokutosei/SimpleXray/commit/3dd77fa0da5cb49d13e758e63474bc6e4546f021))
* **deps:** update protobuf monorepo to v4.36.1 ([#29](https://github.com/ReRokutosei/SimpleXray/issues/29)) ([500452f](https://github.com/ReRokutosei/SimpleXray/commit/500452f67059d6935d1c0c3ac30739c7f2886c24))
* **deps:** update protobuf monorepo to v4.36.2 ([#38](https://github.com/ReRokutosei/SimpleXray/issues/38)) ([637fd50](https://github.com/ReRokutosei/SimpleXray/commit/637fd50fbbfbb445ade4c739d19be3f472988357))
* Eliminate UI jank caused by synchronous I/O on main thread ([c92272c](https://github.com/ReRokutosei/SimpleXray/commit/c92272c6a127ec7d4e2bc096ecf96fc96e86f78c))
* Enable config hot-reload when VPN interface is disabled ([bedadfa](https://github.com/ReRokutosei/SimpleXray/commit/bedadfae0c5d9cb03d55eab8b3edd6af4d78d86b))
* Ensure bottom navigation color matches toolbar ([fd6019a](https://github.com/ReRokutosei/SimpleXray/commit/fd6019a32e176e72d316738731440729af925355))
* Ensure FileOutputStream is closed during log truncation ([8a89d2e](https://github.com/ReRokutosei/SimpleXray/commit/8a89d2ec442e1c3a6530df1a319bf377734f0481))
* Ensure TextField content correctly syncs with external state updates ([45d6178](https://github.com/ReRokutosei/SimpleXray/commit/45d61785973eb1fbf28be3e7bcfb0cfdea4cd26c))
* **i18n:** localize Dashboard titles and dynamically resolve default rule file summary ([ea6fb5f](https://github.com/ReRokutosei/SimpleXray/commit/ea6fb5f2abe142f3a86a03a22c959f87b3862243))
* **i18n:** refine bilingual UI strings ([d7cc025](https://github.com/ReRokutosei/SimpleXray/commit/d7cc02543a10b7061d861b0cc7d8453aabef3e05))
* **import:** sanitize file names from content URIs ([e3f298b](https://github.com/ReRokutosei/SimpleXray/commit/e3f298ba96d8744516c8b272fa1df5b7b284b109))
* Improve bracket highlight visibility ([2e55d9c](https://github.com/ReRokutosei/SimpleXray/commit/2e55d9c48e7c41cd7fff8d98476b5ccd9e293060))
* Improve Settings screen performance ([ee1e4f7](https://github.com/ReRokutosei/SimpleXray/commit/ee1e4f72aad5d16a694961f3f19f0f65df0527b3))
* Improved status bar immersion with LinearLayout insets ([4a61fe2](https://github.com/ReRokutosei/SimpleXray/commit/4a61fe22356c4782823a662c9549b48a1f55a907))
* Include bypassSelectedApps in backup and restore ([4655e69](https://github.com/ReRokutosei/SimpleXray/commit/4655e699e875960b672521fae392532c216e1aa2))
* Increase Gradle JVM heap space in workflow ([539cfa3](https://github.com/ReRokutosei/SimpleXray/commit/539cfa339d0be60790d14787992e40019b2f324b))
* Intermittent Top App Bar color ([341c420](https://github.com/ReRokutosei/SimpleXray/commit/341c420733e62a5a47a7816cd8efb467c57fc050))
* **io:** prevent OkHttp response leak in GeoUpdateWorker and avoid fragile available() in FileManager ([0176876](https://github.com/ReRokutosei/SimpleXray/commit/017687619383ee7bbc29c6fd10e483b5cf2a9aea))
* **jni:** fix PKGNAME macro, JNI signature return types and add Proguard keep rules for HevSocks5Tunnel ([e4c27e5](https://github.com/ReRokutosei/SimpleXray/commit/e4c27e58e1a5efd69027eab3110e280d74e394a9))
* **jni:** use pipe2 with O_CLOEXEC to prevent fd leakage ([303d1d3](https://github.com/ReRokutosei/SimpleXray/commit/303d1d359b13d157cb3831ca0c8eca0d7ae0437f))
* **lifecycle:** cancel only xrayJob in stopXray to prevent serviceScope invalidation on reconnect ([39cad0f](https://github.com/ReRokutosei/SimpleXray/commit/39cad0fb973c234f1f1bcd5cba4a9982780630ad))
* **lifecycle:** prevent activity leak in MainActivity and ensure full resource cleanup in TProxyService.onDestroy ([7edd1d5](https://github.com/ReRokutosei/SimpleXray/commit/7edd1d58729fe982bdd09521487fce285f56dca6))
* **log:** synchronize LogFileManager operations across instances via shared FILE_LOCK ([079dac0](https://github.com/ReRokutosei/SimpleXray/commit/079dac02ce576a2fdb570dd6bf492c16372d74cf))
* **memory:** decouple SOCKS Authenticator from MainViewModel to prevent static leak ([186137f](https://github.com/ReRokutosei/SimpleXray/commit/186137f3562989617e060cd16bf733a511ba0301))
* **native:** harden xray subprocess lifecycle with pdeathsig and cloexec clearance ([62493d1](https://github.com/ReRokutosei/SimpleXray/commit/62493d10eeaf2323b0b16f095b9ca19623f4621d))
* **native:** keep xray spawn async-signal-safe ([515011c](https://github.com/ReRokutosei/SimpleXray/commit/515011cb9772c120b486ca751ade43a211011e70))
* **net:** add DNS resolution timeout protection to TcpPing and guard outbound latency probes ([3340d17](https://github.com/ReRokutosei/SimpleXray/commit/3340d1736ada1998fc0c81244c305f9e399ab36f))
* **net:** correct inverted bypassLan routing and link with geoip:private direct rule ([6569155](https://github.com/ReRokutosei/SimpleXray/commit/6569155ec09e664abee907b0385a987bdabbc6da))
* **network:** disable default HTTP proxy, add independent 10809 HTTP port and update UI strings ([c56a265](https://github.com/ReRokutosei/SimpleXray/commit/c56a265e6169a4aeca235a2936a62c2ffa6e0a81))
* **perf:** defer config file order persistence until drag gesture completion ([de7032f](https://github.com/ReRokutosei/SimpleXray/commit/de7032f979dc908d256e1a135aa5f6b515b78291))
* **perf:** offload app list package loading to IO dispatcher and serialize save operations ([40071f3](https://github.com/ReRokutosei/SimpleXray/commit/40071f304a7bf452768afd89ffa19de42043427d))
* Persist selected config file after rename ([a1d1b1f](https://github.com/ReRokutosei/SimpleXray/commit/a1d1b1f5973514e100cd9c04122537795dc39296))
* **prefs:** persist keepAwake across cold restarts and update state properly ([6732414](https://github.com/ReRokutosei/SimpleXray/commit/67324143d88debbdf1fba59f156579b79eb8e7ac))
* Preserve 'none' value for log.access and log.error in config ([133db5e](https://github.com/ReRokutosei/SimpleXray/commit/133db5e17a720624807375fd5dbb0741c626a070))
* Prevent app list data loss when filtering system apps ([eba64e8](https://github.com/ReRokutosei/SimpleXray/commit/eba64e8e867282d5bf25294250ab062410a8eadb))
* Prevent crashes from fragment management ([1df7b90](https://github.com/ReRokutosei/SimpleXray/commit/1df7b9093c7ec836c8828d4cc180e8739d14a071))
* Prevent duplicate file import on configuration changes ([2051bff](https://github.com/ReRokutosei/SimpleXray/commit/2051bff2915a5a55f2be82baafdd3efd77165e8e))
* Prevent extra newline in config files during backup ([92f569f](https://github.com/ReRokutosei/SimpleXray/commit/92f569fe68b9a52be73c3db4286054bad7c09ae3))
* Prevent failure toast on cancelled file selection ([240b229](https://github.com/ReRokutosei/SimpleXray/commit/240b2293506c54fa25abf6feed7e1b394a2b6d47))
* **provider:** narrow exported file provider path ([c5c7efc](https://github.com/ReRokutosei/SimpleXray/commit/c5c7efcc467fe265b4ebc9a127198a715888b710))
* **proxy:** prune uninstalled app packages from per-app proxy list ([322963c](https://github.com/ReRokutosei/SimpleXray/commit/322963c77aaae0ec7c898da7d3686bc6f886253b))
* Refine UI state and menu management ([3f1d710](https://github.com/ReRokutosei/SimpleXray/commit/3f1d710fe37e552fe60509206f90718409a0f652))
* Refresh app list on permission grant ([802baf2](https://github.com/ReRokutosei/SimpleXray/commit/802baf2aa8eaeeca70cf7f9f032cf07140697f03))
* **release:** strip leading v in versionCode parsing and add Breaking Changes section to release notes ([b7b4097](https://github.com/ReRokutosei/SimpleXray/commit/b7b4097dce347a9fcf94ed199ac2cfce0d1420a4))
* Reload empty app list on resume ([ba03f18](https://github.com/ReRokutosei/SimpleXray/commit/ba03f18b49e660328828362e68549f0bbc47fa40))
* Remove ineffective VPN permission redirect ([4905199](https://github.com/ReRokutosei/SimpleXray/commit/4905199f3234568c0f3436b8dc2f47a73a357b43))
* remove redundant elvis operators on non-nullable ResponseBody ([9785a8f](https://github.com/ReRokutosei/SimpleXray/commit/9785a8fecb36b94acf3c55c947b6c04bc409ac40))
* remove unescaped quotes in strings.xml ([6141f60](https://github.com/ReRokutosei/SimpleXray/commit/6141f606eda9199942adae55a5af1046dc60af7e))
* Resolve dropdown menu and snackbar animation conflict ([026eee7](https://github.com/ReRokutosei/SimpleXray/commit/026eee7905addb9c5a9c83d436dbf84de552925b))
* Resolve incorrect theme mode application ([c06f138](https://github.com/ReRokutosei/SimpleXray/commit/c06f138a9d172304cf753f0afbe41a51788fba71))
* Resolve JNI libs packaging conflict ([6a67be2](https://github.com/ReRokutosei/SimpleXray/commit/6a67be2a77d488843e54c3bffffcfb3694215ce3))
* Restore http proxy enabled to true after Kotlin migration ([b74e0a4](https://github.com/ReRokutosei/SimpleXray/commit/b74e0a4bf077c0ab915eebc12f3e095286bcb57a))
* **security:** disable Android auto backup ([84bb6a9](https://github.com/ReRokutosei/SimpleXray/commit/84bb6a967766fc6b9a611e97e8e9e3ed2f6e1b14))
* **security:** replace xray sandbox validation with lightweight sanity check for dat files ([c02e8d6](https://github.com/ReRokutosei/SimpleXray/commit/c02e8d6eb96da642261cca91f4b42900d2921a82))
* **security:** skip private-address pings, redact config logs ([68a2780](https://github.com/ReRokutosei/SimpleXray/commit/68a2780521451f81f416d4a2b8e34d8c0eaa445f))
* **service:** avoid recursive hev backend start ([3b0dd6f](https://github.com/ReRokutosei/SimpleXray/commit/3b0dd6f627fe2823ab64bd5bc9c82c6262167074))
* **service:** finalize failed startup state ([e49fb1c](https://github.com/ReRokutosei/SimpleXray/commit/e49fb1cb10a7b44ace12fb4e9c0b35e28d4d8e05))
* **service:** gRPC-based startup detection (works with loglevel none); drop connectivity test; dynamic provider authorities ([bd83738](https://github.com/ReRokutosei/SimpleXray/commit/bd83738a3b459673eea325076a325dae4bd0dacf))
* **service:** harden network and backend transitions ([1012166](https://github.com/ReRokutosei/SimpleXray/commit/10121664ef0e05aad97a6f86db197e8320fbd117))
* **service:** quiet expected xray log interruption ([d0f23f4](https://github.com/ReRokutosei/SimpleXray/commit/d0f23f4d979528bc8b74685188ce161a1f6ba10b))
* **service:** recover lost Xray SOCKS listener ([10fd293](https://github.com/ReRokutosei/SimpleXray/commit/10fd293e632daa9793637df407291e893190e930))
* **service:** replace nativeSpawnXray with ProcessBuilder to eliminate TUN socket deadlocks ([0650335](https://github.com/ReRokutosei/SimpleXray/commit/0650335cebd6d01abb8dd8756667b92868f63980))
* **service:** restore TCP API and preserve logs ([467a2c4](https://github.com/ReRokutosei/SimpleXray/commit/467a2c4239a7e4131578e48b8c3d5e99b1f8408d))
* **service:** retry xray start once and stop on repeated failure ([1a9269d](https://github.com/ReRokutosei/SimpleXray/commit/1a9269d3958277cae8e55c0c39b2589ba6df0471))
* **service:** serialize Xray startup ([42e73d7](https://github.com/ReRokutosei/SimpleXray/commit/42e73d7fa933ebc6deee53e699c47b3210a32da1))
* **service:** stamp xray logs with device-local time ([8d61ea3](https://github.com/ReRokutosei/SimpleXray/commit/8d61ea3835fe9cd7977b1edbbc2fb49888bbd8e5))
* **service:** stop VPN when task removed ([139afb1](https://github.com/ReRokutosei/SimpleXray/commit/139afb183b6308ed166bcb93bfaaf07cd75d61a6))
* **service:** synchronize native backend start with lifecycle lock ([c03d795](https://github.com/ReRokutosei/SimpleXray/commit/c03d79588ce7dbef152f161d56e05b6718ae034e))
* **service:** use bitmap webp for notification small icon ([0bce562](https://github.com/ReRokutosei/SimpleXray/commit/0bce562f67d4cfcac3324c9bb6d05256a07f70c6))
* **service:** use startService instead of startForegroundService on ACTION_DISCONNECT in BenchmarkReceiver ([96cb145](https://github.com/ReRokutosei/SimpleXray/commit/96cb145276a813ecf38a6beb639431db43e4e45e))
* **settings:** real-time update custom dat file list on import/delete/download ([af70cde](https://github.com/ReRokutosei/SimpleXray/commit/af70cde5bc0f1dbfdd046ae176312d8b4723c6e5))
* **simpletun:** check epoll_wait errno instead of unsigned comparison ([2892c49](https://github.com/ReRokutosei/SimpleXray/commit/2892c499c429d077a9aca39a627042a51d8390a5))
* **simpletun:** consume payload before upstream FIN ([26688f7](https://github.com/ReRokutosei/SimpleXray/commit/26688f750276b7b58ab9dfc06f3203e0bf4a0695))
* **simpletun:** handle TUN and UDP send failures ([9875a1c](https://github.com/ReRokutosei/SimpleXray/commit/9875a1c274c4a9b46bb7273f318da1921d0db279))
* **simpletun:** map UDP replies to originating client ([b29b588](https://github.com/ReRokutosei/SimpleXray/commit/b29b5888ec92ddb8f233e4fdc36ad7a41521e144))
* **simpletun:** reset evicted active TCP flows ([ed1596b](https://github.com/ReRokutosei/SimpleXray/commit/ed1596b475c244507c6cb6df4f197bb09b583033))
* **sing-tun:** upgrade to upstream pure user-space Go stack and fix Android TUN subnet prefix ([c0e8fdc](https://github.com/ReRokutosei/SimpleXray/commit/c0e8fdc3b8be87edaf3fc17d8fb24ff077c5a4e2))
* skip apk signing on pull request ([c9da785](https://github.com/ReRokutosei/SimpleXray/commit/c9da78551fbc66eac4bf420796c613ed22ce69fe))
* **tun:** avoid Android interface enumeration ([6951518](https://github.com/ReRokutosei/SimpleXray/commit/69515181985ec66219ee882088de000df9cf5cc0))
* **tun:** harden native reload and MTU handling ([69b1cb8](https://github.com/ReRokutosei/SimpleXray/commit/69b1cb8fee9c8c4b7edc956e7fbc6651330a8566))
* **tun:** isolate Go backends in worker processes ([264260f](https://github.com/ReRokutosei/SimpleXray/commit/264260f08462c48926da6af749986c4a764b3c09))
* **tunnel:** resolve SOCKS5 UDP relay packet drop and Android 14 foreground launch ([303fad7](https://github.com/ReRokutosei/SimpleXray/commit/303fad7ed5353b9082967ff188a2c2be379323cb))
* Typo in error message ([c6d8d8e](https://github.com/ReRokutosei/SimpleXray/commit/c6d8d8ef8e8512bb97a82c2a47b026f8316cde32))
* UI/functionality issues on config changes ([c3dcfb1](https://github.com/ReRokutosei/SimpleXray/commit/c3dcfb1080782873d3f8b5150bb5b1f46116243a))
* **ui:** add bottom spacing to core control card on dashboard ([907635c](https://github.com/ReRokutosei/SimpleXray/commit/907635c74e4b1f5931b73878c3a7a8070cf4902c))
* **ui:** keep config editor snackbar above system nav bar ([34dd43d](https://github.com/ReRokutosei/SimpleXray/commit/34dd43d944f37e6cb12cabc05ef492486a834a7d))
* **ui:** pass parent = null to root navigation event dispatcher ([fc872f4](https://github.com/ReRokutosei/SimpleXray/commit/fc872f426a5fd5cac09be184a954481357057f6c))
* **ui:** provide LocalNavigationEventDispatcherOwner to resolve popup menu expansion ([1100b1b](https://github.com/ReRokutosei/SimpleXray/commit/1100b1b7addc2ccaa763b199f3550cf783f2403f))
* **ui:** record random app icon on first launch without touching component states ([f343da9](https://github.com/ReRokutosei/SimpleXray/commit/f343da936d193c98114888d590915c189db76875))
* **ui:** request notification permission on service start and avoid repeated prompts ([471236f](https://github.com/ReRokutosei/SimpleXray/commit/471236f9018abf26a66b011998fa7aba7e3d5389))
* **ui:** resolve icon alias by full class name (debug suffix); move outbound nodes card after core control ([3f1c0b9](https://github.com/ReRokutosei/SimpleXray/commit/3f1c0b9c8618d51c7ee88701533010d2b73f13bc))
* **ui:** resolve OverlayBottomSheet z-index ordering and compact layout padding ([9fb6a6d](https://github.com/ReRokutosei/SimpleXray/commit/9fb6a6dca369ce884f6a7b27595d2cbf54b382f7))
* **ui:** scale vector foreground content via group (108dp canvas, 58dp content) ([d0eb2e5](https://github.com/ReRokutosei/SimpleXray/commit/d0eb2e5c1608111aa66786422cd6e0ed2834b85e))
* **ui:** show snackbar on AppListScreen ([61a2945](https://github.com/ReRokutosei/SimpleXray/commit/61a2945f378bb2e37c01fb31e3344a5a719df6f0))
* **ui:** theme latency status colors ([8d04957](https://github.com/ReRokutosei/SimpleXray/commit/8d04957d7dc0fe8146e21ff1f837347aa5b56c7e))
* **ui:** use collect instead of collectLatest for UI event channels and launch snackbars asynchronously ([aeef71d](https://github.com/ReRokutosei/SimpleXray/commit/aeef71d76e1c98efd09ec3d434d7c2846e9e5f35))
* **update:** detect newer stable and prerelease versions ([4c498a0](https://github.com/ReRokutosei/SimpleXray/commit/4c498a06a223ec792f09eaa1348750686e078a68))
* use START_NOT_STICKY and broadcast ACTION_START on core ready ([036a818](https://github.com/ReRokutosei/SimpleXray/commit/036a818496e908cb6f10aeb3c9753356a9852a60))
* Validate filenames for URI config imports ([ff16257](https://github.com/ReRokutosei/SimpleXray/commit/ff16257582261abfb4029e15f1dcf8ddc9373d47))
* **viewmodel:** close stats client when cleared ([e601ba6](https://github.com/ReRokutosei/SimpleXray/commit/e601ba654c1b986e2c449565adc84b47684731b6))
* **vpn:** fix start mutex deadlock on failure ([2ea2154](https://github.com/ReRokutosei/SimpleXray/commit/2ea2154470e2bd880136d7e7fcc0afa08bc2a328))
* **vpn:** retain JNI symbols in R8 ([289928f](https://github.com/ReRokutosei/SimpleXray/commit/289928f9ddcd9c84b8e0f3ddf4ac4f6608340af7))
* **worker:** bound GeoUpdateWorker retries to prevent infinite retry loops ([c17e3af](https://github.com/ReRokutosei/SimpleXray/commit/c17e3af2cfa5f0c5e022e486f2e4b50ab12d87b9))


* **build:** bump minSdk to 34 (Android 14) and eliminate legacy compatibility code ([dfe6f37](https://github.com/ReRokutosei/SimpleXray/commit/dfe6f37fa9f0a15e80f08822ca32328841396833))

## [2.3.0-alpha.5](https://github.com/ReRokutosei/SimpleXray/compare/v2.3.0-alpha.4...v2.3.0-alpha.5) (2026-10-01)


### Features

* **simpletun:** add adaptive high-watermark flow recycling ([19e5015](https://github.com/ReRokutosei/SimpleXray/commit/19e50151b627b22f1b057345a43982114b491ea1))


### Bug Fixes

* **simpletun:** check epoll_wait errno instead of unsigned comparison ([05a9622](https://github.com/ReRokutosei/SimpleXray/commit/05a962242ffd8d0eea442f3a9dd9e74c0badbe4b))

## [2.3.0-alpha.4](https://github.com/ReRokutosei/SimpleXray/compare/v2.3.0-alpha.3...v2.3.0-alpha.4) (2026-09-30)


### Features

* **simpletun:** add lightweight ipv4 tun-to-socks5 backend ([3e78d70](https://github.com/ReRokutosei/SimpleXray/commit/3e78d70e3055991627e82f76ce4d3255e127e5ea))

## [2.3.0-alpha.3](https://github.com/ReRokutosei/SimpleXray/compare/v2.3.0-alpha.2...v2.3.0-alpha.3) (2026-09-25)


### Bug Fixes

* **deps:** update agp to v9.4.1 ([#39](https://github.com/ReRokutosei/SimpleXray/issues/39)) ([4bfe975](https://github.com/ReRokutosei/SimpleXray/commit/4bfe975d8c22ca5f507f97638558ea76703ed9ab))
* **deps:** update dependency androidx.navigation:navigation-compose-android to v2.10.2 ([#41](https://github.com/ReRokutosei/SimpleXray/issues/41)) ([beceaa7](https://github.com/ReRokutosei/SimpleXray/commit/beceaa74aefc97d66b3a5153e27d94aabd8bd178))
* **deps:** update dependency androidx.work:work-runtime-ktx to v2.12.0 ([#42](https://github.com/ReRokutosei/SimpleXray/issues/42)) ([b618e5f](https://github.com/ReRokutosei/SimpleXray/commit/b618e5f28bc05b5b3c9c40cedec31119186a9c99))
* **deps:** update dependency com.github.nanihadesuka:lazycolumnscrollbar to v3 ([#37](https://github.com/ReRokutosei/SimpleXray/issues/37)) ([304c4f7](https://github.com/ReRokutosei/SimpleXray/commit/304c4f7319ad40b613c975add12dedc97f45fbf8))

## [2.3.0-alpha.2](https://github.com/ReRokutosei/SimpleXray/compare/v2.3.0-alpha.1...v2.3.0-alpha.2) (2026-09-24)


### Bug Fixes

* **vpn:** fix start mutex deadlock on failure ([4814e2c](https://github.com/ReRokutosei/SimpleXray/commit/4814e2c38c606244819743259e9a2340aa99af81))
* **vpn:** retain JNI symbols in R8 ([caa5530](https://github.com/ReRokutosei/SimpleXray/commit/caa5530ceb148e8acf3a859ce50da07f080ad2f9))

## [2.3.0-alpha.1](https://github.com/ReRokutosei/SimpleXray/compare/v2.2.0-beta.1...v2.3.0-alpha.1) (2026-09-24)


### Features

* **tun:** add ZepTUN backend powered by Noisemux ([e4fb51e](https://github.com/ReRokutosei/SimpleXray/commit/e4fb51e41f33c6de0bfc66571072143b41d3a57d))


### Bug Fixes

* **deps:** update dependency androidx.core:core-ktx to v1.19.1 ([#40](https://github.com/ReRokutosei/SimpleXray/issues/40)) ([999207a](https://github.com/ReRokutosei/SimpleXray/commit/999207ad5f3f79803c869df6e86d184c41914c49))
* **service:** synchronize native backend start with lifecycle lock ([1654d4c](https://github.com/ReRokutosei/SimpleXray/commit/1654d4c6b33e529b8c44f0363f03ef367a65b6c0))
* **tun:** isolate Go backends in worker processes ([b3e2794](https://github.com/ReRokutosei/SimpleXray/commit/b3e27949f3f3eaf2f303f76be45a08b68989268d))

## [2.2.0-beta.1](https://github.com/ReRokutosei/SimpleXray/compare/v2.1.1-alpha.3...v2.2.0-beta.1) (2026-09-20)


### Bug Fixes

* **native:** harden xray subprocess lifecycle with pdeathsig and cloexec clearance ([ef89687](https://github.com/ReRokutosei/SimpleXray/commit/ef89687d008f73c485b19f5e78c15ee3f43a1673))

### [2.1.1-alpha.3](https://github.com/ReRokutosei/SimpleXray/compare/v2.1.1-alpha.2...v2.1.1-alpha.3) (2026-09-19)


### Features

* **benchmark:** introduce headless BenchmarkService and asynchronous service teardown ([a8e7698](https://github.com/ReRokutosei/SimpleXray/commit/a8e7698e96658929ac67718344eb549f9c3924e8))


### Bug Fixes

* **sing-tun:** upgrade to upstream pure user-space Go stack and fix Android TUN subnet prefix ([957c2e3](https://github.com/ReRokutosei/SimpleXray/commit/957c2e3f9235c1b5c4ba04d96ff8bd3d852fbb81))

### [2.1.1-alpha.2](https://github.com/ReRokutosei/SimpleXray/compare/v2.1.1-alpha.1...v2.1.1-alpha.2) (2026-09-18)


### Bug Fixes

* **tunnel:** resolve SOCKS5 UDP relay packet drop and Android 14 foreground launch ([ccee388](https://github.com/ReRokutosei/SimpleXray/commit/ccee388472b5894d82ed23e57c9b198eafd450a7))

### [2.1.1-alpha.1](https://github.com/ReRokutosei/SimpleXray/compare/v2.1.1-alpha.0...v2.1.1-alpha.1) (2026-09-18)

## [2.1.0](https://github.com/ReRokutosei/SimpleXray/compare/v2.0.1...v2.1.0) (2026-09-16)


### Features

* **tun:** add SingTUN backend powered by sing-tun Go stack ([8a2dcba](https://github.com/ReRokutosei/SimpleXray/commit/8a2dcbaa5f231dec16727b3c087b1cb3189200a8))

### [2.0.1](https://github.com/ReRokutosei/SimpleXray/compare/v1.5.2...v2.0.1) (2026-09-10)


### ⚠ BREAKING CHANGES

* **build:** Minimum supported Android version is now Android 14 (API 34). Devices running Android 10-13 (API 29-33) are no longer supported.

### Features

* **vpn:** update underlying networks via NetworkCallback during network handover ([6a72815](https://github.com/ReRokutosei/SimpleXray/commit/6a7281506ea6d4f0dd7db3b72e5d19e45710b067))


### Bug Fixes

* **arch:** decouple child ViewModels from MainViewModel to prevent restoration crash ([59b6589](https://github.com/ReRokutosei/SimpleXray/commit/59b6589427c03b6eea0b8f0694798c0c9ee88486))
* **config:** add placeholder proxy outbound to template to match routing rules ([9eb547a](https://github.com/ReRokutosei/SimpleXray/commit/9eb547a21c683d8c6de03ee26d11f65779f8078f))
* **config:** align EFFECTIVE_MATCH_KEYS with Xray RawFieldRule and migrate legacy geosite/geoip ([a074b6b](https://github.com/ReRokutosei/SimpleXray/commit/a074b6b6ec1a8d0a8370c20ba4b88c187646ebc8))
* **config:** support flat outbound endpoints, hysteria naming, and primary socks inbound targeting ([5b144fa](https://github.com/ReRokutosei/SimpleXray/commit/5b144fadbecdcc1596abd5f1c69bd3119c7b810f))
* **core:** inject sniffing block into TUN inbound to restore FakeDNS and domain routing ([67f436c](https://github.com/ReRokutosei/SimpleXray/commit/67f436cc5ed0ae3ded03a756449d9cbcfcd88f64))
* **core:** remove PR_SET_PDEATHSIG and reap native child processes via waitpid ([430bf1d](https://github.com/ReRokutosei/SimpleXray/commit/430bf1dc332ae867b4e2133286fc5b5d5c68266c))
* **deps:** update dependency androidx.compose:compose-bom to v2026.09.00 ([#35](https://github.com/ReRokutosei/SimpleXray/issues/35)) ([ece17d0](https://github.com/ReRokutosei/SimpleXray/commit/ece17d0888d17ed4a6620144fb6d2a3a60c0fffd))
* **deps:** update dependency androidx.navigation:navigation-compose-android to v2.10.1 ([#34](https://github.com/ReRokutosei/SimpleXray/issues/34)) ([c46b4b5](https://github.com/ReRokutosei/SimpleXray/commit/c46b4b5eacb1059f4e7f5ef8795ff902e935a75d))
* **io:** prevent OkHttp response leak in GeoUpdateWorker and avoid fragile available() in FileManager ([759d166](https://github.com/ReRokutosei/SimpleXray/commit/759d1666da3fab4325b406e96fb1a2197e738222))
* **lifecycle:** cancel only xrayJob in stopXray to prevent serviceScope invalidation on reconnect ([b33d181](https://github.com/ReRokutosei/SimpleXray/commit/b33d1814731e32dfa3badc2dc141dd579b8ead0f))
* **lifecycle:** prevent activity leak in MainActivity and ensure full resource cleanup in TProxyService.onDestroy ([aaf1588](https://github.com/ReRokutosei/SimpleXray/commit/aaf1588dcfaa7b00e5dd2f7771a1b69d9e41c6d9))
* **log:** synchronize LogFileManager operations across instances via shared FILE_LOCK ([a795b3a](https://github.com/ReRokutosei/SimpleXray/commit/a795b3a3fb0c47e5a82d81f01a90fa3a4a628055))
* **memory:** decouple SOCKS Authenticator from MainViewModel to prevent static leak ([38f76bd](https://github.com/ReRokutosei/SimpleXray/commit/38f76bde7cb504137d03e4d2edd8e70d9f3a6238))
* **net:** add DNS resolution timeout protection to TcpPing and guard outbound latency probes ([1501e7d](https://github.com/ReRokutosei/SimpleXray/commit/1501e7dc3c947a5bdbb8a02e8a98cf1380dcccab))
* **net:** correct inverted bypassLan routing and link with geoip:private direct rule ([808dd1c](https://github.com/ReRokutosei/SimpleXray/commit/808dd1cff83111f54dea6ec0c23a82574b65e2dc))
* **perf:** defer config file order persistence until drag gesture completion ([835aac8](https://github.com/ReRokutosei/SimpleXray/commit/835aac890429ae24d0dcae421a8a5c057e786191))
* **perf:** offload app list package loading to IO dispatcher and serialize save operations ([16eec02](https://github.com/ReRokutosei/SimpleXray/commit/16eec02a06cbc8962edb43d0c1aacc6d23cfdbf0))
* **release:** strip leading v in versionCode parsing and add Breaking Changes section to release notes ([8d200a4](https://github.com/ReRokutosei/SimpleXray/commit/8d200a48c0ad6fd9b165ca90f210795602d172fc))
* **service:** use startService instead of startForegroundService on ACTION_DISCONNECT in BenchmarkReceiver ([fa675e6](https://github.com/ReRokutosei/SimpleXray/commit/fa675e635c00e51b9926e933b479c1346d898c8f))
* **ui:** use collect instead of collectLatest for UI event channels and launch snackbars asynchronously ([cf25e5d](https://github.com/ReRokutosei/SimpleXray/commit/cf25e5dc427b67b81e07a3d10f135eaa6abaee1a))
* **worker:** bound GeoUpdateWorker retries to prevent infinite retry loops ([bc7b1a0](https://github.com/ReRokutosei/SimpleXray/commit/bc7b1a0a627a7b60099333382685e7cd0919187d))


* **build:** bump minSdk to 34 (Android 14) and eliminate legacy compatibility code ([3492228](https://github.com/ReRokutosei/SimpleXray/commit/3492228ffddcec4a672433e13270c52cfef91728))

### [1.5.2](https://github.com/ReRokutosei/SimpleXray/compare/v1.5.1...v1.5.2) (2026-09-09)

### [1.5.1](https://github.com/ReRokutosei/SimpleXray/compare/v1.5.0...v1.5.1) (2026-09-08)


### Bug Fixes

* **deps:** update dependency androidx.navigation:navigation-compose-android to v2.10.0 ([#26](https://github.com/ReRokutosei/SimpleXray/issues/26)) ([9eb376f](https://github.com/ReRokutosei/SimpleXray/commit/9eb376f0005e5c3e1927697149fc1d5811dcdd8c))
* **deps:** update dependency org.yaml:snakeyaml to v2.7 ([#28](https://github.com/ReRokutosei/SimpleXray/issues/28)) ([d7bf251](https://github.com/ReRokutosei/SimpleXray/commit/d7bf251c6c612f824bea0d90733493cd7d368db3))
* **deps:** update grpc-java monorepo to v1.84.0 ([#31](https://github.com/ReRokutosei/SimpleXray/issues/31)) ([edf5dae](https://github.com/ReRokutosei/SimpleXray/commit/edf5dae515065d857eb38a849e72273065a03dfd))
* **deps:** update kotlin monorepo to v2.4.20 ([#33](https://github.com/ReRokutosei/SimpleXray/issues/33)) ([de7b977](https://github.com/ReRokutosei/SimpleXray/commit/de7b97780927e8ffad6e6691d517c16b0e4a16cf))
* **deps:** update protobuf monorepo to v4.36.1 ([#29](https://github.com/ReRokutosei/SimpleXray/issues/29)) ([3163f9c](https://github.com/ReRokutosei/SimpleXray/commit/3163f9c20c902cd6f0b09a0ef5aa38528a779a38))

## [1.5.0](https://github.com/ReRokutosei/SimpleXray/compare/v1.4.0...v1.5.0) (2026-08-26)


### Features

* **editor,log:** auto-scroll to match in config editor and highlight matches with scroll reset in logs ([32ee94b](https://github.com/ReRokutosei/SimpleXray/commit/32ee94b8268e8bd61bf9dc61534013d364d958e5))
* **log:** decouple log control into Error Log, Access Log, and DNS Log, adjust log buffer limits and align help dialog text ([d22ac97](https://github.com/ReRokutosei/SimpleXray/commit/d22ac972ecedb4a9b35bdc025e41ac87f6afac5f))


### Bug Fixes

* **deps:** update dependency androidx.work:work-runtime-ktx to v2.11.2 ([#23](https://github.com/ReRokutosei/SimpleXray/issues/23)) ([b89bf03](https://github.com/ReRokutosei/SimpleXray/commit/b89bf03bc8966ba8bc7b03c949aae3ad9402c63f))
* **deps:** update dependency com.squareup.okhttp3:okhttp to v5.5.0 ([#20](https://github.com/ReRokutosei/SimpleXray/issues/20)) ([e3fb3c9](https://github.com/ReRokutosei/SimpleXray/commit/e3fb3c9d5154ff98745356608fe983435ca2128e))
* **deps:** update protobuf monorepo to v4.36.0 ([#22](https://github.com/ReRokutosei/SimpleXray/issues/22)) ([dc10c2a](https://github.com/ReRokutosei/SimpleXray/commit/dc10c2a6c6f58410f7ad394f49f5ffc678505118))

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
