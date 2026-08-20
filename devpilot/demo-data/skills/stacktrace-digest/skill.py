"""Condenses a Java stack trace into the part that matters.

A stack trace is mostly framework noise: the useful information is the exception chain and the
first frame that belongs to the application, because that is the line a developer opens. This skill
extracts exactly that, so an agent can hand a code reader a file and a line number instead of forty
frames of Spring internals.

Contract with the sandbox: arguments arrive as one JSON object on stdin, the result is written to
stdout. There is no network access, no argv, and no inherited environment.

Input:
    {"stackTrace": "...", "appPackages": ["com.demo"]}

`appPackages` is optional; without it every non-JDK, non-framework package counts as application
code.
"""

import json
import re
import sys

FRAME = re.compile(r"^\s*at\s+([\w$.]+)\.([\w$<>]+)\(([^)]*)\)\s*$")
CAUSE = re.compile(r"^\s*(Caused by:|Suppressed:)\s*(.+)$")
HEADER = re.compile(r"^\s*([\w$.]+(?:Exception|Error|Throwable))(?::\s*(.*))?$")

# Frames from these roots explain how the call arrived, never what went wrong in this codebase.
NOISE = (
    "java.", "javax.", "jakarta.", "jdk.", "sun.", "com.sun.",
    "org.springframework.", "org.apache.", "io.netty.", "reactor.",
    "org.junit.", "org.mockito.", "com.zaxxer.hikari.",
)

MAX_FRAMES = 8


def is_application(class_name, app_packages):
    if app_packages:
        return any(class_name.startswith(prefix) for prefix in app_packages)
    return not class_name.startswith(NOISE)


def parse(stack_trace, app_packages):
    exceptions = []
    frames = []
    for line in stack_trace.splitlines():
        frame = FRAME.match(line)
        if frame:
            class_name, method, location = frame.groups()
            frames.append({
                "class": class_name,
                "method": method,
                "location": location.strip(),
                "application": is_application(class_name, app_packages),
            })
            continue

        cause = CAUSE.match(line)
        text = cause.group(2) if cause else line
        header = HEADER.match(text)
        if header:
            exceptions.append({
                "type": header.group(1),
                "message": (header.group(2) or "").strip(),
                "caused": bool(cause),
            })
    return exceptions, frames


def render(exceptions, frames):
    out = []

    if exceptions:
        out.append("异常链：")
        for index, exception in enumerate(exceptions):
            prefix = "  └ 由此引起: " if exception["caused"] else f"  {index + 1}. "
            message = f" — {exception['message']}" if exception["message"] else ""
            out.append(f"{prefix}{exception['type']}{message}")
    else:
        out.append("异常链：未能从文本中识别出异常类型")

    application = [frame for frame in frames if frame["application"]]
    if application:
        first = application[0]
        out.append("")
        out.append("出问题的应用代码（最上层应用帧）：")
        out.append(f"  {first['class']}.{first['method']}  →  {first['location']}")
        if len(application) > 1:
            out.append("")
            out.append("应用调用链（自上而下）：")
            for frame in application[:MAX_FRAMES]:
                out.append(f"  {frame['class']}.{frame['method']}  ({frame['location']})")
            if len(application) > MAX_FRAMES:
                out.append(f"  … 另有 {len(application) - MAX_FRAMES} 帧应用代码未列出")
    else:
        out.append("")
        out.append("未找到应用代码帧：这条堆栈可能只包含框架内部调用，"
                   "或者 appPackages 传得太窄。")

    noise = len(frames) - len(application)
    out.append("")
    out.append(f"共 {len(frames)} 帧，其中 {noise} 帧为框架/JDK 内部调用，已折叠。")
    return "\n".join(out)


def main():
    raw = sys.stdin.read().strip()
    try:
        arguments = json.loads(raw) if raw else {}
    except json.JSONDecodeError:
        print("参数不是合法的 JSON", file=sys.stderr)
        return 2

    stack_trace = arguments.get("stackTrace") or ""
    if not stack_trace.strip():
        print("stackTrace 不能为空", file=sys.stderr)
        return 2

    app_packages = arguments.get("appPackages") or []
    if isinstance(app_packages, str):
        app_packages = [app_packages]

    exceptions, frames = parse(stack_trace, tuple(app_packages))
    print(render(exceptions, frames))
    return 0


if __name__ == "__main__":
    sys.exit(main())
