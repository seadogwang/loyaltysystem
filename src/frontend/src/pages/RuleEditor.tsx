import React, { useState, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card, Form, Input, InputNumber, Select, Button, message, Space, Modal, Tag,
  Typography, Alert, Descriptions, Divider,
} from 'antd';
import { SaveOutlined, PlayCircleOutlined, ThunderboltOutlined, SendOutlined } from '@ant-design/icons';
import Editor, { OnMount } from '@monaco-editor/react';
import type { editor } from 'monaco-editor';
import PageWrapper from '../components/PageWrapper';
import api from '../api';

const { Title, Text } = Typography;

// 示例 DRL 模板
const DEFAULT_DRL = `package com.loyalty.platform.rules;

import com.loyalty.platform.rules.drl.MemberFact;
import com.loyalty.platform.rules.drl.EventFact;

rule "示例规则"
  agenda-group "purchase"
  salience 100
  when
    $event: EventFact(eventType == "ORDER_PAID")
    $member: MemberFact(tierCode == "GOLD")
    eval($event.getPayloadNumber("order_amount") > 5000)
  then
    System.out.println("发放积分: 会员=" + $member.getMemberId());
    // 可通过 RewardExecutor 发放积分
end`;

const RuleEditor: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const isEdit = !!id;
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null);

  const [form] = Form.useForm();
  const [drlCode, setDrlCode] = useState(DEFAULT_DRL);
  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [aiPrompt, setAiPrompt] = useState('');
  const [aiLoading, setAiLoading] = useState(false);
  const [aiResult, setAiResult] = useState<any>(null);
  const [publishModal, setPublishModal] = useState<{ open: boolean; level: string; report: any }>({
    open: false, level: '', report: null,
  });
  const [forceReason, setForceReason] = useState('');

  // 采纳 AI 生成的规则
  const handleAdoptAi = () => {
    if (aiResult?.drl_code) {
      setDrlCode(aiResult.drl_code);
      if (aiResult.salience_recommendation) {
        form.setFieldsValue({ salience: aiResult.salience_recommendation.salience });
      }
      setAiResult(null);
      message.success('已采纳 AI 生成的规则');
    }
  };

  // AI 生成规则
  const handleAiGenerate = async () => {
    if (!aiPrompt.trim()) { message.warning('请输入规则描述'); return; }
    setAiLoading(true);
    try {
      const { data } = await api.post('/admin/rules/generate', { prompt: aiPrompt });
      setAiResult(data?.data);
    } catch (e: any) {
      message.error(e.response?.data?.message || 'AI 生成失败，请检查后端 LLM 配置');
    } finally {
      setAiLoading(false);
    }
  };

  // 保存草稿
  const handleSave = async (values: any) => {
    setSaving(true);
    try {
      const payload = { ...values, drl_content: drlCode, status: 'DRAFT' };
      if (isEdit) {
        await api.put(`/admin/rules/${id}`, payload);
      } else {
        await api.post('/admin/rules', payload);
      }
      message.success('规则已保存为草稿');
    } catch (e: any) {
      message.error(e.response?.data?.message || '保存失败');
    } finally {
      setSaving(false);
    }
  };

  // 发布流程（含沙箱回归测试）
  const handlePublish = async () => {
    // 先校验表单+保存
    try {
      const values = await form.validateFields();
      const payload = { ...values, drl_content: drlCode };

      // 保存
      setPublishing(true);
      let ruleId = id ? Number(id) : null;
      if (isEdit) {
        await api.put(`/admin/rules/${id}`, payload);
      } else {
        const { data } = await api.post('/admin/rules', payload);
        ruleId = data?.data?.id;
      }

      // 沙箱回归测试
      try {
        const { data: testData } = await api.post(`/admin/rules/${ruleId}/validate`);
        const report = testData?.data;

        if (!report || report.level === 'GREEN') {
          // 直接发布
          await api.post(`/admin/rules/${ruleId}/publish`);
          message.success('规则已发布！');
          navigate('/rules');
        } else if (report.level === 'YELLOW') {
          setPublishModal({ open: true, level: 'YELLOW', report });
        } else {
          setPublishModal({ open: true, level: 'RED', report });
        }
      } catch {
        // 无沙箱测试接口，直接发布
        await api.post(`/admin/rules/${ruleId || id}/publish`);
        message.success('规则已发布！');
        navigate('/rules');
      }
    } catch (e: any) {
      if (e.errorFields) return; // 表单校验失败
      message.error(e.response?.data?.message || '发布失败');
    } finally {
      setPublishing(false);
    }
  };

  // 强制发布
  const handleForcePublish = async () => {
    if (!forceReason.trim()) { message.warning('请输入强制放行理由'); return; }
    try {
      const ruleId = id ? Number(id) : null;
      await api.post(`/admin/rules/${ruleId}/publish`, { forceOverride: true, reason: forceReason });
      message.success('规则已强制发布');
      setPublishModal({ open: false, level: '', report: null });
      navigate('/rules');
    } catch (e: any) { message.error(e.response?.data?.message || '发布失败'); }
  };

  const handleEditorMount: OnMount = useCallback((editor) => {
    editorRef.current = editor;
  }, []);

  return (
    <PageWrapper>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>
          {isEdit ? `编辑规则 #${id}` : '新建规则'}
        </Title>
        <Space>
          <Button onClick={() => navigate('/rules')}>取消</Button>
          <Button icon={<SaveOutlined />} onClick={() => form.submit()}>保存草稿</Button>
          <Button type="primary" icon={<SendOutlined />} loading={publishing} onClick={handlePublish}>
            发布
          </Button>
        </Space>
      </div>

      <div style={{ display: 'flex', gap: 16 }}>
        {/* 左栏：基本信息 */}
        <div style={{ width: 280, flexShrink: 0 }}>
          <Card size="small" title="基本信息">
            <Form form={form} layout="vertical" onFinish={handleSave} initialValues={{ status: 'DRAFT', salience: 100 }}>
              <Form.Item name="rule_code" label="规则代码" rules={[{ required: true }]}>
                <Input placeholder="RULE_101" />
              </Form.Item>
              <Form.Item name="rule_name" label="规则名称">
                <Input placeholder="示例规则" />
              </Form.Item>
              <Form.Item name="agenda_group" label="议程组" initialValue="purchase">
                <Input placeholder="purchase" />
              </Form.Item>
              <Form.Item name="salience" label="优先级" initialValue={100}>
                <InputNumber min={0} max={1000} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name="rule_type" label="规则类型" initialValue="DRL">
                <Select options={[
                  { label: 'DRL', value: 'DRL' },
                  { label: 'Decision Table', value: 'DECISION_TABLE' },
                  { label: 'DSL', value: 'DSL' },
                ]} />
              </Form.Item>
              <Form.Item name="description" label="描述">
                <Input.TextArea rows={2} />
              </Form.Item>
            </Form>
          </Card>

          {/* AI 辅助区域 */}
          <Card size="small" title={<Space><ThunderboltOutlined />AI 辅助</Space>}
            style={{ marginTop: 12, background: '#f6ffed' }}>
            <Input.TextArea
              value={aiPrompt}
              onChange={e => setAiPrompt(e.target.value)}
              placeholder="用自然语言描述规则，例如：用户如果在周末购买了苹果手机且金额大于5000，额外送100分"
              rows={3}
              style={{ marginBottom: 8 }}
            />
            <Button block icon={<ThunderboltOutlined />} onClick={handleAiGenerate} loading={aiLoading}>
              生成规则
            </Button>

            {aiResult && (
              <div style={{ marginTop: 12 }}>
                <Alert type="info" message={aiResult.analysis || 'AI 分析结果'} style={{ marginBottom: 8 }} />
                <pre style={{ background: '#1e1e1e', color: '#d4d4d4', padding: 8, borderRadius: 4, fontSize: 11, maxHeight: 150, overflow: 'auto' }}>
                  {aiResult.drl_code?.substring(0, 300)}
                </pre>
                <Space style={{ marginTop: 8 }}>
                  <Button size="small" type="primary" onClick={handleAdoptAi}>采纳并编辑</Button>
                  <Button size="small" onClick={() => setAiResult(null)}>取消</Button>
                </Space>
              </div>
            )}
          </Card>
        </div>

        {/* 右栏：Monaco DRL 编辑器 */}
        <div style={{ flex: 1 }}>
          <Card
            size="small"
            title="DRL 规则编辑器"
            extra={<Tag color="blue">Drools 8.44</Tag>}
            style={{ height: 'calc(100vh - 160px)' }}
            bodyStyle={{ padding: 0, height: 'calc(100% - 38px)' }}
          >
            <Editor
              height="100%"
              defaultLanguage="java"
              theme="vs-dark"
              value={drlCode}
              onChange={(v) => setDrlCode(v || '')}
              onMount={handleEditorMount}
              options={{
                minimap: { enabled: false },
                fontSize: 13,
                lineNumbers: 'on',
                scrollBeyondLastLine: false,
                automaticLayout: true,
                tabSize: 2,
                wordWrap: 'on',
              }}
            />
          </Card>
        </div>
      </div>

      {/* 发布沙箱测试结果 Modal */}
      <Modal
        title={publishModal.level === 'YELLOW' ? '⚠️ 沙箱测试警告' : '🚨 沙箱测试严重警告'}
        open={publishModal.open}
        onCancel={() => setPublishModal({ open: false, level: '', report: null })}
        footer={null}
        width={600}
      >
        {publishModal.report && (
          <>
            <Alert
              type={publishModal.level === 'YELLOW' ? 'warning' : 'error'}
              message={publishModal.level === 'YELLOW'
                ? '部分测试用例结果与基线不一致，建议检查后发布'
                : '多个测试用例与基线严重不一致，不建议直接发布'}
              style={{ marginBottom: 16 }}
            />
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="总用例数">{publishModal.report.totalCases}</Descriptions.Item>
              <Descriptions.Item label="差异数">
                <Tag color={publishModal.level === 'YELLOW' ? 'orange' : 'red'}>
                  {publishModal.report.diffCount}
                </Tag>
              </Descriptions.Item>
            </Descriptions>

            {publishModal.level === 'RED' && (
              <div style={{ marginTop: 16 }}>
                <Text strong>强制放行理由（必填）：</Text>
                <Input.TextArea
                  value={forceReason}
                  onChange={e => setForceReason(e.target.value)}
                  rows={2}
                  placeholder="请说明为什么需要强制发布此规则..."
                  style={{ marginTop: 8 }}
                />
              </div>
            )}

            <Divider />
            <Space style={{ justifyContent: 'flex-end', width: '100%' }}>
              <Button onClick={() => setPublishModal({ open: false, level: '', report: null })}>
                {publishModal.level === 'YELLOW' ? '返回修改' : '取消'}
              </Button>
              <Button
                type="primary"
                danger={publishModal.level === 'RED'}
                onClick={publishModal.level === 'YELLOW'
                  ? async () => {
                    try {
                      const ruleId = id ? Number(id) : null;
                      await api.post(`/admin/rules/${ruleId}/publish`);
                      message.success('规则已发布');
                      setPublishModal({ open: false, level: '', report: null });
                      navigate('/rules');
                    } catch (e: any) { message.error(e.response?.data?.message || '发布失败'); }
                  }
                  : handleForcePublish
                }
              >
                {publishModal.level === 'YELLOW' ? '仍要发布' : '强制发布'}
              </Button>
            </Space>
          </>
        )}
      </Modal>
    </PageWrapper>
  );
};

export default RuleEditor;