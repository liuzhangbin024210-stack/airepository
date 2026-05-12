"""游戏循环、状态机与碰撞调度。"""

from __future__ import annotations

import random
from enum import Enum, auto

import pygame

from tank import config
from tank.ai import think_enemy
from tank.entities import BaseCamp, BrickWall, Bullet, Dir, Tank, Wall
from tank.map_loader import LevelData, default_level_path, load_level_file


class GameState(Enum):
    MENU = auto()
    PLAYING = auto()
    PAUSED = auto()
    GAME_OVER = auto()
    VICTORY = auto()


def _tile_center_px(col: int, row: int) -> tuple[int, int]:
    return (
        col * config.TILE_SIZE + config.TILE_SIZE // 2,
        row * config.TILE_SIZE + config.TILE_SIZE // 2,
    )


def _player_keys_to_dir(keys: pygame.key.ScancodeWrapper) -> Dir | None:
    if keys[pygame.K_w] or keys[pygame.K_UP]:
        return Dir.UP
    if keys[pygame.K_s] or keys[pygame.K_DOWN]:
        return Dir.DOWN
    if keys[pygame.K_a] or keys[pygame.K_LEFT]:
        return Dir.LEFT
    if keys[pygame.K_d] or keys[pygame.K_RIGHT]:
        return Dir.RIGHT
    return None


