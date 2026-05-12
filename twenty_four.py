"""24 点：输入四个数，判断能否得到 24 并给出一条算式。"""

from __future__ import annotations

import math
import re
import sys
import unicodedata
from typing import Optional

TARGET = 24.0
ABS_TOL = 1e-9


def solve(items: list[tuple[float, str]]) -> Optional[str]:
    """返回第一条使结果为 TARGET 的表达式，无解返回 None。"""
    if len(items) == 1:
        v, s = items[0]
        if math.isclose(v, TARGET, rel_tol=0.0, abs_tol=ABS_TOL):
            return s
        return None

    n = len(items)
    for i in range(n):
        for j in range(i + 1, n):
            a_v, a_s = items[i]
            b_v, b_s = items[j]
            rest = [items[k] for k in range(n) if k not in (i, j)]

            branches: list[tuple[float, str]] = [
                (a_v + b_v, f"({a_s}+{b_s})"),
                (a_v - b_v, f"({a_s}-{b_s})"),
                (b_v - a_v, f"({b_s}-{a_s})"),
                (a_v * b_v, f"({a_s}*{b_s})"),
            ]
            if not math.isclose(b_v, 0.0, rel_tol=0.0, abs_tol=ABS_TOL):
                branches.append((a_v / b_v, f"({a_s}/{b_s})"))
            if not math.isclose(a_v, 0.0, rel_tol=0.0, abs_tol=ABS_TOL):
                branches.append((b_v / a_v, f"({b_s}/{a_s})"))

            for nv, ns in branches:
                found = solve(rest + [(nv, ns)])
                if found is not None:
                    return found
    return None


def _format_num(x: float) -> str:
    if math.isclose(x, round(x), rel_tol=0.0, abs_tol=1e-12):
        return str(int(round(x)))
    return repr(x)


_ZW_RE = re.compile(r"[\u200b-\u200d\ufeff\u2060]")
# 空白 + 英文/中文逗号，避免 "10，11，12，13" 被当成一个 token
_SPLIT_RE = re.compile(r"[\s,，]+")


def _parse_float_token(raw: str) -> float:
    """规范化全角数字、零宽字符后再解析，避免 10/１２ 等合法输入被误判。"""
    t = unicodedata.normalize("NFKC", raw).strip()
    t = _ZW_RE.sub("", t)
    return float(t)


def _run_case(nums: list[float]) -> None:
    items = [(x, _format_num(x)) for x in nums]
    expr = solve(items)
    if expr is None:
        print("不可以")
    else:
        print(expr)


def main() -> None:
    # 合并命令行参数并按空白/逗号切分，支持 "1 2 3 4"、"1,2,3,4"、"１０，２，３，４"
    argv_tokens: list[str] = []
    for arg in sys.argv[1:]:
        arg_n = unicodedata.normalize("NFKC", arg)
        argv_tokens.extend(p for p in _SPLIT_RE.split(arg_n) if p)

    if len(argv_tokens) >= 4:
        try:
            nums = [_parse_float_token(argv_tokens[i]) for i in range(4)]
        except ValueError:
            print("不可以")
            return
        _run_case(nums)
        return

    if len(argv_tokens) == 0:
        # 交互式可读多行；否则第一次输出后退出到 PowerShell，再输入数字会被当成 shell 命令报错
        if sys.stdin.isatty():
            print(
                "每行输入四个数（空格或逗号分隔）。可连续多组；空行忽略；"
                "Windows 按 Ctrl+Z 后回车结束。",
                file=sys.stderr,
            )
        try:
            while True:
                try:
                    line = unicodedata.normalize("NFKC", input()).strip().lstrip("\ufeff")
                except EOFError:
                    break
                if not line:
                    continue
                parts = [p for p in _SPLIT_RE.split(line) if p]
                if len(parts) != 4:
                    print("不可以")
                    continue
                try:
                    nums = [_parse_float_token(p) for p in parts]
                except ValueError:
                    print("不可以")
                    continue
                _run_case(nums)
        except KeyboardInterrupt:
            print(file=sys.stderr)
        return

    print("不可以")


if __name__ == "__main__":
    main()
