# Changelog

All notable changes to this project will be documented in this file. See [standard-version](https://github.com/conventional-changelog/standard-version) for commit guidelines.

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
