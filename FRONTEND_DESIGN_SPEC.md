# Loyalty SaaS — 前端界面设计规范

> 版本: v1.0 | 日期: 2026-06-04 | 框架: React 18 + TypeScript + Vite + Ant Design 5

---

## 一、全局设计语言

### 1.1 色彩体系

| 用途 | 色值 | 说明 |
|------|------|------|
| 页面背景 | `#ffffff` | 纯白底，全站统一 |
| 主文字 | `#1a1a1a` | 标题、正文 |
| 辅助文字 | `#666666` | 描述性文字 |
| 弱化文字 | `#999999` | 提示、占位 |
| 禁用/分隔 | `#bbb` / `#ccc` | 不可用状态、浅分隔 |
| 分割线 | `#e0e0e0` | 列表行间分割 |
| 卡片边框 | `#e8e8e8` | 内容卡片边框 |
| 输入框边框 | `#e0e0e0` | 默认态；focus 变为 `#1a1a1a` |
| 主按钮 | `#1a1a1a`（黑底白字） | 实心操作按钮 |
| 次按钮 | `#fff` + `1px solid #d9d9d9` | 空心操作按钮 |
| hover 反转 | 白底黑字 ↔ 黑底白字 | 按钮/链接 hover 效果 |

### 1.2 字体

| 项目 | 值 |
|------|-----|
| 字体族 | `-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif` |
| 标题 | 24-28px, weight 700, `#1a1a1a` |
| 卡片标题 | 19px, weight 600 |
| 正文 | 14-15px, `#666` |
| 标签/辅助 | 13px, weight 500 |
| 小字 | 11-12px, `#999` |

### 1.3 圆角与间距

| 元素 | 圆角 |
|------|------|
| 卡片 | 12px |
| 按钮 | 10px |
| 输入框 | 8px |
| 开关(Toggle) | 12px (44×24) |
| 标签(Tag) | 4px |

- 列表行间距: `padding: 10-12px 0` + `border-bottom: 1px solid #e0e0e0`
- 卡片内边距: `24px 28px`
- 组件间 gap: `10px`

### 1.4 按钮规范

```css
/* 主按钮 — 实心黑底白字 */
height: 42px; padding: 0 28px; background: #1a1a1a;
border: none; border-radius: 10px; color: #fff;
font-size: 15px; font-weight: 500;

/* 次按钮 — 空心黑框白底 */
height: 42px; padding: 0 28px; background: #fff;
border: 1px solid #d9d9d9; border-radius: 10px; color: #1a1a1a;
font-size: 15px; font-weight: 500;

/* hover — 反转 */
主按钮: opacity: 0.85
次按钮: background: #1a1a1a; color: #fff
```

### 1.5 输入框规范

```css
height: 42px; padding: 0 14px;
border: 1px solid #e0e0e0; border-radius: 8px;
font-size: 15px; color: #1a1a1a; background: #fff;
/* focus: border-color → #1a1a1a */
```

### 1.6 Toggle 开关

```css
width: 44px; height: 24px; border-radius: 12px;
/* ON: background #1a1a1a, dot left 22px */
/* OFF: background #e0e0e0, dot left 2px */
dot: width 20px; height 20px; background #fff; border-radius 50%;
```

### 1.7 图标

- 全部使用 **自定义 SVG 纯线条图标**
- 统一规格: `stroke="#1a1a1a" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" fill="none"`
- 尺寸: 步进箭头 20×20, 步骤图标 24×24, 按钮箭头 16×16

---

## 二、布局架构

### 2.1 页面路由结构

```
/login              → 独立页面（无菜单）
/onboarding         → 独立页面（无菜单，全屏引导）
/                   → AppShell 包裹（顶部菜单 + 内容区 + 底栏）
  /dashboard        → 仪表盘
  /members          → 会员列表
  /members/:id      → 会员详情
  /modeling/entity  → DB Schema 设计器 (DrawDB)
  /modeling/schema  → 表单 Schema 设计器
  /rules            → 规则列表
  /rules/new        → 规则编辑器
  /channels         → 渠道列表
  /system/*         → 系统设置各页
```

### 2.2 AppShell 布局

```
┌──────────────────────────────────────────────────┐
│ [Logo] 数据建模 会员服务 规则引擎 设置   PROG001 🔔 👤│ ← Header 56px
├──────────────────────────────────────────────────┤
│                                                  │
│              内容区 (max-width: 1400px)           │
│                                                  │
├──────────────────────────────────────────────────┤
│ 当前俱乐部: PROG001          环境: DEV | v1.0.0  │ ← Footer 32px
└──────────────────────────────────────────────────┘
```

- Header: 白色底 `#fff`，`border-bottom: 1px solid #f0f0f0`，`box-shadow: 0 1px 4px rgba(0,0,0,0.04)`
- 菜单: `mode="horizontal"`，`triggerSubMenuAction="hover"`
- Footer: 白色底，顶部 `1px solid #f0f0f0` 分割

### 2.3 菜单结构

