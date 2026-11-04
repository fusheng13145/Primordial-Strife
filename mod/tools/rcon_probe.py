"""rcon_probe.py — 无头专用服的 RCON 探针（ADR-027 ② 授权通道，P1-18 运行手册同款）。

用法：
  python rcon_probe.py "strife world ore status"            # 单条命令
  python rcon_probe.py --suite                              # 跑台账 N 组服务端冒烟套件

服务端启动：cd mod && gradlew :platform:runServer（enable-rcon=true，端口 25575）。
依赖：仅标准库。协议：Minecraft RCON（length/id/type/payload，小端）。
"""
import argparse
import socket
import struct
import sys

HOST = "127.0.0.1"
PORT = 25575
PASSWORD = "strife_rcon"

SERVERDATA_AUTH = 3
SERVERDATA_EXECCOMMAND = 2
SERVERDATA_AUTH_RESPONSE = 2


def _send(sock, req_id, ptype, payload: bytes):
    data = struct.pack("<ii", req_id, ptype) + payload + b"\x00\x00"
    sock.send(struct.pack("<i", len(data)) + data)


def _recv(sock) -> tuple[int, int, bytes]:
    raw = sock.recv(4)
    if len(raw) < 4:
        raise ConnectionError("connection closed")
    (length,) = struct.unpack("<i", raw)
    body = b""
    while len(body) < length:
        chunk = sock.recv(length - len(body))
        if not chunk:
            raise ConnectionError("connection closed mid-packet")
        body += chunk
    req_id, ptype = struct.unpack("<ii", body[:8])
    return req_id, ptype, body[8:-2]


def command(sock, cmd: str, req_id: int = 7) -> str:
    _send(sock, req_id, SERVERDATA_EXECCOMMAND, cmd.encode("utf-8"))
    rid, _ptype, payload = _recv(sock)
    if rid == -1:
        raise PermissionError("RCON auth failed")
    return payload.decode("utf-8", errors="replace")


def connect(password: str = PASSWORD) -> socket.socket:
    sock = socket.create_connection((HOST, PORT), timeout=30)
    _send(sock, 1, SERVERDATA_AUTH, password.encode("utf-8"))
    # 服务端会先回一条空 SERVERDATA_AUTH_RESPONSE 之前的值包，读到 id != -1 的 AUTH_RESPONSE 为止
    for _ in range(3):
        rid, ptype, _payload = _recv(sock)
        if ptype == SERVERDATA_AUTH_RESPONSE:
            if rid == -1:
                raise PermissionError("RCON password rejected")
            return sock
    raise ConnectionError("no auth response")


# 台账 N 组服务端冒烟套件（无玩家上下文可验的子集；每条 = (判据说明, 命令, 输出必须含的子串列表)）。
# locate 类命令的成功输出是 "The nearest <what> is at [x, y, z]"；setblock 前必须 forceload 区块。
SUITE = [
    ("产物面：配方机加载全部丹方", "strife recipe list", ["pill_huichun", "pill_xisui"]),
    ("经济面：坊市价目可读", "strife market list", ["pill_peiyuan"]),
    ("经济面：拍卖行目录", "strife auction list", ["剩余"]),
    ("维度面：miji_realm 已注册", "execute in strife:miji_realm positioned 0 90 0 run locate biome strife:miji_biome", ["nearest strife:miji_biome"]),
    ("维度面：upper_realm 已注册", "execute in strife:upper_realm positioned 0 90 0 run locate biome strife:upper_realm_biome", ["nearest strife:upper_realm_biome"]),
    ("加载面：秘境探针区块 forceload", "execute in strife:miji_realm run forceload add -16 -16 16 16", ["chunks"]),
    ("方块面：炼丹炉四阶已注册", "execute in strife:miji_realm run setblock 8 100 8 strife:furnace_long", ["Changed the block"]),
    ("实体面：秘境妖兽类型已注册", "execute in strife:miji_realm run summon strife:fire_fox 8 92 8", ["Summoned new"]),
    ("结构面：望仙台可定位", "execute in strife:upper_realm run locate structure strife:wangxian_tai", ["nearest strife:wangxian_tai"]),
    ("清理：炉/探针区还原", "execute in strife:miji_realm run setblock 8 100 8 minecraft:air", ["Changed the block"]),
]


def run_suite() -> int:
    failures = 0
    sock = connect()
    try:
        for label, cmd, expects in SUITE:
            try:
                out = command(sock, cmd)
                ok = all(e in out for e in expects)
            except Exception as exc:  # noqa: BLE001 探针：任何异常都记失败并继续
                out, ok = f"<EXC {exc}>", False
            mark = "OK " if ok else "FAIL"
            print(f"[{mark}] {label}\n       $ {cmd}\n       -> {out[:160]}")
            if not ok:
                failures += 1
    finally:
        sock.close()
    print(f"\nsuite: {len(SUITE) - failures}/{len(SUITE)} ok")
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", nargs="?", help="单条 RCON 命令；与 --suite 互斥")
    parser.add_argument("--suite", action="store_true", help="跑台账 N 组服务端冒烟套件")
    args = parser.parse_args()
    if args.suite:
        return 1 if run_suite() else 0
    if not args.command:
        parser.error("需要一条命令或 --suite")
    sock = connect()
    try:
        print(command(sock, args.command))
    finally:
        sock.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
