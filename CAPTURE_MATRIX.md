# Capture Matrix

Матрица описывает, какие свидетельства текущей браузерной сессии доступны приложению, где они фиксируются и куда попадают при ZIP-экспорте. Цель — не заменить реальный браузерный трафик производными запросами, а сохранить максимум доступных исходных данных и явно маркировать вторичные свидетельства.

| Канал | Источник | Что фиксируется | Raw / артефакт | ZIP | Ограничения |
|---|---|---|---|---|---|
| WebView requests | `shouldInterceptRequest` | method, URL, request headers, time | raw event `webview` | `raw-events.json`, HAR | WebView API не отдаёт response body/headers для обычного пропускаемого запроса |
| Navigation | WebView lifecycle + JS history hooks | URL, page, push/replace/pop/hash | raw `navigation` / `history` | raw, HAR/summary где применимо | промежуточные HTTP redirect responses напрямую WebView не раскрывает |
| fetch | JS instrumentation | method, URL/final URL, request headers/body, status, response headers/body, MIME, timing, stack, redirected flag | raw `fetch` | raw, HAR, API summary | для бинарного response body сохраняется маркер `[binary]`; Fetch API не раскрывает redirect chain в режиме follow |
| XHR | JS instrumentation | method, URL/final URL, request headers/body, status, raw response headers, body, MIME, timing, stack | raw `xhr` | raw, HAR, API summary | бинарный body недоступен как текст; forbidden response headers ограничены браузером |
| Static resources | WebView request + `WebResourceCapture` | исходный WebView request; отдельная GET-копия с response headers/body/bytes | raw `webview` + derivative `resource-copy`, resource bytes/meta | raw, HAR, `resources/` | `resource-copy` — вторичный запрос, а не доказательство точного ответа браузеру; должен быть помечен `evidenceType=derivative-resource-copy` |
| Resource redirects | explicit redirect follow в `WebResourceCapture` | каждый доступный hop: URL, status, statusText, Location, resolved Location, response headers, duration; final URL | derivative `resource-copy.redirectChain` + resource metadata | raw/HAR + resources manifest/meta | относится только к производной resource copy; не выдаётся за реальную browser redirect chain |
| Cookies | snapshots + Cookie Trace + HTTP evidence | document.cookie/native cookie snapshot, Set-Cookie где он доступен, JS setter hooks, diff/inference | page snapshot, raw network evidence, `cookie-trace.json` | snapshot/raw/browser artifact | HttpOnly недоступен JS, но может быть виден native CookieManager/Set-Cookie у доступного derivative response |
| localStorage/sessionStorage | snapshot scripts | полный доступный key/value state текущего origin | snapshot | `page-snapshot.json` | snapshot состояния, а не журнал каждой операции |
| IndexedDB | full snapshot | databases/stores/values с установленными лимитами выборки | browser artifacts | `browser/indexeddb-*.json` | до 1000 values на store; ограничения structured-clone/serialization |
| Cache Storage | full snapshot | cache names, до 250 requests на cache, textual bodies до 200k chars | browser artifacts | `browser/cache-*.json` | намеренно ограниченный snapshot; binary cache bodies не копируются |
| DOM/page source | light/full snapshot | title, URL, selected elements, outerHTML на full snapshot | snapshot | `page-snapshot.json`, `page.html` | snapshot конкретного момента; DOM между snapshot-ами восстанавливается только частично по mutation counters |
| DOM changes | MutationObserver | batch counts added/removed/attributes | raw `dom-mutation` | `dom-mutations.json`, raw | сохраняются счётчики batch, не полный diff каждого node |
| JavaScript | DOM script discovery + external copy | inline source, dynamic inline source, external JS bytes, errors | scripts/script errors | `js/`, `js/manifest.json` | external script copy является отдельным GET; redirect provenance сохраняется отдельно |
| Performance | Performance API / snapshots | resource timing, transfer/encoded/decoded sizes, durations | raw/snapshot | `performance.json`, snapshot | зависит от browser timing exposure/CORS/TAO |
| WebSocket | JS wrapper | open, send, receive, textual payload | raw `websocket-*` | `realtime.json`, raw | binary payload помечается `[binary]`; handshake response headers Web API не раскрывает |
| SSE | EventSource wrapper | open, message, data, lastEventId | raw `sse-*` | `realtime.json`, raw | response headers EventSource API не раскрывает |
| User actions | capture listeners | click/change/submit + target metadata | raw `user-action` | `actions.json`, raw | не фиксируются все возможные input/gesture events; значения form controls не добавляются автоматически в action event |
| JS errors | window error / unhandledrejection / console | message, source, line/column, console level | raw `js-error`, `promise-rejection`, `console` | raw | stack зависит от браузера/события |
| Downloads | WebView DownloadListener | URL, suggested filename, MIME, content length, DownloadManager id/status | raw `download` | raw | файл скачивается Android DownloadManager отдельно; его bytes не встраиваются автоматически в research ZIP |
| Browser mode | native controller | mobile/desktop mode, UA, viewport result | raw `browser-mode`, `desktop-viewport` | raw | служебное состояние сессии |
| Session manifest | export-time aggregation | raw/source counters, artifact counters, snapshot completeness indicators, observed warnings, explicit capture limits | derivative export metadata | `session-manifest.json` | не является новым источником трафика и не подменяет raw evidence |
| Capture warnings | native capture + browser instrumentation | точная причина, этап, URL/артефакт и metadata фактической потери/усечения | raw `capture-warning` | `raw-events.json` + сводка в `session-manifest.json` | warning фиксируется только при реально наблюдаемой ошибке или срабатывании лимита |

## Инвариант достоверности

Данные разделяются по уровню доказательности:

1. **Raw browser evidence** — события, непосредственно наблюдаемые WebView/JavaScript instrumentation.
2. **Snapshot evidence** — состояние страницы/хранилищ в конкретный момент.
3. **Derivative evidence** — вторичные HTTP-копии ресурсов или replay. Они полезны для получения body/headers, но всегда должны быть явно помечены и не должны подменять реальный browser traffic.

`ResearchArchive.records` сохраняет raw events до любых debugger correlation/merge преобразований.

## Приоритет оставшихся пробелов

- контролировать рост памяти долгой сессии без молчаливой потери raw evidence.
