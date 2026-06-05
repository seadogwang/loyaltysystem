import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { message, Spin } from 'antd';
import { useAppStore } from '../store';
import api from '../api';

// 全局样式重置
const INJECT_STYLE = `
  html, body, #root { margin: 0 !important; padding: 0 !important; border: none !important; border-top: none !important; outline: none !important; background: #fff; }
`;

// ==================== 点击编辑组件 ====================

const ClickToEditText: React.FC<{
  value: string; onChange: (v: string) => void; style?: React.CSSProperties;
}> = ({ value, onChange, style }) => {
  const [editing, setEditing] = useState(false);
  return editing ? (
    <input
      style={{ width: 100, height: 30, padding: '0 6px', border: '1px solid #1a1a1a', borderRadius: 6, fontSize: 13, color: '#1a1a1a', outline: 'none', background: '#fff', fontFamily: 'inherit', ...style }}
      value={value}
      onChange={e => onChange(e.target.value)}
      onBlur={() => setEditing(false)} autoFocus
      onFocus={e => e.target.select()}
      onKeyDown={e => { if (e.key === 'Enter') setEditing(false); }}
    />
  ) : (
    <span onClick={() => setEditing(true)}
      style={{ fontSize: 13, color: '#1a1a1a', fontWeight: 500, cursor: 'pointer', borderBottom: '1px dashed #d9d9d9', padding: '2px 0', textAlign: 'left', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', ...style }}
      title="点击编辑">{value}</span>
  );
};

const ClickToEdit: React.FC<{
  value: number; label: string; unit?: string; width?: number; onChange: (v: number) => void;
}> = ({ value, label, unit, width = 60, onChange }) => {
  const [editing, setEditing] = useState(false);
  return editing ? (
    <input
      style={{ width, height: 30, padding: '0 6px', border: '1px solid #1a1a1a', borderRadius: 6, fontSize: 13, color: '#1a1a1a', outline: 'none', background: '#fff', fontFamily: 'inherit', textAlign: 'center' }}
      type="number" min={0} value={value}
      onChange={e => onChange(Number(e.target.value))}
      onBlur={() => setEditing(false)} autoFocus
      onFocus={e => e.target.select()}
      onKeyDown={e => { if (e.key === 'Enter') setEditing(false); }}
    />
  ) : (
    <span onClick={() => setEditing(true)}
      style={{ fontSize: 13, color: '#1a1a1a', cursor: 'pointer', borderBottom: '1px dashed #d9d9d9', padding: '2px 4px' }}
      title={`点击编辑${label}`}>{value}{unit ? ` ${unit}` : ''}</span>
  );
};

const ClickToEditSelect: React.FC<{
  value: string; options: { label: string; value: string }[]; onChange: (v: string) => void;
}> = ({ value, options, onChange }) => {
  const [editing, setEditing] = useState(false);
  const label = options.find(o => o.value === value)?.label || value;
  return editing ? (
    <select value={value}
      onChange={e => { onChange(e.target.value); setEditing(false); }}
      onBlur={() => setEditing(false)} autoFocus
      style={{ height: 30, padding: '0 4px', border: '1px solid #1a1a1a', borderRadius: 6, fontSize: 12, color: '#1a1a1a', outline: 'none', background: '#fff', fontFamily: 'inherit', cursor: 'pointer' }}>
      {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
  ) : (
    <span onClick={() => setEditing(true)}
      style={{ fontSize: 13, color: '#1a1a1a', cursor: 'pointer', borderBottom: '1px dashed #d9d9d9', padding: '2px 4px' }}
      title="点击切换">{label}</span>
  );
};

// ==================== SVG 图标 ====================

const IconClub = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#1a1a1a" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="3" width="7" height="7" rx="1" />
    <rect x="14" y="3" width="7" height="7" rx="1" />
    <rect x="3" y="14" width="7" height="7" rx="1" />
    <rect x="14" y="14" width="7" height="7" rx="1" />
  </svg>
);

const IconPoints = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#1a1a1a" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="9" />
    <path d="M12 6v6l4 2" />
    <circle cx="8" cy="8" r="1" fill="#1a1a1a" />
    <circle cx="16" cy="8" r="1" fill="#1a1a1a" />
    <circle cx="12" cy="16" r="1" fill="#1a1a1a" />
  </svg>
);

const IconTier = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#1a1a1a" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 2l3.09 6.26L22 9.27l-5 4.87L18.18 22 12 18.56 5.82 22 7 14.14 2 9.27l6.91-1.01L12 2z" />
  </svg>
);

const IconCheck = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="20 6 9 17 4 12" />
  </svg>
);

