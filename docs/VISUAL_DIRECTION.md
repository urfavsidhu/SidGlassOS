# Reference analysis

All seven JPEGs in `references/` were inspected on 19 October 2026. They are visual research, not redistributable product assets. No screenshot, third-party icon, album cover, logo, or photographed wallpaper is packaged in the application.

| Reference filename suffix | Observations | Application treatment |
| --- | --- | --- |
| `1.15.23 PM (1)` | Tall outlined clock, violet tree canopy, luminous blue mist, wide frosted music card | In-app lock-screen preview with an original procedural forest; actual lock-screen layout remains controlled by ColorOS |
| `1.15.23 PM` | Thin specular card edges, generous corner radius, restrained transport controls | Reusable glass media card; permission-free empty state opens the user's music app |
| `1.15.23 PM (2)` | Rounded controls and vertical volume/brightness shapes; theme-store screenshot | Functional in-app control panel with official Settings shortcuts for restricted toggles |
| `1.16.49 PM` | Expanded brightness surface, soft white highlights, cool blue translucency | Large touch targets, restrained translucent gradients, window brightness preview |
| `1.17.28 PM` | Pale dialer, circular keypad | General geometry reference only; launch the original dialer and preserve its own UI |
| `1.18.46 PM` | Grouped Settings rows; visibly Xiaomi branding | Grouped dashboard preferences; does not imply ColorOS theme compatibility |
| `1.19.57 PM` | Two compact upper cards, sparse app grid, glass icon frames, floating four-icon dock | Launcher composition, clock/information cards, configurable grid and persistent dock |

## Original design rules

- Navy `#0B1220`, midnight `#182A4A`, violet `#4B5FD1`, ice `#DDE5F4`, white.
- Original Canvas-drawn silhouettes and atmospheric gradients rather than copying the photographed forest.
- 28dp card corners, 22dp icon corners, 34dp dock corners, fine top-left highlights.
- Typography uses Android's sans-serif family, light clock numerals and legible body labels. No unlicensed font files.
- Frosting is a translucent, softly graded fallback. Compose blur is not advertised as backdrop blur. Avoid blurring app text or icon content.
- Balanced performance by default; no continuous animations on dashboard/launcher. Live wallpaper only renders while its surface is visible.
- Controls describe their real effects. Permission-dependent and preview features are labeled where the user encounters them.
- Accessibility: minimum 48dp actions, scalable text, content descriptions for icon actions, reduced motion, and high-opacity glass option.
