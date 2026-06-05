import React, { useState, useEffect, useCallback } from 'react';
import { Table, Button, Input, Space, Tag, message, Popconfirm, Typography } from 'antd';
import { PlusOutlined, SearchOutlined, EditOutlined, CopyOutlined, DeleteOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import PageWrapper from '../components/PageWrapper';
import { useAppStore } from '../store';
import api from '../api';

const { Title } = Typography;

const ProgramList: React.FC = () => {
  const navigate = useNavigate();
  const currentProgramCode = useAppStore(s => s.currentProgramCode);

  const [programs, setPrograms] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState('');

  const fetchPrograms = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const { data } = await api.get('/admin/programs', { params: { q: search } });
      setPrograms(data?.data || []);
    } catch (e: any) {
      setError(e.message || '加载失败');
      setPrograms([]);
    } finally {
      setLoading(false);
    }
  }, [search]);

  useEffect(() => { fetchPrograms(); }, [fetchPrograms]);

  const handleDelete = async (programCode: string) => {
    try {
      await api.delete(`/admin/programs/${programCode}`);
      message.success('已删除');
      fetchPrograms();
    } catch (e: any) {
      message.error(e.response?.data?.message || '删除失败');
    }
  };

  const handleCopy = async (programCode: string) => {
    try {
      await api.post(`/admin/programs/${programCode}/copy`);
      message.success('已复制');
      fetchPrograms();
    } catch (e: any) {
      message.error(e.response?.data?.message || '复制失败');
    }
  };

  const columns = [
    { title: '俱乐部代码', dataIndex: 'programCode', width: 150, render: (v: string) => <Tag color="blue">{v}</Tag> },
    { title: '名称', dataIndex: 'displayName', width: 150 },
    { title: '状态', dataIndex: 'status', width: 80, render: (v: string) => <Tag color={v === 'ACTIVE' ? 'green' : 'default'}>{v}</Tag> },
    { title: '会员数', dataIndex: 'memberCount', width: 80 },
    { title: '创建时间', dataIndex: 'createdAt', width: 120 },
    {
      title: '操作', key: 'actions', width: 200,
      render: (_: any, record: any) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => navigate(`/programs/${record.programCode}/edit`)}>编辑</Button>
          <Button size="small" icon={<CopyOutlined />} onClick={() => handleCopy(record.programCode)}>复制</Button>
          <Popconfirm title="确定删除此俱乐部?" onConfirm={() => handleDelete(record.programCode)}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <PageWrapper loading={loading} error={error} onRetry={fetchPrograms}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>俱乐部</Title>
        <Space>
          <Input.Search
            placeholder="搜索俱乐部"
            value={search}
            onChange={e => setSearch(e.target.value)}
            onSearch={fetchPrograms}
            style={{ width: 250 }}
            enterButton={<SearchOutlined />}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/programs/new')}>
            新建俱乐部
          </Button>
        </Space>
      </div>
      <Table dataSource={programs} columns={columns} loading={loading} rowKey="programCode"
        size="small" pagination={{ pageSize: 20 }}
        locale={{ emptyText: '暂无俱乐部' }} />
    </PageWrapper>
  );
};

export default ProgramList;