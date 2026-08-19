# V10 AI protocol provenance

状态：`待真机验证`。

## 公开参考

- csTimer `src/js/hardware/moyu32cube.js`: <https://github.com/cs0x7f/cstimer/blob/master/src/js/hardware/moyu32cube.js>
- smartcube-web-bluetooth `src/smartcube/protocols/moyu32.ts`: <https://github.com/poliva/smartcube-web-bluetooth/blob/main/src/smartcube/protocols/moyu32.ts>

## 当前独立实现范围

- 广播名称粗筛：`WCU_MY32_` / `WCU_MY3`。
- 服务：`0783b03e-7735-b5a0-1760-a305d2795cb0`。
- 通知特征：UUID 末尾 `cb1`；写入特征：UUID 末尾 `cb2`。
- 设备名后缀 MAC 提示、制造商数据逆序读取、MAC 规范化和公开基础 key / IV 派生。
- 纯 Kotlin AES-ECB/NoPadding + IV XOR 包装。
- 只读命令：A1 设备信息、A3 完整局面、A4 电量、AC gyro 开关。
- 消息：A1 / A3 / A4 / A5 / AB；AB 四元数按 little-endian int32 / 2^30 归一化。
- Android 原生 GATT 串行操作队列；无任意 byte array 写入 API。

## 未宣称完成的项目

- 真实设备广播与固件矩阵。
- MAC 来源在具体 Android 版本上的稳定性。
- AES 测试向量和真实包长度的设备确认。
- A3 facelet 顺序与 Cubie/CubeState 的真机一致性。
- move 序号 gap、完整状态重同步和 device timestamp 对齐。
- 四元数轴向、左右手性、参考姿态与三轴校准。

在上述项目由真实 V10 AI 诊断包和回放 fixture 证明前，产品 UI 持续显示“待真机验证”。
