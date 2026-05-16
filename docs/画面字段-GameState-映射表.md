# 《画面字段 → GameState》映射表（首款：血战麻将 / `xuezhan_mahjong_default`）

依据修订计划与 **`picture/1.png`–`13.png`（2556×1179）** 读图结论整理；**归一化坐标**为横屏、本家在下。代码初值见 [`AppProfile.xuezhanDefault()`](../app/src/main/java/com/majiang/counter/profile/AppProfile.kt)；相机预览比例或皮肤更新后请以 **相机页标定**（Room JSON v2）覆盖。

## 素材与坐标系

| 项目 | 说明 |
|------|------|
| 参考分辨率 | **2556 × 1179**（当前入库 PNG 一致） |
| 坐标 | 相对整屏 **左 0 → 右 1**、**上 0 → 下 1**；与 `NormRect(left,top,right,bottom)` 一致 |
| 本家方位 | 画面 **下方** = `Seat.SOUTH` |

## 持久化格式（JSON v2）

相机页标定结果由 [RoiCalibrationCodec](../app/src/main/java/com/majiang/counter/profile/VisualCalibration.kt) 序列化：`version=2`，`rivers` 下按 `EAST`/`SOUTH`/`WEST`/`NORTH` 存四数组，`extras` 下存 `hand_bottom`、`hud` 等与下表 **区域 ID** 一致的键。兼容旧版根对象仅含四座牌河的 JSON。

| 区域 ID | UI 描述 | `GameState` / 配置字段 | 识别方式 | 失败时人工校正 |
|---------|---------|------------------------|----------|----------------|
| `river_bottom` | 本家前方牌河 | `discards[SOUTH]`（本家在下映射见 `GameViewModel`） | ROI 差分 + 分类 | 记牌页点选补牌 / 撤销 |
| `river_right` | 右侧家牌河 | `discards[WEST]` | 同上 | 同上 |
| `river_top` | 对家牌河 | `discards[NORTH]` | 同上 | 同上 |
| `river_left` | 左侧家牌河 | `discards[EAST]` | 同上 | 同上 |
| `hand_bottom` | 本家手牌 | `myHand` | 分类 / 手录 | 分析页手牌录入 |
| `hand_count_*` | 对手张数 UI | `opponentHandCount` | OCR（可选） | 可空；**若三家手数与 HUD「剩张」均已录入**则参与牌墙守恒对账（见 `GameStateReconcile` / `GameViewModel.processCameraFrame`） |
| `melds_*` | 明碰/明杠 | `openMelds[seat]` | 低频 ROI + 分类 | 手录扩展 v1 简化为仅手牌+河 |
| `dingque_*` | 定缺色块+字 | `dingQue[seat]` | 模板/OCR | 设置页定缺选择 |
| `dealer_badge` | 「庄」标 | `dealerSeat` | 模板 | 手选庄家 |
| `hud` | 「剩 NN 张」「第 x/y 局」、标题 | `hudRemainingTiles`（可选） | **ML Kit OCR**（相机页开关）或手录 | 不参与则留空 |
| `center_last_discard` | 中央最近打出 / 箭头指示 / 碰杠浮层 | **辅助**；见下节「主从与去重」 | 状态机 + 差分 / OCR | 映射表注明主从 |

**`GamePhase`**：`EXCHANGE_THREE` 时禁止将右侧挑出 3 张计为河牌；`CLAIM_WINDOW` 记录上一打出座位供规则引擎使用。

**座位与画面**：默认 **本家 = `Seat.SOUTH`**（屏幕下方），逆时针 EAST（左）、NORTH（上）、WEST（右）。

## 中央弃牌区与四向牌河：主从与去重（本款 UI 必读）

读图结论（尤其 `4.png`、`12.png`、`13.png`）：