| 一级菜单 | 二级菜单 |
|---------|---------|
| 数据建模 | DB Schema 设计器 (DrawDB) |
| 会员服务 | 会员列表 |
| 规则引擎 | 规则列表 |
| 设置 | 积分类型、等级设置、渠道列表、脚本工作台、角色权限、操作日志、SPI 日志、租户审计 |

---

## 三、核心组件模式

### 3.1 ClickToEditText（点击编辑文字）

- **显示态**: 文字 + `border-bottom: 1px dashed #d9d9d9` 虚线提示可编辑
- **编辑态**: 点击后原地变为 `<input>`，自动聚焦全选
- **确认**: Enter 或 onBlur 退出编辑
- **宽度**: 通过 `style.width` 固定列宽，`overflow: hidden; textOverflow: ellipsis`

### 3.2 ClickToEdit（点击编辑数值）

- 同 ClickToEditText，但输入框为 `type="number"`
- 可带单位后缀（如 "分"、"天"）

### 3.3 ClickToEditSelect（点击编辑下拉）

- **显示态**: 文字 + 虚线（显示当前选项的 label）
- **编辑态**: 点击后变为 `<select>`，选择后自动退出编辑

### 3.4 Toggle（开关）

- 纯 CSS 实现，44×24 圆形滑块
- `onChange` 回调，受控组件

### 3.5 PageWrapper（页面容器）

```tsx
<PageWrapper loading={loading} error={error} isEmpty={!data?.length}
  emptyText="暂无数据" onRetry={fetchData}>
  {children}
</PageWrapper>
```
- loading → Spin
- error → Result + 重试按钮
- empty → Empty 组件
- 正常 → 渲染 children

### 3.6 StatCard（统计卡片）

```tsx
<StatCard title="会员总数" value={12345} prefix={<Icon />} trend={12} trendLabel="较昨日" />
```

---

## 四、新用户引导流程 (Onboarding)

设计为**3 步顺序向导**，全屏独立页面，无顶部菜单。

### 4.1 页面布局

```
        ①         ②         ③
    俱乐部设置 → 积分类型设置 → 等级设置
    ━━━━━━━━

┌─────────────────────────────────────┐
│ 🧩 俱乐部设置                        │
│ 俱乐部是忠诚度计划的基础容器...        │
│                                     │
│ 俱乐部代码 *  [________]             │
│ 显示名称 *    [________]             │
│ 描述          [________]             │
│                                     │
│              [保存并继续 →]          │
└─────────────────────────────────────┘
```

- 步骤编号: 40px 圆形，active 黑框白底黑字，done 黑底白勾
- 步骤间: 20px 箭头 `────>`
- 步骤标签下方: `40px 宽 2px 深灰线` 标识当前步骤
- 内容卡片: `max-width: 720px 居中`，`maxHeight: calc(100vh-280px) overflow:auto`

### 4.2 Step 1 — 俱乐部设置

- 表单字段: 俱乐部代码(必填)、显示名称(必填)、描述(可选)
- 按钮: `保存并继续 →`（黑底白字，含箭头图标）
- 保存时调用 `POST /api/admin/programs`

### 4.3 Step 2 — 积分类型设置

**行结构(单行 flex)**:
```
[名称] [代码] [类型▼] [配置项...] [×删除]
```

**类型下拉选项**:

| 选项 | 配置项 |
|------|--------|
| 兑换 | 负分(开关→单次/上限) + 可见 + 有效期(数值+模式) |
| 等级 | 可见 + 有效期(数值+模式) |
| 信用 | 仅额度可编辑（其他锁定） |

**有效期模式**: 固定天数 / 自然月 / 自然年（0=永不过期）

**默认预设 3 种**:
| typeCode | 名称 | 类型 | 配置 |
|----------|------|------|------|
| REWARD | 消费积分 | 兑换 | 可见, 有效期1自然年 |
| TIER | 等级成长值 | 等级 | 可见, 有效期0(永久) |
| CREDIT | 授信积分 | 信用 | 额度5000分 |

**交互**:
- 名称/代码: ClickToEditText（点击编辑）
- 兑换/等级/信用: `<select>` 下拉
- 有效期值/额度: ClickToEdit（点击编辑数值）
- 有效期模式: ClickToEditSelect（点击编辑下拉）
- 负分/可见: Toggle 开关
- 删除: `×` 按钮
- 添加: `+ 添加积分类型` 文字链接（灰色虚线，hover 变黑，点击新增一行）

### 4.4 Step 3 — 等级设置

**行结构(单行 flex，与 Step 2 一致)**:
```
[代码] [名称] [关联积分▼] 成长值 [min] — [max] 有效期 [val] [模式] 顺序 N [×]
```

**关联积分下拉**: 仅显示 `tierRelevant=true` 的积分类型（等级成长值、等级评定积分）

**默认预设 4 个等级**: BASE → SILVER → GOLD → PLATINUM，关联 TIER 类型

**交互**: 全部使用 ClickToEdit/ClickToEditSelect，与 Step 2 一致

### 4.5 完成后

```
        ✅ 基础设置完成！
   俱乐部、积分类型、等级已配置完毕
        [进入仪表盘 →]
```

---

## 五、登录页面

