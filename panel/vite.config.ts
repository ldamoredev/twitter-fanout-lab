/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Las rutas son relativas a este archivo: Vite las resuelve contra `root`.
export default defineConfig({
  plugins: [react()],
  publicDir: false,
  build: {
    outDir: '../resources/public',
    emptyOutDir: true,
    minify: false,
    rollupOptions: {
      input: {
        index: 'index.html',
        modelo: 'modelo.html',
        fanout: 'fanout.html',
        hibrido: 'hibrido.html',
      },
      output: {
        entryFileNames: 'assets/[name].js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/[name][extname]',
      },
    },
  },
  server: {
    proxy: {
      '/posts': 'http://127.0.0.1:18080',
      '/follows': 'http://127.0.0.1:18080',
      '/timelines': 'http://127.0.0.1:18080',
      '/metrics': 'http://127.0.0.1:18080',
      '/health': 'http://127.0.0.1:18080',
    },
  },
  test: {
    environment: 'node',
  },
});
