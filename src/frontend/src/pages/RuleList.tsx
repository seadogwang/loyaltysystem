import React, { useState, useEffect, useCallback } from 'react';
import { Table, Tag, Button, Space, Tree, Card, Typography, message, Popconfirm } from 'antd';
import { PlusOutlined, EditOutlined, ExperimentOutlined, PauseCircleOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import PageWrapper from '../components/PageWrapper';
import api from '../api';

const { Title } = Typography;

interface RuleTreeNode {
  key: string;
  title: string;
  children?: RuleTreeNode[];
}

const RuleList: React.FC = () => {
  const navigate = useNavigate();
  const [rules, setRules] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedGroup, setSelectedGroup] = useState<string | null>(null);
  const [treeData, setTreeData] = useState<RuleTreeNode[]>([]);

  const fetchRules = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const { data } = await api.get('/admin/rules');
      const list = data?.data || [];
      setRules(list);

      // 构建规则树：按 activation_group 分组
      const groups = new Map<string, any[]>();
      list.forEach((r: any) => {
        const g = r.agenda_group || r.activation_group || 'default';
        if (!groups.has(g)) groups.set(g, []);
        groups.get(g)!.push(r);
      });

      const tree: RuleTreeNode[] = [...groups.entries()].map(([g, items]) => ({
        key: g,
        title: `${g} (${items.length})`,
        children: items.map((r: any) => ({
          key: `rule-${r.id}`,
          title: r.rule_name || r.rule_code,
        })),
      }));
      setTreeData(tree);
    } catch (e: any) {
      setError(e.message || '加载失败');
      setRules([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchRules(); }, [fetchRules]);

  const handleToggleStatus = async (ruleId: number, currentStatus: string) => {
    const newStatus = currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    try {
      await api.put(`/admin/rules/${ruleId}`, { status: newStatus });
      message.success(`规则已${newStatus === 'ACTIVE' ? '启用' : '停用'}`);
      fetchRules();
    } catch (e: any) { message.error(e.response?.data?.message || '操作失败'); }
  };

  const onTreeSelect = (keys: React.Key[]) => {
    if (keys.length === 0) { setSelectedGroup(null); return; }
    const key = String(keys[0]);
    if (key.startsWith('rule-')) {
      const ruleId = key.replace('rule-', '');
      navigate(`/rules/${ruleId}/edit`);
    } else {
      setSelectedGroup(key);
    }
  };

  const filteredRules = selectedGroup
    ? rules.filter((r: any) => (r.agenda_group || r.activation_group || 'default') === selectedGroup)
    : rules;

  const columns = [
    { title: '代码', dataIndex: 'rule_code', width: 100 },
    { title: '名称', dataIndex: 'rule_name', width: 150 },
    { title: '类型', dataIndex: 'rule_type', width: 80, render: (v: string) => <Tag>{v}</Tag> },
    { title: '议程组', dataIndex: 'agenda_group', width: 100, render: (v: string) => <Tag color="blue">{v || 'default'}</Tag> },
    { title: '优先级', dataIndex: 'salience', width: 70 },
    { title: '版本', dataIndex: 'version', width: 60 },
    { title: '状态', dataIndex: 'status', width: 80, render: (v: string) => <Tag color={v === 'ACTIVE' ? 'green' : 'default'}>{v}</Tag> },
    { title: '修改时间', dataIndex: 'updated_at', width: 140 },
    {
      title: '操作', key: 'actions', width: 220,
      render: (_: any, record: any) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => navigate(`/rules/${record.id}/edit`)}>编辑</Button>
          <Button size="small" icon={<ExperimentOutlined />} onClick={() => navigate(`/rules/${record.id}/test`)}>沙箱</Button>
          <Popconfirm
            title={`确定${record.status === 'ACTIVE' ? '停用' : '启用'}此规则?`}
            onConfirm={() => handleToggleStatus(record.id, record.status)}
          >
            <Button size="small" icon={record.status === 'ACTIVE' ? <PauseCircleOutlined /> : <PlayCircleOutlined />}
              danger={record.status === 'ACTIVE'}>
              {record.status === 'ACTIVE' ? '停用' : '启用'}
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <PageWrapper loading={loading} error={error} onRetry={fetchRules}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>规则管理</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/rules/new')}>新建规则</Button>
      </div>

      <div style={{ display: 'flex', gap: 16 }}>
        {/* 左侧规则树 */}
        <Card size="small" title="规则分组" style={{ width: 260, flexShrink: 0 }}>
          <Tree
            treeData={treeData}
            onSelect={onTreeSelect}
            defaultExpandAll
            style={{ maxHeight: '60vh', overflow: 'auto' }}
          />
        </Card>

        {/* 右侧表格 */}
        <div style={{ flex: 1 }}>
          <Table dataSource={filteredRules} columns={columns} loading={loading} rowKey="id"
            size="small" pagination={{ pageSize: 20 }} scroll={{ x: 1000 }} />
        </div>
      </div>
    </PageWrapper>
  );
};

export default RuleList;