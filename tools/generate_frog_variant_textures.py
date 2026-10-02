"""Derive variant textures from the existing atlas; preserve UVs and original artwork."""
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/uncannyencounters"
source = Image.open(ASSETS / "textures/entity/crystal_frog.png").convert("RGBA")
original = np.asarray(source)
hsv = np.array(source.convert("RGB").convert("HSV"), copy=True)
# The mesh's "crystals" group contains only the highest tips. Crystal surfaces
# also occur in body/head/limb groups, so select the purple material in the atlas
# instead. Pale skin, neutral highlights and black pupils retain their colors.
crystals = ((hsv[:, :, 0] >= 165) & (hsv[:, :, 0] <= 225)
            & (hsv[:, :, 1] >= 30) & (hsv[:, :, 2] >= 15)
            & (original[:, :, 3] > 0))
assert np.count_nonzero(crystals) > source.width * source.height * 0.5
for variant, hue, saturation, brightness in [("zombie", 72, 145, 0.78), ("echo", 126, 195, 0.62), ("ender", 192, 185, 0.57)]:
    changed = hsv.copy()
    if variant == "zombie":
        # Desaturate, darken, and add deterministic mottling while keeping eyes legible.
        y, x = np.indices(crystals.shape)
        rot = ((x // 5 * 73 + y // 5 * 9173) % 251) > 230
        skin = (hsv[:, :, 1] > 35) & (hsv[:, :, 2] > 45)
        changed[:, :, 0][skin] = 65
        changed[:, :, 1][skin] = 90
        changed[:, :, 2] = (hsv[:, :, 2] * np.where(rot, 0.46, 0.76)).astype(np.uint8)
    changed[:, :, 0][crystals] = hue
    changed[:, :, 1][crystals] = saturation
    changed[:, :, 2][crystals] = (hsv[:, :, 2][crystals] * brightness).astype(np.uint8)
    result = np.array(Image.fromarray(changed, "HSV").convert("RGBA"), copy=True)
    result[:, :, 3] = original[:, :, 3]
    if variant != "zombie":
        result[~crystals] = original[~crystals]
    assert np.any(result != original)
    Image.fromarray(result).save(ASSETS / f"textures/entity/crystal_frog_{variant}.png")
    print(f"{variant}: {np.count_nonzero(np.any(result != original, axis=2))} pixels changed")
