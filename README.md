# 四川麻将记牌器（血战到底 · 骨架）

面向 **个人练习** 的 Android 应用：在对局页 **打开相机** 识别牌桌公开信息，点 **开始分析** 后在 **四川血战子集规则** 下给出听牌与打法建议（v1 口径与修订计划一致）。

## 构建

1. 安装 [Android Studio](https://developer.android.com/studio) 与 **Android SDK**。
2. 在项目根目录创建 `local.properties`，写入一行（路径按本机修改）：
   ```properties
   sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk
   ```
3. 用 Android Studio 打开本仓库，同步 Gradle 后运行 `app`。

## 功能（当前最小可用）

- **用户登录**：本地账号（用户名 + 密码），Room 存用户、DataStore 存当前会话。**内置管理员**用户名为 `admin`，首次安装默认密码为 `admin123`（请在设置中 **修改密码**）。**Debug 构建**默认开启 `AUTO_ADMIN_LOGIN`：无会话时启动即登录管理员，便于测试；**Release** 默认关闭，需手动登录。**仅管理员**可在设置中 **添加用户**；任意已登录用户可在设置中 **修改密码**。无短信、邮箱等其它验证方式。
- **对局页**：先 **打开相机** 对准牌桌，再 **开始分析**；自动记录四家舍牌，分析前识别本家手牌（须配置牌面识别模型）。
- **打法建议**：听牌、进张倾向、建议打出与点炮/点杠风险（引擎层与 `SituationAnalyzer` 一致）。
- **设置页**：画面区域标定（四家舍牌区、本家手牌区等）；可选自动识别牌墙剩张。
- **视觉管线**：四河差分 → 牌面分类（manifest 可选 TFLite）→ 置信度门禁写舍牌；牌墙剩张 OCR 可与推算对账。
- **规则**：`SichuanRulesEngine`（定缺、无吃、胡/听、一炮多响 `ronSeats`、`RulesConfig.allowMultiRon` 默认 `true`）。
- **蒸馏 / 策略 TFLite**：`PolicyFeatureV1`（81 维）+ manifest `rulesHash`；模型输出 **27** 时仅听牌学生，**81** 时额外含打出 RonAny/点杠；无 `policy-v1.tflite` 或校验失败则全走 MC；见 `tools/ml/README.md`（含 `train_policy_v1.py` 脚手架）。
- **牌面分类 TFLite**：默认文件名 `tiles-v1.tflite`，与 `model_manifest.json` 中 `tileClassifierFile` 一致。本地训练见 `tools/ml/README.md` 或 Windows 脚本 `tools/ml/run_train_tiles.ps1`。**推荐用 GitHub Actions 下载制品**：步骤见 **`docs/CI-tiles-v1-github.md`**（Actions → **train-tiles-tflite** → 下载 **tiles-v1-tflite** → 解压后放入 `app/src/main/assets/ml/xuezhan_mahjong_default/`）。

## 规则子集（v1，与 `RulesConfig` 一致）

**当前包含**：108 张（万筒条）、定缺约束下的听/胡/合法打出、无吃、明杠点杠判定、**一炮多响**（`ronSeats` 返回多家；`RonAny` = ∃ 至少一家能和）、已胡座位在引擎层 **`inactiveSeats`** 中不参与荣和/明杠（与血战离场一致；`GamePhase` 在 UI/视觉门禁侧使用）。

**默认不包含（开关 false，未与画面双重确认前不启用）**：查花猪、查大叫、牌墙末张必胡等（见 `RulesConfig` 字段说明）。

**桌规提示**：多家同时可胡/碰/杠时，产品层与常见 App 一致采用「胡优先于碰杠」；引擎分别输出荣和家集与可明杠家集，由上层合并。

## 文档

- 修订计划：`.cursor/plans/四川麻将记牌器计划修订_541f9a42.plan.md`
- **GitHub 获取牌面模型**：`docs/CI-tiles-v1-github.md`（Actions 下载 `tiles-v1.tflite`）
- 画面字段映射：`docs/画面字段-GameState-映射表.md`
- 截图帧清单：`picture/README.md`
- 验收摘要：`docs/ACCEPTANCE-IMPLEMENTATION.md`

## 许可与免责

本工具不读取游戏服务器；分析基于相机识别到的公开牌桌信息。使用后果自负。
