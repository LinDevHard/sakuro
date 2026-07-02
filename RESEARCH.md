# Sakuro — ресёрч: видеоплеер с апскейлом (real-time upscaling)

> Проект: **Sakuro** (`com.rinwave.sakuro`)
> Дата: 2026-07-02
> Статус: исследование рынка и технической реализуемости. Кода пока нет.
> Цель проекта: мобильный видеоплеер, ключевая фича — апскейл видео в реальном времени.
> Направление зафиксировано → см. [ARCHITECTURE.md](ARCHITECTURE.md), [DESIGN.md](DESIGN.md), [FEATURES.md](FEATURES.md), [BRAND.md](BRAND.md).

**Принятые решения (сводка):** open-source (GPL), релизы в Google Play + F-Droid; стек **KMP + Compose Multiplatform** (Decompose, Koin, Coil 3, Gradle convention plugins, AndroidX); **два движка** на выбор — libmpv и Media3/ExoPlayer — под единым UI; таргеты Android + Desktop (тестирование UI) + iOS (позже); два флейвора `foss`/`full` с F-Droid-совместимостью с первого дня; старт — только локальное медиа; премиальный дизайн, иконки Lucide; адаптивность (window size classes — телефоны/планшеты/фолды/resizable) и качество кода (detekt + ktlint) с первого дня. Монетизация — донаты + опциональная платная Play-версия (без paywall на фичи).

---

## 1. Краткий вывод (TL;DR)

- **Технология апскейла — не моат.** Всё лучшее в real-time либо open-source (GLSL-шейдеры), либо уже встроено в GPU/SoC (NVIDIA RTX VSR, AMD, MediaTek AI-SR, Arm NSS). Свою нейросеть продакшен-уровня не переплюнуть.
- **Ценность = продукт вокруг апскейла:** UX «включил и работает», адаптация под железо (термал/батарея), платформа/ниша, где нативного решения ещё нет.
- **На ПК ниша фактически закрыта** (VLC + RTX VSR, mpv + ArtCNN бесплатно). **На мобайле готового удобного продукта нет** — это окно, но узкое и закрывается (Arm NSS ~конец 2026, апскейл уходит в SoC).
- **Anime4K устарел** как отдельная технология; актуальный стандарт в комьюнити — **ArtCNN** и **Ani4K**.
- **Для Android базой имеет смысл брать Media3 (Effects API)** — Apache 2.0, готовый декод/стриминг, нативный UX. Но шейдеры под него надо портировать вручную (GLSL ES), и на iOS они не переедут.

---

## 2. Ландшафт рынка апскейла (3 слоя)

### Слой 1. Real-time GLSL-шейдеры (наследники Anime4K) — бесплатно, open-source
- **Anime4K** — набор open-source real-time алгоритмов апскейла/деноиза для аниме. Это НЕ AI-продукт, а GLSL-шейдеры для mpv/IINA/VLC/Magpie. Сам по себе — не продукт, а движок.
- **Anime4K устарел.** В mpv-комьюнити его заменили:
  - **ArtCNN** — лучше обучен под аниме, активно развивается (в отличие от заброшенного FSRCNNX). Лучший вариант для luma-doubling, особенно HD-контент.
  - **Ani4K** — силён на SD и плохих WEB/Blu-ray рипах: агрессивно убирает артефакты компрессии, сохраняет стиль.
- Готовые сборки: **MPV Anime Build v4.3** — авто-детект аниме/live-action, адаптивный апскейл (ArtCNN/Anime4K/NNEDI3/FSRCNNX).
- Работают на любом GPU через GLSL, без перекодирования. Это главный «бесплатный» конкурент.

### Слой 2. Аппаратный апскейл — встроен в железо, бесплатно для владельца
- **NVIDIA RTX VSR** — AI на tensor-ядрах RTX 30/40. Работает в VLC, PotPlayer, Chromium (Chrome/Edge). Реально заметно улучшает картинку, вплоть до 4K.
- **AMD VSR** — spatial (без AI), слабее Nvidia. RX 5000/6000/7000/9000.
- **MediaTek AI-SR 3.0** — в ~2 млрд ТВ (70% рынка smart TV). Апскейл SD/HD/FHD/4K в реальном времени прямо в SoC, энергоэффективно.
- **Тренд:** апскейл становится функцией платформы/железа, а не приложения. Через 2–3 года «встроено везде».

### Слой 3. Offline AI-апскейл — качество выше, но НЕ real-time (другой рынок)
- **Video2X** (Real-ESRGAN + Waifu2x + Real-CUGAN), **Upscayl** (open-source), **Waifu2x Extension GUI**, **Topaz Video AI**, **UniFab**.
- Это перекодирование (минуты–часы на видео), рынок реставрации, а не плеера.

---

## 3. Мобильная сфера (основной фокус проекта)

