# Build and Test の直しの記録 — 261004-role-menu

作業ブランチ `fix/261004-role-menu-bt`（`develop` の 0415b94 から）。依頼者の答え（`build-and-test-questions.md` の Q1〜Q4 と Looks correct）のうち、Q2 A と Q3 A・B・C・D の直しを記録する。Q1（権限の YAML の上限）は測りだけを行い、値は変えていない（下の 1節）。コミットはしていない（提案して承認を得てから行う）。

## 1. Q1 権限の YAML の上限の測り（値は変えていない）

- 設計の想定の規模での正しい書き出しの大きさと節の数、配備と同じ上限（mem_limit 2g・最大ヒープ 1 GiB）の使い捨ての環境での確かめと適用の時間とヒープを測った。結果と上限の値の案は、依頼者に渡す測りの記録（開発担当の作業メモ `bt-q1-limits.md`）に書いた。要点は次のとおり。
  - 1万カラム明示のロール1つは 21,019 の節、0.26 MB（短い名前）〜0.40 MB（20 文字）〜0.82 MB（63 文字）。目安の 100 ロールすべて明示は 210 万の節・26〜40 MiB で、どの候補でも1ファイルに入らない（ロールで分けて読み込む）。
  - 正しい書き出しの形で、約 29 万（5.65 MiB）・50 万（9.68 MiB）・80 万（9.83 MiB）・99 万（12.16 MiB。大きさの上限を使い捨ての環境だけで上げた）の節を確かめと適用した。どれも OOM・Full GC なし、確かめ 2.0〜4.7 秒・適用 3.8〜15.2 秒（目標 15 秒・30 秒の内）。10 MiB 前後と 12 MiB では適用の GC の直前のヒープが 909〜996 MB に届き、Evacuation Failure が出た回があった。
  - 推奨の案: 節 500,000・大きさ 5 MiB（書き出しの超えの印と節の上限が食い違わない組み合わせ）。依頼者が案 A（節 500,000・5 MiB）に決め、下の 9節で直した。
- 本物の書き出しの API（`GET /api/admin/role-transfer/export`）の出力と、測りに使ったファイルがバイト単位で同じであることを確かめた。

## 2. Q2 A: JVM の `-XX:+ExitOnOutOfMemoryError`

| 項目 | 内容 |
|---|---|
| ファイル | `Dockerfile`（ENTRYPOINT の既定の引数に `-XX:+ExitOnOutOfMemoryError` を足し、理由の説明を足した。`MASTERSMITH_JAVA_OPTIONS` の口と ENTRYPOINT の形は変えていない）、`README.md`（JVM の設定の説明に1行足し、`MASTERSMITH_JAVA_OPTIONS` の表の既定の引数の一覧に足した） |
| テストの JVM | 足していない（`backend/build.gradle.kts` は変えていない） |
| 確かめ | (1) `docker build -t mastersmith:bt-q2 .` → `docker run --rm mastersmith:bt-q2 -XX:+PrintFlagsFinal -version` で `ExitOnOutOfMemoryError = true {command line}`・`MaxRAMPercentage = 50.0`。(2) 別の名前のコンテナ `ms-bt-q2`（`-m 2g --cpus 4`、仮の値の環境ファイル）で起動し、`/actuator/health` が 200（健全）、PID 1 のコマンド行が `java -XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Tokyo -Dh2.compactThreads=1 -jar /app/mastersmith.war`。(3) 別の名前のコンテナで `MASTERSMITH_JAVA_OPTIONS=-Xmx16m` を渡して起動し、ヒープが尽きて `Terminating due to java.lang.OutOfMemoryError` を1行出して終わり（終了コード 3、OOMKilled false）、動き続けないことを確かめた |
| 片付け | 確かめのコンテナ2つとイメージ `mastersmith:bt-q2`、仮の値の環境ファイルを消した。配備したアプリのコンテナ（`mastersmith-app-1`）とイメージ `mastersmith:local` は変えていない |

