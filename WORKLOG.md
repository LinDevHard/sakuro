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
