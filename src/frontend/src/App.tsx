import React, { useState } from 'react';
import { ConfigProvider, Layout, Menu, theme, Typography } from 'antd';
import {
  PartitionOutlined, BuildOutlined, FormOutlined, CodeOutlined,
  TeamOutlined, DollarOutlined, CrownOutlined, ThunderboltOutlined,
  ApiOutlined, InboxOutlined, AuditOutlined, DashboardOutlined,
  SettingOutlined, BellOutlined, HistoryOutlined,
} from '@ant-design/icons';
import EntityModeler from './components/EntityModeler/EntityModeler';
import SchemaBuilder from './components/SchemaBuilder/SchemaBuilder';
import DynamicRenderer from './components/DynamicRenderer/DynamicRenderer';
import ScriptingWorkbench from './components/ScriptingWorkbench/ScriptingWorkbench';

const { Sider, Content } = Layout;
const { Text } = Typography;

interface MenuItem {
  key: string;
  icon: React.ReactNode;
  label: string;
  children?: MenuItem[];
}

const menuItems: MenuItem[] = [
  {
    key: 'modeling', icon: <DashboardOutlined />, label: '数据建模',
    children: [
      { key: 'entity-modeler', icon: <PartitionOutlined />, label: '实体建模器' },
      { key: 'form-designer', icon: <BuildOutlined />, label: '表单设计器' },
    ],
  },
  {
    key: 'member', icon: <TeamOutlined />, label: '会员管理',
    children: [
      { key: 'member-list', icon: <TeamOutlined />, label: '会员列表' },
      { key: 'dynamic-renderer', icon: <FormOutlined />, label: '会员详情/编辑' },
    ],
  },
  {
    key: 'points', icon: <DollarOutlined />, label: '积分管理',
    children: [
      { key: 'points-grant', icon: <DollarOutlined />, label: '积分发放' },
      { key: 'points-redeem', icon: <ThunderboltOutlined />, label: '积分核销' },
      { key: 'points-history', icon: <HistoryOutlined />, label: '流水查询' },
    ],
  },
  {
    key: 'tier-rule', icon: <CrownOutlined />, label: '等级与规则',
    children: [
      { key: 'tier-config', icon: <CrownOutlined />, label: '等级阶梯配置' },
      { key: 'rule-management', icon: <SettingOutlined />, label: '规则管理' },
    ],
  },
  {
    key: 'channel', icon: <ApiOutlined />, label: '渠道集成',
    children: [
      { key: 'channel-config', icon: <ApiOutlined />, label: '渠道配置' },
      { key: 'scripting', icon: <CodeOutlined />, label: '脚本工作台' },
    ],
  },
  {
    key: 'ops', icon: <InboxOutlined />, label: '运维监控',
    children: [
      { key: 'event-inbox', icon: <InboxOutlined />, label: '事件收件箱' },
      { key: 'audit-logs', icon: <AuditOutlined />, label: '审计日志' },
      { key: 'notification', icon: <BellOutlined />, label: '通知管理' },
    ],
  },
];

type PageKey = 'entity-modeler' | 'form-designer' | 'member-list' | 'dynamic-renderer' |
  'points-grant' | 'points-redeem' | 'points-history' | 'tier-config' | 'rule-management' |
  'channel-config' | 'scripting' | 'event-inbox' | 'audit-logs' | 'notification';

const PAGE_TITLES: Record<PageKey, string> = {
  'entity-modeler': '实体建模器', 'form-designer': '表单设计器',
  'member-list': '会员管理', 'dynamic-renderer': '会员详情',
  'points-grant': '积分发放', 'points-redeem': '积分核销', 'points-history': '流水查询',
  'tier-config': '等级阶梯配置', 'rule-management': '规则管理',
  'channel-config': '渠道配置', 'scripting': '脚本工作台',
  'event-inbox': '事件收件箱', 'audit-logs': '审计日志', 'notification': '通知管理',
};

