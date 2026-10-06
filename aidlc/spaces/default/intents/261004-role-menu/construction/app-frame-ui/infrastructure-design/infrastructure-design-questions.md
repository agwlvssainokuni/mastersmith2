# 基盤の設計の質問 — U7 app-frame-ui

単位 U7 app-frame-ui（kind: ui。B9 で作る）の基盤の設計の前に、決まっていない点を確かめます。作る成果物は、段の定義の `produces_kinds` により `infrastructure-specification.md`・`monitoring-design.md`・`cicd-pipeline.md`・`traceability.json` の4つです。

読んだもの:

- この単位の承認済みの NFR 設計: `nfr-design/performance-design.md`・`security-design.md`・`logical-components.md`・`traceability.json`。それぞれの「承認の場の決定と直し」も読みました。ui の単位のため、scalability・reliability・observability の設計はありません。
- NFR 要件（`nfr-requirements/`。とくに `tech-stack-decisions.md` の NFR6.3・NFR6.6）と機能設計（`functional-design/functional-spec.md`・`frontend-components.md`）
- `inception/domain-design/components.md`、`inception/contract-design/contract-summary.md`（C2・C8・C9）、`inception/delivery-planning/bolt-plan.md`（B9）
- role-admin-ui と navigation の基盤の設計の成果物と、その読み直しの指摘（読み取りだけ）
- 既存のもの（読み取りだけ）:
  - CI とビルド: `.github/workflows/ci.yml`、`.github/dependabot.yml`、`build.gradle.kts`（`vendorInstall`・`vendorBuild`・`vendorUnchanged`・`osvScan` の対象・`e2eTest`）
  - 画面: `frontend/package.json`、`frontend/vite.config.ts`、`frontend/playwright.config.ts`、`frontend/e2e/`（010〜130 と `support/`。`support/mailpit.ts`）、`frontend/scripts/check-bundle-size.mjs`
  - `vendor/make-you-chic-ui`（今の固定先 e82b651。中身は変えない）
- `team.md`・`project.md`

## 決まっていること（質問にしない）

### 配備と基盤の範囲

- 画面は、既存のとおりビルドの結果（`frontend/dist`）を実行可能 WAR に同梱し、同じオリジンで配ります。
- 次のものは変えません。
  - バックエンド・内部DB・接続プール・`application.yaml`・`compose.yaml`・`Dockerfile`・`.env.example`
  - 深い道で `index.html` を返す既存の配信（`WebConfig`）
- 新しい npm の依存は足しません（NFR6.5）。新しい秘密も足しません。
- 骨組みの新しい部品（`app/work-role`・`app/navigation`）は入口に入ります。次の3つは遅延読み込み（`lazy`）で、入口に入れません（`performance-design.md` 5節）。
  - `features/tables`
  - `features/mypermissions`
  - `LogoutPage`（route `/logout`）
- 開閉の状態は `sessionStorage` の鍵に保存し、ログアウトで消します（`security-design.md` 3節）。サーバー側の保存は足しません。
- `main.tsx` は変えません。B8 で data router に替わる前提です。B9 の計画の最初に、B8 の差し替えが入ったかを確かめます。入っていなければ `BrowserRouter` のまま進め、未保存の確かめ（D27）だけが出ない形にします（`logical-components.md` 9節）。

### make-you-chic-ui の固定先の更新（設計で決まっている）

- e82b651 → 5bf1ffe の更新は、B9 の最初の専用のコミットで行います。前後のハッシュと2つの版の `git diff --stat` を記録します（NFR6.6、`project.md` の Mandated）。上流に追加の依頼が入っていれば、その版にします。
- そのコミットで次を確かめます。
  - 2つの版の間で `package.json`・`package-lock.json`・`LICENSE` に変更が無い（Apache License 2.0 のまま）。
  - `vendorInstall`（`npm ci`。project.md の学びのとおり `--ignore-scripts` を付けない）が lockfile どおりに通る。
  - `vendor/make-you-chic-ui/package-lock.json` を含む OSV-Scanner（`osvScan` の対象の3つの lockfile）が通る。
  - `vendorUnchanged`（サブモジュールの中身を変えていない）と `./gradlew verify` が通る。
  - アイコンの一覧（18 個）が変わらず、`allowed-icons.txt` と画面の一覧が一致する。
- 統合は、`team.md` の fast-forward の例外（サブモジュールの固定先の更新を含む変更）を使ってよいことになっています（機能設計 D28）。squash にするか fast-forward にするかは、B9 のコード生成の計画で決めます。どちらでも、統合の前に1コマンドの検査を通します。
- `.github/dependabot.yml` は変えません。npm の項は `/frontend` だけを見ており、`vendor/make-you-chic-ui` は見ていません。サブモジュールの依存は make-you-chic-ui のリポジトリ側で更新し、このリポジトリでは OSV-Scanner の関門で気づきます。

### CI と1コマンドの検査（既にあるものの記録）

