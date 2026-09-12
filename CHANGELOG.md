# Changelog

История опубликованных релизов. Новые release-блоки добавляются автоматически сверху; ручной текст вне автоматически управляемых блоков сохраняется.

<!-- AUTO-CHANGELOG-INSERT -->
<!-- AUTO-CHANGELOG-v31-START -->
## v31 — 2026-09-12T19:18:37Z

- Release commit: `c1dc5c53f67043ccd822c2c3eb5cb187fd6eea4e`
- Artifact: `web-research-v31.apk`
- SHA-256: `fcdfaefd53b8f5c42ab15f344b26fce18bdf8b0cb69d49e83e7cd3786da04c99`
- Previous release: **v30**

### Changes

- ui: stabilize menus and polish browser controls
- release: publish polished stable menu interactions [release]

### Changed files

- `app/src/main/java/ru/evrasia/research/CookieTraceDetailsDialog.kt`
- `app/src/main/java/ru/evrasia/research/NetworkDebuggerControlsController.kt`
- `app/src/main/java/ru/evrasia/research/NetworkDebuggerDetailsController.kt`
- `app/src/main/java/ru/evrasia/research/NetworkReplayController.kt`
- `app/src/main/java/ru/evrasia/research/TechIconDrawable.kt`
- `app/src/main/java/ru/evrasia/research/WebResearchBrowserLayout.kt`
- `app/src/main/java/ru/evrasia/research/WebResearchMenuController.kt`
<!-- AUTO-CHANGELOG-v31-END -->
<!-- AUTO-CHANGELOG-v30-START -->
## v30 — 2026-09-12T18:23:23Z

- Release commit: `5d598ee1d3ed265725c651242785592b8d26c50d`
- Artifact: `web-research-v30.apk`
- SHA-256: `0a71e3562aa0df21bb085d9e385869c1714bc0d3435b49e977988b6739daae59`
- Previous release: **v29**

### Changes

- ui: enlarge and strengthen vector icons
- release: publish larger bolder vector icons [release]

### Changed files

- `app/src/main/java/ru/evrasia/research/NetworkDebuggerDetailViews.kt`
- `app/src/main/java/ru/evrasia/research/TechIconDrawable.kt`
<!-- AUTO-CHANGELOG-v30-END -->
<!-- AUTO-CHANGELOG-v29-START -->
## v29 — 2026-09-12T18:11:11Z

- Release commit: `5cdbfdbba25cac46c1f418a8019cb3b67a697c43`
- Artifact: `web-research-v29.apk`
- SHA-256: `88da441e14c97828a33bd9a7c9f69ec35f7e912c7c1ab84bb6a0ffb9fd6f501a`
- Previous release: **v28**

### Changes

- capture: record explicit omission warnings
- fix: position bottom sheets before first frame
- ui: replace glyph buttons with vector icon system
- release: publish capture and interface improvements [release]

### Changed files

- `CAPTURE_MATRIX.md`
- `app/src/main/java/ru/evrasia/research/CaptureWarning.kt`
- `app/src/main/java/ru/evrasia/research/NetworkDebuggerControlsController.kt`
- `app/src/main/java/ru/evrasia/research/NetworkDebuggerDetailViews.kt`
- `app/src/main/java/ru/evrasia/research/NetworkDebuggerDetailsController.kt`
- `app/src/main/java/ru/evrasia/research/SessionManifestBuilder.kt`
- `app/src/main/java/ru/evrasia/research/TechIconDrawable.kt`
- `app/src/main/java/ru/evrasia/research/WebCaptureController.kt`
- `app/src/main/java/ru/evrasia/research/WebResearchBrowserLayout.kt`
- `app/src/main/java/ru/evrasia/research/WebResearchMenuController.kt`
- `app/src/main/java/ru/evrasia/research/WebResearchScripts.kt`
- `app/src/main/java/ru/evrasia/research/WebResearchV10Activity.kt`
- `app/src/main/java/ru/evrasia/research/WebResourceCapture.kt`
- `app/src/test/java/ru/evrasia/research/CaptureWarningRegressionTest.kt`
<!-- AUTO-CHANGELOG-v29-END -->
<!-- AUTO-CHANGELOG-v28-START -->
## v28 — 2026-09-12T17:30:55Z

- Release commit: `ba873b1db244a9c8c8d171adc2e48848431b06a6`
- Artifact: `web-research-v28.apk`
- SHA-256: `64f6ddabe98eb8112ad35a3c2e80c84d60ad81d76e462e4f0dc1ade429f275c0`
- Previous release: **v27**

### Changes

- capture: preserve derivative redirect chains
- ci: synchronize universal APK workflow standard
- ci: harden release numbering and work-branch validation
- ci: add automatic work branch cleanup
- capture: add session completeness manifest
- release: publish capture completeness improvements [release]

