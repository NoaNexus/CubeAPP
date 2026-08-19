# 方迹 CubeTrace

完全离线的 Android 3×3 CFOP 魔方工具：公式、识别训练、手动计时、成绩与复盘，以及待真机验证的魔域威龙 V10 AI 适配层。

## 当前交付

- Kotlin + Jetpack Compose 原生 Android 工程，`minSdk 26`。
- 不声明 `INTERNET`；公式、训练、计时、记录和备份全部本地运行。
- 四个固定一级入口：公式、训练、计时、记录。
- 119 个稳定 ID 的本地 CFOP 索引（41 F2L / 57 OLL / 21 PLL），统一使用黄顶蓝前；局面由公式逆序生成，九宫格和三维演示共用同一状态。
- SQLite 本地数据：预设 case、用户掌握度、收藏、笔记、用户公式、Solve 和 MoveEvent。
- DataStore 设置：停顿阈值、低动态、色觉辅助、振动和 gyro 跟随偏好。
- 统计：最佳、平均、ao5、ao12，保留原始毫秒数并单独保存 +2 / DNF。
- 备份：`.cubetrace.zip`，包含 manifest、JSONL 用户数据、校验和与通知文件。
- V10 AI：服务 UUID、MAC 提示/规范化、AES 包装、A1/A3/A4/A5/AB 解码、四元数归一化与 q/-q 连续性、串行 GATT 操作和只读 opcode 白名单。

## 打开与构建

用 Android Studio 打开本目录，等待 Gradle 同步后选择 `app` 运行。工程使用 Java 17、Kotlin 2.0.21、Android Gradle Plugin 8.7.2，首次构建需要 Android SDK 35。

也可以在已安装 Gradle 的环境执行：

```text
gradle assembleDebug
```

当前工作区没有提交签名密钥、`local.properties` 或构建产物。发布前请按 `docs/spec/07-开源合规与离线分发.md` 准备 release keystore、源码包和许可证附件。

## 真机范围与限制

- V10 AI 连接需要系统蓝牙权限；Android 12+ 使用附近设备权限，Android 8–11 使用系统要求的位置权限。
- 应用不会后台扫描，不提供任意十六进制写入，不实现改名、重置或固件升级。
- V10 AI 协议、坐标轴和四元数跟随尚未在用户真实设备上验收，界面显示“待真机验证”。
- 当前 UI 的三维展示使用本地生成的二维贴面示意；三维渲染桥与完整实时 CubeState/PhysicalOrientation 视图留在后续硬件验证切片。
- 当前 119 个 case 的 stableId、canonicalState 和公式可由 `core/cube` 重建；公式来源与许可记录在 `docs/provenance/formulas.yml`。

## 代码结构

```text
app/src/main/java/com/cubetrace/app/
├─ MainActivity.kt
└─ core/
   ├─ backup/       离线备份
   ├─ cube/         公式解析、CubeState、scramble、case 生成
   ├─ data/         SQLite 与 DataStore
   ├─ device/       V10 AI 协议与 BluetoothGatt 状态机
   └─ model/        领域实体、统计与展示口径
```

下一步建议按文档路线推进：先用真实 V10 AI 完成 G0 协议证据，再替换 reviewed CFOP 内容包，之后接入受限本地三维渲染与完整事件复盘。
