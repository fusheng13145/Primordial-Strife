"""rss_sampler.py — P1-18 2h 连续运行的内存采样器（运行手册同款方法，自动化化）。

每 10 分钟采样一次无头服务器的 RSS（WorkingSet64），共 13 次（含基线），输出 CSV 到 stdout 与
deliverables/artifacts/p1-18-采样记录.csv。结束判据：首尾差 <200MB。
"""
import csv
import subprocess
import sys
import time

SAMPLES = 13
INTERVAL_SEC = 600  # 10 分钟

OUT = r"C:\Users\ASUS\Desktop\Ucode\backend-project\玄黄劫争\Primordial Strife\deliverables\artifacts\p1-18-采样记录.csv"

PS_SCRIPT = (
    "Get-Process java -ErrorAction SilentlyContinue | "
    "Where-Object { $_.MainWindowTitle -eq '' } | "
    "Measure-Object WorkingSet64 -Sum | "
    "ForEach-Object { [math]::Round($_.Sum / 1MB) }"
)


def sample_rss_mb() -> int:
    out = subprocess.run(
        ["powershell", "-Command", PS_SCRIPT], capture_output=True, text=True, timeout=60
    )
    text = out.stdout.strip()
    return int(text) if text.isdigit() else -1


def main() -> int:
    rows = []
    for i in range(SAMPLES):
        ts = time.strftime("%H:%M:%S")
        rss = sample_rss_mb()
        label = "baseline" if i == 0 else f"t+{i * 10}min"
        rows.append((label, ts, rss))
        print(f"{label},{ts},{rss} MB", flush=True)
        if i < SAMPLES - 1:
            time.sleep(INTERVAL_SEC)
    with open(OUT, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["sample", "time", "rss_mb"])
        writer.writerows(rows)
    first, last = rows[0][2], rows[-1][2]
    delta = last - first
    verdict = "PASS (<200MB)" if abs(delta) < 200 else f"REVIEW (delta={delta}MB)"
    print(f"\nRSS first={first} last={last} delta={delta}MB -> {verdict}", flush=True)
    return 0 if abs(delta) < 200 else 2


if __name__ == "__main__":
    sys.exit(main())