const IconArrowLong = ({ color = '#1a1a1a' }: { color?: string }) => (
  <svg width="80" height="16" viewBox="0 0 80 16" fill="none">
    <line x1="0" y1="8" x2="70" y2="8" stroke={color} strokeWidth="1.5" strokeLinecap="round" />
    <polyline points="62,3 72,8 62,13" stroke={color} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
  </svg>
);

const IconArrowBtn = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M5 12h14" /><path d="M12 5l7 7-7 7" />
  </svg>
);

// ==================== 步骤定义 ====================

interface StepDef {
  num: number;
  key: string;
  label: string;
  icon: React.ReactNode;
  description: string;
  why: string[];
}

const stepDefs: StepDef[] = [
  {
    num: 1, key: 'club', label: '俱乐部设置', icon: <IconClub />,
    description: '俱乐部是忠诚度计划的基础容器，实现会员、积分、规则的数据完全隔离。',
    why: [],
  },
  {
    num: 2, key: 'points', label: '积分类型设置', icon: <IconPoints />,
    description: '定义积分账户体系——消费积分、等级成长值、虚拟货币等。积分是忠诚度体系的"货币"，必须先定义才能进行发放和核销。',
    why: ['每种积分类型可独立配置：是否可兑换、是否算等级', '支持信用透支额度、负数挂账等金融级特性', '设置后仍可随时修改'],
  },
  {
    num: 3, key: 'tier', label: '等级设置', icon: <IconTier />,
    description: '定义会员成长路径——银卡、金卡、铂金等不同等级享有不同权益。等级驱动会员活跃度，自动升降级结合规则引擎实现实时变更。',
    why: ['不同等级享有不同权益，激励消费升级', '结合积分规则引擎，实现实时自动升降级', '设置后仍可随时修改'],
  },
];

// ==================== 样式 ====================

