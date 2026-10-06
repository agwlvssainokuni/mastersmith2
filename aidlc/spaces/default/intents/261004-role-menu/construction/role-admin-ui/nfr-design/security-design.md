# セキュリティの設計 — U6 role-admin-ui

## 出典

- この単位の承認済みの NFR 要件 `construction/role-admin-ui/nfr-requirements/`（`security-requirements.md` の NFR1.1・NFR1.4〜NFR1.12、`tech-stack-decisions.md` の NFR4.1〜NFR4.7・NFR6.1〜NFR6.10、`performance-requirements.md`）。ui の単位は scalability・reliability・observability の要件を作らないため、段の定義が必須とするそれらの入力は無い。
- 承認済みの機能設計 `functional-spec.md`（D1〜D32・7節・8節）と `frontend-components.md`、契約 `contract-summary.md`（C6・C7）。
- 統合の点: `construction/role/nfr-design/`（`RoleTransferSlot` の `ROLE_BUSY`）。
- 既存のコード: `frontend/playwright.config.ts`・`frontend/playwright-secret-check-reporter.ts`・`frontend/e2e/support/secretValues.ts`・`frontend/e2e/support/adminApiRoute.ts`・make-you-chic-ui（e82b651）。
- この段の答え: Q1 A、まとめの確認は Looks correct。部品の ID（L1〜L13）は `logical-components.md` の 1節。

## 1. 画面の判定とサーバーの判定（NFR1.1）

- 道とサイドバーの項目は ADMIN で登録し（L1）、管理者でない利用者が道を直接入れたときは骨組みの `decideRoute` が `ADMIN_FORBIDDEN` を返す。
- 管理の API の 403 `ACCESS_DENIED` は、各フックが `useAdminForbidden(error, 道)` に渡す。真が返れば画面は何も出さない（骨組みが権限なしの表示に置き換える）。
- 確かめ（読み直しの R-02 の手当てを含む表のテスト）:

| テスト（形） | 中身 |
|---|---|
| `RoleAdminForbidden.test.tsx`（機能ごとに1つ、パラメーターの表） | S3・S4・S5・S6・S7・S9 の画面ごとに、読み込みと各操作の API が 403 を返す表を流し、`useAdminForbidden` が要求の道つきで1回呼ばれ、画面が独自の文言を出さないこと |
| `registration.test.ts`（各機能） | U6 の6つの道で、管理者でないとき `decideRoute` が `ADMIN_FORBIDDEN`、未ログインはログインへ、管理者は画面になること |

## 2. 送る項目・読む項目（NFR1.4・NFR1.9）

- L2 は要求の本文を関数の引数の型から組み立て、決めた項目だけを送る（`{name}`・`{userId}` か `{groupId}`・`{scope, entries}`・`{userId}`、import は本文と指紋のヘッダー）。`entries` は L7 の純粋な関数だけが作る。
- 道の ID と名前は `encodeURIComponent`、権限の木は `URLSearchParams` で問い合わせの引数にする。
- 応答は L2 が型の項目だけを写した新しい値にする。必要な項目が無い・型が違う・列挙の知らない値は通信の失敗にする（`reason` は L3 の汎用の文言）。
- 名前の画面の検査は L9（案内で、判定の正はサーバー）。
- 確かめ: 各機能の `api/*Api.test.ts`（送る道・引数・本文の項目、余分な項目を足した応答を写した値、欠けた応答、`/`・`?`・`#`・`%` を含む名前の道）。`validateAdminName.test.ts`（64 と 65 コードポイント、空白だけ、全角の空白、制御文字、サロゲートペア）。

## 3. 権限の YAML のファイル（NFR1.5・NFR1.10・NFR6.6、Q1 A）

- **事前の大きさ**: L8 がファイルを選んだ時点で `File.size` を `MAX_TRANSFER_BYTES`（10,485,760。`transferLimits.ts` に1か所）と比べ、超えれば読まずに送らない。上限ちょうどは送る。文言の上限の値は定数から差し込む。
- **確かめた本文の保持**（Q1 A）: 確かめのときに `File.text()` で読んだ本文の文字列を、画面の状態（フックの中）にだけ持ち、適用でそれを送る。ブラウザの保存・URL・コンソールに出さない。ファイルの選び直し・適用の成功・画面を離れたときに捨てる。ファイルを書き換えたら「ファイルを選ぶ」から選び直す案内を、確かめの結果の上に出す。
- **指紋**: 確かめの応答の `fingerprint` を同じく画面の状態にだけ持ち、適用のヘッダー `X-Role-Transfer-Fingerprint` に入れる。
- **書き出しのファイル名**（`safeFileName.ts`）: `Content-Disposition` の名前が英数字・`.`・`_`・`-` だけで `.yaml` か `.yml` で終わるときだけ使い、それ以外は `roles.yaml`。保存の一時の URL は保存の後すぐに捨てる。
- **上限を超えた書き出し**: L10 の `headers` の `X-Role-Transfer-Exceeds-Import-Limit` が `true` のときだけ案内を出す（判定はサーバーの1か所）。
- 確かめ: `TransferFilePicker.test.tsx`（10,485,760 は送る・10,485,761 は送らない）、`transferLimits.test.ts`（定数が role の値と同じ）、`safeFileName.test.ts`（`roles.yaml`・`../x.yaml`・`a/b.yaml`・制御文字・空・`x.txt`・無い）、`useRoleTransfer.test.ts`（確かめた本文がそのまま適用で送られる、選び直しで本文と指紋が捨てられる、ヘッダーが `true`・無い・`false`・ほかの値の境界）。

