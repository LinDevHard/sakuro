# Sakuro — журнал работ

## 2026-07-05 (сессия 12)

**Порт Anime4K CNN на движок Media3** — фазы 1–2/4 по
`docs/anime4k-media3-port-plan.md`. Замер sakuro-bench показал, что media3-«аниме»
(одноходовой unsharp) почти не даёт выигрыша (VMAF ≈ off), тогда как настоящие
Anime4K-шейдеры в engine-mpv берут VMAF 88–91. Ключевое: это НЕ ML-задача —
веса вшиты в GLSL как `mat4(...)`, нужен лишь исполнительный фреймворк
многопроходного рендер-графа mpv/libplacebo поверх GL-пайплайна Media3. Решение
из плана — дженерик-рантайм формата mpv user-shaders (а не хардкод каждого
шейдера): тогда все 6 (и будущие) `.glsl` работают без правки.

### Фаза 1 — чистое ядро без GL (коммит 481ff5c)

- `RpnExpression` — эвалюатор формул `//!WIDTH/HEIGHT/WHEN` в обратной польской
  записи (арифметика `+ - * /` + сравнения `> < >= <= =`; ссылки на размеры
  текстур `MAIN.w`/`OUTPUT.h`/`conv2d_last_tf.w`). Пример depth-to-space:
  `conv2d_last_tf.w 2 *`.
- `UserShaderPass` + `MpvUserShaderParser` — файл `.glsl` → список проходов по
  директивам `//!DESC/HOOK/BIND/SAVE/WIDTH/HEIGHT/COMPONENTS/WHEN`. Лицензионная
  шапка и всё до первого прохода отбрасываются; `SAVE`/`BIND` по умолчанию =
  `HOOKED` (хукнутая стадия). Незнакомые директивы (`OFFSET`/`COMPUTE`) игнор —
  Anime4K v4.0.1 их не использует.
- `Anime4KGraphPlanner` — статически прогоняет граф от размера входного кадра:
  считает разрешение каждой промежуточной текстуры по RPN, отсекает проходы с
  ложным `//!WHEN`, отдаёт итоговый размер `MAIN` (depth-to-space даёт ×2).
  Стадии `MAIN`/`PREKERNEL`/`NATIVE` (реального скейлера между ними у нас нет)
  и `OUTPUT` (для гейтинга).
- `ShaderPreamble` — на каждый `//!BIND <n>` генерит `<n>_tex/_texOff/_pos/
  _pt/_size` поверх обычного `sampler2D`, чтобы тело `hook()` компилировалось
  БЕЗ правок. ES 3.00, `highp`, единый `v_texcoord` на все входы.
- 15 юнит-тестов (RPN, парсер на реальной структуре Upscale_CNN_x2_S,
  планировщик). detekt чист.

### Фаза 2/4 — GL-рантайм и интеграция (коммит 263fc38)

- `Anime4KShaderProgram` (`BaseGlShaderProgram`) — мини-рантайм рендер-графа
  внутри ОДНОГО `GlEffect` (не плодим эффект на проход — не воюем с
  resolution-negotiation Media3). В `drawFrame` каждый проход рисует
  фулскрин-квад в собственный `GL_RGBA16F` FBO (FP16 обязателен: фичемапы CNN
  выходят за [0,1] и уходят в минус). `MAIN/PREKERNEL/NATIVE/HOOKED` резолвятся
  в живые текстуры (мультибинд `MAIN + conv2d_last_tf` для depth-to-space
  поддержан). Финал — present-проход `MAIN` → выходная текстура Media3 (alpha
  форсируется в 1). Выходной FBO базового класса захватывается через
  `glGetIntegerv(GL_FRAMEBUFFER_BINDING)`. Нет color-renderable FP16 / FBO
  неполон → деградация в passthrough (кадр без апскейла), а не падение
  конвейера.
