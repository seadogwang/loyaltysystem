import React, { useState, useEffect } from 'react';
import { Card, Table, Input, Button, Switch, Select, InputNumber, message, Spin, Tag } from 'antd';
import { PlusOutlined, SaveOutlined } from '@ant-design/icons';
import { useAppStore } from '../store';
import api from '../api';

interface PointTypeItem {
  typeCode: string;
  name: string;
  pointCategory: string; // ASSET / CONTRIBUTION / RECORD
  redeemable: boolean;
  tierRelevant: boolean;
  transferable: boolean;
  allowNegative: boolean;
  allowRepay: boolean;
  expiryMode: string;
  expiryValue: number;
  visible: boolean;
  creditLimit: number;
  overdraftLimit: number;
}

const CATEGORY_LABELS: Record<string, { label: string; color: string }> = {
  ASSET: { label: '资产型', color: 'blue' },
  CONTRIBUTION: { label: '贡献型', color: 'green' },
  RECORD: { label: '记录型', color: 'orange' },
};

const CATEGORY_OPTIONS = [
  { label: '资产型 (ASSET)', value: 'ASSET' },
  { label: '贡献型 (CONTRIBUTION)', value: 'CONTRIBUTION' },
  { label: '记录型 (RECORD)', value: 'RECORD' },
];

const defaultPointTypes: PointTypeItem[] = [
  { typeCode: 'REWARD', name: '消费积分', pointCategory: 'ASSET', redeemable: true, tierRelevant: false, transferable: true, allowNegative: false, allowRepay: false, expiryMode: 'CALENDAR_YEARS', expiryValue: 1, visible: true, creditLimit: 0, overdraftLimit: 0 },
  { typeCode: 'TIER', name: '等级积分', pointCategory: 'CONTRIBUTION', redeemable: false, tierRelevant: true, transferable: false, allowNegative: false, allowRepay: false, expiryMode: 'FIXED_DAYS', expiryValue: 0, visible: true, creditLimit: 0, overdraftLimit: 0 },
  { typeCode: 'CREDIT', name: '授信积分', pointCategory: 'ASSET', redeemable: true, tierRelevant: false, transferable: false, allowNegative: true, allowRepay: false, expiryMode: 'FIXED_DAYS', expiryValue: 0, visible: false, creditLimit: 0, overdraftLimit: 0 },
  { typeCode: 'PURCHASE_COUNT', name: '购买次数积分', pointCategory: 'CONTRIBUTION', redeemable: false, tierRelevant: true, transferable: false, allowNegative: false, allowRepay: false, expiryMode: 'FIXED_DAYS', expiryValue: 0, visible: false, creditLimit: 0, overdraftLimit: 0 },
  { typeCode: 'BEHAVIOR_POINTS', name: '行为积分', pointCategory: 'CONTRIBUTION', redeemable: false, tierRelevant: true, transferable: false, allowNegative: false, allowRepay: false, expiryMode: 'FIXED_DAYS', expiryValue: 0, visible: false, creditLimit: 0, overdraftLimit: 0 },
  { typeCode: 'ACTIVE_DAYS', name: '活跃天数', pointCategory: 'RECORD', redeemable: false, tierRelevant: false, transferable: false, allowNegative: false, allowRepay: false, expiryMode: 'FIXED_DAYS', expiryValue: 0, visible: false, creditLimit: 0, overdraftLimit: 0 },
  { typeCode: 'CUSTOM_POINTS', name: '自定义积分', pointCategory: 'CONTRIBUTION', redeemable: false, tierRelevant: true, transferable: false, allowNegative: false, allowRepay: false, expiryMode: 'FIXED_DAYS', expiryValue: 0, visible: false, creditLimit: 0, overdraftLimit: 0 },
];

const expiryModeLabels: Record<string, string> = {
  FIXED_DAYS: '固定天数',
  CALENDAR_MONTHS: '自然月',
  CALENDAR_YEARS: '自然年',
  NONE: '永不过期',
};

