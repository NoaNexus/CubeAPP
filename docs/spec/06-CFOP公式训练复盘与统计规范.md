# 06. CFOP 公式、训练、复盘与统计规范

## 1. 内容范围

首版预设：

| 阶段 | 数量 | 说明 |
|---|---:|---|
| F2L | 41 | 标准非已配对基础 case，不含 Cross 教程数量 |
| OLL | 57 | 完整 OLL |
| PLL | 21 | 完整 PLL |
| 合计 | 119 | 每个 case 至少一个已验证变体 |

Cross 提供概念、记号和训练入口，但不计入这 119 个预设 case。

## 2. 公式数据规范

每个 CubeCase 包含：

- stableId。
- stage：F2L / OLL / PLL。
- 标准编号和常用别名。
- canonicalState：完整局面。
- viewOrientation：展示时的 U / F 面定义。
- recognitionFeatures：用于辅助讲解的关键贴片，不作为唯一真值。
- symmetryGroup：允许的 y 转体、镜像或 AUF 归一化规则。
- defaultVariantIds。
- sourceNotice、presetVersion 和 verifiedBy。

每个 AlgorithmVariant 包含：

- 原始 notation。
- 解析后的 AST。
- 展开后的规范 move 列表。
- 预置 AUF / 结束 AUF。
- 手法分组。
- 标签，例如少转体、左手、右手、短步数。
- 来源类型：预设、用户、导入。
- 校验状态和校验版本。

图片只作为缓存，由 canonicalState 和 viewOrientation 生成，不是源数据。

## 3. 记号解析

### 3.1 首版必须支持

- 面转：U D L R F B。
- 后缀：prime、2；允许用户输入普通撇号并规范化。
- 宽层：Rw、Uw 等；可接受 r、u 等别名，保存时统一为用户偏好。
- 夹层：M E S。
- 整体转体：x y z。
- 括号分组和重复次数。
- 空格与常见全角空格容错。

### 3.2 内部 AST

- Move(face, amount, layerRange)。
- Rotation(axis, amount)。
- Sequence(children)。
- Repeat(node, count)。
- Group(node, annotation)。

解析和显示分开：保存原始输入供用户编辑，同时保存规范 AST 和 normalizedMoves 用于执行、验证和统计。

### 3.3 错误

错误必须给 token 位置和建议：

- 未知符号。
- 同一 move 出现冲突后缀，例如 R2 prime 2。
- 括号未闭合。
- 重复次数无效。
- 当前版本不支持的交换子或共轭语法。

不对疑似错误公式自动“修好”后静默保存。

## 4. 公式校验

校验步骤：

1. 解析 notation。
2. 从 case 的 canonicalState 出发。
3. 应用预置 AUF。
4. 执行 normalizedMoves。
5. 检查目标状态。
6. 应用允许的结束 AUF 等价规则。
7. 记录 engineVersion、输入摘要和结果。

目标：

- OLL：完整公式通常应解决到可进入 PLL 的正确状态，具体由该变体声明；不能只检查顶面颜色。
- PLL：执行后允许 U 层 AUF 达到 solved。
- F2L：目标 pair 插入且不破坏已声明保留的 Cross / slots；不同槽位通过对称映射归一化。

预设公式必须有自动校验和至少一次人工核对。用户公式可以保存为“未校验”，但不能显示与已校验相同的徽标。

## 5. 公式来源和更新

- 每批预设内容有 presetVersion、来源说明和内容 hash。
- 如果从 GPL 项目复制受版权保护的数据集合，则该文件与应用一并按 GPL 分发并保留来源。
- 若只参考公开 case 分类，自行录入并逐条验证，也要记录整理日期和验证脚本版本。
- 应用升级修改预设公式时，不改变用户首选、排序、笔记和自建变体。
- 被预设更新替换的旧版本保留迁移记录，用户可以查看差异。

## 6. 局面生成

公式详情和训练局面由核心引擎生成：

- 从 canonicalState 直接渲染。
- 或从 solved 状态应用 setup alg。
- 所有生成结果必须与 canonicalState hash 一致。

训练 scramble 需要满足：

- 不泄露答案的随机 AUF / y 方向变化。
- 保持该 case 的等价类。
- 可复现的 randomSeed，便于诊断。
- 智能魔方设置指导产生的最终 facelet 与目标一致。

## 7. 训练记录与掌握度

### 7.1 Attempt 结果

| 结果 | 含义 |
|---|---|
| WRONG | 识别错误、公式错误或最终局面错误 |
| HESITANT | 正确但反应超过个人阈值，或用户主观标记迟疑 |
| CORRECT | 正确完成 |
| SKIPPED | 主动跳过，不影响掌握度 |
| INCOMPLETE | 设备断连或数据不足，不影响掌握度 |

### 7.2 复习箱

