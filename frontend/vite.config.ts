/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//
// 画面のビルドと開発サーバーの設定。
// - resolve.dedupe: make-you-chic-ui を file: 参照で取り込むため、React の二重読み込みを防ぐ（組み込みガイド）。
// - 開発サーバーは /api と /actuator をバックエンドへ転送する。元の Host を保ち（changeOrigin: false）、
//   エラー応答の type の URL が開発サーバーの Host から組み立てられるようにする。CORS の設定は置かない。
// - ビルドの出力は dist。名前にハッシュが付くファイルは assets/ の下。初回の読み込みの量を測るため manifest を出す。
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const backend = 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  resolve: {
    dedupe: ['react', 'react-dom'],
  },
  server: {
    proxy: {
      '/api': { target: backend, changeOrigin: false },
      '/actuator': { target: backend, changeOrigin: false },
    },
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    manifest: true,
    // index.html に埋め込みのスクリプトを置かない（CSP の script-src 'self' に合わせる）。
    modulePreload: { polyfill: false },
    assetsInlineLimit: 0,
  },
})
