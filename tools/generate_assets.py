import os
import shutil
from PIL import Image, ImageDraw

SRC_IMG = r"C:\Users\Solo-P-Leveller\.gemini\antigravity\brain\b7812721-6b54-453f-9286-917af11f7e17\.user_uploaded\media_1795416534305_20c20d93.jpg"
ROOT = r"d:\Program files\phonebox-AI"

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
        os.path.join(ROOT, "local ai", "web", "logo.png"),
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

    for ico_name in ["phonebox.ico", "phonemon.ico"]:
        ico_dest = os.path.join(ROOT, "agent", "PhoneBox.Agent", ico_name)
        ico_frames[-1].save(
            ico_dest,
            format="ICO",
            sizes=[(f.width, f.height) for f in ico_frames],
            append_images=ico_frames[:-1]
        )
        print(f"Saved ICO: {ico_dest} ({os.path.getsize(ico_dest)} bytes)")

    # Web favicons
    for web_ico in [
        os.path.join(ROOT, "tools", "pages", "favicon.ico"),
        os.path.join(ROOT, "local ai", "web", "favicon.ico"),
    ]:
        os.makedirs(os.path.dirname(web_ico), exist_ok=True)
        shutil.copy(os.path.join(ROOT, "agent", "PhoneBox.Agent", "phonebox.ico"), web_ico)
        print(f"Copied web favicon: {web_ico}")

    # 3. Android Mipmaps and Drawables
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }

    # Android main app
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

    # Foreground for adaptive icon
    drawable_dir = os.path.join(android_res, "drawable")
    fg_adaptive = create_adaptive_foreground(base_img, canvas_size=432, content_ratio=0.74)
    fg_adaptive.save(os.path.join(drawable_dir, "ic_launcher_fg.png"), "PNG")
    fg_adaptive.save(os.path.join(drawable_dir, "ic_launcher_foreground.png"), "PNG")
    old_fg_xml = os.path.join(drawable_dir, "ic_launcher_fg.xml")
    if os.path.exists(old_fg_xml):
        os.remove(old_fg_xml)
        print(f"Removed obsolete vector: {old_fg_xml}")

    # Local AI app
    local_ai_res = os.path.join(ROOT, "local ai", "app", "src", "main", "res")
    for folder, sz in densities.items():
        dir_path = os.path.join(local_ai_res, folder)
        os.makedirs(dir_path, exist_ok=True)
        sq = squircle_1024.resize((sz, sz), Image.Resampling.LANCZOS)
        sq.save(os.path.join(dir_path, "ic_launcher.png"), "PNG")
        round_img = base_img.copy()
        round_img.putalpha(make_circle_mask((1024, 1024)))
        round_icon = round_img.resize((sz, sz), Image.Resampling.LANCZOS)
        round_icon.save(os.path.join(dir_path, "ic_launcher_round.png"), "PNG")

    local_ai_drawable = os.path.join(local_ai_res, "drawable")
    fg_adaptive.save(os.path.join(local_ai_drawable, "ic_launcher_fg.png"), "PNG")
    fg_adaptive.save(os.path.join(local_ai_drawable, "ic_launcher_foreground.png"), "PNG")
    old_local_fg_xml = os.path.join(local_ai_drawable, "ic_launcher_foreground.xml")
    if os.path.exists(old_local_fg_xml):
        os.remove(old_local_fg_xml)
        print(f"Removed obsolete vector: {old_local_fg_xml}")

    # 4. Copy to installed app directory if present
    installed_app_dir = os.path.expandvars(r"%LOCALAPPDATA%\PhoneBox\app")
    if os.path.exists(installed_app_dir):
        shutil.copy(os.path.join(ROOT, "agent", "PhoneBox.Agent", "logo.png"), os.path.join(installed_app_dir, "logo.png"))
        shutil.copy(os.path.join(ROOT, "agent", "PhoneBox.Agent", "phonebox.ico"), os.path.join(installed_app_dir, "phonebox.ico"))
        shutil.copy(os.path.join(ROOT, "agent", "PhoneBox.Agent", "phonemon.ico"), os.path.join(installed_app_dir, "phonemon.ico"))
        print(f"Updated installed app assets in {installed_app_dir}")

    print("All assets successfully generated!")

if __name__ == "__main__":
    main()
