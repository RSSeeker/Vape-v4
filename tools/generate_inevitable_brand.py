#!/usr/bin/env python3
"""Regenerate the checked-in InevitableClient icon and wordmarks (requires Pillow)."""
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
    draw.rounded_rectangle((20, 20, 1004, 1004), 230, fill=(10, 10, 10, 255),
                           outline=(112, 112, 112, 255), width=9)
    # A clean, high-contrast I monogram remains legible at Windows icon sizes.
    draw.rounded_rectangle((260, 235, 764, 320), 34, fill=(246, 246, 246, 255))
    draw.rounded_rectangle((466, 285, 558, 738), 30, fill=(246, 246, 246, 255))
    draw.rounded_rectangle((260, 704, 764, 789), 34, fill=(246, 246, 246, 255))
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
    image.save(ROOT / "assets/inevitable-icon.png", optimize=True)
    image.save(ROOT / "native/inevitable.ico", sizes=[(16,16), (24,24), (32,32), (48,48), (64,64), (128,128), (256,256)])
    logo = Image.new("RGBA", (480, 96), (0, 0, 0, 0))
    logo.alpha_composite(icon(80), (4, 8))
    centered_text(logo, "INEVITABLE", (102, 8, 370, 80), (246, 246, 246, 255))
    logo.save(ROOT / "native/loader_gui/assets/logo.png", optimize=True)
    # Keep the texture keys for the existing atlas, but replace their pixels.
    word = Image.new("RGBA", (712, 212), (0, 0, 0, 0))
    centered_text(word, "INEVITABLE", (4, 8, 704, 192), (255, 255, 255, 255))
    word.resize((178, 53), RESAMPLE).save(ROOT / "src/main/resources/resources/textures/vapelogo.png", optimize=True)
    badge = Image.new("RGBA", (76, 53), (0, 0, 0, 0))
    badge.alpha_composite(icon(53), (11, 0))
    badge.save(ROOT / "src/main/resources/resources/textures/v4.png", optimize=True)


if __name__ == "__main__":
    main()
