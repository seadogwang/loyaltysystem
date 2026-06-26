import React from 'react';
import { Typography } from 'antd';
import { ArrowUpOutlined } from '@ant-design/icons';

const { Text } = Typography;

interface SegmentBreakdown {
  members: number;
  conversions: number;
  conversionRate: number;
}

interface Props {
  baselineConversion: number;
  predictedConversion: number;
  upliftPct: number;
  segmentBreakdown?: Record<string, SegmentBreakdown>;
}

const COLORS = ['#3b82f6', '#22c55e', '#eab308', '#ef4444', '#8b5cf6', '#ec4899'];

/**
 * 模拟结果可视化 — 纯 CSS 条形图。
 * 对比基线 vs 预测转化率 + 分群表现。
 */
const SimulationResultChart: React.FC<Props> = ({
  baselineConversion,
  predictedConversion,
  upliftPct,
  segmentBreakdown,
}) => {
  const baselinePct = (baselineConversion || 0) * 100;
  const predictedPct = (predictedConversion || 0) * 100;
  const upliftDisplay = upliftPct ? (upliftPct * 100).toFixed(0) : '0';

  return (
    <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
      {/* 转化率对比 */}
      <div style={{ flex: 1, minWidth: 280 }}>
        <Text strong style={{ display: 'block', marginBottom: 12 }}>转化率对比</Text>

        <div style={{ marginBottom: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
            <Text style={{ fontSize: 13 }}>基线转化率</Text>
            <Text style={{ fontSize: 13 }}>{baselinePct.toFixed(1)}%</Text>
          </div>
          <div style={{ height: 24, background: '#f0f0f0', borderRadius: 4, overflow: 'hidden' }}>
            <div style={{ height: '100%', width: `${Math.min(baselinePct * 5, 100)}%`, backgroundColor: '#9ca3af', borderRadius: 4, transition: 'width 0.5s' }} />
          </div>
        </div>

        <div style={{ marginBottom: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
            <Text style={{ fontSize: 13 }}>预测转化率</Text>
            <Text style={{ fontSize: 13, fontWeight: 600, color: '#3b82f6' }}>{predictedPct.toFixed(1)}%</Text>
          </div>
          <div style={{ height: 28, background: '#f0f0f0', borderRadius: 4, overflow: 'hidden' }}>
            <div style={{ height: '100%', width: `${Math.min(predictedPct * 5, 100)}%`, backgroundColor: '#3b82f6', borderRadius: 4, transition: 'width 0.5s' }} />
          </div>
        </div>

        <div style={{ textAlign: 'center', padding: '8px 16px', background: '#f0fdf4', borderRadius: 8, border: '1px solid #bbf7d0' }}>
          <Text style={{ color: '#16a34a', fontSize: 18, fontWeight: 700 }}>
            <ArrowUpOutlined /> {upliftDisplay}% 提升
          </Text>
        </div>
      </div>

      {/* 分群表现 */}
      {segmentBreakdown && Object.keys(segmentBreakdown).length > 0 && (
        <div style={{ flex: 1, minWidth: 280 }}>
          <Text strong style={{ display: 'block', marginBottom: 12 }}>分群表现</Text>
          {Object.entries(segmentBreakdown).map(([segment, data], idx) => (
            <div key={segment} style={{ marginBottom: 12 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                <Text style={{ fontSize: 12 }}>{segment}</Text>
                <Text style={{ fontSize: 12, color: '#888' }}>
                  {data.members?.toLocaleString()} 人 | 转化率 {(data.conversionRate * 100).toFixed(1)}%
                </Text>
              </div>
              <div style={{ height: 18, background: '#f0f0f0', borderRadius: 4, overflow: 'hidden' }}>
                <div style={{
                  height: '100%',
                  width: `${Math.min(data.conversionRate * 100, 100)}%`,
                  backgroundColor: COLORS[idx % COLORS.length],
                  borderRadius: 4, transition: 'width 0.5s',
                }} />
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default SimulationResultChart;
