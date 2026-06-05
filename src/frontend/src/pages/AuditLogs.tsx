import React, { useState, useEffect, useCallback } from 'react';
import { Table, Tag, Card, Select, Button, Space } from 'antd';
import { ReloadOutlined, AuditOutlined } from '@ant-design/icons';
import { useAppStore } from '../store';
import api from '../api';

const AuditLogs: React.FC = () => {
  const [logs, setLogs] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [filter, setFilter] = useState('ALL');

  const fetch = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, any> = { limit: 100 };
      if (filter === 'UNAUTHORIZED_ACCESS') params.type = 'UNAUTHORIZED_ACCESS';
      else if (filter === 'FORCE_OVERRIDE') params.type = 'FORCE_OVERRIDE';

      const { data } = await api.get('/admin/audit/operations', { params });
      setLogs(data?.data || []);
    } catch (e) {
      console.error('[AuditLogs] 加载失败:', e);
      setLogs([]);
    } finally { setLoading(false); }
  }, [filter]);

  useEffect(() => { fetch(); }, [fetch]);

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '操作', dataIndex: 'action', width: 150, render: (v: string) => <Tag color={v?.includes('UNAUTHORIZED') ? 'red' : 'blue'}>{v}</Tag> },
    { title: '请求ID', dataIndex: 'request_id', width: 150 },
    { title: '租户', dataIndex: 'program_code', width: 100, render: (v: string) => <Tag>{v}</Tag> },
    { title: '时间', dataIndex: 'created_at', width: 160 },
  ];

  return (
    <Card title={<span><AuditOutlined /> 审计日志</span>} extra={
      <Space>
        <Select value={filter} onChange={setFilter} style={{ width: 150 }}
          options={[{label:'全部',value:'ALL'},{label:'越权访问',value:'UNAUTHORIZED_ACCESS'},{label:'强制放行',value:'FORCE_OVERRIDE'}]} />
        <Button icon={<ReloadOutlined />} onClick={fetch}>刷新</Button>
      </Space>
    }>
      <Table dataSource={logs} columns={columns} loading={loading} rowKey="id" size="small" pagination={{ pageSize: 30 }} />
    </Card>
  );
};

export default AuditLogs;