## 3. Q3 A（app-frame-ui R-01）: 作業ロールの切り替えの後の読み直しの失敗

| 項目 | 内容 |
|---|---|
| ファイル | `frontend/src/app/navigation/BusinessNavigationProvider.tsx` |
| 直し | 読めた木に、そのときの作業ロールの切り替えの回数（`WorkRoleProvider` の `revision`）を持たせた。読み直しが失敗したとき、前の木が今と違う回数のときに読んだものなら、木を捨てて初めての読み込みの失敗と同じ `load-error` にする。サイドバーは既存の押せない項目「業務のメニューを読み込めませんでした」（`sidebar-nav-biz-load-error`、D18）、ホームは既存の案内「業務のメニューを読み込めませんでした。」と［もう一度］（D19）を出す。新しい文言・形は足していない。同じ回数の間の読み直しの失敗（道の変化・ログイン状態の知らせなど）は、今までどおり前の木を残す |
| 設計との差 | 機能設計 `app-frame-ui/functional-design/functional-spec.md` の D18「読み直しの失敗では、前に読めた木を残す」に、「作業ロールの切り替えの回数が変わった後の読み直しの失敗では残さない」という例外を足した形になる。`revision` は切り替えの成功（`switched`）と拒否（`rejected`。割り当てが変わった可能性がある）の両方で進むため、拒否の後の読み直しの失敗でも木を捨てる。設計の文書は書き換えていない（`project.md` の決まり）。**依頼者は D18 への例外を受け入れた（決定 3: A）** |
| テスト | `BusinessNavigationProvider.test.tsx` に2件（切り替えの後の失敗で木を捨てて `load-error` になり、［もう一度］で読み直せる。切り替えの後に読めた木は、同じロールの間の読み直しの失敗では残る）、`ShellLayout.test.tsx` に1件（切り替えの後の失敗で、サイドバーに押せない失敗の項目が出て、前のロールの木が消える） |
| コマンドと結果 | `npx vitest run src/app/navigation/BusinessNavigationProvider.test.tsx src/app/layout/ShellLayout.test.tsx src/app/pages/HomePage.test.tsx` → 3 ファイル・56 件 passed |

## 4. Q3 B（group R-03）: 見えない文字だけの名前

| 項目 | 内容 |
|---|---|
| ファイル | バックエンド `group/domain/GroupName.java`・`GroupNameValidation.java`、`role/domain/RoleName.java`・`RoleNameValidation.java`（ロールの名前は group と同じ形だったため同じに直した）。画面 `frontend/src/shared/validation/validateAdminName.ts` |
| 直し | 前後の空白を取り除いた後、長さと制御文字の確かめの後に、すべてのコードポイントが Unicode の White_Space か書式文字（Cf。U+200B・U+200C・U+200D・U+2060・U+FEFF・U+00AD など）なら、空として `INVALID_BLANK`（画面は `required`）にする。目に見える文字と一緒の Cf は今までどおり受け付ける。確かめの順（空 → 長さ → 制御文字 → 見えない文字だけ）は、既存の結果を変えないように最後に置き、バックエンドと画面でそろえた |
| 影響 | ロールの名前を使う権限の YAML の読み込み（`RoleName.parse`）も同じ判定になる。誤りの code・状態コード・画面の文言は既存の `INVALID_BLANK`・`required` のまま |
| テスト | バックエンド: `GroupNameTest`・`RoleNameTest` に3件ずつ（Cf と空白だけの7つの形が `INVALID_BLANK`、目に見える文字と一緒の Cf は受け付けて値を保つ、判定の順）。jqwik の `GroupNamePropertyTest`・`RoleNameKeyPropertyTest` は、材料に U+200B・U+FEFF を足して受け付けの規則の性質に見えない文字だけの条件を足し、性質 `invisibleOnlyNamesAreBlank`（Cf と空白だけの名前は必ず `INVALID_BLANK`、目に見える文字を1つ足すと受け付ける）を足した。画面: `validateAdminName.test.ts` に3件（7つの形が `required`、判定の順、fast-check の性質で同じ2つの性質） |
| コマンドと結果 | `./gradlew :backend:spotlessApply`、`./gradlew :backend:test --tests 'cherry.mastersmith.group.domain.GroupName*' --tests 'cherry.mastersmith.role.domain.RoleName*'` → GroupNamePropertyTest 6・GroupNameTest 25・RoleNameKeyPropertyTest 7・RoleNameTest 25 件 passed。`npx vitest run src/shared/validation src/features/groupadmin src/features/roleadmin` → 39 ファイル・308 件 passed |