### Что уже есть на мобильных (конкуренты)
- **Anime4K на телефоне почти не работает из коробки.** GLSL-шейдеры Anime4K в mpv-android дают «синий экран» — нужны хаки/фиксы совместимости.
- **Официального приложения-плеера с апскейлом в App Store / Google Play фактически НЕТ.** Это ключевой факт — ниша нормального массового продукта не занята.
- **VLC / mpv-android** технически поддерживают шейдеры, но это гиковский путь: ручная настройка конфигов, не для массового пользователя.

### Железо: real-time на телефоне реально
- Исследования (Mobile AI Challenge): video super-resolution **до 80 FPS на мобильном GPU**; **~500 FPS / 0.2 Вт на NPU** (MediaTek Dimensity 9000). 4x-апскейл в реальном времени на NPU — реален и энергоэффективен.
- **Apple MetalFX** — neural spatial upscaler + non-AI temporal, доступен на iPhone/iPad (заточен под игры).
- **Qualcomm AI Frame Fusion (AIFF)** — апскейл + frame generation на GPU/NPU (Snapdragon, до ~60 TOPS).
- **MediaTek AI-SR** — апскейл на SoC.
- **Arm Neural Super Sampling (NSS)** — DLSS-подобное для Mali GPU: 540p→1080p за 4 мс/кадр, −50% нагрузки на GPU. Массово с **конца 2026**. В 2026 добавят Neural Frame Rate Upscaling.
  - ⚠️ NSS/MetalFX заточены под **рендеринг игр** (есть motion vectors). Видео-апскейл — это post-decode задача (ближе к шейдерам/video-SR сетям). Разные пайплайны, не путать.

### Риски именно на мобайле
1. **Термал и батарея** — постоянный 4x-апскейл full-screen греет телефон и жрёт заряд. 20-мин серия → троттлинг, горячий корпус. Главный UX-киллер.
2. **iOS-ограничения** — Apple жёстко относится к энергопотреблению/фоновой обработке. Нужна сильная оптимизация.
3. **Фрагментация Android** — на слабых SoC без нормального NPU апскейл тормозит → нужен fallback на лёгкие шейдер-пресеты (режимы Anime4K A/B/C).
4. **Контент** — на экране 6" выигрыш заметен в основном на **SD/низкобитрейтном/аниме**. На хорошем 1080p разница почти не видна → сужает ЦА.

---

## 4. Куда есть выход (ниши)

Прямая конкуренция с VLC/mpv/RTX бессмысленна. Работают узкие входы:
- **Платформы без нативного апскейла** — мобайл (iOS/Android), где RTX недоступен. Спрос: аниме, ретро, низкобитрейтные стримы.
- **Нишевый контент/комьюнити** — аниме: движок бесплатный, комьюнити есть, но хочет «включил и работает».
- **B2B/встраивание** — SDK апскейла для стриминга, видеонаблюдения, ТВ-приставок.
- **«Всё в одном»** — плеер + апскейл + шумодав + интерполяция кадров + авто-пресеты под контент. Ценность = UX, которого нет у open-source.

---

## 5. Техническая реализуемость на Android: Media3

### Да, штатно возможно через Effects API
Media3 (androidx.media3, наследник ExoPlayer) с ~1.2 имеет **Effects API для плейбэка**:
```kotlin
exoPlayer.setVideoEffects(listOf(pass1, pass2, ...))
```
- Пишешь свой `GlShaderProgram` (через `BaseGlShaderProgram` — берёт на себя аллокацию выходных текстур), оборачиваешь в `GlEffect` как фабрику.
- **Настоящий апскейл возможен:** в `configure()` возвращается выходной `Size` — можно отдать текстуру больше входной (super-resolution, а не просто фильтр).
- Многопроходность Anime4K/ArtCNN → **цепочка из нескольких `GlEffect`** (каждый проход = отдельный эффект).
- **Адаптивный контроллер** просто меняет список эффектов в `setVideoEffects()` на лету.