- WRONG：下降一级，最低 0；本轮稍后重新出现。
- HESITANT：等级不变，缩短下次间隔。
- CORRECT：连续正确时上升一级，最高 5。
- 用户手动更改掌握度，记录 reason=manual。

同一个 case 的识别能力和执行能力分别统计，但首版掌握度可取两者较低值，避免“会背名字但不会做”。

### 7.3 时间统计

- 反应时间：局面稳定显示到用户揭示、选择或第一 move。
- 执行时间：第一 move 到目标状态。
- 总时间：包含准备以外的反应 + 执行。
- 使用中位数和最近 10 次趋势，减少极端值影响。

## 8. Solve 事件标准化

每个完整 Solve 至少需要：

- scramble 及其目标状态。
- 计时开始与结束单调时间。
- 按时间排序的 MoveEvent。
- 起始和最终 FaceletCheckpoint。
- 可选 OrientationTrack。
- 连接 gap、重同步和用户操作。

分析前检查：

- 起始局面是否与 scramble 一致。
- move 应用后是否达到 solved。
- sequence 是否完整。
- 时间是否单调。
- 是否存在重同步导致的未知区间。

失败时仍保存 Solve，但 completeness 降级，受影响分析不输出精确值。

## 9. CFOP 自动分段

### 9.1 参考坐标

默认用户白 Cross，采用黄顶蓝前的持握方式，白色对应 D。用户设置其他 Cross 色时，先用中心色映射到规范 D / U 坐标。整体手持转体不改变 cubie 的颜色身份。

色中不在首版正式支持范围；未来可通过分析多个 Cross 候选实现。

### 9.2 状态谓词

CrossSolved：

- 四个 D 层 Cross edge 均在正确位置和方向。
- 其侧色与对应中心一致。

F2LSolved：

- CrossSolved。
- 四个 D 层 corner 和四个中层 edge 均在正确位置和方向。

LastLayerOriented：

- F2LSolved。
- U 层所有向上 facelet 与 U 中心同色。

CubeSolved：

- 所有 cubie 位置和方向均正确；允许在统计展示层单独记录最终 AUF，但存储真值为明确状态。

### 9.3 稳定完成点

不能把某个谓词第一次短暂成立就当作阶段结束。离线分析使用“持续成立”：

- Cross 结束：从该 move 后 CrossSolved 一直保持到 F2L 完成的最早候选。
- F2L 结束：从该 move 后 F2LSolved 一直保持到 solved。
- OLL 结束：从该 move 后 LastLayerOriented 一直保持到 solved。
- PLL 结束：首次 CubeSolved。

若求解过程中破坏已完成部分，记录 regression anomaly，并选择后续稳定完成点。

### 9.4 特殊情况

- X-cross：Cross 结束时已有一个或多个完整 F2L pair；F2L 从相同时间开始，已完成 pair 数记录为 initialPairs。
- OLL skip：F2L 结束时 LastLayerOriented 已成立，OLL 时长为 0，明确标“跳 OLL”。
- PLL skip：OLL 结束时 CubeSolved，PLL 时长为 0。
- OLL + PLL skip：F2L 结束即 solved，两个阶段均为 0。
- AUF：归入 PLL 或独立 final AUF 指标；主显示默认归 PLL。
- 断连 gap 跨越边界：边界标低置信度，使用前后 checkpoint 给出区间而非伪精确时刻。

### 9.5 F2L pair 进度

对四个 slot 分别判断 corner + edge 是否正确：

- 每个 move 后记录 solvedSlots 位图。
- 新增 slot 是 progress。
- 已解 slot 被破坏是 regression。
- 多个 slot 同一步变化仍按状态记录，不猜测用户意图。

识别具体 F2L case 需要在插入前找到 pair 的位置、朝向和目标 slot，并处理 y 方向等价；列为 P2。首版 P1 先保证阶段和 slot 进度正确。

## 10. OLL / PLL case 识别

- OLL 在 F2L 完成后的状态上识别，枚举 U 层 AUF 和允许的 y 等价。
- PLL 在 LastLayerOriented 后识别，枚举 AUF 并保持中心坐标一致。
- 匹配结果保存 caseId、setup transform 和 confidence。
- 无唯一匹配时保留候选，不强行选择。
- 公式执行中的临时局面不用于重新命名 case。

## 11. 指标定义

### 11.1 时间

- Solve time：结束单调时间减开始单调时间。
- Phase time：阶段结束减阶段开始。
- Inspection：独立保存，不计入 Solve time。
- Penalty-adjusted time：仅用于成绩排序和平均值。

### 11.2 move count

| 口径 | 定义 |
|---|---|
| HTM | 每个外层或块转动计 1，90° 与 180°均计 1；整体转体计 0 |
| QTM | 90°计 1，180°计 2；整体转体计 0 |
| STM | 每个单次 slice / block turn 计 1；整体转体计 0 |

