#!/usr/bin/env bash
# docs/03 §7 "纯净 jar 独立启动冒烟"（headless 开服 60s 无异常，A0-8）——CI 与本地跑同一份脚本，
# 避免"CI 里那段 shell 从没被执行过"这种假绿。
#
# 用法: server-smoke.sh <已装好的服务端目录> <启动命令...>
#   CI:  server-smoke.sh "$WORK" ./run.sh
#   本地: server-smoke.sh "$WORK" ./runlocal.sh     # Windows 下 java @win_args.txt 的包装
# 环境变量:
#   SMOKE_JAR    要放进 mods/ 的服务端 jar（必填）
#   SMOKE_UPTIME Done 之后继续保持运行的秒数（默认 60，docs/03 §7 的口径）
set -uo pipefail

work="${1:?usage: server-smoke.sh <server-dir> <run-command...>}"
shift
run_cmd=("$@")
jar="${SMOKE_JAR:?set SMOKE_JAR to the dedicated-server jar to smoke}"
uptime="${SMOKE_UPTIME:-60}"

mkdir -p "$work/mods"
rm -f "$work/mods/"strife-server-*.jar "$work/server.log"
cp "$jar" "$work/mods/"
echo "eula=true" > "$work/eula.txt"
# 不给 server.properties：让服务端自己生成完整的一份（手写半份会在首启时刷一条无害但刺眼的 ERROR）。
rm -rf "$work/world" "$work/server.properties"

cd "$work"
# 必须自带截止时间：服务端在 Done 之前就死掉时，没有截止时间的 until 会永远等下去，
# 而 timeout 只能杀掉 run.sh、杀不掉这个还在往已断开的管道里写的子 shell。
(
  deadline=$((SECONDS + 300))
  until grep -q "Done (" server.log 2>/dev/null; do
    sleep 3
    [ "$SECONDS" -lt "$deadline" ] || break
  done
  sleep "$uptime"
  echo stop
) | timeout $((uptime + 600)) "${run_cmd[@]}" nogui >server.log 2>&1
rc=$?
echo "server exit code: $rc (uptime window: ${uptime}s)"

fail() {
  echo "::error::$1"
  tail -n 40 server.log
  exit 1
}

grep -q "Done (" server.log || fail "server never finished startup (no 'Done (' in log)"
grep -q "Stopping the server" server.log || fail "server did not reach a clean stop"
# 这条是"冒烟测的是服务端而不是客户端"的证据，缺了它整个检查就没意义。
grep -q "Env=SERVER" server.log || fail "not a dedicated-server boot (no Env=SERVER)"
grep -q "(strife)" server.log || fail "the strife MOD was not discovered by the server"
grep -q "strife platform entry constructed" server.log ||
  fail "strife was loaded but its platform entry never constructed on the server"
grep -nE "Exception in thread|Caused by:|A fatal error|Mod loading error" server.log &&
  fail "server threw during boot, the ${uptime}s window, or shutdown"
# 首次开服自己生成 server.properties 时必然报这一行，是唯一已知的无害 ERROR。
if grep -n "/ERROR" server.log | grep -v "Failed to load properties from file"; then
  fail "server logged an unexplained ERROR line"
fi
[ "$rc" -eq 0 ] || fail "server process exited with $rc"

echo "server-smoke OK: $(grep -m1 -o 'Done ([0-9.]*s)' server.log), ${uptime}s uptime, stopped clean,"
echo "  strife loaded on Env=SERVER, no unexplained ERROR line"