- `Anime4KGlEffect` — единый эффект, `isNoOp` при пустой цепочке.
- `Anime4KChain` — выбор моделей S/M и канонический порядок
  Clamp→Denoise→Restore→Upscale 1:1 с `MpvUpscaleProperties.buildAnime4kChain`;
  загрузка+парсинг `.glsl` из `assets/anime4k/` (их вендорит engine-mpv, в APK
  ассеты модулей смёрджены). `IOException` (сборка без engine-mpv) → откат на
  legacy.
- `UpscaleEffectChain.build(context, …)` — для ANIME/CARTOON профилей отдаёт
  `Anime4KGlEffect`, иначе прежняя legacy-цепочка (Sharpen/Denoise/Presentation).
  `Media3PlayerEngine` прокидывает `applicationContext`.
- Полная сборка `:composeApp:compileFossDebugSources` зелёная, detekt чист,
  все юнит-тесты зелёные.

### Не сделано / дальше по плану

- **Runtime-проверка на устройстве** (не гонялось): визуальная корректность и
  сверка порт↔mpv через sakuro-bench (фаза 7 — оракул корректности). Дельта
  порт↔mpv должна быть ≪ различий между режимами. Пользователь проверяет сам.
- **Фаза 3** — Denoise использует стадии `PREKERNEL`/`LINELUMA`/`STATSMAX` и
  `COMPONENTS 1`; рантайм дженерик и формально их тянет (всё в RGBA16F), но на
  живом контенте не проверялся.
- **Фаза 5** — детекция FP16/перфа + гейтинг через `AdaptiveController` (не
  пускать M-модели на слабых SoC). Сейчас только внутренняя passthrough-
  деградация при отсутствии FP16.
- **Фаза 6** — многопроходность = N полноэкранных RTT на кадр; профилировать на
  реальном железе (эмулятор рендерит хост-GPU — нерепрезентативно).

## 2026-07-04 (сессия 11)

Премиальный каталог: директории, сортировка, UX в стиле Google Photos.

- **Модель** (`core-media`): `VideoItem.folderName` (bucket-имя папки; пусто →
  раскладывается в `FOLDER_OTHER = "Другое"`). Android — из
  `MediaStore.Video.Media.BUCKET_DISPLAY_NAME`; desktop-сэмплы разложены по
  папкам (Аниме / Фильмы / Camera) с разными датами.
- **`LibraryOrganizer`** — чистые функции: `sortedBy(SortOrder)` (поле
  Дата/Имя/Размер/Длительность/Разрешение × направление, вторичный ключ — имя),
  `toFolders` (группировка по папке, алфавит, обложка + счётчик),
  `toSections` (относительные корзины Сегодня/Вчера/На этой неделе/В этом
  месяце/Ранее — только при сортировке по дате; иначе одна секция без шапки).
  Время вынесено в `expect/actual nowEpochSeconds()` ради тестируемости.
- **`LibraryPreferencesStore`** — сохранение сортировки (multiplatform-settings,
  два ключа, без JSON); выбор переживает перезапуск.
- **`LibraryComponent`** — сегменты Видео/Папки, drill-down в папку, системный
  «назад» через Essenty `BackCallback`, пересборка секций/папок под сортировку.
- **`LibraryScreen`** — сегмент-контрол, кнопка сортировки → `ModalBottomSheet`
  (радио + тумблер направления), липкие заголовки секций через
  `GridItemSpan(maxLineSpan)`, карточки папок (обложка + бейдж-счётчик),
  `AnimatedContent` при переключении вкладок.

Проверка: detekt чист, desktop-компиляция и `compileFossDebugKotlinAndroid` ок,
unit-тесты зелёные (+10: `LibraryOrganizerTest`, `LibraryPreferencesStoreTest`).
Runtime-проверка на устройстве при следующей сессии.

### Фикс обновления каталога

