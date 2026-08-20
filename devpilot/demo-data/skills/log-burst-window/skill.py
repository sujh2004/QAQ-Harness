"""Finds when a burst of errors started and how it developed.

"Errors went up" is not actionable; "errors went from 0 to 37 per minute at 10:31, three minutes
after the coupon service started returning 503" is. This skill buckets timestamps by minute, draws
a text histogram and reports the peak, so an agent can name a window instead of a vague "recently".

Contract with the sandbox: arguments arrive as one JSON object on stdin, the result is written to
stdout. There is no network access, no argv, and no inherited environment.

Input:
    {"timestamps": ["2026-08-20T10:31:02", ...], "bucketMinutes": 1}

Timestamps may also be embedded in whole log lines; the first ISO-like timestamp on each line is
used, which means the output of a log search can be pasted in unchanged.
"""

import json
import re
import sys
from collections import Counter
from datetime import datetime

TIMESTAMP = re.compile(
    r"(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})"
)

BAR_WIDTH = 40
MAX_BUCKETS = 60


def parse_timestamp(value):
    match = TIMESTAMP.search(str(value))
    if not match:
        return None
    year, month, day, hour, minute, second = (int(part) for part in match.groups())
    try:
        return datetime(year, month, day, hour, minute, second)
    except ValueError:
        return None


def bucket_of(moment, bucket_minutes):
    minute = (moment.minute // bucket_minutes) * bucket_minutes
    return moment.replace(minute=minute, second=0, microsecond=0)


def render(counts, bucket_minutes, parsed, skipped):
    if not counts:
        return "没有可用的时间戳：请确认传入的是日志行或 ISO 时间字符串。"

    ordered = sorted(counts.items())
    peak_bucket, peak_count = max(ordered, key=lambda item: (item[1], item[0]))
    scale = max(peak_count, 1)

    out = [
        f"共解析 {parsed} 条时间戳"
        + (f"，另有 {skipped} 条无法识别已跳过" if skipped else "")
        + f"，按 {bucket_minutes} 分钟分桶：",
        "",
    ]

    shown = ordered[-MAX_BUCKETS:]
    if len(ordered) > MAX_BUCKETS:
        out.append(f"（只显示最近 {MAX_BUCKETS} 个桶，共 {len(ordered)} 个）")
        out.append("")

    for moment, count in shown:
        bar = "█" * max(1, round(count / scale * BAR_WIDTH))
        out.append(f"  {moment:%m-%d %H:%M}  {count:>4}  {bar}")

    first_bucket = ordered[0][0]
    span_minutes = int((ordered[-1][0] - first_bucket).total_seconds() // 60) + bucket_minutes
    out.append("")
    out.append(f"起始：{first_bucket:%Y-%m-%d %H:%M}")
    out.append(f"峰值：{peak_bucket:%Y-%m-%d %H:%M}，{peak_count} 条")
    out.append(f"跨度：{span_minutes} 分钟")

    # A single dense bucket and a long flat tail mean different things to whoever reads this.
    if len(ordered) == 1:
        out.append("形态：全部集中在一个时间桶内，像是一次性爆发而非持续故障。")
    elif peak_count >= 3 * (sum(counts.values()) / len(ordered)):
        out.append("形态：存在明显尖峰，建议以峰值时刻为中心追查上游变更或依赖故障。")
    else:
        out.append("形态：分布较平缓，更像持续性问题而非突发事件。")
    return "\n".join(out)


def main():
    raw = sys.stdin.read().strip()
    try:
        arguments = json.loads(raw) if raw else {}
    except json.JSONDecodeError:
        print("参数不是合法的 JSON", file=sys.stderr)
        return 2

    values = arguments.get("timestamps")
    if isinstance(values, str):
        values = values.splitlines()
    if not values:
        print("timestamps 不能为空", file=sys.stderr)
        return 2

    try:
        bucket_minutes = int(arguments.get("bucketMinutes") or 1)
    except (TypeError, ValueError):
        bucket_minutes = 1
    bucket_minutes = min(max(bucket_minutes, 1), 60)

    counts = Counter()
    parsed = 0
    skipped = 0
    for value in values:
        moment = parse_timestamp(value)
        if moment is None:
            skipped += 1
            continue
        parsed += 1
        counts[bucket_of(moment, bucket_minutes)] += 1

    print(render(counts, bucket_minutes, parsed, skipped))
    return 0


if __name__ == "__main__":
    sys.exit(main())
