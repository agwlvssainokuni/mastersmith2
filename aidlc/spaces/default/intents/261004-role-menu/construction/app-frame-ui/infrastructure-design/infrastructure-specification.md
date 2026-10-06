# 基盤の仕様 — U7 app-frame-ui

U7 app-frame-ui は ui の単位で、B9 で作ります。中身は次のとおりです。

- 骨組みの変更: 作業ロールの切り替え、区画のある N 階層のサイドバー、ログアウトの画面
- 新しい画面: テーブルの置き場（S2）、自分の権限（S8）
- make-you-chic-ui の固定先の更新（e82b651 → 5bf1ffe）

画面は、既存のとおりビルドの結果を実行可能 WAR に同梱し、同じオリジンで配ります。バックエンド・内部DB・設定・依存・秘密は変えません。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤・CDN・IaC は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

## 出典

- 答え: `infrastructure-design-questions.md`（Q1: A、まとめの確認は Looks correct）
- 上流（どれも `aidlc/spaces/default/intents/261004-role-menu/` の下）:
  - `construction/app-frame-ui/nfr-design/performance-design.md`（1〜8節）・`security-design.md`（1〜9節）・`logical-components.md`（1〜10節）・`traceability.json`
  - ui の単位のため、`scalability-design.md`・`reliability-design.md`・`observability-design.md` は作られていない（NFR 設計の段の `produces_kinds`）
  - `construction/app-frame-ui/nfr-requirements/tech-stack-decisions.md`（NFR6.3・NFR6.6）
  - `construction/app-frame-ui/functional-design/functional-spec.md`・`frontend-components.md`
  - `inception/domain-design/components.md`（AppFrame・MyPermissionsUi・TablePlaceholderUi・SharedTreeView・ApiClient）
  - `inception/contract-design/contract-summary.md`（C2・C8・C9）
  - `inception/delivery-planning/bolt-plan.md`（B9）
  - role-admin-ui と navigation の基盤の設計の成果物（読み取りだけ）
- 既存のもの（正とする。読むだけ）:
  - `frontend/package.json`・`frontend/vite.config.ts`・`frontend/scripts/check-bundle-size.mjs`
  - `build.gradle.kts`（`vendorInstall`・`vendorBuild`・`vendorUnchanged`・`osvScan`）
  - `.github/dependabot.yml`
  - `backend/src/main/java/cherry/mastersmith/config/WebConfig.java`
  - `vendor/make-you-chic-ui`（中身は変えない）

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| Compute model | 画面は SPA で、利用者のブラウザで動く。配るのは既存のアプリのコンテナ（実行可能 WAR）で、変えない | `team.md` の Code Style |
| 画面の成果物 | `frontend/dist` を `bootWar` が同梱する。骨組みの新しい部品（`app/work-role`・`app/navigation`）は入口に入る。`features/tables`・`features/mypermissions`・`LogoutPage` は遅延読み込み（`lazy`）の別の塊 | `performance-design.md` 5節 |
| 入口の量の判定（Q1: A） | B9 の後、manifest の入口からたどった静的な import の中に、上の3つの塊が無いこと。前後の値と塊の大きさは記録する（`cicd-pipeline.md` 6節） | 骨組みが入口で増えることを受け入れたまま、新しい画面が入口に入っていないことを確かめる |
| 入口（`main.tsx`） | 変えない。B8 で data router に替わる前提。入っていなければ `BrowserRouter` のまま進め、未保存の確かめ（D27）だけが出ない | `logical-components.md` 3節・9節 |
| Networking | 同じオリジン（8080）。CORS の設定は置かない | `team.md` の Code Style |
| 深い道 | `/tables?schema=…&table=…`・`/me/permissions`・`/logout` を開き直したときに `index.html` を返す既存の配信（`WebConfig`）を変えない | — |
| Storage | ブラウザの `sessionStorage` の1つの鍵（`mastersmith.nav.expanded`）に、開閉の状態の項目の `id` の配列だけを置く。ログアウトと未ログインの知らせで消す。サーバー側の保存は足さない | `security-design.md` 3節 |
| デザインシステム | `vendor/make-you-chic-ui` の固定先を e82b651 から 5bf1ffe に上げる（2節）。中身は変えない | NFR6.6、`project.md` の Forbidden |
| Environments | 配備は `compose.yaml` の同じイメージ。E2E は WAR を一時の内部DB で起動して流す | 既存のまま |
| IaC approach | 作らない | 配備先が決まっていない |
| Configuration | `vite.config.ts`・`vitest.config.ts`・`playwright.config.ts`・`application.yaml`・`.github/dependabot.yml` は変えない | — |
| Dependencies | 新しい npm の依存は足さない（`frontend/package.json`・`package-lock.json` を変えない） | NFR6.5 |
| Rollback | 直前の版のイメージで起動し直す（イメージだけ）。前の版には U7 の骨組みが無く、今までのサイドバーに戻る。古い固定先の make-you-chic-ui もイメージに同梱されているため、イメージだけで戻る | 4節 |

## 2. make-you-chic-ui の固定先の更新（B9 の最初の専用のコミット）

