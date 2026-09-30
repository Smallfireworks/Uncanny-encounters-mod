"""Reproducible original frog SFX and pixel sprites. Requires numpy, Pillow and ffmpeg."""
import json
import math
from pathlib import Path
import shutil
import subprocess

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/uncannyencounters"
RATE = 44100
RNG = np.random.default_rng(263)


def silence(seconds):
    return np.zeros(round(seconds * RATE), dtype=np.float64)


def noise(seconds, low, high):
    n = round(seconds * RATE)
    spectrum = np.fft.rfft(RNG.normal(size=n))
    hz = np.fft.rfftfreq(n, 1 / RATE)
    mask = np.exp(-(hz / high) ** 4) * (1 - np.exp(-(hz / max(low, 1)) ** 4))
    signal = np.fft.irfft(spectrum * mask, n)
    return signal / max(np.std(signal), 1e-6)


def add(buffer, clip, at=0, gain=1):
    start = round(at * RATE)
    count = min(len(clip), len(buffer) - start)
    if count > 0:
        buffer[start:start + count] += clip[:count] * gain


def glass(seconds, frequency, decay=0.15):
    t = np.arange(round(seconds * RATE)) / RATE
    out = np.zeros_like(t)
    for ratio, gain in [(1, 1), (2.71, .32), (4.13, .15), (6.37, .045)]:
        phase = 2 * np.pi * frequency * ratio * (t + .00002 * np.sin(31 * t))
        out += gain * np.sin(phase) * np.exp(-t / (decay / math.sqrt(ratio)))
    return out * (1 - np.exp(-t * 1800))


def bubble(seconds, frequency, decay=.075):
    t = np.arange(round(seconds * RATE)) / RATE
    pitch = frequency * (.4 + .6 * np.exp(-t * 35))
    phase = 2 * np.pi * np.cumsum(pitch) / RATE
    voice = np.sin(phase + .55 * np.sin(phase * 1.47))
    return (voice + noise(seconds, 120, 2000) * .12) * np.exp(-t / decay) * (1 - np.exp(-t * 1300))


def grit(seconds, decay=.06):
    t = np.arange(round(seconds * RATE)) / RATE
    return noise(seconds, 250, 6500) * np.exp(-t / decay) * (1 - np.exp(-t * 1600))


def thud(seconds, pitch=100):
    t = np.arange(round(seconds * RATE)) / RATE
    phase = 2 * np.pi * np.cumsum(pitch * (.35 + .65 * np.exp(-t * 22))) / RATE
    return np.sin(phase) * np.exp(-t * 15) * (1 - np.exp(-t * 900))


def sounds():
    result = {}
    charge = silence(.48)
    for at, pitch in [(0, 215), (.08, 275), (.17, 230), (.29, 330)]:
        add(charge, bubble(.16, pitch, .065), at, .28)
    t = np.arange(len(charge)) / RATE
    charge += noise(.48, 130, 900) * np.sin(np.pi * t / .48) ** 2 * .035
    add(charge, glass(.18, 1350, .07), .28, .04)
    result['slime_charge'] = charge
    spit = silence(.26)
    add(spit, bubble(.2, 440, .043), gain=.75)
    add(spit, grit(.15, .025), .015, .18)
    add(spit, glass(.17, 1700, .055), .028, .065)
    result['slime_spit'] = spit
    for variant in (1, 2):
        splash = silence(.37)
        add(splash, noise(.16, 350, 4600) * np.exp(-np.arange(round(.16 * RATE)) / RATE * 40), gain=.15)
        for i in range(7):
            add(splash, bubble(.14, RNG.uniform(350, 1000), .032), i * .017, .2 / (1 + i * .16))
        add(splash, glass(.22, 2150 + variant * 130, .07), .035, .055)
        result[f'slime_splash_{variant}'] = splash
    form = silence(.65)
    for at, freq in [(0, 640), (.045, 960), (.095, 1440), (.15, 1920)]:
        add(form, glass(.5, freq, .19), at, .24)
    result['shell_form'] = form
    for variant in (1, 2):
        impact = silence(.23)
        add(impact, glass(.22, 970 + variant * 130, .055), gain=.6)
        add(impact, grit(.055, .016), gain=.075)
        result[f'shell_hit_{variant}'] = impact
    shatter = silence(.5)
    add(shatter, grit(.27, .06), gain=.2)
    for i in range(15):
        add(shatter, glass(.22, RNG.uniform(1150, 3600), .055), RNG.uniform(0, .19), .09)
    result['shell_break'] = shatter
    fade = silence(.45)
    add(fade, glass(.4, 1250, .12), gain=.2)
    add(fade, glass(.34, 835, .11), .075, .18)
    fade *= np.minimum(1, np.arange(len(fade)) / (RATE * .035))
    result['shell_fade'] = fade
    land = silence(.23)
    add(land, thud(.22, 105), gain=.65)
    add(land, grit(.17, .036), .005, .14)
    result['shock_land'] = land
    shock = silence(.46)
    add(shock, thud(.44, 88), gain=.7)
    add(shock, grit(.3, .09), .025, .12)
    for at in (.03, .06, .105, .15):
        add(shock, glass(.18, RNG.uniform(500, 1300), .035), at, .05)
    result['shock_burst'] = shock
    chime = silence(.22)
    add(chime, glass(.21, 1220, .07), gain=.6)
    add(chime, glass(.16, 1830, .055), .016, .15)
    result['echo_chime'] = chime
    echo = silence(.5)
    for i, freq in enumerate((1540, 2170, 2890, 3470)):
        add(echo, glass(.4, freq, .105), i * .008, .24)
    add(echo, grit(.14, .04), .012, .08)
    add(echo, glass(.3, 1540, .12), .15, .08)
    result['echo_burst'] = echo
    return result


