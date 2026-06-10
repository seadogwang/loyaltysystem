import React, { useState, useMemo, useEffect } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import {
  Card, Form, Input, InputNumber, Select, Button, message, Space, Tag,
  Typography, Divider, Table, Checkbox, Radio, Row, Col, DatePicker, Collapse, Tooltip,
} from 'antd';
import {
  SaveOutlined, SendOutlined, PlusOutlined, DeleteOutlined,
  PlayCircleOutlined, ThunderboltOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import PageWrapper from '../components/PageWrapper';
import api from '../api';

const { Title, Text } = Typography;

type Option = { label: string; value: string };
type SchemaField = Option & { type: string; format?: string; enumValues?: string[] };

interface EntityCondition {
  key: string; entity: string; attribute: string; operator: string;
  valueStr?: string; valueMin?: number; valueMax?: number; valueArr?: string[];
}

interface RewardStep {
  key: string; lower: number; upper?: number; multiplier: number; isCycleThreshold: boolean;
}

const ENTITIES: Option[] = [
  { label: 'Order (订单)', value: 'Order' },
  { label: 'Member (会员)', value: 'Member' },
  { label: 'OrderItem (订单明细)', value: 'OrderItem' },
];

const OPERATORS: Record<string, Option[]> = {
  number: [{ label: '>=', value: 'GTE' }, { label: '>', value: 'GT' }, { label: '<=', value: 'LTE' }, { label: '<', value: 'LT' }, { label: '=', value: 'EQ' }, { label: '区间', value: 'BETWEEN' }],
  string: [{ label: '=', value: 'EQ' }, { label: '包含', value: 'IN' }, { label: '≠', value: 'NE' }],
  date: [{ label: '>=', value: 'GTE' }, { label: '<=', value: 'LTE' }, { label: '区间', value: 'BETWEEN' }],
};

const LIFECYCLE_MILESTONES: Option[] = [
  { label: '生日月首单', value: 'FIRST_ORDER_IN_BIRTHDAY_MONTH' },
  { label: '注册周年', value: 'REGISTRATION_ANNIVERSARY' },
  { label: '会员升级日', value: 'TIER_UPGRADE_ANNIVERSARY' },
];

const EXCESS_STRATEGIES: Option[] = [
  { label: '停止赠送', value: 'STOP' },
  { label: '按比例缩放', value: 'RATIO' },
  { label: '截断并降级', value: 'TRUNCATE_AND_DOWNGRADE' },
];

// ==================== 组件 ====================

const PromoEditor: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const isEdit = !!id;

  // 基础信息
  const [ruleCode, setRuleCode] = useState('');
  const [ruleName, setRuleName] = useState('');
  const [ruleGroup, setRuleGroup] = useState('');
  const [priority, setPriority] = useState(10);
  const [effectiveStart, setEffectiveStart] = useState<string>('');
  const [effectiveEnd, setEffectiveEnd] = useState<string>('');
  const [isPermanent, setIsPermanent] = useState(true);

  // 触发条件
  const [conditions, setConditions] = useState<EntityCondition[]>([]);
  const [orderSchema, setOrderSchema] = useState<SchemaField[]>([]);
  const [memberSchema, setMemberSchema] = useState<SchemaField[]>([]);

  // 奖励规则
  const [steps, setSteps] = useState<RewardStep[]>([
    { key: '1', lower: 0, upper: undefined, multiplier: 1.0, isCycleThreshold: false },
  ]);
  const [cycleMode, setCycleMode] = useState<string>('SINGLE_MATCH');
  const [remainderMode, setRemainderMode] = useState<string>('USE_STEP_MULTIPLIER');
  const [remainderFixedMult, setRemainderFixedMult] = useState(1.0);
  const [perOrderLimit, setPerOrderLimit] = useState<number | undefined>(undefined);
  const [accumulativeLimit, setAccumulativeLimit] = useState<number | undefined>(undefined);
  const [excessStrategy, setExcessStrategy] = useState('STOP');
  const [downgradeMultiplier, setDowngradeMultiplier] = useState(1.0);
  const [downgradeContinueCycle, setDowngradeContinueCycle] = useState(false);

  // 预览
  const [previewAmount, setPreviewAmount] = useState(1000);
  const [previewAlready, setPreviewAlready] = useState(0);
  const [previewResult, setPreviewResult] = useState<any>(null);
  const [previewLoading, setPreviewLoading] = useState(false);

  // 保存
  const [saving, setSaving] = useState(false);

  // 加载 Schema
  useEffect(() => {
    api.get('/schemas/ORDER').then(({ data }) => {
      const s = data?.data?.schema || data?.data;
      setOrderSchema(Object.entries(s?.properties || {}).map(([k, v]: any) => ({
        label: `${v.title || k}`, value: k, type: v.type || 'string',
        format: v.format, enumValues: v.enum,
      })));
    }).catch(() => {});
    api.get('/schemas/MEMBER').then(({ data }) => {
      const s = data?.data?.schema || data?.data;
      setMemberSchema(Object.entries(s?.properties || {}).map(([k, v]: any) => ({
        label: `${v.title || k}`, value: k, type: v.type || 'string',
        format: v.format, enumValues: v.enum,
      })));
    }).catch(() => {});
  }, []);

  // 编辑模式加载
  useEffect(() => {
    if (!id) return;
    api.get(`/admin/rules/${id}`).then(({ data }) => {
      const r = data?.data; if (!r) return;
      setRuleCode(r.rule_code || '');
      setRuleName(r.rule_name || '');
      setRuleGroup(r.rule_group || '');
      setPriority(r.priority || 10);
      if (r.effective_start) { setEffectiveStart(r.effective_start); setIsPermanent(false); }
      if (r.effective_end) setEffectiveEnd(r.effective_end);

      try {
        const meta = r.metadata ? (typeof r.metadata === 'string' ? JSON.parse(r.metadata) : r.metadata) : null;
        if (meta) {
          if (meta.entity_conditions) setConditions(meta.entity_conditions.map((c: any, i: number) => ({
            ...c, key: String(i + 1),
            valueStr: typeof c.value === 'string' ? c.value : undefined,
            valueMin: c.value?.min, valueMax: c.value?.max,
            valueArr: Array.isArray(c.value) ? c.value : undefined,
          })));
          if (meta.reward) {
            const rw = meta.reward;
            if (rw.steps) setSteps(rw.steps);
            if (rw.cycleMode) setCycleMode(rw.cycleMode);
            if (rw.perOrderLimit) setPerOrderLimit(rw.perOrderLimit);
            if (rw.accumulativeLimit) setAccumulativeLimit(rw.accumulativeLimit);
            if (rw.excessStrategy) setExcessStrategy(rw.excessStrategy);
            if (rw.remainderMode) setRemainderMode(rw.remainderMode);
          }
        }
      } catch (e) {}
    }).catch(() => {});
  }, [id]);

  const getSchemaFields = (entity: string): SchemaField[] => {
    switch (entity) {
      case 'Order': return orderSchema;
      case 'Member': return memberSchema;
      case 'OrderItem': return [
        { label: 'SKU', value: 'sku', type: 'string' },
        { label: 'Category ID', value: 'category_id', type: 'string' },
        { label: 'Price', value: 'price', type: 'number' },
        { label: 'Quantity', value: 'quantity', type: 'number' },
      ];
      default: return [];
    }
  };

  const addCondition = () => {
    setConditions([...conditions, { key: String(Date.now()), entity: 'Order', attribute: '', operator: 'GTE' }]);
  };

  const removeCondition = (key: string) => {
    setConditions(conditions.filter(c => c.key !== key));
  };

  const updateCondition = (key: string, field: string, value: any) => {
    setConditions(conditions.map(c => c.key === key ? { ...c, [field]: value } : c));
  };

  const addStep = () => {
    const lastStep = steps[steps.length - 1];
    setSteps([...steps, { key: String(Date.now()), lower: lastStep.upper || 0, upper: undefined, multiplier: 1.0, isCycleThreshold: false }]);
  };

  const removeStep = (key: string) => {
    if (steps.length <= 1) { message.warning('至少保留一个阶梯区间'); return; }
    setSteps(steps.filter(s => s.key !== key));
  };

  const cycleThresholds = steps.filter(s => s.isCycleThreshold).map(s => s.lower).sort((a, b) => b - a);

  const buildMetadata = () => ({
    entity_conditions: conditions.filter(c => c.attribute).map(c => {
      let value: any = c.valueStr;
      if (c.operator === 'BETWEEN') value = { min: c.valueMin, max: c.valueMax };
      else if (c.operator === 'IN') value = c.valueArr;
      return { entity: c.entity, attribute: c.attribute, operator: c.operator, value };
    }),
    effective_time_range: {
      start: effectiveStart || null,
      end: isPermanent ? null : (effectiveEnd || null),
    },
    reward: {
      steps: steps.map(s => ({ lower: s.lower, upper: s.upper, multiplier: s.multiplier, isCycleThreshold: s.isCycleThreshold })),
      cycleMode,
      cycleThresholdOrder: cycleThresholds,
      remainderMode,
      remainderFixedMultiplier: remainderFixedMult,
      perOrderLimit: perOrderLimit,
      accumulativeLimit: accumulativeLimit,
      excessStrategy,
      downgradeMultiplier,
      downgradeContinueCycle,
    },
  });

  const handlePreview = async () => {
    setPreviewLoading(true);
    try {
      const { data } = await api.post('/admin/rules/preview', {
        orderAmount: previewAmount,
        alreadyRewarded: previewAlready,
        reward: buildMetadata().reward,
      });
      setPreviewResult(data?.data);
    } catch (e: any) { message.error('预览失败'); }
    finally { setPreviewLoading(false); }
  };

  const handleSave = async () => {
    if (!ruleCode.trim()) { message.warning('请输入规则代码'); return; }
    setSaving(true);
    try {
      const meta = buildMetadata();
      const payload = {
        rule_code: ruleCode,
        rule_name: ruleName || ruleCode,
        rule_type: 'ACTIVITY_PROMO',
        rule_category: 'promo',
        rule_group: ruleGroup,
        priority,
        effective_start: effectiveStart || null,
        effective_end: isPermanent ? null : (effectiveEnd || null),
        drl_content: '',
        metadata: meta,
        status: 'DRAFT',
      };
      if (isEdit) await api.put(`/admin/rules/${id}`, payload);
      else await api.post('/admin/rules', payload);
      message.success('已保存草稿');
    } catch (e: any) { message.error(e?.message || '保存失败'); }
    finally { setSaving(false); }
  };

  const handlePublish = async () => {
    if (!ruleCode.trim()) { message.warning('请输入规则代码'); return; }
    try {
      await handleSave();
      await api.post(`/admin/rules/${id || ''}/publish`);
      message.success('已发布');
      navigate('/rules');
    } catch (e: any) { message.error(e?.message || '发布失败'); }
  };

  const stepColumns = [
    { title: '下限', dataIndex: 'lower', width: 80, render: (v: number, _: any, i: number) => <InputNumber size="small" min={0} value={v} style={{ width: 70 }} onChange={val => { const n = [...steps]; n[i] = { ...n[i], lower: val || 0 }; setSteps(n); }} /> },
    { title: '上限', dataIndex: 'upper', width: 80, render: (v: number | undefined, _: any, i: number) => <InputNumber size="small" min={0} value={v} placeholder="不限" style={{ width: 70 }} onChange={val => { const n = [...steps]; n[i] = { ...n[i], upper: val || undefined }; setSteps(n); }} /> },
    { title: '倍数', dataIndex: 'multiplier', width: 80, render: (v: number, _: any, i: number) => <InputNumber size="small" min={0.1} step={0.1} value={v} style={{ width: 70 }} onChange={val => { const n = [...steps]; n[i] = { ...n[i], multiplier: val || 0.1 }; setSteps(n); }} /> },
    { title: '循环点', dataIndex: 'isCycleThreshold', width: 60, render: (v: boolean, _: any, i: number) => cycleMode === 'THRESHOLD_LOOP' ? <Checkbox checked={v} onChange={e => { const n = [...steps]; n[i] = { ...n[i], isCycleThreshold: e.target.checked }; setSteps(n); }} /> : null },
    { title: '操作', key: 'act', width: 60, render: (_: any, __: any, i: number) => <Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeStep(steps[i].key)} /> },
  ];

  return (
    <PageWrapper>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>{isEdit ? `编辑活动 #${id}` : '新建积分活动'}</Title>
        <Space>
          <Button onClick={() => navigate('/rules')}>取消</Button>
          <Button icon={<SaveOutlined />} onClick={handleSave} loading={saving}>保存草稿</Button>
          <Button type="primary" icon={<SendOutlined />} onClick={handlePublish}>发布</Button>
        </Space>
      </div>

      {/* 基础信息 */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <Row gutter={12}>
          <Col span={4}><Form.Item label="规则代码" style={{ marginBottom: 0 }}><Input size="small" value={ruleCode} onChange={e => setRuleCode(e.target.value)} placeholder="PROMO_001" /></Form.Item></Col>
          <Col span={4}><Form.Item label="规则名称" style={{ marginBottom: 0 }}><Input size="small" value={ruleName} onChange={e => setRuleName(e.target.value)} placeholder="618大促" /></Form.Item></Col>
          <Col span={3}><Form.Item label="规则组" style={{ marginBottom: 0 }}><Input size="small" value={ruleGroup} onChange={e => setRuleGroup(e.target.value)} placeholder="promo_grp" /></Form.Item></Col>
          <Col span={2}><Form.Item label="优先级" style={{ marginBottom: 0 }}><InputNumber size="small" min={0} max={999} value={priority} onChange={v => setPriority(v || 0)} style={{ width: '100%' }} /></Form.Item></Col>
          <Col span={5}><Form.Item label="生效周期" style={{ marginBottom: 0 }}><DatePicker.RangePicker size="small" showTime format="YYYY-MM-DD HH:mm" placeholder={['开始', '结束(留空=永久)']} value={[effectiveStart ? dayjs(effectiveStart) : null, effectiveEnd && !isPermanent ? dayjs(effectiveEnd) : null] as any} onChange={(d) => { setEffectiveStart(d?.[0]?.format('YYYY-MM-DD HH:mm:ss') || ''); if (d?.[1]) { setEffectiveEnd(d[1].format('YYYY-MM-DD HH:mm:ss')); setIsPermanent(false); } else { setIsPermanent(true); } }} style={{ width: '100%' }} /></Form.Item></Col>
          <Col span={2}><Form.Item label="永久" style={{ marginBottom: 0 }}><Checkbox checked={isPermanent} onChange={e => setIsPermanent(e.target.checked)} /></Form.Item></Col>
        </Row>
      </Card>

      {/* 触发条件 */}
      <Card size="small" title="触发条件" extra={<Text type="secondary" style={{ fontSize: 11 }}>所有条件为 AND 关系</Text>} style={{ marginBottom: 16 }}>
        {conditions.map(c => {
          const fields = getSchemaFields(c.entity);
          const fieldMeta = fields.find(f => f.value === c.attribute);
          const ops = OPERATORS[fieldMeta?.type || 'string'] || OPERATORS.string;
          return (
            <Row gutter={8} key={c.key} style={{ marginBottom: 4 }}>
              <Col span={3}><Select size="small" value={c.entity} options={ENTITIES} style={{ width: '100%' }} onChange={v => updateCondition(c.key, 'entity', v)} /></Col>
              <Col span={4}>
                <Select size="small" showSearch value={c.attribute || undefined} options={fields} style={{ width: '100%' }} onChange={v => { updateCondition(c.key, 'attribute', v); if (fieldMeta?.type === 'string') updateCondition(c.key, 'operator', 'EQ'); }}
                  placeholder="属性" />
              </Col>
              <Col span={3}><Select size="small" value={c.operator} options={ops} style={{ width: '100%' }} onChange={v => updateCondition(c.key, 'operator', v)} /></Col>
              <Col span={9}>
                {c.entity === 'Member' && c.attribute === 'lifecycle_milestone' ? (
                  <Select size="small" value={c.valueStr} options={LIFECYCLE_MILESTONES} style={{ width: '100%' }} onChange={v => updateCondition(c.key, 'valueStr', v)} placeholder="里程碑" />
                ) : c.operator === 'BETWEEN' ? (
                  <Space size={4}>
                    <InputNumber size="small" placeholder="min" value={c.valueMin} style={{ width: 80 }} onChange={v => updateCondition(c.key, 'valueMin', v)} />
                    <Text>~</Text>
                    <InputNumber size="small" placeholder="max" value={c.valueMax} style={{ width: 80 }} onChange={v => updateCondition(c.key, 'valueMax', v)} />
                  </Space>
                ) : c.operator === 'IN' ? (
                  <Select size="small" mode="tags" value={c.valueArr} style={{ width: '100%' }} onChange={v => updateCondition(c.key, 'valueArr', v)} placeholder="输入并回车" />
                ) : (
                  <Input size="small" value={c.valueStr || ''} onChange={e => updateCondition(c.key, 'valueStr', e.target.value)} placeholder="值" />
                )}
              </Col>
              <Col span={2}><Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeCondition(c.key)} /></Col>
            </Row>
          );
        })}
        <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={addCondition} style={{ marginTop: 4 }}>添加条件</Button>
      </Card>

      {/* 奖励规则 */}
      <Card size="small" title="奖励规则（阶梯）" extra={<Button size="small" type="dashed" icon={<PlusOutlined />} onClick={addStep}>添加区间</Button>} style={{ marginBottom: 16 }}>
        <Table dataSource={steps} columns={stepColumns} rowKey="key" size="small" pagination={false} />
        <Text type="secondary" style={{ fontSize: 11 }}>上限为空表示无上限；"循环点"仅在启用循环扣除时显示</Text>
      </Card>

      {/* 循环扣除配置 */}
      <Collapse ghost style={{ marginBottom: 16 }} items={[{
        key: 'cycle', label: <Space><Checkbox checked={cycleMode === 'THRESHOLD_LOOP'} onChange={e => setCycleMode(e.target.checked ? 'THRESHOLD_LOOP' : 'SINGLE_MATCH')}>启用循环扣除</Checkbox></Space>,
        children: (
          <div>
            <Form.Item label="循环分段点（从高到低）" style={{ marginBottom: 8 }}>
              {cycleThresholds.map((t, i) => <Tag key={i} color="blue" style={{ marginRight: 4 }}>{t}</Tag>)}
              {cycleThresholds.length === 0 && <Text type="secondary">请在阶梯表格中勾选"循环点"</Text>}
            </Form.Item>
            <Form.Item label="剩余金额处理" style={{ marginBottom: 0 }}>
              <Radio.Group value={remainderMode} onChange={e => setRemainderMode(e.target.value)}>
                <Radio value="USE_STEP_MULTIPLIER">按阶梯倍数</Radio>
                <Radio value="FIXED_MULTIPLIER">固定倍数</Radio>
              </Radio.Group>
              {remainderMode === 'FIXED_MULTIPLIER' && <InputNumber size="small" min={0.1} step={0.1} value={remainderFixedMult} onChange={v => setRemainderFixedMult(v || 0.1)} style={{ marginLeft: 8 }} />}
            </Form.Item>
          </div>
        ),
      }]} />

      {/* 上限控制 */}
      <Card size="small" title="上限控制" style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={8}><Form.Item label="单笔上限" style={{ marginBottom: 0 }}><InputNumber size="small" min={0} value={perOrderLimit} onChange={v => setPerOrderLimit(v || undefined)} addonAfter="分" style={{ width: '100%' }} placeholder="不限" /></Form.Item></Col>
          <Col span={8}><Form.Item label="累计上限" style={{ marginBottom: 0 }}><InputNumber size="small" min={0} value={accumulativeLimit} onChange={v => setAccumulativeLimit(v || undefined)} addonAfter="分" style={{ width: '100%' }} placeholder="不限" /></Form.Item></Col>
        </Row>
        {(accumulativeLimit || 0) > 0 && (
          <>
            <Divider style={{ margin: '8px 0' }} />
            <Form.Item label="超限策略" style={{ marginBottom: 0 }}>
              <Select size="small" value={excessStrategy} options={EXCESS_STRATEGIES} style={{ width: 200 }}
                onChange={v => setExcessStrategy(v)} />
            </Form.Item>
            {excessStrategy === 'TRUNCATE_AND_DOWNGRADE' && (
              <div style={{ marginTop: 8 }}>
                <Form.Item label="降级倍数" style={{ marginBottom: 4 }}>
                  <InputNumber size="small" min={0} step={0.1} value={downgradeMultiplier} onChange={v => setDowngradeMultiplier(v || 0)} />
                </Form.Item>
                <Checkbox checked={downgradeContinueCycle} onChange={e => setDowngradeContinueCycle(e.target.checked)}>降级后继续循环</Checkbox>
              </div>
            )}
          </>
        )}
      </Card>

      {/* 预览 */}
      <Card size="small" title={<Space><PlayCircleOutlined />测试与预览</Space>} style={{ marginBottom: 16 }}>
        <Row gutter={16} align="middle">
          <Col>订单金额: <InputNumber size="small" min={0} value={previewAmount} onChange={v => setPreviewAmount(v || 0)} style={{ width: 100 }} addonAfter="元" /></Col>
          <Col>已累计: <InputNumber size="small" min={0} value={previewAlready} onChange={v => setPreviewAlready(v || 0)} style={{ width: 100 }} addonAfter="分" /></Col>
          <Col><Button icon={<PlayCircleOutlined />} onClick={handlePreview} loading={previewLoading}>运行预览</Button></Col>
        </Row>
        {previewResult && (
          <Card size="small" style={{ marginTop: 12, background: '#f6ffed' }}>
            <Row gutter={24}>
              <Col span={6}><Text strong>理论积分</Text><br /><Text style={{ fontSize: 18, color: '#1677ff' }}>{previewResult.theoreticalTotal}</Text></Col>
              <Col span={6}><Text strong>最终积分</Text><br /><Text style={{ fontSize: 18, color: '#52c41a' }}>{previewResult.finalPoints}</Text></Col>
              <Col span={6}><Text strong>剩余容量</Text><br /><Text>{previewResult.remainingCap != null ? previewResult.remainingCap : '不限'}</Text></Col>
              <Col span={6}><Text strong>新累计</Text><br /><Text>{previewResult.newTotal}</Text></Col>
            </Row>
            {previewResult.segments?.length > 0 && (
              <>
                <Divider style={{ margin: '8px 0' }} />
                <Text type="secondary" style={{ fontSize: 11 }}>计算明细：</Text>
                {previewResult.segments.map((seg: any, i: number) => (
                  <Tag key={i} style={{ margin: 2, fontSize: 11 }}>{seg.amount} × {seg.multiplier} = {seg.points}</Tag>
                ))}
              </>
            )}
          </Card>
        )}
      </Card>
    </PageWrapper>
  );
};

export default PromoEditor;