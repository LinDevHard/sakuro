# Sakuro — архитектура и стек

> Проект: **Sakuro** (`com.rinwave.sakuro`)
> Дата: 2026-07-02
> Статус: зафиксированные решения по стеку. Кода пока нет.
> Связано: [RESEARCH.md](RESEARCH.md) (ресёрч рынка), [DESIGN.md](DESIGN.md) (дизайн-система).

---

## 1. Зафиксированные решения

| Область | Решение |
|---|---|
| Название | **Sakuro** |
| Package / namespace | **`com.rinwave.sakuro`** (applicationId тот же; модули — `com.rinwave.sakuro.*`) |
| Платформа/стек | **Kotlin Multiplatform (KMP)** — общий код, общий UI |
| Таргеты | **Android** (продукт), **Desktop/JVM** (тестирование UI), **iOS** (Фаза 3) |
| UI | **Compose Multiplatform** — единый UI на все таргеты и на оба движка |
| Навигация/компоненты | **Decompose** — component-based навигация + lifecycle (KMP) |
| DI | **Koin** (multiplatform) |
| Изображения/превью | **Coil 3** (Compose Multiplatform) |
| Сборка | **Gradle convention plugins** (`:build-logic`), version catalog |
| Качество кода | **detekt** + **ktlint** через convention plugin, гейт в CI (см. §12) |
| Адаптивность | window size classes + canonical adaptive layouts: телефон/планшет/resizable/фолды (см. DESIGN.md §8) |
| База | **AndroidX** (Media3, DataStore, lifecycle и т.д.) |
| Движки воспроизведения | **Два**, переключаемые в настройках: **libmpv** и **Media3 (ExoPlayer)** |
| Абстракция | Единый интерфейс `PlayerEngine`, две реализации за ним |
| Апскейл | Anime4K / ArtCNN. libmpv — нативно (`.glsl`); Media3 — порт проходов под GLSL ES |
| Флейворы | **Два**: `foss` (F-Droid) и `full` (Play Store). F-Droid-совместимость с первого дня |
| Дистрибуция | Google Play Store + F-Droid |
| Лицензия | Open-source (GPL — из-за линковки libmpv; см. §7) |
| Первая фаза | **Только локальное медиа** (без стриминга/DRM) |
| Дизайн | Премиальный, качественная типографика, иконки **Lucide** (см. DESIGN.md) |

> Примечание: ExoPlayer — это плеер **внутри** Media3, не отдельный движок. «Два движка» = libmpv + Media3/ExoPlayer.

---

## 2. Почему два движка

Пользователь выбирает движок в настройках — оба закрывают разные сценарии:

- **libmpv** — Anime4K/ArtCNN работают из коробки (`.glsl` user-shaders), богатый декод, кроссплатформенность (Android + iOS одним движком). Минус: обёртка над C, лицензия GPL.
- **Media3 (ExoPlayer)** — нативный Android, Apache 2.0, готовые адаптивный битрейт/форматы/DRM-инфраструктура, идеальная интеграция с Compose. Минус: Android-only, шейдеры апскейла надо портировать под GLSL ES; апскейл через `setVideoEffects()` + кастомные `GlEffect`/`GlShaderProgram`.

Единый UI поверх обоих достигается абстракцией `PlayerEngine` (см. §4).

---

## 3. Структура модулей (KMM)

Layout соответствует текущим рекомендациям JetBrains (см. §3.1): модуль `composeApp` с иерархией source set'ов на все таргеты + отдельные KMP-библиотеки логики + `iosApp` (Xcode-обёртка). Общая конфигурация — convention plugins в `build-logic` поверх version catalog.