```
        [Logo SVG]
    忠诚度管理平台
  Loyalty SaaS Platform

  [👤 用户名        ]
  [🔑 密码          ]

      [🔒 登 录]

    默认账号: admin / admin123
```

- 全屏居中，纯白底
- 自定义 SVG 图标 (锁、用户、钥匙)
- 输入框: 灰色底 `#fafafa`，focus 白底 + 黑边框
- 按钮: 黑底白字 44px 高
- 当前为开发模式，点击直接登录

---

## 六、DB Schema 设计器 (DrawDB 集成)

### 6.1 集成方式

- 使用 iframe 嵌入 DrawDB 独立应用 (端口 5174)
- 无 sandbox 限制，允许完整交互
- 提供云端/本地模式切换

### 6.2 DrawDB 能力

- 拖拽建表、字段类型配置
- 表关联关系（1:1/1:N/N:M）可视化
- SQL 导出（MySQL/PostgreSQL/SQLite/MariaDB/SQL Server/Oracle）
- JSON 导出（完整 schema: tables, relationships, notes, areas）
- DBML/Mermaid/Documentation 导出
- PNG/SVG 图片导出

### 6.3 嵌入组件结构

```
┌─────────────────────────────────────────────┐
│ 数据库 Schema 设计器  DrawDB                 │
│ JSON SQL DBML PNG/SVG    [云端/本地] [全屏]  │
├─────────────────────────────────────────────┤
│                                             │
│          DrawDB iframe (100% 高度)           │
│                                             │
└─────────────────────────────────────────────┘
```

---

## 七、表单 Schema 设计器

### 7.1 实体选择器

```
实体: [会员] [交易] [+ 添加实体]
```

- 点击标签切换实体
- 点击 `+ 添加实体` 输入新实体名称
- 每个实体独立 Schema 存储

### 7.2 布局（三栏）

```
┌──────┬────────────────────────────┬──────┐
│组件  │         设计画布            │ 属性 │
│面板  │    ┌──────────────────┐    │ 面板 │
│      │    │  字段1: Input    │    │      │
│Input │    │  字段2: Select   │    │      │
│Select│    └──────────────────┘    │      │
│...   │                            │      │
└──────┴────────────────────────────┴──────┘
```

- 左栏: 组件面板 (Card, 200px)
- 中栏: 设计画布 (拖拽区域, dashed border)
- 字段卡片: 名称 + 类型标签 + 配置/删除按钮
- 展开配置区: 标题、必填开关、联动表达式

---

## 八、路由与状态管理

### 8.1 React Router v6

- 使用 `createBrowserRouter` + `RouterProvider`
- 登录/引导页为独立路由（无 AppShell）
- 业务页面为 AppShell 子路由

### 8.2 Zustand Store

```typescript
interface AppStore {
  currentProgramCode: string;     // 当前 Program
  programs: Program[];            // Program 列表
  user: UserInfo | null;         // 用户信息
  permissions: string[];         // 权限列表
  online: boolean;               // 网络状态
}
```

### 8.3 API 层

- axios 实例，baseURL: `/api`
- 请求拦截器自动注入 `X-Program-Code`、`X-Trace-Id`
- `X-Program-Code` 从 Zustand store 读取

---

## 九、页面通用规范

### 9.1 表格行

```css
padding: 10px 0;
border-bottom: 1px solid #e0e0e0;
display: flex; align-items: center; gap: 10px;
overflow-x: auto;  /* 内容超出时横向滚动 */
```

- 所有列使用 `flexShrink: 0` 防止压缩
- 删除按钮: `marginLeft: auto` 推到最右
- `×` 按钮: 灰色 `#ccc`，无背景无边框

### 9.2 卡片容器

```css
border: 1px solid #e8e8e8;
border-radius: 12px;
padding: 24px 28px;
background: #fff;
maxHeight: calc(100vh - 280px);
overflow: auto;
```

### 9.3 标题栏

```css
font-size: 19px; font-weight: 600; color: #1a1a1a;
display: flex; align-items: center; gap: 10px;
/* 右侧操作: marginLeft: 'auto' */
```

- `<< 上一步` 文字链接: 13px `#999`，hover `#1a1a1a`，`marginLeft: 'auto'`

### 9.4 表单区

```css
margin-top: 20px; padding-top: 16px;
border-top: 1px solid #f0f0f0;
```

### 9.5 添加文字链接

```css
font-size: 13px; color: #999; cursor: pointer;
border-bottom: 1px dashed #d9d9d9; padding: 2px 0;
/* hover: color → #1a1a1a */
```

---

## 十、关键设计决策

| 决策 | 说明 |
|------|------|
| **纯白底色** | 全站统一 `#fff`，无灰色背景区 |
| **黑线条风格** | 按钮/图标/边框使用 `#1a1a1a` 黑线条 |
| **点击编辑** | 列表项默认显示文字，点击才出现输入框，减少视觉噪音 |
| **单行布局** | 列表行所有元素在一个 flex 行内，不换行 |
| **独立引导页** | 引导流程全屏无菜单，用户完成设置后才看到管理后台 |
| **DrawDB iframe** | 直接嵌入 DrawDB 独立应用，非组件化改写 |