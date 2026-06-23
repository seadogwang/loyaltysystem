import React, { useState, useEffect } from 'react';
import { Card, Table, Input, InputNumber, Button, Select, message, Spin } from 'antd';
import { PlusOutlined, DeleteOutlined, SaveOutlined } from '@ant-design/icons';
import { useAppStore } from '../store';
import api from '../api';

interface TierItem {
  tierCode: string;
  tierName: string;
  minPoints: number;
  maxPoints: number;
  sequence: number;
  validityMode: string;
  validityValue: number;
}

const validityModeLabels: Record<string, string> = {
  FIXED_DAYS: '固定天数',
  CALENDAR_MONTHS: '自然月',
  CALENDAR_YEARS: '自然年',
};

const defaultTiers: TierItem[] = [
  { tierCode: 'BASE', tierName: '普通会员', minPoints: 0, maxPoints: 1000, sequence: 1, validityMode: 'FIXED_DAYS', validityValue: 0 },
  { tierCode: 'SILVER', tierName: '银卡会员', minPoints: 1000, maxPoints: 5000, sequence: 2, validityMode: 'CALENDAR_YEARS', validityValue: 1 },
  { tierCode: 'GOLD', tierName: '金卡会员', minPoints: 5000, maxPoints: 10000, sequence: 3, validityMode: 'CALENDAR_YEARS', validityValue: 1 },
  { tierCode: 'PLATINUM', tierName: '铂金会员', minPoints: 10000, maxPoints: 9999999, sequence: 4, validityMode: 'CALENDAR_YEARS', validityValue: 1 },
];

