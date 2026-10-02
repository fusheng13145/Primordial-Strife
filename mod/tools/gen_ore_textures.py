"""生成 M4 矿石方块贴图（16x16 RGBA PNG）。

与迭代 8 的物品贴图同一策略：程序化生成占位纹理，来源与替换流程记录在 CREDITS.md。
矿石贴图按"底色 + 矿斑"两层画：底色是石头灰，矿斑按矿石属性（灵玉=青金 / 赤炎=赤橙 / 寒玉=冰蓝）着色，
斑块位置用固定种子打散，保证"同一个矿在任何地方长得一样"（贴图是资源不是随机产物）。
"""

import struct
import zlib
from pathlib import Path

TEXTURES = Path(__file__).resolve().parents[1] / "content-base/src/main/resources/assets/strife/textures/block"

# (文件名, 矿斑主色, 矿斑高光色) —— 属性取自 tables/ores.csv 的 element 列
ORES = [
    ("block_ore_lingyu", (86, 156, 214), (168, 214, 246)),
    ("block_ore_chiyan", (198, 74, 42), (240, 148, 78)),
    ("block_ore_hanyu", (74, 132, 190), (150, 206, 240)),
]

STONE_DARK = (122, 122, 122)
STONE_LIGHT = (146, 146, 146)


def write_png(path: Path, size: int, pixels: list[list[tuple[int, int, int, int]]]) -> None:
    raw = b"".join(
        b"\x00" + b"".join(struct.pack("BBBB", *px) for px in row) for row in pixels
    )

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    header = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    path.write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )


def ore_texture(name: str, blob: tuple[int, int, int], highlight: tuple[int, int, int]) -> list:
    # 固定种子：矿斑布局是资源的一部分，不能每次跑生成器都变（否则 diff 噪声掩盖真实内容变更）。
    seed = sum(name.encode()) 
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            # 石头底纹：按坐标做固定棋盘扰动，两级灰。
            base = STONE_LIGHT if ((x * 7 + y * 13 + seed) // 3) % 5 == 0 else STONE_DARK
            # 矿斑：两个斜向条带 + 稀疏点，命中即用主色，带内偏移一格用高光色。
            band = (x + y * 2 + seed) % 7
            if band in (0, 1, 2):
                color = highlight if band == 1 else blob
            elif (x * 3 + y * 5 + seed) % 11 == 0:
                color = blob
            else:
                color = base
            row.append((color[0], color[1], color[2], 255))
        rows.append(row)
    return rows


def main() -> None:
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for name, blob, highlight in ORES:
        path = TEXTURES / f"{name}.png"
        write_png(path, 16, ore_texture(name, blob, highlight))
        print(f"wrote {path}")


if __name__ == "__main__":
    main()
