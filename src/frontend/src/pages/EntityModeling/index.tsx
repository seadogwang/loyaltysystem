import React from 'react';
import { Button, Space, Typography, Select } from 'antd';
import { SendOutlined } from '@ant-design/icons';
import PageWrapper from '../../../components/PageWrapper';
import EntityModelingCanvas from './EntityModelingCanvas';
import { useModelingStore } from './store';

const { Title } = Typography;

const EntityModeling: React.FC = () => {
  const store = useModelingStore();

  return (
    <PageWrapper>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
        <Title level={4} style={{ margin: 0 }}>统一实体建模与映射</Title>
        <Space>
          <Button size="small" onClick={() => store.toggleSnap()}>
            吸附: {store.snapToGrid ? 'ON' : 'OFF'}
          </Button>
          <Button size="small" onClick={() => store.toggleMiniMap()}>
            小地图: {store.showMiniMap ? 'ON' : 'OFF'}
          </Button>
          <Button size="small" onClick={() => store.toggleSidebar()}>
            面板: {store.sidebarCollapsed ? 'OFF' : 'ON'}
          </Button>
          <Select size="small" value={store.filterCategory} onChange={store.setFilterCategory} style={{ width: 110 }}
            options={[
              { label: '全部', value: 'ALL' },
              { label: '业务实体', value: 'BUSINESS' },
              { label: '系统实体', value: 'SYSTEM' },
              { label: 'API实体', value: 'API_REQUEST' },
            ]} />
          <Button type="primary" size="small" icon={<SendOutlined />}>发布</Button>
        </Space>
      </div>

      <EntityModelingCanvas />
    </PageWrapper>
  );
};

export default EntityModeling;