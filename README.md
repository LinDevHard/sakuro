# Sakuro

> Видеоплеер с реалтайм-апскейлом. **Sakuro** by **Rinwave** (`com.rinwave.sakuro`).
> Open source (GPLv3 — из-за будущей линковки libmpv, см. [ARCHITECTURE.md](ARCHITECTURE.md) §7).

Kotlin Multiplatform + Compose Multiplatform: Android (продукт) и Desktop/JVM (полигон для UI).
Документы проекта: [ARCHITECTURE.md](ARCHITECTURE.md) · [FEATURES.md](FEATURES.md) · [DESIGN.md](DESIGN.md) · [BRAND.md](BRAND.md) · [RESEARCH.md](RESEARCH.md)

---

## Статус (v0.1.0)

Рабочая Фаза-1-версия с **одним боевым движком — Media3/ExoPlayer**:

- ✅ **Media3-движок** (`engine/engine-media3`): воспроизведение локального видео, hw-декод,
  апскейл через `ExoPlayer.setVideoEffects()` — цепочка `GlEffect`:
  - `Presentation` — реальное повышение выходного разрешения (upscale-проход);
  - `SharpenGlEffect` — GLSL ES luma-guided unsharp mask с анти-рингингом (упрощённый Anime4K-проход);
  - `DenoiseGlEffect` — bilateral-lite edge-preserving деноиз.
- ✅ **FakePlayerEngine** (`engine/engine-fake`) — desktop и UI-отладка без нативных зависимостей.
- ✅ Абстракция `PlayerEngine` + `EngineRegistry` (ARCHITECTURE §4) — UI не знает, какой движок под ним.
- ✅ Пресеты (`core/core-upscale`): Выкл / Anime SD / Anime HD / Live-action light,
  сериализация для импорта/экспорта (`ProfileCodec`), смена на лету из плеера.
- ✅ Библиотека локальных видео (MediaStore) + открытие файла через SAF.
- ✅ Debug-оверлей «stats for nerds»: движок, декодер, кодеки, source→output разрешение, fps,
  dropped, битрейт, аудио, активные проходы апскейла.
- ✅ Жесты: тап (контролы), двойной тап (перемотка ±10с / пауза), удержание (2×), слайдер-перемотка.
- ✅ Настройки: выбор движка, пресет по умолчанию, debug-оверлей (multiplatform-settings).
- ✅ Флейворы `foss` / `full` (пока идентичны, без проприетарных SDK — F-Droid-ready).
- ✅ Decompose (навигация) + Koin (DI) + Lucide (иконки) + бренд-палитра из BRAND.md.

Проверено на эмуляторе (Pixel 9a, API 36): воспроизведение, смена пресетов на лету,
2×-апскейл `854x480 → 1708x960`, debug-оверлей, навигация, разрешения.

## Сборка и запуск

Требуется JDK 17+ и Android SDK (путь — в `local.properties`).

```bash
# Android (foss-флейвор)
./gradlew :composeApp:assembleFossDebug
adb install -r composeApp/build/outputs/apk/foss/debug/composeApp-foss-debug.apk

# Desktop (UI-полигон с FakePlayerEngine)
./gradlew :composeApp:desktopRun
```

## Структура модулей

```
composeApp/            UI (Compose Multiplatform), Decompose-навигация, точки входа Android/Desktop
core/core-player/      PlayerEngine, PlayerState, DebugStats, EngineRegistry
core/core-upscale/     UpscaleProfile/пресеты, ProfileCodec (импорт/экспорт)
core/core-media/       модель библиотеки, MediaStore-сканер (Android), сэмплы (Desktop)
core/core-settings/    настройки (multiplatform-settings)
engine/engine-media3/  движок №1: Media3/ExoPlayer + GLSL ES-эффекты апскейла (Android)
engine/engine-fake/    FakePlayerEngine для desktop/тестов
```

## Отклонения от ARCHITECTURE.md (осознанные, для v0.1)

- **`engine-mpv` (libmpv) не реализован** — второй движок Фазы 1, требует NDK-сборки `.so` + JNI-моста.
  `EngineType.MPV` заведён, в настройках показан как «скоро».
- **`build-logic` (convention plugins), detekt/ktlint** — пока нет; конфигурация в модульных
  build-файлах поверх version catalog. Вынести при росте числа модулей.
- **`core-detect` (авто-класс контента), AdaptiveController (термал/батарея)** — Фаза 2.
- **Шрифт Inter (variable), Coil 3 (превью-кадры), полный набор жестов
  (свайпы яркость/громкость, пинч)** — TODO Фазы 1.
- Известный нюанс Media3: при активных видеоэффектах `onVideoSizeChanged` может не приходить —
  размер источника берётся из `player.videoFormat` (см. `Media3PlayerEngine.onSourceSizeKnown`).
  Смена эффектов на подготовленном плеере выполняется быстрым re-prepare с восстановлением позиции.
