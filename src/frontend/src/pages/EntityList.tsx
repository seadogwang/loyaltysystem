import React, { useState, useEffect } from 'react';
import { Table, Tag, Button, Space, Typography, Card, message, Spin } from 'antd';
import { EditOutlined, ReloadOutlined, PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import PageWrapper from '../components/PageWrapper';
import api from '../api';

const { Title, Text } = Typography;

interface EntityInfo {
  entityType: string;
  category: string;
  version: string;
  status: string;
  fieldCount: number;
  description: string;
}

const ENTITY_META: Record<string, { category: string; description: string; icon: string }> = {
  MEMBER:            { category: 'SYSTEM',  description: '会员实体 — 含基础属性与扩展属性', icon: '👤' },
  ORDER:             { category: 'BUSINESS', description: '订单实体 — 交易事件核心数据', icon: '📦' },
  OrderItem:         { category: 'BUSINESS', description: '订单明细 — 订单的子对象', icon: '📋' },
  TRANSACTION_EVENT: { category: 'SYSTEM',  description: '交易事件 — SPI 接入标准载体', icon: '🔄' },
  BEHAVIOR:          { category: 'BUSINESS', description: '行为事件 — 非交易类用户行为', icon: '🎯' },
};

const EntityList: React.FC = () => {
  const navigate = useNavigate();
  const [entities, setEntities] = useState<EntityInfo[]>([]);
  const [loading, setLoading] = useState(false);

  const loadEntities = async () => {
    setLoading(true);
    const entTypes = Object.keys(ENTITY_META);
    const results: EntityInfo[] = [];

    for (const ent of entTypes) {
      try {
        const { data } = await api.get(`/schemas/${ent}`);
        const schema = data?.data?.schema || data?.data;
        const version = data?.data?.version || '—';
        const fields = schema?.properties ? Object.keys(schema.properties).length : 0;

        results.push({
          entityType: ent,
          category: ENTITY_META[ent]?.category || 'BUSINESS',
          version,
          status: version !== '—' ? '已发布' : '未定义',
          fieldCount: fields,
          description: ENTITY_META[ent]?.description || '',
        });
      } catch {
        results.push({
          entityType: ent,
          category: ENTITY_META[ent]?.category || 'BUSINESS',
          version: '—',
          status: '加载失败',
          fieldCount: 0,
          description: ENTITY_META[ent]?.description || '',
        });
      }
    }

    setEntities(results);
    setLoading(false);
  };

  useEffect(() => { loadEntities(); }, []);

  const categoryColor = (cat: string) =>
    cat === 'SYSTEM' ? 'blue' : cat === 'BUSINESS' ? 'green' : 'orange';

  return (
    <PageWrapper>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>实体列表</Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadEntities}>刷新</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/schema-editor')}>
            新建实体 Schema
          </Button>
        </Space>
      </div>

      <Card size="small">
        <Spin spinning={loading}>
          <Table<EntityInfo>
            dataSource={entities}
            rowKey="entityType"
            size="small"
            pagination={false}
            columns={[
              {
                title: '实体类型', dataIndex: 'entityType', width: 200,
                render: (v: string, r: EntityInfo) => (
                  <Space>
                    <Text style={{ fontSize: 16 }}>{ENTITY_META[v]?.icon || '📄'}</Text>
                    <Text strong style={{ fontSize: 13, fontFamily: 'monospace' }}>{v}</Text>
                    <Tag color={categoryColor(r.category)} style={{ fontSize: 10 }}>{r.category}</Tag>
                  </Space>
                ),
              },
              { title: '描述', dataIndex: 'description', ellipsis: true,
                render: (v: string) => <Text type="secondary" style={{ fontSize: 12 }}>{v}</Text> },
              { title: '版本', dataIndex: 'version', width: 80, align: 'center',
                render: (v: string) => <Text code style={{ fontSize: 11 }}>{v}</Text> },
              { title: '字段数', dataIndex: 'fieldCount', width: 70, align: 'center' },
              {
                title: '状态', dataIndex: 'status', width: 90, align: 'center',
                render: (v: string) => {
                  const color = v === '已发布' ? 'green' : v === '未定义' ? 'default' : 'red';
                  return <Tag color={color} style={{ fontSize: 10 }}>{v}</Tag>;
                },
              },
              {
                title: '操作', key: 'action', width: 120,
                render: (_: any, r: EntityInfo) => (
                  <Button size="small" icon={<EditOutlined />}
                    onClick={() => navigate(`/schema-editor?entity=${r.entityType}`)}>
                    编辑
                  </Button>
                ),
              },
            ]}
          />
        </Spin>
      </Card>
    </PageWrapper>
  );
};

export default EntityList;