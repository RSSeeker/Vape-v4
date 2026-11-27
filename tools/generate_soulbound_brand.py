#!/usr/bin/env python3
"""Regenerate the checked-in Soulbound icon and wordmarks (requires Pillow)."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
FONT = ROOT / "src/main/resources/resources/proximabd.ttf"
RESAMPLE = Image.Resampling.LANCZOS


def curve(p0, p1, p2, p3, steps=50):
    result = []
    for i in range(steps + 1):
        t, u = i / steps, 1 - i / steps
        result.append((u**3*p0[0] + 3*u*u*t*p1[0] + 3*u*t*t*p2[0] + t**3*p3[0],
                       u**3*p0[1] + 3*u*u*t*p1[1] + 3*u*t*t*p2[1] + t**3*p3[1]))
    return result


def icon(size):
    image = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((20, 20, 1004, 1004), 240, fill=(15, 17, 31, 255))
    draw.rounded_rectangle((102, 102, 922, 922), 220, fill=(36, 30, 69, 255),
                           outline=(119, 98, 208, 255), width=7)
    points = curve((704, 310), (653, 221), (408, 214), (336, 300))
    points += curve((336, 300), (249, 405), (360, 459), (504, 505))[1:]
    points += curve((504, 505), (668, 558), (760, 610), (680, 710))[1:]
    points += curve((680, 710), (605, 803), (378, 802), (312, 708))[1:]
    draw.line(points, fill=(239, 236, 255, 255), width=88)
    for x, y in points:
        draw.ellipse((x-44, y-44, x+44, y+44), fill=(239, 236, 255, 255))
    draw.ellipse((655, 268, 689, 302), fill=(139, 224, 209, 255))
    draw.ellipse((335, 716, 369, 750), fill=(139, 224, 209, 255))
    return image.resize((size, size), RESAMPLE)


def centered_text(image, text, bounds, fill):
    x, y, width, height = bounds
    for size in range(height * 2, 5, -1):
        font = ImageFont.truetype(str(FONT), size)
        box = font.getbbox(text)
        if box[2] - box[0] <= width and box[3] - box[1] <= height:
            ImageDraw.Draw(image).text((x + (width - (box[2]-box[0]))/2 - box[0],
                                        y + (height - (box[3]-box[1]))/2 - box[1]),
                                       text, font=font, fill=fill)
            return
    raise ValueError("wordmark does not fit")


def main():
    image = icon(1024)
    image.save(ROOT / "assets/soulbound-icon.png", optimize=True)
    image.save(ROOT / "native/soulbound.ico", sizes=[(16,16), (24,24), (32,32), (48,48), (64,64), (128,128), (256,256)])
    logo = Image.new("RGBA", (480, 96), (0, 0, 0, 0))
    logo.alpha_composite(icon(80), (4, 8))
    centered_text(logo, "SOULBOUND", (102, 8, 370, 80), (246, 245, 255, 255))
    logo.save(ROOT / "native/loader_gui/assets/logo.png", optimize=True)
    # Keep the texture keys for the existing atlas, but replace their pixels.
    word = Image.new("RGBA", (712, 212), (0, 0, 0, 0))
    centered_text(word, "SOULBOUND", (4, 8, 704, 192), (255, 255, 255, 255))
    word.resize((178, 53), RESAMPLE).save(ROOT / "src/main/resources/resources/textures/vapelogo.png", optimize=True)
    badge = Image.new("RGBA", (76, 53), (0, 0, 0, 0))
    badge.alpha_composite(icon(53), (11, 0))
    badge.save(ROOT / "src/main/resources/resources/textures/v4.png", optimize=True)


if __name__ == "__main__":
    main()