// hover 输入框
const HoverInput: React.FC<{ value: string; onChange: (v: string) => void; w?: number }> = ({ value, onChange, w }) => {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(value);
  if (editing) return <Input size="small" value={draft} autoFocus style={{ width: w }} onChange={e => setDraft(e.target.value)} onBlur={() => { onChange(draft); setEditing(false); }} onPressEnter={() => { onChange(draft); setEditing(false); }} />;
  return <span style={{ cursor: 'pointer', padding: '4px 8px', borderRadius: 4, display: 'inline-block', width: w, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', border: '1px solid transparent' }} onMouseEnter={e => { e.currentTarget.style.background = '#f5f5f5'; e.currentTarget.style.borderColor = '#d9d9d9'; }} onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.borderColor = 'transparent'; }} onClick={() => { setDraft(value); setEditing(true); }}>{value || <span style={{ color: '#ccc' }}>-</span>}</span>;
};

// hover 数字输入框
const HoverNumber: React.FC<{ value: number; onChange: (v: number) => void; w?: number }> = ({ value, onChange, w }) => {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(value);
  if (editing) return <InputNumber size="small" value={draft} autoFocus style={{ width: w }} onChange={v => setDraft(v ?? 0)} onBlur={() => { onChange(draft); setEditing(false); }} onPressEnter={() => { onChange(draft); setEditing(false); }} />;
  return <span style={{ cursor: 'pointer', padding: '4px 8px', borderRadius: 4, display: 'inline-block', width: w, border: '1px solid transparent' }} onMouseEnter={e => { e.currentTarget.style.background = '#f5f5f5'; e.currentTarget.style.borderColor = '#d9d9d9'; }} onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.borderColor = 'transparent'; }} onClick={() => { setDraft(value); setEditing(true); }}>{value}</span>;
};

// hover 下拉选择
const HoverSelect: React.FC<{ value: string; options: { label: string; value: string }[]; onChange: (v: string) => void; w?: number }> = ({ value, options, onChange, w }) => {
  const [editing, setEditing] = useState(false);
  const label = options.find(o => o.value === value)?.label || value;
  if (editing) return <Select size="small" value={value} autoFocus style={{ width: w }} onChange={v => { onChange(v); setEditing(false); }} onBlur={() => setEditing(false)} options={options} />;
  return <span style={{ cursor: 'pointer', padding: '4px 8px', borderRadius: 4, display: 'inline-block', width: w, border: '1px solid transparent', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }} onMouseEnter={e => { e.currentTarget.style.background = '#f5f5f5'; e.currentTarget.style.borderColor = '#d9d9d9'; }} onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.borderColor = 'transparent'; }} onClick={() => setEditing(true)}>{label}</span>;
};

// 分类的默认行为
const categoryDefaults: Record<string, Partial<PointTypeItem>> = {
  ASSET: { redeemable: true, tierRelevant: false, expiryMode: 'CALENDAR_YEARS', expiryValue: 1 },
  CONTRIBUTION: { redeemable: false, tierRelevant: true, expiryMode: 'FIXED_DAYS', expiryValue: 0 },
  RECORD: { redeemable: false, tierRelevant: false, expiryMode: 'FIXED_DAYS', expiryValue: 0 },
};

