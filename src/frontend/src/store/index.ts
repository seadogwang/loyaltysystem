import { create } from 'zustand';

// ==================== 类型定义 ====================

export interface UserInfo {
  userId: string;
  username: string;
  displayName: string;
  avatar?: string;
}

export interface Program {
  programCode: string;
  displayName: string;
  status: 'ACTIVE' | 'INACTIVE';
  memberCount?: number;
  createdAt?: string;
}

interface AppStore {
  // ---- 租户上下文 ----
  currentProgramCode: string;
  programs: Program[];
  setCurrentProgram: (code: string) => void;
  setPrograms: (programs: Program[]) => void;

  // ---- 用户信息 ----
  user: UserInfo | null;
  permissions: string[];
  setUser: (user: UserInfo, permissions: string[]) => void;
  hasPermission: (perm: string) => boolean;

  // ---- 全局 UI ----
  sidebarCollapsed: boolean;
  toggleSidebar: () => void;
  globalLoading: boolean;
  setGlobalLoading: (loading: boolean) => void;

  // ---- 网络状态 ----
  online: boolean;
  setOnline: (online: boolean) => void;
}

// ==================== Store ====================

export const useAppStore = create<AppStore>((set, get) => ({
  // ---- 租户上下文 ----
  currentProgramCode: (() => {
    try { return sessionStorage.getItem('current_program_code') || 'PROG001'; }
    catch { return 'PROG001'; }
  })(),
  programs: [],
  setCurrentProgram: (code: string) => {
    try { sessionStorage.setItem('current_program_code', code); } catch {}
    set({ currentProgramCode: code });
    // 切换 Program 时触发全局刷新（通过 key 变化强制重渲染）
    window.dispatchEvent(new CustomEvent('programChanged', { detail: code }));
  },
  setPrograms: (programs) => set({ programs }),

  // ---- 用户信息 ----
  user: null,
  permissions: [],
  setUser: (user, permissions) => set({ user, permissions }),
  hasPermission: (perm: string) => {
    const { permissions } = get();
    return permissions.includes(perm) || permissions.includes('*');
  },

  // ---- 全局 UI ----
  sidebarCollapsed: false,
  toggleSidebar: () => set(s => ({ sidebarCollapsed: !s.sidebarCollapsed })),
  globalLoading: false,
  setGlobalLoading: (loading) => set({ globalLoading: loading }),

  // ---- 网络状态 ----
  online: typeof navigator !== 'undefined' ? navigator.onLine : true,
  setOnline: (online) => set({ online }),
}));