### Подводные камни
1. **Шейдеры Anime4K НЕ переносятся как есть.** Файлы `.glsl` написаны под user-shader формат mpv (`//!HOOK MAIN`, текстуры `HOOKED`/`LUMA`, RAVU). Media3 ест обычные GLSL ES фрагментные шейдеры → математику проходов надо **переписать вручную** под GLSL ES (алгоритм открыт, но это работа).
2. **DRM/защищённый контент — стоп.** На secure-декодере Media3 рендерит в protected surface, `setVideoEffects()` там не работает. **Netflix/Prime/YouTube-Premium и прочий DRM апскейлить нельзя** (ни через Media3, ни вообще). Стриминг = только свой источник / открытые потоки.
3. **Шероховатости API.** В трекере Media3 висят баги про `setVideoEffects()` (чёрный экран в отдельных layout'ах, краши с некоторыми эффектами). API живой, но не bulletproof.

### Media3 vs libmpv

| | Media3 (Effects API) | libmpv |
|---|---|---|
| Лицензия | **Apache 2.0** — чисто коммерч. | LGPL/GPL — сложнее для закрытого продукта |
| Стриминг/DRM/адаптив | из коробки (ExoPlayer) | руками |
| Нативность Android/Compose | идеальная | обёртка над C |
| Anime4K из коробки | ❌ портируешь под GLSL ES | ✅ работает нативно |
| Переиспользование на iOS | ❌ ноль (iOS → Metal) | ✅ один движок на обе платформы |

**Вывод:** если Android — приоритет №1, брать **Media3** (Apache + готовый стриминг + нативный UX перевешивают). Но шейдеры под Media3 (GLSL ES) на iOS не переедут — там Metal Shading Language. libmpv наоборот: один движок на оба, но GPL и менее нативно. Решение по iOS отложить до Фазы 3.

---

## 6. Архитектура (разделение движка и оболочек)

```
┌─────────────────────────────────────────────┐
│  UI-оболочка (нативная)                        │
│  iOS: SwiftUI   │   Android: Kotlin/Compose     │
├─────────────────────────────────────────────┤
│  Ядро апскейла                                 │
│  ├─ Декод: Media3/ExoPlayer (Android)          │
│  │         AVPlayer/libmpv (iOS/кросс)          │
│  ├─ Апскейл-пайплайн (абстракция бэкенда)      │
│  │    GLSL ES (Android)  │  Metal (iOS)          │
│  ├─ Движки: Anime4K/ArtCNN (shader) + NN-модель │
│  └─ Адаптивный контроллер (термал/батарея/FPS)  │
└─────────────────────────────────────────────┘
```
**Адаптивный контроллер — сердце продукта.** Решает: какой пресет включить под SoC/термал/заряд, когда деградировать до лёгкого шейдера, когда вырубить. Именно этого нет у open-source и за это платят.

---

## 7. Рекомендация: фазировать (выбран максимальный scope — кросс, универсальный контент, локал+стриминг)

Не отказываясь от финальной цели (универсальный кросс-плеер), собрать MVP на дешёвом срезе:

- **Фаза 1 (MVP, ~2–3 мес):** Android + локальные файлы + аниме/SD-пресеты (Anime4K/ArtCNN, портированные под GLSL ES) на **Media3**. Проверяет главную гипотезу — «платят ли за удобный апскейл на телефоне» — за минимум денег.
- **Фаза 2:** адаптивный контроллер + NN-модель для универсального контента (тяжёлый режим только на топ-SoC с NPU).
- **Фаза 3:** iOS-оболочка (движок общий) + стриминг. Тогда честно решить: переписать шейдеры под Metal vs. перейти на общий libmpv.

### Открытые вопросы (определяют технику и бизнес)
1. **Стриминг** — свой контент/каталог или поверх чужих сервисов? DRM (Netflix/YouTube) не даст трогать декодированный кадр → стриминг = свой источник / открытые потоки.
2. **Монетизация** — подписка / разовая покупка / freemium? От этого зависит, где ставить paywall (напр. тяжёлый NN-режим — только в pro).

---

## 8. Источники

### Рынок / апскейл в целом
- Anime4K (GitHub) — https://github.com/bloc97/Anime4K
- ArtCNN vs Anime4K (обсуждение) — https://github.com/dyphire/mpv-config/discussions/78
- MPV Anime Build v4.3 — https://chinna95p.github.io/mpv-anime-build/
- Открытые видео-апскейлеры 2026 — https://www.aiarty.com/ai-video-enhancer/open-source-video-upscaler-enhancer.htm

### Аппаратный апскейл
- NVIDIA RTX VSR — https://blogs.nvidia.com/blog/rtx-video-super-resolution/
- AMD Video Upscaler (Tom's Hardware) — https://www.tomshardware.com/pc-components/gpus/amd-video-upscaler-arrives-for-rx-7000-gpu-owners-yearning-for-nvidias-rtx-video
- MediaTek AI Super Resolution — https://www.mediatek.com/press-room/mediatek-ai-super-resolution-to-improve-streaming-content-on-hisense-smart-tvs

### Мобайл
- Anime4K на Android (Issue #99) — https://github.com/bloc97/Anime4K/issues/99
- Power Efficient Video SR on Mobile (arXiv) — https://arxiv.org/pdf/2211.05256
- Real-Time Video SR on Smartphones, Mobile AI 2021 (arXiv) — https://arxiv.org/pdf/2105.08826
- Arm Neural Super Sampling / Neural Graphics — https://newsroom.arm.com/news/arm-announces-arm-neural-technology
- Arm NSS 540p→1080p 4ms (BigGo) — https://biggo.com/news/202508160713_Arm_Neural_Graphics_Technology_Mobile_Gaming

### Media3 / Android-реализация
- Media3 GlEffect (androidx/media) — https://github.com/androidx/media/blob/release/libraries/effect/src/main/java/androidx/media3/effect/GlEffect.java
- Media3 editing app guide (setVideoEffects, custom shaders) — https://developer.android.com/media/implement/editing-app
- Media3 setVideoEffects issue (известные баги) — https://github.com/androidx/media/issues/791
- Anime4K GLSL instructions — https://github.com/bloc97/Anime4K/blob/master/md/GLSL_Instructions_Advanced.md