- CI・Gradle のタスク・Vitest と Playwright の設定は変えません。
- 画面のテスト（`*.test.ts(x)`、fast-check の `buildNavSections.property.test.ts`・`tablePlaceholderPath.test.ts` を含む）は段 5 で流れます。カバレッジ（全体の合計で行 80%・分岐 70%）は段 7 で確かめます。
- Vitest は `src/**/*.test.{ts,tsx}` のすべてを流すため、名前の振り分けに当たらないテストはありません（group の読み直しの R-01 の確かめ）。
- U7 は `frontend/` の外のファイルを読む画面のテストを持ちません。アイコンの一覧のファイルを読むテストは U5 の持ち物で、`import.meta.url` から道を組みます（navigation の読み直しの R-04）。
- 既存のテストと E2E の補助の書き換え（NFR6.7）は、確かめていた中身を減らしません。対象は次のとおりです。
  - `ShellLayout.test.tsx`・`AppRouter.test.tsx`・`navigationItems.test.ts`・`features/auth` の登録のテスト
  - `e2e/support/registeredUser.ts`・`080-preferences-accessibility.e2e.ts` の開き口（L12 の `userMenu.ts` へ）

### E2E（verify と CI の外）

- 足すファイルは次の2つです（`logical-components.md` 5節・6節）。番号は B9 の計画の最初に、U6 の 140・150 と突き合わせて確定します。
  - **160（案）U7 の画面の検査**: 実際のブラウザの axe（表示の設定の 20 組、組ごとに1つのテスト）、幅 360・768・1280 px と悪い側の1組（lg・dark・360px）、画面の時間の測り。本数に数えません。1組あたりの時間の見積もりで全体が 10 分を超えるなら、160・161 の2つに分けます（分けても数えません）。
  - **170（案）I の流れ**: 前提（`E2EI_` の版 2 の DSL・ロール・招待から登録まで済ませた利用者）を自分で作り、後始末はしません。
- **本数の数え方**（role-admin-ui の読み直しの R-03 の手当て。承認済みの U7 の NFR6.3「Intent で2本のまま」の読み方）:
  - `team.md` の「束ねた元の Intent ごとに数える（Intent 261004-role-menu は最大2本）」のとおり、F（ロールベースの権限）の流れは 150 の1本、I（N 階層のメニュー）の流れは 170 の1本です。合わせて2本です。
  - B9 が 150 に足す後半（作業ロールの切り替え。機能設計 6.5）は、F の流れの同じ1本の中の手順で、本数を増やしません。
  - 160・161 と 140（検査と測り）、既存の E2E の書き換え（080 など）は数えません。
  - この読み方を、U7 の `cicd-pipeline.md` と、承認の場で扱う role-admin-ui の R-03 の手当てにそろえます。
- **Mailpit に残るメール**（role-admin-ui の読み直しの R-04 の手当て）:
  - 170 と 150 の招待のメールは Mailpit に残ります。
  - 既存の E2E の決まり（`e2e/support/mailpit.ts`: Mailpit の API は GET だけを使い、消す API は使わない。メールは開発者が片付ける）のままにします。
  - 宛先は走らせるごとに一意の予約のドメインのアドレスで、検索は宛先で絞るため、前の実行のメールに当たりません。
- E2E の報告の秘密の確かめ（`playwright-secret-check-reporter.ts`）は 160・170 にも当たります。170 は `secretValues.ts` の口で書いた値を読み戻して揃わなければ失敗にします（`security-design.md` 6節）。
- **統合の前**: 手元で `./gradlew e2eTest` の全体（010〜170）を流します。B9 は骨組み・ログアウト・ルーターに手が入るため、次も手元で確かめます（role-admin-ui の読み直しの R-05 の手当て）。
  - ログアウトとログイン
  - 深い道（`/tables?schema=…&table=…`・`/me/permissions`）の再読み込み
  - ブラウザの戻る・進む
  - `/logout` の直接の表示

### 監視

- role-admin-ui と同じく、画面の側の監視（ブラウザの誤りや時間を集める仕組み）は置きません。
- U7 が呼ぶ API の時間・5xx は、ダッシュボードの区画（group の Q1 A。navigation・role の道）で見ます。
  - メニュー: `/api/me/navigation`
  - 置き場: `/api/me/table-access`
  - 作業ロール: `/api/me/work-role`
  - 自分の権限: `/api/me/permissions/…`
- 画面の時間は 160 の測りで記録します（5 回の中央値。目標を超えたら Build and Test が `Not Met` と値で記録し、承認の場で扱いを決める。`performance-design.md` 4.4）。

### 配備の段への引き継ぎ

- U7 自身の移行・設定の変更は無く、戻しはイメージだけです。前の版には U7 の骨組み（作業ロールの切り替え・区画のサイドバー）が無く、今までのサイドバーに戻ります。
- **古いタブの遅延読み込みの失敗**（role-admin-ui の読み直しの R-06 の手当て）: 入れ替えの前に開いていたタブは、古い入口が古い塊の名前を要求するため、`lazy` の画面（置き場・自分の権限・ログアウト）への移動で読み込みに失敗しえます。
  - 配備の後のスモークテストの最初に「開いているタブを読み込み直す」手順を置きます。
  - 画面の側で読み込みの失敗を拾って読み込み直す仕組みは、今の画面に無く、この Intent では足しません（受け入れた制約）。