// hover 时显示输入框
const HoverInput: React.FC<{
  value: string;
  onChange: (v: string) => void;
  w?: number;
}> = ({ value, onChange, w }) => {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(value);

  if (editing) {
    return (
      <Input size="small" value={draft} autoFocus
        style={{ width: w }}
        onChange={e => setDraft(e.target.value)}
        onBlur={() => { onChange(draft); setEditing(false); }}
        onPressEnter={() => { onChange(draft); setEditing(false); }}
      />
    );
  }
  return (
    <span
      style={{ cursor: 'pointer', padding: '4px 8px', borderRadius: 4, transition: 'background 0.2s', display: 'inline-block', width: w, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
      onMouseEnter={e => (e.currentTarget.style.background = '#f5f5f5')}
      onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
      onClick={() => { setDraft(value); setEditing(true); }}
    >
      {value || <span style={{ color: '#ccc' }}>点击编辑</span>}
    </span>
  );
};

// hover 时显示数字输入框的单元格
const HoverNumber: React.FC<{
  value: number;
  onChange: (v: number) => void;
  w?: number;
}> = ({ value, onChange, w }) => {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(value);

  if (editing) {
    return (
      <InputNumber size="small" value={draft} autoFocus
        style={{ width: w }}
        onChange={v => setDraft(v ?? 0)}
        onBlur={() => { onChange(draft); setEditing(false); }}
        onPressEnter={() => { onChange(draft); setEditing(false); }}
      />
    );
  }
  return (
    <span
      style={{ cursor: 'pointer', padding: '4px 8px', borderRadius: 4, transition: 'background 0.2s', display: 'inline-block', width: w }}
      onMouseEnter={e => (e.currentTarget.style.background = '#f5f5f5')}
      onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
      onClick={() => { setDraft(value); setEditing(true); }}
    >
      {value}
    </span>
  );
};

// hover 时显示下拉选择
const HoverSelect: React.FC<{ value: string; options: { label: string; value: string }[]; onChange: (v: string) => void; w?: number }> = ({ value, options, onChange, w }) => {
  const [editing, setEditing] = useState(false);
  if (editing) return <Select size="small" value={value} autoFocus style={{ width: w }} onChange={v => { onChange(v); setEditing(false); }} onBlur={() => setEditing(false)} options={options} />;
  return <span style={{ cursor: 'pointer', padding: '4px 8px', borderRadius: 4, transition: 'background 0.2s', display: 'inline-block', width: w }} onMouseEnter={e => e.currentTarget.style.background = '#f5f5f5'} onMouseLeave={e => e.currentTarget.style.background = 'transparent'} onClick={() => setEditing(true)}>{validityModeLabels[value] || value}</span>;
};

const TierConfig: React.FC = () => {
  const PROG = useAppStore(s => s.currentProgramCode);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [tiers, setTiers] = useState<TierItem[]>(defaultTiers);

  useEffect(() => {
    setLoading(true);
    api.get('/admin/tiers')
      .then(({ data }) => {
        const d = data?.data;
        if (d?.tiers && d.tiers.length > 0) {
          setTiers(d.tiers.map((t: any) => ({
            tierCode: t.tierCode || '',
            tierName: t.tierName || '',
            minPoints: t.minPoints ?? 0,
            maxPoints: t.maxPoints ?? 0,
            sequence: t.sequence ?? 99,
            validityMode: t.validityMode || 'CALENDAR_YEARS',
            validityValue: t.validityValue ?? 1,
          })));
        }
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [PROG]);

  const updateField = (idx: number, field: string, value: any) => {
    setTiers(prev => prev.map((t, i) => i === idx ? { ...t, [field]: value } : t));
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      await api.put('/admin/tiers', { tiers });
      message.success('等级配置已保存');
    } catch (e: any) {
      message.error(e.response?.data?.message || '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    {
      title: '排序', dataIndex: 'sequence', width: 80,
      render: (v: number, _: any, idx: number) => (
        <HoverNumber value={v} onChange={val => updateField(idx, 'sequence', val)} w={50} />
      ),
    },
    {
      title: '等级代码', dataIndex: 'tierCode', width: 150,
      render: (v: string, _: any, idx: number) => (
        <HoverInput value={v} onChange={val => updateField(idx, 'tierCode', val)} w={130} />
      ),
    },
    {
      title: '等级名称', dataIndex: 'tierName', width: 150,
      render: (v: string, _: any, idx: number) => (
        <HoverInput value={v} onChange={val => updateField(idx, 'tierName', val)} w={130} />
      ),
    },
    {
      title: '过期模式', dataIndex: 'validityMode', width: 100,
      render: (v: string, _: any, idx: number) => (
        <HoverSelect value={v} onChange={val => updateField(idx, 'validityMode', val)}
          w={90}
          options={[
            { label: '固定天数', value: 'FIXED_DAYS' },
            { label: '自然月', value: 'CALENDAR_MONTHS' },
            { label: '自然年', value: 'CALENDAR_YEARS' },
          ]}
        />
      ),
    },
    {
      title: '有效期', dataIndex: 'validityValue', width: 80,
      render: (v: number, _: any, idx: number) => (
        <HoverNumber value={v} onChange={val => updateField(idx, 'validityValue', val)} w={70} />
      ),
    },
    {
      title: '操作', width: 50,
      render: (_: any, __: any, idx: number) => (
        <span style={{ cursor: 'pointer', display: 'inline-flex', alignItems: 'center' }}
          onClick={() => setTiers(prev => prev.filter((_, i) => i !== idx))}>
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
      title="等级配置"
      extra={<Button type="primary" icon={<SaveOutlined />} onClick={handleSave} loading={saving}>保存</Button>}
    >
      <style>{`.ant-input:focus, .ant-input-number:focus, .ant-input-focused, .ant-input-number-focused { box-shadow: none !important; border-color: #d9d9d9 !important; }`}</style>
      {loading ? <Spin /> : (
        <Table
          dataSource={tiers}
          columns={columns}
          rowKey={(_, idx) => String(idx)}
          pagination={false}
          size="small"
          scroll={{ x: 450 }}
          footer={() => (
            <Button size="small" type="text" icon={<PlusOutlined />} block
              onClick={() => setTiers(prev => [...prev, {
                tierCode: '', tierName: '新等级', minPoints: 0, maxPoints: 0, sequence: prev.length + 1, validityMode: 'CALENDAR_YEARS', validityValue: 1,
              }])}
            >
              新增等级
            </Button>
          )}
        />
      )}
    </Card>
  );
};

export default TierConfig;