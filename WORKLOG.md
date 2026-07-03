# Sakuro — журнал работ

## 2026-07-02

Собрана рабочая v0.1 (до этого в репозитории были только доки):

- KMP + Compose Multiplatform (Android + desktop), Decompose + Koin + Lucide.
- Движок Media3/ExoPlayer с GLSL ES-эффектами апскейла через `setVideoEffects`
  (Presentation + кастомные Sharpen/Denoise `GlEffect`), FakePlayerEngine на desktop.
- Экраны: библиотека, плеер (жесты тап/двойной тап/долгий тап), настройки, debug-оверлей.
- Сборка: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :composeApp:assembleFossDebug`.

## 2026-07-03

Репозиторий переведён под git (initial commit v0.1). Реализованы два недостающих
блока из ARCHITECTURE §5 и FEATURES §1 + их интеграция в плеер:

- **`:core:core-detect`** — `ContentClassifier` → `Flow<ContentDetection>`
  (класс + уверенность + источник). Первый слой — `FilenameContentClassifier`:
  теги фансаб-групп, ключевые слова, паттерн релиз-нейминга. Анализ кадров —
  следующий слой (заместит результат более уверенным).
- **`AdaptiveController`** (core-upscale) — чистая state-machine: термальные полы
  (moderate → без деноиза, severe → апскейл ≤1.5×, critical → выкл),
  энергосбережение/низкий заряд → минимум один шаг деградации, деградация по
  дропам кадров с гистерезисным восстановлением. Выбор пользователя не трогается.
- **`DeviceStatusMonitor`** — Android-реализация (thermal listener API 29+,
  броадкасты батареи/power-save), статическая заглушка для desktop.
- **`PlaybackHealthTracker`** (core-player) — % дропнутых кадров за окно
  между снимками `DebugStats` относительно ожидаемых кадров (fps × время).
- **Интеграция в `PlayerComponent`**: адаптивный контур `combine(debugStats,
  deviceStatus, selectedPreset, detection)`; движок дёргается только при
  реальной смене эффективной цепочки (applyUpscale = re-prepare на Media3).
- **Пресет «Авто»**: цепочка подбирается по классу контента; шторка пресетов
  подсвечивает выбор пользователя, debug-оверлей показывает detect/adaptive.

Проверка: 25 unit-тестов (desktopTest) зелёные; `assembleFossDebug` и
десктоп-компиляция успешны. Runtime-проверка на эмуляторе не выполнялась
(автономный запуск) — стоит прогнать на Pixel_9a при следующей сессии.

### Дальше по докам

- engine-mpv (libmpv через NDK) — движок №2.
- Второй слой детекции — по сэмплам кадров.
- Свайп-жесты яркость/громкость, горизонтальный свайп-перемотка, пинч fit/fill/zoom.
- build-logic (convention plugins) + detekt/ktlint, шрифт Inter, Coil-превью.

## 2026-07-03 (сессия 2)

**Жесты плеера (FEATURES §3.1)** — свайпы и пинч поверх существующих тапов:

- Чистая математика жестов в `ui/gestures` (без Compose-зависимостей):
  `SeekSwipeSession` (ширина экрана = ±90 с), `LevelSwipeSession` (высота =
  весь диапазон 0..1), `PinchSession` (гистерезис ×1.3 на шаг режима) —
  14 unit-тестов в commonTest.
- `detectPlayerGestures` — свой обработчик на `awaitEachGesture`: до порога
  touchSlop события не потребляются (тапы/long-press в соседнем pointerInput
  живут как раньше), потреблённые события (long-press 2×) отменяют жест.
- `PlayerSystemControls` (expect/actual): Android — яркость через атрибуты
  окна (сброс при выходе с экрана), громкость через AudioManager/STREAM_MUSIC;
  desktop — заглушка в памяти.
- `ScaleMode` FIT/FILL/ZOOM → `PlayerView.resizeMode`, пинч циклит режимы,
  тактильный отклик на смене режима и long-press.
- Центральный бейдж-индикатор (время+дельта / иконка+прогресс+% / режим кадра),
  задерживается 600 мс после жеста; настройка «Жесты в плеере» (вкл по умолч.).

**Runtime-проверка на эмуляторе Pixel_9a** (двумя проходами, до/после фикса):

- Детекция: `detect anime 75% (filename)` в оверлее на обоих движках.
- Пресет «Авто»: chip → Anime HD, оверлей `pass upscale ×1.5 / sharpen 0.5`,
  выход реально апскейлится `854x480 → 1281x720`. Смена пресета на
  подготовленном плеере (re-prepare) — без залипаний.
- Адаптивный контур: `cmd thermalservice override-status 4` → оверлей
  `adaptive L3 термал: critical`, выход падает до `854x480 → 854x480`
  (цепочка выключена). После reset возвращается.
- Жесты: свайп-перемотка (+50 с при 600 px — совпадает с математикой),
  громкость 65% и яркость 17% с бейджами. Пинч через adb не проверить
  (нет мультитача) — только unit-тесты.
- Пойманный на эмуляторе баг: бейдж жеста рисовался под контролами
  (play-кнопка в центре его перекрывала) — вынесен выше по z-order.

Коммиты: `29887bc` (жесты), `d385e94` (z-order фикс).

### Дальше по докам

- Второй слой детекции — по сэмплам кадров (заместит filename-результат).
- engine-mpv (libmpv через NDK) — движок №2, самый большой блок.
- build-logic (convention plugins) + detekt/ktlint, шрифт Inter, Coil-превью.
- Опционально: PiP по свайпу вниз, настройка чувствительности жестов.

## 2026-07-03 (сессия 3)

**Детекция по сэмплам кадров (FEATURES §1, второй слой)** — коммит `bda4d80`:

- `FrameSampler` (интерфейс, не зависит от движка) + `FrameSample`
  (ARGB-пиксели уменьшенного кадра). Android-реализация
  `RetrieverFrameSampler`: MediaMetadataRetriever, 5 кадров равномерно
  из 10..90% длительности (по краям логотипы/титры), ~96 px по большей
  стороне (`getScaledFrameAtTime`, фолбэк для API 26). Desktop-реализации
  пока нет — слой просто молчит.
- `FrameContentClassifier`: три признака «рисованности», устойчивые
  к даунскейлу — доля плоских заливок (соседние пиксели совпадают),
  бедность квантованной палитры (4 бит/канал), «пустая середина»
  гистограммы градиентов (у аниме заливки + жёсткие контуры, но нет
  полутоновых переходов живой съёмки). Взвешенная сумма → аниме/мульт
  (порог 0.55, деление по насыщенности) или live action (≤0.30);
  середина шкалы = сигналы противоречат = ничего не эмитим.
  Уверенность 0.6..0.85 по удалению от порога. Пороги v1 — по синтетике,
  калибровка на реальной библиотеке впереди.
- `CompositeContentClassifier`: слои параллельно, наружу — только результат
  не хуже показанного (кадры замещают имя файла, менее уверенное
  противоречие отбрасывается, UNKNOWN не эмитится).
- DI Android: filename + frames в композите; PlayerComponent не менялся
  (работает через интерфейс).

Проверка: 48 unit-тестов зелёные (9 новых в core-detect), `assembleFossDebug`
и десктоп-компиляция ок. Runtime на Pixel_9a:

- `sample_video.mp4` (копия testsrc-паттерна с нейтральным именем, filename
  молчит) → `detect anime 82% (frames)`, «Авто» → Anime HD с реальным
  апскейлом. Плоские заливки testsrc корректно читаются как «рисованное».
- `holiday_footage.mp4` (mandelbrot: богатая палитра, сплошные полутона) →
  `detect live_action 74% (frames)`, «Авто» → Live-action light.
- `anime_test_480p.mp4` (в имени «anime») → filename 75% замещается
  frames 82% — путь замещения работает и вживую.
- Тестовые ролики оставлены на эмуляторе (/sdcard/Movies/) для будущих сессий.

### Дальше по докам

- engine-mpv (libmpv через NDK) — движок №2, самый большой блок.
- build-logic (convention plugins) + detekt/ktlint, шрифт Inter, Coil-превью.
- Desktop-реализация FrameSampler (когда появится реальный движок).
- Калибровка порогов детекции на реальном контенте (не синтетике).
- Опционально: PiP по свайпу вниз, настройка чувствительности жестов.

## 2026-07-03 (сессия 4)

Инфраструктура сборки + шрифт Inter (пункт 2 очереди работ).

### build-logic + detekt/ktlint (коммиты 2633860, 087ed97)

- **build-logic** (included build, precompiled script plugins):
  - `sakuro.kmp.library` — androidTarget + jvm("desktop"), JVM 17,
    default hierarchy, kotlin.test в commonTest; модулю остаётся
    namespace и зависимости.
  - `sakuro.android.library` — Android-only модули (движки).
  - `sakuro.detekt` — detekt 1.23.8 + detekt-formatting (правила ktlint),
    `autoCorrect = true`, общий конфиг `config/detekt/detekt.yml`.
- Все 8 модулей переведены на convention plugins: из каждого
  build.gradle.kts ушло ~20 строк повторяющегося boilerplate
  (compileSdk/minSdk/jvmTarget/compileOptions).
- Конфиг detekt поверх дефолтов: послабления под Compose
  (LongMethod/LongParameterList/CyclomaticComplexMethod/FunctionNaming
  игнорируют @Composable), MagicNumber выключен (пороги детекции и
  жестов читаются лучше на месте), maxLineLength 120,
  constructorThreshold 8 (DI-компоненты Decompose).
- Прогон по кодовой базе: ~40 находок. autoCorrect поправил форматирование
  (trailing commas, wrapping), длинные строки развёрнуты руками,
  `FrameFeatures.extract` избавлен от лишней вложенности (локальная
  `countPair`), `powerFloor` в AdaptiveController упрощён. Точечные
  `@Suppress` с обоснованием: конечный автомат жестов (сложность —
  свойство задачи), best-effort catch в RetrieverFrameSampler.

### Шрифт Inter (коммит d89be16, DESIGN.md §3)

- Inter 4.1: статические Regular/Medium/SemiBold/Bold (~1.6 МБ) в
  `composeResources/font/`. Вариативный TTF не годится: у ресурсного
  `Font()` в CMP нет параметра осей — все веса рисовались бы одним.
- Вся Typography (15 стилей M3) на Inter, прежние letterSpacing
  сохранены. OFL-лицензия — `licenses/inter/LICENSE.txt` (F-Droid).

Проверка: detekt чист по всем модулям, 48 unit-тестов зелёные,
`assembleFossDebug` + desktop-компиляция ок; APK установлен на Pixel_9a —
библиотека рендерится Inter'ом, кириллица на месте, без фолбэка.

### Дальше по докам

- engine-mpv (libmpv через NDK) — движок №2, самый большой блок.
- Coil-превью кадров в библиотеке (сейчас иконка-заглушка).
- Desktop-реализация FrameSampler; калибровка порогов детекции
  на реальном контенте.
- Опционально: PiP по свайпу вниз, настройка чувствительности жестов.

## 2026-07-03 (сессия 5)

Coil-превью в библиотеке + desktop FrameSampler (пункты 2 и 3 очереди).

### Превью кадров в библиотеке (коммит ff8654e, ARCHITECTURE: Coil 3)

- **coil-compose + coil-video 3.3.0** (androidMain). Свежее нельзя:
  3.4.0/3.5.0 собраны Kotlin 2.3/2.4 со `strictly`-констрейнтом stdlib —
  метаданные не читаются нашим Kotlin 2.1.21 (Internal compiler error
  уже на этапе type checkers). 3.5.0 вдобавок требует compileSdk 36.
  Зафиксировано комментарием в libs.versions.toml; апгрейд Coil пойдёт
  вместе с апгрейдом Kotlin.
- `VideoThumbnail` expect/actual: Android — AsyncImage c ImageRequest
  `videoFramePercent(0.2)` (начало ролика часто чёрное/с логотипами),
  crossfade; desktop — no-op, снизу остаётся прежний плейсхолдер
  (SampleVideoLibrary отдаёт fake://-URI, видео-декодера в Coil на JVM нет).
- `SakuroApplication` реализует `SingletonImageLoader.Factory` и
  регистрирует `VideoFrameDecoder` — без него Coil видео не понимает.
- В `VideoCard` превью рисуется поверх плейсхолдера (`matchParentSize`),
  бейдж длительности — после превью в z-order.
- Runtime-проверка на Pixel_9a: все три тестовых ролика показывают
  реальные кадры (testsrc-палитра, mandelbrot), скругления карточки
  и бейдж поверх — ок.

### Desktop FrameSampler (коммит bd4db03, FEATURES §1)

- `FfmpegFrameSampler` (desktopMain core-detect): системные ffmpeg/ffprobe
  через ProcessBuilder — ffprobe даёт длительность и размеры (csv),
  ffmpeg отдаёт по кадру на позицию сырым ARGB в pipe. Та же сетка,
  что у RetrieverFrameSampler: равномерно в 10..90% длительности, ~96px.
- Best-effort по контракту FrameSampler: нет бинарей в PATH, не-локальный
  URI (fake://), битый файл, таймаут (15 с) — пустой список, слой молчит.
- Подключён в desktop main.kt тем же CompositeContentClassifier, что на
  Android: filename отвечает сразу, кадры замещают не-худшим результатом.
- 8 тестов: чистые функции (csv-парсинг, scale, ARGB-байты→пиксели,
  locale-независимый формат секунд) + интеграционный на сгенерированном
  testsrc-ролике (сам скипается без ffmpeg). Всего в проекте 56 тестов.
- detekt: у FunctionNaming дефолтные excludes не знают кастомный
  source set `desktopTest` — расширены в config/detekt/detekt.yml.

Проверка: detekt чист, 56 unit-тестов зелёные, assembleFossDebug +
desktop-компиляция ок, APK проверен на Pixel_9a.

### Дальше по докам

- engine-mpv (libmpv через NDK) — движок №2, последний большой блок.
- Калибровка порогов FrameContentClassifier на реальном контенте.
- Опционально: PiP по свайпу вниз, настройка чувствительности жестов,
  реальная громкость на desktop.

## 2026-07-03 (сессия 6)

Жесты — «мелочи» из FEATURES §3.1/3.2: PiP и чувствительность свайпов.

### PiP при сворачивании (FEATURES §3.1)

- По докам «(опц.) свайп вниз — PiP/сворачивание», но вертикальный свайп
  занят яркостью/громкостью — реализовано как PiP при сворачивании
  приложения во время воспроизведения: на Android 12+ авто-вход
  (`setAutoEnterEnabled`), раньше — `onUserLeaveHint`. Манифест:
  `supportsPictureInPicture` (configChanges уже покрывали PiP-ресайз).
- `PictureInPicture` expect/actual в composeApp: `PipEffect(isPlaying,
  videoWidth, videoHeight)` сообщает активити состояние плеера (PiP
  только с экрана плеера и только при воспроизведении; аспект окна из
  размеров видео, зажат в 1:2.39..2.39:1), `rememberIsInPip()` /
  `isInPipNow()` — текущий режим. Android-мост — `PipBridge`
  (StateFlow в обе стороны с MainActivity); desktop — no-op.
- В PiP рисуется только `VideoSurface`: жесты, контролы, бейджи,
  debug-оверлей и preset-шит скрыты (guard'ы по `inPip`, сам surface
  не пересоздаётся).
- **Грабля 1:** `lifecycle.doOnPause { engine.pause() }` останавливал
  видео при входе в PiP (активити в PiP «на паузе») — теперь пауза
  только `if (!isInPipNow())`; система шлёт onPictureInPictureModeChanged
  до onPause, порядок гарантирует корректный снимок.
- **Грабля 2:** при активных videoEffects GL-конвейер Media3 привязан
  к размеру surface на момент prepare: после входа в PiP окно чёрное,
  после разворота кадр рисуется маленьким в углу. Лечится re-prepare
  той же цепочки (`PlayerComponent.onPipModeChanged` →
  `engine.applyUpscale(appliedProfile)`) на каждой смене PiP-режима;
  в UI — LaunchedEffect по фронту `inPip`.

### Чувствительность свайпов (FEATURES §3.2)

- `SakuroSettings.gestureSensitivity` (0.5..2, дефолт 1, persist через
  multiplatform-settings), сеттер зажимает диапазон.
- `SeekSwipeSession`/`LevelSwipeSession` получили множитель
  `sensitivity` — вся математика по-прежнему чистая и покрыта тестами
  (+2 теста, всего 58). Пинч не трогаем: у него пороговые шаги.
- Settings: слайдер «Чувствительность свайпов» (0.5×..2× с шагом 0.25,
  выключен при выключенных жестах) под свитчем жестов.
- Runtime-проверка на Pixel_9a: слайдер 1.75× → свайп на полширины
  даёт +79с вместо +45с (упёрся в конец минутного ролика, бейдж +0:56);
  PiP: Home во время воспроизведения → окно с живым видео, разворот
  обратно — корректный полноэкранный кадр с активным пресетом
  (заодно живьём видно frames-детекцию: anime 82%, Anime HD ×1.5).

Проверка: detekt чист, 58 unit-тестов зелёные, assembleFossDebug +
desktop-компиляция ок, PiP и слайдер проверены на Pixel_9a.

### Дальше по докам

- engine-mpv (libmpv через NDK) — движок №2, последний большой блок.
- Калибровка порогов FrameContentClassifier на реальном контенте.
- Опционально: реальная громкость на desktop (ждёт реального
  desktop-движка — FakePlayerEngine звука не имеет).