```
gradle/libs.versions.toml   version catalog (единые версии)
build-logic/                convention plugins (KMP / Compose / Android / flavors)

composeApp/                 приложение: UI + навигация + точки входа
  src/commonMain            Compose UI, Decompose (RootComponent + экраны), Koin-wiring, Coil
  src/androidMain           Android entry (Activity), flavors foss|full → зависит от :engine:media3, :engine:mpv
  src/desktopMain           Desktop entry (JVM) — прогон UI → зависит от :engine:fake (см. §3.2)
  src/iosMain               iOS entry (Фаза 3) → зависит от :engine:mpv

core/                       KMP-библиотеки логики (без UI), подключаются в composeApp
  core-player/              PlayerEngine (interface), PlayerState, tracks, контроллер, DebugStats
  core-upscale/             UpscaleProfile/Preset, пресеты (Anime4K/ArtCNN), импорт/экспорт, AdaptiveController
  core-detect/              ContentClassifier (anime/cartoon/live-action) → Flow<ContentClass> (см. FEATURES.md §1)
  core-media/               модель локальной библиотеки, метаданные, сканер
  core-settings/            выбор движка, пресеты, тема (multiplatform-settings/DataStore)

engine/                     реализации PlayerEngine — отдельные модули (зависят от :core:core-player)
  engine-media3/            Android-only: Media3/ExoPlayer + GlEffect-цепочка апскейла (GLSL ES)
  engine-mpv/               libmpv: Android (NDK .so + JNI), iOS (framework + cinterop); NDK-сборка изолирована
  engine-fake/              FakePlayerEngine — desktop и тесты, без нативных зависимостей

iosApp/                     Xcode-проект-обёртка (Фаза 3)
```

Ключ: **логика — в KMP-модулях `core/*`** (без зависимости от UI и платформы); **UI, навигация (Decompose) и точки входа — в `composeApp`**; **каждый движок — отдельный модуль `engine/*`**, зависящий только от `:core:core-player` (инверсия зависимостей — ядро не знает о движках). Media3 живёт только в `engine-media3` (Android-only), libmpv — в `engine-mpv` (android/ios), fake — в `engine-fake` (desktop/тесты). `composeApp` подключает нужные движки **пофлейворно/потаргетно** и связывает их через Koin. DI инициализируется в точке входа каждого таргета.

### 3.1 Соответствие рекомендациям JetBrains

- **`composeApp` + `iosApp`** — стандартный layout из KMP-визарда JetBrains для проектов с общим Compose-UI (вместо старого `shared` + `androidApp`).
- **Default hierarchy template** — `applyDefaultHierarchyTemplate()`; промежуточные source set'ы (`appleMain` и т.д.) создаёт сам Kotlin.
- **Таргеты** — `androidTarget()`, `jvm("desktop")`, `iosArm64()/iosSimulatorArm64()/iosX64()`.
- **Интерфейсы + DI вместо `expect/actual`** — по гайдам JetBrains `expect/actual` только для истинно платформенных объявлений; логику (включая `PlayerEngine`) держим на интерфейсах и внедряем через Koin. Это заодно упрощает `FakePlayerEngine` на desktop.
- **Version catalog + convention plugins** — единые версии в `libs.versions.toml`, повторяемая конфигурация модулей в `build-logic` (рекомендованный способ масштабирования KMP).

### 3.2 Desktop-таргет — только для тестирования UI

Desktop (JVM) добавлен как быстрый полигон для интерфейсов (запуск без эмулятора, **Compose Hot Reload**). Реального воспроизведения там на старте нет:
- по умолчанию на desktop используется **`FakePlayerEngine`** (мок состояния/прогресса/дорожек) — достаточно, чтобы гонять весь UI, навигацию и дизайн-систему;
- Media3 на desktop недоступен (Android-only); libmpv на JVM теоретически возможен позже, но для целей «тестирование интерфейсов» не требуется.

Это делает desktop дешёвым и не тянет за собой нативные движки в JVM-таргет.

---

## 4. Абстракция движка (единый UI поверх двух движков)