Симптом: файлы, добавленные на устройство вне приложения, не появлялись —
«Обновить» лишь перечитывал MediaStore, а свежие файлы туда ещё не
проиндексированы (особенно `adb push`). Появлялись только после открытия файла
через SAF (запрос по `content://…document…` заставляет MediaProvider
проиндексировать файл).

- `MediaLibrary.requestSystemRescan()` (default no-op; Android — `MediaScannerConnection.scanFile`
  по публичным папкам Movies/DCIM/Download + корню). Кнопка «Обновить» теперь
  форсит скан, затем перечитывает индекс.
- `MediaStoreVideoLibrary` регистрирует `ContentObserver` на
  `Video.EXTERNAL_CONTENT_URI` → авто-`requestRefresh` при любом изменении
  индекса (файлы появляются сами, без кнопки).
- `LibraryComponent`: ручной `refresh()` (rescan+reload) отделён от
  обсервер-`reload()` (только запрос) — иначе петля скан→обсервер→скан; спиннер
  только на первой загрузке, фоновые обновления бесшумны.

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

## 2026-07-03 (сессия 7)

**engine-mpv** (ARCHITECTURE §2) — движок №2, последний большой блок
Фазы 1. Вместо собственной NDK-сборки — prebuilt `dev.jdtech.mpv:libmpv
1.0.0` (форк libmpv-android от Findroid, Maven Central): mpv 0.41.0,
ffmpeg n8.1, четыре ABI. AAR требует compileSdk 36 — подняли (targetSdk
остался 35; AGP 8.10 API 36 поддерживает).

### Что в модуле

- `MpvPlayerEngine` — реализация `PlayerEngine` поверх инстансного API
  MPVLib 1.0.0. Статус выводится из событийных флагов
  (FILE_LOADED + pause/paused-for-cache/eof-reached), позиция/длительность —
  наблюдаемые time-pos/duration (double), дорожки — JSON `track-list`
  (`MpvTrackList`, парсер чистый + 4 теста). `content://` из MediaStore
  открывается через `openFileDescriptor` → `fdclose://<fd>` — у libmpv
  нет ContentResolver. `keep-open=always`: EOF ловим по eof-reached →
  ENDED, play() из ENDED делает seek 0.
- Пресеты (`MpvUpscaleProperties`, 4 теста): применяются свойствами mpv
  **на лету, без re-prepare** (в отличие от Media3) — Upscale →
  scale/cscale=ewa_lanczossharp (фактор не нужен, mpv скейлит к surface),
  Sharpen → sharpen. **Denoise деградирует**: в бандл-ffmpeg libavfilter
  собран без денойз-фильтров (нет hqdn3d/nlmeans/atadenoise), а сломанный
  vf-граф отключает видео-дорожку целиком — проверено и вырезано.
- `VideoSurface.android.kt` — ветка mpv: SurfaceView + SurfaceHolder,
  режимы кадра через keepaspect/panscan (`MpvScaleMode`), жесты/оверлеи
  работают как есть (они выше surface).
- Регистрация в SakuroApplication (Media3 остаётся первым/fallback),
  подпись движка в настройках больше не «скоро».

### Грабли (все пойманы runtime-проверкой на Pixel_9a)

1. **vo=gpu без поверхности фатален**: loadfile до attachSurface →
   `[vo/gpu/android:fatal] Missing surface pointer`, видео-дорожка
   отключается до конца файла (звук идёт). А переключение vo=null→gpu
   при активном видео блокирует core → ANR. Решение — паттерн
   mpv-android: loadfile откладывается до attachSurface (`pendingLoad`).
2. **hwdec на эмуляторе вешает core намертво** (goldfish-декодер не
   отдаёт кадры ffmpeg-мосту; зависает даже чтение свойств из другого
   потока): на goldfish/ranchu/cutf_cvm — `hwdec=no`, на реальном железе
   `mediacodec-copy` (прямой mediacodec рендерит мимо GL — шейдеры бы
   не работали). Заодно debugStats-опрос свойств ушёл с main на
   Dispatchers.Default — getProperty ждёт core-лок.
