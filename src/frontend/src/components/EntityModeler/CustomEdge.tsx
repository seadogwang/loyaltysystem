import React from 'react';
import { EdgeLabelRenderer, Position, type EdgeProps } from '@xyflow/react';
import type { EntityEdgeData } from './types';

/**
 * 计算 ChartDB 风格 step-path：直线段(L) + 圆角转角(Q)
 */
function getStepPath(
  sourceX: number, sourceY: number,
  targetX: number, targetY: number,
  borderRadius: number = 6,
): string {
  const dx = targetX - sourceX;
  const dy = targetY - sourceY;
  const r = borderRadius;
  const signY = dy >= 0 ? 1 : -1;

  // 计算偏移后的中间水平段 Y 坐标
  const midY = sourceY + Math.abs(dy) * 0.3 - 20;
  const hY = sourceY + signY * Math.max(20, Math.min(80, Math.abs(dy) * 0.3));

  const sx = sourceX + 35;
  const tx = targetX - 35;

  if (Math.abs(dy) < 30) {
    // 几乎水平：简单直线
    return `M${sourceX},${sourceY}L${targetX},${targetY}`;
  }

  // 步骤路径：source → 水平出 → 圆角 → 垂直 → 圆角 → 水平入 → target
  return [
    `M${sourceX},${sourceY}`,
    `L${sx},${sourceY}`,
    `L${sx + r},${sourceY}`,
    `Q${sx + r},${sourceY} ${sx + r},${sourceY + signY * r}`,
    `L${sx + r},${targetY - signY * r}`,
    `Q${sx + r},${targetY} ${sx + r * 2},${targetY}`,
    `L${tx - r * 2},${targetY}`,
    `Q${tx - r},${targetY} ${tx - r},${targetY}`,
    `L${tx},${targetY}`,
    `L${targetX},${targetY}`,
  ].join(' ');
}

/**
 * 自定义 React Flow 边：ChartDB 风格 step-path + 基数标记
 */
const CustomEdgeComponent: React.FC<EdgeProps> = ({
  id, sourceX, sourceY, targetX, targetY,
  sourcePosition, targetPosition, data, selected,
}) => {
  const edgePath = getStepPath(sourceX, sourceY, targetX, targetY, 6);

  const edgeData = data as EntityEdgeData | undefined;
  const isLocked = edgeData?.locked;
  const edgeColor = selected ? '#ff4d4f' : isLocked ? '#999' : '#1677ff';
  const relType = edgeData?.type || '1:N';

  let leftCard = '1', rightCard = 'N';
  if (relType === '1:1') { leftCard = '1'; rightCard = '1'; }
  else if (relType === '1:N') { leftCard = '1'; rightCard = 'N'; }
  else if (relType === 'N:1') { leftCard = 'N'; rightCard = '1'; }
  else if (relType === 'N:M') { leftCard = 'N'; rightCard = 'M'; }

  const midX = (sourceX + targetX) / 2;
  const midY = (sourceY + targetY) / 2;

  return (
    <>
      {/* 透明宽路径 — 更容易点击 */}
      <path d={edgePath} fill="none" stroke="transparent" strokeWidth={20} className="react-flow__edge-interaction" />
      {/* 可见边 */}
      <path d={edgePath} fill="none" stroke={edgeColor} strokeWidth={selected ? 2.5 : 1.5}
        strokeDasharray={isLocked ? '4,2' : 'none'} />
      {/* 左侧基数 */}
      <circle cx={sourceX + 14} cy={sourceY} r="7" fill={selected ? '#ff4d4f' : '#1677ff'} stroke="#fff" strokeWidth={1} />
      <text x={sourceX + 14} y={sourceY + 3.5} textAnchor="middle" fontSize={8} fontWeight={600}
        fill="#fff" style={{ fontFamily: 'monospace' }}>{leftCard}</text>
      {/* 右侧基数 */}
      <circle cx={targetX - 14} cy={targetY} r="7" fill={selected ? '#ff4d4f' : '#1677ff'} stroke="#fff" strokeWidth={1} />
      <text x={targetX - 14} y={targetY + 3.5} textAnchor="middle" fontSize={8} fontWeight={600}
        fill="#fff" style={{ fontFamily: 'monospace' }}>{rightCard}</text>
      {/* 中间标签 */}
      {edgeData?.type && (
        <EdgeLabelRenderer>
          <div style={{
            position: 'absolute',
            transform: `translate(-50%, -50%) translate(${midX}px, ${midY}px)`,
            fontSize: 10, color: edgeColor, pointerEvents: 'all',
            background: '#fff', padding: '1px 6px', borderRadius: 3,
            border: `1px solid ${edgeColor}`, fontFamily: 'monospace', fontWeight: 600,
          }}>{relType}</div>
        </EdgeLabelRenderer>
      )}
    </>
  );
};

export default CustomEdgeComponent;