/**
 * DAG 校验器 — 循环检测 + 拓扑排序 + 完整校验。
 */

interface Node { id: string; type?: string; name?: string; [key: string]: any }
interface Edge { id?: string; source: string; target: string; condition?: string; [key: string]: any }

interface ValidationResult {
  valid: boolean;
  errors: string[];
  warnings: string[];
}

/** 构建邻接表 */
function buildAdjList(nodes: Node[], edges: Edge[]): Map<string, string[]> {
  const graph = new Map<string, string[]>();
  nodes.forEach(n => graph.set(n.id, []));
  edges.forEach(e => {
    const neighbors = graph.get(e.source) || [];
    neighbors.push(e.target);
    graph.set(e.source, neighbors);
    if (!graph.has(e.target)) graph.set(e.target, []);
  });
  return graph;
}

/** DFS 循环检测 */
export function detectCycle(nodes: Node[], edges: Edge[]): string[] {
  const graph = buildAdjList(nodes, edges);
  const visited = new Set<string>();
  const stack = new Set<string>();
  const path: string[] = [];

  function dfs(nodeId: string): boolean {
    visited.add(nodeId);
    stack.add(nodeId);
    for (const neighbor of (graph.get(nodeId) || [])) {
      if (!visited.has(neighbor)) {
        if (dfs(neighbor)) { path.push(nodeId); return true; }
      } else if (stack.has(neighbor)) {
        path.push(nodeId, neighbor); return true;
      }
    }
    stack.delete(nodeId);
    return false;
  }

  for (const node of nodes) {
    if (!visited.has(node.id) && dfs(node.id)) return path.reverse();
  }
  return [];
}

/** Kahn 拓扑排序 */
export function topologicalSort(nodes: Node[], edges: Edge[]): string[] {
  const graph = buildAdjList(nodes, edges);
  const inDegree = new Map<string, number>();
  nodes.forEach(n => inDegree.set(n.id, 0));
  edges.forEach(e => inDegree.set(e.target, (inDegree.get(e.target) || 0) + 1));

  const queue: string[] = [];
  inDegree.forEach((d, id) => { if (d === 0) queue.push(id); });

  const result: string[] = [];
  while (queue.length > 0) {
    const id = queue.shift()!;
    result.push(id);
    for (const neighbor of (graph.get(id) || [])) {
      const d = (inDegree.get(neighbor) || 1) - 1;
      inDegree.set(neighbor, d);
      if (d === 0) queue.push(neighbor);
    }
  }
  if (result.length !== nodes.length) throw new Error('DAG contains cycle');
  return result;
}

/** 完整 DAG 校验 */
export function validateDAG(nodes: Node[], edges: Edge[]): ValidationResult {
  const errors: string[] = [];
  const warnings: string[] = [];

  // 1. 循环检测
  const cycle = detectCycle(nodes, edges);
  if (cycle.length > 0) errors.push(`Cycle detected: ${cycle.join(' → ')}`);

  // 2. 孤立节点检测
  const connected = new Set<string>();
  edges.forEach(e => { connected.add(e.source); connected.add(e.target); });
  nodes.forEach(n => {
    if (!connected.has(n.id) && n.type !== 'START' && n.type !== 'END') {
      warnings.push(`Node "${n.name || n.id}" is isolated (no connections)`);
    }
  });

  // 3. 缺少 END 节点
  if (!nodes.some(n => n.type === 'END')) {
    errors.push('Missing END node');
  }

  // 4. 缺少 START 节点
  if (!nodes.some(n => n.type === 'START')) {
    errors.push('Missing START node');
  }

  // 5. 无效转换检测
  const validTransitions: Record<string, string[]> = {
    START: ['AUDIENCE_FILTER', 'EVENT_TRIGGER'],
    AUDIENCE_FILTER: ['CONDITION', 'AI_SCORE', 'SEND_EMAIL', 'SEND_SMS', 'SPLIT'],
    CONDITION: ['SEND_EMAIL', 'SEND_SMS', 'DELAY', 'MERGE'],
    SEND_EMAIL: ['CONDITION', 'DELAY', 'END'],
    SEND_SMS: ['CONDITION', 'DELAY', 'END'],
    END: [],
  };
  edges.forEach(e => {
    const src = nodes.find(n => n.id === e.source);
    const tgt = nodes.find(n => n.id === e.target);
    if (src && tgt && src.type && tgt.type) {
      const allowed = validTransitions[src.type] || [];
      if (allowed.length > 0 && !allowed.includes(tgt.type) && tgt.type !== 'END') {
        warnings.push(`Transition "${src.type}" → "${tgt.type}" may not be supported`);
      }
    }
  });

  return { valid: errors.length === 0, errors, warnings };
}
