# picture/ 素材与 ROI 标定（关键路径 0）

修订计划要求：在逐张全屏截图上完成 **ROI 与场景标注**，并回填 `AppProfile` 与 `docs/画面字段-GameState-映射表.md`。

## 素材规格（当前入库）

| 属性 | 值 |
|------|-----|
| 分辨率 | **2556 × 1179**（横屏） |
| 格式 | PNG |
| 目标 App | 画面中央 **「血战麻将」**，房号示例 **796240** |

## 帧清单（`1.png` … `13.png`）

| 文件 | 场景摘要 | 视觉 / 记牌注意 |
|------|-----------|----------------|
| 1.png | 换三张；等待其他玩家选牌 | `GamePhase=EXCHANGE_THREE`，**勿**将挑出 3 张计为河牌 |
| 2.png | 对局中；HUD 剩 55 张、第 1/8 局 | 基准布局 |
| 3.png | 对局中；多家弃牌靠近中央 | 四 `river_*` 与中央区注意 **去重**（见映射表） |
| 4.png | 对局中；**中央横向弃牌堆**明显 | 主从规则：河牌 multiset 以四向 ROI 为准，中央堆叠勿双计 |
| 5.png | **结算 / 胜利**界面 | 非对局；**关闭**自动写河与差分 |
| 6.png | 对局中；侧家明刻、箭头指示最近打出 | `CLAIM`/箭头可作 `center_last_discard` 辅助 |
| 7.png | 对局中；大号 **「碰」** 按钮 | `GamePhase=CLAIM_WINDOW` 级语义 |
| 8.png | 换三张；多侧「已选牌」 | 同 1.png 门禁 |
| 9.png | 对局中；明杠、摸牌浮张 | 手牌区含副露时勿与河混淆 |
| 10.png | 对局中；四家定缺色块齐全 | 定缺 OCR/模板标定参考帧 |
| 11.png | 对局中；左侧双头像（血战已胡等） | `wonSeats` / 存活集与画面对齐 |
| 12.png | 对局中；多区弃牌 | 去重 / 守恒校验参考 |
| 13.png | 胡提示；中央多枚弃牌 + 多副露 | 中央展示丰富，**严禁**简单像素差分把整堆当单次出牌 |

## 标定产出

在图外用任意工具框选区域，记录 **归一化坐标**（相对整屏 0..1，与 `NormRect` 一致）。代码侧 **`AppProfile.xuezhanDefault()`** 已写入与上表对齐的 **初版** 几何；若你机位/游戏缩放不同，请用 **相机页标定** 写入 Room（`RoiConfigEntity.rectsJson`，JSON v2）覆盖。

## 金帧回归（自动化）

仪器测试 `com.majiang.counter.vision.GoldenFrameRiverDiffAndroidTest` 直接打包本目录 PNG（见 `app/build.gradle.kts` `androidTest.assets`）。运行方式与扩展约定见仓库根目录 **`docs/golden-frames.md`**。
