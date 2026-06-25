import React, { useState, useEffect, useCallback } from 'react';
import { Table, Tag, Button, Space, Tabs, Typography, message, Popconfirm, Tooltip } from 'antd';
import {
  PlusOutlined, EditOutlined,
  PauseCircleOutlined, PlayCircleOutlined, CrownOutlined,
  SettingOutlined, ThunderboltOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import PageWrapper from '../components/PageWrapper';
import api from '../api';

const { Title, Text } = Typography;

const TierRuleList: React.FC = () => {
  const navigate = useNavigate();
  const [rules, setRules] = useState<any[]>([]);
  const [tierRules, setTierRules] = useState<any[]>([]);
  const [activities, setActivities] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<string>('tier_points');

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const { data } = await api.get('/admin/rules');
      const all = data?.data || [];
      // 等级积分规则：tier 类型的积分规则
      setRules(all.filter((r: any) => {
        const meta = r.metadata ? (typeof r.metadata === 'string' ? JSON.parse(r.metadata) : r.metadata) : null;
        return meta?.ruleType === 'tier' || r.rule_category === 'tier';
      }));
      // 等级规则：升级/保级规则
      setTierRules(all.filter((r: any) => {
        const meta = r.metadata ? (typeof r.metadata === 'string' ? JSON.parse(r.metadata) : r.metadata) : null;
        return meta?.rule_purpose === 'TIER_UPGRADE' || meta?.rule_purpose === 'TIER_RETENTION';
      }));
    } catch (e: any) {
      setError(e.message || '加载失败');
    }
    // 加载等级活动
    try {
      const { data: ad } = await api.get('/admin/tier-activities');
      setActivities(ad?.data?.activities || []);
    } catch { setActivities([]); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { fetchData(); }, [fetchData]);

  const handleToggleStatus = async (ruleId: number, currentStatus: string) => {
    const newStatus = currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    try {
      await api.put(`/admin/rules/${ruleId}`, { status: newStatus });
      message.success(`已${newStatus === 'ACTIVE' ? '启用' : '停用'}`);
      fetchData();
    } catch (e: any) { message.error(e.response?.data?.message || '操作失败'); }
  };

  const ruleColumns = [
    { title: '名称', dataIndex: 'rule_name', width: 160 },
    { title: '代码', dataIndex: 'rule_code', width: 120 },
    {
      title: '触发条件', key: 'conditions', width: 260,
      render: (_: any, r: any) => {
        const meta = r.metadata ? (typeof r.metadata === 'string' ? JSON.parse(r.metadata) : r.metadata) : null;
        if (!meta?.extConditions?.length) return <Text type="secondary">-</Text>;
        return <Text style={{ fontSize: 12 }}>{meta.extConditions.filter((c: any) => c.field).map((c: any) => `${c.entity}:${c.field} ${c.op} ${c.value}`).join(', ')}</Text>;
      },
    },
    { title: '状态', dataIndex: 'status', width: 80, render: (v: string) => <Tag color={v === 'ACTIVE' ? 'green' : v === 'DRAFT' ? 'orange' : 'default'}>{v}</Tag> },
    { title: '更新时间', dataIndex: 'updated_at', width: 140 },
    {
      title: '操作', key: 'actions', width: 180,
      render: (_: any, record: any) => (
        <Space>
          <Tooltip title="编辑"><Button size="small" icon={<EditOutlined style={{ color: '#595959' }} />} onClick={() => navigate(`/rules/${record.id}/edit?type=tier`)} /></Tooltip>
          <Popconfirm title={`确定${record.status === 'ACTIVE' ? '停用' : '启用'}?`} onConfirm={() => handleToggleStatus(record.id, record.status)}>
            <Button size="small" icon={record.status === 'ACTIVE' ? <PauseCircleOutlined /> : <PlayCircleOutlined />}
              danger={record.status === 'ACTIVE'}>{record.status === 'ACTIVE' ? '停用' : '启用'}</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const activityColumns = [
    { title: '活动代码', dataIndex: 'activityCode', width: 140 },
    { title: '活动名称', dataIndex: 'activityName', width: 180 },
    { title: '目标等级', dataIndex: 'targetTierCode', width: 100, render: (v: string) => <Tag color="gold">{v}</Tag> },
    { title: '触发方式', dataIndex: 'triggerType', width: 100, render: (v: string) => ({ PAYMENT: '支付直升', TASK: '任务直升', MANUAL: '手动', EVENT: '事件' }[v] || v) },
    { title: '状态', dataIndex: 'status', width: 80, render: (v: string) => <Tag color={v === 'ACTIVE' ? 'green' : 'default'}>{v}</Tag> },
    { title: '有效期', key: 'valid', width: 200, render: (_: any, r: any) => <Text style={{ fontSize: 12 }}>{r.validStartTime?.substring(0, 10)} ~ {r.validEndTime?.substring(0, 10) || '永久'}</Text> },
  ];

  const tabItems = [
    {
      key: 'tier_points',
      label: <Space><SettingOutlined />等级积分规则</Space>,
      children: (
        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>等级积分（TIER）发放规则，与消费积分规则独立配置</Text>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/rules/new?type=tier')}>
              新建等级积分规则
            </Button>
          </div>
          <Table dataSource={rules} columns={ruleColumns} loading={loading} rowKey="id"
            size="small" pagination={{ pageSize: 10 }}
            locale={{ emptyText: '尚未配置等级积分规则' }} />
        </div>
      ),
    },
    {
      key: 'tier_rules',
      label: <Space><CrownOutlined />等级规则</Space>,
      children: (
        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>等级升级/降级/保级规则配置，支持多维度评估条件</Text>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/rules/tier/config')}>
              配置等级规则
            </Button>
          </div>
          <Table dataSource={tierRules} columns={ruleColumns} loading={loading} rowKey="id"
            size="small" pagination={{ pageSize: 10 }}
            locale={{ emptyText: '尚未配置等级规则' }} />
        </div>
      ),
    },
    {
      key: 'tier_activities',
      label: <Space><ThunderboltOutlined />等级活动</Space>,
      children: (
        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>等级直升活动管理，支持支付直升、任务直升等场景</Text>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/rules/tier/activity/new')}>
              新建等级活动
            </Button>
          </div>
          <Table dataSource={activities} columns={activityColumns} loading={loading} rowKey="activityCode"
            size="small" pagination={{ pageSize: 10 }}
            locale={{ emptyText: '暂无等级活动' }} />
        </div>
      ),
    },
  ];

  return (
    <PageWrapper loading={loading} error={error} onRetry={fetchData}>
      <Title level={4} style={{ marginBottom: 16 }}>等级规则管理</Title>
      <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabItems} />
    </PageWrapper>
  );
};

export default TierRuleList;