# 台本の直し（script-fixes）

Intent `261004-role-menu` の Performance Validation の Q3: A・Q4: A・Q5: A・Q6: A に沿って、負荷の試験の台本と手順書を直した記録です。作業ブランチは `fix/261004-role-menu-pv`（`develop` の先頭から作成）で、まだコミットしていません。アプリのソース（`backend/`・`frontend/`）は変えていません。

## 1. 直したこと

| ファイル | 直したこと | 出どころ |
|---|---|---|
| `perf/k6/scenarios.js` | `dslLight`・`dslCycle` を「1回の繰り返しに要求1つ」に直した。繰り返しの始めに `exec.vu.metrics.tags.op` に操作の名前を入れ、op ごとの `iteration_duration` の p95 < 1000 ms と `checks` の率 1 で判定する。`http_req_duration{name:…}` は並べて記録するだけ（`max>=0`）にした | Q3: A、`project.md` の学び（VM の時計のずれ） |
| 同上 | `dslLight`: 今の状態（op `status`）と履歴（op `history`）を、繰り返しごとに交互に送る。`setup` で条件（適用中の DSL・プレビュー・履歴 `DSL_HISTORY_MIN` 件。既定 20）を確かめ、そろっていなければ始める前に止まる | Q3: A |
| 同上 | `dslCycle`: 投入（op `submit`。記録だけ）→ 適用（op `apply`）→ 投入 → 破棄（op `discard`）→ 履歴（op `history`）を、繰り返しの番号で1つずつ送る。適用の `previewId` は直前の投入の応答から受ける。`VUS` が 1 でなければ `setup` で止まる | Q3: A |
| 同上 | 準備の場面 `dslPrepare`（1 VU・1 回）を足した。`DSL_FILE` の投入と適用を、履歴が `DSL_HISTORY_MIN` 件になるまで繰り返し、最後に同じ DSL を投入してプレビューに置く（`dslLight` の条件を作る） | Q3: A |
| 同上 | 管理者のトークンを `setup` で1回取る（`dslPrepare`・`dslLight`・`dslCycle`）。繰り返しの中でログインし直さない。使われなくなった `dslPreviewId()` を消した。`dslMixed` は変えていない（判定は `checks` だけ） | `project.md` の学び（トークンは setup で取る） |
| `perf/ui/dsl-ui-lang.mjs` | 画面のログインの前に、ブラウザーと Cookie を分けた API の要求の文脈（Playwright の `request.newContext`）で仮の管理者にログインし、`PUT /api/me/preferences` で表示の言語を `en` にする。終わったら元の値に戻す（`languageRestored`）。ログインの後に `<html lang>` が `en` になったことを待って確かめ（段 `englishUi`）、合格の条件に `htmlLang === 'en'` を足した | Q4: A、dsl-v2 の読み直し R-02 |
| `perf/README.md` | 冒頭の「手順」に、PC の眠りの守り（`caffeinate -dims` で台本の全体を包む、流す前に `pmset -g assertions`、流した後に `pmset -g log` で `Sleep`・`DarkWake` が無いことを確かめ、入っていたら流し直す）を足し、すべての節の `caffeinate -i` を `caffeinate -dims` にした | `project.md` の学び（B8 の Thermal Emergency） |
| 同上 | 手順 1 の一時の置き場を `mktemp -d` からホームの下（`mktemp -d "$HOME/.mastersmith-perf-XXXXXX"`、権限 700）にした | `project.md` の学び（VM から見えない一時ディレクトリ） |
| 同上 | 「k6 の DSL の場面」の節を書き直した（判定の形、`dslPrepare`、流す順 `dslCycle` → `dslPrepare` → `dslLight` → `dslMixed`、`DURATION=3m`、イメージのタグを `<タグ>` に）。`--lang` の行に言語の設定の手順を書き足した | Q3: A・Q4: A |
| 同上 | ロールの管理の節に「警報の確かめの回」を足した。上限 4・`rolePoolLimit`・`VUS=12`・5 分、lgtm を先に網につないでから使い捨てのアプリを起動し直す、`ua-limit.sh` を場面だけ替えた写し `role-limit.sh` で流す、BUSY（`ROLE_BUSY`・`GROUP_BUSY`）の L3 と L4 の結び付き、鳴らなければ上限 2・`VUS=20`・8 分で1回流し直す | Q6: A |

### 警報の確かめの回の下ごしらえで分かったこと

