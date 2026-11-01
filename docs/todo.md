# To do later

## Map: switch to MapLibre for tilt
Swap the map engine from osmdroid to MapLibre so the map can tilt as you
zoom in (like Pokémon GO), rotate smoothly and show 3D buildings.

- Rewrite the map screen's markers, overlays (shrine reach circle, risen
  shrine glow) and gestures for MapLibre.
- Use a free vector tile style (e.g. OpenFreeMap) and keep the dark/light map
  styles.
- Robolectric screenshot tests can't run MapLibre's native code: give the map
  a stand-in there.
- Estimate: a full session or two; some risk of map bugs in the first release.
