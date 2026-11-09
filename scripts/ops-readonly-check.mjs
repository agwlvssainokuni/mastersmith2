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
// 振り返りの段（Feedback & Optimization）などで毎回手で行う、手元の環境の読み取りだけの確かめを1つにまとめた台本
// （Intent 261108-ci-checks-release の A1、FR5.1）。何も変えない（コンテナの起動・停止・作り直しをしない）。
//
// 流し方（リポジトリのルートで。colima の PC では DOCKER_HOST をシェルに渡しておく。書き方は docs/target-db.md）:
//   node scripts/ops-readonly-check.mjs [--compose <file>] [--env <file>] [--env-example <file>] [--war <file>]
//   既定は compose.yaml・.env・.env.example・backend/build/libs/mastersmith.war（どれもリポジトリのルートから）。
//
// 出すもの（Markdown）:
//   1. 動いているコンテナごとのイメージの版とダイジェスト（docker inspect の RepoDigests）と、compose.yaml の image: の比べ。
//      食い違うものは「食い違い」と示す。版が変数（${…}）で決まるサービスは、名前だけを比べる（.env の値は読まないため）。
//   2. .env と .env.example の環境変数の名前の数と、片方にだけある名前（名前だけ。値は読まない）。
//   3. WAR の SHA-256。
//   4. .env の更新の時刻。
//   5. docker system df の要約。
//
// 終了コード: 0（食い違いなし）、1（1 の比べで食い違いがある）、2（Docker に繋がらない・compose.yaml が無いなどで、
// 1 の比べを確かめていない）、3（引数の誤り）。2 のときは出力の最初に「1 の比べは確かめていない」と書き、ほかの項目は出す。
//
// 決まり:
// - .env は行ごとに最初の `=` の左側（名前）だけを切り出して使い、右側（値）は切り出さず、使わず、出さない。
//   コメントの行（# で始まる）は名前の形に当たらないため数えない。
// - docker inspect は --format で決まった項目（名前・イメージ・サービスのラベル・状態）だけを取り出す（環境変数の値を含む
//   Config.Env を読まない）。DOCKER_HOST は値（ホームの下のソケットのパス）を出さず、設定の有無だけを出す。
// - 外の道具の標準エラーは出さない（ソケットのパスなどホームの下のパスを含みうるため）。
import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { createReadStream, existsSync, readFileSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

/** 引数の誤り（終了コード 3）。 */
class ArgError extends Error {}

function parseArgs(argv) {
  const options = {
    compose: 'compose.yaml',
    env: '.env',
    envExample: '.env.example',
    war: 'backend/build/libs/mastersmith.war',
  }
  const keys = {
    '--compose': 'compose',
    '--env': 'env',
    '--env-example': 'envExample',
    '--war': 'war',
  }
  for (let i = 0; i < argv.length; i++) {
    const key = keys[argv[i]]
    if (key === undefined) throw new ArgError(`分からない引数です: ${argv[i]}`)
    const value = argv[i + 1]
    if (value === undefined || value.startsWith('--'))
      throw new ArgError(`${argv[i]} の値がありません`)
    options[key] = value
    i++
  }
  return options
}

/** リポジトリのルートからの相対のパスで表す（ホームの下の絶対のパスを出さないため）。 */
function shown(file) {
  const relative = path.relative(repoRoot, path.resolve(repoRoot, file))
  return relative.startsWith('..') ? path.basename(file) : relative
}

/** docker を起動し、成功したら標準出力を、失敗したら null を返す（標準エラーは捨てる）。 */
function docker(args) {
  const result = spawnSync('docker', args, {
    cwd: repoRoot,
    encoding: 'utf8',
    maxBuffer: 16 * 1024 * 1024,
  })
  if (result.error || result.status !== 0) return null
  return result.stdout
}

/**
 * compose.yaml の services の下の、サービスの名前と image: の値を読む（YAML の部品を使わない簡易の読み方。
 * このリポジトリの compose.yaml の書式（2 字下げのサービス名、4 字下げの image:）を前提にする）。
 */
function readComposeImages(file) {
  const images = new Map()
  let inServices = false
  let service = null
  for (const line of readFileSync(file, 'utf8').split('\n')) {
    if (/^\S/.test(line)) {
      inServices = /^services:\s*$/.test(line)
      service = null
      continue
    }
    if (!inServices) continue
    const serviceMatch = /^ {2}([A-Za-z0-9_.-]+):\s*$/.exec(line)
    if (serviceMatch) {
      service = serviceMatch[1]
      continue
    }
    const imageMatch = /^ {4}image:\s*['"]?([^'"\s]+)['"]?\s*$/.exec(line)
    if (imageMatch && service) images.set(service, imageMatch[1])
  }
  return images
}

/** image: の値を、名前・版・ダイジェストに分ける。 */
function splitImage(reference) {
  const at = reference.indexOf('@')
  const digest = at >= 0 ? reference.slice(at + 1) : null
  const nameTag = at >= 0 ? reference.slice(0, at) : reference
  const colon = nameTag.lastIndexOf(':')
  const slash = nameTag.lastIndexOf('/')
  const hasTag = colon > slash
  return {
    name: hasTag ? nameTag.slice(0, colon) : nameTag,
    tag: hasTag ? nameTag.slice(colon + 1) : 'latest',
    digest,
  }
}

/** 1. コンテナの版とダイジェストを compose.yaml と比べる。食い違いの数を返す（比べられなければ null。終了コード 2）。 */
function checkContainers(out, composeFile) {
  out.push('## 1. コンテナのイメージと compose.yaml の比べ')
  out.push('')
  out.push(`- DOCKER_HOST: ${process.env.DOCKER_HOST ? '設定あり（値は出さない）' : '設定なし'}`)
  const ps = docker(['ps', '--quiet', '--no-trunc'])
  if (ps === null) {
    out.push(
      '- 確かめていない（Docker の実行環境に繋がらない。colima が止まっているか、DOCKER_HOST が無い）',
    )
    out.push('')
    return null
  }
  const ids = ps.split('\n').filter((id) => id.trim() !== '')
  if (!existsSync(path.resolve(repoRoot, composeFile))) {
    out.push(`- ${shown(composeFile)} が無いため比べられない`)
    out.push('')
    return null
  }
  const expected = readComposeImages(path.resolve(repoRoot, composeFile))
  if (ids.length === 0) {
    out.push('- コンテナが無い（動いているコンテナが 0 件）')
    out.push('')
    return 0
  }
  const format =
    '{{.Name}}\t{{.Config.Image}}\t{{.Image}}\t{{index .Config.Labels "com.docker.compose.service"}}\t{{.State.Status}}'
  const inspected = docker(['inspect', '--format', format, ...ids])
  if (inspected === null) {
    out.push('- docker inspect が読めない')
    out.push('')
    return null
  }
  let mismatches = 0
  const running = new Set()
  out.push('| コンテナ | サービス | 動いている版 | ダイジェスト | compose.yaml | 判定 |')
  out.push('|---|---|---|---|---|---|')
  for (const line of inspected.split('\n').filter((l) => l.trim() !== '')) {
    const [rawName, configImage, imageId, service, status] = line.split('\t')
    const name = rawName.replace(/^\//, '')
    const repoDigestsJson = docker([
      'image',
      'inspect',
      '--format',
      '{{json .RepoDigests}}',
      imageId,
    ])
    let repoDigests = []
    try {
      repoDigests = repoDigestsJson ? (JSON.parse(repoDigestsJson) ?? []) : []
    } catch {
      repoDigests = []
    }
    const actual = splitImage(configImage)
    const digests = repoDigests.map((d) => d.slice(d.indexOf('@') + 1))
    const digestShown = digests.length
      ? digests.map((d) => d.slice(0, 19)).join('・')
      : '—（手元で作ったイメージ）'
    const want = service ? expected.get(service) : undefined
    let verdict
    if (!service || want === undefined) {
      verdict = 'compose.yaml に無い'
    } else if (want.includes('${')) {
      const w = splitImage(want.replace(/\$\{[^}]*\}/g, 'x'))
      verdict = w.name === actual.name ? '名前は一致（版は変数で決まる）' : '食い違い（名前）'
    } else {
      const w = splitImage(want)
      const tagOk = w.name === actual.name && w.tag === actual.tag
      const digestOk = w.digest === null || digests.includes(w.digest)
      verdict = tagOk && digestOk ? '一致' : `食い違い（${!tagOk ? '版' : 'ダイジェスト'}）`
    }
    if (verdict.startsWith('食い違い')) mismatches++
    if (service) running.add(service)
    out.push(
      `| ${name}（${status}） | ${service || '—'} | ${actual.name}:${actual.tag} | ${digestShown} | ${want ?? '—'} | ${verdict} |`,
    )
  }
  const stopped = [...expected.keys()].filter((s) => !running.has(s))
  out.push('')
  out.push(
    `- 動いていない compose.yaml のサービス: ${stopped.length ? stopped.join('・') : 'なし'}`,
  )
  out.push(`- 食い違い: ${mismatches} 件`)
  out.push('')
  return mismatches
}

/** 環境変数のファイルから、名前だけを取り出す（値は持たない）。ファイルが無ければ null。 */
function readEnvNames(file) {
  const full = path.resolve(repoRoot, file)
  if (!existsSync(full)) return null
  const names = new Set()
  for (const line of readFileSync(full, 'utf8').split('\n')) {
    const eq = line.indexOf('=')
    if (eq < 0) continue
    // 最初の `=` の左側だけを切り出して見る。右側（値）は切り出さない。
    const match = /^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*$/.exec(line.slice(0, eq))
    if (match) names.add(match[1])
  }
  return names
}

/** 2. 環境変数の名前の数え。 */
function checkEnvNames(out, envFile, exampleFile) {
  out.push('## 2. 環境変数の名前（値は読まない）')
  out.push('')
  const env = readEnvNames(envFile)
  const example = readEnvNames(exampleFile)
  out.push(`- ${shown(envFile)}: ${env === null ? '無い' : `${env.size} 個`}`)
  out.push(`- ${shown(exampleFile)}: ${example === null ? '無い' : `${example.size} 個`}`)
  if (env !== null && example !== null) {
    const onlyEnv = [...env].filter((n) => !example.has(n)).sort()
    const onlyExample = [...example].filter((n) => !env.has(n)).sort()
    out.push(`- ${shown(envFile)} にだけある名前: ${onlyEnv.length ? onlyEnv.join('・') : 'なし'}`)
    out.push(
      `- ${shown(exampleFile)} にだけある名前: ${onlyExample.length ? onlyExample.join('・') : 'なし'}`,
    )
  }
  out.push('')
}

/** 3. WAR の SHA-256。 */
async function checkWar(out, warFile) {
  out.push('## 3. WAR の SHA-256')
  out.push('')
  const full = path.resolve(repoRoot, warFile)
  if (!existsSync(full)) {
    out.push(`- ${shown(warFile)}: 無い`)
    out.push('')
    return
  }
  const hash = createHash('sha256')
  await new Promise((resolve, reject) => {
    createReadStream(full)
      .on('data', (chunk) => hash.update(chunk))
      .on('end', resolve)
      .on('error', reject)
  })
  const stat = statSync(full)
  out.push(`- ${shown(warFile)}: \`${hash.digest('hex')}\``)
  out.push(`- 大きさ: ${stat.size} バイト、更新の時刻: ${stat.mtime.toISOString()}`)
  out.push('')
}

/** 4. .env の更新の時刻。 */
function checkEnvTime(out, envFile) {
  out.push('## 4. .env の更新の時刻')
  out.push('')
  const full = path.resolve(repoRoot, envFile)
  out.push(
    existsSync(full)
      ? `- ${shown(envFile)}: ${statSync(full).mtime.toISOString()}`
      : `- ${shown(envFile)}: 無い`,
  )
  out.push('')
}

/** 5. docker system df の要約。 */
function checkDiskUsage(out) {
  out.push('## 5. docker system df')
  out.push('')
  const df = docker(['system', 'df'])
  if (df === null) {
    out.push('- 確かめていない（Docker の実行環境に繋がらない）')
  } else {
    out.push('```text')
    out.push(df.trimEnd())
    out.push('```')
  }
  out.push('')
}

async function main() {
  const options = parseArgs(process.argv.slice(2))
  const out = [
    '# 手元の環境の読み取りの確かめ',
    '',
    `- 調べた時刻: ${new Date().toISOString()}`,
    '',
  ]
  const mismatches = checkContainers(out, options.compose)
  checkEnvNames(out, options.env, options.envExample)
  await checkWar(out, options.war)
  checkEnvTime(out, options.env)
  checkDiskUsage(out)
  if (mismatches === null) {
    // 1 の比べを確かめていないことを、通ったように見えないよう出力の最初に書く（Code Generation のレビュー R-02）。
    out.splice(
      2,
      0,
      '> **1 の比べは確かめていない**（終了コード 2）。Docker の実行環境と compose.yaml を確かめて流し直す。',
      '',
    )
  }
  process.stdout.write(`${out.join('\n')}\n`)
  if (mismatches === null) return 2
  return mismatches > 0 ? 1 : 0
}

try {
  process.exitCode = await main()
} catch (error) {
  if (error instanceof ArgError) {
    process.stderr.write(`ops-readonly-check: ${error.message}\n`)
    process.exitCode = 3
  } else {
    throw error
  }
}