## 5. Q3 C（role-admin-ui R-02）: `useSlowNotice` の複製のテスト

| 項目 | 内容 |
|---|---|
| ファイル | `frontend/src/features/groupadmin/useSlowNotice.test.tsx`・`frontend/src/features/roletransfer/useSlowNotice.test.tsx`（新しく作った） |
| 直し | 本体は変えていない（`shared/` への1つにまとめる直しは計画に無いため行わない）。roleadmin の `useSlowNotice.test.tsx` と同じ5つの観点（5 秒の境界、送信中の2回目を捨てる、失敗で状態を戻して投げ直す、同期の例外を失敗として扱い次を受け付ける、離れた後に状態を変えない）を当てた。roletransfer には `testing` の `deferred` が無いため、テストの中に置いた |
| コマンドと結果 | `npx vitest run src/features/{groupadmin,roletransfer,roleadmin}/useSlowNotice.test.tsx --coverage.enabled --coverage.include='src/features/*/useSlowNotice.ts'` → 3 ファイル・15 件 passed。3つの `useSlowNotice.ts` とも行 100%・分岐 75%（roleadmin と同じ。残りは画面を離れた後の答えの枝） |

## 6. Q3 D（app-frame-ui R-05）: 修飾キーつきのクリック

| 項目 | 内容 |
|---|---|
| ファイル | `frontend/src/app/navigation/buildNavSections.ts` |
| 直し | サイドバーの href を持つ項目（ホーム・業務のメニュー・管理）のクリックで、主ボタン以外（中ボタンなど）か Ctrl・Cmd（Meta）・Shift・Alt が押されているときは、`preventDefault` も画面の中の移動もせず、ブラウザの既定（新しいタブ・新しい窓など）に任せる。Alt は依頼の一覧に無いが、ブラウザの既定の操作（保存）を持つ修飾キーのためそろえた。ユーザーメニューの項目（`ShellLayout.tsx`）は依頼の範囲の外のため変えていない。**依頼者は Alt を足したことと、ユーザーメニューを範囲の外にしたことを受け入れた（決定 4: A）** |
| テスト | `buildNavSections.test.ts` に6件（Ctrl・Cmd・Shift・Alt・中ボタンのそれぞれで、ホーム・業務・管理の項目が `preventDefault` と移動をしない。修飾キーが無ければ今までどおり移る）。`ShellLayout.test.tsx` に1件（実際のサイドバーのリンクを Ctrl・Cmd・Shift・中ボタンで押すと既定が止められず、画面の道が変わらない。修飾キーなしでは止めて移る） |
| コマンドと結果 | `npx vitest run src/app` → 37 ファイル・347 件 passed |

## 7. 静的検査

- 画面: `npm run typecheck`・`npm run lint`（oxlint と ESLint）・`npm run format:check`・`npm run license:check` → どれも誤りなし。

## 8. `verify` の結果（止めた）

