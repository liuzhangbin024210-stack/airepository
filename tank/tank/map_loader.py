"""从字符矩阵加载关卡：墙体、湖泊、基地与出生点。"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import pygame

from tank import config


# 字符约定
CHAR_EMPTY = {".", " ", "\t"}
CHAR_BRICK = "#"
CHAR_STEEL = "@"
CHAR_PLAYER = "P"
CHAR_ENEMY = "E"
CHAR_LAKE = "~"
CHAR_BASE = "H"


@dataclass(frozen=True)
class LevelData:
    width_px: int
    height_px: int
    walls: pygame.sprite.Group
    lakes: pygame.sprite.Group
    base: "BaseCamp | None"
    player_spawn_tile: tuple[int, int]
    enemy_spawn_tiles: list[tuple[int, int]]


def _tile_top_left(col: int, row: int) -> tuple[int, int]:
    return col * config.TILE_SIZE, row * config.TILE_SIZE


def load_level_from_lines(lines: list[str]) -> LevelData:
    """解析已去尾换行、等宽的行列表。"""
    if not lines:
        raise ValueError("空关卡")

    rows = len(lines)
    cols = max(len(line) for line in lines)
    walls = pygame.sprite.Group()
    lakes = pygame.sprite.Group()
    player_tile: tuple[int, int] | None = None
    enemy_tiles: list[tuple[int, int]] = []
    base_obj: "BaseCamp | None" = None

    for row, line in enumerate(lines):
        for col in range(cols):
            ch = line[col] if col < len(line) else "."
            if ch in CHAR_EMPTY:
                continue
            if ch == CHAR_BRICK:
                from tank.entities import BrickWall

                x, y = _tile_top_left(col, row)
                walls.add(BrickWall(x, y, hp=config.BRICK_HP))
            elif ch == CHAR_STEEL:
                from tank.entities import SteelWall

                x, y = _tile_top_left(col, row)
                walls.add(SteelWall(x, y))
            elif ch == CHAR_LAKE:
                from tank.entities import Lake

                x, y = _tile_top_left(col, row)
                lakes.add(Lake(x, y))
            elif ch == CHAR_BASE:
                from tank.entities import BaseCamp

                if base_obj is not None:
                    raise ValueError("关卡中只能有一个基地标记 H")
                x, y = _tile_top_left(col, row)
                base_obj = BaseCamp(x, y)
            elif ch == CHAR_PLAYER:
                if player_tile is not None:
                    raise ValueError("关卡中只能有一个 P")
                player_tile = (col, row)
            elif ch == CHAR_ENEMY:
                enemy_tiles.append((col, row))
            else:
                raise ValueError(f"未知地图字符: {ch!r} at ({row},{col})")

    if player_tile is None:
        raise ValueError("关卡缺少玩家出生点 P")
    if base_obj is None:
        raise ValueError("关卡缺少基地 H（被摧毁则游戏失败）")

    width_px = cols * config.TILE_SIZE
    height_px = rows * config.TILE_SIZE
    return LevelData(
        width_px=width_px,
        height_px=height_px,
        walls=walls,
        lakes=lakes,
        base=base_obj,
        player_spawn_tile=player_tile,
        enemy_spawn_tiles=enemy_tiles,
    )


def load_level_file(path: Path) -> LevelData:
    text = path.read_text(encoding="utf-8")
    lines = [line.rstrip("\n") for line in text.splitlines() if line.strip() != ""]
    return load_level_from_lines(lines)


def default_level_path() -> Path:
    return Path(__file__).resolve().parent / "assets" / "levels" / "level01.txt"
