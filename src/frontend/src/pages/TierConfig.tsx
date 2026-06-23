import React, { useState, useEffect } from 'react';
import { Card, Table, Input, InputNumber, Button, Select, Switch, message, Spin, Space, Typography, Divider, Tag, Modal, Form, Collapse, Popconfirm } from 'antd';
import { PlusOutlined, DeleteOutlined, SaveOutlined, EditOutlined, GiftOutlined, RiseOutlined, FallOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { useAppStore } from '../store';
import api from '../api';

const { Text, Title } = Typography;

interface TierItem {
  tierCode: string;
  tierName: string;
  sequence: number;
  tierLevel: number;
  tierValue: number;
  tierIcon?: string;
  tierBenefits?: Record<string, any>;
  upgradeCriteria?: Record<string, any>;
  downgradeCriteria?: Record<string, any>;
  description?: string;
}

interface TierActivityItem {
  id?: number;
  activityCode: string;
  activityName: string;
  targetTierCode: string;
  triggerType: string;
  triggerConfig: Record<string, any>;
  validStartTime: string;
  validEndTime?: string;
  oncePerMember: boolean;
  memberScope: string;
  status: string;
  description?: string;
}

const TierConfig: React.FC = () => {
  const PROG = useAppStore(s => s.currentProgramCode);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [tiers, setTiers] = useState<TierItem[]>([]);
  const [activities, setActivities] = useState<TierActivityItem[]>([]);
  const [editModal, setEditModal] = useState<{ open: boolean; tier: TierItem | null }>({ open: false, tier: null });
  const [activityModal, setActivityModal] = useState<{ open: boolean; activity: TierActivityItem | null }>({ open: false, activity: null });
  const [activeTab, setActiveTab] = useState<'tiers' | 'activities'>('tiers');
  const [editForm] = Form.useForm();
  const [activityForm] = Form.useForm();

  useEffect(() => { fetchData(); }, [PROG]);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [tierRes, activityRes] = await Promise.all([
        api.get('/admin/tiers'),
        api.get('/admin/tier-activities'),
      ]);
      const td = tierRes.data?.data;
      if (td?.tiers) setTiers(td.tiers);
      const ad = activityRes.data?.data;
      if (ad?.activities) setActivities(ad.activities);
    } catch (e) {
      // 活动接口可能未部署，忽略
    } finally { setLoading(false); }
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      await api.put('/admin/tiers', { tiers });
      message.success('等级配置已保存');
    } catch (e: any) {
      message.error(e.response?.data?.message || '保存失败');
    } finally { setSaving(false); }
  };

  // ====== 等级编辑弹窗 ======
  const openEdit = (tier: TierItem) => {
    setEditModal({ open: true, tier });
    editForm.setFieldsValue({
      tierCode: tier.tierCode,
      tierName: tier.tierName,
      tierLevel: tier.tierLevel ?? tier.sequence,
      tierValue: tier.tierValue ?? 0,
      tierIcon: tier.tierIcon || '',
      tierBenefits: tier.tierBenefits || {},
      upgradeCriteria: tier.upgradeCriteria || {},
      downgradeCriteria: tier.downgradeCriteria || {},
      description: tier.description || '',
    });
  };

  const handleEditSave = () => {
    const values = editForm.getFieldsValue();
    setTiers(prev => prev.map(t => t.tierCode === editModal.tier?.tierCode ? { ...t, ...values } : t));
    setEditModal({ open: false, tier: null });
  };

  // ====== 活动编辑弹窗 ======
  const openActivityEdit = (activity?: TierActivityItem) => {
    setActivityModal({ open: true, activity: activity || null });
    if (activity) {
      activityForm.setFieldsValue(activity);
    } else {
      activityForm.resetFields();
    }
  };

  const handleActivitySave = async () => {
    const values = await activityForm.validateFields();
    try {
      if (activityModal.activity?.id) {
        await api.put(`/admin/tier-activities/${activityModal.activity.activityCode}`, values);
      } else {
        await api.post('/admin/tier-activities', values);
      }
      message.success('活动已保存');
      setActivityModal({ open: false, activity: null });
      fetchData();
    } catch (e: any) {
      if (e?.errorFields) return;
      message.error(e.response?.data?.message || '保存失败');
    }
  };

  const handleActivityPublish = async (activityCode: string) => {
    try {
      await api.post(`/admin/tier-activities/${activityCode}/publish`);
      message.success('活动已发布');
      fetchData();
    } catch (e: any) {
      message.error('发布失败');
    }
  };

  const tierColumns = [
    { title: '排序', dataIndex: 'sequence', width: 60 },
    { title: '等级代码', dataIndex: 'tierCode', width: 100, render: (v: string) => <Tag color="blue">{v}</Tag> },
    { title: '等级名称', dataIndex: 'tierName', width: 120 },
    { title: '等级值', dataIndex: 'tierValue', width: 80, render: (v: number) => v?.toLocaleString() },
    {
      title: '权益', dataIndex: 'tierBenefits', width: 200,
      render: (v: Record<string, any>) => {
        if (!v) return <Text type="secondary">未配置</Text>;
        const items = [];
        if (v.discount) items.push(`${((1 - v.discount) * 100).toFixed(0)}折`);
        if (v.free_shipping) items.push('免运费');
        if (v.birthday_gift) items.push(`生日礼${v.birthday_gift}分`);
        return items.length > 0 ? items.join(' | ') : <Text type="secondary">未配置</Text>;
      },
    },
    {
      title: '操作', width: 80,
      render: (_: any, record: TierItem) => (
        <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
      ),
    },
  ];

  const activityColumns = [
    { title: '活动代码', dataIndex: 'activityCode', width: 120 },
    { title: '活动名称', dataIndex: 'activityName', width: 160 },
    { title: '目标等级', dataIndex: 'targetTierCode', width: 100, render: (v: string) => <Tag color="gold">{v}</Tag> },
    {
      title: '触发方式', dataIndex: 'triggerType', width: 100,
      render: (v: string) => ({ PAYMENT: '支付直升', TASK: '任务直升', MANUAL: '手动', EVENT: '事件' }[v] || v),
    },
    {
      title: '状态', dataIndex: 'status', width: 80,
      render: (v: string) => <Tag color={v === 'ACTIVE' ? 'green' : 'default'}>{{ DRAFT: '草稿', ACTIVE: '生效中', INACTIVE: '已停用' }[v] || v}</Tag>,
    },
    {
      title: '操作', width: 150,
      render: (_: any, record: TierActivityItem) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => openActivityEdit(record)}>编辑</Button>
          {record.status !== 'ACTIVE' && (
            <Popconfirm title="确认发布？" onConfirm={() => handleActivityPublish(record.activityCode)}>
              <Button type="link" size="small">发布</Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Card
        title="等级管理"
        extra={
          <Space>
            <Button size="small" type={activeTab === 'tiers' ? 'primary' : 'default'} onClick={() => setActiveTab('tiers')}>等级列表</Button>
            <Button size="small" type={activeTab === 'activities' ? 'primary' : 'default'} onClick={() => setActiveTab('activities')}>直升活动</Button>
          </Space>
        }
      >
        <Spin spinning={loading}>
          {activeTab === 'tiers' ? (
            <Table
              dataSource={tiers}
              columns={tierColumns}
              rowKey="tierCode"
              pagination={false}
              size="small"
              footer={() => (
                <Space>
                  <Button size="small" icon={<PlusOutlined />} onClick={() => {
                    const newTier: TierItem = {
                      tierCode: `TIER_${Date.now() % 10000}`, tierName: '新等级', sequence: tiers.length + 1,
                      tierLevel: tiers.length, tierValue: 0,
                    };
                    setTiers([...tiers, newTier]); openEdit(newTier);
                  }}>新增等级</Button>
                  <Button type="primary" size="small" icon={<SaveOutlined />} onClick={handleSave} loading={saving}>保存全部</Button>
                </Space>
              )}
            />
          ) : (
            <Table
              dataSource={activities}
              columns={activityColumns}
              rowKey="activityCode"
              pagination={false}
              size="small"
              footer={() => (
                <Button size="small" icon={<PlusOutlined />} onClick={() => openActivityEdit()}>新建直升活动</Button>
              )}
            />
          )}
        </Spin>
      </Card>

      {/* ====== 等级编辑弹窗 ====== */}
      <Modal
        title={`编辑等级：${editModal.tier?.tierName || ''}`}
        open={editModal.open}
        onCancel={() => setEditModal({ open: false, tier: null })}
        onOk={handleEditSave}
        width={640}
        okText="确认"
        cancelText="取消"
      >
        <Form form={editForm} layout="vertical" size="small">
          <Divider plain>基础信息</Divider>
          <Space style={{ display: 'flex' }} size={12}>
            <Form.Item name="tierCode" label="等级代码" rules={[{ required: true }]}>
              <Input style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="tierName" label="等级名称" rules={[{ required: true }]}>
              <Input style={{ width: 140 }} />
            </Form.Item>
            <Form.Item name="tierLevel" label="等级层级">
              <InputNumber style={{ width: 80 }} min={0} />
            </Form.Item>
            <Form.Item name="tierValue" label="等级门槛(分)">
              <InputNumber style={{ width: 120 }} min={0} />
            </Form.Item>
          </Space>
          <Form.Item name="tierIcon" label="等级图标URL">
            <Input placeholder="https://..." />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>

          <Divider plain><GiftOutlined /> 等级权益</Divider>
          <Space style={{ display: 'flex' }} size={12} wrap>
            <Form.Item name={['tierBenefits', 'discount']} label="折扣率">
              <InputNumber min={0} max={1} step={0.01} style={{ width: 100 }} placeholder="0.9=9折" />
            </Form.Item>
            <Form.Item name={['tierBenefits', 'free_shipping']} label="免运费" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name={['tierBenefits', 'birthday_gift']} label="生日礼包(分)">
              <InputNumber style={{ width: 100 }} min={0} />
            </Form.Item>
            <Form.Item name={['tierBenefits', 'priority_service']} label="专属客服" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name={['tierBenefits', 'exclusive_events']} label="专属活动" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>

          <Divider plain><RiseOutlined /> 升级规则</Divider>
          <Space style={{ display: 'flex' }} size={12}>
            <Form.Item name={['upgradeCriteria', 'target_tier']} label="目标等级">
              <Select style={{ width: 140 }} options={tiers.filter(t => t.tierCode !== editModal.tier?.tierCode).map(t => ({ label: t.tierName, value: t.tierCode }))} />
            </Form.Item>
            <Form.Item name={['upgradeCriteria', 'dimension']} label="评估维度">
              <Select style={{ width: 140 }} options={[
                { label: '等级积分', value: 'TIER_POINTS' },
                { label: '购买次数', value: 'ORDER_COUNT' },
                { label: '累计金额', value: 'TOTAL_AMOUNT' },
                { label: '连续活跃', value: 'CONTINUOUS_DAYS' },
              ]} />
            </Form.Item>
            <Form.Item name={['upgradeCriteria', 'required_value']} label="要求值">
              <InputNumber style={{ width: 120 }} min={0} />
            </Form.Item>
          </Space>

          <Divider plain><FallOutlined /> 保级/降级规则</Divider>
          <Space style={{ display: 'flex' }} size={12}>
            <Form.Item name={['downgradeCriteria', 'dimension']} label="评估维度">
              <Select style={{ width: 140 }} options={[
                { label: '等级积分', value: 'TIER_POINTS' },
                { label: '购买次数', value: 'ORDER_COUNT' },
                { label: '累计金额', value: 'TOTAL_AMOUNT' },
              ]} />
            </Form.Item>
            <Form.Item name={['downgradeCriteria', 'required_value']} label="保级要求值">
              <InputNumber style={{ width: 120 }} min={0} />
            </Form.Item>
            <Form.Item name={['downgradeCriteria', 'downgrade_target']} label="降级目标">
              <Select style={{ width: 140 }} options={tiers.filter(t => t.tierCode !== editModal.tier?.tierCode).map(t => ({ label: t.tierName, value: t.tierCode }))} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      {/* ====== 活动编辑弹窗 ====== */}
      <Modal
        title={activityModal.activity ? '编辑直升活动' : '新建直升活动'}
        open={activityModal.open}
        onCancel={() => setActivityModal({ open: false, activity: null })}
        onOk={handleActivitySave}
        width={600}
        okText="保存"
        cancelText="取消"
      >
        <Form form={activityForm} layout="vertical" size="small">
          <Space style={{ display: 'flex' }} size={12}>
            <Form.Item name="activityCode" label="活动代码" rules={[{ required: true }]}>
              <Input style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="activityName" label="活动名称" rules={[{ required: true }]}>
              <Input style={{ width: 200 }} />
            </Form.Item>
          </Space>
          <Space style={{ display: 'flex' }} size={12}>
            <Form.Item name="targetTierCode" label="目标等级" rules={[{ required: true }]}>
              <Select style={{ width: 160 }} options={tiers.map(t => ({ label: t.tierName, value: t.tierCode }))} />
            </Form.Item>
            <Form.Item name="triggerType" label="触发方式" rules={[{ required: true }]}>
              <Select style={{ width: 140 }} options={[
                { label: '支付直升', value: 'PAYMENT' },
                { label: '任务直升', value: 'TASK' },
                { label: '手动操作', value: 'MANUAL' },
              ]} />
            </Form.Item>
          </Space>
          <Space style={{ display: 'flex' }} size={12}>
            <Form.Item name="validStartTime" label="开始时间" rules={[{ required: true }]}>
              <Input placeholder="2026-06-01T00:00:00" style={{ width: 220 }} />
            </Form.Item>
            <Form.Item name="validEndTime" label="结束时间">
              <Input placeholder="2026-06-30T23:59:59" style={{ width: 220 }} />
            </Form.Item>
          </Space>
          <Space style={{ display: 'flex' }} size={12}>
            <Form.Item name="memberScope" label="适用范围">
              <Select style={{ width: 140 }} options={[
                { label: '全部会员', value: 'ALL' },
                { label: '仅新会员', value: 'NEW_ONLY' },
                { label: '仅现有会员', value: 'EXISTING_ONLY' },
              ]} />
            </Form.Item>
            <Form.Item name="oncePerMember" label="仅首次直升" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
          <Form.Item name="description" label="活动说明">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default TierConfig;