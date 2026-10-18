# 基盤の仕様 — U4 u4-a11y-carryover

U4 u4-a11y-carryover は ui の単位で、B3（前半）と B7（後半）で作ります。中身は次のとおりです。

- 行の操作のトリガーの名前（「操作: …」、L1 `rowActionsName`）と、トップバーの利用者のメニューの名前（L2）
- E2E の axe の補助の直し（L3 鍵の関数・L4 区分の要約・L5 passes の確かめ・L6 試しの記録の形）
- make-you-chic-ui の固定先の更新（B3 で a9309c4 → b0a1e79。B7 で、依頼（5）の上流の対応が公開されていればもう一度）
- axe-core の 4 系の最新（4.14 以上）への引き上げと、Dependabot の `ignore` の外し（B7）

画面は、既存のとおりビルドの結果（`frontend/dist`）を実行可能 WAR に同梱し、同じオリジンで配ります。バックエンド・内部DB・設定・本番の依存・秘密は変えません。配備先は開発者の PC 上のコンテナだけで、クラウドの基盤・CDN・IaC は作りません（`aidlc/spaces/default/memory/project.md` の Deployment）。

答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/infrastructure-design/infrastructure-design-questions.md`（Q1: A・Q2: A、まとめの確認: Looks correct）。

## 1. Deployment

| Facet | Choice | Rationale |
|---|---|---|
| Compute model | 画面は SPA で、利用者のブラウザで動く。配るのは既存のアプリのコンテナ（実行可能 WAR）で、変えない | `team.md` の Code Style |
| 画面の成果物に入るもの | L1 `rowActionsName`（`frontend/src/shared/row-actions/`）と L2 トップバーの名前（`frontend/src/app/layout/ShellLayout.tsx` の `topbarLabels.userMenu`）。どちらもアプリのコードで、`dist` に入る | 機能設計の D1・D6。NFR 設計のまとめの確認の文言との差は 6節 |
| 画面の成果物に入らないもの | L3〜L6（`frontend/e2e/support/` の中）と axe-core（devDependency）。B3・B7 のビルドの後に、`frontend/dist` を文字列（`axe-core`・`axe.run` など）で検索して 0 件であることを確かめる | U4-NFR4.8 |
| Networking | 同じオリジン（8080）。CORS の設定は置かない | `team.md` の Code Style |
| Storage | 足さない。名前の関数は状態・保存を持たない | `performance-design.md` 1節 |
| デザインシステム | `vendor/make-you-chic-ui` の固定先を B3 で b0a1e79 に上げる（2節）。中身は変えない | U4-NFR4.9、`project.md` の Forbidden |
| Environments | 配備は `compose.yaml` の同じイメージ。E2E は WAR を一時の内部DB で起動して流す（`frontend/playwright.config.ts`、`workers: 1`） | 既存のまま |
| IaC approach | 作らない | 配備先が決まっていない |
| Configuration | 変えるのは `frontend/vitest.config.ts` の対象だけ（Q2: A。`cicd-pipeline.md` 3節）。`vite.config.ts`・`playwright.config.ts`・`application.yaml`・`compose.yaml`・`Dockerfile`・`.env.example` は変えない。`.github/dependabot.yml` は B7 で axe-core の `ignore` を外し、コメントを書き直す（Q1: A） | — |
| Dependencies | 本番の依存は足さない。axe-core（devDependency、MPL-2.0）を B7 で 4 系の最新に上げ、`frontend/package-lock.json` で1つに固定する。`frontend/.npmrc` の `ignore-scripts=true` のまま入れる | `nfr-requirements/tech-stack-decisions.md`、U4-NFR4.8 |
| Rollback | 直前の版のイメージで起動し直す（イメージだけ）。前の版の名前（「の操作」など）に戻る。古い固定先の make-you-chic-ui もイメージに同梱されているため、イメージだけで戻る | 移行・設定の変更が無い |

## 2. make-you-chic-ui の固定先の更新（B3 の最初の専用のコミット）

| 手順・確かめ | 中身 | 出典 |
|---|---|---|
| 辿れること | B3 の最初に依頼者の承認を得て `git -C vendor/make-you-chic-ui fetch` し、b0a1e79 が上流の `main` から辿れることを確かめる。この段では fetch していない（書き込みのため） | `team.md` の Way of Working |
| この段で読み取りで確かめた差 | b0a1e79 は a9309c4 の直後の1コミットで、手元の `origin/main` の先頭。差は `Dropdown.css`・`Dropdown.test.tsx`・`Dropdown.tsx` の3ファイル（119行足し・42行消し） | `git log a9309c4..b0a1e79`・`git diff --stat` |
| lockfile の差 | `vendor/make-you-chic-ui/package-lock.json`・`package.json`・`packages/make-you-chic-ui/package.json`・`LICENSE` に差が無い。`hasInstallScript` の項は前後とも1件で増えていない。そのため `frontend/package-lock.json` も変わらない見込み。B3 で fetch の後に確かめ直す | U4-NFR4.9 |
| 記録 | 前後のコミットハッシュ・差の量・lockfile の差の有無を、コード生成の記録に書く | `project.md` の Mandated |
| 通すもの | `vendorInstall`（`npm ci`。`--ignore-scripts` を付けない。`project.md` の学び）・`vendorUnchanged`・OSV-Scanner（`osvScan` の3つの lockfile）・`./gradlew verify`・E2E の全件 | `cicd-pipeline.md` 5節 |
| 統合 | fast-forward で、固定先の更新の専用のコミットを残す | `bolt-plan.md`（B3）、`team.md` |
| B7 でもう一度上げるとき | 依頼（5）の上流の対応を、報告ではなく上流のソース（読み取りだけの `git show`）で確かめてから、同じ手順で上げる | `team.md`、U4-NFR4.9 |

## 3. Infrastructure Services

| Service | Role | Configuration | Notes |
|---|---|---|---|
| （無し） | — | — | U4 は API・データ・外への接続を持たない。業務データの E2E が使う見本の対象DB は U3 の持ち物（7節） |

## 4. 配備の段への引き継ぎ

- U4 自身の移行・設定の変更は無く、戻しはイメージだけです。
- 配備の後のスモークテストでは、行の操作のトリガーとトップバーのトリガーの読み上げの名前（「操作: …」・「<頭文字>: <利用者のメニュー>」）を確かめる項目を置けます。項目と手順は Deployment Pipeline で決めます。

## 5. Shared Infrastructure

| Shared Resource | Owner Unit | Consumer Units | Access Boundary |
|---|---|---|---|
| 表示の組 20 組（`frontend/e2e/support/displayCombos.ts`） | 既存 | U3・U4 | 変えない。U4 の axe はすべての組で流す（U4-NFR5.1） |
| L1 `rowActionsName`（`frontend/src/shared/row-actions/`） | U4 | U3（業務データの行の操作） | 名前付きのエクスポートの純粋な関数。ログを書かない |
| L5 passes の確かめ（`frontend/e2e/support/axe.ts`）と L3 鍵の関数（`frontend/e2e/support/` の別のモジュール） | U4 | U3（E2E 185） | E2E の補助の中だけ。`dist` に入らない |
| E2E の WAR・内部DB・初期管理者 | 既存 | 全 E2E | 全ファイルで共有して順に流す。初期管理者の状態は変えない（`team.md`） |

## 6. 上流との差

- **120 秒の読み**: `nfr-design/performance-design.md` 3節（40行）は「`frontend/playwright.config.ts` の 120 秒」を E2E ごとの時間の上限と書いています。実際の 120 秒（`timeout: 120_000`）は `webServer` の起動の待ちです。テストごとの上限は設定に無く、長いテストは各ファイルの `test.setTimeout`（120〜240 秒。例 `frontend/e2e/080-preferences-accessibility.e2e.ts` 207行、`frontend/e2e/160-app-frame-accessibility.e2e.ts` 305・416・463行）で決めています。「原因を確かめずに延ばさない」（`team.md`）は、起動の待ちとテストごとの上限の両方に当てます。承認済みの文書は書き換えません。
- **L1・L2 が dist に入ること**: NFR 設計のまとめの確認には「どれも E2E の補助の中だけで使い、成果物には入れない」とありました。実際には、L1・L2 は承認済みの機能設計（D1・D6）のとおりアプリのコードで、`dist` に入ります。入らないのは L3〜L6 と axe-core だけです。この段の「決まっていること」で依頼者に明示し、まとめの確認（Looks correct）を得ました（NFR 設計のレビューの R-06）。

## 7. 後の段に回すこと

- **B3（コード生成の計画）**
  - 依頼者の承認を得て `git fetch` し、b0a1e79 が上流の `main` から辿れることと、2節の lockfile の差を確かめ直す。
  - NFR 設計のレビューの残り（承認の場の決定のとおり、推奨の方向でコード生成の計画で直す）:
    - R-01: menuOwner の選択子を Dropdown のトリガー（`aria-haspopup` と `aria-expanded` の両方を持つ要素）に絞り、サイドバーを展開した場面で失敗にならない確かめを入れる。
    - R-03: owner の末尾の置き換えを、数字だけの末尾と `r<n>` の形に限る。`work-role-switcher` が残る見本を確かめに入れる。
    - R-04: `target` を除いた後の手がかり（`data-testid` を正規化して残すか、祖先のタグの並びを足すか）を決める。
    - R-05: 説明を持つ作業ロールの項目（C6）を前提に入れ、直し先（アプリ側の項目の組み立てか、make-you-chic-ui への依頼か）の判断の基準を決める。
  - R-02 は、この段の Q2: A で置き場を決めた（`cicd-pipeline.md` 3節）。
- **B7（コード生成の計画）**
  - axe-core の版を確定する（その時点の 4 系の最新）。CHANGELOG で既定で流れる規則を確かめ、lockfile の `license` が MPL-2.0 のままか、`hasInstallScript` が無いかを確かめる。
  - `.github/dependabot.yml` の axe-core の `ignore` を外し、コメントを書き直す（Q1: A。`cicd-pipeline.md` 6節）。
  - 依頼（5）の上流の対応が公開されていれば、2節の手順で固定先をもう一度上げる。
  - トップバーの passes の確かめの置き場（130 か新しい E2E）。新しいファイルは U3 の 180・185 と番号を重ねない。

## 8. U1・U2・U3 とそろえる点

- U3 の行の操作は L1 を使い、鍵の owner は `tables-row-actions-*` に正規化する（U3 の NFR4.21）。
- U3 の E2E 180・185 は、B7 と Build and Test の E2E の全件に入る。180 が前提とする見本の対象DB（PostgreSQL）と、040 の前提との衝突の扱いは U3 の基盤の設計で決め、U4 の全件の手順はその結果に合わせる。
- U3 の B5・B6 の 4.14 の試しの結果は L6 の形で記録し、B7 の対象の区分の確定に入れる。
- U1・U2 は画面を持たないため、U4 の配る形とは重ならない。

## Sources

- 答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/infrastructure-design/infrastructure-design-questions.md`（Q1: A・Q2: A、Looks correct）
- NFR 設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/nfr-design/performance-design.md`・`security-design.md`・`logical-components.md`・`traceability.json`。ui の単位のため、scalability・reliability・observability の設計はない
- NFR 設計の承認の場の決定: `aidlc/spaces/default/intents/261016-business-data-views/construction/nfr-design/memory.md`（最後の行）
- NFR 設計のレビュー: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/nfr-design/units/u4-a11y-carryover/df13e747868fc8fa/1.json`（R-01〜R-06）
- NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/nfr-requirements/security-requirements.md`・`performance-requirements.md`・`tech-stack-decisions.md`
- 機能設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/functional-design/functional-spec.md`（D1・D6）・`frontend-components.md`
- Bolt の計画: `aidlc/spaces/default/intents/261016-business-data-views/inception/delivery-planning/bolt-plan.md`（B3・B7）
- 既存のもの（読むだけ）: `frontend/package.json`・`frontend/package-lock.json`・`frontend/.npmrc`・`frontend/vitest.config.ts`・`frontend/playwright.config.ts`・`frontend/e2e/support/axe.ts`・`frontend/e2e/support/displayCombos.ts`・`.github/dependabot.yml`・`.github/workflows/ci.yml`・`vendor/make-you-chic-ui`（a9309c4・b0a1e79）
- 前の Intent の形: `aidlc/spaces/default/intents/261004-role-menu/construction/app-frame-ui/infrastructure-design/`
- 決まり: `aidlc/spaces/default/memory/team.md`・`aidlc/spaces/default/memory/project.md`

## Assumptions & Open Questions

- [assumption] b0a1e79 の後の上流の `main` に、固定先の判断を変えるコミットは無い。B3 の fetch で確かめる。
- [assumption] 固定先の更新で `frontend/package-lock.json` は変わらない（`packages/make-you-chic-ui/package.json` に差が無いため）。B3 で確かめる。
- 依頼（5）の上流の対応が B7 までに公開されるかは未定。間に合わないときの扱いは B7 の時点で依頼者に諮る（`bolt-plan.md`）。
