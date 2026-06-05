import React from 'react';
import { Spin, Empty, Result, Button } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';

/**
 * 通用页面容器 — 统一处理 loading / empty / error 三态
 *
 * 使用方式:
 *   <PageWrapper loading={loading} error={error} isEmpty={!data?.length}
 *     onRetry={fetchData}>
 *     {children}
 *   </PageWrapper>
 */
export interface PageWrapperProps {
  loading?: boolean;
  error?: string | null;
  isEmpty?: boolean;
  emptyText?: string;
  emptyAction?: React.ReactNode;
  onRetry?: () => void;
  children: React.ReactNode;
}

const PageWrapper: React.FC<PageWrapperProps> = ({
  loading, error, isEmpty,
  emptyText = '暂无数据',
  emptyAction,
  onRetry, children,
}) => {
  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', padding: 80 }}>
        <Spin size="large" tip="加载中..." />
      </div>
    );
  }

  if (error) {
    return (
      <Result
        status="error"
        title="加载失败"
        subTitle={error}
        extra={onRetry && (
          <Button type="primary" icon={<ReloadOutlined />} onClick={onRetry}>重试</Button>
        )}
      />
    );
  }

  if (isEmpty) {
    return (
      <Empty
        description={emptyText}
        style={{ padding: 60 }}
      >
        {emptyAction}
      </Empty>
    );
  }

  return <>{children}</>;
};

export default PageWrapper;