const S = {
  page: {
    minHeight: '100vh',
    background: '#fff',
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
    display: 'flex',
    flexDirection: 'column' as const,
  },
  container: {
    maxWidth: 960,
    margin: '0 auto',
    padding: '36px 40px',
    width: '100%',
  },
  header: {
    textAlign: 'center' as const,
    marginBottom: 28,
    flexShrink: 0,
  },
  title: {
    fontSize: 26,
    fontWeight: 700,
    color: '#1a1a1a',
    margin: 0,
    marginBottom: 6,
    letterSpacing: -0.5,
  } as React.CSSProperties,
  subtitle: {
    fontSize: 15,
    color: '#888',
    lineHeight: 1.6,
    margin: 0,
  },

  // 进度条
  progressWrap: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'flex-start',
    gap: 0,
    marginBottom: 28,
    flexShrink: 0,
  } as React.CSSProperties,
  progressItem: {
    display: 'flex',
    flexDirection: 'column' as const,
    alignItems: 'center',
    width: 160,
    position: 'relative' as const,
  },
  arrowWrap: {
    display: 'flex',
    alignItems: 'center',
    paddingTop: 14,
  } as React.CSSProperties,
  progressDot: (active: boolean, done: boolean) => ({
    width: 40,
    height: 40,
    borderRadius: '50%',
    background: done ? '#1a1a1a' : active ? '#fff' : '#fff',
    border: done ? '2px solid #1a1a1a' : active ? '2px solid #1a1a1a' : '2px solid #d9d9d9',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: 17,
    fontWeight: 600,
    color: done ? '#fff' : active ? '#1a1a1a' : '#bbb',
    marginBottom: 8,
    transition: 'all 0.3s',
    position: 'relative' as const,
    zIndex: 2,
  } as React.CSSProperties),
  progressLine: (active: boolean) => ({
    position: 'absolute' as const,
    top: 20,
    left: '50%',
    width: 140,
    height: 2,
    background: active ? '#1a1a1a' : '#e8e8e8',
    zIndex: 1,
    transition: 'background 0.3s',
  } as React.CSSProperties),
  progressLabel: (active: boolean, done: boolean) => ({
    fontSize: 13,
    color: active || done ? '#1a1a1a' : '#bbb',
    fontWeight: active ? 600 : 400,
    transition: 'color 0.3s',
  }),
  progressUnderline: {
    width: 36,
    height: 2,
    background: '#1a1a1a',
    marginTop: 5,
    borderRadius: 1,
  } as React.CSSProperties,

  // 内容区
  contentCard: {
    border: '1px solid #e8e8e8',
    borderRadius: 12,
    padding: '24px 28px',
    background: '#fff',
    maxHeight: 'calc(100vh - 280px)',
    overflow: 'auto',
  } as React.CSSProperties,
  stepTitle: {
    fontSize: 19,
    fontWeight: 600,
    color: '#1a1a1a',
    marginBottom: 10,
    display: 'flex',
    alignItems: 'center',
    gap: 10,
    flexShrink: 0,
  } as React.CSSProperties,
  stepDesc: {
    fontSize: 14,
    color: '#666',
    lineHeight: 1.7,
    marginBottom: 14,
    flexShrink: 0,
  },
  whyTitle: {
    fontSize: 12,
    color: '#999',
    textTransform: 'uppercase' as const,
    letterSpacing: 1,
    marginBottom: 8,
    flexShrink: 0,
  },
  whyItem: {
    display: 'flex',
    alignItems: 'flex-start',
    gap: 8,
    marginBottom: 4,
    fontSize: 13,
    color: '#999',
    lineHeight: 1.5,
    flexShrink: 0,
  } as React.CSSProperties,
  whyDot: {
    width: 5,
    height: 5,
    borderRadius: '50%',
    background: '#1a1a1a',
    marginTop: 7,
    flexShrink: 0,
    opacity: 0.3,
  } as React.CSSProperties,

  // 表单
  formSection: {
    marginTop: 20,
    paddingTop: 16,
    borderTop: '1px solid #f0f0f0',
    display: 'flex',
    flexDirection: 'column' as const,
  } as React.CSSProperties,
  formGroup: {
    marginBottom: 12,
    flexShrink: 0,
  } as React.CSSProperties,
  label: {
    display: 'block',
    fontSize: 14,
    fontWeight: 500,
    color: '#1a1a1a',
    marginBottom: 5,
  } as React.CSSProperties,
  input: {
    width: '100%',
    height: 42,
    padding: '0 14px',
    border: '1px solid #e0e0e0',
    borderRadius: 8,
    fontSize: 15,
    color: '#1a1a1a',
    outline: 'none',
    background: '#fff',
    fontFamily: 'inherit',
    boxSizing: 'border-box' as const,
    transition: 'border-color 0.2s',
  } as React.CSSProperties,
  textarea: {
    width: '100%',
    minHeight: 70,
    padding: '10px 14px',
    border: '1px solid #e0e0e0',
    borderRadius: 8,
    fontSize: 15,
    color: '#1a1a1a',
    outline: 'none',
    background: '#fff',
    fontFamily: 'inherit',
    resize: 'vertical' as const,
    boxSizing: 'border-box' as const,
    transition: 'border-color 0.2s',
  } as React.CSSProperties,
  btn: {
    height: 42,
    padding: '0 28px',
    background: '#1a1a1a',
    border: 'none',
    borderRadius: 10,
    color: '#fff',
    fontSize: 15,
    fontWeight: 500,
    cursor: 'pointer',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'flex-end',
    gap: 6,
    fontFamily: 'inherit',
    transition: 'opacity 0.2s',
    flexShrink: 0,
  } as React.CSSProperties,
  btnOutline: {
    height: 42,
    padding: '0 28px',
    background: '#fff',
    border: '1px solid #d9d9d9',
    borderRadius: 10,
    color: '#1a1a1a',
    fontSize: 15,
    fontWeight: 500,
    cursor: 'pointer',
    display: 'inline-flex',
    alignItems: 'center',
    gap: 6,
    fontFamily: 'inherit',
    transition: 'all 0.2s',
    flexShrink: 0,
  } as React.CSSProperties,
  doneCard: {
    textAlign: 'center' as const,
    padding: '32px 0',
  },
  smallInput: {
    width: 110,
    height: 42,
    padding: '0 10px',
    border: '1px solid #e0e0e0',
    borderRadius: 8,
    fontSize: 15,
    color: '#1a1a1a',
    outline: 'none',
    background: '#fff',
    fontFamily: 'inherit',
    boxSizing: 'border-box' as const,
    transition: 'border-color 0.2s',
    textAlign: 'center' as const,
  } as React.CSSProperties,
  switchRow: {
    display: 'flex',
    alignItems: 'center',
    gap: 10,
    flexShrink: 0,
  } as React.CSSProperties,
  toggle: (on: boolean) => ({
    width: 44,
    height: 24,
    borderRadius: 12,
    background: on ? '#1a1a1a' : '#e0e0e0',
    cursor: 'pointer',
    position: 'relative' as const,
    transition: 'background 0.2s',
    flexShrink: 0,
  } as React.CSSProperties),
  toggleDot: (on: boolean) => ({
    width: 20,
    height: 20,
    borderRadius: '50%',
    background: '#fff',
    position: 'absolute' as const,
    top: 2,
    left: on ? 22 : 2,
    transition: 'left 0.2s',
    boxShadow: '0 1px 3px rgba(0,0,0,0.2)',
  } as React.CSSProperties),
  inlineLabel: {
    fontSize: 13,
    color: '#1a1a1a',
    userSelect: 'none' as const,
  },
  btnRow: {
    display: 'flex',
    gap: 12,
    marginTop: 20,
    flexShrink: 0,
  } as React.CSSProperties,
};

