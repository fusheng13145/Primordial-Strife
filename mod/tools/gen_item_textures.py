"""gen_item_textures.py — 35 件新物品的 16×16 像素贴图程序化生成（EP1 丹药/铸器/妖兽体系）。

用法：python mod/tools/gen_item_textures.py
产出：mod/content-base/src/main/resources/assets/strife/textures/item/*.png

设计约束：
- 16×16 RGBA（Minecraft 物品贴图标准尺寸）
- 每件 2-3 色（base/highlight/shadow），与已有 8 张贴图风格一致
- 分类模板：丹药(圆形) / 草药(植物) / 矿石(菱形晶体) / 异火(火焰) / 功能(方/书/符)
"""
import os
from PIL import Image, ImageDraw

TEX_DIR = os.path.join(os.path.dirname(__file__), '..', 'content-base', 'src', 'main',
                       'resources', 'assets', 'strife', 'textures', 'item')
SIZE = 16
os.makedirs(TEX_DIR, exist_ok=True)


def hex2rgb(h):
    return (h >> 16 & 0xFF, h >> 8 & 0xFF, h & 0xFF, 255)


def shade(base, factor):
    r, g, b, a = base
    return (max(0, min(255, int(r * factor))),
            max(0, min(255, int(g * factor))),
            max(0, min(255, int(b * factor))), a)


def draw_pill(base, accent=None):
    """丹药：圆形带高光，底部阴影。"""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    hi = shade(base, 1.3)
    lo = shade(base, 0.6)
    # 主体圆 (3,3)-(12,12)
    d.ellipse([3, 3, 12, 12], fill=base, outline=lo)
    # 高光 (5,4)-(7,6)
    d.ellipse([5, 4, 7, 6], fill=hi)
    # 底部阴影弧
    d.arc([3, 6, 12, 13], 20, 160, fill=lo)
    if accent:
        d.ellipse([7, 7, 9, 9], fill=accent)
    return img


def draw_herb(base, stem_color):
    """草药：茎 + 两片叶 + 顶端花/果。"""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    hi = shade(base, 1.3)
    lo = shade(base, 0.6)
    stem = shade(stem_color, 0.8)
    # 茎
    d.line([8, 14, 8, 7], fill=stem, width=1)
    # 左叶
    d.polygon([(8, 11), (4, 9), (7, 13)], fill=base)
    d.polygon([(8, 11), (5, 10), (7, 12)], fill=hi)
    # 右叶
    d.polygon([(8, 9), (12, 7), (11, 11)], fill=base)
    d.polygon([(8, 9), (11, 8), (10, 10)], fill=hi)
    # 顶端花/果
    d.ellipse([6, 3, 10, 7], fill=base, outline=lo)
    d.ellipse([7, 4, 9, 6], fill=hi)
    return img


def draw_crystal(base):
    """矿石/晶体：菱形多面体。"""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    hi = shade(base, 1.4)
    lo = shade(base, 0.5)
    # 主菱形
    d.polygon([(8, 2), (13, 8), (8, 14), (3, 8)], fill=base, outline=lo)
    # 左亮面
    d.polygon([(8, 2), (3, 8), (8, 8)], fill=hi)
    # 右暗面
    d.polygon([(8, 8), (13, 8), (8, 14)], fill=lo)
    return img


def draw_flame(base, tip_color):
    """火焰：底宽顶尖摇曳形。"""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    hi = shade(base, 1.4)
    # 火焰主体（多边形近似）
    d.polygon([(8, 1), (12, 6), (13, 11), (8, 14), (3, 11), (4, 6)], fill=base)
    # 内焰
    d.polygon([(8, 4), (10, 8), (8, 12), (6, 8)], fill=tip_color)
    # 高光
    d.polygon([(8, 1), (10, 5), (8, 4)], fill=hi)
    return img


