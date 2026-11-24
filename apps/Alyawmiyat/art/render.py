"""Render the app's original journal artwork and launcher icon using Pillow."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

root = Path(__file__).resolve().parents[1] / 'app' / 'src' / 'main'
scale = 4
canvas = Image.new('RGBA', (248 * scale, 216 * scale))
size = canvas.size

def layer():
    return Image.new('RGBA', size)

def xy(points):
    return [(round(x * scale), round(y * scale)) for x, y in points]

def box(bounds):
    return tuple(round(x * scale) for x in bounds)

def line(image, points, fill, width):
    ImageDraw.Draw(image).line(xy(points), fill=fill, width=round(width * scale), joint='curve')

def poly(image, points, fill):
    ImageDraw.Draw(image).polygon(xy(points), fill=fill)

def ellipse(image, bounds, fill):
    ImageDraw.Draw(image).ellipse(box(bounds), fill=fill)

def round_rect(image, bounds, radius, fill):
    ImageDraw.Draw(image).rounded_rectangle(box(bounds), round(radius * scale), fill=fill)

def gradient(bounds, radius, first, last):
    image = layer()
    mask = Image.new('L', size)
    ImageDraw.Draw(mask).rounded_rectangle(box(bounds), round(radius * scale), fill=255)
    draw = ImageDraw.Draw(image)
    top, bottom = int(bounds[1] * scale), int(bounds[3] * scale)
    for y in range(top, bottom + 1):
        t = (y - top) / (bottom - top)
        draw.line((0, y, image.width, y), fill=tuple(round(a + (b - a) * t) for a, b in zip(first, last)))
    image.putalpha(mask)
    return image

def star(x, y, r, color):
    poly(canvas, [(x + dx*r, y + dy*r) for dx,dy in [
        (0,-1),(.25,-.25),(1,0),(.25,.25),(0,1),(-.25,.25),(-1,0),(-.25,-.25)
    ]],color)

shadow = layer()
ellipse(shadow,(42,155,201,187),(4,30,23,160))
canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(8 * scale)))
halo = layer()
ellipse(halo,(32,27,212,186),(109,207,153,24))
ImageDraw.Draw(halo).ellipse(box((48,40,198,177)),outline=(181,255,212,62),width=scale)
canvas.alpha_composite(halo)
book = layer()
round_rect(book,(33,63,196,170),13,(16,73,57,255))
book.alpha_composite(gradient((34,57,194,163),13,(112,213,160),(34,137,101)))
round_rect(book,(44,59,187,154),9,(18,93,71,255))
# Page edges and the two halves of the notebook.
poly(book,[(44,64),(57,58),(116,67),(175,57),(185,64),(187,149),(176,154),(116,147),(55,152),(44,145)],(176,216,194,255))
poly(book,[(44,57),(56,51),(114,62),(115,141),(101,137),(56,146),(44,141)],(249,251,231,255))
poly(book,[(115,62),(174,51),(185,57),(185,139),(173,144),(131,137),(115,141)],(255,254,239,255))
poly(book,[(111,62),(115,61),(119,62),(119,141),(115,143),(111,141)],(216,235,217,255))
for y in (77,91,105,119):
    line(book,[(54,y),(78,y-3),(103,y+2)],(177,215,191,255),1.1)
    line(book,[(127,y+2),(149,y-2),(174,y-1)],(177,215,191,255),1.1)
line(book,[(59,86),(70,84),(75,86),(88,83),(96,86)],(62,148,110,212),1.7)
line(book,[(62,101),(72,99),(80,101),(94,100)],(62,148,110,189),1.7)
line(book,[(133,89),(143,86),(147,88),(155,86),(169,87)],(94,168,130,203),1.7)
poly(book,[(71,137),(83,136),(83,166),(77,161),(71,168)],(246,190,113,255))
canvas.alpha_composite(book.rotate(-10,resample=Image.Resampling.BICUBIC,center=(115*scale,108*scale)))
pen = layer()
round_rect(pen,(104,121,204,136),6,(19,99,75,100))
pen.alpha_composite(gradient((102,116,200,129),5,(117,226,178),(33,153,107)))
round_rect(pen,(185,116,202,129),4,(183,245,207,255))
round_rect(pen,(179,116,185,129),1,(218,247,226,255))
poly(pen,[(90,122.5),(103,116),(103,129)],(253,222,164,255))
poly(pen,[(88,122.5),(94,120),(94,125)],(16,64,54,255))
line(pen,[(109,118),(174,118)],(202,255,223,160),1)
canvas.alpha_composite(pen.rotate(39,resample=Image.Resampling.BICUBIC,center=(150*scale,124*scale)))
star(41,54,9,(192,252,213,255))
star(205,52,13,(242,207,137,255))
star(214,129,8,(192,252,213,255))
star(45,154,6,(242,207,137,255))
ellipse(canvas,(59,32,63,36),(182,231,205,255))
ellipse(canvas,(214,98,218,102),(183,246,205,255))
ellipse(canvas,(190,166,194,170),(243,214,159,255))
canvas.resize((496,432),Image.Resampling.LANCZOS).save(root/'assets/journal.webp', lossless=True)

icon = Image.new('RGBA',(432,432),(0,0,0,0))
draw = ImageDraw.Draw(icon)
draw.rounded_rectangle((0,0,431,431),106,fill='#16835d')
draw.ellipse((71,66,361,356),fill='#20966c')
draw.rounded_rectangle((122,102,319,330),39,fill='#075840')
draw.rounded_rectangle((121,88,316,312),38,fill='#cdf5dc')
draw.rounded_rectangle((137,93,163,306),11,fill='#9ddcb7')
draw.rounded_rectangle((171,107,310,299),21,fill='#f1fced')
draw.rounded_rectangle((196,160,276,170),5,fill='#198358')
draw.rounded_rectangle((196,191,263,200),4,fill='#73bd9a')
draw.rounded_rectangle((196,220,252,229),4,fill='#73bd9a')
draw.polygon([(228,96),(255,96),(255,146),(242,137),(228,146)],fill='#efbd73')
draw.polygon([(344,69),(350,89),(369,95),(350,102),(344,122),(338,102),(320,95),(338,89)],fill='#c4f1d6')
icon.resize((192,192),Image.Resampling.LANCZOS).save(root/'res/drawable-nodpi/journal_icon.png')
