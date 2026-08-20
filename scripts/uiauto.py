#!/usr/bin/env python3
"""실기기 UI 자동화 보조 (scripts/verify-timer-recording.sh 전용).

uiautomator XML 덤프에서 노드를 찾고, 스크린샷에서 플로팅 버블(빨간 원)을 찾는다.
버블은 시스템 오버레이 창이라 uiautomator 덤프에 잡히지 않으므로 픽셀로 찾아야 한다.
"""

from __future__ import annotations

import re
import sys

BOUNDS = re.compile(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')

# Kinetic recRed(#EF4444) 판정 여유값. 화면 합성/압축 오차를 감안한다.
RED_MIN, GREEN_MAX, BLUE_MAX = 200, 120, 120

# 버블 원 지름 52dp. 기기 density를 곱해 픽셀로 환산한 뒤 이 비율만큼 오차를 허용한다.
CIRCLE_DP = 52
SIZE_TOLERANCE = 0.35
# 원이라면 채움 비율이 pi/4(0.785)에 가깝다. 사각 아이콘/썸네일을 걸러낸다.
MIN_FILL, MAX_FILL = 0.55, 0.95


def _node_centers(xml: str, attr: str, value: str) -> list[tuple[int, int]]:
    """`attr="value"`를 가진 노드들의 중심 좌표."""
    centers = []
    for node in xml.split("<"):
        if f'{attr}="{value}"' not in node:
            continue
        found = BOUNDS.search(node)
        if not found:
            continue
        x1, y1, x2, y2 = (int(v) for v in found.groups())
        centers.append(((x1 + x2) // 2, (y1 + y2) // 2))
    return centers


def find_node(xml_path: str, needle: str) -> None:
    """text 또는 content-desc가 정확히 일치하는 첫 노드의 중심을 출력한다."""
    find_any_node(xml_path, [needle])


def find_any_node(xml_path: str, needles: list[str]) -> None:
    """후보 문구 중 먼저 발견되는 노드의 중심을 출력한다 (시스템 UI 언어 대응)."""
    xml = open(xml_path, encoding="utf-8", errors="replace").read()
    for needle in needles:
        for attr in ("text", "content-desc"):
            centers = _node_centers(xml, attr, needle)
            if centers:
                print(f"{centers[0][0]} {centers[0][1]}")
                return
    sys.exit(f"노드를 찾지 못했다: {needles!r}")


def find_desc(xml_path: str, needle: str) -> None:
    """content-desc만 찾는다.

    라벨 텍스트와 버튼의 contentDescription이 같은 문구인 경우(예: "녹화 시작")
    text로 찾으면 클릭할 수 없는 라벨이 먼저 잡힌다.
    """
    xml = open(xml_path, encoding="utf-8", errors="replace").read()
    centers = _node_centers(xml, "content-desc", needle)
    if not centers:
        sys.exit(f"content-desc 노드를 찾지 못했다: {needle!r}")
    print(f"{centers[0][0]} {centers[0][1]}")


def find_edit_texts(xml_path: str) -> None:
    """EditText 노드 중심을 x 순서로 출력한다 (시/분/초 입력 필드)."""
    xml = open(xml_path, encoding="utf-8", errors="replace").read()
    centers = _node_centers(xml, "class", "android.widget.EditText")
    if not centers:
        sys.exit("입력 필드를 찾지 못했다")
    for x, y in sorted(centers):
        print(f"{x} {y}")


def find_bubble(png_path: str, density: float) -> None:
    """스크린샷에서 플로팅 버블(빨간 원)의 중심을 출력한다.

    버블은 좌우 가장자리에 붙으므로 가장자리 밴드만 훑고, 연결된 빨간 덩어리 중
    크기와 채움 비율이 원에 맞는 것을 고른다. 대상 앱에 빨간 UI가 있어도 오탐하지 않는다.
    """
    from PIL import Image

    image = Image.open(png_path).convert("RGB")
    width, height = image.size
    pixels = image.load()
    expected = CIRCLE_DP * density
    band = int(expected * 6)

    columns = sorted(set(range(0, min(band, width))) | set(range(max(0, width - band), width)))
    reds = {
        (x, y)
        for y in range(height)
        for x in columns
        if pixels[x, y][0] >= RED_MIN and pixels[x, y][1] <= GREEN_MAX and pixels[x, y][2] <= BLUE_MAX
    }
    if not reds:
        sys.exit("버블(빨간 원)을 찾지 못했다 — 플로팅 버튼이 켜져 있는지 확인하라")

    best: tuple[float, int, int] | None = None
    for component in _components(reds):
        xs = [x for x, _ in component]
        ys = [y for _, y in component]
        box_w, box_h = max(xs) - min(xs) + 1, max(ys) - min(ys) + 1
        if not _size_matches(box_w, expected) or not _size_matches(box_h, expected):
            continue
        fill = len(component) / (box_w * box_h)
        if not MIN_FILL <= fill <= MAX_FILL:
            continue
        error = abs(box_w - expected) + abs(box_h - expected)
        if best is None or error < best[0]:
            best = (error, (min(xs) + max(xs)) // 2, (min(ys) + max(ys)) // 2)

    if best is None:
        sys.exit(f"빨간 영역은 있으나 버블 크기({expected:.0f}px)의 원이 아니다")
    print(f"{best[1]} {best[2]}")


def _components(reds: set[tuple[int, int]]):
    """4-이웃으로 연결된 빨간 픽셀 덩어리들."""
    remaining = set(reds)
    while remaining:
        seed = remaining.pop()
        component = [seed]
        queue = [seed]
        while queue:
            x, y = queue.pop()
            for neighbour in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if neighbour in remaining:
                    remaining.remove(neighbour)
                    component.append(neighbour)
                    queue.append(neighbour)
        yield component


def _size_matches(value: int, expected: float) -> bool:
    return abs(value - expected) <= expected * SIZE_TOLERANCE


COMMANDS = {
    "node": lambda argv: find_node(argv[0], argv[1]),
    "any-node": lambda argv: find_any_node(argv[0], argv[1:]),
    "desc": lambda argv: find_desc(argv[0], argv[1]),
    "edit-texts": lambda argv: find_edit_texts(argv[0]),
    "bubble": lambda argv: find_bubble(argv[0], float(argv[1])),
}

if __name__ == "__main__":
    if len(sys.argv) < 3 or sys.argv[1] not in COMMANDS:
        sys.exit(f"사용법: uiauto.py [{'|'.join(COMMANDS)}] <인자...>")
    COMMANDS[sys.argv[1]](sys.argv[2:])
