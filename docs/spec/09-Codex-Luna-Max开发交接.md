# 09. Codex / Luna Max 开发交接

## 1. 使用方式

用户准备在新的 Codex 任务页中开发。请：

1. 在新任务页打开独立工作区 C:\CodexProjects\CubeAPP。
2. 在模型选项中选择 GPT-5.6 Luna，并将 reasoning effort 设为 max。
3. 把本文件第 4 节提示词粘贴到新任务。
4. 先完成 M0，不要一口气生成整个 APP。

“Luna max”在这里表示 GPT-5.6 Luna + max 推理强度，不是另一个名为 Luna Max 的模型。官方模型页列出 GPT-5.6 Luna，最新模型指南说明 max 属于支持的推理强度：

- [GPT-5.6 Luna 模型页](https://developers.openai.com/api/docs/models/gpt-5.6-luna)
- [最新模型选择指南](https://developers.openai.com/api/docs/guides/latest-model)

Luna 适合高吞吐、成本敏感场景；用户已明确选择 Luna + max，开发任务应保留这个选择。若某个极难问题反复失败，再由用户决定是否切换 Sol，不应在交接时擅自更换。

## 2. 工作区隔离

独立项目根目录：

    C:\CodexProjects\CubeAPP

设计文档位置：

    C:\CodexProjects\CubeAPP\docs\spec

新任务必须：

- 只在该独立项目根目录创建魔方业务代码。
- 在根目录初始化 Android / Git 项目，并完整保留 docs\spec。
- 不读取或改动 C:\CodexProjects\出差记录APP。
- 不再复制一份设计文档到其他工程，避免产生两个不一致版本。

暂定：

- 应用名：方迹 CubeTrace。
- applicationId：com.cubetrace.app。
- minSdk：26。
- 许可证：GPL-3.0-only。

应用名和 applicationId 在第一次对朋友发布前可以修改；发布后保持稳定。

## 3. 开发纪律

- 按 README → 00 → 09 的顺序读总览，再读当前里程碑涉及的专题。
- 先检查新工作区的 AGENTS.md 和现有修改。
- 使用原生 Kotlin / Compose / BluetoothGatt。
- 无 INTERNET 权限。
- BLE 写入只有强类型白名单，不实现任意 raw write。
- 先保存事实，再做分析；move 不可丢，gyro 可降采样。
- 真实硬件能力未验证时明确标“待真机验证”。
- 每次只完成一个可验收里程碑或一个小的垂直切片。
- 任何数据库变更包含迁移和迁移测试。
- 任何第三方代码或数据都记录来源、版本和许可证。
- 不复制参考图片、视频帧或第三方 APP UI。
- 不以编译成功代替真机和数据一致性验收。

## 4. 首个新任务提示词

以下内容可直接复制到新的 Codex 任务页：

~~~text
你要在一个全新的 Android 工作区中实现“方迹 CubeTrace”，这是完全离线的 3×3 CFOP 公式、训练、计时和魔域威龙 V10 AI 复盘 APP。

设计基线在：
C:\CodexProjects\CubeAPP\docs\spec

请完整阅读该目录的 README.md、00-可行性评估与决策.md、04-技术架构与数据规范.md、05-V10AI蓝牙与三维跟踪规范.md、07-开源合规与离线分发.md、08-测试验收与迭代路线图.md、09-Codex-Luna-Max开发交接.md。若工作区有 AGENTS.md，也必须先完整阅读。

重要边界：
1. 当前工作区 C:\CodexProjects\CubeAPP 已与“出差记录APP”隔离；不得读取或改动后者。
2. 在当前项目根目录初始化独立 Git / Android 工程，必须完整保留 docs\spec。
3. 使用 Kotlin、Jetpack Compose、Coroutines / Flow、Android 原生 BluetoothGatt。minSdk 26，target / compile 使用开发时稳定最新版。
4. 应用完全离线，不声明 INTERNET 权限。
5. 项目按 GPL-3.0-only；任何上游代码、协议或数据都保留来源、许可证和 commit。
6. 只支持魔域 V10 AI；不要提前实现其他品牌。
7. BLE 写入必须是强类型白名单，只允许请求设备信息、完整局面、电量和开关 gyro。不得提供任意字节写入，不得实现改名、重置或固件命令。
8. move 改变内部 CubeState；四元数只改变整个模型的 PhysicalOrientation。两者必须分离。
9. 没有真实真机证据时，不得声称协议里程碑完成。

本任务只实现 M0“V10 Lab 协议验证”，不要开发完整产品页面。

M0 交付：
- 可安装的最小 Compose APP。
- Android 8–11 与 Android 12+ 的正确蓝牙权限流程。
- 扫描、选择、连接、断开、超时和串行 GATT 队列。
- 独立纯 Kotlin 协议层：MAC 规范化与来源、AES key / IV 派生、RX / TX 包装、消息 decoder。
- 读取设备信息、电量、完整 facelet、move，以及用 0xAC 开启 0xAB gyro。
- 四元数按 little-endian int32 / 2^30 解析、归一化、q / -q 连续处理和三轴校准。
- CubeState 与 PhysicalOrientation 分离的简单三维显示；若先用本地 WebView，必须按文档关闭文件访问和任意导航。
- move sequence gap 检测、完整状态重同步、连接 epoch。
- 只读安全诊断页和脱敏 JSON 导出，绝不提供 raw write 输入框。
- 协议测试向量、fake transport、解析 / 四元数 / 状态机单元测试。
- README：构建、安装、真机验证步骤、已知限制。
- LICENSE、NOTICE、THIRD_PARTY_NOTICES 和协议来源记录。

先检查环境和文档，给出简短实施计划，然后直接开始。每完成一个可验证切片就运行对应测试。不要修改范围外文件，不要提交或推送，除非我另行要求。

如果你无法直接访问 V10 AI，请完成 fake / fixture 可验证部分，生成供我安装的 debug APK 和明确的真机操作清单；把 M0 标记为“待真机验证”，等待我把脱敏诊断导出交给你分析。
~~~

## 5. M0 推荐切片

### Slice 0：工程与安全边界

- 新项目、Compose 空页面。
- LICENSE / NOTICE / 第三方账本。
- 确认 Manifest 无 INTERNET。
- permission abstraction 和 fake device。

验收：构建、安装、权限拒绝后 APP 仍可进入。

### Slice 1：扫描与 GATT

- 扫描列表。
- 连接状态机。
- 服务发现。
- 串行操作队列。

验收：真机能看到服务和特征；失败码可导出。

### Slice 2：密码与基本消息

- KeyDeriver。
- PacketCipher。
- info、battery、facelet decoder。

验收：测试向量通过，真机数据可解释。

### Slice 3：move 和 CubeState

- move decoder。
- sequence / epoch。
- CubeEngine。
- gap → facelet resync。

验收：500 move 和 20 次 Solve 一致性。

### Slice 4：gyro 和校准

- 0xAC 安全命令。
- 0xAB decoder。
- normalize、sign continuity、SLERP。
- 三轴校准和简单显示。

验收：05 文档 gyro 条目通过。

### Slice 5：诊断与证据

- 脱敏 JSON。
- 延迟、频率、gap 和错误统计。
- 真机验收记录。

验收：G0 报告能让下一任务判断是否进入 M1。

## 6. M0 目录建议

    app/
    core/
      model/
      cube/
      device/
      render3d/
    docs/
      spec/
      protocol/
      validation/
      provenance/
    LICENSE
    NOTICE.md
    THIRD_PARTY_NOTICES.md
    README.md

协议层内部：

    transport/
    gatt/
    moyu/
      KeyDeriver
      PacketCipher
      MessageDecoder
      MoyuV10Adapter
      SafeCommand
    diagnostics/
    fake/

不要在 Activity 或 ViewModel 中直接解析 BLE byte array。

## 7. 真机反馈格式

如果新任务需要用户运行 APK，输出一个单一清单，不让用户读开发日志：

1. 安装 debug APK。
2. 关闭其他可能连接魔方的 APP。
3. 打开蓝牙并授权附近设备。
4. 依次完成连接、六面各转、三轴校准、指定 scramble 和完整复原。
5. 点击“导出脱敏诊断”。
6. 把 JSON 文件附回新任务。

诊断包应自动包含：

- 手机型号和 Android 版本。
- APP commit。
- 设备广播名、遮蔽地址、固件和服务。
- 握手各阶段结果。
- 包 type、长度和时间统计。
- move sequence 摘要。
- gyro 频率、长度误差和三轴验证。
- 最终状态 hash。

不得包含密钥、完整 MAC、其他附近设备和用户旧成绩。

## 8. M0 之后的提示词

只有 G0 通过，下一任务才使用：

~~~text
继续方迹 CubeTrace。先阅读 docs/spec 和 docs/validation/M0 的真实 V10 AI 验收记录，确认 G0 已通过。现在只实现 08 文档中的 M1：产品骨架与公式库。严格遵循 01、02、03、04、06、07 文档，不扩展到训练、完整计时或复盘。保持无 INTERNET 权限和 GPL 来源账本。先审计现有工作区与测试，再给出计划并实现；以 119 个 stableId 唯一、预设可验证、列表流畅、三维安全降级作为完成条件。
~~~

后续仍按 M2、M3、M4、M5 分任务推进。每次把上一个里程碑的真实验收记录作为下一个任务的入口条件。

## 9. 交接检查

新任务开始前，用户只需确认：

- 已在新任务中选中独立目录 C:\CodexProjects\CubeAPP。
- 已选择 GPT-5.6 Luna 和 max 推理强度。
- V10 AI 在手边，或接受 M0 暂停在“待真机验证”。
- 不再让新任务修改“出差记录 APP”。

其余产品和技术选择已经在文档中给出，不需要重新从零讨论。
