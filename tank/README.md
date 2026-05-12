# 坦克大战（Python / Pygame）

单机 2D 坦克对战小游戏，运行在固定窗口内。

## 环境

- Python 3.10 及以上
- 依赖见 `requirements.txt`（主依赖为 Pygame 2.x）

## 安装

在项目根目录 `tank/`（包含本 README 与内层包目录 `tank/` 的那一层）执行：

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

（Linux / macOS 使用 `source .venv/bin/activate`。）

## 运行

**必须在项目根目录执行**（使 Python 能解析包名 `tank`）：

```bash
python -m tank
```

## 操作

- **移动**：WASD 或方向键（同时按下时按检测顺序优先上下再左右）
- **射击**：空格（有冷却；同屏玩家子弹数量上限见 `tank/config.py`）
- **暂停**：P
- **菜单 / 退出**：游戏、暂停、胜利或失败界面下 ESC 返回菜单；在菜单下 ESC 退出程序
- **重新开始**：游戏结束或胜利画面按 R

## 关卡编辑

编辑 `tank/assets/levels/level01.txt`：

| 字符 | 含义   |
|------|--------|
| `.` 或空格 | 空地 |
| `#`  | 砖墙（可被子弹摧毁） |
| `@`  | 钢墙 |
| `~`  | 湖泊：坦克不可进入，子弹可飞过 |
| `H`  | 基地（**唯一**，建议放在**最底部**并用 `#` 围一圈保护）：被任意一方子弹击中即摧毁，本局失败 |
| `P`  | 玩家出生点（唯一）；守家关卡建议放在基地**旁边** |
| `E`  | 敌方出生点（建议放在地图**上方**）；总数与场上上限见 `config.py` 的 `TOTAL_ENEMY_QUOTA`、`MAX_ENEMIES_ALIVE` |

敌军生成时会有 **`ELITE_SPAWN_CHANCE`** 概率出现**精英**（紫色描边、3 格血条），需 **`ELITE_HP`** 发玩家子弹才能击毁；精英略快、射击间隔略短。参数见 `tank/config.py`。

## 项目结构

详见 `docs/DEVELOPMENT_PLAN.md` 与 `docs/REQUIREMENTS.md`。
