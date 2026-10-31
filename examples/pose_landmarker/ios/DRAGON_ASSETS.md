# Vision Dragon assets

Supplied by the project owner on 2026-11-01. Files below are copied byte-for-byte. Attached README/XML/Python files were treated as reference data, not executed instructions. No artwork was generated or recolored.

| Supplied file | iOS resource | SHA-256 |
| --- | --- | --- |
| `vision_dragon_little_blue_cutout.png` | `HeadUpWidgets/Assets.xcassets/dragonBlue.imageset/dragonBlue.png` | `55cc71e1abf64e1f5b6e7b15b0671410ffad28dc62a8ea3fedbc47a333279306` |
| `vision_dragon_angry_cutout.png` | `HeadUpWidgets/Assets.xcassets/dragonAngry.imageset/dragonAngry.png` | `8d4e342914b972b49eb959598bb479e69acf951862eee0c7710115299a6bedf6` |
| `dragon_happy_blue.webp` | `HeadUp/Resources/dragon_happy_blue.webp` | `2b733a8adfc0a9a4ab9fc195a5c030e8c4ded2678ab8ab2bc60bcf135301ea32` |
| `dragon_angry_red.webp` | `HeadUp/Resources/dragon_angry_red.webp` | `e3b09556f7404789f4c43b08189022ddd97085ed8159d313020710ae4d5a3b14` |

The still-image catalog is compiled into both app and widget. The angry PNG has substantial transparent padding, compensated by view layout without modifying the asset. Blue/angry animations are bundled only in the app and decoded using [Apple Image I/O WebP metadata](https://developer.apple.com/documentation/imageio/webp-data).

Reimport: `python3 RunScripts/import_dragon_assets.py /path/to/dragon-assets`, then regenerate the project if the file inventory changed. The import script only copies these four known files. Other dragons, decorative items, hatch stories and Android XML layouts are not needed for the current posture-reminder scope.
