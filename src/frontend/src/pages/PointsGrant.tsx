import React, { useState, useEffect } from 'react';
import { Card, Table, Input, Button, Switch, message, Spin } from 'antd';
import { PlusOutlined, DeleteOutlined, SaveOutlined } from '@ant-design/icons';
import { useAppStore } from '../store';
import api from '../api';

interface PointTypeItem {
  typeCode: string;
  name: string;
  redeemable: boolean;
  tierRelevant: boolean;
  transferable: boolean;
  allowNegative: boolean;
}

const PointsGrant: React.FC = () => {
  const PROG = useAppStore(s => s.currentProgramCode);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [pointTypes, setPointTypes] = useState<PointTypeItem[]>([
    { typeCode: 'REWARD_POINTS', name: '消费积分', redeemable: true, tierRelevant: true, transferable: false, allowNegative: false },
    { typeCode: 'TIER_POINTS', name: '等级成长值', redeemable: false, tierRelevant: true, transferable: false, allowNegative: false },
  ]);

  useEffect(() => {
    setLoading(true);
    api.get(`/admin/programs/${PROG}`)
      .then(({ data }) => {
        const p = data?.data;
        if (p?.pointTypes) setPointTypes(p.pointTypes);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [PROG]);

  const updatePointType = (idx: number, field: string, value: any) => {
    setPointTypes(prev => prev.map((pt, i) => i === idx ? { ...pt, [field]: value } : pt));
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      await api.put(`/admin/programs/${PROG}`, { pointTypes });
      message.success('积分类型已保存');
    } catch (e: any) {
      message.error(e.response?.data?.message || '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    { title: '类型编码', dataIndex: 'typeCode', width: 160, render: (v: string, _: any, idx: number) => <Input size="small" value={v} onChange={e => updatePointType(idx, 'typeCode', e.target.value)} /> },
    { title: '名称', dataIndex: 'name', width: 140, render: (v: string, _: any, idx: number) => <Input size="small" value={v} onChange={e => updatePointType(idx, 'name', e.target.value)} /> },
    { title: '可兑换', dataIndex: 'redeemable', width: 80, render: (v: boolean, _: any, idx: number) => <Switch size="small" checked={v} onChange={c => updatePointType(idx, 'redeemable', c)} /> },
    { title: '算等级', dataIndex: 'tierRelevant', width: 80, render: (v: boolean, _: any, idx: number) => <Switch size="small" checked={v} onChange={c => updatePointType(idx, 'tierRelevant', c)} /> },
    { title: '可转赠', dataIndex: 'transferable', width: 80, render: (v: boolean, _: any, idx: number) => <Switch size="small" checked={v} onChange={c => updatePointType(idx, 'transferable', c)} /> },
    { title: '允许负数', dataIndex: 'allowNegative', width: 90, render: (v: boolean, _: any, idx: number) => <Switch size="small" checked={v} onChange={c => updatePointType(idx, 'allowNegative', c)} /> },
    {
      title: '操作', width: 60,
      render: (_: any, __: any, idx: number) => (
        <Button size="small" danger icon={<DeleteOutlined />} onClick={() => setPointTypes(prev => prev.filter((_, i) => i !== idx))} />
      ),
    },
  ];

  return (
    <Card
      title="积分类型字典"
      extra={<Button type="primary" icon={<SaveOutlined />} onClick={handleSave} loading={saving}>保存</Button>}
      style={{ maxWidth: '100%' }}
    >
      {loading ? <Spin /> : (
        <Table
          dataSource={pointTypes}
          columns={columns}
          rowKey="typeCode"
          pagination={false}
          size="small"
          footer={() => (
            <Button size="small" type="dashed" icon={<PlusOutlined />} block onClick={() => setPointTypes(prev => [...prev, { typeCode: '', name: '', redeemable: false, tierRelevant: false, transferable: false, allowNegative: false }])}>
              添加积分类型
            </Button>
          )}
        />
      )}
    </Card>
  );
};

export default PointsGrant;