const PointsGrant: React.FC = () => {
  const PROG = useAppStore(s => s.currentProgramCode);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [pointTypes, setPointTypes] = useState<PointTypeItem[]>(defaultPointTypes);

  useEffect(() => {
    setLoading(true);
    api.get('/admin/tiers')
      .then(({ data }) => {
        const d = data?.data;
        if (d?.pointTypes && d.pointTypes.length > 0) {
          setPointTypes(d.pointTypes.map((p: any) => ({
            typeCode: p.typeCode || '',
            name: p.name || '',
            pointCategory: p.pointCategory || 'ASSET',
            redeemable: p.redeemable ?? true,
            tierRelevant: p.tierRelevant ?? false,
            transferable: p.transferable ?? false,
            allowNegative: p.allowNegative ?? false,
            allowRepay: p.allowRepay ?? false,
            expiryMode: p.expiryMode || 'FIXED_DAYS',
            expiryValue: p.expiryValue ?? 365,
            visible: p.visible ?? true,
            creditLimit: p.creditLimit ?? 0,
            overdraftLimit: p.overdraftLimit ?? 0,
          })));
        }
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [PROG]);

  const updatePointType = (idx: number, field: string, value: any) => {
    setPointTypes(prev => prev.map((pt, i) => {
      if (i !== idx) return pt;
      const updated = { ...pt, [field]: value };

      // 切换分类时自动设置默认行为
      if (field === 'pointCategory') {
        const defaults = categoryDefaults[value];
        if (defaults) {
          Object.assign(updated, defaults);
        }
      }

      // 开启算等级时，有效期自动设为永久
      if (field === 'tierRelevant' && value === true) {
        updated.expiryMode = 'NONE';
        updated.expiryValue = 0;
      }

      return updated;
    }));
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      await api.put('/admin/tiers', { pointTypes });
      message.success('积分类型已保存');
    } catch (e: any) {
      message.error(e.response?.data?.message || '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    {
      title: '类型编码', dataIndex: 'typeCode', width: 130,
      render: (v: string, _: any, idx: number) => <HoverInput value={v} onChange={val => updatePointType(idx, 'typeCode', val)} w={110} />,
    },
    {
      title: '名称', dataIndex: 'name', width: 100,
      render: (v: string, _: any, idx: number) => <HoverInput value={v} onChange={val => updatePointType(idx, 'name', val)} w={80} />,
    },
    {
      title: '分类', dataIndex: 'pointCategory', width: 200,
      render: (v: string, _: any, idx: number) => (
        <HoverSelect value={v} onChange={val => updatePointType(idx, 'pointCategory', val)} w={190}
          options={CATEGORY_OPTIONS} />
      ),
    },
    {
      title: '可兑换', dataIndex: 'redeemable', width: 60,
      render: (v: boolean, _: any, idx: number) => <Switch size="small" checked={v} onChange={c => updatePointType(idx, 'redeemable', c)} />,
    },
    {
      title: '算等级', dataIndex: 'tierRelevant', width: 60,
      render: (v: boolean, _: any, idx: number) => <Switch size="small" checked={v} onChange={c => updatePointType(idx, 'tierRelevant', c)} />,
    },
    {
      title: '允许负分', dataIndex: 'allowNegative', width: 70,
      render: (v: boolean, _: any, idx: number) => <Switch size="small" checked={v} onChange={c => updatePointType(idx, 'allowNegative', c)} />,
    },
    {
      title: '可冲抵', dataIndex: 'allowRepay', width: 60,
      render: (v: boolean, _: any, idx: number) => <Switch size="small" checked={v} onChange={c => updatePointType(idx, 'allowRepay', c)} />,
    },
    {
      title: '过期模式', dataIndex: 'expiryMode', width: 100,
      render: (v: string, r: PointTypeItem, idx: number) => (
        r.tierRelevant ? <span style={{ color: '#ccc', padding: '4px 8px' }}>-</span> :
        <HoverSelect value={v} onChange={val => updatePointType(idx, 'expiryMode', val)} w={90}
          options={[
            { label: '固定天数', value: 'FIXED_DAYS' },
            { label: '自然月', value: 'CALENDAR_MONTHS' },
            { label: '自然年', value: 'CALENDAR_YEARS' },
            { label: '永不过期', value: 'NONE' },
          ]} />
      ),
    },
    {
      title: '过期值', dataIndex: 'expiryValue', width: 65,
      render: (v: number, r: PointTypeItem, idx: number) => (
        r.tierRelevant ? <span style={{ color: '#ccc', padding: '4px 8px' }}>-</span> :
        <HoverNumber value={v} onChange={val => updatePointType(idx, 'expiryValue', val)} w={50} />
      ),
    },
    {
      title: '操作', width: 50,
      render: (_: any, __: any, idx: number) => (
        <span style={{ cursor: 'pointer', display: 'inline-flex', alignItems: 'center' }}
          onClick={() => setPointTypes(prev => prev.filter((_, i) => i !== idx))}>
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
            <circle cx="10" cy="10" r="9" stroke="#1a1a1a" strokeWidth="1.5" fill="white" />
            <path d="M6.5 6.5L13.5 13.5M13.5 6.5L6.5 13.5" stroke="#1a1a1a" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
        </span>
      ),
    },
  ];

  return (
    <Card
      title="积分类型配置"
      extra={<Button type="primary" icon={<SaveOutlined />} onClick={handleSave} loading={saving}>保存</Button>}
    >
      <style>{`.ant-input:focus, .ant-input-number:focus, .ant-input-focused, .ant-input-number-focused { box-shadow: none !important; border-color: #d9d9d9 !important; } .ant-select-focused .ant-select-selector { box-shadow: none !important; border-color: #d9d9d9 !important; }`}</style>
      {loading ? <Spin /> : (
        <Table
          dataSource={pointTypes}
          columns={columns}
          rowKey={(_, idx) => String(idx)}
          pagination={false}
          size="small"
          scroll={{ x: 1050 }}
          footer={() => (
            <Button size="small" type="text" icon={<PlusOutlined />} block
              onClick={() => setPointTypes(prev => [...prev, {
                typeCode: '', name: '新积分类型', pointCategory: 'ASSET',
                redeemable: true, tierRelevant: false,
                transferable: false, allowNegative: false, allowRepay: false,
                expiryMode: 'CALENDAR_YEARS', expiryValue: 1, visible: true,
                creditLimit: 0, overdraftLimit: 0,
              }])}
            >
              添加积分类型
            </Button>
          )}
        />
      )}
    </Card>
  );
};

export default PointsGrant;