"""Render the original notes launcher and its in-app illustration."""
from pathlib import Path
from PIL import Image, ImageDraw

main = Path(__file__).resolve().parents[1] / "app/src/main"
icon = Image.new("RGBA", (432, 432))
draw = ImageDraw.Draw(icon)
draw.rounded_rectangle((0, 0, 431, 431), radius=104, fill="#EAB02B")
draw.ellipse((52, 49, 380, 376), fill="#F6C553")
draw.rounded_rectangle((106, 119, 322, 336), radius=36, fill="#A36416")
draw.rounded_rectangle((96, 98, 313, 315), radius=36, fill="#FFF3CD")
draw.rounded_rectangle((96, 98, 313, 151), radius=26, fill="#FFE194")
draw.rectangle((96, 124, 313, 151), fill="#FFE194")
for y, end in ((175, 267), (213, 268), (251, 220)):
    draw.rounded_rectangle((135, y, end, y + 11), radius=5, fill="#C17C2D")
draw.polygon([(301, 255), (331, 237), (369, 300), (373, 339), (338, 319)], fill="#9C601B")
draw.polygon([(296, 246), (321, 232), (357, 293), (359, 326), (332, 310)], fill="#ED992A")
draw.polygon([(332, 310), (357, 293), (359, 326)], fill="#FFF1C5")
draw.polygon([(349, 319), (358, 314), (359, 326)], fill="#69402A")
draw.polygon([(352, 73), (360, 92), (379, 99), (360, 106),
              (352, 125), (345, 106), (326, 99), (345, 92)], fill="#FFF4CE")
launcher = icon.resize((192, 192), Image.Resampling.LANCZOS)
launcher.save(main / "res/drawable-nodpi/notes_icon.png")
launcher.save(main / "assets/brand.png")

scene = Image.new("RGBA", (280, 160))
draw = ImageDraw.Draw(scene)
draw.ellipse((55, 104, 239, 148), fill="#F8EACB")
paper = Image.new("RGBA", scene.size)
draw = ImageDraw.Draw(paper)
draw.rounded_rectangle((92, 23, 186, 128), radius=13, fill="#C68D3C")
draw.rounded_rectangle((87, 17, 181, 121), radius=13, fill="#FFE196", outline="#DAA74B", width=2)
for y, end in ((44, 161), (62, 161), (80, 147)):
    draw.rounded_rectangle((105, y, end, y + 4), 2, fill="#B47724")
scene.alpha_composite(paper.rotate(-11, resample=Image.Resampling.BICUBIC, center=(132, 70)))
draw = ImageDraw.Draw(scene)
draw.polygon([(173, 112), (202, 59), (214, 66), (185, 121), (173, 128)], fill="#AD6821")
draw.polygon([(171, 107), (200, 53), (212, 60), (183, 115), (170, 124)], fill="#E5A133")
draw.polygon([(171, 107), (183, 115), (170, 124)], fill="#FFF1C6")
for x, y, size in ((57, 52, 9), (224, 36, 13), (233, 87, 6)):
    draw.polygon([(x,y-size),(x+3,y-3),(x+size,y),(x+3,y+3),(x,y+size),
                  (x-3,y+3),(x-size,y),(x-3,y-3)], fill="#E8B445")
scene.save(main / "assets/blank.webp", lossless=True)
