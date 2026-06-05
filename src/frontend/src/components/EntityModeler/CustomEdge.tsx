import React from 'react';
import { BaseEdge, EdgeLabelRenderer, getBezierPath, type EdgeProps } from '@xyflow/react';
import type { EntityEdgeData } from './types';

/**
 * 自定义 React Flow 边：实体关系连线
 * 锁定边显示为灰色虚线，未锁定边显示为蓝色实线 + 箭头
 */
const CustomEdgeComponent: React.FC<EdgeProps> = ({
  id, sourceX, sourceY, targetX, targetY,
  sourcePosition, targetPosition, data, selected,
  markerEnd,
}) => {
  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX, sourceY, sourcePosition,
    targetX, targetY, targetPosition,
  });

  const edgeData = data as EntityEdgeData | undefined;
  const isLocked = edgeData?.locked;

  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        style={{
          stroke: isLocked ? '#999' : '#1677ff',
          strokeWidth: selected ? 3 : 2,
          strokeDasharray: isLocked ? '4,2' : 'none',
        }}
        markerEnd={isLocked ? undefined : markerEnd}
      />
      {edgeData?.label && (
        <EdgeLabelRenderer>
          <div style={{
            position: 'absolute',
            transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
            fontSize: 10, color: '#666', pointerEvents: 'all',
            background: '#fff', padding: '0 4px', borderRadius: 2,
          }}>
            {edgeData.label}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
};

export default CustomEdgeComponent;