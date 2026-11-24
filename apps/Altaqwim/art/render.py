"""Render the original calendar artwork and app icon."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

root = Path(__file__).resolve().parents[1] / "app/src/main"
canvas = Image.new("RGBA", (480,416))
shadow = Image.new("RGBA", canvas.size)
ImageDraw.Draw(shadow).ellipse((90,300,388,361), fill=(1,14,43,160))
canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(13)))
board = Image.new("RGBA", canvas.size)
d = ImageDraw.Draw(board)
d.rounded_rectangle((92,83,365,316), radius=35, fill="#183e93")
d.rounded_rectangle((84,69,357,301), radius=35, fill="#b0d1ff")
d.rounded_rectangle((91,62,354,284), radius=31, fill="#f1f6ff")
d.rounded_rectangle((91,62,354,145), radius=29, fill="#78abf5")
d.rectangle((91,115,354,145), fill="#78abf5")
d.rounded_rectangle((147,42,171,103), radius=12, fill="#314c9d")
d.rounded_rectangle((267,42,291,103), radius=12, fill="#314c9d")
for row in range(2):
    for col in range(4):
        x,y = 119+53*col,165+51*row
        c = "#d8e7fb" if (row,col) != (0,2) else "#a6e0df"
        d.rounded_rectangle((x,y,x+37,y+34), radius=8,fill=c)
d.line([(230,181),(236,187),(247,174)], fill="#287581", width=5, joint="curve")
canvas.alpha_composite(board.rotate(-11,resample=Image.Resampling.BICUBIC,center=(224,183)))
clock=ImageDraw.Draw(canvas)
clock.ellipse((286,236,415,367), fill="#14306c")
clock.ellipse((284,222,414,354), fill="#f6c77b")
clock.ellipse((296,230,402,338), fill="#ffe7ae")
clock.line([(350,251),(350,284),(370,297)], fill="#694b6c", width=7, joint="curve")
clock.ellipse((346,280,355,289), fill="#694b6c")
for x,y,s,color in [(67,93,13,"#c0ddff"),(384,75,20,"#f9d48a"),(63,276,12,"#a1ded8"),(422,157,13,"#c3ddff")]:
    clock.polygon([(x,y-s),(x+s//4,y-s//4),(x+s,y),(x+s//4,y+s//4),(x,y+s),(x-s//4,y+s//4),(x-s,y),(x-s//4,y-s//4)],fill=color)
canvas.save(root/"assets/calendar.webp",lossless=True)
icon=Image.new("RGBA",(432,432))
d=ImageDraw.Draw(icon)
d.rounded_rectangle((0,0,431,431),radius=104,fill="#245ab9")
d.ellipse((56,49,376,367),fill="#3372ce")
d.rounded_rectangle((94,124,338,329),radius=38,fill="#173d92")
d.rounded_rectangle((94,105,338,307),radius=38,fill="#e8f2ff")
d.rounded_rectangle((94,105,338,182),radius=35,fill="#a8ccff")
d.rectangle((94,151,338,181),fill="#a8ccff")
for x in (143,260):
    d.rounded_rectangle((x,84,x+26,144),radius=12,fill="#1d4a9d")
for y in (197,249):
    for x in (135,193,251):
        d.rounded_rectangle((x,y,x+37,y+29),radius=7,fill="#8cb5ed" if (x,y)!=(251,249) else "#f3c670")
d.polygon([(367,78),(373,96),(391,103),(373,109),(367,128),(361,109),(343,103),(361,96)],fill="#f6d58d")
icon.resize((192,192),Image.Resampling.LANCZOS).save(root/"res/drawable-nodpi/calendar_icon.png")