- **利用者の数は足りる**: `rolePoolLimit` は VU ごとにメンバーの候補 `perf-gr-<VU の番号>` を使う（`setupRoleMembers` の `roleMembers(token, VUS)`）。グループの管理の節の 2G で 1,000 人入れるため、`VUS=12`（流し直しの 20）でも足りる。台本は変えていない。
- **トークンの有効期限を足した**（指示に無い追加）: `rolePoolLimit` は `setup` で取ったトークンを使い続け、アクセストークンの有効期限は既定 5 分（`MASTERSMITH_AUTH_ACCESS_TOKEN_TTL`）のため、5 分の回では終わりの近くで 401（`rolePoolLimit_other`）が混ざる見込み。手順では、この回だけ使い捨てのアプリに `MASTERSMITH_AUTH_ACCESS_TOKEN_TTL=15m` を足す形にした（接続プールの使い方は変わらない。配備したアプリの設定は変えない）。
- `ua-limit.sh` から `role-limit.sh` を作る `sed` は、README の本文から取り出した写しで試し、`SCENARIO=rolePoolLimit` と `ROLE_DURATION=$DUR` がちょうど1か所ずつになることを確かめた。

## 2. 確かめの結果

### 2.1 `k6 inspect --include-system-env-vars`（要求は送らない）

| 場面 | 場面の形 | 閾値 |
|---|---|---|
| `dslPrepare` | per-vu-iterations・1 VU・1 回 | `checks{scenario:dslPrepare}` |
| `dslLight` | constant-vus | `iteration_duration{scenario:dslLight,op:status}`・`{…,op:history}`、`checks`、`http_req_duration{name:dslStatus}`・`{name:dslHistory}`（`max>=0`） |
| `dslCycle` | constant-vus | op `apply`・`discard`・`history` の `iteration_duration`（p95）、op `submit`（`max>=0`）、`checks`、`http_req_duration` の4つ（`max>=0`） |
| `dslMixed` | `dslHeavy`（1 VU）と `logins`（`VUS`）。変わらない | `checks{scenario:dslHeavy}`・`{scenario:logins}` |
| `rolePoolLimit`（`VUS=12`） | constant-vus・12 VU・3 分（`ROLE_DURATION` で上書きする） | 無し（件数で判定）。`setupTimeout` 10 分 |

### 2.2 使い捨ての環境での試し走り（2026-10-14 18:17〜18:22 UTC）

- 環境: `KEEP=1 MASTERSMITH_IMAGE_TAG=local OUT_DIR=build/perf-results/pv-trial ./perf/dsl-timing.sh --lang postgres`（イメージは配備した `mastersmith:local` = `a16cd68`）。全体を `caffeinate -dims` で包んだ。配備したアプリ・Mailpit・見本の対象DB は止めていない（VM の使えるメモリは始める前に 約 4.9 GB）。終わったら `down -v` と一時ディレクトリの削除で片付け、`mastersmith-perf` のコンテナ・ボリューム・網が残っていないことと、配備したアプリが healthy のままであることを確かめた。
- **試し走りの値は判定に使わない**（配備したアプリを止めておらず、長さも短いため）。

| 確かめ | 結果 |
|---|---|
| `dslCycle`（`VUS=1`・20 秒） | 73 回、`checks` 73/73。op ごとの件数は submit 30・apply 15・discard 14・history 14 で、op のタグが `iteration_duration` に付くことを確かめた。p95（ミリ秒）は apply 10.9・discard 6.7・history 4.3・submit 750.3 |
| `dslPrepare` | `checks` 10/10。「投入と適用 3 回、履歴 20 件、プレビューを置いた」 |
| `dslLight`（`VUS=2`・20 秒） | `setup` が条件を確かめた（「適用中あり、プレビューあり、履歴 20 件」）。`checks` 96,370/96,370、p95 は status 0.82 ms・history 0.79 ms |
| `dslCycle` を `VUS=2` で起動 | `setup` が「dslCycle は VUS=1 で流してください」で止まった（意図どおり） |
| `--lang` の1回目 | 失敗。API のログインを画面と同じ文脈で行ったためリフレッシュトークンの Cookie が画面に渡り、ログインの画面を通らずにホームが開いた。API の要求の文脈を分けて直した |
| `--lang` の2回目 | 言語の設定（`ja` → `en`）と戻し（`languageRestored: true`）、`<html lang>` が `en`、5つの段の応答の `message` に日本語の文字なし、Accept-Language は `en`、画面に応答の `message` がそのまま出ることは、どれも通った。**ただし `pass: false`**。書式の版 1 の誤り（`versionError`）の画面の文字に、全角のコロン「：」が1文字あった（下の 3節） |
| 参考: 既定の DSL の大きさ（U2-NFR2.7） | 生成の内訳の `bytes` は 7,261,390（試算の 約 6.3 MB より大きいが、上限 10 MiB の内）。判定は本番の回で行う |

