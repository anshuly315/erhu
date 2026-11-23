import skia, re, random, math, sys, os
S = 1024
ICONS = '/tmp/icons'
def icon_image(name, px):
    svg = open(f'{ICONS}/{name}.svg').read().replace('<path ', '<path fill="#ffffff" ')
    svg = re.sub(r'height="48"', f'height="{px}"', svg); svg = re.sub(r'width="48"', f'width="{px}"', svg)
    open('/tmp/_icon.svg', 'w').write(svg)
    dom = skia.SVGDOM.MakeFromStream(skia.Stream.MakeFromFile('/tmp/_icon.svg'))
    dom.setContainerSize(skia.Size(px, px))
    surf = skia.Surface(px, px); dom.render(surf.getCanvas())
    return surf.makeImageSnapshot()

def hexc(h, a=255):
    h = h.lstrip('#'); return skia.Color(int(h[0:2],16), int(h[2:4],16), int(h[4:6],16), a)

def render(out, icon, base, blobs, seed, size=512, label=None):
    rnd = random.Random(seed)
    surf = skia.Surface(S, S); c = surf.getCanvas()
    c.clear(hexc(base))
    # mesh of soft colour light
    for i, col in enumerate(blobs):
        x = rnd.uniform(0.05, 0.95) * S; y = rnd.uniform(0.05, 0.95) * S
        r = rnd.uniform(0.35, 0.6) * S
        p = skia.Paint(AntiAlias=True, Color=hexc(col, 230))
        p.setMaskFilter(skia.MaskFilter.MakeBlur(skia.kNormal_BlurStyle, r * 0.45))
        c.drawCircle(x, y, r * 0.75, p)
    # vinyl grooves from one corner
    cx, cy = rnd.choice([(S*1.05, S*1.05), (-S*0.05, S*1.05), (S*1.05, -S*0.05)])
    for k in range(18):
        p = skia.Paint(AntiAlias=True, Color=skia.Color(255, 255, 255, 10 if k % 3 else 18), Style=skia.Paint.kStroke_Style, StrokeWidth=1.5)
        c.drawCircle(cx, cy, 160 + k * 46, p)
    # icon: large, soft glow, a drop shadow, then crisp white
    ic = icon_image(icon, int(S * 0.5))
    x = (S - ic.width()) / 2; y = (S - ic.height()) / 2
    glow = skia.Paint(ImageFilter=skia.ImageFilters.Blur(40, 40), Alphaf=0.55)
    c.drawImage(ic, x, y, skia.SamplingOptions(), glow)
    shadow = skia.Paint(ImageFilter=skia.ImageFilters.DropShadowOnly(0, 18, 18, 18, skia.Color(0, 0, 0, 110)))
    c.drawImage(ic, x, y, skia.SamplingOptions(), shadow)
    c.drawImage(ic, x, y, skia.SamplingOptions(), skia.Paint(Alphaf=0.94))
    # vignette
    vg = skia.Paint(Shader=skia.GradientShader.MakeRadial(skia.Point(S/2, S/2), S*0.75, [skia.Color(0,0,0,0), skia.Color(0,0,0,150)]))
    c.drawRect(skia.Rect(0, 0, S, S), vg)
    img = surf.makeImageSnapshot()
    # film grain
    import numpy as np
    a = img.toarray()[:, :, [2, 1, 0]].astype(np.float32)
    noise = np.random.default_rng(seed).normal(0, 7, (S, S, 1))
    a = np.clip(a + noise, 0, 255).astype(np.uint8)
    from PIL import Image
    Image.fromarray(a[:, :, ::-1] if False else a).resize((size, size), Image.LANCZOS).save(out, quality=86, optimize=True)

GENRES = {
 'bollywood': ('movie', '#1a0710', ['#ff4d6d', '#ff9e00', '#c9184a', '#7b2cbf']),
 'chill': ('nights_stay', '#06121f', ['#4cc9f0', '#4361ee', '#3a0ca3', '#80ffdb']),
 'classical': ('piano', '#120d08', ['#e9c46a', '#b08968', '#6f4e37', '#f4a261']),
 'country': ('landscape', '#120c05', ['#f4a261', '#e76f51', '#2a9d8f', '#e9c46a']),
 'devotional': ('self_improvement', '#160b02', ['#ff9f1c', '#ffbf69', '#d62828', '#f77f00']),
 'electronic': ('graphic_eq', '#07051a', ['#f72585', '#7209b7', '#4cc9f0', '#3a0ca3']),
 'hiphop': ('mic_external_on', '#0d0d0d', ['#ffd60a', '#ff5400', '#9d0208', '#6a040f']),
 'jazz': ('nightlife', '#0b0a17', ['#5a189a', '#e0aaff', '#ff6d00', '#3c096c']),
 'pop': ('star', '#1a0620', ['#ff70a6', '#ff9770', '#ffd670', '#70d6ff']),
 'reggae': ('sunny', '#06140a', ['#2b9348', '#ffd000', '#d00000', '#55a630']),
 'rock': ('bolt', '#120303', ['#d00000', '#ff6d00', '#370617', '#ffba08']),
 'talk': ('record_voice_over', '#041518', ['#00b4d8', '#0077b6', '#90e0ef', '#023e8a']),
 'world': ('public', '#04140f', ['#06d6a0', '#118ab2', '#ffd166', '#073b4c']),
}
SONGS = [
 ('music_note', '#0e0b1f', ['#7b2cbf', '#e0aaff', '#3c096c', '#ff70a6']),
 ('album', '#071a1a', ['#2ec4b6', '#cbf3f0', '#ff9f1c', '#0b525b']),
 ('headphones', '#1a0b05', ['#ff7b00', '#ffb700', '#9d0208', '#ff5400']),
 ('queue_music', '#05101f', ['#4361ee', '#4cc9f0', '#f72585', '#3a0ca3']),
 ('library_music', '#13071a', ['#b5179e', '#f72585', '#560bad', '#ffafcc']),
 ('graphic_eq', '#04140b', ['#38b000', '#ccff33', '#007200', '#70e000']),
 ('audiotrack', '#1a0610', ['#ef476f', '#ffd166', '#c9184a', '#ff8fa3']),
 ('equalizer', '#0a0f1a', ['#48cae4', '#ade8f4', '#0096c7', '#f9c74f']),
]
out = sys.argv[1]
for i, (g, (icon, base, blobs)) in enumerate(GENRES.items()):
    render(f'{out}/genre_{g}.jpg', icon, base, blobs, 100 + i)
for i, (icon, base, blobs) in enumerate(SONGS):
    render(f'{out}/song_art_{i+1}.jpg', icon, base, blobs, 200 + i)