| 手順・確かめ | 中身 | 出典 |
|---|---|---|
| 専用のコミット | `vendor/make-you-chic-ui` の固定先だけを e82b651 から 5bf1ffe に上げる（上流に追加の依頼が入っていれば、その版）。コミットの前に依頼者の承認を得る | `project.md` の Mandated（承認を得た専用のコミット）・Change Control |
| 記録 | 前後のハッシュ、2つの版の `git diff --stat`、下の確かめの結果 | `project.md` の Mandated、NFR6.6 |
| 変わらないこと | 2つの版の間で `package.json`・`package-lock.json`・`LICENSE` に変更が無い（Apache License 2.0 のまま） | `logical-components.md` 2.1節 |
| `vendorInstall` | `npm ci` が lockfile どおりに通る（`--ignore-scripts` を付けない。`project.md` の学び） | NFR6.6 |
| OSV-Scanner | `osvScan` の3つの lockfile（`backend/gradle.lockfile`・`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json`）で通る | `team.md` の Code Style |
| `vendorUnchanged`・`verify` | サブモジュールの追跡されるファイルが変わっていない。1コマンドの検査が通る | `build.gradle.kts` |
| アイコン | 一覧（18 個）が変わらず、`allowed-icons.txt`（U5）と画面の `app/registry` の一覧が一致する | NFR6.6、navigation の NFR6.7 |
| Dependabot | `.github/dependabot.yml` は変えない。npm の項は `/frontend` だけを見て、`vendor/make-you-chic-ui` は見ない。サブモジュールの依存の更新は make-you-chic-ui のリポジトリ側で行い、このリポジトリでは OSV-Scanner の関門で気づく | `.github/dependabot.yml`、`team.md` の Way of Working |
| 統合の形 | `team.md` の fast-forward の例外（サブモジュールの固定先の更新を含む変更）を使ってよい。squash にするか fast-forward にするかは B9 のコード生成の計画で決める | 機能設計 D28 |

## 3. Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| 画面の静的配信 | web サーバー（既存） | 変えない。遅延読み込みの塊も同じ道で配る | キャッシュの扱いは既存の `CacheControlFilter` のまま |
| U4・U5 の API | 呼ぶ先 | 変えない | `/api/me/work-role`・`/api/me/navigation`・`/api/me/table-access`・`/api/me/permissions/…`。時間・5xx は区画で見る（`monitoring-design.md` 3節） |
| cache・queue・search・CDN・DNS・load-balancer | — | 使わない | — |

## 4. 配備の段への引き継ぎ

- U7 自身の移行・設定の変更は無く、戻しはイメージだけです。
- **古いタブの遅延読み込みの失敗**（role-admin-ui の読み直しの R-06）:
  - 入れ替えの前に開いていたタブは、古い入口が古い塊の名前を要求するため、`lazy` の画面（置き場・自分の権限・ログアウト）への移動で読み込みに失敗しえます。
  - 配備の後のスモークテストの最初に「開いているタブを読み込み直す」手順を置きます。
  - 画面の側で読み込みの失敗を拾って読み込み直す仕組みは足しません（受け入れた制約）。
- **スモークテストの前提**: 配備の後にメニューを見るには、版 2 の DSL・ロール・作業ロールの用意が要ります。用意の操作は監査に残るため、前もって依頼者に伝え、期待の件数を合わせてから数えます（`project.md` の学び）。
- **はみ出しの確かめ**: 開いた作業ロールの Dropdown・5 段のサイドバーが画面の中に収まることを、配備の後にも見ます（`project.md` の学び）。

## 5. Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| make-you-chic-ui の固定先（`vendor/make-you-chic-ui`） | U7（B9 で更新） | 画面のすべての機能 | 中身は変えない。更新は専用のコミット（2節） |
| 登録の型と骨組みの口（`beforeNavigate`・`clearLogoutIntent`・`useClearLogoutIntent`） | U7（B9 で足す互換の変更） | `features/auth` と骨組み | 骨組みは `features/` を import しない。印を消す口は `LoginStateGate` の文脈で渡す（`logical-components.md` 3節） |
| 画面の入口（data router） | U6（B8） | U7 | U7 は `main.tsx` を変えない |
| E2E の番号と DSL の見本 | U6（140・150 の案）と U7（160・170 の案） | 全 E2E（1つの WAR と内部DB を番号の順に共有） | U7 の I の流れは `E2EI_`、F の流れは `E2EF_` で、名前が重ならない。番号は B9 の計画の最初に確定する |
| E2E の補助（`e2e/support/userMenu.ts`・`frameApiRoute.ts`・`navTreeFixtures.ts`） | U7 | U7 の 160・170 と、既存の `registeredUser.ts`・`080` の開き口 | 既存の E2E の確かめていた中身を減らさない |
| 手元の監視のダッシュボードの区画（この Intent の API） | Observability Setup（group の Q1 A） | U3・U4・U5 の API（U7 が呼ぶ先） | `monitoring-design.md` 3節 |

## 6. 上流との差

| 上流 | 上流の書き方 | この設計 | 理由 |
|---|---|---|---|
| `performance-design.md` 5節（NFR2.10） | 入口の値を B9 の前と後で測って記録する。`lazy` は登録のテストで確かめる | 記録に加えて、B9 の後に manifest の入口からたどった静的な import の中に3つの画面の塊が無いことで判定する。前の値は B9 の変更の前に取る（`cicd-pipeline.md` 6節） | Q1: A。登録が `lazy` でも、静的な import で画面が入口に入ると気づけないため。role-admin-ui の入口の量の判定（その読み直しの R-01）も同じ形にそろえる |