### 2.3 行っていない確かめ

- `./gradlew verify` は**流していない**。3節の問題で、このブランチにアプリの直しが入るかどうかが決まらないため、決まってから1回だけ流す（`perf/` は `verify` のフォーマット・リンタ・ライセンスヘッダーの対象外で、当たるのは Gitleaks だけ）。
- `perf/` の台本は frontend の Prettier・oxlint・ESLint の対象外（`frontend/` の下だけが対象）。構文は `k6 inspect` と試し走り（k6・node）で確かめた。
- `rolePoolLimit` の警報の確かめの回は、手順を書いただけで流していない（本番の回で流す）。

## 3. 計画の外の問題: 英語の画面に全角のコロンが出る

- **事実**: DSL の投入で誤りが1件だけのとき、画面の誤りの表示は `<種類>：<message>` の形で、区切りが言語によらず全角のコロン「：」に固定されている（`frontend/src/features/dsl/DslErrorList.tsx` の 90 行目の `{'：'}`。Intent 260923-dsl-schema-loader の U5、`345a94e` から）。英語の表示でも `Format version：Format version 1 can no longer be used. …` と出る。
- **影響**: U2-NFR2.8（英語の文言が届く）の `--lang` の判定は、画面の文字に日本語の文字（全角の記号を含む）が無いことを条件にしている。誤りが1件の段（`versionError`）でこれに当たり、`pass: false` になる。応答の文言は英語で、ほかの段は通る。
- **選択肢**:
  - A. アプリを直す（区切りを文言の鍵に移し、ja は「：」・en は「: 」にする）。直しを再現する画面のテストを同じコミットに入れ、`verify`・E2E を通して統合し、イメージを作り直して配備し直す（Deployment Execution の手順に戻る）。この段の測りは、直した版のイメージで行う。所要は直しと確かめで 1〜1.5 時間と配備のやり直し。
  - B. この段では直さない（推奨）。台本の判定は変えず、U2-NFR2.8 を `Not Met` として記録し、直しは次の Intent に回す（全角のコロン1文字の表示だけの問題で、文言の中身はすべて英語）。この段の測りは配備した版（`a16cd68`）のまま進められる。
  - C. 台本の判定を、全角の記号（コロンなど）を除いて日本語の文字（ひらがな・カタカナ・漢字）だけを見る形に緩め、`Met` とする。目標の文言の読み方を変えることになり、`project.md` の「目標を緩めて満たしたことにしない」に当たりうる。
- **推奨の理由（B）**: 配備した版を変えずに、この段の測り（k6・DSL の時間）をそのまま進められます。表示だけの小さな問題で、業務の動作と応答の文言には影響しません。判定の条件も緩めません。直しはテストつきで次の Intent に入れられます。

## 4. コミットの区切りの案（未実施。承認を得てから行う）

| 案 | 中身 | 件名の案 |
|---|---|---|
| C1 | `perf/k6/scenarios.js`・`perf/ui/dsl-ui-lang.mjs`・`perf/README.md`（台本と手順書の直し） | 負荷の試験の台本を直す（DSL の k6 の場面を1回に要求1つにし op ごとに判定、--lang で利用者の言語を英語に、眠りの守りと警報の確かめの回） |
| C2 | `aidlc/` の下（この段の質問・`script-fixes.md`・監査ログ） | Performance Validation の質問と台本の直しの記録 |

- `develop` への統合は、C1 の後に `./gradlew verify` を通してから squash で行う（3節で A を選ぶ場合は、アプリの直しのコミットを別に足してから `verify`・E2E を流す）。

## Sources

- `aidlc/spaces/default/intents/261004-role-menu/operation/performance-validation/performance-validation-questions.md`（Q3〜Q6 の答え）
- `perf/k6/scenarios.js`・`perf/ui/dsl-ui-lang.mjs`・`perf/README.md`・`perf/dsl-timing.sh`（直した後の版）
- `backend/src/main/java/cherry/mastersmith/user/web/MeController.java`・`PreferencesRequest.java`・`user/domain/Language.java`、`dslmanage/web/DslAdminController.java`、`backend/src/main/resources/application.yaml`（`access-token-ttl`）、`frontend/src/app/display-settings/DisplaySettingsProvider.tsx`（`<html lang>`）、`frontend/src/features/dsl/DslErrorList.tsx`（読み取り）
- 試し走りの結果: `build/perf-results/pv-trial/`（`summary.md`・`postgres/ui-lang.json`・`postgres/ui-lang-2.json`・`trial-*.json`・`trial-*.log`。git の対象外）
- `aidlc/spaces/default/memory/project.md`（Testing Posture の学び）・`team.md`（Way of Working）