## 4. 値を出さない（NFR1.6・NFR1.7・NFR1.8）

- 名前・表示名・氏名・メールアドレスは React の文字として描く。`dangerouslySetInnerHTML` を使わない（L12）。
- 文言は L3 が `code` と状態だけから選び、`detail`・`title` を読まない。S7 の `ROLE_BUSY` は「ほかの読み込みか操作と重なりました。少し待ってからもう一度お試しください」（`RoleTransferSlot` と行の排他を区別しない）。
- コンソール・ブラウザの保存・URL・履歴に応答の値・検索の文字・YAML の本文・指紋を出さない。今のページと検索の文字は画面の状態だけに持つ。
- 確かめ: 画面のテストで `<script>`・`<img onerror>`・`&`・`"`・`'` を含む名前が文字のまま出ること、`detail`・`title` の目印が画面に出ないこと、操作の後に `console` の呼び出しが無くブラウザの保存に書かれないこと（spy）。`reasonMessage.test.ts` に fast-check（任意の文字列で例外にならず、知らない値は汎用）。

## 5. E2E の報告と差し替え（NFR1.11・NFR6.10）

- **F の流れのファイル**（150 の案）:
  - 利用者は既存の `createRegisteredUser` で作り、作った直後に `recordSecretValues` でメールアドレス・パスワード・氏名・`runTag` を値のファイルに書く。既存の報告の部品（`playwright-secret-check-reporter.ts`）が、値と値の形（`u7-perf-…@example.com`・`e2e-u7-pw-…`）で json の報告・`test-results/` を探し、見つかれば実行を失敗にする（読み直しの R-06）。
  - 初期管理者の資格情報は今までどおりプロセスの環境変数で渡し、`webServer.env` に置かない。ロール・DSL の名前は秘密でないため値のファイルに入れない。
  - `test.step` の題・注記・添付に値を入れない。
- **U6 の検査のファイル**（140 の案）: 本物の利用者を作らず、見本（`example.com`）だけを使う。`/api/admin/` の下は `support/adminApiRoute.ts` の差し替えの口で受け、書き換えをサーバーへ届けない。見本の無い書き換えは打ち切って記録し、各テストの終わりに口が受けた件数 1 以上と打ち切り 0 件を確かめる。
- **見本の型**: 差し替えの見本は機能ごとに1つの作り方（関数）にまとめ、各機能の `api/types.ts` の型を付ける。F の流れの中で、本物の応答（ロールの一覧・権限の木・割り当て・確かめの応答）の項目の名前と型が見本と一致することを確かめる。

## 6. 静的検査と依存（NFR1.12・NFR6.5・NFR6.8）

- 既存の oxlint のセキュリティ系の決まり・ESLint（機能どうしの import の禁止、`export default`・`enum` の禁止）・型検査（`any` の禁止）を、決まりを緩めずに通す。
- 新しい npm の依存は足さない。依存の脆弱性の関門（OSV-Scanner）は今のまま。

## 7. 脅威と設計の対応

| 脅威 | 設計 |
|---|---|
| なりすまし（道の直接入力） | 1節（骨組みの判定とサーバーの 403） |
| 改ざん（本文・道の差し替え） | 2節（決めた項目だけ、判定はサーバー） |
| 情報の漏えい（コンソール・保存・URL・報告） | 4節・5節 |
| サービスの妨げ（大きなファイル・大きな応答） | 3節の事前の大きさ、`performance-design.md` の 3節の描画の範囲、サーバーの上限と `RoleTransferSlot`（U4） |
| クロスサイトスクリプティング | 4節・6節 |
| 保存の名前の悪用 | 3節 |
| 確かめた後の中身のすり替え | 3節（確かめた本文をそのまま送る）とサーバーの指紋の比べ（U4） |

## 8. 受け入れた制約

- 画面の確かめは案内で、画面を経ない要求はサーバーだけが守る。
- 確かめた本文の文字列（最大 10 MiB）を画面に持つため、確かめの後にファイルを書き換えても反映されない（選び直しの案内で補う）。
- 表示中の氏名・メールアドレスは、ブラウザの拡張機能や開発者の道具から読める。

## 9. 上流との差

- Q1 A: 適用で送る本文を、確かめのときに読んだ文字列にした（機能設計 W7.4 の「同じファイル」の読み）。
- S7 の `ROLE_BUSY` の文言を、`RoleTransferSlot` を含む形に広げた（機能設計 D4 の読み替え）。
- 1節の表のテストは、機能設計の再レビューの R-02 と NFR 要件の NFR1.1 の確かめ方を、テストの形に落としたもの。
