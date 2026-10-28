import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path';
import { fileURLToPath } from 'url';
import process from 'node:process';
import tailwindcss from '@tailwindcss/vite';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const devPort = Number(process.env.VITE_DEV_SERVER_PORT || process.env.SIRH_DEV_WEB_PORT || 5173);

// https://vite.dev/config/
export default defineConfig({
  server: {
    host: process.env.VITE_DEV_SERVER_HOST || '127.0.0.1',
    port: devPort,
    strictPort: true,
    // Docker Desktop ne transmet pas toujours les événements fs du bind mount.
    watch: { usePolling: process.env.VITE_USE_POLLING === 'true', interval: 500 },
    hmr: { clientPort: Number(process.env.VITE_HMR_CLIENT_PORT) || devPort },
    proxy: {
      '/api': {
        target: process.env.VITE_ECC_BACKEND || 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  plugins: [
    react(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@components': path.resolve(__dirname, './src/components'),
      '@pages': path.resolve(__dirname, './src/pages'),
      '@api': path.resolve(__dirname, './src/api'),
      '@context': path.resolve(__dirname, './src/context'),
      '@assets': path.resolve(__dirname, './src/assets'),
      '@services': path.resolve(__dirname, './src/services'),
      '@hooks': path.resolve(__dirname, './src/hooks'),
      '@utils': path.resolve(__dirname, './src/utils'),
    },
  },
  build: {
    target: 'es2022',
    cssCodeSplit: true,
    cssMinify: true,
    rollupOptions: {
      output: {
        // Vite 8 (Rolldown) requiert manualChunks sous forme de fonction
        manualChunks(id) {
          if (id.includes('node_modules/react') || id.includes('node_modules/react-dom')) {
            return 'vendor-react';
          }
          if (id.includes('node_modules/react-router') || id.includes('node_modules/react-router-dom')) {
            return 'vendor-router';
          }
          if (id.includes('node_modules/axios')) {
            return 'vendor-axios';
          }
          if (id.includes('node_modules/lucide-react')) {
            return 'vendor-lucide';
          }
        },
      },
    },
  },
})
