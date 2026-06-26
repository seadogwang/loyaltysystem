import React from 'react';
import { Typography } from 'antd';
import type { AllocationDetail } from '../../../api/campaign';

const { Text } = Typography;

interface BudgetAllocationChartProps {
  allocations: AllocationDetail[];
  totalBudget: number;
}

const COLORS = ['#3b82f6', '#22c55e', '#eab308', '#ef4444', '#8b5cf6', '#ec4899'];

/**
 * 预算分配可视化 — 纯 CSS 条形图 + SVG 饼图。
 *
 * <p>无需外部图表库，使用 CSS flexbox 和 SVG circle 实现。
 */
const BudgetAllocationChart: React.FC<BudgetAllocationChartProps> = ({
  allocations,
  totalBudget,
}) => {
  const sorted = [...allocations].sort((a, b) => b.percentage - a.percentage);

  // --- 饼图数据计算 ---
  const total = 2 * Math.PI;
  let cumulativeAngle = -Math.PI / 2; // 从顶部开始

  const slices = sorted.map((item, idx) => {
    const sliceAngle = (item.percentage / 100) * total;
    const startAngle = cumulativeAngle;
    cumulativeAngle += sliceAngle;

    const x1 = 50 + 40 * Math.cos(startAngle);
    const y1 = 50 + 40 * Math.sin(startAngle);
    const x2 = 50 + 40 * Math.cos(startAngle + sliceAngle);
    const y2 = 50 + 40 * Math.sin(startAngle + sliceAngle);
    const largeArc = sliceAngle > Math.PI ? 1 : 0;

    const pathData = [
      `M 50 50`,
      `L ${x1} ${y1}`,
      `A 40 40 0 ${largeArc} 1 ${x2} ${y2}`,
      `Z`,
    ].join(' ');

    return { pathData, color: COLORS[idx % COLORS.length], ...item };
  });

  return (
    <div style={{ display: 'flex', gap: 32, flexWrap: 'wrap' }}>
      {/* 条形图 */}
      <div style={{ flex: 1, minWidth: 300 }}>
        <Text strong style={{ display: 'block', marginBottom: 12 }}>预算分配</Text>
        {sorted.map((item, idx) => (
          <div key={item.initiativeId} style={{ marginBottom: 12 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
              <Text style={{ fontSize: 13, maxWidth: 180 }} ellipsis={{ tooltip: item.initiativeName }}>
                {item.initiativeName || item.initiativeId}
              </Text>
              <Text style={{ fontSize: 12, color: '#888' }}>
                ¥{item.allocatedBudget?.toLocaleString()} ({item.percentage?.toFixed(1)}%)
              </Text>
            </div>
            <div style={{
              height: 20,
              background: '#f0f0f0',
              borderRadius: 4,
              overflow: 'hidden',
            }}>
              <div style={{
                height: '100%',
                width: `${Math.min(item.percentage || 0, 100)}%`,
                backgroundColor: COLORS[idx % COLORS.length],
                borderRadius: 4,
                transition: 'width 0.5s ease',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'flex-end',
                paddingRight: item.percentage > 10 ? 8 : 0,
              }}>
                {item.percentage > 15 && (
                  <Text style={{ color: '#fff', fontSize: 11, fontWeight: 600 }}>
                    {item.expectedRoi?.toFixed(1)}x ROI
                  </Text>
                )}
              </div>
            </div>
          </div>
        ))}
        <div style={{ marginTop: 12 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            总预算: ¥{totalBudget?.toLocaleString()} | 已分配: ¥
            {allocations.reduce((sum, a) => sum + (a.allocatedBudget || 0), 0).toLocaleString()}
          </Text>
        </div>
      </div>

      {/* 饼图 */}
      <div style={{ width: 220, textAlign: 'center' }}>
        <Text strong style={{ display: 'block', marginBottom: 8 }}>分配占比</Text>
        <svg width="180" height="180" viewBox="0 0 100 100">
          {slices.map((slice, idx) => (
            <path
              key={idx}
              d={slice.pathData}
              fill={slice.color}
              stroke="#fff"
              strokeWidth="0.5"
            />
          ))}
          <circle cx="50" cy="50" r="24" fill="#fff" />
          <text x="50" y="47" textAnchor="middle" fontSize="8" fill="#333" fontWeight="bold">
            {allocations.length}
          </text>
          <text x="50" y="57" textAnchor="middle" fontSize="4" fill="#888">
            项分配
          </text>
        </svg>
        {/* 图例 */}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, justifyContent: 'center', marginTop: 8 }}>
          {sorted.map((item, idx) => (
            <div key={item.initiativeId} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
              <span style={{
                width: 10, height: 10, borderRadius: '50%',
                backgroundColor: COLORS[idx % COLORS.length], display: 'inline-block',
              }} />
              <Text style={{ fontSize: 11 }}>{item.percentage?.toFixed(0)}%</Text>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default BudgetAllocationChart;