### Changed files

- `.github/scripts/update-release-docs.py`
- `.github/workflows/_release-apk.yml`
- `.github/workflows/_release-core.yml`
- `.github/workflows/cleanup-work-branches.yml`
- `.github/workflows/validate-work-branches.yml`
- `CAPTURE_MATRIX.md`
- `app/src/main/java/ru/evrasia/research/ResearchArchiveExporter.kt`
- `app/src/main/java/ru/evrasia/research/SessionManifestBuilder.kt`
- `app/src/main/java/ru/evrasia/research/WebResourceCapture.kt`
- `app/src/test/java/ru/evrasia/research/CaptureRegressionTest.kt`
<!-- AUTO-CHANGELOG-v28-END -->
<!-- AUTO-CHANGELOG-v27-START -->
## v27 — 2026-09-10T12:22:56Z

- Release commit: `90ad694a9530993316136d6ec98e48c671c3dd97`
- Artifact: `web-research-v27.apk`
- SHA-256: `8ae44ec6ec850060d89e89178394a7fb43f8335720dfebe5b288c1edcb66a74d`
- Previous release: **v26**

### Changes

- test: add deterministic capture regression suite
- release: publish regression guard [release]

### Changed files

- `.github/actions/build-apk/action.yml`
- `app/build.gradle.kts`
- `app/src/test/java/ru/evrasia/research/CaptureRegressionTest.kt`
- `app/src/test/resources/session-fixture.json`
<!-- AUTO-CHANGELOG-v27-END -->
<!-- AUTO-CHANGELOG-v26-START -->
## v26 — 2026-09-10T12:14:46Z

- Release commit: `b188f7279f6c3d5fab467d058c9a4949821cd310`
- Artifact: `web-research-v26.apk`
- SHA-256: `82330cacb7aeef813c972f63feb0ffdefb56e1e01ea2ab9e81b169c21ef4e347`
- Previous release: **v25**

### Changes

- docs: align architecture with v25
- release: publish architecture audit [release]

### Changed files

- Нет файловых изменений.
<!-- AUTO-CHANGELOG-v26-END -->
<!-- AUTO-CHANGELOG-v25-START -->
## v25 — 2026-09-10T10:06:39Z

- Release commit: `485dcfbfd37a54e87174ed7551f577d8373b6421`
- Artifact: `web-research-v25.apk`
- SHA-256: `dbfcf5e671790d6fa1d3a2088fcc34675d40a8514de91c40d1a9760bc0519833`
- Previous release: **v24**

### Changes

- fix: remove stale debugger counter and dead activity state
- fix: qualify debugger background color
- ci: update README and refactoring docs on every release
- release: publish debugger activity cleanup [release]

### Changed files

- `.github/scripts/update-release-docs.py`
- `.github/workflows/android-apk.yml`
- `.github/workflows/validate-work-branches.yml`
- `app/src/main/java/ru/evrasia/research/NetworkDebuggerActivity.kt`
<!-- AUTO-CHANGELOG-v25-END -->
<!-- AUTO-CHANGELOG-v24-START -->
## v24 — 2026-09-10T09:47:49Z

- Release commit: `a56bce81b33f5f22e0d263c1d9b6e9493af75f71`
- Artifact: `web-research-v24.apk`
- SHA-256: `26cc6df2aeece39d5ce33960129b306b33cdfcebc22767bd6a67be83ac154205`
- Previous release: **v23**

### Changes

- refactor: reduce cookie trace provider to lifecycle bridge
- release: publish cookie provider cleanup [release]

### Changed files

- `app/src/main/java/ru/evrasia/research/CookieTraceProvider.kt`
<!-- AUTO-CHANGELOG-v24-END -->
<!-- AUTO-CHANGELOG-v23-START -->
## v23 — 2026-09-10T09:40:33Z

- Release commit: `0f04f60df8f82c707bfb70ab2dae1d7d428908af`
- Artifact: `web-research-v23.apk`
- SHA-256: `9c5120ac33b35bb761758a5e83740d291bfd5e2cd847ef2e0c153ef7a2ff6755`
- Previous release: **v22**

### Changes

- refactor: extract cookie trace engine
- release: publish cookie trace engine refactor [release]

### Changed files

- `app/src/main/java/ru/evrasia/research/CookieTraceEngine.kt`
- `app/src/main/java/ru/evrasia/research/CookieTraceProvider.kt`
<!-- AUTO-CHANGELOG-v23-END -->
<!-- AUTO-CHANGELOG-v22-START -->
## v22 — 2026-09-10T03:23:36Z

- Release commit: `a192f87b2da8aa54405a1ce74c81ca2121733d49`
- Artifact: `web-research-v22.apk`
- SHA-256: `b0055b4e48190e2b0b6c6f485696980098f72a16343721670f645c6deaf58c16`
- Previous release: **v21**

