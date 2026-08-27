"""Build transparent, color-swapped Vision Dragon animations from source videos.

This is an asset-generation tool, not an Android runtime dependency. It extracts a
small frame set, removes the studio background once, creates five color variants,
and encodes compact animated WebP files for Android's ImageDecoder.
"""

from __future__ import annotations

import argparse
import colorsys
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageFilter


DRAGON_HUES = {
    "blue": None,
    "red": 0.01,
    "mint": 0.39,
    "violet": 0.76,
    "gold": 0.13,
}


def run(command: list[str]) -> None:
    subprocess.run(command, check=True)


def extract_frames(ffmpeg: Path, source: Path, output_dir: Path, fps: int, width: int) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    run(
        [
            str(ffmpeg),
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-i",
            str(source),
            "-vf",
            f"fps={fps},scale={width}:-2:flags=lanczos",
            str(output_dir / "frame_%04d.png"),
        ]
    )


def pad_square(image: Image.Image, size: int) -> Image.Image:
    image.thumbnail((size, size), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(image, ((size - image.width) // 2, (size - image.height) // 2))
    return canvas


def recolor_blue_regions(image: Image.Image, target_hue: float | None) -> Image.Image:
    if target_hue is None:
        return image.copy()

    source = image.convert("RGBA")
    pixels = list(source.getdata())
    recolored: list[tuple[int, int, int, int]] = []
    for red, green, blue, alpha in pixels:
        if alpha == 0:
            recolored.append((red, green, blue, alpha))
            continue
        hue, saturation, value = colorsys.rgb_to_hsv(red / 255.0, green / 255.0, blue / 255.0)
        # Keep eyes, belly, blush, horns and highlights intact. Only the cyan/blue
        # scales and wing membranes are shifted to the selected dragon color.
        blue_weight = max(0.0, min(1.0, (saturation - 0.18) / 0.35))
        hue_is_blue = 0.43 <= hue <= 0.75
        if hue_is_blue and blue_weight > 0.0:
            new_hue = (hue * (1.0 - blue_weight)) + (target_hue * blue_weight)
            new_red, new_green, new_blue = colorsys.hsv_to_rgb(new_hue, saturation, value)
            recolored.append(
                (
                    round(new_red * 255),
                    round(new_green * 255),
                    round(new_blue * 255),
                    alpha,
                )
            )
        else:
            recolored.append((red, green, blue, alpha))
    output = Image.new("RGBA", source.size)
    output.putdata(recolored)
    return output


def encode_webp(
    ffmpeg: Path,
    frames_dir: Path,
    output: Path,
    fps: int,
) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    run(
        [
            str(ffmpeg),
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-framerate",
            str(fps),
            "-i",
            str(frames_dir / "frame_%04d.png"),
            "-c:v",
            "libwebp_anim",
            "-pix_fmt",
            "yuva420p",
            "-lossless",
            "0",
            "-quality",
            "78",
            "-compression_level",
            "5",
            "-loop",
            "0",
            str(output),
        ]
    )


def process_source(
    source: Path,
    output_dir: Path,
    ffmpeg: Path,
    prefix: str,
    variants: dict[str, float | None],
    fps: int,
    size: int,
) -> None:
    try:
        from rembg import new_session, remove
    except ImportError as error:
        raise SystemExit("Install rembg[cpu] before running this tool.") from error

    with tempfile.TemporaryDirectory(prefix=f"headup_{prefix}_") as temporary:
        work = Path(temporary)
        extracted = work / "extracted"
        transparent = work / "transparent"
        extract_frames(ffmpeg, source, extracted, fps, size)
        transparent.mkdir(parents=True, exist_ok=True)

        session = new_session("u2netp")
        frame_paths = sorted(extracted.glob("frame_*.png"))
        for index, frame_path in enumerate(frame_paths, start=1):
            with Image.open(frame_path) as source_image:
                cutout = remove(
                    source_image.convert("RGB"),
                    session=session,
                    alpha_matting=False,
                    post_process_mask=True,
                ).convert("RGBA")
            alpha = cutout.getchannel("A").filter(ImageFilter.GaussianBlur(radius=0.35))
            cutout.putalpha(alpha)
            pad_square(cutout, size).save(transparent / frame_path.name, optimize=True)
            if index % 20 == 0 or index == len(frame_paths):
                print(f"{prefix}: removed background from {index}/{len(frame_paths)} frames", flush=True)

        for variant_name, target_hue in variants.items():
            variant_dir = work / variant_name
            variant_dir.mkdir(parents=True, exist_ok=True)
            for frame_path in sorted(transparent.glob("frame_*.png")):
                with Image.open(frame_path) as frame:
                    recolor_blue_regions(frame, target_hue).save(variant_dir / frame_path.name, optimize=True)
            encode_webp(
                ffmpeg,
                variant_dir,
                output_dir / f"dragon_{prefix}_{variant_name}.webp",
                fps,
            )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--happy", type=Path, required=True)
    parser.add_argument("--angry", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--ffmpeg", type=Path, required=True)
    parser.add_argument("--fps", type=int, default=15)
    parser.add_argument("--size", type=int, default=360)
    args = parser.parse_args()

    process_source(args.happy, args.output, args.ffmpeg, "happy", DRAGON_HUES, args.fps, args.size)
    process_source(args.angry, args.output, args.ffmpeg, "angry", {"red": None}, args.fps, args.size)


if __name__ == "__main__":
    main()