def export_sounds():
    encoder = shutil.which('ffmpeg')
    if not encoder:
        raise RuntimeError('ffmpeg is required to encode OGG/Vorbis')
    folder = ASSETS / 'sounds/crystal_frog'
    folder.mkdir(parents=True, exist_ok=True)
    stats = {}
    for name, samples in sounds().items():
        samples -= samples.mean()
        fade = min(round(.008 * RATE), len(samples) // 4)
        samples[:fade] *= np.linspace(0, 1, fade)
        samples[-fade:] *= np.linspace(1, 0, fade)
        samples *= .7 / max(np.max(np.abs(samples)), 1e-6)
        subprocess.run([encoder, '-hide_banner', '-loglevel', 'error', '-y', '-f', 'f32le', '-ar', str(RATE),
                        '-ac', '1', '-i', 'pipe:0', '-c:a', 'libvorbis', '-q:a', '5', '-map_metadata', '-1',
                        str(folder / (name + '.ogg'))], input=samples.astype('<f4').tobytes(), check=True)
        stats[name] = {'seconds': round(len(samples) / RATE, 3), 'peak': round(float(np.max(np.abs(samples))), 4),
                       'rms': round(float(np.sqrt(np.mean(samples ** 2))), 4)}
    mapping = {}
    for event in ('slime_charge', 'slime_spit', 'slime_splash', 'shell_form', 'shell_hit', 'shell_break',
                  'shell_fade', 'shock_land', 'shock_burst', 'echo_chime', 'echo_burst'):
        variants = [event + '_1', event + '_2'] if event in ('slime_splash', 'shell_hit') else [event]
        mapping['crystal_frog.' + event] = {
            'subtitle': 'subtitles.uncannyencounters.crystal_frog.' + event,
            'sounds': [{'name': 'uncannyencounters:crystal_frog/' + n, 'attenuation_distance': 12} for n in variants]}
    (ASSETS / 'sounds.json').write_text(json.dumps(mapping, indent=2) + '\n', encoding='utf-8')
    report = ROOT / 'build/frog-fx/audio_manifest.json'
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps(stats, indent=2) + '\n', encoding='utf-8')


