# Implementation status

本文件把“已经写入工程”和“需要真实设备或内容审校才能宣称完成”分开，避免把代码存在误读成硬件验收通过。

## 已写入工程

- 四入口 Compose 工作台、浅色视觉 token、设备状态胶囊、Case 卡片、Move Rail、统计趋势和设置弹层。
- 公式 token parser：外层面、prime、2、宽层别名、夹层、转体、括号分组与重复次数错误定位。
- 54 facelet CubeState、局面 hash、scramble、程序生成贴面图和原生 Canvas 等距三维演示。
- SQLite schema：case / variant / user state / solve / move event / training attempt；显式 schema version 2，升级时保留用户状态并迁移预设方向。
- DataStore 设置、掌握度五级复习箱、手动计时、+2 / DNF、ao5 / ao12、离线 ZIP 备份。
- V10 AI：BLE 权限抽象、广播粗筛、GATT 服务发现、串行操作队列、公开协议字段解码、AES 包装、四元数连续性和安全命令白名单。

## 当前明确的产品边界

- `PresetCatalog` 已内置 41 F2L / 57 OLL / 21 PLL 的完整参考目录；公式统一按黄顶蓝前解释，canonicalState 由公式逆序生成，来源记录在 `docs/provenance/formulas.yml`。
- V10 AI 适配层是代码实现，不是 G0 真机证据。真实广播、固件、MAC、AES 包、facelet 顺序、move gap、四元数轴向与校准仍需设备记录。
- 手动 Solve 没有动作事件时，复盘页会明确显示“没有动作事件”，不输出伪造的 CFOP 阶段时间。
- 三维演示只负责本地展示，不持有业务 CubeState；BLE move、orientation 到三维渲染的完整实时事件桥需要在真机协议确认后接入。