// ==================== 表单组件 ====================

interface ToggleProps {
  checked: boolean;
  onChange: (v: boolean) => void;
}

const Toggle: React.FC<ToggleProps> = ({ checked, onChange }) => (
  <div style={S.toggle(checked)} onClick={() => onChange(!checked)}>
    <div style={S.toggleDot(checked)} />
  </div>
);

// ==================== 主组件 ====================

const Onboarding: React.FC = () => {
  const navigate = useNavigate();
  const currentProgramCode = useAppStore(s => s.currentProgramCode);

  // 注入全局样式移除默认边距
  useEffect(() => {
    const style = document.createElement('style');
    style.textContent = INJECT_STYLE;
    document.head.appendChild(style);
    return () => { document.head.removeChild(style); };
  }, []);

  // 当前步骤 (0-based)
  const [activeStep, setActiveStep] = useState(0);
  // 已完成的步骤
  const [doneSteps, setDoneSteps] = useState<number[]>([]);
  // 加载状态
  const [saving, setSaving] = useState(false);

  // --- Step 1: 俱乐部设置 ---
  const [programCode, setProgramCode] = useState('');
  const [programName, setProgramName] = useState('');
  const [programDesc, setProgramDesc] = useState('');

  // --- Step 2: 积分类型设置 ---
  const [pointTypes, setPointTypes] = useState([
    { typeCode: 'REWARD', name: '消费积分', redeemable: true, tierRelevant: false, transferable: false, allowNegative: false, overdraftLimit: 0, creditLimit: 0, expiryMode: 'CALENDAR_YEARS', expiryValue: 1, visible: true },
    { typeCode: 'TIER', name: '等级成长值', redeemable: false, tierRelevant: true, transferable: false, allowNegative: false, overdraftLimit: 0, creditLimit: 0, expiryMode: 'FIXED_DAYS', expiryValue: 0, visible: true },
    { typeCode: 'CREDIT', name: '授信积分', redeemable: true, tierRelevant: false, transferable: false, allowNegative: true, overdraftLimit: 0, creditLimit: 5000, expiryMode: 'FIXED_DAYS', expiryValue: 0, visible: false },
  ]);

  // --- Step 3: 等级设置 ---
  const [tiers, setTiers] = useState([
    { tierCode: 'BASE', tierName: '普通会员', pointType: 'TIER', minPoints: 0, maxPoints: 10000, validityValue: 0, validityMode: 'FIXED_DAYS' },
    { tierCode: 'SILVER', tierName: '银卡会员', pointType: 'TIER', minPoints: 10000, maxPoints: 50000, validityValue: 12, validityMode: 'CALENDAR_MONTHS' },
    { tierCode: 'GOLD', tierName: '金卡会员', pointType: 'TIER', minPoints: 50000, maxPoints: 100000, validityValue: 12, validityMode: 'CALENDAR_MONTHS' },
    { tierCode: 'PLATINUM', tierName: '铂金会员', pointType: 'TIER', minPoints: 100000, maxPoints: 99999999, validityValue: 12, validityMode: 'CALENDAR_MONTHS' },
  ]);

  const isStepDone = (step: number) => doneSteps.includes(step);
  const canAccessStep = (step: number) => step === 0 || isStepDone(step - 1);

  // Step 1: 保存 Program
  const handleSaveProgram = async () => {
    if (!programCode.trim()) { message.warning('请输入俱乐部代码'); return; }
    if (!programName.trim()) { message.warning('请输入俱乐部名称'); return; }
    setSaving(true);
    try {
      await api.post('/admin/programs', {
        programCode: programCode.trim(),
        displayName: programName.trim(),
        description: programDesc.trim(),
        status: 'ACTIVE',
      });
      useAppStore.getState().setCurrentProgram(programCode.trim());
      message.success('俱乐部创建成功');
      setDoneSteps(prev => [...prev, 0]);
      setActiveStep(1);
    } catch (e: any) {
      console.warn('[Onboarding] 后端不可用，本地保存:', e.message);
      useAppStore.getState().setCurrentProgram(programCode.trim() || 'PROG001');
      message.success('俱乐部创建成功（本地模式）');
      setDoneSteps(prev => [...prev, 0]);
      setActiveStep(1);
    } finally {
      setSaving(false);
    }
  };

  // Step 2: 保存积分类型
  const handleSavePoints = async () => {
    if (pointTypes.length === 0) { message.warning('请至少添加一种积分类型'); return; }
    setSaving(true);
    try {
      await api.put('/admin/tiers', { pointTypes });
      message.success('积分类型设置完成');
      setDoneSteps(prev => [...prev, 1]);
      setActiveStep(2);
    } catch (e: any) {
      console.warn('[Onboarding] 后端不可用，本地保存:', e.message);
      message.success('积分类型设置完成（本地模式）');
      setDoneSteps(prev => [...prev, 1]);
      setActiveStep(2);
    } finally {
      setSaving(false);
    }
  };

  // Step 3: 保存等级
  const handleSaveTiers = async () => {
    if (tiers.length === 0) { message.warning('请至少设置一个等级'); return; }
    setSaving(true);
    try {
      await api.put('/admin/tiers', { tiers });
      message.success('等级设置完成');
      setDoneSteps(prev => [...prev, 2]);
    } catch (e: any) {
      console.warn('[Onboarding] 后端不可用，本地保存:', e.message);
      message.success('等级设置完成（本地模式）');
      setDoneSteps(prev => [...prev, 2]);
    } finally {
      setSaving(false);
    }
  };

  const allDone = isStepDone(0) && isStepDone(1) && isStepDone(2);


  const removePointType = (idx: number) => {
    setPointTypes(prev => prev.filter((_, i) => i !== idx));
  };

  const updateTier = (idx: number, field: string, value: any) => {
    setTiers(prev => prev.map((t, i) => i === idx ? { ...t, [field]: value } : t));
  };

  return (
    <div style={S.page}>
      <div style={S.container}>
        {/* 标题 */}
        <div style={S.header}>
          <h1 style={S.title}>欢迎使用忠诚度管理平台</h1>
          <p style={S.subtitle}>
            在开始使用之前，请按顺序完成以下 3 个基础设置。<br />
            这些配置决定了忠诚度计划如何运作，只需设置一次。
          </p>
        </div>

        {/* 进度条 */}
        <div style={S.progressWrap}>
          {stepDefs.map((s, i) => (
            <React.Fragment key={s.key}>
              <div style={S.progressItem}>
                <div
                  style={S.progressDot(activeStep === i, isStepDone(i))}
                  onClick={() => { if (canAccessStep(i)) setActiveStep(i); }}
                >
                  {isStepDone(i) ? <IconCheck /> : s.num}
                </div>
                <div style={S.progressLabel(activeStep === i, isStepDone(i))}>{s.label}</div>
                {activeStep === i && <div style={S.progressUnderline} />}
              </div>
              {i < stepDefs.length - 1 && (
                <div style={S.arrowWrap}>
                  <IconArrowLong color={isStepDone(i) ? '#1a1a1a' : '#d9d9d9'} />
                </div>
              )}
            </React.Fragment>
          ))}
        </div>

        {/* ====== Step 1: 俱乐部设置 ====== */}
        {activeStep === 0 && (
          <div style={S.contentCard}>
            <div style={S.stepTitle}>
              {stepDefs[0].icon} {stepDefs[0].label}
            </div>
            <div style={S.stepDesc}>{stepDefs[0].description}</div>

            {stepDefs[0].why.length > 0 && (
              <>
                <div style={S.whyTitle}>为什么需要设置：</div>
                {stepDefs[0].why.map((w, j) => (
                  <div key={j} style={S.whyItem}><div style={S.whyDot} />{w}</div>
                ))}
              </>
            )}

            {/* 表单 */}
            <div style={S.formSection}>
              <div style={S.formGroup}>
                <label style={S.label}>俱乐部代码 *</label>
                <input
                  style={S.input}
                  value={programCode}
                  onChange={e => setProgramCode(e.target.value)}
                  placeholder="例如: BRAND-A"
                  onFocus={e => e.currentTarget.style.borderColor = '#1a1a1a'}
                  onBlur={e => e.currentTarget.style.borderColor = '#e0e0e0'}
                />
              </div>
              <div style={S.formGroup}>
                <label style={S.label}>显示名称 *</label>
                <input
                  style={S.input}
                  value={programName}
                  onChange={e => setProgramName(e.target.value)}
                  placeholder="例如: 品牌A 忠诚度计划"
                  onFocus={e => e.currentTarget.style.borderColor = '#1a1a1a'}
                  onBlur={e => e.currentTarget.style.borderColor = '#e0e0e0'}
                />
              </div>
              <div style={S.formGroup}>
                <label style={S.label}>描述（可选）</label>
                <textarea
                  style={S.textarea}
                  value={programDesc}
                  onChange={e => setProgramDesc(e.target.value)}
                  placeholder="简要描述该忠诚度计划的业务场景..."
                  onFocus={e => e.currentTarget.style.borderColor = '#1a1a1a'}
                  onBlur={e => e.currentTarget.style.borderColor = '#e0e0e0'}
                />
              </div>
              <button
                style={S.btn}
                onClick={handleSaveProgram}
                disabled={saving}
                onMouseEnter={e => e.currentTarget.style.opacity = '0.85'}
                onMouseLeave={e => e.currentTarget.style.opacity = '1'}
              >
                {saving ? <Spin size="small" /> : null}
                {saving ? '保存中...' : '保存并继续'}
                {!saving && <IconArrowBtn />}
              </button>
            </div>
          </div>
        )}

        {/* ====== Step 2: 积分类型设置 ====== */}
        {activeStep === 1 && (
          <div style={S.contentCard}>
            <div style={S.stepTitle}>
              {stepDefs[1].icon} {stepDefs[1].label}
              <span onClick={() => setActiveStep(0)}
                style={{ fontSize: 13, color: '#999', cursor: 'pointer', marginLeft: 'auto' }}
                onMouseEnter={e => e.currentTarget.style.color = '#1a1a1a'}
                onMouseLeave={e => e.currentTarget.style.color = '#999'}
              >&lt;&lt; 上一步</span>
            </div>
            <div style={S.stepDesc}>{stepDefs[1].description}</div>

            {/* 积分类型表格 */}
            <div style={S.formSection}>
              {pointTypes.map((pt, i) => {
                  const isCredit = pt.typeCode === 'CREDIT';
                  const mode = isCredit ? 'credit' : pt.redeemable ? 'redeem' : pt.tierRelevant ? 'tier' : 'redeem';
                  return (
                <div key={i} style={{ padding: '10px 0', borderBottom: '1px solid #e0e0e0' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, overflowX: 'auto' }}>
                    {isCredit ? (
                      <span style={{ fontSize: 13, color: '#1a1a1a', fontWeight: 500, width: 90, flexShrink: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{pt.name}</span>
                    ) : (
                      <ClickToEditText value={pt.name} onChange={v => setPointTypes(prev => prev.map((p, j) => j === i ? { ...p, name: v } : p))} style={{ width: 90, flexShrink: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', textAlign: 'left' }} />
                    )}
                    {isCredit ? (
                      <code style={{ fontSize: 12, color: '#666', width: 90, flexShrink: 0 }}>{pt.typeCode}</code>
                    ) : (
                      <ClickToEditText value={pt.typeCode} onChange={v => setPointTypes(prev => prev.map((p, j) => j === i ? { ...p, typeCode: v } : p))} style={{ fontSize: 12, fontFamily: 'monospace', fontWeight: 400, width: 90, flexShrink: 0 }} />
                    )}
                    <select value={mode} onChange={e => {
                        const m = e.target.value;
                        setPointTypes(prev => prev.map((p, j) => j === i ? { ...p, redeemable: m === 'redeem', tierRelevant: m === 'tier', typeCode: m === 'credit' ? 'CREDIT' : (m === 'tier' ? 'TIER' : 'REWARD') } : p));
                      }}
                      style={{ height: 36, padding: '0 6px', border: '1px solid #e0e0e0', borderRadius: 8, fontSize: 13, color: '#1a1a1a', outline: 'none', background: '#fff', fontFamily: 'inherit', cursor: 'pointer', flexShrink: 0 }}>
                      <option value="redeem">兑换</option>
                      <option value="tier">等级</option>
                      <option value="credit">信用</option>
                    </select>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 440, flexShrink: 0 }}>
                    {isCredit ? (
                      <div style={{ display: 'flex', alignItems: 'center', gap: 4, flexShrink: 0 }}>
                        <span style={S.inlineLabel}>额度</span>
                        <ClickToEdit value={pt.creditLimit} label="授信额度" unit="分" width={70}
                          onChange={v => setPointTypes(prev => prev.map((p, j) => j === i ? { ...p, creditLimit: v } : p))}
                        />
                      </div>
                    ) : (
                      <>
                        {mode === 'redeem' && (
                          <>
                            <div style={S.switchRow}>
                              <Toggle checked={pt.allowNegative} onChange={v => setPointTypes(prev => prev.map((p, j) => j === i ? { ...p, allowNegative: v } : p))} />
                              <span style={S.inlineLabel}>负分</span>
                            </div>
                            {pt.allowNegative && (
                              <select value={pt.overdraftLimit > 0 ? 'max' : 'once'}
                                onChange={e => setPointTypes(prev => prev.map((p, j) => j === i ? { ...p, overdraftLimit: e.target.value === 'max' ? 1000 : 0 } : p))}
                                style={{ height: 30, padding: '0 6px', border: '1px solid #e0e0e0', borderRadius: 6, fontSize: 12, color: '#1a1a1a', outline: 'none', background: '#fff', fontFamily: 'inherit', cursor: 'pointer', flexShrink: 0 }}>
                                <option value="once">单次</option>
                                <option value="max">上限</option>
                              </select>
                            )}
                            {pt.allowNegative && pt.overdraftLimit > 0 && (
                              <ClickToEdit value={pt.overdraftLimit} label="负分上限" unit="分" width={60}
                                onChange={v => setPointTypes(prev => prev.map((p, j) => j === i ? { ...p, overdraftLimit: v } : p))}
                              />
                            )}
                          </>
                        )}
                        <div style={S.switchRow}>
                          <Toggle checked={pt.visible} onChange={v => setPointTypes(prev => prev.map((p, j) => j === i ? { ...p, visible: v } : p))} />
                          <span style={S.inlineLabel}>可见</span>
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 4, flexShrink: 0 }}>
                          <span style={S.inlineLabel}>有效期</span>
                          <ClickToEdit value={pt.expiryValue} label="有效期" width={40}
                            onChange={v => setPointTypes(prev => prev.map((p, j) => j === i ? { ...p, expiryValue: v } : p))}
                          />
                          <ClickToEditSelect value={pt.expiryMode}
                            options={[
                              { label: '固定天数', value: 'FIXED_DAYS' },
                              { label: '自然月', value: 'CALENDAR_MONTHS' },
                              { label: '自然年', value: 'CALENDAR_YEARS' },
                            ]}
                            onChange={v => setPointTypes(prev => prev.map((p, j) => j === i ? { ...p, expiryMode: v } : p))}
                          />
                        </div>
                      </>
                    )}
                    </div>
                    <button onClick={() => removePointType(i)}
                      style={{ background: 'none', border: 'none', color: '#ccc', cursor: 'pointer', fontSize: 18, padding: '0 4px', lineHeight: 1, marginLeft: 'auto', flexShrink: 0 }}>
                      ×
                    </button>
                  </div>
                </div>
                  );
                })}

              {/* 添加按钮 */}
              <div style={{ marginTop: 14, textAlign: 'center', marginBottom: 20 }}>
                <span onClick={() => {
                    setPointTypes(prev => [...prev, {
                      typeCode: `NEW_${prev.length + 1}`, name: '新积分类型',
                      redeemable: true, tierRelevant: false, transferable: false, allowNegative: false,
                      overdraftLimit: 0, creditLimit: 0,
                      expiryMode: 'CALENDAR_YEARS', expiryValue: 1, visible: true,
                    }]);
                  }}
                  onMouseEnter={e => e.currentTarget.style.color = '#1a1a1a'}
                  onMouseLeave={e => e.currentTarget.style.color = '#999'}
                  style={{ fontSize: 13, color: '#999', cursor: 'pointer', borderBottom: '1px dashed #d9d9d9', padding: '2px 0' }}
                >
                  + 添加积分类型
                </span>
              </div>

              <button style={S.btn}
                  onClick={handleSavePoints}
                  disabled={saving}
                  onMouseEnter={e => e.currentTarget.style.opacity = '0.85'}
                  onMouseLeave={e => e.currentTarget.style.opacity = '1'}
                >
                  {saving ? <Spin size="small" /> : null}
                  {saving ? '保存中...' : '保存并继续'}
                  {!saving && <IconArrowBtn />}
                </button>
              </div>
          </div>
        )}

        {/* ====== Step 3: 等级设置 ====== */}
        {activeStep === 2 && (
          <div style={S.contentCard}>
            <div style={S.stepTitle}>
              {stepDefs[2].icon} {stepDefs[2].label}
              <span onClick={() => setActiveStep(1)}
                style={{ fontSize: 13, color: '#999', cursor: 'pointer', marginLeft: 'auto' }}
                onMouseEnter={e => e.currentTarget.style.color = '#1a1a1a'}
                onMouseLeave={e => e.currentTarget.style.color = '#999'}
              >&lt;&lt; 上一步</span>
            </div>
            <div style={S.stepDesc}>{stepDefs[2].description}</div>

            {/* 等级列表 */}
            <div style={S.formSection}>
              {tiers.map((t, i) => (
                <div key={i} style={{
                  display: 'flex', alignItems: 'center', gap: 10,
                  padding: '10px 0', borderBottom: '1px solid #e0e0e0',
                  overflowX: 'auto',
                }}>
                  <ClickToEditText value={t.tierCode} onChange={v => updateTier(i, 'tierCode', v)} style={{ width: 70, textAlign: 'left' }} />
                  <ClickToEditText value={t.tierName} onChange={v => updateTier(i, 'tierName', v)} style={{ width: 90, textAlign: 'left' }} />
                  <select value={t.pointType}
                    onChange={e => updateTier(i, 'pointType', e.target.value)}
                    style={{ height: 36, padding: '0 6px', border: '1px solid #e0e0e0', borderRadius: 8, fontSize: 12, color: '#1a1a1a', outline: 'none', background: '#fff', fontFamily: 'inherit', cursor: 'pointer', flexShrink: 0 }}>
                    {pointTypes.filter(pt => pt.tierRelevant).map(pt => <option key={pt.typeCode} value={pt.typeCode}>{pt.name}</option>)}
                  </select>
                  <span style={{ fontSize: 12, color: '#999', flexShrink: 0 }}>成长值</span>
                  <ClickToEdit value={t.minPoints} label="最低成长值" width={60}
                    onChange={v => updateTier(i, 'minPoints', v)}
                  />
                  <span style={{ color: '#ccc', flexShrink: 0 }}>—</span>
                  <ClickToEdit value={t.maxPoints} label="最高成长值" width={60}
                    onChange={v => updateTier(i, 'maxPoints', v)}
                  />
                  <div style={{ display: 'flex', alignItems: 'center', gap: 4, flexShrink: 0 }}>
                    <span style={{ fontSize: 12, color: '#999' }}>有效期</span>
                    <ClickToEdit value={t.validityValue} label="有效期" width={40}
                      onChange={v => updateTier(i, 'validityValue', v)}
                    />
                    <ClickToEditSelect value={t.validityMode}
                      options={[
                        { label: '固定天数', value: 'FIXED_DAYS' },
                        { label: '自然月', value: 'CALENDAR_MONTHS' },
                        { label: '自然年', value: 'CALENDAR_YEARS' },
                      ]}
                      onChange={v => updateTier(i, 'validityMode', v)}
                    />
                  </div>
                  <span style={{ fontSize: 11, color: '#bbb', flexShrink: 0 }}>顺序 {i + 1}</span>
                  <button onClick={() => setTiers(prev => prev.filter((_, j) => j !== i))}
                    style={{ background: 'none', border: 'none', color: '#ccc', cursor: 'pointer', fontSize: 18, padding: '0 4px', lineHeight: 1, flexShrink: 0 }}>
                    ×
                  </button>
                </div>
              ))}

              <div style={{ marginTop: 14, textAlign: 'center', marginBottom: 20 }}>
                <span onClick={() => {
                    setTiers(prev => [...prev, {
                      tierCode: `NEW_${prev.length + 1}`, tierName: '新等级',
                      pointType: 'TIER', minPoints: 0, maxPoints: 0,
                      validityValue: 12, validityMode: 'CALENDAR_MONTHS',
                    }]);
                  }}
                  onMouseEnter={e => e.currentTarget.style.color = '#1a1a1a'}
                  onMouseLeave={e => e.currentTarget.style.color = '#999'}
                  style={{ fontSize: 13, color: '#999', cursor: 'pointer', borderBottom: '1px dashed #d9d9d9', padding: '2px 0' }}
                >
                  + 添加等级
                </span>
              </div>

              <button style={S.btn}
                onClick={handleSaveTiers}
                disabled={saving}
                onMouseEnter={e => e.currentTarget.style.opacity = '0.85'}
                onMouseLeave={e => e.currentTarget.style.opacity = '1'}
              >
                {saving ? <Spin size="small" /> : null}
                {saving ? '保存中...' : '完成设置'}
                {!saving && <IconArrowBtn />}
              </button>
            </div>
          </div>
        )}

        {/* 全部完成后 */}
        {allDone && (
          <div style={{ ...S.contentCard, ...S.doneCard }}>
            <div style={{
              width: 56, height: 56, borderRadius: '50%', background: '#1a1a1a',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              margin: '0 auto 20px',
            }}>
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <polyline points="20 6 9 17 4 12" />
              </svg>
            </div>
            <h3 style={{ fontSize: 20, fontWeight: 600, color: '#1a1a1a', marginBottom: 8 }}>
              基础设置完成！
            </h3>
            <p style={{ fontSize: 14, color: '#999', marginBottom: 28 }}>
              俱乐部、积分类型、等级阶梯已配置完毕，现在可以开始使用忠诚度管理平台了。
            </p>
            <button
              style={{ ...S.btn, height: 44, padding: '0 32px', fontSize: 15 }}
              onClick={() => navigate('/dashboard')}
              onMouseEnter={e => e.currentTarget.style.opacity = '0.85'}
              onMouseLeave={e => e.currentTarget.style.opacity = '1'}
            >
              进入仪表盘
              <IconArrowBtn />
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default Onboarding;