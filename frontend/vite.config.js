import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// In development the Vite server proxies /api to Spring Boot. The browser therefore
// sees a single origin (http://localhost:5173), so the HTTP-only session cookie and
// the XSRF-TOKEN cookie work without any cross-site cookie configuration.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  const backendUrl = env.VITE_BACKEND_URL || 'http://localhost:8080';
  const proxy = { '/api': { target: backendUrl, changeOrigin: false } };

  return {
    plugins: [react(), tailwindcss()],
    server: { port: 5173, strictPort: true, proxy },
    preview: { port: 4173, proxy },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: './src/test/setup.js',
      css: false,
      include: ['src/**/*.test.{js,jsx}'], // Playwright specs in e2e/ run with `npm run e2e`
    },
  };
});
