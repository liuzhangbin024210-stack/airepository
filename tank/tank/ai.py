"""敌方坦克简单 AI：间歇思考、朝向玩家/基地或随机转向、概率射击。"""

from __future__ import annotations

import random

import pygame

from tank import config
from tank.entities import Dir, Tank


def think_enemy(
    enemy: Tank,
    dt: float,
    player_rect: pygame.Rect,
    rng: random.Random,
    base_rect: pygame.Rect | None = None,
) -> bool:
    """
    更新敌方思考计时；到期时选择新朝向。
    返回 True 表示本帧希望尝试开火（仍由游戏层检查冷却与子弹上限）。
    """
    enemy.ai_think_timer -= dt
    if enemy.ai_think_timer > 0:
        return False

    enemy.ai_think_timer = rng.uniform(config.ENEMY_THINK_MIN_S, config.ENEMY_THINK_MAX_S)

    if base_rect is not None and rng.random() < config.ENEMY_BIAS_BASE_CHANCE:
        enemy.face_point(float(base_rect.centerx), float(base_rect.centery))
    elif rng.random() < 0.58:
        enemy.face_point(float(player_rect.centerx), float(player_rect.centery))
    else:
        enemy.set_direction(Dir(rng.randint(0, 3)))

    return rng.random() < config.ENEMY_SHOOT_CHANCE_PER_THINK
