import React from 'react';
import { BaseEdge, EdgeLabelRenderer, getSmoothStepPath, type EdgeProps } from '@xyflow/react';
import type { EntityEdgeData } from './types';

/**
 * 自定义 React Flow 边：实体关系连线
 * 使用 step-path 绕行实体，显示 1:N 基数标记
 */
const CustomEdgeComponent: React.FC<EdgeProps> = ({
  id, sourceX, sourceY, targetX, targetY,
  sourcePosition, targetPosition, data, selected,
}) => {
  const [edgePath, labelX, labelY] = getSmoothStepPath({
    sourceX, sourceY, sourcePosition,
    targetX, targetY, targetPosition,
    borderRadius: 8,
  });

  const edgeData = data as EntityEdgeData | undefined;
  const isLocked = edgeData?.locked;
  const edgeColor = selected ? '#ff4d4f' : isLocked ? '#999' : '#1677ff';
  const relType = edgeData?.type || '1:N';

  // 解析关系类型为左右两侧的基数
  let leftCardinality = '1';
  let rightCardinality = 'N';
  if (relType === '1:1') { leftCardinality = '1'; rightCardinality = '1'; }
  else if (relType === '1:N') { leftCardinality = '1'; rightCardinality = 'N'; }
  else if (relType === 'N:1') { leftCardinality = 'N'; rightCardinality = '1'; }
  else if (relType === 'N:M') { leftCardinality = 'N'; rightCardinality = 'M'; }

  return (
    <>
      {/* 可见的交互路径（透明，宽的，用于点击） */}
      <path
        d={edgePath}
        fill="none"
        stroke="transparent"
        strokeWidth={20}
        className="react-flow__edge-interaction"
      />
      {/* 可见的边 */}
      <path
        d={edgePath}
        fill="none"
        stroke={edgeColor}
        strokeWidth={selected ? 3 : 2}
        strokeDasharray={isLocked ? '4,2' : 'none'}
      />
      {/* 左侧基数标记 */}
      <circle cx={sourceX + 14} cy={sourceY} r="8"
        fill={selected ? '#ff4d4f' : '#1677ff'}
        stroke="#fff" strokeWidth={1}
      />
      <text x={sourceX + 14} y={sourceY + 4}
        textAnchor="middle" fontSize={9} fontWeight={600}
        fill="#fff"
        style={{ fontFamily: 'monospace' }}
      >{leftCardinality}</text>
      {/* 右侧基数标记 */}
      <circle cx={targetX - 14} cy={targetY} r="8"
        fill={selected ? '#ff4d4f' : '#1677ff'}
        stroke="#fff" strokeWidth={1}
      />
      <text x={targetX - 14} y={targetY + 4}
        textAnchor="middle" fontSize={9} fontWeight={600}
        fill="#fff"
        style={{ fontFamily: 'monospace' }}
      >{rightCardinality}</text>
      {/* 中间标签 */}
      {edgeData?.type && (
        <EdgeLabelRenderer>
          <div style={{
            position: 'absolute',
            transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
            fontSize: 10, color: edgeColor, pointerEvents: 'all',
            background: '#fff', padding: '1px 6px', borderRadius: 3,
            border: `1px solid ${edgeColor}`,
            fontFamily: 'monospace', fontWeight: 600,
          }}>
            {relType}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
};

export default CustomEdgeComponent;