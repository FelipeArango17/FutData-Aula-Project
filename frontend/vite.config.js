import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Navegadores mínimos: iOS 15 (Safari 15) y Chrome 100 en Android 9 (Chrome sigue actualizándose por Play Store)
  build: { target: ['es2020', 'chrome100', 'safari15'] },
})
