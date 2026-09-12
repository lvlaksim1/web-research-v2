# web-research

Android WebView-инструмент для максимально полного захвата текущей браузерной сессии, последующего экспорта исходных данных в ZIP и ручного анализа.

<!-- AUTO-RELEASE-START -->
## Текущий релиз

- Версия: **v28**
- `versionCode`: **28**
- `versionName`: **v28**
- package: `web.research`
- commit: `ba873b1db244a9c8c8d171adc2e48848431b06a6`
- APK: `web-research-v28.apk`
- SHA-256: `64f6ddabe98eb8112ad35a3c2e80c84d60ad81d76e462e4f0dc1ade429f275c0`
- Опубликован: `2026-09-12T17:30:55Z`
- Предыдущий релиз: **v27**
- Release: https://github.com/lvlaksim1/web-research/releases/tag/v28
- APK: https://github.com/lvlaksim1/web-research/releases/download/v28/web-research-v28.apk
<!-- AUTO-RELEASE-END -->

## Назначение

Приложение открывает сайты внутри Android WebView и сохраняет доступные свидетельства реальной сессии без попытки автоматически превращать её в Postman-коллекцию или универсальный AUTH-replay.

Критический принцип: сырые события сначала добавляются в `ResearchArchive`, и только после этого передаются в слой корреляции и отображения сетевого debugger-а. Производные представления не должны заменять или изменять исходный архив.

## Что фиксируется

- WebView resource requests и копирование доступных ресурсов;
- `fetch` и `XMLHttpRequest`: URL, метод, request/response headers, request/response body, status, timing и final URL;
- navigation/history и действия пользователя;
- cookies, `localStorage`, `sessionStorage`;
- full/light page snapshots, DOM и HTML страницы;
- JavaScript-файлы и динамические inline scripts;
- resource/navigation timing и performance events;
- DOM mutations;
- WebSocket и Server-Sent Events;
- JavaScript errors и unhandled promise rejections;
- доступные сведения о скачиваниях файлов.

## ZIP-экспорт

Основной результат исследования — ZIP текущей сессии. В него входят, в частности:

- `session-manifest.json` — counters, completeness indicators, фактические предупреждения и явные capture limits;
- `raw-events.json` — исходный журнал событий;
- `network.har`;
- `api-summary.json`;
- `actions.json`;
- `dom-mutations.json`;
- `realtime.json`;
- `performance.json`;
- `page-snapshot.json`;
- `page.html`, если HTML доступен;
- архивированные JavaScript-файлы и `js/manifest.json`;
- сохранённые ресурсы и `resources/manifest.json`.

## Сетевой debugger

Сетевой экран предназначен для анализа уже собранных данных. Он поддерживает фильтрацию по домену, методу и типу ответа, поиск, объединённое/раздельное отображение, request/response headers и bodies, JSON-просмотр, URL decode, cURL, GET BODY и EDIT / REPLAY.

## Скачивание файлов

Если сайт инициирует скачивание через WebView DownloadListener, приложение передаёт его Android DownloadManager, добавляя доступные User-Agent, Cookie и Referer. Сам факт скачивания также фиксируется как событие исследования.

## Android-конфигурация

- `applicationId`: `web.research`
- `minSdk`: 26
- `targetSdk`: 35
- `compileSdk`: 35
- Java/Kotlin target: 17
- номер релиза одновременно используется как `versionCode`; `versionName = v<versionCode>`
- APK подписываются постоянной signing identity из GitHub Secret `ANDROID_KEYSTORE`

## Стандартная документация

- `ARCHITECTURE.md` — текущая архитектура приложения, границы компонентов и инварианты.
- `CHANGELOG.md` — накопительная история релизов.
- `RELEASE.md` — правила сборки, проверки, публикации и сопровождения релизов.
- `.release/latest.json` — канонические машиночитаемые metadata последнего опубликованного релиза.

После каждого успешного релиза workflow обновляет только стандартизированные автоматически управляемые части этих документов и делает отдельный docs commit без `[release]`.
