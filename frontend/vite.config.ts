import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5180,
    // Fail instead of drifting to another port: the API redirects back to this one.
    strictPort: true,
    // The browser only ever talks to this origin; the dev server forwards API
    // calls to the Java service, so no CORS configuration is needed.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: { modules: { classNameStrategy: 'non-scoped' } },
  },
})
