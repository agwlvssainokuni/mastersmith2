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
// アイコンのバイナリ（PNG・ICO）の形の確かめと、ICO を詰める純粋な関数（U6 の SD3）。
// - ICO は PNG をそのまま並べた入れ物（予約 0・種類 1・画像の数・16 バイトのディレクトリ・PNG の並び）。
// - 形の確かめは「先頭 4 バイト」「画像の数」「各エントリが収まる」「各画像が PNG の署名を持つ」「大きさの上限」。
//   HTML の本文（素材が欠けて index.html が返ったもの）は、先頭 4 バイトで落ちる。
// - 例外でなく結果（`{ ok: false, code }`）で伝える。ただし packIco は作る側の道具のため、入力が PNG でなければ例外を投げる。

/** PNG の先頭 8 バイトの署名 */
export const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
/** `.png` の大きさの上限（NFR2.1.1） */
export const MAX_PNG_BYTES = 16 * 1024
/** `.ico` の大きさの上限（NFR2.1.1） */
export const MAX_ICO_BYTES = 32 * 1024
/** ICO の画像の数の範囲 */
export const MIN_ICO_IMAGES = 1
export const MAX_ICO_IMAGES = 8

const ICO_HEADER_BYTES = 6
const ICO_ENTRY_BYTES = 16

/**
 * PNG の幅と高さを読む（先頭 8 バイトの署名と IHDR）。
 * @param {Buffer} buffer PNG のバイト列
 * @returns {{ ok: true, width: number, height: number } | { ok: false, code: string }} 結果
 */
export function readPngSize(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length < 24) {
    return { ok: false, code: 'png-too-short' }
  }
  if (!buffer.subarray(0, 8).equals(PNG_SIGNATURE)) {
    return { ok: false, code: 'png-signature' }
  }
  if (buffer.toString('latin1', 12, 16) !== 'IHDR') {
    return { ok: false, code: 'png-ihdr' }
  }
  const width = buffer.readUInt32BE(16)
  const height = buffer.readUInt32BE(20)
  if (width === 0 || height === 0) {
    return { ok: false, code: 'png-size' }
  }
  return { ok: true, width, height }
}

/**
 * PNG の形と大きさを確かめる。
 * @param {Buffer} buffer PNG のバイト列
 * @param {{ limit?: number }} [options] 大きさの上限（既定は 16 KiB）
 * @returns {{ ok: true, width: number, height: number } | { ok: false, code: string }} 結果
 */
export function inspectPng(buffer, options = {}) {
  const limit = options.limit ?? MAX_PNG_BYTES
  const size = readPngSize(buffer)
  if (!size.ok) {
    return size
  }
  if (buffer.length > limit) {
    return { ok: false, code: 'png-too-large' }
  }
  return size
}

/**
 * PNG の並びを ICO の入れ物に詰める。
 * @param {Buffer[]} pngs PNG のバイト列の並び（1〜8 枚）
 * @returns {Buffer} ICO のバイト列
 */
export function packIco(pngs) {
  if (!Array.isArray(pngs) || pngs.length < MIN_ICO_IMAGES || pngs.length > MAX_ICO_IMAGES) {
    throw new RangeError(`PNG の数は ${MIN_ICO_IMAGES}〜${MAX_ICO_IMAGES} 枚にしてください`)
  }
  const sizes = pngs.map((png, i) => {
    const size = readPngSize(png)
    if (!size.ok) {
      throw new TypeError(`${i + 1} 枚目が PNG ではありません（${size.code}）`)
    }
    return size
  })
  const header = Buffer.alloc(ICO_HEADER_BYTES + ICO_ENTRY_BYTES * pngs.length)
  header.writeUInt16LE(0, 0)
  header.writeUInt16LE(1, 2)
  header.writeUInt16LE(pngs.length, 4)
  let offset = header.length
  pngs.forEach((png, i) => {
    const at = ICO_HEADER_BYTES + ICO_ENTRY_BYTES * i
    // 幅・高さは 1 バイト（256 以上は 0）
    header.writeUInt8(sizes[i].width >= 256 ? 0 : sizes[i].width, at)
    header.writeUInt8(sizes[i].height >= 256 ? 0 : sizes[i].height, at + 1)
    header.writeUInt8(0, at + 2)
    header.writeUInt8(0, at + 3)
    header.writeUInt16LE(1, at + 4)
    header.writeUInt16LE(32, at + 6)
    header.writeUInt32LE(png.length, at + 8)
    header.writeUInt32LE(offset, at + 12)
    offset += png.length
  })
  return Buffer.concat([header, ...pngs])
}

/**
 * ICO の形と大きさを確かめる。
 * @param {Buffer} buffer ICO のバイト列
 * @param {{ limit?: number }} [options] 大きさの上限（既定は 32 KiB）
 * @returns {{ ok: true, images: { width: number, height: number, offset: number, size: number }[] } | { ok: false, code: string }} 結果
 */
export function inspectIco(buffer, options = {}) {
  const limit = options.limit ?? MAX_ICO_BYTES
  if (!Buffer.isBuffer(buffer) || buffer.length < ICO_HEADER_BYTES) {
    return { ok: false, code: 'ico-too-short' }
  }
  if (buffer.length > limit) {
    return { ok: false, code: 'ico-too-large' }
  }
  if (buffer.readUInt16LE(0) !== 0 || buffer.readUInt16LE(2) !== 1) {
    return { ok: false, code: 'ico-header' }
  }
  const count = buffer.readUInt16LE(4)
  if (count < MIN_ICO_IMAGES || count > MAX_ICO_IMAGES) {
    return { ok: false, code: 'ico-count' }
  }
  const directoryEnd = ICO_HEADER_BYTES + ICO_ENTRY_BYTES * count
  if (buffer.length < directoryEnd) {
    return { ok: false, code: 'ico-directory' }
  }
  const images = []
  for (let i = 0; i < count; i++) {
    const at = ICO_HEADER_BYTES + ICO_ENTRY_BYTES * i
    const size = buffer.readUInt32LE(at + 8)
    const offset = buffer.readUInt32LE(at + 12)
    if (offset < directoryEnd || size === 0 || offset + size > buffer.length) {
      return { ok: false, code: 'ico-entry-range' }
    }
    const png = readPngSize(buffer.subarray(offset, offset + size))
    if (!png.ok) {
      return { ok: false, code: 'ico-entry-png' }
    }
    images.push({ width: png.width, height: png.height, offset, size })
  }
  return { ok: true, images }
}
