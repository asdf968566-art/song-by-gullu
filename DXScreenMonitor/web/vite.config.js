import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Build straight into the signaling server's public folder, so the server serves the dashboard.
export default defineConfig({
  plugins: [react()],
  base: './',
  build: { outDir: '../server/public', emptyOutDir: true },
});
