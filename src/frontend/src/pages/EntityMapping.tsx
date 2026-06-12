import React, { useState, useEffect } from 'react';
import { Table, Tag, Button, Space, Typography, Card, message, Spin, Modal, Form, Input, Select } from 'antd';
import { PlusOutlined, ReloadOutlined, EditOutlined, DeleteOutlined, LinkOutlined } from '@ant-design/icons';
import PageWrapper from '../components/PageWrapper';
import api from '../api';

const { Title, Text } = Typography;

interface ApiEntity {
  id?: number;
  entityType: string;
  direction: 'INBOUND' | 'OUTBOUND';
  targetBusinessEntity: string;
  sourceBusinessEntity: string;
  httpMethod: string;
  httpPath: string;
  authType: string;
  channel: string;
}

const CHANNELS = ['TMALL','JD','DOUYIN','WECHAT_MINI','WEBHOOK'];
const DIRECTIONS = ['INBOUND','OUTBOUND'];
const METHODS = ['GET','POST','PUT','DELETE'];
const AUTH_TYPES = ['NONE','BASIC','BEARER','HMAC-SHA256'];
const BUS_ENTITIES = ['Order','Member','PointTx','TransactionEvent'];

const EntityMapping: React.FC = () => {
  const [entities, setEntities] = useState<ApiEntity[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ApiEntity | null>(null);
  const [form] = Form.useForm();

  const loadEntities = async () => {
    setLoading(true);
    try {
      const { data } = await api.get('/admin/api-entities');
      if (data?.data?.length) {
        setEntities(data.data);
      } else {
        setEntities(getDefaultEntities());
      }
    } catch {
      setEntities(getDefaultEntities());
    }
    setLoading(false);
  };

  const getDefaultEntities = (): ApiEntity[] => [
    { id: 1, entityType: 'TmallOrderResp', direction: 'INBOUND', targetBusinessEntity: 'Order', sourceBusinessEntity: '', httpMethod: 'GET', httpPath: '/trade/simple/get', authType: 'HMAC-SHA256', channel: 'TMALL' },
    { id: 2, entityType: 'JdOrderResp', direction: 'INBOUND', targetBusinessEntity: 'Order', sourceBusinessEntity: '', httpMethod: 'POST', httpPath: '/order/query', authType: 'HMAC-SHA256', channel: 'JD' },
    { id: 3, entityType: 'PointsChangeReq', direction: 'OUTBOUND', targetBusinessEntity: '', sourceBusinessEntity: 'PointTx', httpMethod: 'POST', httpPath: '/api/points/change', authType: 'BEARER', channel: 'WEBHOOK' },
  ];

  useEffect(() => { loadEntities(); }, []);

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      if (editing) {
        await api.put(`/admin/api-entities/${editing.id}`, values);
      } else {
        await api.post('/admin/api-entities', values);
      }
      message.success(editing ? '已更新' : '已创建');
      setModalOpen(false);
      loadEntities();
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error('保存失败');
    }
  };

  const handleDelete = async (id?: number) => {
    if (!id) return;
    try {
      await api.delete(`/admin/api-entities/${id}`);
      message.success('已删除');
      loadEntities();
    } catch { message.error('删除失败'); }
  };

  return (
    <PageWrapper>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>API 实体与映射配置</Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadEntities}>刷新</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditing(null); form.resetFields(); setModalOpen(true); }}>
            新增 API 实体
          </Button>
        </Space>
      </div>

      <Card size="small">
        <Spin spinning={loading}>
          <Table<ApiEntity>
            dataSource={entities}
            rowKey="entityType"
            size="small"
            pagination={false}
            columns={[
              {
                title: 'API 实体', dataIndex: 'entityType', width: 160,
                render: (v: string) => <Text strong code style={{ fontSize: 12 }}>{v}</Text>,
              },
              {
                title: '方向', dataIndex: 'direction', width: 90,
                render: (v: string) => (
                  <Tag color={v === 'INBOUND' ? 'blue' : 'orange'}>{v === 'INBOUND' ? '入站' : '出站'}</Tag>
                ),
              },
              { title: '渠道', dataIndex: 'channel', width: 100,
                render: (v: string) => <Tag>{v}</Tag> },
              {
                title: '目标/源实体', key: 'entity', width: 150,
                render: (_: any, r: ApiEntity) => (
                  <Space size={4}>
                    {r.direction === 'INBOUND' ? (
                      <>
                        <Text type="secondary" style={{ fontSize: 11 }}>→</Text>
                        <Tag color="green">{r.targetBusinessEntity}</Tag>
                      </>
                    ) : (
                      <>
                        <Tag color="blue">{r.sourceBusinessEntity}</Tag>
                        <Text type="secondary" style={{ fontSize: 11 }}>→</Text>
                      </>
                    )}
                  </Space>
                ),
              },
              {
                title: 'HTTP', key: 'http', width: 180,
                render: (_: any, r: ApiEntity) => (
                  <Space size={4}>
                    <Tag color="geekblue" style={{ fontSize: 10 }}>{r.httpMethod}</Tag>
                    <Text code style={{ fontSize: 11 }}>{r.httpPath}</Text>
                  </Space>
                ),
              },
              { title: '认证', dataIndex: 'authType', width: 100,
                render: (v: string) => <Text style={{ fontSize: 11 }}>{v}</Text> },
              {
                title: '操作', key: 'action', width: 120,
                render: (_: any, r: ApiEntity) => (
                  <Space size={2}>
                    <Button size="small" icon={<EditOutlined />}
                      onClick={() => { setEditing(r); form.setFieldsValue(r); setModalOpen(true); }} />
                    <Button size="small" danger icon={<DeleteOutlined />}
                      onClick={() => handleDelete(r.id)} />
                  </Space>
                ),
              },
            ]}
          />
        </Spin>
      </Card>

      <Modal title={editing ? '编辑 API 实体' : '新增 API 实体'} open={modalOpen}
        onOk={handleSave} onCancel={() => setModalOpen(false)} width={560}>
        <Form form={form} layout="vertical" size="small">
          <Form.Item label="实体类型编码" name="entityType" rules={[{ required: true }]}>
            <Input placeholder="例如: TmallOrderResp" />
          </Form.Item>
          <Form.Item label="方向" name="direction" initialValue="INBOUND">
            <Select options={DIRECTIONS.map(d => ({ label: d === 'INBOUND' ? '入站 (INBOUND)' : '出站 (OUTBOUND)', value: d }))} />
          </Form.Item>
          <Form.Item label="渠道" name="channel">
            <Select options={CHANNELS.map(c => ({ label: c, value: c }))} />
          </Form.Item>
          <Form.Item label="目标业务实体（入站）" name="targetBusinessEntity" tooltip="入站时，API响应将映射到此业务实体">
            <Select allowClear options={BUS_ENTITIES.map(e => ({ label: e, value: e }))} />
          </Form.Item>
          <Form.Item label="源业务实体（出站）" name="sourceBusinessEntity" tooltip="出站时，从此业务实体读取数据映射到API请求">
            <Select allowClear options={BUS_ENTITIES.map(e => ({ label: e, value: e }))} />
          </Form.Item>
          <Space>
            <Form.Item label="HTTP 方法" name="httpMethod" initialValue="GET">
              <Select style={{ width: 100 }} options={METHODS.map(m => ({ label: m, value: m }))} />
            </Form.Item>
            <Form.Item label="HTTP 路径" name="httpPath">
              <Input placeholder="/api/..." style={{ width: 200 }} />
            </Form.Item>
          </Space>
          <Form.Item label="认证方式" name="authType" initialValue="NONE">
            <Select options={AUTH_TYPES.map(a => ({ label: a, value: a }))} />
          </Form.Item>
        </Form>
      </Modal>
    </PageWrapper>
  );
};

export default EntityMapping;