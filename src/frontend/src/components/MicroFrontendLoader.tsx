import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Spin, Card, Alert, Typography } from 'antd';
import { LoadingOutlined } from '@ant-design/icons';

const { Text } = Typography;

/**
 * 微前端动态加载器骨架 — Ch8.2.1。
 *
 * <p>当 Schema 中的 x-component-props 指定了 microApp 地址时，
 * 通过动态 <script> 加载远程组件并挂载。
 *
 * <p>生产环境应使用 qiankun 或 Module Federation：
 * <pre>
 *   registerMicroApps([{ name: 'store-selector', entry: '//localhost:7100', ... }])
 * </pre>
 */
export interface MicroAppProps {
  microApp: string;           // 远程 JS Bundle URL
  moduleName: string;         // 导出的组件名
  onLoad?: () => void;
  onError?: (e: Error) => void;
}

const MicroFrontendLoader: React.FC<MicroAppProps & Record<string, unknown>> = ({
  microApp, moduleName, onLoad, onError, ...props
}) => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const loadedRef = useRef(false);

  useEffect(() => {
    if (loadedRef.current) return;
    loadedRef.current = true;

    const script = document.createElement('script');
    script.src = microApp;
    script.async = true;

    script.onload = () => {
      setLoading(false);
      onLoad?.();
      // 生产环境: qiankun loadMicroApp() 或 Module Federation 动态 import()
      try {
        const Component = (window as unknown as Record<string, unknown>)[moduleName];
        if (Component && containerRef.current) {
          // React 18 createRoot rendering would happen here
        }
      } catch (e) {
        console.warn('[MicroFrontend] 动态组件挂载: qiankun/Module Federation 未配置');
      }
    };

    script.onerror = () => {
      const err = new Error(`Failed to load micro app: ${microApp}`);
      setError(err.message);
      setLoading(false);
      onError?.(err);
    };

    document.head.appendChild(script);
    return () => { document.head.removeChild(script); };
  }, [microApp, moduleName, onLoad, onError]);

  if (loading) return <Spin indicator={<LoadingOutlined />} tip={`加载远程组件: ${moduleName}`} />;
  if (error) return <Alert type="error" message="组件加载失败" description={error} showIcon />;

  return (
    <Card size="small" style={{ border: '1px dashed #d9d9d9', background: '#fff' }}>
      <Text type="secondary" style={{ fontSize: 12 }}>
        🔌 微前端组件: {moduleName} ({microApp})
      </Text>
      <div ref={containerRef} style={{ minHeight: 100 }} {...props as Record<string, unknown>} />
    </Card>
  );
};

export default MicroFrontendLoader;