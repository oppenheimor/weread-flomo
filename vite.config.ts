import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const base = env.APP_BASE_PATH || '/';
  if (!/^\/(?:[a-zA-Z0-9_-]+\/)*$/.test(base)) throw new Error('APP_BASE_PATH 必须是以 / 开始和结束的路径');
  return {
    base,
    plugins: [react()],
    server: {
      host: '127.0.0.1',
      port: Number(env.FRONTEND_PORT || 5173),
      // 登录的同源校验要求固定地址，端口变动需同步 APP_PUBLIC_ORIGIN。
      strictPort: true,
      cors: false,
      fs: { deny: ['.env', '.env.*', '*.{crt,pem}', '**/.git/**', '**/data/**', '**/backend/**', '**/deploy/**'] },
      proxy: { [`${base}api`]: { target: 'http://127.0.0.1:18081', changeOrigin: false } },
    },
    preview: { host: '127.0.0.1', port: 4173, strictPort: true },
  };
});