class Game:
    def __init__(self, screen: pygame.Surface) -> None:
        self.screen = screen
        self.clock = pygame.time.Clock()
        self.state = GameState.MENU
        self.rng = random.Random()

        pygame.font.init()
        self.font = pygame.font.SysFont("microsoftyahei,simhei,arial", 22)
        self.big_font = pygame.font.SysFont("microsoftyahei,simhei,arial", 40)

        self.world_bounds = pygame.Rect(0, 0, config.WINDOW_WIDTH, config.WINDOW_HEIGHT)

        self.level: LevelData | None = None
        self.walls: pygame.sprite.Group = pygame.sprite.Group()
        self.lakes: pygame.sprite.Group = pygame.sprite.Group()
        self.tank_obstacles: pygame.sprite.Group = pygame.sprite.Group()
        self.base: BaseCamp | None = None
        self.base_group: pygame.sprite.GroupSingle = pygame.sprite.GroupSingle()
        self.base_active = True
        self.lose_reason = ""

        self.player: Tank | None = None
        self.player_group: pygame.sprite.GroupSingle = pygame.sprite.GroupSingle()
        self.enemies: pygame.sprite.Group = pygame.sprite.Group()
        self.player_bullets: pygame.sprite.Group = pygame.sprite.Group()
        self.enemy_bullets: pygame.sprite.Group = pygame.sprite.Group()
        self.lives = 0
        self.kills = 0
        self._spawned_enemy_count = 0

    def _rebuild_tank_obstacles(self) -> None:
        self.tank_obstacles = pygame.sprite.Group()
        self.tank_obstacles.add(*self.walls.sprites())
        self.tank_obstacles.add(*self.lakes.sprites())
        if self.base is not None and self.base_active:
            self.tank_obstacles.add(self.base)

    def _try_spawn_one_enemy(self) -> bool:
        if self.level is None or not self.level.enemy_spawn_tiles:
            return False
        for _ in range(28):
            col, row = self.rng.choice(self.level.enemy_spawn_tiles)
            cx, cy = _tile_center_px(col, row)
            is_elite = self.rng.random() < config.ELITE_SPAWN_CHANCE
            max_hp = config.ELITE_HP if is_elite else 1
            spd = config.ENEMY_SPEED_PX_S * (
                config.ENEMY_ELITE_SPEED_MULT if max_hp > 1 else 1.0
            )
            t = Tank(
                cx,
                cy,
                "enemy",
                spd,
                direction=Dir(self.rng.randint(0, 3)),
                max_hp=max_hp,
            )
            if pygame.sprite.spritecollide(t, self.tank_obstacles, dokill=False):
                continue
            if pygame.sprite.spritecollide(t, self.enemies, dokill=False):
                continue
            if self.player is not None and t.rect.colliderect(self.player.rect.inflate(48, 48)):
                continue
            self.enemies.add(t)
            self._spawned_enemy_count += 1
            return True
        return False

    def _top_up_enemies(self) -> None:
        while (
            len(self.enemies) < config.MAX_ENEMIES_ALIVE
            and self._spawned_enemy_count < config.TOTAL_ENEMY_QUOTA
        ):
            if not self._try_spawn_one_enemy():
                break

    def reset_session(self) -> None:
        path = default_level_path()
        self.level = load_level_file(path)
        self.walls = self.level.walls
        self.lakes = self.level.lakes
        self.base = self.level.base
        self.base_active = True
        self.lose_reason = ""
        self.base_group = pygame.sprite.GroupSingle(self.base)
        self._rebuild_tank_obstacles()

        pc, pr = self.level.player_spawn_tile
        px, py = _tile_center_px(pc, pr)
        self.player = Tank(px, py, "player", config.PLAYER_SPEED_PX_S, Dir.UP)
        self.player_group = pygame.sprite.GroupSingle(self.player)
        self.lives = config.PLAYER_LIVES

        self.enemies = pygame.sprite.Group()
        self._spawned_enemy_count = 0
        self._top_up_enemies()

        self.player_bullets.empty()
        self.enemy_bullets.empty()
        self.kills = 0
        self.state = GameState.PLAYING

    def handle_event(self, event: pygame.event.Event) -> None:
        if event.type == pygame.QUIT:
            pygame.event.post(pygame.event.Event(pygame.QUIT))

        if event.type == pygame.KEYDOWN:
            if event.key == pygame.K_ESCAPE:
                if self.state in (
                    GameState.PLAYING,
                    GameState.PAUSED,
                    GameState.GAME_OVER,
                    GameState.VICTORY,
                ):
                    self.state = GameState.MENU
                elif self.state == GameState.MENU:
                    pygame.event.post(pygame.event.Event(pygame.QUIT))

            if self.state == GameState.MENU and event.key == pygame.K_SPACE:
                self.reset_session()

            if self.state == GameState.PLAYING and event.key == pygame.K_p:
                self.state = GameState.PAUSED

            if self.state == GameState.PAUSED and event.key == pygame.K_p:
                self.state = GameState.PLAYING

            if self.state in (GameState.GAME_OVER, GameState.VICTORY) and event.key == pygame.K_r:
                self.reset_session()

    def handle_playing_input(self, keys: pygame.key.ScancodeWrapper, dt: float) -> None:
        if self.player is None:
            return
        d = _player_keys_to_dir(keys)
        if d is not None:
            self.player.set_direction(d)
            self.player.move_along_direction(dt, self.tank_obstacles)

        if keys[pygame.K_SPACE] and self.player.can_shoot():
            if len(self.player_bullets) < config.MAX_PLAYER_BULLETS_ON_SCREEN:
                self.player_bullets.add(self.player.spawn_bullet())
                self.player.start_cooldown(config.PLAYER_SHOOT_COOLDOWN_S)

    def _update_bullets_vs_walls(self) -> None:
        for bullet in list(self.player_bullets) + list(self.enemy_bullets):
            hits = pygame.sprite.spritecollide(bullet, self.walls, dokill=False)
            if not hits:
                continue
            wall = hits[0]
            assert isinstance(wall, Wall)
            if isinstance(wall, BrickWall):
                wall.hp -= 1
                if wall.hp <= 0:
                    wall.kill()
            bullet.kill()

    def _update_bullets_vs_base(self) -> None:
        if self.base is None or not self.base_active:
            return
        for bullet in list(self.player_bullets) + list(self.enemy_bullets):
            if not bullet.rect.colliderect(self.base.rect):
                continue
            bullet.kill()
            self.base.hp -= 1
            if self.base.hp <= 0:
                self.base_active = False
                self.base.set_destroyed_look()
                if self.base in self.tank_obstacles:
                    self.tank_obstacles.remove(self.base)
            return

    def _update_bullets_vs_tanks(self) -> None:
        all_bullets = list(self.player_bullets) + list(self.enemy_bullets)
        for bullet in all_bullets:
            targets: list[Tank] = []
            if self.player is not None and self.player.alive():
                targets.append(self.player)
            targets.extend(e for e in self.enemies if e.alive())

            for tank in targets:
                if tank.team == bullet.owner_team:
                    continue
                if not bullet.rect.colliderect(tank.rect):
                    continue
                if tank.team == "player" and tank.invuln_left > 0:
                    bullet.kill()
                    break

                bullet.kill()
                if tank.team == "enemy":
                    if tank.apply_enemy_hit():
                        tank.kill()
                        self.kills += 1
                else:
                    self.lives -= 1
                    if self.lives <= 0:
                        if self.player is not None:
                            self.player.kill()
                    else:
                        assert self.level is not None
                        pc, pr = self.level.player_spawn_tile
                        px, py = _tile_center_px(pc, pr)
                        tank.respawn_at(px, py)
                break

    def update(self, dt: float) -> None:
        if self.state != GameState.PLAYING:
            return
        assert self.player is not None

        self.player.tick_cooldown(dt)

        keys = pygame.key.get_pressed()
        self.handle_playing_input(keys, dt)

        base_rect = self.base.rect if (self.base is not None and self.base_active) else None
        for enemy in self.enemies:
            enemy.tick_cooldown(dt)
            want_shoot = think_enemy(enemy, dt, self.player.rect, self.rng, base_rect)
            enemy.move_along_direction(dt, self.tank_obstacles)
            if want_shoot and enemy.can_shoot():
                self.enemy_bullets.add(enemy.spawn_bullet())
                cd = (
                    config.ENEMY_SHOOT_COOLDOWN_S * 0.88
                    if enemy.is_elite
                    else config.ENEMY_SHOOT_COOLDOWN_S
                )
                enemy.start_cooldown(cd)

        for group in (self.player_bullets, self.enemy_bullets):
            for b in group:
                assert isinstance(b, Bullet)
                b.update(dt, self.world_bounds)

        self._update_bullets_vs_walls()
        self._update_bullets_vs_base()
        self._update_bullets_vs_tanks()
        self._top_up_enemies()

        if not self.base_active:
            self.state = GameState.GAME_OVER
            self.lose_reason = "base"
        elif self.player is None or not self.player.alive():
            self.state = GameState.GAME_OVER
            self.lose_reason = "player"
        elif self.kills >= config.TOTAL_ENEMY_QUOTA:
            self.state = GameState.VICTORY

    def draw(self) -> None:
        self.screen.fill(config.COLOR_BG)
        if self.level is not None:
            pygame.draw.rect(
                self.screen,
                (10, 14, 10),
                pygame.Rect(0, 0, self.level.width_px, self.level.height_px),
                width=0,
            )

        self.walls.draw(self.screen)
        self.lakes.draw(self.screen)
        self.base_group.draw(self.screen)
        self.player_bullets.draw(self.screen)
        self.enemy_bullets.draw(self.screen)
        if self.player is not None and self.player.alive():
            blink = int(pygame.time.get_ticks() / 120) % 2 == 0
            if self.player.invuln_left <= 0 or blink:
                self.screen.blit(self.player.image, self.player.rect)
        self.enemies.draw(self.screen)

        quota = config.TOTAL_ENEMY_QUOTA
        alive_e = len(self.enemies)
        if self.base is not None:
            base_hud = "完好" if self.base_active else "已毁"
        else:
            base_hud = "—"
        line1 = self.font.render(
            f"生命 {max(self.lives, 0)}   敌军 {self.kills}/{quota}   场上 {alive_e}   基地 {base_hud}",
            True,
            config.COLOR_HUD_TEXT,
        )
        line2 = self.font.render("WASD/方向 移动 · 空格射击 · P暂停 · ESC菜单", True, config.COLOR_HUD_TEXT)
        self.screen.blit(line1, (8, 4))
        self.screen.blit(line2, (8, 28))

        if self.state == GameState.MENU:
            self._draw_overlay(
                "坦克大战",
                ["空格 — 开始游戏", "ESC — 退出", "保卫基地 H，消灭全部敌军"],
            )
        elif self.state == GameState.PAUSED:
            self._draw_overlay("已暂停", ["P — 继续", "ESC — 返回菜单"])
        elif self.state == GameState.GAME_OVER:
            title = "基地被毁" if self.lose_reason == "base" else "游戏结束"
            self._draw_overlay(title, ["R — 重新开始", "ESC — 菜单"])
        elif self.state == GameState.VICTORY:
            self._draw_overlay("胜利！", ["基地安全，敌军已歼灭", "R — 再玩一局", "ESC — 菜单"])

        pygame.display.flip()

    def _draw_overlay(self, title: str, lines: list[str]) -> None:
        w, h = self.screen.get_size()
        dim = pygame.Surface((w, h), pygame.SRCALPHA)
        dim.fill((0, 0, 0, 160))
        self.screen.blit(dim, (0, 0))

        y = h // 2 - 80
        t = self.big_font.render(title, True, config.COLOR_MENU_TITLE)
        self.screen.blit(t, t.get_rect(center=(w // 2, y)))
        y += 70
        for line in lines:
            s = self.font.render(line, True, config.COLOR_HUD_TEXT)
            self.screen.blit(s, s.get_rect(center=(w // 2, y)))
            y += 36


def main() -> None:
    pygame.init()
    try:
        pygame.display.set_caption(config.TITLE)
        screen = pygame.display.set_mode((config.WINDOW_WIDTH, config.WINDOW_HEIGHT))
        game = Game(screen)
        running = True
        while running:
            dt = game.clock.tick(config.FPS) / 1000.0
            for event in pygame.event.get():
                if event.type == pygame.QUIT:
                    running = False
                else:
                    game.handle_event(event)
            game.update(dt)
            game.draw()
    finally:
        pygame.quit()
