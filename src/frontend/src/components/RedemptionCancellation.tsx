import React, { useState, useCallback } from 'react';
import { Button, Card, InputNumber, Select, message, Result, Descriptions, Tag, Spin, Typography } from 'antd';
import { RollbackOutlined } from '@ant-design/icons';
import { useAppStore } from '../store';
import api from '../api';

const { Text } = Typography;

interface CancellationResult {
  restored_count: number;
  expired_count: number;
  grace_count: number;
  total_restored: number;
  total_expired: number;
}

const RedemptionCancellation: React.FC = () => {
  const programCode = useAppStore(s => s.currentProgramCode);
  const [redemptionTxId, setRedemptionTxId] = useState<number | null>(null);
  const [graceDays, setGraceDays] = useState(7);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<CancellationResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleCancel = useCallback(async () => {
    if (!redemptionTxId) { message.warning('请输入核销流水 ID'); return; }
    setLoading(true); setError(null); setResult(null);
    try {
      const { data } = await api.post(`/admin/redemption/${redemptionTxId}/cancel`, {
        grace_days: graceDays,
      });
      if (data.code === 'SUCCESS') {
        setResult(data.data);
        message.success('取消兑换处理完成');
      } else {
        setError(data.message);
      }
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '请求失败');
    } finally {
      setLoading(false);
    }
  }, [redemptionTxId, graceDays]);

  return (
    <Card title={<span><RollbackOutlined /> 退换货积分还原</span>} style={{ maxWidth: 700, margin: 24 }}>
      <Descriptions column={1} bordered size="small" style={{ marginBottom: 16 }}>
        <Descriptions.Item label="核销流水 ID">
          <InputNumber
            style={{ width: 200 }}
            value={redemptionTxId}
            onChange={(v) => setRedemptionTxId(v)}
            placeholder="Redemption Transaction ID"
          />
        </Descriptions.Item>
        <Descriptions.Item label="宽限期（天）">
          <Select value={graceDays} onChange={setGraceDays} style={{ width: 120 }}
            options={[0, 1, 3, 7, 14, 30].map(d => ({ label: `${d} 天`, value: d }))}
          />
        </Descriptions.Item>
      </Descriptions>

      <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
        通过 redemption_allocation 追溯原始 ACCRUAL 批次，
        将分摊额度加回。若原始批次已过期：
        宽限期内正常恢复，超宽限期自动作废。
      </Text>

      <Button type="primary" icon={<RollbackOutlined />} onClick={handleCancel} loading={loading}>
        执行取消兑换
      </Button>

      {loading && <Spin style={{ display: 'block', marginTop: 16 }} />}

      {result && (
        <Result status="success" title="积分还原完成" style={{ marginTop: 16 }}>
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="恢复批次">{result.restored_count}</Descriptions.Item>
            <Descriptions.Item label="过期拦截">{result.expired_count}</Descriptions.Item>
            <Descriptions.Item label="宽限期内恢复">{result.grace_count}</Descriptions.Item>
            <Descriptions.Item label="恢复总额"><Tag color="green">{result.total_restored}</Tag></Descriptions.Item>
            <Descriptions.Item label="作废总额" span={2}><Tag color="red">{result.total_expired}</Tag></Descriptions.Item>
          </Descriptions>
        </Result>
      )}

      {error && (
        <Result status="error" title="操作失败" subTitle={error} style={{ marginTop: 16 }} />
      )}
    </Card>
  );
};

export default RedemptionCancellation;