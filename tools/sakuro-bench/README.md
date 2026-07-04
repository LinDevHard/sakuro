# sakuro-bench

Независимый инструмент бенчмаркинга качества апскейл-режимов Sakuro по кадрам.
Никак не связан с Gradle-проектом — отдельный Python-CLI, держит свой venv.

## Зачем

Sakuro применяет апскейл в реальном времени разными режимами (bilinear,
mpv-скейлеры `ewa_lanczossharp`, Anime4K-CNN, кастомные Media3-эффекты).
Инструмент отвечает на вопрос **«какой режим объективно лучше на этом контенте»**
через набор метрик, а не на глаз.

## Методология (коротко)

Full-reference метрике нужен **эталон** того же разрешения. При апскейле «идеала»
нет, поэтому:

* **FR через downscale от мастера.** Берём высокий оригинал (720p/1080p — 4K не
  обязателен) → даунскейлим до входа плеера → апскейлим режимом → сравниваем с
  оригиналом. Честный ground-truth. **Подгоняй коэффициент под боевой** (для
  «1080→4K ×2» бери 1080-мастер ÷2 → 540 → ×2). Деградацию делай реалистичной
  (`--degrade realistic`: blur+noise), чтобы режим не «обращал» чистый bicubic.
* **NR без эталона.** Когда оригинал сам минимального разрешения — только
  no-reference прокси (резкость/ВЧ/шум/ringing). Относительные, не MOS.

Метрики читать **панелью**, не одним числом: пиксельные FR (PSNR/SSIM) штрафуют
CNN за «выдуманную» деталь и шарпен. **VMAF-NEG** ловит, когда прирост — это
шарпен-читерство, а не восстановление.

Подробный разбор углов (offline-mpv vs on-device, темпоралка, привязка к
content-классу как оракул для детектора) — в истории обсуждения.

## Установка

Ничего ставить руками не надо — лаунчер сам создаёт `.venv` при первом запуске.
Нужны в системе: **ffmpeg с libvmaf** (`ffmpeg -filters | grep vmaf`) и **python3**.

```bash
cd tools/sakuro-bench
./sakuro-bench --version
```

## Сценарий 1 — реальные скриншоты с устройства (главный)

Достовернее всего: меряется настоящий пайплайн (оба движка, GPU, FP16-CNN).

```bash
# 1) в Sakuro пауза на кадре, режим, контролы спрятаны — снять каждый режим:
./capture-device.sh off       caps/
./capture-device.sh mpv_ewa   caps/
./capture-device.sh anime4k_m caps/

# 2a) только NR (эталона нет):
./sakuro-bench capture-compare --captures caps --out report

# 2b) строгий FR: в плеере играем 480p, сделанный ИЗ master_1080p.png,
#     эталон = мастер:
./sakuro-bench capture-compare --captures caps --ref master_1080p.png --out report
```

Требования к скриншотам: один и тот же кадр (пауза, тот же таймкод), одинаковый
scale-mode (fit), без UI. Кроп/выравнивание/нормализацию тул делает сам
(`--crop x,y,w,h`, `--no-letterbox`, `--expand-range` при TV-range).

Если `screencap` отдаёт чёрное — SurfaceView ушёл в hardware overlay; включи
*Disable HW overlays* в Developer Options.

## Сценарий 2 — синтетический self-test (baseline-скейлеры)

Быстрая проверка движка метрик и baseline ffmpeg-скейлеров без устройства:

```bash
./sakuro-bench synth --master master_1080p.png --scale 2 \
    --degrade realistic --modes bilinear,bicubic,lanczos,spline --out report
```

## Сценарий 3 — offline mpv + Anime4K (`synth-mpv`)

Гоняет **те же Anime4K user-shaders** (v4.0.1), что и engine-mpv, headless через
`mpv --vo=gpu-next` в том же каноническом порядке Clamp→Denoise→Restore→Upscale.
Нужен установленный `mpv` (`brew install mpv`) и **дисплей** (рендер идёт в окно).

```bash
./sakuro-bench synth-mpv --master master_1080p.png --scale 2 --degrade realistic \
    --modes off,mpv_ewa,anime4k_s,anime4k_m --out report
```

Пресеты: `off` (bilinear), `mpv_ewa` (ewa_lanczossharp), `anime4k_s/_m`
(Clamp+Restore+Upscale, модель S/M), `anime4k_denoise_m` (+Denoise).

Это **приближение** engine-mpv, не тождество: десктоп считает CNN в FP32, мобилка
может в FP16 → для аниме-шейдеров видимая разница. Ранжир достоверен, абсолютные
числа сверять с `capture-compare` на устройстве. Media3-эффекты offline не
воспроизводятся — только `capture-compare`.

## Отчёт

`report/index.html` — самодостаточный:
* **Инфографика** (inline SVG): VMAF vs VMAF-NEG бары с зазором Δ (прирост от
  улучшения), радар-профиль качества по режимам, scatter «резкость × ringing».
* **Таблицы** FR/NR, лучшее в столбце подсвечено.
* **Галерея с before/after слайдерами** Original⟷Enhanced (перетаскиваемый
  разделитель) + diff-хитмапы.

Плюс `report/metrics.csv` и `report/metrics.json`.

## Метрики

**FR** (выше = лучше): `VMAF`, `VMAF-NEG`, `SSIM`, `MS-SSIM`, `PSNR-Y`.
**NR** (относительные): `sharpness` (Lap.var), `hf_ratio` (ВЧ-энергия),
`noise` (σ, ниже лучше), `ringing` (гало, ниже лучше).

Расширение: NR из `pyiqa` (BRISQUE/NIQE/MUSIQ/CLIP-IQA) и FR SSIMULACRA2/LPIPS —
подключаемы, но требуют torch (на Python 3.14 wheels пока нет; ставить в отдельном
окружении 3.11/3.12 и дергать как внешний бэкенд).