```kotlin
// commonMain
interface PlayerEngine {
    val state: StateFlow<PlayerState>
    val debugStats: Flow<DebugStats>            // stats for nerds (FEATURES.md §4)
    fun load(media: MediaItem)
    fun play(); fun pause(); fun seekTo(ms: Long)
    fun selectTrack(track: TrackSelection)
    fun applyUpscale(profile: UpscaleProfile)   // единая точка для апскейла
    fun release()
}

enum class EngineType { MPV, MEDIA3 }
```

- UI работает только с `PlayerEngine` и `PlayerState` — не знает, какой движок под ним.
- `EngineType` определён в `:core:core-player`; интерфейс `PlayerEngine` там же.
- Каждый модуль `engine/*` предоставляет свой **Koin-модуль** с `PlayerEngineFactory`, помеченной своим `EngineType` (напр. через `named`/qualifier). `composeApp` подключает только те движки, что доступны на текущем таргете/флейворе, и включает их Koin-модули.
- Переключение в настройках = запрос у Koin фабрики по выбранному `EngineType` и пересоздание движка. Ядро (`core-player`) о конкретных движках не знает (инверсия зависимостей).
- `applyUpscale()` инкапсулирует различия: libmpv грузит `.glsl`, Media3 собирает список `GlEffect` и зовёт `setVideoEffects()`.

---

## 5. Апскейл: адаптивный контроллер (ядро продукта)

`AdaptiveController` в `:shared` решает, какой `UpscaleProfile` активен, исходя из:
- модель/класс SoC (наличие NPU, tier производительности),
- текущий тепловой статус (Android `PowerManager.getThermalStatus` / `addThermalStatusListener`; iOS `ProcessInfo.thermalState`),
- уровень заряда и режим энергосбережения,
- фактический FPS/дропы кадров.

Логика: подниматься к более тяжёлому пресету при запасе, деградировать к лёгкому (Anime4K режимы A/B/C) при нагреве/просадке, полностью отключать при критическом термале. Это то, чего нет у DIY-форков и что определяет «включил и работает».

---

## 6. Флейворы и F-Droid (с первого дня)

Базовый `applicationId` / `namespace` — **`com.rinwave.sakuro`**. Два product flavor'а в `composeApp` (Android):

- **`foss`** → F-Droid. Без Firebase/Crashlytics/Play Services/AdMob и любых проприетарных SDK. Краш-репорты — **ACRA** (opt-in) или отсутствуют. Аналитика — нет или self-hosted opt-in.
- **`full`** → Play Store. Может включать opt-in краш-репортинг; без обязательных проприетарных зависимостей ядра.

Требования F-Droid, заложенные сразу:
- 100% FLOSS toolchain, воспроизводимая сборка на серверах F-Droid.
- Нативная сборка libmpv на F-Droid сложна, но проверена (mpv-android там годами) — заложить build-рецепт.
- Разделять зависимости так, чтобы `foss` не тянул ничего проприетарного (проверять `./gradlew :androidApp:fossDebugDependencies`).

---

## 7. Лицензирование

- **libmpv линкуется → продукт становится GPL** (совместимо с open-source целью).
- **Anime4K — MIT**, ArtCNN — открытые (проверить конкретную лицензию перед вендорингом шейдеров).
- **Media3 — Apache 2.0**.
- Итог: репозиторий под **GPLv3** (из-за libmpv). Это ок для FOSS-дистрибуции; закрытых форков ценности не боимся — моат в продукте/UX, а не в коде.

---

## 8. Монетизация (в контексте FOSS)

- Донаты: GitHub Sponsors / Open Collective / Liberapay.
- Опциональная платная «support/pro» версия в Play Store (то же приложение как удобство).
- **Без paywall на фичу** — в FOSS не защитить (весь код открыт). Ранний план «тяжёлый NN-режим только в pro» отменён.

---

## 9. Фазы (актуализировано)

