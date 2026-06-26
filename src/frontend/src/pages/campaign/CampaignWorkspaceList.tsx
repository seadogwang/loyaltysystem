import React, { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card, Table, Button, Space, Tag, Input, Select, Modal, Form, message, Typography, Tooltip,
} from 'antd';
import {
  PlusOutlined, SearchOutlined, RightOutlined, EditOutlined,
  ArchiveOutlined, HistoryOutlined,
} from '@ant-design/icons';
import {
  listWorkspaces, createWorkspace, archiveWorkspace, CampaignWorkspace,
} from '../../api/campaign';

const { Text, Title } = Typography;

const CampaignWorkspaceList: React.FC = () => {
  const navigate = useNavigate();
  const [workspaces, setWorkspaces] = useState<CampaignWorkspace[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [programFilter, setProgramFilter] = useState<string>('all');
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createLoading, setCreateLoading] = useState(false);
  const [form] = Form.useForm();

  const fetchWorkspaces = useCallback(async () => {
    setLoading(true);
    try {
      const data = await listWorkspaces();
      setWorkspaces(data || []);
    } catch (err: any) {
      message.error('加载工作区列表失败: ' + (err?.response?.data?.message || err.message));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchWorkspaces();
  }, [fetchWorkspaces]);

  const handleCreate = async (values: any) => {
    setCreateLoading(true);
    try {
      await createWorkspace({
        name: values.name,
        programCode: values.programCode,
        description: values.description,
        config: {
          timezone: values.timezone || 'Asia/Shanghai',
          defaultBudget: values.defaultBudget || 0,
        },
      });
      message.success('工作区创建成功');
      setCreateModalOpen(false);
      form.resetFields();
      fetchWorkspaces();
    } catch (err: any) {
      message.error('创建失败: ' + (err?.response?.data?.message || err.message));
    } finally {
      setCreateLoading(false);
    }
  };

  const handleArchive = async (workspace: CampaignWorkspace) => {
    Modal.confirm({
      title: '确认归档',
      content: `确定要归档工作区「${workspace.name}」吗？`,
      okText: '确认归档',
      cancelText: '取消',
      onOk: async () => {
        try {
          await archiveWorkspace(workspace.id);
          message.success('归档成功');
          fetchWorkspaces();
        } catch (err: any) {
          message.error('归档失败: ' + (err?.response?.data?.message || err.message));
        }
      },
    });
  };

  const getStatusTag = (status: string) => {
    const map: Record<string, { color: string; text: string }> = {
      ACTIVE: { color: 'green', text: '● ACTIVE' },
      ARCHIVED: { color: 'default', text: '○ ARCHIVED' },
      LOCKED: { color: 'orange', text: '● LOCKED' },
    };
    const s = map[status] || { color: 'default', text: status };
    return <Tag color={s.color}>{s.text}</Tag>;
  };

  const filtered = workspaces.filter(w => {
    const matchSearch = !searchText ||
      w.name.toLowerCase().includes(searchText.toLowerCase()) ||
      w.programCode.toLowerCase().includes(searchText.toLowerCase());
    const matchProgram = programFilter === 'all' || w.programCode === programFilter;
    return matchSearch && matchProgram;
  });

  const programOptions = Array.from(new Set(workspaces.map(w => w.programCode)));

  const columns = [
    {
      title: '工作区',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: CampaignWorkspace) => (
        <Space>
          <Text strong>{name}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>({record.programCode})</Text>
        </Space>
      ),
    },
    {
      title: 'Program',
      dataIndex: 'programCode',
      key: 'programCode',
      width: 120,
    },
    {
      title: '当前目标',
      key: 'activeGoal',
      width: 150,
      render: (_: any, record: CampaignWorkspace) => (
        <Text type={record.activeGoalId ? undefined : 'secondary'}>
          {record.activeGoalId ? '已设置' : '未设置'}
        </Text>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: string) => getStatusTag(status),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (t: string) => new Date(t).toLocaleString('zh-CN'),
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_: any, record: CampaignWorkspace) => (
        <Space>
          <Button type="primary" size="small" icon={<RightOutlined />}
            onClick={() => navigate(`/campaign/workspace/${record.id}`)}>
            进入
          </Button>
          {record.status === 'ACTIVE' && (
            <>
              <Tooltip title="归档">
                <Button size="small" icon={<ArchiveOutlined />}
                  onClick={() => handleArchive(record)} />
              </Tooltip>
            </>
          )}
          <Tooltip title="快照">
            <Button size="small" icon={<HistoryOutlined />} />
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 24 }}>
          <div>
            <Title level={4} style={{ margin: 0 }}>📊 营销工作区</Title>
            <Text type="secondary">管理营销活动的战略规划与执行</Text>
          </div>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalOpen(true)}>
            新建工作区
          </Button>
        </div>

        <div style={{ display: 'flex', gap: 16, marginBottom: 16 }}>
          <Input
            placeholder="搜索工作区..."
            prefix={<SearchOutlined />}
            value={searchText}
            onChange={e => setSearchText(e.target.value)}
            style={{ width: 300 }}
            allowClear
          />
          <Select
            value={programFilter}
            onChange={setProgramFilter}
            style={{ width: 160 }}
            options={[
              { label: '全部 Program', value: 'all' },
              ...programOptions.map(p => ({ label: p, value: p })),
            ]}
          />
        </div>

        <Table
          dataSource={filtered}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 10, showSizeChanger: true }}
        />
      </Card>

      {/* 新建工作区弹窗 */}
      <Modal
        title="新建工作区"
        open={createModalOpen}
        onCancel={() => setCreateModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={createLoading}
        okText="创建"
      >
        <Form form={form} layout="vertical" onFinish={handleCreate}>
          <Form.Item name="name" label="工作区名称" rules={[{ required: true, message: '请输入工作区名称' }]}>
            <Input placeholder="例如：618大促" />
          </Form.Item>
          <Form.Item name="programCode" label="关联 Program" rules={[{ required: true, message: '请选择 Program' }]}>
            <Select
              placeholder="选择 Program"
              options={[
                { label: 'PROG001', value: 'PROG001' },
                { label: 'BRAND-A', value: 'BRAND-A' },
              ]}
            />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="工作区描述" />
          </Form.Item>
          <Form.Item name="timezone" label="时区" initialValue="Asia/Shanghai">
            <Select
              options={[
                { label: 'Asia/Shanghai (UTC+8)', value: 'Asia/Shanghai' },
                { label: 'America/New_York (UTC-5)', value: 'America/New_York' },
              ]}
            />
          </Form.Item>
          <Form.Item name="defaultBudget" label="默认预算（元）">
            <Input type="number" prefix="¥" placeholder="0" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default CampaignWorkspaceList;
