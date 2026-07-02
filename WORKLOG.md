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
