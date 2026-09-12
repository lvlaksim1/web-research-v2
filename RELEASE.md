# Release

Документ фиксирует обязательный release contract проекта. Конкретные metadata последнего релиза обновляются автоматически; правила процесса изменяются только осознанно вместе со стандартом.

<!-- AUTO-RELEASE-START -->
## Последний проверенный релиз

- Релиз: **v32**
- Релизный commit: `3a9fe1f5c54fc2aae8bcb849d4bdd4678e1bbd7f`
- Артефакт: `web-research-v32.apk`
- SHA-256: `4667c4f0239142e6c1dc0861607c263df70fd4c90b80af9ac60170b311c23b7b`
- Опубликован: `2026-09-12T19:45:47Z`
<!-- AUTO-RELEASE-END -->

## Уровни release-системы

Точка входа проекта находится в `.github/workflows/android-apk.yml`. Она вызывает универсальный APK workflow L2, который использует универсальное release-ядро L1 и проектный L3 build adapter.

```text
push в main
     ↓
L3 entrypoint: android-apk.yml
     ↓
L2: _release-apk.yml
     ├── L1 prepare: _release-core.yml
     ├── L3 build adapter: .github/actions/build-apk/action.yml
     └── L1 publish: _release-core.yml
```

### L1 — универсальное release-ядро

`.github/workflows/_release-core.yml` отвечает за release gate по `[release]`, определение `vN`, публикацию, recovery/rollback, проверку target commit, SHA-256, download-back verification и очистку временных Actions artifacts.

### L2 — универсальный APK-уровень

`.github/workflows/_release-apk.yml` отвечает за unsigned APK contract, package/version/ABI checks, controlled signing, certificate verification, retention, Telegram и GitHub Step Summary.

### L3 — проектная часть

`.github/actions/build-apk/action.yml` отвечает только за проектно-специфичную сборку `./gradlew :app:assembleRelease`. Номер релиза передаётся как `RELEASE_VERSION_CODE`.

## Триггер и нумерация

Каждый push в `main` запускает workflow. Реальный релиз выполняется только если сообщение triggering commit содержит literal `[release]`. Следующая версия определяется как `max(vN) + 1`.

## Подпись и проверки APK

До доступа к production signing material L2 проверяет существование unsigned candidate, package id, `versionCode`, `versionName`, отсутствие действующей подписи и native ABI policy. После этого L2 проверяет SHA-256 production certificate, подписывает APK и выполняет `apksigner verify`.

## Публикация и recovery

Release создаётся как draft, APK загружается, Release публикуется и APK скачивается обратно для SHA-256 verification. Незавершённый draft восстанавливается детерминированно; конфликтующий tag/release приводит к fail.

## Retention и доставка

Хранятся APK текущего релиза и ближайшего предыдущего релиза, в котором реально существует APK. Старые Release metadata и tags сохраняются. Telegram получает прямую ссылку на GitHub Release APK; ошибка Telegram не отменяет успешно опубликованный Release.

## Стандарт документации релиза

После каждого успешного Release отдельный project-level docs job обновляет обязательные файлы:

- `README.md` — metadata текущего релиза;
- `ARCHITECTURE.md` — контрольная точка актуальности архитектурного документа;
- `CHANGELOG.md` — накопительный блок нового релиза;
- `RELEASE.md` — metadata последнего проверенного release;
- `.release/latest.json` — канонический машиночитаемый release manifest.

История изменений хранится только в `CHANGELOG.md`. Статические разделы README/ARCHITECTURE/RELEASE не генерируются автоматически. Docs update выполняется отдельным commit без `[release]`.

## Workflow dependencies

GitHub Actions закрепляются по полным commit SHA. Dependabot проверяет их ежемесячно и группирует обновления. Checkout без необходимости push выполняется с `persist-credentials: false`; write credentials оставляются только в post-release docs job.
