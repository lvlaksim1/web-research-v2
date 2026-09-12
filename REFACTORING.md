# Рефакторинг web research

<!-- AUTO-RELEASE-START -->
## Контрольная точка рефакторинга

- Актуально для релиза: **v28**
- Релизный commit: `ba873b1db244a9c8c8d171adc2e48848431b06a6`
- Опубликован: `2026-09-12T17:30:55Z`

### Изменения между релизами

- capture: preserve derivative redirect chains
- ci: synchronize universal APK workflow standard
- ci: harden release numbering and work-branch validation
- ci: add automatic work branch cleanup
- capture: add session completeness manifest
- release: publish capture completeness improvements [release]

### Изменённые файлы

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
<!-- AUTO-RELEASE-END -->

Автоматическая контрольная точка текущего состояния рефакторинга. Детальные архитектурные инварианты описаны в `ARCHITECTURE.md`.