### Changes

- refactor: centralize cookie trace details dialog
- refactor: keep cookie list section labels
- release: publish cookie details refactor [release]

### Changed files

- `app/src/main/java/ru/evrasia/research/CookieTraceDetailsDialog.kt`
- `app/src/main/java/ru/evrasia/research/CookieTraceProvider.kt`
- `app/src/main/java/ru/evrasia/research/CookieTraceUiProvider.kt`
<!-- AUTO-CHANGELOG-v22-END -->
<!-- AUTO-CHANGELOG-v21-START -->
## v21 — 2026-09-10T03:15:00Z

- Release commit: `5747160d1cc65ef3724b059d0bc9be96fbafacf3`
- Artifact: `web-research-v21.apk`
- SHA-256: `582f86fd78febe7a6541bee18e78427189ec223f4e7e1943ccba3026b247b773`
- Previous release: **v20**

### Changes

- refactor: extract debugger detail view helpers
- release: publish debugger detail view refactor [release]

### Changed files

- `app/src/main/java/ru/evrasia/research/NetworkDebuggerDetailViews.kt`
- `app/src/main/java/ru/evrasia/research/NetworkDebuggerDetailsController.kt`
<!-- AUTO-CHANGELOG-v21-END -->
<!-- AUTO-CHANGELOG-v20-START -->
## v20 — 2026-09-10T03:06:56Z

- Release commit: `217bd4a82c3b037ac7e4b3b63d34927e6776d4bb`
- Artifact: `web-research-v20.apk`
- SHA-256: `702351872d53663fbbc047bc3b0b14e582813c8992f8039a7af36dcce1248c4b`
- Previous release: **v19**

### Changes

- refactor: extract debugger controls and filters
- release: publish debugger controls refactor [release]

### Changed files

- `app/src/main/java/ru/evrasia/research/NetworkDebuggerActivity.kt`
- `app/src/main/java/ru/evrasia/research/NetworkDebuggerControlsController.kt`
<!-- AUTO-CHANGELOG-v20-END -->
<!-- AUTO-CHANGELOG-v19-START -->
## v19 — 2026-09-10T03:00:14Z

- Release commit: `446e068856bc05d77514f543938075b54e5f2a8b`
- Artifact: `web-research-v19.apk`
- SHA-256: `0c33b9ed2276c560d5b3d4236ea4db3562e0e093979308dc45567c816a8651f9`
- Previous release: **v18**

### Changes

- refactor: extract debugger details controller
- refactor: extract realtime session dialog
- release: publish debugger details and realtime refactor [release]

### Changed files

- `app/src/main/java/ru/evrasia/research/NetworkDebuggerActivity.kt`
- `app/src/main/java/ru/evrasia/research/NetworkDebuggerDetailsController.kt`
- `app/src/main/java/ru/evrasia/research/NetworkDebuggerRealtimeController.kt`
<!-- AUTO-CHANGELOG-v19-END -->
<!-- AUTO-CHANGELOG-v18-START -->
## v18 — 2026-09-10T02:23:03Z

- Release commit: `b81e25c5506c4a7fad243ccbb4d577021c2accb4`
- Artifact: `web-research-v18.apk`
- SHA-256: `739c14d22f350ed3fea1b09803507f2b6d633810554e9c7f16bfc66e3c3692f0`
- Previous release: **v17**

### Changes

- refactor: extract debugger row rendering
- release: publish debugger row refactor [release]

### Changed files

- `app/src/main/java/ru/evrasia/research/NetworkDebuggerActivity.kt`
- `app/src/main/java/ru/evrasia/research/NetworkDebuggerEventAdapter.kt`
- `app/src/main/java/ru/evrasia/research/NetworkDebuggerRowPresentation.kt`
<!-- AUTO-CHANGELOG-v18-END -->
<!-- AUTO-CHANGELOG-v17-START -->
## v17 — 2026-09-10T02:11:53Z

- Release commit: `ab8ec82db13a369c437e4335a5d8de61dda4b10a`
- Artifact: `web-research-v17.apk`
- SHA-256: `db1e70a12d9a41539ce82756544a490e5f4d172f78d78229419d1a288d0ba057`
- Previous release: **v16**

### Changes

- refactor: extract browser menu controller
- refactor: keep activity accent propagation
- refactor: extract browser layout construction
- release: publish browser activity refactor [release]

### Changed files

- `app/src/main/java/ru/evrasia/research/WebResearchBrowserLayout.kt`
- `app/src/main/java/ru/evrasia/research/WebResearchMenuController.kt`
- `app/src/main/java/ru/evrasia/research/WebResearchV10Activity.kt`
<!-- AUTO-CHANGELOG-v17-END -->
<!-- AUTO-CHANGELOG-v16-START -->
## v16 — 2026-09-10T01:54:40Z