3. **PiP не пересоздаёт surface, а ресайзит**: на паузе после ресайза
   VO остаётся чёрным — refresh-seek (`seek 0 exact`) в resizeSurface
   перерисовывает кадр. (Тот же трюк в attachSurface для возврата
   к загруженному файлу.)
4. **vf через set_property не задать вообще**: голое `hqdn3d=…` → -4
   (invalid parameter), `lavfi=[…]` принимается, но роняет разбор
   графа. Команда `vf set` парсит как командная строка — но фильтров
   в бандле всё равно нет (см. выше), ветка vf удалена.
5. `MPVLib.create()` nullable — обёрнут в checkNotNull.

### Runtime-проверка (Pixel_9a, libmpv выбран в настройках)

Воспроизведение anime_test_480p: видео+звук, h264 sw-декод (эмулятор),
854x480→1080x2424, 24 fps, оверлей полный (vo=gpu, кодеки, битрейт,
цвет yuv420p bt.601). Пауза/плей, свайп-перемотка (event: seek),
EOF→ENDED→replay, живое переключение Выкл→Anime HD→Anime SD
(scale/sharpen применяются без re-prepare, denoise молча пропущен),
PiP-цикл: живое видео в окне, после разворота кадр на месте, выход
из плеера — release без крэша.

Проверка: detekt чист, 66 unit-тестов (+8), assembleFossDebug +
desktop-компиляция ок.

### Дальше по докам

- Прогон engine-mpv на реальном устройстве (hwdec=mediacodec-copy
  не покрыт эмулятором).
- Anime4K `.glsl` user-shaders для mpv (glsl-shaders) — родной путь
  апскейла вместо ewa_lanczossharp; заодно решит денойз.
- Калибровка порогов FrameContentClassifier на реальном контенте.
- Реальная громкость на desktop (ждёт desktop-движка).

## 2026-07-03 (сессия 8)

**Anime4K user-shaders для engine-mpv** (ARCHITECTURE §4) — родной путь
апскейла по докам вместо ewa_lanczossharp; заодно вернулся Denoise,
который деградировал из-за бандл-ffmpeg без денойз-фильтров (шейдеру
libavfilter не нужен).

### Что сделано

- Вендорены 6 шейдеров **Anime4K v4.0.1** (MIT, bloc97) в
  `engine-mpv/src/main/assets/anime4k/` (~120 КБ): Clamp_Highlights,
  Restore_CNN_S/M, Upscale_CNN_x2_S/M, Denoise_Bilateral_Mode.
  Лицензия — licenses/anime4k/LICENSE.txt.
- `MpvUpscaleProperties.kt` → `buildMpvRenderConfig()`: для пресетов
  с contentClass ANIME/CARTOON цепочка UpscalePass транслируется
  в user-shaders в **каноническом порядке Anime4K** (Clamp → Denoise →
  Restore → Upscale), а не в порядке проходов пресета. Размер CNN
  по силе прохода: Sharpen ≥0.6 / Upscale ≥1.75 → M, иначе S
  (Anime SD → M-модели, Anime HD → S). При активной цепочке свойство
  `sharpen` обнуляется — резкость делает Restore_CNN, иначе двойная
  резкость. Не-аниме контент — старый путь свойствами (Anime4K
  по назначению только для аниме).
- `MpvShaderStore` — mpv читает `glsl-shaders` только с ФС: ассеты
  при первом обращении копируются в filesDir/shaders/anime4k/v4.0.1
  (каталог версионирован, staging+rename, старые версии чистятся).
  Если копия не удалась — деградация до пути свойствами, Sharpen
  не теряется.
- Цепочка ставится свойством `glsl-shaders` (пути через `:`) — на лету,
  без re-prepare, как и остальные свойства. В DebugStats extras —
  строка `shaders` из фактического значения glsl-shaders глазами mpv.

### Runtime-проверка (Pixel_9a, libmpv)