- **Фаза 1 (MVP):** Android, **только локальное медиа**, оба движка (libmpv + Media3) с выбором в настройках, базовые пресеты Anime4K/ArtCNN, премиальный UI (Compose + Lucide), флейворы `foss`/`full`, F-Droid-релиз. Параллельно — **desktop-таргет с `FakePlayerEngine`** для быстрой отладки интерфейсов.
- **Фаза 2:** адаптивный контроллер (термал/батарея), универсальные пресеты, оптимизация под классы SoC.
- **Фаза 3:** iOS-таргет (libmpv через KMM, UI общий на Compose Multiplatform), затем стриминг (свой источник / открытые потоки; DRM исключён).

---

## 10. Открытые технические вопросы

- Compose Multiplatform на iOS — стабильность к моменту Фазы 3 (перепроверить).
- libmpv в KMM: сборка `.so` (Android NDK) и `.framework` (iOS) + JNI/cinterop-мосты.
- Media3-апскейл: где именно в `GlEffect`-цепочке контролировать выходной `Size` (super-resolution vs фильтр), обход известных багов `setVideoEffects()`.
- Хранилище настроек: `multiplatform-settings` vs DataStore (Android) + аналог на iOS.

---

## 11. Библиотеки стека (сводка)

| Библиотека | Роль | Заметки |
|---|---|---|
| **Kotlin Multiplatform** | общий код (логика + UI) | таргеты: Android сейчас, iOS в Фазе 3 |
| **Compose Multiplatform** | единый декларативный UI | общий на обе платформы и оба движка |
| **Decompose** | навигация + lifecycle компонентов | дерево компонентов в `:shared/navigation`, интеграция с Compose; корректный back/state/процесс-смерть |
| **Koin** | внедрение зависимостей | multiplatform; общие модули в `:shared/di`, старт в точке входа платформы |
| **Coil 3** | загрузка изображений/превью | Compose Multiplatform; превью-кадры и обложки в библиотеке |
| **Material3 Adaptive** | адаптивные лейауты | `material3-adaptive`, `-navigation-suite`, window size class (телефон/планшет/resizable) |
| **detekt + ktlint** | качество/стиль кода | через convention plugin + CI (§12) |
| **Gradle convention plugins** | конфигурация сборки | модуль `:build-logic`, поверх version catalog (`libs.versions.toml`) |
| **AndroidX** | платформенная база | Media3, DataStore, lifecycle, core-ktx и т.д. |
| **Media3 (ExoPlayer)** | движок №1 (Android) | Apache 2.0; апскейл через `setVideoEffects()` + `GlEffect` |
| **libmpv** | движок №2 (кросс) | GPL; Anime4K/ArtCNN нативно |

Все зависимости в `foss`-флейворе обязаны быть свободными и без проприетарных транзитивных SDK (Koin/Coil/Decompose/Compose/Media3 — все FOSS, ок для F-Droid).

---

## 12. Качество кода и тулинг

- **detekt** — статический анализ (code smells, сложность, потенциальные баги). Конфиг `config/detekt/detekt.yml`, baseline для легаси.
- **ktlint** — форматирование/стиль (official Kotlin style). Подключается как **`detekt-formatting`** (обёртка ktlint-правил в detekt) — единый прогон, либо отдельный ktlint-плагин; фиксируем один способ, чтобы не дублировать правила.
- Оба — в **convention plugin** (`build-logic`), применяются ко всем модулям одинаково (`:composeApp`, `core/*`).
- **CI-гейт:** detekt + ktlint check блокируют merge; авто-исправление форматирования локально (`ktlintFormat`).
- **Pre-commit hook** (опц.) — быстрый ktlint/detekt на изменённых файлах.
- Compose-специфика: подключить detekt-правила для Compose (naming/стейт/модификаторы), напр. правила `Compose Rules` (mrmans0n) — ловят типичные ошибки composable-функций.
- Единый стиль — `.editorconfig` в корне (ktlint читает его).
