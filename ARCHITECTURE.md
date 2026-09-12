# Architecture

Документ описывает фактическую архитектуру `web-research` после серии рефакторингов v18–v25 и ключевые инварианты, которые должны сохраняться дальше.

<!-- AUTO-RELEASE-START -->
## Контрольная точка документа

- Актуально для релиза: **v31**
- Релизный commit: `c1dc5c53f67043ccd822c2c3eb5cb187fd6eea4e`
- Опубликован: `2026-09-12T19:18:37Z`
<!-- AUTO-RELEASE-END -->

## Главная цель проекта

Максимально полно зафиксировать текущую реальную браузерную сессию и сохранить исходные данные в ZIP для последующего анализа. Автоматическое построение Postman/AUTH-replay не является целью проекта.

## Критический инвариант raw-данных

`ResearchArchive.records` — источник исходных событий сессии. Событие сначала фиксируется в raw archive и только после этого попадает в производный debugger pipeline.

```text
browser / network event
        ↓
ResearchArchive.records   ← RAW, не модифицируется debugger-ом
        ↓
NetworkRecordPipeline
        ↓
NetworkDebugStore
        ↓
NetworkEventCorrelator
        ↓
debugger projection / display
```

`NetworkRecordPipeline` делает отдельную JSON-копию для debugger-а. Нормализация headers, корреляция, merge, фильтрация и display transformations не должны менять объект, уже помещённый в `ResearchArchive.records`.

## Browser / capture слой

- `WebResearchV10Activity` — lifecycle и верхнеуровневая оркестрация браузера. Она связывает контроллеры, но не должна содержать большие UI-подсистемы или capture-алгоритмы.
- `WebResearchBrowserLayout` — построение основного browser UI: toolbar, address bar, ZIP, Network, badge, progress, WebView container.
- `WebResearchMenuController` — меню браузера, bookmarks, cookies UI, theme/accent и About.
- `WebResearchWebViewController` — WebViewClient/WebChromeClient и события WebView.
- `WebNavigationController` — URL normalization и navigation.
- `WebBookmarkController` — bookmarks.
- `WebDownloadController` — скачивания, инициированные сайтом.
- `WebCaptureController` — запуск browser-side instrumentation, snapshots, JS bridge и сбор chunk-артефактов.
- `WebResearchScripts` — JavaScript instrumentation для fetch/XHR/navigation/history/actions/storage/DOM/performance/realtime и связанных browser-side событий.
- `WebResourceCapture` — сохранение доступных ресурсов и внешних JavaScript-файлов.

## Raw archive и экспорт

`ResearchArchive` отвечает только за mutable state текущей исследовательской сессии:

- `records`;
- scripts и script errors;
- resources и resource metadata;
- extra artifacts;
- page snapshot;
- clear/reset.

`SessionManifestBuilder` строит только производные metadata экспорта: counters, completeness indicators, capture limits и warnings; он не изменяет raw archive.

`ResearchArchiveExporter` является отдельным read/export слоем и строит:

- `session-manifest.json`;
- `raw-events.json`;
- `network.har`;
- `api-summary.json`;
- `actions.json`;
- `dom-mutations.json`;
- `realtime.json`;
- `performance.json`;
- `page-snapshot.json` и `page.html`;
- `js/manifest.json` и JavaScript-файлы;
- `resources/manifest.json` и ресурсы;
- browser artifacts.

`WebResearchExportController` управляет lifecycle ZIP-экспорта и вызывает `ResearchArchiveExporter`. Export-код не должен изменять raw state.

## Network debugger

`NetworkDebuggerActivity` после декомпозиции является UI-orchestrator, а не монолитным debugger-ом.

Его компоненты:

- `NetworkDebuggerDataSource` — получение изменений из `NetworkDebugStore`.
- `NetworkDebuggerProjection` — фильтрация, realtime session projection, CHANGED и counters.
- `NetworkDebuggerEventAdapter` — rendering списка событий.
- `NetworkDebuggerRowPresentation` — presentation rules и row flags.
- `NetworkDebuggerControlsController` — нижняя панель, filters, search, merge mode и popup UI.
- `NetworkDebuggerDetailsController` — orchestration окна деталей request/response.
- `NetworkDebuggerDetailViews` — JSON tree, highlighting, collapsible sections и detail UI primitives.
- `NetworkDebuggerRealtimeController` — realtime session dialog.
- `NetworkDebuggerText` — форматирование request/response/headers/cURL/body/timing.
- `NetworkDisplayMerger` — display-only merge.
- `NetworkEventClassifier` — классификация событий.
- `NetworkEventCorrelator` — политика корреляции производных debugger-событий.
- `NetworkReplayController` и `NetworkRequestActions` — EDIT/REPLAY и вспомогательные действия. Replay — аналитический инструмент и не является источником raw browser traffic.

## Cookie Trace

Cookie Trace разделён на четыре ответственности:

- `CookieTraceProvider` — lifecycle bridge между браузером, engine и UI.
- `CookieTraceEngine` — JS cookie hooks, Set-Cookie ingestion, snapshots/diff, связь изменений cookie с HTTP-событиями, confidence/origin и `cookie-trace.json`.
- `CookieTraceSupport` — общая предметная логика и форматирование происхождения/истории cookie.
- `CookieTraceDetailsDialog` — общий details/replay UI.
- `CookieTraceUiProvider` — интерфейс навигации по cookie trace данным.

Cookie Trace — производный аналитический слой. Он не должен заменять или сокращать исходные cookie/network evidence в `ResearchArchive`.

## UI и служебные компоненты

- `WebUiTheme` и `AccentColorPickerView` — theme/accent.
- `TechIconDrawable` — технические иконки.
- `ResultDelivery` — сохранение/шаринг подготовленного текста и бинарных файлов.

## Границы, которые не следует снова смешивать

1. raw capture ≠ debugger correlation/display;
2. raw state ≠ export generation;
3. browser orchestration ≠ menu/layout construction;
4. cookie capture/inference ≠ cookie UI;
5. debugger data/projection ≠ row/detail rendering;
6. реальный browser traffic ≠ аналитический replay.

## Критерий дальнейшего рефакторинга

Дальнейшее выделение классов оправдано только при наличии одной из причин: несколько независимых ответственностей, фактическое дублирование, трудность детерминированного тестирования или реальный риск изменения raw capture. Уменьшение файла само по себе больше не является целью.
