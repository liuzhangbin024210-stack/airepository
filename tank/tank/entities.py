"""坦克、子弹、墙体精灵。"""

from __future__ import annotations

from enum import IntEnum

import pygame

from tank import config


class Dir(IntEnum):
    UP = 0
    RIGHT = 1
    DOWN = 2
    LEFT = 3


def dir_to_vec(d: Dir) -> tuple[float, float]:
    if d == Dir.UP:
        return 0.0, -1.0
    if d == Dir.DOWN:
        return 0.0, 1.0
    if d == Dir.LEFT:
        return -1.0, 0.0
    return 1.0, 0.0


def snap_tank_to_grid(rect: pygame.Rect) -> None:
    """轻微对齐到 2 像素网格，减少长时间漂移导致的缝隙问题。"""
    rect.x = int(rect.x // 2) * 2
    rect.y = int(rect.y // 2) * 2


class Wall(pygame.sprite.Sprite):
    def __init__(self, x: int, y: int, color: tuple[int, int, int], destructible: bool) -> None:
        super().__init__()
        self.destructible = destructible
        self.image = pygame.Surface((config.TILE_SIZE, config.TILE_SIZE))
        self.image.fill(color)
        pygame.draw.rect(self.image, (0, 0, 0), self.image.get_rect(), width=1)
        self.rect = self.image.get_rect(topleft=(x, y))


class BrickWall(Wall):
    def __init__(self, x: int, y: int, hp: int = 1) -> None:
        super().__init__(x, y, config.COLOR_BRICK, destructible=True)
        self.hp = hp


class SteelWall(Wall):
    def __init__(self, x: int, y: int) -> None:
        super().__init__(x, y, config.COLOR_STEEL, destructible=False)


class Lake(pygame.sprite.Sprite):
    """湖泊：坦克不可进入，子弹可飞过（不加入墙体碰撞组）。"""

    def __init__(self, x: int, y: int) -> None:
        super().__init__()
        self.image = pygame.Surface((config.TILE_SIZE, config.TILE_SIZE))
        self.image.fill(config.COLOR_LAKE)
        r = self.image.get_rect()
        pygame.draw.ellipse(self.image, config.COLOR_LAKE_SHINE, r.inflate(-8, -12))
        pygame.draw.rect(self.image, (25, 70, 110), r, width=1)
        self.rect = self.image.get_rect(topleft=(x, y))


class BaseCamp(pygame.sprite.Sprite):
    """基地：被任意子弹击中则摧毁（游戏逻辑在 game 中判负）。"""

    def __init__(self, x: int, y: int) -> None:
        super().__init__()
        self.hp = config.BASE_HP
        self.image = pygame.Surface((config.TILE_SIZE, config.TILE_SIZE), pygame.SRCALPHA)
        self._redraw()
        self.rect = self.image.get_rect(topleft=(x, y))

    def _redraw(self) -> None:
        self.image.fill((0, 0, 0, 0))
        r = pygame.Rect(0, 0, config.TILE_SIZE, config.TILE_SIZE)
        pygame.draw.rect(self.image, config.COLOR_BASE_FILL, r, border_radius=2)
        pygame.draw.rect(self.image, config.COLOR_BASE_ACCENT, r, width=3, border_radius=2)
        cx, cy = r.center
        pygame.draw.polygon(
            self.image,
            (240, 220, 100),
            [(cx, cy - 10), (cx - 10, cy + 8), (cx + 10, cy + 8)],
        )

    def set_destroyed_look(self) -> None:
        self.image.fill((0, 0, 0, 0))
        r = pygame.Rect(0, 0, config.TILE_SIZE, config.TILE_SIZE)
        pygame.draw.rect(self.image, config.COLOR_BASE_DESTROYED, r, border_radius=2)
        pygame.draw.line(self.image, (30, 30, 30), r.topleft, r.bottomright, width=3)
        pygame.draw.line(self.image, (30, 30, 30), r.topright, r.bottomleft, width=3)


class Bullet(pygame.sprite.Sprite):
    def __init__(self, x: float, y: float, direction: Dir, owner_team: str) -> None:
        super().__init__()
        self.direction = direction
        self.owner_team = owner_team
        color = (
            config.COLOR_BULLET_PLAYER if owner_team == "player" else config.COLOR_BULLET_ENEMY
        )
        self.image = pygame.Surface((config.BULLET_SIZE, config.BULLET_SIZE))
        self.image.fill(color)
        self.rect = self.image.get_rect(center=(int(x), int(y)))
        self._vx, self._vy = dir_to_vec(direction)
        spd = config.BULLET_SPEED_PX_S
        self._vx *= spd
        self._vy *= spd

    def update(self, dt: float, bounds: pygame.Rect) -> None:
        self.rect.x += int(round(self._vx * dt))
        self.rect.y += int(round(self._vy * dt))
        if not bounds.colliderect(self.rect):
            self.kill()


class Tank(pygame.sprite.Sprite):
    def __init__(
        self,
        center_x: int,
        center_y: int,
        team: str,
        speed_px_s: float,
        direction: Dir = Dir.UP,
        *,
        max_hp: int = 1,
    ) -> None:
        super().__init__()
        self.team = team
        self.direction = direction
        self.speed_px_s = speed_px_s
        self.shoot_cooldown = 0.0
        self.max_hp = max(1, max_hp)
        self.hp = self.max_hp
        self.is_elite = team == "enemy" and self.max_hp > 1
        if team == "player":
            color = config.COLOR_PLAYER
        elif self.is_elite:
            color = config.COLOR_ENEMY_ELITE
        else:
            color = config.COLOR_ENEMY
        self.image = pygame.Surface((config.TANK_SIZE, config.TANK_SIZE), pygame.SRCALPHA)
        self._base_color = color
        self._draw_body()
        self.rect = self.image.get_rect(center=(center_x, center_y))
        self.ai_think_timer = 0.0
        self.invuln_left = 0.0

    def _draw_body(self, blink_invuln: bool = False) -> None:
        self.image.fill((0, 0, 0, 0))
        body = pygame.Rect(0, 0, config.TANK_SIZE, config.TANK_SIZE)
        color = self._base_color
        if blink_invuln and self.invuln_left > 0:
            color = tuple(min(255, c + 60) for c in color)  # type: ignore[assignment]
        pygame.draw.rect(self.image, color, body, border_radius=4)
        if self.is_elite:
            pygame.draw.rect(
                self.image, config.COLOR_ENEMY_ELITE_TRIM, body, width=2, border_radius=4
            )
            # 血量刻度（精英）
            for i in range(self.max_hp):
                bx = 6 + i * 8
                filled = i < self.hp
                pygame.draw.rect(
                    self.image,
                    (120, 255, 120) if filled else (60, 60, 60),
                    pygame.Rect(bx, 4, 6, 4),
                    border_radius=1,
                )
        # 炮管方向
        cx, cy = body.centerx, body.centery
        vx, vy = dir_to_vec(self.direction)
        gun_len = config.TANK_SIZE // 2
        ex = int(cx + vx * gun_len)
        ey = int(cy + vy * gun_len)
        pygame.draw.line(self.image, (40, 40, 40), (cx, cy), (ex, ey), width=4)

    def set_direction(self, d: Dir) -> None:
        self.direction = d
        self._draw_body()

    def try_move(self, dx: float, dy: float, obstacles: pygame.sprite.Group) -> None:
        if dx == 0 and dy == 0:
            return
        old = self.rect.copy()

        self.rect.x += int(round(dx))
        self.rect.y += int(round(dy))
        if not pygame.sprite.spritecollide(self, obstacles, dokill=False):
            snap_tank_to_grid(self.rect)
            return
        self.rect = old

        self.rect.x += int(round(dx))
        if not pygame.sprite.spritecollide(self, obstacles, dokill=False):
            snap_tank_to_grid(self.rect)
            return
        self.rect = old

        self.rect.y += int(round(dy))
        if not pygame.sprite.spritecollide(self, obstacles, dokill=False):
            snap_tank_to_grid(self.rect)
            return
        self.rect = old

    def move_along_direction(self, dt: float, obstacles: pygame.sprite.Group) -> None:
        vx, vy = dir_to_vec(self.direction)
        dist = self.speed_px_s * dt
        self.try_move(vx * dist, vy * dist, obstacles)

    def face_point(self, tx: float, ty: float) -> None:
        dx = tx - self.rect.centerx
        dy = ty - self.rect.centery
        if dx == 0 and dy == 0:
            return
        if abs(dx) >= abs(dy):
            self.set_direction(Dir.RIGHT if dx > 0 else Dir.LEFT)
        else:
            self.set_direction(Dir.DOWN if dy > 0 else Dir.UP)

    def can_shoot(self) -> bool:
        return self.shoot_cooldown <= 0

    def start_cooldown(self, duration: float) -> None:
        self.shoot_cooldown = duration

    def tick_cooldown(self, dt: float) -> None:
        self.shoot_cooldown = max(0.0, self.shoot_cooldown - dt)
        self.invuln_left = max(0.0, self.invuln_left - dt)

    def spawn_bullet(self) -> Bullet:
        vx, vy = dir_to_vec(self.direction)
        margin = config.TANK_SIZE // 2 + config.BULLET_SIZE
        bx = self.rect.centerx + vx * margin
        by = self.rect.centery + vy * margin
        return Bullet(bx, by, self.direction, self.team)

    def respawn_at(self, cx: int, cy: int) -> None:
        self.rect.center = (cx, cy)
        self.invuln_left = config.PLAYER_RESPAWN_INVULN_S
        self._draw_body()

    def apply_enemy_hit(self) -> bool:
        """敌方坦克受击。返回 True 表示已被击毁。"""
        if self.team != "enemy":
            return False
        self.hp -= 1
        if self.hp <= 0:
            return True
        self._draw_body()
        return False