- 前の確かめ: colima は Running（CPU 4・6GiB）、`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` は README のとおりにシェルに渡した。`pmset -g assertions` で眠らない状態（PreventUserIdleSystemSleep）を確かめた。
- 1回目（21:57 ごろ）・2回目（22:07:54〜22:10:20）の `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` は、どちらも `:frontendTest` で同じ1件が落ちて止まった（1,581 件のうち 1 failed・1,580 passed。バックエンドのテストとカバレッジ・静的解析・秘密情報・脆弱性の検査の段まで進んでいないため、件数とカバレッジは測れていない）。
  - 落ちたテスト: `src/app/layout/ShellLayout.test.tsx` の「work role switcher placement by width (G2: A) > moves the switcher to the top of the content area below 768px and keeps its name」。`TestingLibraryElementError: Unable to find an accessible element with the role "button" and name "作業ロール: 営業"`。そのときの切り替えの部品は「作業ロール: 読み込んでいます」（作業ロールの読み込みの答えがまだ届いていない状態）。
  - 見立て（未検証）: このテストは、帯（`shell-work-role-bar`）が出るのを待った直後に、作業ロールの読み込みの後に出るボタンを同期の `getByRole` で探している。`verify` では Gradle が `:frontendTest` を `:frontendTypecheck` などと並べて動かし負荷が高く、読み込みの答えが帯より後に届くと落ちる。`team.md` の「描画の後に反映される値は waitFor で待って確かめる」に当たる形。今回の直し（`BusinessNavigationProvider`・`buildNavSections`）はこの部品の読み込みに触れないが、同じファイルにテストを3件足したことが時間の並びに影響した可能性は否定できない。
  - 再現の試み（上限を約 10 分と決めた）: このファイルだけを5回（37 件ずつ）と、画面のテストの全体を2回（1,581 件ずつ）、`NODE_OPTIONS=--no-experimental-webstorage npx vitest run` で流し、どれも passed。`verify` の中だけで2回続けて落ちた。
- `team.md` の「同じテストが二度目に落ちたら、原因を直すまで次へ進まない」に当たるため、ここで止めた。E2E の全体は流していない。
- スリープ: `pmset -g log` で 21:40 以降に Sleep・DarkWake・Wake の記録は無かった。

## 9. 不安定なテストの直し（依頼者の決定 1: A）

- `team.md` の不安定なテストの決まりで、同じテストが二度目に落ちた（8節）ため、原因を直した。
- 原因: `ShellLayout.test.tsx` の「moves the switcher to the top of the content area below 768px and keeps its name」は、帯（`shell-work-role-bar`）が出るのを待った直後に、作業ロールの読み込みの答えの後に出るボタン「作業ロール: 営業」を同期の `getByRole` で探していた。帯は描画で出るが、ボタンは作業ロールの読み込みの答え（非同期）の後に出るため、負荷が高い `verify` の中では答えが帯より後に届き、「作業ロール: 読み込んでいます」の状態で探して落ちた（2回の失敗の DOM がこの状態だった）。
- 直し: そのボタンを `await within(switcher).findByRole('button', { name: '作業ロール: 営業' })` で待って押す（期待は変えない。待つ時間の上限は既定のまま）。同じファイルの同じ形（描画の後の効果・非同期の答えで反映される値を同期で確かめている所）を洗い出した。ほかの同期の確かめは、待った要素と同じ描画で出るもの（業務の区画の見出し・開いた枝の項目・押した直後のメニューの項目）か、すでに `waitFor` で効果の終わりを待っているもの（保存の直後の氏名）で、直す所は無かった。
- 確かめ: 直した後、このファイルだけを3回流し 37 件ずつ passed。結果は 10節の `verify`。

## 10. 権限の YAML の上限を下げた（依頼者の決定 2: A、節 500,000・5 MiB）

