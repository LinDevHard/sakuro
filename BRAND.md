# Sakuro Brand And Visual Mood

> Project: **Sakuro** by **Rinwave** (`com.rinwave.sakuro`)
> Date: 2026-07-02
> Status: visual identity and mood.
> Related: [DESIGN.md](DESIGN.md), [ARCHITECTURE.md](ARCHITECTURE.md).

Put the key teaser art reference at `assets/branding/sakuro-teaser.png`.

## 1. Mood

Cinematic night **twilight**: dark plum-purple background, sakura branch and falling petals, a Mount Fuji silhouette reflected in a lake, a chrome-pink logo with a small flare, and a thin glowing eclipse ring.

Keywords: premium, cinematic, serene, ethereal, Japanese aesthetics, sakura, Fuji, twilight, dreamlike.

The feeling should be a flagship-product teaser, not a hobbyist fork. Content and image quality come first; UI serves the atmosphere.

## 2. Palette

Dark plum base, pink-lavender accents, and metallic logo chrome.

| Token | Hex | Purpose |
|---|---|---|
| `background` | `#120A17` | near-black plum base |
| `surface` | `#1E1226` | level-1 surfaces |
| `surfaceElevated` | `#2A1A34` | cards and overlays |
| `twilight` | `#4A2A5A` | twilight purple for sky gradients |
| `accentSakura` | `#EC8FC0` | primary sakura-petal accent |
| `accentLavender` | `#A57FD6` | secondary lavender accent |
| `glowMagenta` | `#C94F9C` | glow, active states, flare |
| `textPrimary` | `#F3E9F2` | soft pink-white text |
| `textMuted` | `#B9A7C4` | secondary text |

Logo metallic gradient: `#F6D9E6 -> #E79ECB -> #B98CD9`, with a soft outer glow and a small flare.

Rules: the base is always dark; accents are targeted; glow stays restrained.

## 3. Motifs

- **Sakura and petals:** a subtle branch in a corner and delicate falling petals for splash, empty states, or very light background particles.
- **Eclipse ring:** a thin glowing ring behind the logo; reuse it as loader, spinner, progress motif, and the "O" in the wordmark.
- **Fuji and reflection:** promo, splash, and onboarding only; avoid it in work screens.
- **Lens flare:** small accent detail, used sparingly.

## 4. Logo

- **SAKURO** uses a wide, elegant geometric sans shape, all caps, high contrast, and large tracking.
- The "O" is a thin glowing ring.
- Use the pink-lavender metallic gradient on a dark background and preserve the outer glow.
- **BY RINWAVE** uses muted color, smaller size, and wide tracking.
- Clearspace: at least one letter height around the wordmark. Do not place it on bright or busy backgrounds without a dark backing.

## 5. Design-System Link

- Palette maps to the theme tokens in [DESIGN.md](DESIGN.md).
- The eclipse ring becomes loaders, progress, and micro-accents.
- Brand/display lettering is only for wordmark and splash; UI text remains neutral.
- Fonts must be freely licensed for F-Droid.
- Splash/onboarding may use full art; working screens stay restrained.

## 6. Assets

- Store key art and derivatives in `assets/branding/`.
- App icon is based on the eclipse ring, sakura, gradient, and dark background.
- All assets and fonts in the `foss` flavor must use free licenses.