- Release commit: `9af900940abfc3a791d34700a2407e6c330c9082`
- Artifact: `web-research-v16.apk`
- SHA-256: `b5562228e04a2c2de6b0daf3095937a5cc7602d169b225a016c2e616544d2539`
- Previous release: **v15**

### Changes

- refactor: split debugger projection and text formatting
- refactor: centralize shared cookie trace logic
- refactor: separate raw archive from export generation
- release: publish refactoring stages 2-4 [release]

### Changed files

- `app/src/main/java/ru/evrasia/research/CookieTraceProvider.kt`
- `app/src/main/java/ru/evrasia/research/CookieTraceSupport.kt`
- `app/src/main/java/ru/evrasia/research/CookieTraceUiProvider.kt`
- `app/src/main/java/ru/evrasia/research/NetworkDebuggerActivity.kt`
- `app/src/main/java/ru/evrasia/research/NetworkDebuggerProjection.kt`
- `app/src/main/java/ru/evrasia/research/NetworkDebuggerText.kt`
- `app/src/main/java/ru/evrasia/research/ResearchArchive.kt`
- `app/src/main/java/ru/evrasia/research/ResearchArchiveExporter.kt`
- `app/src/main/java/ru/evrasia/research/WebResearchExportController.kt`
<!-- AUTO-CHANGELOG-v16-END -->
<!-- AUTO-CHANGELOG-v15-START -->
## v15 — 2026-09-10T01:35:51Z

- Release commit: `5a552759abe27730dbca0a185d9834cf93943f33`
- Artifact: `web-research-v15.apk`
- SHA-256: `c23ac04647c6c90130b3ff205cf4682fd8bb9aaf835f15fbcc0f54e5e7af5893`
- Previous release: **v14**

### Changes

- refactor: remove unreachable legacy code
- release: publish dead-code cleanup [release]

### Changed files

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/ru/evrasia/research/NetworkDebuggerActivity.kt`
- `app/src/main/java/ru/evrasia/research/NetworkRequestActions.kt`
- `app/src/main/java/ru/evrasia/research/NetworkResearchActivity.kt`
- `app/src/main/java/ru/evrasia/research/ResearchSecretRedactor.kt`
- `app/src/main/java/ru/evrasia/research/WebCookieStatsController.kt`
- `app/src/main/java/ru/evrasia/research/WebResearchV10Activity.kt`
<!-- AUTO-CHANGELOG-v15-END -->
<!-- AUTO-CHANGELOG-v14-START -->
## v14 — 2026-09-10T01:07:37Z

- Release commit: `9f1c626114450c00669dbe0b90fa65812d5343eb`
- Artifact: `web-research-v14.apk`
- SHA-256: `330fe5fbd9d4c2036da752f18bf7828b542abb6aa7f0245f0f16e578378f585f`
- Previous release: **v13**

### Changes

- ci: recover standardized release documentation for v13
- docs: verify standardized release documentation automation [release]

### Changed files

- `.github/workflows/android-apk.yml`
<!-- AUTO-CHANGELOG-v14-END -->
<!-- AUTO-CHANGELOG-v13-START -->
## v13 — 2026-09-10T01:00:17Z

- Release commit: `e50ed17c141e9ed36ab8bc5a5180d0f4d0c3f1b0`
- Artifact: `web-research-v13.apk`
- SHA-256: `fabd7fffe97ebdb5e6640a98cbe6b041fedcadd9a3e8771cdedc3111029280c3`
- Previous release: **v12**

### Changes

- docs: standardize release documentation contract
- docs: accept standardized release documentation contract [release]

### Changed files

- `.github/scripts/update-release-docs.py`
- `.github/workflows/android-apk.yml`
- `.github/workflows/validate-work-branches.yml`
<!-- AUTO-CHANGELOG-v13-END -->
<!-- AUTO-CHANGELOG-v12-START -->
## v12 — 2026-09-10T00:39:13Z

- Release commit: `05fb86c6dca3abe1d895cf9c53da11bc7c1aa4af`
- Artifact: `web-research-v12.apk`
- SHA-256: `f755ab9c749525951635744d8a8a10ed260f83baf89297a076c34a39ac327ffb`
- Previous release: **v11**

### Changes

- docs: automate release documentation updates
- ci: refresh and maintain release workflow dependencies
- ci: accept maintained release workflow dependencies [release]

### Changed files

- `.github/actions/build-apk/action.yml`
- `.github/dependabot.yml`
- `.github/scripts/update-release-docs.py`
- `.github/workflows/_release-apk.yml`
- `.github/workflows/_release-core.yml`
- `.github/workflows/android-apk.yml`
- `.github/workflows/validate-work-branches.yml`
<!-- AUTO-CHANGELOG-v12-END -->
