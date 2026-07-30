import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // The backend whitelists CORS for http://localhost:3000 only
    // (UrlShortenerApplication#corsConfigurer). Vite's default 5173 would be
    // rejected by the browser, so pin the dev server to the allowed origin.
    port: 3000,
    strictPort: true,
  },
})