def draw_orb(base, accent):
    """圆球/珠：灵石/妖丹类。"""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    hi = shade(base, 1.5)
    lo = shade(base, 0.5)
    d.ellipse([3, 3, 13, 13], fill=base, outline=lo)
    d.ellipse([5, 4, 8, 7], fill=hi)
    d.arc([3, 6, 13, 14], 30, 150, fill=lo)
    if accent:
        d.ellipse([9, 8, 11, 10], fill=accent)
    return img


def draw_book(base, accent):
    """书本：封面+书脊+页面。"""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    hi = shade(base, 1.3)
    lo = shade(base, 0.5)
    page = (230, 220, 200, 255)
    # 封面
    d.rectangle([3, 2, 12, 14], fill=base, outline=lo)
    # 书脊
    d.rectangle([3, 2, 5, 14], fill=lo)
    # 页面
    d.rectangle([11, 3, 13, 13], fill=page)
    # 装饰（封面中心符号）
    d.rectangle([7, 6, 9, 10], fill=accent)
    d.rectangle([6, 7, 10, 9], fill=accent)
    return img


def draw_talisman(base, accent):
    """符箓：竖长纸条 + 红色符文。"""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    lo = shade(base, 0.7)
    # 纸条
    d.rectangle([5, 1, 11, 15], fill=base, outline=lo)
    # 符文（红色竖线+横线）
    d.line([8, 3, 8, 12], fill=accent, width=1)
    d.line([6, 5, 10, 5], fill=accent, width=1)
    d.line([6, 8, 10, 8], fill=accent, width=1)
    d.line([7, 11, 9, 11], fill=accent, width=1)
    return img


def draw_scarecrow(base, accent):
    """草人：十字架 + 头 + 草。"""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    hi = shade(base, 1.3)
    lo = shade(base, 0.6)
    # 十字架
    d.line([8, 3, 8, 14], fill=lo, width=2)
    d.line([4, 6, 12, 6], fill=lo, width=2)
    # 头
    d.ellipse([6, 1, 10, 5], fill=accent, outline=lo)
    # 草衣
    d.polygon([(5, 6), (11, 6), (10, 12), (6, 12)], fill=base)
    d.line([6, 8, 10, 8], fill=hi)
    d.line([7, 10, 9, 10], fill=hi)
    return img


def draw_ingot(base):
    """金属锭：梯形锭块。"""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    hi = shade(base, 1.4)
    lo = shade(base, 0.5)
    # 梯形锭
    d.polygon([(2, 10), (4, 6), (12, 6), (14, 10), (12, 12), (4, 12)], fill=base, outline=lo)
    # 顶面高光
    d.polygon([(4, 6), (12, 6), (11, 8), (5, 8)], fill=hi)
    return img


# ══════════════════════════════════════════════════════════════
# 35 件物品定义
# ══════════════════════════════════════════════════════════════
C = hex2rgb  # shorthand

