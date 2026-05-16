# 录屏 / 截图金帧回归

与修订计划 **§风险与验收标准 → 金帧或录屏回归集** 对齐的**自动化子集**：在仪器测试（需真机或模拟器）中加载仓库根目录 `picture/` 下的 PNG，对 `RiverDiffTableTracker` 做回归断言。

## 覆盖范围（当前）

| 资产文件 | 断言意图 |
|----------|----------|
| `5.png` | 结算类画面：**不得**产出弃牌事件（结算启发式 + 无差分误触发） |
| `2.png` | 对局中基准帧：同一帧连续处理两次，河 ROI 无变化 → **不得**产出弃牌事件 |

更细的「期望河牌增量」「换三张/碰杠窗禁写河」等仍以 `picture/README.md` 帧清单与人工标注为主；后续可在本目录扩展用例表与 JSON 期望，再接到同一测试类或独立脚本。

## 如何运行

1. 确保本机已克隆且 `picture/2.png`、`picture/5.png` 存在（与 `picture/README.md` 一致）。
2. 连接设备或启动模拟器。
3. 在项目根执行：

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.majiang.counter.vision.GoldenFrameRiverDiffAndroidTest"
```

Windows（PowerShell）：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --tests "com.majiang.counter.vision.GoldenFrameRiverDiffAndroidTest"
```

说明：`app/build.gradle.kts` 已将 `picture/` 合并进 `androidTest` 的 assets，无需再复制 PNG 到 `src/androidTest/assets`。

## 与产品功能的关系

- **不替代**手录 / 相机真机标定；用于防止 ROI、差分阈值、结算启发式等改动导致「误写河」类退化。
- 牌面分类使用测试内 **固定假分类器**，不依赖 `tileClassifierFile` 是否打包。
