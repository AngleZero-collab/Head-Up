#!/usr/bin/env python3
"""Copy the supplied blue/alert artwork verbatim; never execute package scripts."""
import argparse
from pathlib import Path
import shutil

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('source', type=Path, help='Directory containing the supplied dragon assets')
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
pairs = [
    ('vision_dragon_little_blue_cutout.png', 'HeadUpWidgets/Assets.xcassets/dragonBlue.imageset/dragonBlue.png'),
    ('vision_dragon_angry_cutout.png', 'HeadUpWidgets/Assets.xcassets/dragonAngry.imageset/dragonAngry.png'),
    ('dragon_happy_blue.webp', 'HeadUp/Resources/dragon_happy_blue.webp'),
    ('dragon_angry_red.webp', 'HeadUp/Resources/dragon_angry_red.webp'),
]
for source, _ in pairs:
    if not (args.source / source).is_file():
        parser.error(f'Missing asset: {source}')
for source, destination in pairs:
    target = root / destination
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(args.source / source, target)
    print(f'Imported {source}')