anime_test_480p + Anime SD: рендер живой (известный «синий экран»
Anime4K из RESEARCH.md на mpv 0.41/GLES не воспроизвёлся), dropped 0,
в оверлее цепочка Clamp+Denoise+Restore_M+Upscale_M, ошибок компиляции
шейдеров в logcat нет. Живое переключение: Anime HD → цепочка сменилась
на S-модели, Выкл → цепочка очистилась. holiday_footage (detect
live_action 74%) + Live-action light: шейдеров нет, sharpen свойством.

Проверка: detekt чист, юнит-тесты зелёные (MpvUpscalePropertiesTest
8 вместо 4), assembleFossDebug ок, шейдеры в APK.

### Дальше по докам

- Прогон engine-mpv на реальном устройстве: hwdec=mediacodec-copy
  и **скорость CNN-шейдеров на мобильном GPU** (эмулятор рендерит
  хост-GPU — перф не показателен; возможно, Anime SD на слабых SoC
  надо ограничить S-моделями через AdaptiveController).
- Калибровка порогов FrameContentClassifier на реальном контенте.
- Реальная громкость на desktop (ждёт desktop-движка; libmpv на JVM).

## 2026-07-03 (сессия 9)

**Пользовательские пресеты** (FEATURES.md §2.2) — создание/редактирование/
удаление своих пресетов и импорт/экспорт строкой через буфер обмена.
Достроена работа, начатая в прошлой сессии (UserPresetStore + тесты).

### Что сделано

- `UserPresetStore` (core-upscale, commonMain) — стор пользовательских
  пресетов поверх multiplatform-settings: весь список одним JSON-ключом
  (атомарная перезапись, пресетов единицы). Санитизация на входе
  (границы проходов, builtIn=false, имя), id `user-xxxxxx` никогда
  не затеняет встроенные и «auto», битый payload деградирует в пустой
  список. Импорт всегда создаёт НОВЫЙ пресет (id перегенерируется).
  9 юнит-тестов на MapSettings (multiplatform-settings-test).
- DI: `UserPresetStore` собирается в `appModule`, прокинут
  в `PlayerComponent` и `SettingsComponent` через `AppDependencies`.
- `PlayerComponent.presets` теперь `StateFlow` («Авто» + встроенные +
  пользовательские); user-пресеты принимаются в `applyPreset`
  и резолвятся в адаптивном контуре (пятый flow в combine —
  live-редактирование пресета пересчитывает решение). Удалённый
  выбранный пресет деградирует в OFF.
- Настройки: секция «Свои пресеты» — список со строкой действий
  (изменить/экспорт в буфер/удалить), кнопки «Создать» и «Из буфера»,
  транзиентное сообщение о результате. Редактор — диалог: имя, класс
  контента чипами (для ANIME/CARTOON mpv применит Anime4K), три
  слайдера (апскейл ×1..×4 с шагом 0.25, резкость/деноиз 0..100%);
  описание собирается из цепочки («апскейл ×2 · резкость 39%»).
  Пользовательские пресеты доступны и как «по умолчанию» (радио-список
  теперь реактивный); удаление выбранного по умолчанию сбрасывает
  на «Выкл». Шторка пресетов в плеере получила прокрутку.
- Detekt: constructorThreshold 8 → 9 (PlayerComponent получил девятую
  зависимость конструктором — в духе имеющегося послабления для DI).

### Runtime-проверка (Pixel_9a, libmpv)

Создание в редакторе (имя, чип «Аниме», ×2 + резкость 39%) → пресет
в обоих списках настроек и в шторке плеера; экспорт в буфер → импорт
создал копию, копия удалена; «Anime boost» на anime_test_480p:
в оверлее preset/passes свои, mpv собрал Anime4K-цепочку
Clamp+Restore_S+Upscale_M (S по sharpen<0.6, M по upscale≥1.75 — как
задумано), dropped 0, перемотка/пауза ок; пресет пережил force-stop.
Ошибок компиляции шейдеров в logcat нет.