- 配備の後のスモークテストでメニューを見るには、版 2 の DSL・ロール・作業ロールの用意が要ります。用意の操作は監査に残るため、前もって依頼者に伝えます。

## Q1 入口の JavaScript の量の判定（B9 の前と後）

### 背景

承認済みの U7 の NFR2.10（`performance-design.md` 5節）は、次のように書いています。

- 骨組みの新しい部品（`app/work-role`・`app/navigation`）は入口に入る。
- 入口の値（gzip、`check-bundle-size.mjs`。目安 500KB で超えたら警告だけ）を、B9 の前と後で測って**記録する**。
- `features/tables`・`features/mypermissions`・`LogoutPage` は `lazy` で登録し、入口に入れない。確かめは登録のテストで `lazy` であること。

U7 では入口が増えることが前提なので、role-admin-ui の「増えていない」の判定（その読み直しの R-01 Major で、data router への差し替えで数 KB 増えると成り立たないと指摘され、承認の場で扱う）は、そのままでは当てられません。

一方、U7 の確かめ（登録のテストで `lazy` であること）には弱い点があります。登録が `lazy` でも、骨組みや別の部品が画面のモジュールを静的に import すると、入口に入ってしまいます。また、前の値をいつ取るかも決まっていません。B9 の作業ブランチで変更を始めた後では、前の値を取れません。

`check-bundle-size.mjs` は、Vite の manifest の入口から静的な import をたどって数えます。同じ manifest から、遅延読み込みの塊が入口の静的な import の中に無いことを、決まった形で確かめられます。

### 選択肢

A. **判定は「遅延読み込みの塊が入口の静的な import に無いこと」、値は前と後を記録する（推奨）**
  - B9 の計画の最初（develop の B8 の統合の後、B9 の変更の前）に、`npm run build` と `check-bundle-size.mjs` で前の値を取って記録します。
  - B9 の後に、manifest の入口から静的な import をたどった集まりに、`features/tables`・`features/mypermissions`・`LogoutPage` の塊が無いことを確かめます。台本は変えず、Build and Test で manifest を読んで確かめます。
  - 入口の値の前と後の差と、3つの塊の大きさを記録します。500KB の目安の警告は今のままです。
  - 理由: 骨組みが入口で増えることを受け入れたまま、「新しい画面が入口に入っていない」ことを、大きさの許す幅を決めずに確かめられます。role-admin-ui の R-01 の推奨の案（新しい画面の塊が入口の静的な import に無いこと）とも同じ形です。承認の場で U6 が違う形に決まったら、U7 もそろえ直します。
  - 承認済みの NFR2.10（記録だけ）に、manifest での確かめを足す差として、成果物の「上流との差」に書きます。

B. **入口の増えに許す幅を決める**
  - 例: 前と比べて gzip で N KB まで。幅を超えたら原因を調べます。
  - 数で判定できる代わりに、幅の値の根拠が無く、骨組みの部品の量しだいで空振りか見落としが出ます。

C. **承認済みのとおり記録だけにする**
  - 前と後の値と塊の大きさを記録し、登録のテストで `lazy` であることだけを確かめます。前の値は B9 の計画の最初に取ります。
  - 作業は最も少ない代わりに、静的な import で画面が入口に入っても気づけません。

X. Other (please specify)

[Answer]: A **Mode:** guided

## Consolidated Summary Confirmation

答えのまとめ（app-frame-ui の基盤の設計）:

- Q1 A: 入口の JavaScript の量は、B9 の変更の前に前の値を取り、B9 の後に manifest の入口からたどった静的な import の中に新しい画面（`features/tables`・`features/mypermissions`・`LogoutPage`）の塊が無いことで判定する。前後の差と塊の大きさは記録し、500KB の警告は今のまま。role-admin-ui の入口の量の判定（読み直しの R-01）も同じ形にそろえる。
- 問いにしなかった点と先に手当てした点（E2E は F の流れ 150 と I の流れ 170 の2本で、B9 が 150 に足す作業ロールの切り替えは同じ1本の中の手順として数えず、140・160 の検査と既存の E2E の書き換えも数えない。Mailpit に残るメールは既存の決まりのまま。統合の前の E2E の全体に加えて、ログアウトとログイン・深い道の再読み込み・戻る・進む・`/logout` の直接の表示を手元で確かめる。配備の後のスモークテストの最初に「開いているタブを読み込み直す」を置き、画面の側の仕組みは足さない受け入れた制約とする）と、決まっていること（make-you-chic-ui の固定先 e82b651 → 5bf1ffe は B9 の最初の専用のコミットで行い、前後のハッシュと差分の量・`package.json`・`package-lock.json`・`LICENSE` が変わらないこと・vendorInstall・OSV-Scanner・vendorUnchanged・verify・アイコンの 18 個の一致を確かめる、統合の形は B9 の計画で決める、dependabot.yml・バックエンド・設定・依存・秘密・`main.tsx` は変えない、E2E の番号は計画で突き合わせて確定、画面の側の監視は置かない、戻しはイメージだけ）は案のとおり。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
