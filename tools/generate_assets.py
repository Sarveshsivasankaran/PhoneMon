import os
import shutil
import sys
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_IMG = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "docs", "logo.png")


def make_squircle_mask(size, radius_ratio=0.20):
    w, h = size
    r = int(min(w, h) * radius_ratio)
    scale = 4
    mask = Image.new("L", (w * scale, h * scale), 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle([0, 0, w * scale, h * scale], radius=r * scale, fill=255)
    return mask.resize((w, h), Image.Resampling.LANCZOS)


def make_circle_mask(size):
    w, h = size
    scale = 4
    mask = Image.new("L", (w * scale, h * scale), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse([0, 0, w * scale, h * scale], fill=255)
    return mask.resize((w, h), Image.Resampling.LANCZOS)


def create_adaptive_foreground(src_img, canvas_size=432, content_ratio=0.74):
    """Creates a transparent 432x432 canvas with the centered logo fitting inside Android's 72dp safe zone."""
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    target_dim = int(canvas_size * content_ratio)
    resized_content = src_img.resize((target_dim, target_dim), Image.Resampling.LANCZOS)
    offset = (canvas_size - target_dim) // 2
    canvas.paste(resized_content, (offset, offset))
    return canvas


def main():
    base_img = Image.open(SRC_IMG).convert("RGBA")
    print(f"Loaded source image: {base_img.size}")

    # 1. Full logo PNGs (512x512)
    logo_512 = base_img.resize((512, 512), Image.Resampling.LANCZOS)
    logo_paths = [
        os.path.join(ROOT, "agent", "PhoneBox.Agent", "logo.png"),
        os.path.join(ROOT, "docs", "logo.png"),
        os.path.join(ROOT, "tools", "pages", "logo.png"),
    ]
    for p in logo_paths:
        os.makedirs(os.path.dirname(p), exist_ok=True)
        logo_512.save(p, "PNG")
        print(f"Saved logo: {p}")

    # 2. Windows ICO generation
    squircle_1024 = base_img.copy()
    mask_1024 = make_squircle_mask((1024, 1024), radius_ratio=0.18)
    squircle_1024.putalpha(mask_1024)

    ico_sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    ico_frames = [squircle_1024.resize(sz, Image.Resampling.LANCZOS) for sz in ico_sizes]

    ico_dest = os.path.join(ROOT, "agent", "PhoneBox.Agent", "phonebox.ico")
    ico_frames[-1].save(
        ico_dest,
        format="ICO",
        sizes=[(f.width, f.height) for f in ico_frames],
        append_images=ico_frames[:-1],
    )
    print(f"Saved ICO: {ico_dest} ({os.path.getsize(ico_dest)} bytes)")

    web_ico = os.path.join(ROOT, "tools", "pages", "favicon.ico")
    os.makedirs(os.path.dirname(web_ico), exist_ok=True)
    shutil.copy(ico_dest, web_ico)
    print(f"Copied web favicon: {web_ico}")

    # 3. Android Mipmaps and Drawables
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }

    android_res = os.path.join(ROOT, "android", "app", "src", "main", "res")
    for folder, sz in densities.items():
        dir_path = os.path.join(android_res, folder)
        os.makedirs(dir_path, exist_ok=True)
        sq = squircle_1024.resize((sz, sz), Image.Resampling.LANCZOS)
        sq.save(os.path.join(dir_path, "ic_launcher.png"), "PNG")
        round_img = base_img.copy()
        round_img.putalpha(make_circle_mask((1024, 1024)))
        round_icon = round_img.resize((sz, sz), Image.Resampling.LANCZOS)
        round_icon.save(os.path.join(dir_path, "ic_launcher_round.png"), "PNG")

    drawable_dir = os.path.join(android_res, "drawable")
    fg_adaptive = create_adaptive_foreground(base_img, canvas_size=432, content_ratio=0.74)
    fg_adaptive.save(os.path.join(drawable_dir, "ic_launcher_fg.png"), "PNG")
    fg_adaptive.save(os.path.join(drawable_dir, "ic_launcher_foreground.png"), "PNG")

    print("All assets successfully generated!")


if __name__ == "__main__":
    main()