Грабли: onboarding-оверлей стилуса Gboard на эмуляторе перехватывал
тапы по слайдерам (uiautomator dump его не показал — виден только
на скриншоте), а системное превью буфера обмена (Android 13+) после
экспорта съело тап и открыло Quick Share — закрыть и повторить.

Проверка: detekt чист, юнит-тесты зелёные (71, +9 UserPresetStoreTest),
assembleFossDebug ок.

### Дальше по докам

- Прогон engine-mpv на реальном устройстве: hwdec=mediacodec-copy
  и скорость CNN-шейдеров на мобильном GPU (возможно, ограничивать
  M-модели через AdaptiveController на слабых SoC).
- Калибровка порогов FrameContentClassifier на реальном контенте.
- Реальная громкость на desktop (ждёт desktop-движка; libmpv на JVM).

## 2026-07-03 (сессия 10)

**Закрепление пресета за файлом** (FEATURES.md §1.3/§2.3) — «выбор можно
закрепить для файла»: пин приоритетнее общего дефолта и переживает
перезапуск. Пункты 1–2 очереди (реальное железо, калибровка порогов
на реальном контенте) в автономной сессии недоступны; libmpv на JVM
требует brew install mpv — отложено до подтверждения.

### Что сделано

- `PinnedPresetStore` (core-upscale, commonMain) — карта uri → id
  пресета одним JSON-ключом (как UserPresetStore); порядок вставки
  сохраняется, при переполнении (MAX_PINS=200) вытесняется самый
  старый пин, повторный пин освежает запись. Пустые uri/id
  игнорируются, битый payload деградирует в пустую карту.
  8 юнит-тестов на MapSettings.
- `PlayerComponent`: при открытии пин приоритетнее общего дефолта
  (`initialPresetId()`); битый пин (пресет удалили) снимается
  и открытие идёт по дефолту. `isPinned: StateFlow<Boolean>`;
  пока файл закреплён, `applyPreset` обновляет пин, а НЕ общий выбор.
  `togglePinned()`: снятие пина не трогает текущий выбор сессии —
  дефолт вернётся при следующем открытии.
- `SettingsComponent.deleteUserPreset` чистит пины удалённого пресета
  (`removeAllFor`).
- UI: в шторке пресетов строка «Закрепить за этим файлом» с иконкой
  пина и Switch (розовый акцент при включении).
- DI: `UserPresetStore` + `PinnedPresetStore` сгруппированы
  в `PresetStores` (один узел Koin) — иначе конструктор
  PlayerComponent пробил бы detekt LongParameterList (порог 9
  оставлен как есть).

### Runtime-проверка (Pixel_9a, pm clear → чистое состояние)

anime_test_480p: пин при «Выкл» → выбор Anime HD в шторке обновил пин
(чип Anime HD); sample_video открылся с глобальным «Выкл» (дефолт
не тронут); force-stop → anime_test_480p снова Anime HD (пин
персистентен); снятие пина → переоткрытие с «Выкл». Грабли эмулятора:
авто-скрытие контролов (3.5 c) обгоняет медленный uiautomator dump —
для устойчивых тапов ставить видео на паузу (контролы не прячутся).

Проверка: detekt чист, юнит-тесты зелёные (79, +8 PinnedPresetStoreTest),
assembleFossDebug ок.

### Дальше по докам

- Прогон engine-mpv на реальном устройстве: hwdec=mediacodec-copy
  и скорость CNN-шейдеров на мобильном GPU (возможно, ограничивать
  M-модели через AdaptiveController на слабых SoC).
- Калибровка порогов FrameContentClassifier на реальном контенте.
- Реальная громкость на desktop (ждёт desktop-движка; libmpv на JVM —
  на хосте нет libmpv, нужен brew install mpv).
- (Опц.) пин для папки — v1 закрывает только файл; ключ-схема стора
  это позволит расширить.