const App: React.FC = () => {
  const [activePage, setActivePage] = useState<PageKey>('member-list');
  const [collapsed, setCollapsed] = useState(false);
  const { token } = theme.useToken();

  const renderPage = () => {
    switch (activePage) {
      case 'entity-modeler': return <EntityModeler />;
      case 'form-designer': return <SchemaBuilder entityType="MEMBER" />;
      case 'dynamic-renderer': return <div style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
        <DynamicRenderer programCode="PROG001" memberId={8821} />
      </div>;
      case 'scripting': return <ScriptingWorkbench defaultChannel="TMALL" />;

      // ---- 其他页面：后端 API 全部就绪，前端待实现 ----
      case 'member-list':    return <PlaceholderPage title="会员管理" desc="会员搜索、列表、新建、编辑、合并、禁用" icon={<TeamOutlined />} />;
      case 'points-grant':   return <PlaceholderPage title="积分发放" desc="手动发分：选择会员 → 选择积分类型 → 输入金额 → 提交（走瀑布流冲抵引擎）" icon={<DollarOutlined />} />;
      case 'points-redeem':  return <PlaceholderPage title="积分核销" desc="手动核销：选择会员 → 输入金额 → 系统 FIFO 扣减 + 生成 RedemptionAllocation" icon={<ThunderboltOutlined />} />;
      case 'points-history': return <PlaceholderPage title="流水查询" desc="按会员/时间/交易类型查询 account_transaction 流水" icon={<HistoryOutlined />} />;
      case 'tier-config':    return <PlaceholderPage title="等级阶梯配置" desc="配置各等级升级/保级条件（tier_definition 表）" icon={<CrownOutlined />} />;
      case 'rule-management': return <PlaceholderPage title="规则管理" desc="DRL 规则列表、新建、编辑、发布、沙箱回归测试、AI 生成" icon={<SettingOutlined />} />;
      case 'channel-config': return <PlaceholderPage title="渠道配置" desc="天猫/京东/抖音/微信小程序适配器配置（auth_config/request_mapping）" icon={<ApiOutlined />} />;
      case 'event-inbox':    return <PlaceholderPage title="事件收件箱" desc="监控 event_inbox 状态、死信重放、手动重试" icon={<InboxOutlined />} />;
      case 'audit-logs':     return <PlaceholderPage title="审计日志" desc="越权访问记录、操作审计、租户污染检测" icon={<AuditOutlined />} />;
      case 'notification':   return <PlaceholderPage title="通知管理" desc="短信/微信模板配置、发送记录、重试" icon={<BellOutlined />} />;
      default: return null;
    }
  };

  return (
    <ConfigProvider theme={{ algorithm: theme.defaultAlgorithm }}>
      <Layout style={{ minHeight: '100vh' }}>
        <Sider collapsible collapsed={collapsed} onCollapse={setCollapsed}
          style={{ background: token.colorBgContainer }} width={220}>
          <div style={{ padding: '16px', textAlign: 'center', borderBottom: `1px solid ${token.colorBorderSecondary}` }}>
            <Text strong style={{ fontSize: collapsed ? 12 : 15 }}>Loyalty SaaS</Text>
          </div>
          <Menu
            mode="inline"
            selectedKeys={[activePage]}
            defaultOpenKeys={['member', 'points', 'ops']}
            onClick={({ key }) => setActivePage(key as PageKey)}
            items={menuItems.map(group => ({
              key: group.key,
              icon: group.icon,
              label: group.label,
              children: group.children?.map(item => ({
                key: item.key,
                icon: item.icon,
                label: item.label,
              })),
            }))}
            style={{ borderRight: 0 }}
          />
        </Sider>
        <Layout>
          <Content style={{ padding: 24, background: token.colorBgLayout, minHeight: '100vh' }}>
            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 20, fontWeight: 600 }}>
                {PAGE_TITLES[activePage]}
              </Text>
            </div>
            {renderPage()}
          </Content>
        </Layout>
      </Layout>
    </ConfigProvider>
  );
};

/** 占位页面 — 后端 API 已就绪，前端待实现 */
const PlaceholderPage: React.FC<{ title: string; desc: string; icon: React.ReactNode }> = ({ title, desc, icon }) => (
  <div style={{
    background: '#fff', borderRadius: 8, padding: 60, textAlign: 'center',
    border: '1px solid #e8e8e8',
  }}>
    <div style={{ fontSize: 48, color: '#d9d9d9', marginBottom: 16 }}>{icon}</div>
    <h2 style={{ marginBottom: 8 }}>{title}</h2>
    <p style={{ color: '#999', fontSize: 14 }}>{desc}</p>
    <p style={{ color: '#bbb', fontSize: 12, marginTop: 16 }}>
      ✅ 后端 API 已完成&nbsp;&nbsp;|&nbsp;&nbsp;⏳ 前端页面待实现
    </p>
  </div>
);

export default App;