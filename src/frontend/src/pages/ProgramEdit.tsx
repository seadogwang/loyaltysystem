import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Drawer, Form, Input, Select, Button, Tabs, Table, InputNumber, Switch,
  message, Space, Typography, Divider, Popconfirm, Tag,
} from 'antd';
import { PlusOutlined, DeleteOutlined, SaveOutlined, DragOutlined } from '@ant-design/icons';
import PageWrapper from '../components/PageWrapper';
import api from '../api';

const { Title, Text } = Typography;

// ==================== 积分类型字典 ====================

interface PointTypeItem {
  typeCode: string;
  name: string;
  redeemable: boolean;
  tierRelevant: boolean;
  transferable: boolean;
  allowNegative: boolean;
}

// ==================== 等级阶梯 ====================

interface TierItem {
  tierCode: string;
  tierName: string;
  minPoints: number;
  maxPoints: number;
  benefits: string;
}

const ProgramEdit: React.FC = () => {
  const { code } = useParams<{ code: string }>();
  const navigate = useNavigate();
  const isEdit = !!code;

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  const [activeTab, setActiveTab] = useState('basic');

  // 积分类型字典
  const [pointTypes, setPointTypes] = useState<PointTypeItem[]>([
    { typeCode: 'REWARD_POINTS', name: '消费积分', redeemable: true, tierRelevant: true, transferable: false, allowNegative: false },
    { typeCode: 'TIER_POINTS', name: '等级成长值', redeemable: false, tierRelevant: true, transferable: false, allowNegative: false },
  ]);

  // 等级阶梯
  const [tiers, setTiers] = useState<TierItem[]>([
    { tierCode: 'BASE', tierName: '普通会员', minPoints: 0, maxPoints: 1000, benefits: '基础权益' },
    { tierCode: 'SILVER', tierName: '银卡会员', minPoints: 1000, maxPoints: 5000, benefits: '银卡权益' },
    { tierCode: 'GOLD', tierName: '金卡会员', minPoints: 5000, maxPoints: 10000, benefits: '金卡权益' },
    { tierCode: 'PLATINUM', tierName: '铂金会员', minPoints: 10000, maxPoints: 9999999, benefits: '铂金权益' },
  ]);

  useEffect(() => {
    if (isEdit) {
      setLoading(true);
      api.get(`/admin/programs/${code}`)
        .then(({ data }) => {
          const p = data?.data;
          if (p) {
            form.setFieldsValue(p);
            if (p.pointTypes) setPointTypes(p.pointTypes);
            if (p.tiers) setTiers(p.tiers);
          }
        })
        .catch(() => message.error('加载失败'))
        .finally(() => setLoading(false));
    }
  }, [code, isEdit, form]);

  const handleSave = async (values: any) => {
    setSaving(true);
    try {
      const payload = {
        ...values,
        pointTypes,
        tiers,
        overdraftLimit: values.overdraftLimit || 0,
        graceDays: values.graceDays || 7,
      };
      if (isEdit) {
        await api.put(`/admin/programs/${code}`, payload);
      } else {
        await api.post('/admin/programs', payload);
      }
      message.success(isEdit ? '俱乐部已更新' : '俱乐部已创建');
      navigate('/programs');
    } catch (e: any) {
      message.error(e.response?.data?.message || '保存失败');
    } finally {
      setSaving(false);
    }
  };

  // 积分类型字典列
  const ptColumns = [
    { title: '类型编码', dataIndex: 'typeCode', width: 140, render: (v: string, _: any, idx: number) => <Input size="small" value={v} onChange={e => updatePointType(idx, 'typeCode', e.target.value)} /> },
    { title: '名称', dataIndex: 'name', width: 120, render: (v: string, _: any, idx: number) => <Input size="small" value={v} onChange={e => updatePointType(idx, 'name', e.target.value)} /> },
    { title: '可兑换', dataIndex: 'redeemable', width: 70, render: (v: boolean, _: any, idx: number) => <Switch size="small" checked={v} onChange={c => updatePointType(idx, 'redeemable', c)} /> },
    { title: '算等级', dataIndex: 'tierRelevant', width: 70, render: (v: boolean, _: any, idx: number) => <Switch size="small" checked={v} onChange={c => updatePointType(idx, 'tierRelevant', c)} /> },
    { title: '可转赠', dataIndex: 'transferable', width: 70, render: (v: boolean, _: any, idx: number) => <Switch size="small" checked={v} onChange={c => updatePointType(idx, 'transferable', c)} /> },
    { title: '允许负数', dataIndex: 'allowNegative', width: 80, render: (v: boolean, _: any, idx: number) => <Switch size="small" checked={v} onChange={c => updatePointType(idx, 'allowNegative', c)} /> },
    {
      title: '操作', width: 60,
      render: (_: any, __: any, idx: number) => (
        <Button size="small" danger icon={<DeleteOutlined />} onClick={() => setPointTypes(prev => prev.filter((_, i) => i !== idx))} />
      ),
    },
  ];

  const updatePointType = (idx: number, field: string, value: any) => {
    setPointTypes(prev => prev.map((pt, i) => i === idx ? { ...pt, [field]: value } : pt));
  };

  // 等级阶梯列
  const tierColumns = [
    { title: '等级代码', dataIndex: 'tierCode', width: 100, render: (v: string, _: any, idx: number) => <Input size="small" value={v} onChange={e => updateTier(idx, 'tierCode', e.target.value)} /> },
    { title: '等级名称', dataIndex: 'tierName', width: 120, render: (v: string, _: any, idx: number) => <Input size="small" value={v} onChange={e => updateTier(idx, 'tierName', e.target.value)} /> },
    { title: '最低成长值', dataIndex: 'minPoints', width: 110, render: (v: number, _: any, idx: number) => <InputNumber size="small" value={v} onChange={val => updateTier(idx, 'minPoints', val || 0)} /> },
    { title: '最高成长值', dataIndex: 'maxPoints', width: 110, render: (v: number, _: any, idx: number) => <InputNumber size="small" value={v} onChange={val => updateTier(idx, 'maxPoints', val || 0)} /> },
    { title: '权益描述', dataIndex: 'benefits', width: 200, render: (v: string, _: any, idx: number) => <Input size="small" value={v} onChange={e => updateTier(idx, 'benefits', e.target.value)} /> },
    {
      title: '操作', width: 60,
      render: (_: any, __: any, idx: number) => (
        <Button size="small" danger icon={<DeleteOutlined />} onClick={() => setTiers(prev => prev.filter((_, i) => i !== idx))} />
      ),
    },
  ];

  const updateTier = (idx: number, field: string, value: any) => {
    setTiers(prev => prev.map((t, i) => i === idx ? { ...t, [field]: value } : t));
  };

  const tabItems = [
    {
      key: 'basic',
      label: '基础信息',
      children: (
        <div style={{  }}>
          <Form.Item name="programCode" label="俱乐部代码" rules={[{ required: true, message: '请输入' }]}>
            <Input disabled={isEdit} placeholder="如 BRAND-A" />
          </Form.Item>
          <Form.Item name="displayName" label="显示名称" rules={[{ required: true }]}>
            <Input placeholder="如 品牌A忠诚度计划" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue="ACTIVE">
            <Select options={[{ label: '激活', value: 'ACTIVE' }, { label: '停用', value: 'INACTIVE' }]} />
          </Form.Item>
        </div>
      ),
    },
    {
      key: 'pointTypes',
      label: '积分类型字典',
      children: (
        <div>
          <Button type="dashed" icon={<PlusOutlined />} style={{ marginBottom: 12 }}
            onClick={() => setPointTypes(prev => [...prev, { typeCode: '', name: '', redeemable: true, tierRelevant: false, transferable: false, allowNegative: false }])}>
            添加积分类型
          </Button>
          <Table dataSource={pointTypes} columns={ptColumns} rowKey={(_, idx) => String(idx)}
            size="small" pagination={false} />
        </div>
      ),
    },
    {
      key: 'tiers',
      label: '等级阶梯',
      children: (
        <div>
          <Button type="dashed" icon={<PlusOutlined />} style={{ marginBottom: 12 }}
            onClick={() => setTiers(prev => [...prev, { tierCode: '', tierName: '', minPoints: 0, maxPoints: 0, benefits: '' }])}>
            添加等级
          </Button>
          <Table dataSource={tiers} columns={tierColumns} rowKey={(_, idx) => String(idx)}
            size="small" pagination={false} />
        </div>
      ),
    },
    {
      key: 'reverse',
      label: '逆向策略',
      children: (
        <div style={{ maxWidth: 400 }}>
          <Form.Item name="overdraftLimit" label="透支限额" initialValue={0}>
            <InputNumber min={0} style={{ width: 200 }} addonAfter="积分" />
          </Form.Item>
          <Form.Item name="creditLimit" label="信用额度" initialValue={0}>
            <InputNumber min={0} style={{ width: 200 }} addonAfter="积分" />
          </Form.Item>
          <Form.Item name="graceDays" label="退款过期宽限天数" initialValue={7}>
            <InputNumber min={0} max={365} style={{ width: 200 }} addonAfter="天" />
          </Form.Item>
        </div>
      ),
    },
  ];

  return (
    <PageWrapper loading={loading}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>
          {isEdit ? `编辑俱乐部: ${code}` : '新建俱乐部'}
        </Title>
        <Space>
          <Button onClick={() => navigate('/programs')}>取消</Button>
          <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={() => form.submit()}>
            保存
          </Button>
        </Space>
      </div>

      <Form form={form} layout="vertical" onFinish={handleSave}>
        <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabItems} />
      </Form>
    </PageWrapper>
  );
};

export default ProgramEdit;