def save(image, relative):
    path = ASSETS / 'textures' / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def sprites():
    drop = Image.new('RGBA', (16, 16))
    d = ImageDraw.Draw(drop)
    d.polygon([(8, 1), (12, 7), (13, 10), (11, 14), (5, 14), (3, 10), (4, 7)], fill=(70, 25, 122, 245))
    d.polygon([(8, 3), (11, 8), (11, 11), (9, 13), (5, 11), (5, 8)], fill=(163, 76, 213, 235))
    d.line([(7, 6), (6, 8), (6, 10)], fill=(241, 219, 255, 250), width=2)
    save(drop, 'particle/frog_slime_drop.png')
    glob = Image.new('RGBA', (32, 32))
    d = ImageDraw.Draw(glob)
    d.polygon([(11, 3), (21, 4), (27, 9), (29, 19), (24, 27), (13, 29), (5, 24), (3, 13), (6, 7)], fill=(69, 27, 108, 250))
    d.ellipse((5, 5, 26, 26), fill=(133, 54, 181, 245))
    d.ellipse((8, 6, 21, 16), fill=(187, 104, 232, 250))
    d.polygon([(10, 6), (15, 5), (14, 8), (9, 11), (7, 14), (7, 10)], fill=(241, 218, 255, 255))
    d.polygon([(22, 15), (25, 18), (21, 23), (18, 21)], fill=(197, 139, 238, 230))
    save(glob, 'entity/crystal_slime_glob.png')
    splat = Image.new('RGBA', (32, 32))
    d = ImageDraw.Draw(splat)
    points = []
    for i in range(24):
        angle = i * math.pi / 12
        radius = 13 if i % 3 == 0 else 7 + i % 4
        points.append((round(16 + math.cos(angle) * radius), round(16 + math.sin(angle) * radius)))
    d.polygon(points, fill=(116, 46, 159, 180))
    d.ellipse((8, 10, 23, 22), fill=(167, 88, 210, 200))
    d.line([(11, 13), (14, 11), (18, 11)], fill=(228, 185, 255, 220), width=2)
    save(splat, 'particle/frog_slime_splat.png')
    shard = Image.new('RGBA', (16, 24))
    d = ImageDraw.Draw(shard)
    d.polygon([(8, 1), (13, 7), (11, 18), (7, 23), (3, 16), (3, 7)], fill=(187, 139, 240, 210))
    d.polygon([(8, 1), (8, 17), (7, 23), (3, 16), (3, 7)], fill=(107, 73, 168, 215))
    d.line([(8, 1), (13, 7), (11, 18), (7, 23)], fill=(232, 223, 255, 245), width=1)
    d.line([(8, 2), (8, 17)], fill=(211, 197, 255, 220), width=1)
    save(shard, 'particle/frog_crystal_chip.png')
    panel = Image.new('RGBA', (32, 64))
    d = ImageDraw.Draw(panel)
    poly = [(16, 1), (29, 14), (27, 49), (16, 62), (4, 49), (2, 14)]
    d.polygon(poly, fill=(149, 107, 219, 65))
    d.polygon([(16, 1), (16, 62), (4, 49), (2, 14)], fill=(112, 92, 186, 100))
    d.line(poly + [poly[0]], fill=(207, 194, 251, 220), width=2)
    d.line([(16, 3), (16, 60)], fill=(218, 209, 255, 130), width=1)
    d.line([(3, 15), (16, 23), (28, 14)], fill=(194, 177, 244, 140), width=1)
    save(panel, 'entity/crystal_shell_panel.png')
    ring = Image.new('RGBA', (64, 64))
    d = ImageDraw.Draw(ring)
    for i in range(24):
        d.arc((1, 1, 62, 62), i * 15 + 1, i * 15 + 12, fill=(196, 159, 243, 220), width=2)
        a = i * math.pi / 12
        d.line([(32 + math.cos(a) * 24, 32 + math.sin(a) * 24),
                (32 + math.cos(a) * 30, 32 + math.sin(a) * 30)], fill=(152, 125, 191, 135), width=1)
    save(ring, 'particle/frog_ground_wave.png')
    cluster = Image.new('RGBA', (32, 32))
    d = ImageDraw.Draw(cluster)
    for x, bottom, height in [(9, 28, 16), (24, 29, 18), (16, 30, 28)]:
        d.polygon([(x, bottom-height), (x+5, bottom-height+7), (x+4, bottom-3), (x, bottom),
                   (x-4, bottom-3), (x-5, bottom-height+7)], fill=(171, 117, 219, 220))
        d.polygon([(x, bottom-height), (x, bottom), (x-4, bottom-3), (x-5, bottom-height+7)], fill=(108, 64, 168, 205))
        d.line([(x, bottom-height), (x+5, bottom-height+7), (x+4, bottom-3)], fill=(237, 215, 255, 255), width=1)
    save(cluster, 'particle/frog_echo_cluster.png')
    mapping = {'slime_drop': 'slime_drop', 'slime_splat': 'slime_splat', 'crystal_chip': 'crystal_chip',
               'shock_mark': 'ground_wave', 'shock_wave': 'ground_wave', 'echo_seed': 'echo_cluster',
               'echo_glow': 'echo_cluster', 'echo_peak': 'echo_cluster'}
    folder = ASSETS / 'particles'
    folder.mkdir(parents=True, exist_ok=True)
    for name, texture in mapping.items():
        (folder / ('frog_' + name + '.json')).write_text(json.dumps({'textures': ['uncannyencounters:frog_' + texture]}, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    export_sounds()
    sprites()
    print('Generated 13 original mono OGG clips, 11 sound events and frog FX sprites.')