| 項目 | 内容 |
|---|---|
| バックエンド | `role/service/RoleTransferLimits.java`（`DEFAULT_MAX_BYTES` 10,485,760 → 5,242,880、`MAX_EXPANDED_NODES` 1,000,000 → 500,000）、`role/web/RoleTransferLimitProperties.java`（`@DefaultValue` を 5242880）。`application.yaml` に `mastersmith.role-transfer.max-bytes` の既定は無い（変えていない。`application.yaml` の 10 MiB の記述は DSL の投入の Tomcat の読み捨ての話で別のもの） |
| バックエンドのテスト | `TransferYamlTrees.LIMITS`（5 MiB・500,000）、`RoleTransferReaderTest#expandedNodeBoundary`（500,000 は中身の確かめに進み、500,001 は `TOO_MANY_ALIASES`）、`RoleTransferLimitsApiIT`（`size` は 5,242,880 ちょうどが 200、1 バイト超えが Content-Length・分割送信とも 413。上限ちょうどのファイルは 10 ロール × 1万カラム × 名前 30 文字に直した。`expandedNodeLimit` は 500,000）、`RoleTransferTomcatSwallowIT`（5 MiB の本文、10 ロールに直した）、`LargeRoleTransferYamlTest`（5 MiB ちょうど、10 ロール） |
| 画面 | `features/roletransfer/transferLimits.ts`（`MAX_TRANSFER_BYTES` 5,242,880。文言の `{{limit}}` はこの値から 5 MiB になる）、`TransferFilePicker.tsx`・`useRoleTransfer.ts`・`testing/fixtures.tsx` の説明文、テスト（`transferLimits.test.ts`・`TransferFilePicker.test.tsx`・`RoleTransferPage.test.tsx`・`useRoleTransfer.test.tsx` の境界と文言） |
| 負荷の試験 | `perf/k6/scenarios.js`（説明文と `ROLE_LARGE_COUNT` の既定 25 → 19。準備のロールの合計 1,000 は変わらない）、`perf/README.md`（上限ちょうどの YAML を `large-5mb.yaml`・19 ロール・5,242,880 バイトに。中身は約 96%・約 39 万の節で、残り約 185 KB がコメント） |
| README | 権限の受け渡しの「信頼できない入力の守り」・分割送信・画面の説明の 10 MiB・1,000,000 を 5 MiB・500,000 に直し、下げた理由を1文足した（DSL の管理の 10 MiB は別の上限で変えていない） |
| コマンドと結果 | `./gradlew :backend:spotlessApply`、`./gradlew :backend:test --tests 'cherry.mastersmith.role.transfer.*' --tests 'cherry.mastersmith.role.testsupport.*' :backend:integrationTest --tests 'cherry.mastersmith.role.web.RoleTransfer*'` → RoleTransferReaderTest 18・LargeRoleTransferYamlTest 3・RoleTransferApiIT 7・RoleTransferAuditIT 3・RoleTransferExportLimitIT 1・RoleTransferLimitsApiIT 6・RoleTransferTomcatSwallowIT 2 件 passed。`npx vitest run src/features/roletransfer` → 78 件 passed |
| 直した後の測り | 使い捨ての環境（2g・50%）で、5 MiB の正しい書き出しの最大（39.9 万の節）は確かめ 1.11 s・適用 2.35 s、悪い形（流れの書き方で 49.7 万の節＋1.5 MB の1行のコメント、5,242,880 バイトちょうど）は確かめ 5.42 s・適用 11.41 s。どれも 200、OOM・Full GC なし。悪い形では GC の直前のヒープが 1,024 MB に届き、Young GC の後も 830 MB、Evacuation Failure が 5・11 回（詳しくは測りの記録 `bt-q1-limits.md` の 5節） |

### 設計（NFR）との差（設計の文書は書き換えていない）

