import api from './index';
import type { ApiResponse } from '../types';

// ==================== Types ====================

export interface CampaignWorkspace {
  id: string;
  programCode: string;
  name: string;
  description: string;
  status: string;
  activeGoalId: string | null;
  config: Record<string, any>;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface CampaignGoal {
  id: string;
  workspaceId: string;
  name: string;
  description: string;
  goalType: string;
  status: string;
  targetMetric: string;
  targetValue: number;
  currentValue: number;
  startTime: string;
  endTime: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  kpis?: CampaignKpi[];
  progress?: number;
}

export interface CampaignKpi {
  id: string;
  kpiType: string;
  targetValue: number;
  currentValue: number;
  weight: number;
}

export interface CampaignInitiative {
  id: string;
  workspaceId: string;
  goalId: string;
  name: string;
  description: string;
  initiativeType: string;
  status: string;
  priority: number;
  startTime: string;
  endTime: string;
  ruleConfig: Record<string, any>;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  kpis?: CampaignKpi[];
}

export interface CampaignPortfolio {
  id: string;
  workspaceId: string;
  name: string;
  description: string;
  status: string;
  optimizationMode: string;
  totalBudget: number;
  startTime: string;
  endTime: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  allocations?: PortfolioAllocation[];
}

export interface PortfolioAllocation {
  initiativeId: string;
  initiativeName: string;
  allocatedBudget: number;
  expectedROI: number;
  priorityWeight: number;
  percentage: number;
}

export interface WorkspaceContext {
  workspace: CampaignWorkspace;
  activeGoal: CampaignGoal | null;
  initiatives: CampaignInitiative[];
  portfolios: CampaignPortfolio[];
}

// ==================== Workspace API ====================

export async function createWorkspace(data: {
  name: string;
  programCode: string;
  description?: string;
  config?: Record<string, any>;
}) {
  const res = await api.post<ApiResponse<CampaignWorkspace>>('/campaign/workspace', data);
  return res.data.data;
}

export async function getWorkspace(workspaceId: string) {
  const res = await api.get<ApiResponse<CampaignWorkspace>>(`/campaign/workspace/${workspaceId}`);
  return res.data.data;
}

export async function updateWorkspace(workspaceId: string, data: Partial<CampaignWorkspace>) {
  const res = await api.put<ApiResponse<CampaignWorkspace>>(`/campaign/workspace/${workspaceId}`, data);
  return res.data.data;
}

export async function archiveWorkspace(workspaceId: string) {
  const res = await api.post<ApiResponse<CampaignWorkspace>>(`/campaign/workspace/${workspaceId}/archive`);
  return res.data.data;
}

export async function listWorkspaces(params?: { programCode?: string; userId?: string }) {
  const res = await api.get<ApiResponse<CampaignWorkspace[]>>('/campaign/workspace', { params });
  return res.data.data;
}

export async function loadWorkspaceContext(workspaceId: string) {
  const res = await api.get<ApiResponse<WorkspaceContext>>(`/campaign/workspace/${workspaceId}/context`);
  return res.data.data;
}

// ==================== Goal API ====================

export async function createGoal(data: {
  workspaceId: string;
  name: string;
  description?: string;
  goalType: string;
  targetMetric?: string;
  targetValue: number;
  startTime?: string;
  endTime?: string;
  kpis?: { kpiType: string; targetValue: number; weight?: number }[];
}) {
  const res = await api.post<ApiResponse<CampaignGoal>>('/campaign/goal', data);
  return res.data.data;
}

export async function getGoal(goalId: string) {
  const res = await api.get<ApiResponse<CampaignGoal>>(`/campaign/goal/${goalId}`);
  return res.data.data;
}

export async function getGoalsByWorkspace(workspaceId: string) {
  const res = await api.get<ApiResponse<CampaignGoal[]>>(`/campaign/goal/workspace/${workspaceId}`);
  return res.data.data;
}

export async function activateGoal(goalId: string) {
  const res = await api.post<ApiResponse<CampaignGoal>>(`/campaign/goal/${goalId}/activate`);
  return res.data.data;
}

export async function pauseGoal(goalId: string) {
  const res = await api.post<ApiResponse<CampaignGoal>>(`/campaign/goal/${goalId}/pause`);
  return res.data.data;
}

export async function completeGoal(goalId: string) {
  const res = await api.post<ApiResponse<CampaignGoal>>(`/campaign/goal/${goalId}/complete`);
  return res.data.data;
}

export async function archiveGoal(goalId: string) {
  const res = await api.post<ApiResponse<CampaignGoal>>(`/campaign/goal/${goalId}/archive`);
  return res.data.data;
}

// ==================== Initiative API ====================

export async function createInitiative(data: {
  goalId: string;
  name: string;
  description?: string;
  initiativeType: string;
  priority?: number;
  startTime?: string;
  endTime?: string;
  ruleConfig?: Record<string, any>;
  kpis?: { kpiType: string; targetValue: number; weight?: number }[];
}) {
  const res = await api.post<ApiResponse<CampaignInitiative>>('/campaign/initiative', data);
  return res.data.data;
}

export async function getInitiativesByGoal(goalId: string) {
  const res = await api.get<ApiResponse<CampaignInitiative[]>>(`/campaign/initiative/goal/${goalId}`);
  return res.data.data;
}

export async function getInitiativesByWorkspace(workspaceId: string) {
  const res = await api.get<ApiResponse<CampaignInitiative[]>>(`/campaign/initiative/workspace/${workspaceId}`);
  return res.data.data;
}

export async function activateInitiative(initiativeId: string) {
  const res = await api.post<ApiResponse<CampaignInitiative>>(`/campaign/initiative/${initiativeId}/activate`);
  return res.data.data;
}

export async function pauseInitiative(initiativeId: string) {
  const res = await api.post<ApiResponse<CampaignInitiative>>(`/campaign/initiative/${initiativeId}/pause`);
  return res.data.data;
}

// ==================== Portfolio API ====================

export async function createPortfolio(data: {
  workspaceId: string;
  name: string;
  description?: string;
  optimizationMode?: string;
  totalBudget: number;
  startTime?: string;
  endTime?: string;
}) {
  const res = await api.post<ApiResponse<CampaignPortfolio>>('/campaign/portfolio', data);
  return res.data.data;
}

export async function getPortfoliosByWorkspace(workspaceId: string) {
  const res = await api.get<ApiResponse<CampaignPortfolio[]>>(`/campaign/portfolio/workspace/${workspaceId}`);
  return res.data.data;
}

export async function optimizePortfolio(portfolioId: string) {
  const res = await api.post<ApiResponse<CampaignPortfolio>>(`/campaign/portfolio/${portfolioId}/optimize`);
  return res.data.data;
}

export async function lockPortfolio(portfolioId: string) {
  const res = await api.post<ApiResponse<CampaignPortfolio>>(`/campaign/portfolio/${portfolioId}/lock`);
  return res.data.data;
}

// ==================== Decision Engine API ====================

export interface CampaignCandidate {
  id: string;
  name: string;
  initiativeId: string;
  recommendedBudget: number;
  minBudget: number;
  maxBudget: number;
  expectedROI: number;
  opportunityScore: number;
  strategicWeight: number;
  recencyBoost: number;
  channel: string;
  segment: string;
}

export interface AllocationItem {
  candidateId: string;
  candidateName: string;
  initiativeId: string;
  allocatedBudget: number;
  expectedROI: number;
  priorityScore: number;
  percentage: number;
}

export interface AllocationResult {
  portfolioId: string;
  totalBudget: number;
  totalExpectedROI: number;
  allocations: AllocationItem[];
}

export interface SimulationResult {
  candidateId: string;
  exposureRate: number;
  behaviorRate: number;
  conversionRate: number;
  expectedRevenue: number;
  expectedROI: number;
  estimatedReach: number;
  estimatedConversions: number;
  modelDetails: Record<string, any>;
}

/** 预算分配 */
export async function allocateBudget(candidates: CampaignCandidate[], totalBudget: number) {
  const res = await api.post<ApiResponse<AllocationResult>>('/campaign/decision/allocate', { candidates, totalBudget });
  return res.data.data;
}

/** 带约束分配 */
export async function allocateWithConstraints(
  candidates: CampaignCandidate[], totalBudget: number, channelCapacity: Record<string, number>
) {
  const res = await api.post<ApiResponse<AllocationResult>>('/campaign/decision/allocate/constrained', {
    candidates, totalBudget, channelCapacity,
  });
  return res.data.data;
}

/** 冲突仲裁排序 */
export async function prioritizeCandidates(candidates: CampaignCandidate[]) {
  const res = await api.post<ApiResponse<CampaignCandidate[]>>('/campaign/decision/prioritize', candidates);
  return res.data.data;
}

/** 模拟预测 */
export async function simulateCampaign(candidate: CampaignCandidate, audienceSize: number) {
  const res = await api.post<ApiResponse<SimulationResult>>('/campaign/decision/simulate', { candidate, audienceSize });
  return res.data.data;
}

/** 批量模拟 */
export async function simulateBatch(candidates: CampaignCandidate[], audienceSize: number) {
  const res = await api.post<ApiResponse<SimulationResult[]>>('/campaign/decision/simulate/batch', { candidates, audienceSize });
  return res.data.data;
}

/** 注意力预算检查 */
export async function checkAttentionBudget(userId: string, channel: string) {
  const res = await api.get(`/campaign/decision/attention/${userId}/${channel}`);
  return res.data.data;
}

// ==================== Decision Engine v2 API ====================

export interface DecisionConstraints {
  channelCapacity?: Record<string, number>;
  maxFrequencyPerUser?: number;
  minROIThreshold?: number;
  blacklistSegments?: string[];
}

export interface DecisionRequest {
  workspaceId: string;
  portfolioId: string;
  goalId: string;
  constraints?: DecisionConstraints;
}

export interface AllocationDetail {
  initiativeId: string;
  initiativeName: string;
  allocatedBudget: number;
  expectedRoi: number;
  percentage: number;
  executionOrder: number;
  priorityScore: number;
  opportunityCount: number;
  targetUserCount: number | null;
  status: string;
}

export interface ArbitrationSummary {
  userConflicts: number;
  budgetConflicts: number;
  channelConflicts: number;
  resolved: number;
}

export interface DecisionResultResponse {
  decisionId: string;
  workspaceId: string;
  portfolioId: string;
  goalId: string;
  decisionType: string;
  status: string;
  totalBudget: number;
  totalAllocated: number;
  expectedTotalRoi: number;
  conflictsResolved: number;
  rejectedCandidates: number;
  allocations: AllocationDetail[];
  arbitrationSummary: ArbitrationSummary;
  createdBy: string;
  createdAt: string;
  appliedAt: string;
}

export interface DecisionSummary {
  decisionId: string;
  workspaceId: string;
  portfolioId: string;
  decisionType: string;
  status: string;
  totalBudget: number;
  totalAllocated: number;
  expectedTotalRoi: number;
  allocationCount: number;
  conflictsResolved: number;
  createdBy: string;
  createdAt: string;
  appliedAt: string;
}

/** 执行完整决策 */
export async function executeDecision(request: DecisionRequest) {
  const res = await api.post<ApiResponse<DecisionResultResponse>>('/campaign/decision/execute', request);
  return res.data.data;
}

/** 应用决策 */
export async function applyDecision(decisionId: string) {
  const res = await api.post<ApiResponse<DecisionResultResponse>>(`/campaign/decision/${decisionId}/apply`);
  return res.data.data;
}

/** 获取最新决策 */
export async function getLatestDecision(portfolioId: string) {
  const res = await api.get<ApiResponse<DecisionResultResponse>>('/campaign/decision/latest', { params: { portfolioId } });
  return res.data.data;
}

/** 获取决策详情 */
export async function getDecisionDetail(decisionId: string) {
  const res = await api.get<ApiResponse<DecisionResultResponse>>(`/campaign/decision/${decisionId}`);
  return res.data.data;
}

/** 获取历史决策 */
export async function getDecisionHistory(workspaceId: string, page = 0, size = 20) {
  const res = await api.get('/campaign/decision/history', { params: { workspaceId, page, size } });
  return res.data.data;
}

/** 回滚决策 */
export async function rollbackDecision(decisionId: string, reason?: string) {
  const res = await api.post(`/campaign/decision/${decisionId}/rollback`, { reason: reason || 'Manual rollback' });
  return res.data.data;
}

// ==================== Canvas DAG API ====================

export interface CampaignPlan {
  id: string;
  workspaceId: string;
  goalId: string;
  initiativeId: string;
  name: string;
  description: string;
  status: string;
  totalBudget: number;
  expectedRoi: number;
  strategyJson: string;
  allocationJson: string;
  graphJson: string;
  forecastJson: string;
  zeebeProcessId: string;
  zeebeVersion: number;
  zeebeInstanceKey: number;
  createdBy: string;
  approvedBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface CanvasNode {
  id: string;
  type: string;
  label?: string;
  config?: Record<string, any>;
  x?: number;
  y?: number;
}

export interface CanvasEdge {
  id: string;
  source: string;
  target: string;
  condition?: string;
  label?: string;
}

export interface CanvasDag {
  nodes: CanvasNode[];
  edges: CanvasEdge[];
}

export interface GraphValidationResult {
  valid: boolean;
  errors: string[];
  warnings: string[];
}

export interface AIRequest {
  goal: string;
  description?: string;
  budget?: string;
  audience?: string;
  channel?: string;
  additionalInstructions?: string;
}

export interface NodeTypeInfo {
  type: string;
  label: string;
  category: string;
  description: string;
  configFields: string[];
}

/** 创建计划 */
export async function createPlan(plan: Partial<CampaignPlan>) {
  const res = await api.post<ApiResponse<CampaignPlan>>('/campaign/canvas/plan', plan);
  return res.data.data;
}

/** 获取计划 */
export async function getPlan(planId: string) {
  const res = await api.get<ApiResponse<CampaignPlan>>(`/campaign/canvas/plan/${planId}`);
  return res.data.data;
}

/** 保存 DAG */
export async function saveDag(planId: string, dag: CanvasDag) {
  const res = await api.put<ApiResponse<CampaignPlan>>(`/campaign/canvas/plan/${planId}/dag`, dag);
  return res.data.data;
}

/** 校验 DAG */
export async function validateDag(dag: CanvasDag) {
  const res = await api.post<ApiResponse<GraphValidationResult>>('/campaign/canvas/validate', dag);
  return res.data.data;
}

/** 编译 BPMN */
export async function compileToBpmn(planId: string) {
  const res = await api.get<ApiResponse<string>>(`/campaign/canvas/plan/${planId}/compile`);
  return res.data.data;
}

/** AI 生成 DAG */
export async function aiGenerate(request: AIRequest) {
  const res = await api.post<ApiResponse<CanvasDag>>('/campaign/canvas/ai-generate', request);
  return res.data.data;
}

/** 获取节点类型 */
export async function getNodeTypes() {
  const res = await api.get<ApiResponse<NodeTypeInfo[]>>('/campaign/canvas/node-types');
  return res.data.data;
}

// ==================== Content & Approval API ====================

export interface CampaignContentAsset {
  id: string;
  programCode: string;
  assetName: string;
  assetType: string;
  channel: string;
  subjectLine: string;
  bodyText: string;
  variableSchema: string;
  status: string;
  createdBy: string;
  approvedBy: string;
  approvedAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface CampaignApprovalRecord {
  id: string;
  assetId: string;
  planId: string;
  requesterId: string;
  approverId: string;
  action: string;
  comment: string;
  createdAt: string;
}

/** 创建素材 */
export async function createAsset(asset: Partial<CampaignContentAsset>) {
  const res = await api.post<ApiResponse<CampaignContentAsset>>('/campaign/content/assets', asset);
  return res.data.data;
}

/** 获取素材 */
export async function getAsset(assetId: string) {
  const res = await api.get<ApiResponse<CampaignContentAsset>>(`/campaign/content/assets/${assetId}`);
  return res.data.data;
}

/** 更新素材 */
export async function updateAsset(assetId: string, asset: Partial<CampaignContentAsset>) {
  const res = await api.put<ApiResponse<CampaignContentAsset>>(`/campaign/content/assets/${assetId}`, asset);
  return res.data.data;
}

/** 查询素材列表 */
export async function listAssets(programCode: string, type?: string) {
  const params: any = { programCode };
  if (type) params.type = type;
  const res = await api.get<ApiResponse<CampaignContentAsset[]>>('/campaign/content/assets', { params });
  return res.data.data;
}

/** 提交审批 */
export async function submitForApproval(assetId: string, requesterId: string, comment?: string) {
  const res = await api.post(`/campaign/content/assets/${assetId}/submit`, { requesterId, comment });
  return res.data.data;
}

/** 审批通过 */
export async function approveAsset(assetId: string, approverId: string, comment?: string) {
  const res = await api.post(`/campaign/content/assets/${assetId}/approve`, { approverId, comment });
  return res.data.data;
}

/** 驳回 */
export async function rejectAsset(assetId: string, approverId: string, reason: string) {
  const res = await api.post(`/campaign/content/assets/${assetId}/reject`, { approverId, reason });
  return res.data.data;
}

/** 待审批列表 */
export async function getPendingAssets(programCode: string) {
  const res = await api.get<ApiResponse<CampaignContentAsset[]>>('/campaign/content/assets/pending', { params: { programCode } });
  return res.data.data;
}

/** 审批历史 */
export async function getApprovalHistory(assetId: string) {
  const res = await api.get(`/campaign/content/assets/${assetId}/history`);
  return res.data.data;
}

/** 渲染模板 */
export async function renderTemplate(assetId: string, variables: Record<string, string>) {
  const res = await api.post<ApiResponse<string>>(`/campaign/content/assets/${assetId}/render`, variables);
  return res.data.data;
}

/** 合规校验 */
export async function validateContent(assetId: string) {
  const res = await api.post(`/campaign/content/assets/${assetId}/validate`);
  return res.data.data;
}

// ==================== Intervention API ====================

export interface CampaignInterventionCommand {
  id: string;
  programCode: string;
  planId: string;
  targetNodeId: string;
  commandType: string;
  reason: string;
  operatorId: string;
  createdAt: string;
  executedAt: string;
}

/** 暂停活动 */
export async function pauseCampaign(planId: string, body: { operatorId: string; reason?: string }) {
  const res = await api.post(`/campaign/intervention/${planId}/pause`, body);
  return res.data.data;
}

/** 恢复活动 */
export async function resumeCampaign(planId: string, body: { operatorId: string; reason?: string }) {
  const res = await api.post(`/campaign/intervention/${planId}/resume`, body);
  return res.data.data;
}

/** 取消活动 */
export async function cancelCampaign(planId: string, body: { operatorId: string; reason?: string }) {
  const res = await api.post(`/campaign/intervention/${planId}/cancel`, body);
  return res.data.data;
}

/** 跳过节点 */
export async function skipNode(planId: string, nodeId: string, body: { operatorId: string; reason?: string }) {
  const res = await api.post(`/campaign/intervention/${planId}/skip/${nodeId}`, body);
  return res.data.data;
}

/** 覆盖节点配置 */
export async function overrideConfig(planId: string, nodeId: string, body: any) {
  const res = await api.put(`/campaign/intervention/${planId}/config/${nodeId}`, body);
  return res.data.data;
}

/** 获取干预历史 */
export async function getInterventions(planId: string) {
  const res = await api.get(`/campaign/intervention/${planId}/interventions`);
  return res.data.data;
}

/** 获取运行状态 */
export async function getPlanStatus(planId: string) {
  const res = await api.get(`/campaign/intervention/${planId}/status`);
  return res.data.data;
}

/** 紧急限流 */
export async function emergencyThrottle(tenantId: string, factor: number) {
  const res = await api.post(`/campaign/intervention/throttle/${tenantId}`, { factor });
  return res.data;
}

/** 取消限流 */
export async function removeThrottle(tenantId: string) {
  const res = await api.delete(`/campaign/intervention/throttle/${tenantId}`);
  return res.data;
}

/** Worker 执行前检查 */
export async function checkBeforeExecution(planId: string, nodeId: string, tenantId: string) {
  const res = await api.post(`/campaign/intervention/${planId}/check/${nodeId}?tenantId=${tenantId}`);
  return res.data.data;
}

// ==================== Execution Engine API ====================

/** 部署流程 */
export async function deployPlan(planId: string) {
  const res = await api.post(`/campaign/execution/${planId}/deploy`);
  return res.data.data;
}

/** 启动流程 */
export async function startExecution(planId: string) {
  const res = await api.post(`/campaign/execution/${planId}/start`);
  return res.data.data;
}

/** 执行节点 */
export async function executeNode(instanceKey: number, jobType: string, variables?: Record<string, any>) {
  const res = await api.post(`/campaign/execution/instance/${instanceKey}/execute/${jobType}`, variables || {});
  return res.data.data;
}

/** 完成流程 */
export async function completeInstance(instanceKey: number) {
  const res = await api.post(`/campaign/execution/instance/${instanceKey}/complete`);
  return res.data.data;
}

/** 获取实例 */
export async function getInstance(instanceKey: number) {
  const res = await api.get(`/campaign/execution/instance/${instanceKey}`);
  return res.data.data;
}

/** 获取 Plan 的流程实例 */
export async function getInstanceByPlan(planId: string) {
  const res = await api.get(`/campaign/execution/${planId}/instance`);
  return res.data.data;
}

/** 获取 Worker 列表 */
export async function getWorkers() {
  const res = await api.get('/campaign/execution/workers');
  return res.data.data;
}

/** 获取 Job 类型 */
export async function getJobTypes() {
  const res = await api.get('/campaign/execution/job-types');
  return res.data.data;
}

/** 获取执行状态 */
export async function getExecutionStatus(planId: string) {
  const res = await api.get(`/campaign/execution/status/${planId}`);
  return res.data.data;
}

/** 暂停执行 */
export async function pauseExecution(planId: string) {
  const res = await api.post(`/campaign/execution/${planId}/pause`);
  return res.data.data;
}

/** 恢复执行 */
export async function resumeExecution(planId: string) {
  const res = await api.post(`/campaign/execution/${planId}/resume`);
  return res.data.data;
}

// ==================== Feedback & Events API ====================

export interface FeedbackMetrics {
  id: string; planId: string;
  predictedRoi: number; predictedConversion: number; predictedRevenue: number;
  actualRoi: number; actualConversion: number; actualRevenue: number; actualCost: number;
  roiDeviation: number; conversionDeviation: number;
  totalExposures: number; totalConversions: number; uniqueUsers: number;
  calculatedAt: string;
}

export interface ModelDrift {
  id: string; modelName: string; modelVersion: string;
  driftDetected: boolean; driftScore: number; threshold: number;
  sampleSize: number; meanPredicted: number; meanActual: number;
  mae: number; rmse: number; detectedAt: string; status: string;
}

export interface StrategyAdjustment {
  id: string; planId: string; workspaceId: string;
  adjustmentType: string; triggerEvent: string;
  reason: string; expectedImprovement: number;
  status: string; createdBy: string; createdAt: string; appliedAt: string;
}

/** 获取反馈指标 */
export async function getFeedbackMetrics(planId: string) {
  const res = await api.get(`/campaign/feedback/${planId}`);
  return res.data.data;
}

/** 触发反馈计算 */
export async function triggerFeedbackCalculation(planId: string) {
  const res = await api.post(`/campaign/feedback/${planId}/calculate`);
  return res.data.data;
}

/** 获取漂移记录 */
export async function getDriftHistory(modelName = 'roi_prediction') {
  const res = await api.get('/campaign/feedback/drift', { params: { modelName } });
  return res.data.data;
}

/** 获取策略调整 */
export async function getStrategyAdjustments(workspaceId: string) {
  const res = await api.get('/campaign/feedback/adjustments', { params: { workspaceId } });
  return res.data.data;
}

/** 应用策略调整 */
export async function applyAdjustment(id: string) {
  const res = await api.post(`/campaign/feedback/adjustments/${id}/apply`);
  return res.data.data;
}

// ==================== Opportunity Intelligence API ====================

export interface Opportunity {
  id: string;
  memberId: string;
  segmentCode: string;
  opportunityType: string;
  score: number;
  churnProbability: number;
  upliftScore: number;
  conversionProbability: number;
  rfmScore: number;
  externalInfluence: number;
  externalSignalIds: string[];
  recommendedAction: string;
  recommendedChannel: string;
  confidence: number;
  status: string;
  source: string;
  detectedAt: string;
  expiresAt: string;
}

export interface ExternalSignalItem {
  id: string;
  signalType: string;
  severity: string;
  sourceSkill: string;
  targetEntity: string;
  title: string;
  description: string;
  impactFactor: number;
  affectedSegments: string;
  recommendedAction: string;
  expiresAt: string;
  createdAt: string;
}

/** 发现机会 */
export async function discoverOpportunities(workspaceId: string, goalId: string, maxResults?: number) {
  const res = await api.post('/campaign/opportunity/discover', { workspaceId, goalId, maxResults: maxResults || 10000 });
  return res.data.data;
}

/** 查询机会列表 */
export async function queryOpportunities(workspaceId: string, goalId: string, params?: {
  types?: string[]; minScore?: number; status?: string; limit?: number; offset?: number;
}) {
  const queryParams: any = { workspaceId, goalId, limit: params?.limit || 100, offset: params?.offset || 0 };
  if (params?.types?.length) queryParams.types = params.types.join(',');
  if (params?.minScore !== undefined) queryParams.minScore = params.minScore;
  if (params?.status) queryParams.status = params.status;
  const res = await api.get('/campaign/opportunity', { params: queryParams });
  return res.data.data;
}

/** 消费机会 */
export async function consumeOpportunity(opportunityId: string) {
  const res = await api.post(`/campaign/opportunity/${opportunityId}/consume`);
  return res.data.data;
}

/** 获取外部信号 */
export async function getExternalSignals(programCode: string, severity?: string) {
  const params: any = { programCode };
  if (severity) params.severity = severity;
  const res = await api.get('/campaign/opportunity/external-signal', { params });
  return res.data.data;
}

/** 手动触发技能 */
export async function executeSkill(skillName: string, context?: Record<string, any>) {
  const res = await api.post('/campaign/opportunity/external-signal/execute', { skillName, context: context || {} });
  return res.data.data;
}

/** 获取外部信号影响系数 */
export async function calculateExternalWeight(programCode: string, segmentCode: string) {
  const res = await api.post('/campaign/opportunity/external-signal/weight', { programCode, segmentCode });
  return res.data.data;
}

// ==================== Simulation & Optimization API ====================

export interface BaselineResult {
  segmentCode: string;
  totalMembers: number;
  convertedMembers: number;
  conversionRate: number;
  avgOrderValue: number;
  estimatedRevenue: number;
  periodDays: number;
  segmentBreakdown: Record<string, number>;
  calculatedAt: string;
}

export interface SimulationFullResult {
  id: string;
  workspaceId: string;
  goalId: string;
  simulationType: string;
  name: string;
  baselineConversion: number;
  predictedConversion: number;
  predictedRevenue: number;
  predictedRoi: number;
  upliftPct: number;
  confidence: number;
  exposureCount: number;
  behaviorCount: number;
  conversionCount: number;
  segmentBreakdown: string;
  channelBreakdown: string;
  status: string;
  createdAt: string;
}

export interface OptimizationAllocationDetail {
  initiativeId: string;
  initiativeName: string;
  allocatedBudget: number;
  expectedRoi: number;
  percentage: number;
}

export interface OptimizationResultResponse {
  optimizationId: string;
  optimizationType: string;
  status: string;
  expectedRoi: number;
  expectedRevenue: number;
  improvementPct: number;
  iterationCount: number;
  convergenceTimeMs: number;
  baselineRoi: number;
  allocationDetails: OptimizationAllocationDetail[];
  createdAt: string;
}

/** 运行模拟 */
export async function runSimulation(params: {
  workspaceId: string; goalId: string; name?: string;
  segmentCode: string; channel?: string; offerStrength?: number; offerMatch?: number; budget?: number;
}) {
  const res = await api.post<ApiResponse<SimulationFullResult>>('/campaign/simulation/run', params);
  return res.data.data;
}

/** 计算基线 */
export async function calculateBaseline(goalId: string, segmentCode: string) {
  const res = await api.post<ApiResponse<BaselineResult>>('/campaign/simulation/baseline', { goalId, segmentCode });
  return res.data.data;
}

/** 获取模拟结果 */
export async function getSimulationResult(id: string) {
  const res = await api.get<ApiResponse<SimulationFullResult>>(`/campaign/simulation/${id}`);
  return res.data.data;
}

/** 获取模拟历史 */
export async function getSimulationHistory(workspaceId: string, page = 0, size = 20) {
  const res = await api.get('/campaign/simulation/history', { params: { workspaceId, page, size } });
  return res.data.data;
}

/** 运行优化 */
export async function runOptimization(params: {
  portfolioId: string; optimizationType?: string;
  constraints?: { maxBudget?: number; maxGenerations?: number; populationSize?: number; channelCapacity?: Record<string, number> };
}) {
  const res = await api.post<ApiResponse<OptimizationResultResponse>>('/campaign/optimization/run', params);
  return res.data.data;
}

/** 获取优化结果 */
export async function getOptimizationResult(id: string) {
  const res = await api.get<ApiResponse<OptimizationResultResponse>>(`/campaign/optimization/${id}`);
  return res.data.data;
}

/** 获取优化历史 */
export async function getOptimizationHistory(portfolioId: string, page = 0, size = 20) {
  const res = await api.get('/campaign/optimization/history', { params: { portfolioId, page, size } });
  return res.data.data;
}

