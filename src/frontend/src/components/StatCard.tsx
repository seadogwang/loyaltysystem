import React from 'react';
import { Card, Statistic, Typography } from 'antd';
import { ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons';

const { Text } = Typography;

/**
 * 统计卡片组件 — 用于仪表盘指标展示
 */
export interface StatCardProps {
  title: string;
  value: number | string;
  prefix?: React.ReactNode;
  trend?: number;            // 正数上升，负数下降
  trendLabel?: string;       // 趋势描述，如"较昨日"
  onClick?: () => void;
  loading?: boolean;
  style?: React.CSSProperties;
}

const StatCard: React.FC<StatCardProps> = ({
  title, value, prefix, trend, trendLabel, onClick, loading, style,
}) => {
  return (
    <Card
      hoverable={!!onClick}
      onClick={onClick}
      loading={loading}
      style={{ ...style, cursor: onClick ? 'pointer' : 'default' }}
    >
      <Statistic
        title={title}
        value={value}
        prefix={prefix}
        valueStyle={{ fontSize: 28 }}
      />
      {trend !== undefined && (
        <div style={{ marginTop: 8 }}>
          <Text type={trend >= 0 ? 'success' : 'danger'}>
            {trend >= 0 ? <ArrowUpOutlined /> : <ArrowDownOutlined />}
            {' '}{Math.abs(trend)}%
          </Text>
          {trendLabel && <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>{trendLabel}</Text>}
        </div>
      )}
    </Card>
  );
};

export default StatCard;