- `role/nfr-requirements/security-requirements.md` NFR1.5・`role/nfr-design/security-design.md`（大きさ 10 MiB・展開後の節 1,000,000）→ 5 MiB・500,000。
- `role/nfr-requirements/scalability-requirements.md` NFR2.9（大きさの上限を 10 MiB で確定、「上限はそのロール約 25 個分」）→ 5 MiB（1万カラム明示のロールで約 12〜19 個分）。書き出しの超えの印（`X-Role-Transfer-Exceeds-Import-Limit`）は同じ設定の値で判定するため、5 MiB を超える書き出しに付く。
- `role/nfr-requirements/performance-requirements.md` NFR2.4（「上限ちょうど（10 MiB、約 25 万の対象を置き換える）」・k6 のデータ「置き換える先のロール 25 個」）→ 上限ちょうど 5 MiB、置き換える先 19 個（約 19 万の対象）。時間の目標（確かめ 15 秒・適用 30 秒）は変えていない。
- 画面の側の上限（role-admin-ui の NFR1.5 の `TransferFilePicker`、契約の値 10,485,760）→ 5,242,880。

## 11. 直した後の `verify` と E2E

### 11.1 `verify`（3回目、2026-10-14 00:49:04〜01:03:31、成功）

- 前の確かめ: colima は Running（CPU 4・6GiB、ホームの下を共有）、`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` は README のとおりにシェルに渡し、`pmset -g assertions` で眠らない状態を確かめた。
- コマンド: `caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` → `BUILD SUCCESSFUL in 14m 26s`。対象DB のテストの飛ばしの警告は無かった。
- 件数（実測）:
  - バックエンドの単体テスト 2,102 件（240 クラス）、結合テスト 1,165 件（222 クラス）、どれも失敗 0・飛ばし 0。
  - 画面のテスト 185 ファイル・1,581 件 passed（9節で直したテストを含む）。
- カバレッジ:
  - バックエンド（JaCoCo）全体: 行 97.9%・分岐 92.5%。パッケージ 67 のうち、下限（行 80%・分岐 70%）を下回るのは `common.health`（行 79.2%）だけで、`packagesJudgedByTotal` の一覧にあり全体の合計で判定する（今回は手を入れていない）。手を入れたパッケージ: `group.domain` 行 97.0%・分岐 90.3%、`role.domain` 94.2%・85.7%、`role.service` 96.6%・90.3%、`role.web` 96.0%・86.0%、`role.transfer` 97.8%・91.6%。
  - 画面（`@vitest/coverage-v8`）: 文 95.25%・分岐 90.31%・関数 96.76%・行 95.42%（下限の内）。
- 秘密情報の検出（Gitleaks）: `no leaks found`。
- SpotBugs（＋FindSecBugs）: 関門 `spotbugsGate` を通った。priority 1 の指摘 0 件、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` の指摘 0 件、警告（priority 2・3）278 件。
- OSV-Scanner: `verify` の中では入力が変わらず UP-TO-DATE だったため、`./gradlew osvScan --rerun` で流し直した。失敗の条件に当たるもの 0 件、警告 19 件（`vendor/make-you-chic-ui/package-lock.json` の開発用の npm `undici` など。開発用は警告の扱い）。

### 11.2 E2E（2026-10-14 01:04:18〜01:14:50、成功）

- 前の確かめ: Mailpit の API（`http://127.0.0.1:8025/api/v1/info`）が 200。
- コマンド: `caffeinate -dims ./gradlew e2eTest` → **230 passed（10.4 分）**、失敗・再実行・不安定（flaky）は 0。前回落ちた B4 の 135・B8 の 060 も通った。
- 報告の秘密の確かめ: 「E2E の報告に残してはならない値は含まれていません（値の種類 7・確かめたファイル: json の報告 1・json の報告の添付 301・trace 0・失敗の画面の写し 0・そのほか 0・前の html の報告 0・見つかった件数 0）」。
- 報告の置き場（`frontend/test-results`）は確かめた後に消した（`frontend/playwright-report` は作られていない）。Mailpit に届いたメールは消していない（配備した環境の Mailpit は止めない指示のため）。

### 11.3 スリープ

- `pmset -g log` で、測りと実行の窓（2026-10-14 00:44〜01:15）に Sleep・DarkWake・Wake の記録は無かった。この窓の前（22:21〜00:40）の待ちの間には眠っていたが、どの実行とも重ならない。
