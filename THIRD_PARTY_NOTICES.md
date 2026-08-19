# Third-party notices

本文件是当前工程的依赖与来源账本。版本和使用范围应在发布包中随源码一起保留。

| 组件 / 来源 | 许可证 | 用途 | 处理 |
|---|---|---|---|
| Kotlin | Apache-2.0 | 语言与标准库 | 通过 Gradle 依赖，保留上游通知 |
| AndroidX Activity / Lifecycle / Compose / Material 3 | Apache-2.0 | Android UI 与生命周期 | 通过 Gradle 依赖，保留上游通知 |
| AndroidX DataStore | Apache-2.0 | 本地设置 | 通过 Gradle 依赖，保留上游通知 |
| csTimer `moyu32cube.js` | GPL-3.0 | V10 AI 协议事实与行为参考 | 当前未复制 JavaScript 文件；事实与引用记录在 `docs/provenance/protocol.md` |
| smartcube-web-bluetooth `moyu32.ts` | MIT | V10 AI 协议、AES、消息字段与 gyro 参考 | 当前为独立 Kotlin 实现；保留来源链接与 MIT 署名 |
| Noto Sans SC / Barlow Condensed / JetBrains Mono | OFL-1.1 | 视觉规范指定字体角色 | 当前 UI 使用系统 Sans / Monospace 回退，字体文件未打包 |

## 分发提醒

若以后复制或修改 GPL / MPL 覆盖的上游源代码或公式数据，应把对应许可证全文、版权头、修改说明与源码一并放入发布目录。当前自建公式样例不宣称来自任一第三方数据集。
