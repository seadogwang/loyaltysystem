import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
      // DrawDB 自托管：开发时代理到 DrawDB dev server (端口 5174)
      '/drawdb': {
        target: 'http://localhost:5174',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/drawdb/, ''),
      },
    },
  },
  resolve: {
    alias: {
      '@': '/src',
    },
  },
});