V10 AI 正常只报告物理外层 turn；由公式 AST 产生的宽层和夹层按上述定义计算。

### 11.3 TPS

- 总 TPS = move count ÷ 阶段总秒数。
- 活跃 TPS（启发式）= move count ÷ activeSeconds。
- 对相邻 move 间隔大于 pauseThreshold 的部分，activeSeconds 只保留阈值长度，超出部分计为 pause。

默认 pauseThreshold 为 250 ms，可调。活跃 TPS 必须标明“估算”，总 TPS 是主要可比较口径。

### 11.4 停顿

- gap = 当前 move 时间 − 前一 move 时间。
- gap 大于阈值记一次停顿。
- 停顿时长 = gap − pauseThreshold。
- 阶段起点到第一 move、最后 move 到阶段终点分别单独记录 startPause 和 endPause。
- 最长停顿显示绝对 gap 和所在 Move Rail 位置。

### 11.5 转体

由 05 文档的 gyro 稳定方向算法产生：

- count。
- x / y / z 类别。
- 起止时间。
- confidence。

无 gyro 的 Solve 显示“未记录”，不是 0。

## 12. 平均与最佳

单次排序：

- DNF 最差。
- +2 使用加罚后的成绩。
- 原始时间只在同一最终成绩的诊断中使用。

aoN：

- N 至少为 5。
- 两端各剔除 ceil(N × 5%) 个结果。
- DNF 作为最差结果；DNF 数超过可剔除的最差数量时，平均为 DNF。
- 对剩余有效最终成绩取算术平均，显示到应用统一精度。

因此：

- ao5 两端各剔除 1 个。
- ao12 两端各剔除 1 个。
- ao100 两端各剔除 5 个。

此口径在帮助页公开，并对 DNF、+2、相同成绩和不足 N 条建立单元测试。

## 13. 分析置信度

每个阶段和诊断条目都给内部 0–1 分值，UI 映射为：

| 范围 | UI |
|---:|---|
| 0.90–1.00 | 可靠 |
| 0.65–0.89 | 可参考 |
| 低于 0.65 | 可能不准确 |

降低因素：

- move sequence gap。
- 重同步跨越阶段边界。
- 起始局面不匹配 scramble。
- gyro 未校准或样本中断。
- 多个 case 等价匹配。
- 用户手动修改阶段边界。

用户修正后，显示“人工修正”，不把置信度假装提升为自动可靠。

## 14. 诊断语句边界

允许：

- “F2L 用时占本次的 54%。”
- “第 2 对完成前有 1.12 秒无 move。”
- “OLL 比你最近 20 次的中位数慢 0.38 秒。”
- “检测到 3 次稳定整体转体，结果可参考。”
- “这次 move 数据不完整，无法准确计算 PLL 步数。”

不允许：

- “你的 lookahead 很差。”
- “你在这里换了右手食指。”
- “这是唯一最佳解法。”
- “AI 确定你应该改用某公式。”

任何建议都要链接到支撑它的时间区间、case 或历史样本。

## 15. 分析器版本与重算

AnalyzerVersion 使用语义化版本：

- patch：不改变结果口径的 bug 修复。
- minor：新增指标或可选诊断。
- major：阶段或指标定义改变。

保存 inputDigest。应用升级后：

- 旧结果可立即展示，并标“使用旧分析”。
- 后台按最近使用顺序重算。
- 用户可选择重算全部。
- 人工修正按 move ordinal 和相对时间迁移；无法可靠迁移时请求确认。

## 16. 测试语料

至少建立下列可重复 event fixtures：

- 正常 CFOP。
- X-cross。
- F2L pair 插入后被破坏再恢复。
- OLL skip。
- PLL skip。
- OLL + PLL 双 skip。
- 最终 AUF。
- move 序号回绕。
- gap 位于阶段内。
- gap 跨阶段边界。
- gyro 缺失。
- q / -q 交替输入。
- 用户选择非白 Cross。
- DNF 和 +2 的 ao5 / ao12 / ao100。

每个 fixture 保存 scramble、move 列表、预期阶段 ordinal、指标和置信度，不依赖 UI 截图作为唯一断言。

## 17. 深度成绩分析扩展

后续智能成绩分析按 [10-智能成绩分析与纪录规划.md](./10-智能成绩分析与纪录规划.md) 实施。该文档在本规范基础上进一步冻结：

- `C / F1 / F2 / F3 / F4 / O / P` 的稳定分段与 X-cross 口径。
- 总体和分阶段 time、TPS、活跃 TPS、停顿率及跨边界 pause 分配。
- 基于个人历史的可解释提速建议。
- current / best ao5、ao12、PB 事件和“下一把多少可刷新纪录”的精确算法。

实现时不得在 UI、数据库查询和分析器中分别维护三套平均或停顿公式；所有入口必须调用同一纯 Kotlin 领域函数。