ITEMS = {
    # ── 丹药（11）──
    'pill_baoxue':   draw_pill(C(0xCC3333)),           # 暴血丹：赤红
    'pill_jingang':  draw_pill(C(0x8888AA)),           # 金刚丹：银灰
    'pill_jifeng':   draw_pill(C(0x44BB44)),           # 疾风丹：翠绿
    'pill_huichun':  draw_pill(C(0xEE8888)),           # 回春丹：粉红
    'pill_liaoshang': draw_pill(C(0x77CC66)),          # 疗伤丹：草绿
    'pill_jiedu':    draw_pill(C(0xDDDDBB), C(0x44AA44)),  # 解毒丹：白绿
    'pill_ningshen': draw_pill(C(0x55BBCC)),           # 凝神丹：青蓝
    'pill_pojing':   draw_pill(C(0xDDBB44)),           # 破境丹：金黄
    'pill_wenyuan':  draw_pill(C(0x4488CC)),           # 稳元丹：深蓝
    'pill_wugou':    draw_pill(C(0xDDDDEE), C(0x8888CC)),  # 洗尘丹：银白
    'pill_xisui':    draw_pill(C(0xEEEEEE), C(0xCC88CC)),  # 洗髓丹：白紫
    'pill_xuanyu':   draw_pill(C(0x336633), C(0x88CC44)),  # 玄玉丹：墨绿
    # ── 灵药（5）──
    'item_hanshi_cao':  draw_herb(C(0x88CCEE), C(0x448866)),   # 寒石草：蓝白
    'item_zijin_hua':   draw_herb(C(0xAA66CC), C(0x554477)),   # 紫金花：紫金
    'item_baiyu_guo':   draw_herb(C(0xEEEECC), C(0x668844)),   # 白玉果：白绿
    'item_qingxin_lan': draw_herb(C(0x44CCBB), C(0x337755)),   # 青心兰：青碧
    'item_xuelian':     draw_herb(C(0xEEDDFF), C(0x8899BB)),   # 天山雪莲：银白
    # ── 矿石/兽核（4）──
    'item_xuantie':   draw_ingot(C(0x555555)),                 # 玄铁：暗灰
    'item_wuse_shi':  draw_crystal(C(0xCC88CC)),               # 五色石：多彩紫
    'item_yaodan':    draw_orb(C(0x883333), C(0xCC6600)),      # 妖丹：暗红橙
    'item_xuanyu':    draw_orb(C(0x2A5A2A), C(0x88CC44)),      # 玄玉：墨绿
    # ── 灵石中上品（2）──
    'item_lingshi_zhong': draw_orb(C(0xC0C0D0), C(0xE8E8F8)),  # 中品灵石：银白
    'item_lingshi_shang': draw_orb(C(0xFFD700), C(0xFFF8CC)),  # 上品灵石：金色
    # ── 异火（6）──
    'item_fire_mu':      draw_flame(C(0x44BB44), C(0x88EE88)),   # 木灵火：翠绿焰
    'item_fire_dixin':   draw_flame(C(0x886633), C(0xCCAA44)),   # 地心火：土橙焰
    'item_fire_xuanbing': draw_flame(C(0x66AAEE), C(0xCCEEFF)),  # 玄冰焰：冰蓝焰
    'item_fire_chiyan':  draw_flame(C(0xCC4422), C(0xFFAA44)),   # 赤炎：红橙焰
    'item_fire_jiuyou':  draw_flame(C(0x442266), C(0x8844CC)),   # 九幽冥火：暗紫焰
    'item_fire_taiyang': draw_flame(C(0xFFCC33), C(0xFFFFFF)),   # 太阳真火：金白焰
    # ── 天材地宝（3）──
    'item_tianguo': draw_orb(C(0xFFAA22), C(0xFFEE88)),          # 天元果：金橙
    'item_longxue': draw_herb(C(0xCC3333), C(0x44AA44)),         # 龙血草：红绿
    'item_fengxue': draw_crystal(C(0xFF8844)),                   # 凤血：橙红晶
    # ── 功能物品（4）──
    'item_dunfu':         draw_talisman(C(0xEEEECC), C(0xCC2222)),  # 遁符：白纸红符
    'item_tishen_caoren': draw_scarecrow(C(0xCCBB77), C(0xDDBB88)),  # 替身草人：草黄
    'item_guide_intro':   draw_book(C(0x8B4513), C(0xDAA520)),    # 修真入门：棕金书
    'item_zijin_hua_fall': draw_herb(C(0xAA66CC), C(0x554477)),   # 紫金花副本（已有）
}

# ══════════════════════════════════════════════════════════════
# 生成全部贴图
# ══════════════════════════════════════════════════════════════
count = 0
for item_id, img in ITEMS.items():
    path = os.path.join(TEX_DIR, f'{item_id}.png')
    img.save(path, 'PNG')
    count += 1

print(f'✦ {count} textures generated in {TEX_DIR}')
