import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // In development the frontend runs on :5173 and the backend on :8080.
    // The proxy forwards /api calls to the backend, so the browser sees one
    // origin and we need no CORS setup.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
  },
})