1. **主数据源**：各家正式弃牌序列仍以 **四向 `river_*` ROI** 内出现的牌面为准，写入 `discards[seat]`。
2. **中央区域**：部分局面在桌面 **几何中心附近** 另有多枚弃牌或集堆展示，可能与某一侧的 `river_*` **空间重叠**或 **重复展示**同一批牌。
3. **禁止行为**：不得把中央整堆牌面 **一次性** 当作「单次出牌」写入河牌；不得以中央 ROI 单独累加一副「全局河」再与四向河相加（会双计）。
4. **推荐策略（v1）**：
   - 差分检测新牌时，以 **单侧 `river_*` 变化** 为主触发；
   - `center_last_discard` 仅用于 **最近打出高亮 / 碰杠窗** 的辅助确认与 **去重校验**（例如校验中央高亮张与刚检测到的新弃牌是否一致）；
   - `GamePhase != PLAYING` 时关闭向 `GameState` 的自动河牌写入。
5. **结算帧**（如 `5.png`）：非对局画面，**禁止**自动写河。

## `AppProfile` 归一化初值（与代码一致）

与 [`AppProfile.xuezhanDefault()`](../app/src/main/java/com/majiang/counter/profile/AppProfile.kt) 及 [`RoiCalibrationPack.defaultFromProfile`](../app/src/main/java/com/majiang/counter/profile/VisualCalibration.kt) 一致：

| 分组 | 键 / 顺序 | 归一化矩形（left, top, right, bottom） | 说明 |
|------|-----------|----------------------------------------|------|
| 四牌河 | EAST, SOUTH, WEST, NORTH | 0.00–0.20×0.22–0.78；0.12–0.88×0.58–0.81；0.80–1.00×0.22–0.78；0.15–0.85×0.03–0.20 | 左列加宽以覆盖「偏中央」的左侧弃牌；本家河与手牌区分横带 |
| HUD | `hudRect` 与 `extras.hud` | 0.28–0.72×0.28–0.54 | 「剩」「第」「血战麻将」等 |
| `hand_bottom` | | 0.05–0.95×0.825–0.995 | 本家立牌 / 换三张时含挑出分组 |
| `center_last_discard` | | 0.42–0.58×0.38–0.52 | 计时环周边辅助区（勿当整桌弃牌堆） |
| `hand_count_*`（东/北/西） | | 0.02–0.14×0.32–0.50；0.40–0.60×0.02–0.12；0.86–0.98×0.32–0.50 | 对手背面张数 OCR 示意 |
| `dealer_badge` | | 0.04–0.24×0.06–0.20 | **左上** 头像邻域示意；庄家位随局变化，精细识别宜后续按座位拆多 ROI |

**牌面模型路径**：`assets/ml/{appId}/` + `model_manifest.json` 的 `tileClassifierFile`；见 `tools/ml/README.md`「牌面分类」。

## 逐张截图 ROI / 场景记录（`picture/`）

**说明**：下列各帧 **共用** 上表「`AppProfile` 初值」一套 ROI；未逐帧单独改矩形（本批截图布局一致）。若个别皮肤或版本位移，在对应帧旁记差异并更新标定 JSON。

| 文件名 | 分辨率 | 场景 | 与默认 ROI 关系 / 备注 |
|--------|--------|------|------------------------|
| 1.png | 2556×1179 | 换三张、等待他家 | `EXCHANGE_THREE`；禁写河 |
| 2.png | 2556×1179 | 对局中 | 基准；HUD「剩 55」 |
| 3.png | 2556×1179 | 对局中 | 弃牌近中央，依赖 EAST/SOUTH 加宽 |
| 4.png | 2556×1179 | 对局中 + **中央弃牌横排** | **去重**重点样张 |
| 5.png | 2556×1179 | **结算** | 非 `PLAYING`；禁用视觉写河 |
| 6.png | 2556×1179 | 对局中 | 明刻、箭头指示 |
| 7.png | 2556×1179 | **碰** 响应 | `CLAIM_WINDOW` |
| 8.png | 2556×1179 | 换三张 | 同 1.png |
| 9.png | 2556×1179 | 对局中 | 明杠、浮张摸牌 |
| 10.png | 2556×1179 | 对局中 | 四家定缺可见 |
| 11.png | 2556×1179 | 对局中 | 左侧双头像（血战状态） |
| 12.png | 2556×1179 | 对局中 | 多区弃牌，去重参考 |
| 13.png | 2556×1179 | 胡提示、中央多牌 | **禁**把中央整堆当单次出牌 |
