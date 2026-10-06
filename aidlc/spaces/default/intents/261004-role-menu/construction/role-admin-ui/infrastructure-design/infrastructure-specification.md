# 基盤の仕様 — U6 role-admin-ui

U6 role-admin-ui は ui の単位で、B8 で作ります。画面は次のとおりです。

- ロールの管理（S3・S4）
- グループの管理（S5・S6）
- 権限の受け渡し（S7）
- 利用者の管理の画面のロールの表示（`UserRolesDialog`）

画面は、既存のとおりビルドの結果を実行可能 WAR に同梱し、同じオリジンで配ります。バックエンド・内部DB・設定・依存・秘密は変えません。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤・CDN・IaC は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 出典

- 答え: `infrastructure-design-questions.md`（質問なし、設計の要点 6 件、まとめの確認は Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261004-role-menu/` の下）:
  - `construction/role-admin-ui/nfr-design/performance-design.md`（1〜9節）・`security-design.md`（1〜9節）・`logical-components.md`（1〜6節）・`traceability.json`
  - ui の単位のため、`scalability-design.md`・`reliability-design.md`・`observability-design.md` は作られていない（NFR 設計の段の `produces_kinds`）。同じ理由で、要件の段の scalability・reliability・observability も無い。
  - `construction/role-admin-ui/nfr-requirements/tech-stack-decisions.md`
  - `construction/role-admin-ui/functional-design/functional-spec.md`・`frontend-components.md`
  - `inception/domain-design/components.md`（RoleAdminUi・GroupAdminUi・RoleTransferUi・UserAdminUi・SharedTreeView・ApiClient）
  - `inception/contract-design/contract-summary.md`（C2・C6・C7）
  - `inception/delivery-planning/bolt-plan.md`（B8）
  - group・role の基盤の設計の成果物（読み取りだけ）
- 既存のもの（正とする。読むだけ）:
  - `frontend/package.json`・`frontend/package-lock.json`（react-router 8.4.0）
  - `frontend/vite.config.ts`・`frontend/src/main.tsx`・`frontend/scripts/check-bundle-size.mjs`
  - `backend/build.gradle.kts`（`bootWar` が `frontend/dist` を `WEB-INF/classes/static` に同梱）
  - `backend/src/main/java/cherry/mastersmith/config/WebConfig.java`（深い道で `index.html` を返す配信）

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| Compute model | 画面は SPA で、利用者のブラウザで動く。配るのは既存のアプリのコンテナ（実行可能 WAR）で、変えない | `team.md` の Code Style（WAR に `dist` を同梱） |
| 画面の成果物 | `frontend/dist`（Vite のビルド）を `bootWar` が `WEB-INF/classes/static` に同梱する。U6 の3つの画面と `UserRolesDialog` は遅延読み込み（`lazy`）の別の塊になる | `performance-design.md` 5節、`logical-components.md` 1節 L1 |
| 入口 | `frontend/src/main.tsx` を `BrowserRouter` から `createBrowserRouter`＋`RouterProvider`（data router）に替える。今の `App` を1つの道（`path: '*'`）で包み、表示の設定の受け渡しと `StrictMode` を保つ | `logical-components.md` 3節 L11（`useBlocker` のため） |
| Networking | 同じオリジン（8080）。CORS の設定は置かない。書き出しの応答のヘッダー `X-Role-Transfer-Exceeds-Import-Limit` は同じオリジンのため、`Access-Control-Expose-Headers` は要らない | `team.md` の Code Style、`logical-components.md` 3節 L10 |
| 深い道 | `/admin/roles/…` などを開き直したときに `index.html` を返す既存の配信（`WebConfig`）を変えない | data router は今の道の形をそのまま受ける |
| Storage | 使わない。画面はブラウザの保存（localStorage など）に応答の値・YAML の本文・指紋を書かない | `security-design.md` 3節・4節 |
| Environments | 配備は `compose.yaml` の同じイメージ。E2E は WAR を一時の内部DB で起動して流す（`playwright.config.ts`） | 既存のまま |
| IaC approach | 作らない | 配備先が決まっていない |
| Resource sizing | サーバー側は変わらない。確かめの大きな応答（25 ロール × 1万の変わる点）と確かめた本文（最大 10 MiB）は利用者のブラウザのメモリに持つ（受け入れた制約） | `performance-design.md` 3節・7節 |
| Configuration | `vite.config.ts`・`vitest.config.ts`・`playwright.config.ts`・`application.yaml` は変えない | `tech-stack-decisions.md` NFR6.5 |
| Dependencies | 新しい npm の依存は足さない（`package.json`・`package-lock.json` を変えない） | NFR6.5 |
| Rollback | 直前の版のイメージで起動し直す（イメージだけ）。前の版には U6 の画面とサイドバーの項目が無い。戻したときの DSL とロールの扱いは dsl-v2 と U3・U4 の配備の段への引き継ぎ（版 2 の DSL は読めず DSL が無い状態、管理の可否は `users.admin_flag` のまま） | 4節 |

## 2. Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 画面の静的配信（Spring Boot の静的資源） | web サーバー（既存） | 変えない。遅延読み込みの塊も同じ道で配る | キャッシュの扱いは既存の `CacheControlFilter` のまま |
| ApiClient（`shared/api-client/apiClient.ts`） | 画面の HTTP の部品（既存） | `ApiDownload` に `headers: Headers` を足す（互換の変更。既存の `blob`・`contentDisposition` と DSL のダウンロードは変えない） | `logical-components.md` 3節 L10 |
| U3・U4 の管理の API | 呼ぶ先 | 変えない（要求の形を変えない） | API の時間・5xx は group・role の監視で見る（`monitoring-design.md` 3節） |
| cache・queue・search・CDN・DNS・load-balancer | — | 使わない | — |

## 3. Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| 画面の入口（`main.tsx` の data router） | U6（B8 で差し替える） | 画面のすべての機能（既存の画面と U7） | 今の `App` を包むだけで、道の形と表示の設定の受け渡しを変えない。B8 の最初に、既存の画面のテストと E2E（010〜130）が変わらないこと、`useBlocker` が止まることを確かめ、成り立たなければ戻す |
| `ApiDownload` の `headers` | U6（B8） | U6 の書き出し。DSL のダウンロードは読まない | 互換の変更。`apiClient.download.test.ts` に項目を足し、既存の確かめは変えない |
| `shared/validation/validateAdminName.ts` | U6（新しい） | roleadmin・groupadmin | 機能どうしの import の制限（U1）の中で、`shared/` に置く |
| E2E の差し替えの口 `frontend/e2e/support/adminApiRoute.ts` | 既存（U6 が `body: string` の形を足す） | 既存の 120・130、U6 の 140、U7 | 今の `json` の形は残し、既存の 120・130 は変えない（承認の場の直し R-01） |
| E2E の番号と DSL の見本 | U6（140・150 の案）と U7（160・170 の案） | 全 E2E（1つの WAR と内部DB を番号の順に共有） | F の流れの DSL の名前は `E2EF_` の接頭辞で、U7 の I の流れの見本と重ねない。番号はコード生成の計画で確定する |
| 手元の監視のダッシュボードの区画（この Intent の API） | Observability Setup（group の Q1 A） | U3・U4・U5 の API（U6 が呼ぶ先） | `monitoring-design.md` 3節 |

## 4. 配備の段への引き継ぎ

- U6 自身の移行・設定の変更は無く、戻しはイメージだけです。
- 配備の後のスモークテストで U6 の画面を触るなら、ロール・グループの操作は監査に残ります。触る前に依頼者に伝え、監査の確かめの期待の件数を合わせてから数えます（`project.md` の学び）。
- 開いた Dropdown・Modal が画面の中に収まることを、配備の後にも見ます。前の Intent で E2E が拾えなかったはみ出しがあったためです（`project.md` の学び）。

## 5. 上流との差

この文書の範囲（配信・入口・共有の部品）では、承認済みの NFR 設計と違う作りはありません。
