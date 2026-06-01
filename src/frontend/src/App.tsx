import React, { useState } from 'react';
import { ConfigProvider, Layout, Tabs, theme } from 'antd';
import { BuildOutlined, FormOutlined, CodeOutlined, PartitionOutlined } from '@ant-design/icons';
import SchemaBuilder from './components/SchemaBuilder/SchemaBuilder';
import DynamicRenderer from './components/DynamicRenderer/DynamicRenderer';
import ScriptingWorkbench from './components/ScriptingWorkbench/ScriptingWorkbench';
import EntityModeler from './components/EntityModeler/EntityModeler';

const { Header, Content } = Layout;

const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState('builder');
  const { token } = theme.useToken();

  const tabItems = [
    { key: 'entity-modeler', label: '实体建模器', icon: <PartitionOutlined /> },
    { key: 'builder', label: '表单设计器', icon: <BuildOutlined /> },
    { key: 'renderer', label: '动态渲染器', icon: <FormOutlined /> },
    { key: 'scripting', label: '渠道脚本工作台', icon: <CodeOutlined /> },
  ];

  return (
    <ConfigProvider theme={{ algorithm: theme.defaultAlgorithm }}>
      <Layout style={{ minHeight: '100vh', background: token.colorBgLayout }}>
        <Header style={{
          background: token.colorBgContainer,
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
          display: 'flex', alignItems: 'center', padding: '0 24px',
        }}>
          <h1 style={{ margin: 0, fontSize: 18, fontWeight: 600 }}>
            Loyalty SaaS Admin
          </h1>
          <span style={{ marginLeft: 12, fontSize: 12, color: token.colorTextTertiary }}>
            Schema-Driven UI 管理后台
          </span>
        </Header>
        <Content style={{ padding: 0 }}>
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            tabBarStyle={{ paddingLeft: 24, background: token.colorBgContainer, marginBottom: 0 }}
            items={tabItems.map((item) => ({
              key: item.key,
              label: <span>{item.icon} {item.label}</span>,
            }))}
          />
          <div style={{ height: 'calc(100vh - 118px)', overflow: 'auto' }}>
            {activeTab === 'entity-modeler' && <EntityModeler />}
            {activeTab === 'builder' && (
              <SchemaBuilder
                entityType="MEMBER"
                onSave={(schema) => console.log('[App] Schema saved:', schema)}
              />
            )}
            {activeTab === 'renderer' && (
              <div style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
                <DynamicRenderer
                  programCode="PROG001"
                  memberId={8821}
                />
              </div>
            )}
            {activeTab === 'scripting' && (
              <ScriptingWorkbench
                defaultChannel="TMALL"
                onSaveScript={(channel, code) =>
                  console.log('[App] Script saved for channel:', channel, code.length, 'chars')
                }
              />
            )}
          </div>
        </Content>
      </Layout>
    </ConfigProvider>
  );
};

export default App;