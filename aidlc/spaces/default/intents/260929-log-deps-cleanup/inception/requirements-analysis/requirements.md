# 要件（260929-log-deps-cleanup）

- scope: bugfix、深さ: Minimal、テスト戦略: Minimal
- 出典の書き方: `[desc]` は依頼の文、`[Q<n>]`・`[F<n>]` は `requirements-analysis-questions.md` の答え、`[decided]` は依頼者が Intent の作成の直後（2026-09-29）に決めたこと（同じ質問ファイルの冒頭「決まっていること」）、`[K-<n>]` はコード知識ベースの所見、`[assumption]` は確かめていない前提。

## Intent の分析

Initial description: 「初期管理者の作成のログからメールアドレスを外し、make-you-chic-ui の固定先を 077f5b4 に更新して E2E の既知の違反を外し、Dependabot の spotless と @types/node を取り込み、Jackson をすべての版で ignore にし、TypeScript 7 と typescript-eslint の ignore を決め、ms-check-p95 の境界を直す。team.mdも修正する。」 [desc]

前の Intent（260928-quality-followup）の後に残った小さな直しをまとめた bugfix で、目的は次の4つです。

- `project.md` の Forbidden（メールアドレスをアプリのログに含めない）に反しているログを直す。
- make-you-chic-ui 側で直ったコントラストの不足を取り込み、E2E の「既知の違反」の例外を無くす。
- Dependabot の知らせを整理する（取り込むもの・知らせを止めるものを決める）。
- 手元の監視の警報と、チームの決まりの記述を、今の作りに合わせる。

依頼の文と決めた内容の差: 依頼の文は「TypeScript 7 と typescript-eslint の ignore を決め」だが、依頼者は `typescript` の大きな版だけを ignore にし、typescript-eslint は ignore に入れないと決めた [decided]。依頼の文（`project-description.json`）は書き換えない。

## 機能の要件

### FR1 初期管理者の作成のログのメールアドレス（部品 `user`、K-11）

- **FR1.1** `InitialAdminInitializer` の INFO のログ3か所（既にいるとき・同時の起動で既にいたとき・作ったとき）は、メールアドレスそのものを載せず、FR1.2 の伏せ字の形で載せる [desc][Q1][F1]。
  - Given 初期管理者の設定が正しい、When アプリを起動して初期管理者を作る（または既にいる）、Then INFO の本文とキー・値のどこにもメールアドレスそのものが無く、伏せ字の形が1つ載る。
- **FR1.2** 伏せ字の形は「ローカル部の先頭の1文字＋`***`＋`@`＋ドメイン」とする（例 `a***@example.com`）。この形はメールアドレスそのものではないとみなし、`project.md` の Forbidden に反しないものとして扱う [F1]。
  - `@` を含まない値・ローカル部が空の値は `***` とし、値が無い（null・空）ときはキーを載せない [assumption]。
- **FR1.3** 文言（「初期管理者を作成しました」「初期管理者は既にいるため、作成しませんでした」）は変えない。README の起動の確かめ（215 行）の手順はそのまま使える [assumption]。
- **FR1.4** クラスの説明（Javadoc 38〜40 行の「ログの項目は前の Intent のまま据え置き」）を、今回の決定に合わせて書き直す [K-11]。

### FR2 監査イベントの記録の失敗の ERROR のメールアドレス（部品 `audit`）

- **FR2.1** 監査イベントの記録に失敗したときの ERROR（`監査イベントの記録に失敗しました`）のキー `enteredEmail` は、FR1.2 と同じ伏せ字の形で載せる。ほかの項目（種類・結果・時刻・失敗の理由・接続元・要求のパス・トレース ID など）は変えない [Q2][F2]。
  - Given 監査の書き込みが失敗する、When メールアドレスを入力したログインの失敗を記録しようとする、Then ERROR の `enteredEmail` は伏せ字で、入力したメールアドレスそのものはログのどこにも無い。
- **FR2.2** README 774 行の説明（「この ERROR には…メールアドレスを含む…がキーと値で載ります」）を、伏せ字で載ることと、後から手で補う手がかりが伏せ字・利用者 ID・トレース ID になることに書き直す [F2]。

### FR3 make-you-chic-ui の固定先の更新と E2E の既知の違反（部品 `make-you-chic-ui`・`frontend-e2e`、K-12・K-13・K-16）

- **FR3.1** `vendor/make-you-chic-ui` の固定先を `310e1ec` から `077f5b4` へ上げる。承認を得た専用のコミットで行い、前後のハッシュを記録する [desc][decided]。
- **FR3.2** `frontend/e2e/100-app-text-contrast.e2e.ts` の `STATE_KNOWN_VIOLATIONS` の2件（Tabs の選ばれたタブ、primary の Button の hover）を外し、関連する注記を直す [desc][decided]。
  - Given 固定先が `077f5b4`、When 手元で E2E の 100 を流す、Then 2件の状態でコントラストの違反が無く、100 が通る。
- **FR3.3** README の関連の記述を直す: 38 行の古い固定先（`735ef04`）、158・957・990〜993 行の既知の違反の説明、固定先の解消を説明する節（974〜988 行付近の hover の文字の色の前提を含む） [decided][K-16]。

### FR4 Dependabot の知らせと依存の版（部品 `build-and-verify`、K-14）

- **FR4.1** spotless の版を 8.10.2 から 8.10.3 に上げる（`gradle/libs.versions.toml` の1行。#19） [desc][decided]。
- **FR4.2** `@types/node` を 26.6.2 から 26.6.3 に上げる（`frontend/package.json` と `package-lock.json`。#18） [desc][decided]。
- **FR4.3** `.github/dependabot.yml` の npm に、`typescript` の大きな版（semver-major）だけを ignore にする設定を足す。typescript-eslint（`@typescript-eslint/*`）は ignore に入れない [decided]。
- **FR4.4** `.github/dependabot.yml` の gradle の `tools.jackson:jackson-bom` の ignore を、パッチを含むすべての版に広げる。脆弱性の直しには OSV-Scanner の関門だけで気づくことをコメントに書く [desc][decided]。
- **FR4.5** TypeScript 7.0.2（#5）と jackson-bom 3.1.7（#20）は取り込まない [decided]。

### FR5 警報 ms-check-p95 のしきい値（部品 `perf-and-monitoring`、K-15）

- **FR5.1** 警報 `ms-check-p95` のしきい値を 300 ms から、`http.server.requests` のバケットの境界にある 500 ms に上げる [desc][Q3][F3]。
- **FR5.2** 同じ値を持つ4か所をそろえる: 警報の決まり（`docker/monitoring/provisioning/alerting/mastersmith.yaml`）、ダッシュボードのパネルのしきい値の段、ダッシュボードの SLI の表（確認用 API の 95 パーセンタイル）、README の「警報と対応の手順」の表 [K-15]。
  - Given 直した後の監視の設定、When しきい値を持つ4か所を見る、Then すべて 500 ms で、300 はどこにも残らない。
- **FR5.3** 確認用 API の目標を 300 ms から 500 ms に緩めたことを、この要件と README に記録する [F3]。

### FR6 team.md の記述（K-10）

- **FR6.1** `aidlc/spaces/default/memory/team.md` の Testing Posture の `packagesJudgedByTotal` の記述（「`user.domain`・`user.repository`・`user.service` を含む 22 パッケージ」）を、今のビルド（`backend/build.gradle.kts` の一覧、12 パッケージで `user.*` を含まない）に合わせる [desc][Q4]。
- **FR6.2** Dependabot の ignore の方針は team.md に足さない（`dependabot.yml` のコメントに書く） [Q4]。

## 非機能の要件

- **NFR1 秘密・個人に関する値の漏えい**: FR1・FR2 の後、アプリのログ（標準出力の JSON）に、初期管理者の作成と監査の記録の失敗の経路でメールアドレスそのものが出ないことを、テストで確かめる。伏せ字の形が決めたとおりであることも確かめる（`project.md` の Mandated「不具合を直すときは、再現するテストを同じコミットに含める」）。
- **NFR2 既存の検査が通ること**: `./gradlew verify`（フォーマット・リンタ・ライセンスヘッダー・ビルド・全テスト・カバレッジの下限・秘密情報の検出・静的解析・依存関係の脆弱性の検査）が、対象DB のテストを飛ばさずに通る（`team.md` の Way of Working）。`user.service`・`audit.service` などパッケージごとの下限の対象のパッケージは、下限（行 80%・分岐 70%）を保つ。
- **NFR3 E2E**: 画面に関わる変更（固定先の更新）を統合する前に、手元で E2E（`./gradlew e2eTest`）を流して通す（`team.md` の Testing Posture）。
- **NFR4 監視の設定の正しさ**: 警報の決まりを変えた後、手元の監視を起動して、警報の式としきい値が読み込まれることを確かめる（`project.md` の Corrections「警報やダッシュボードの式は…書いた後にすべての式を実行して正しいことを確かめる」）。

## 制約

- make-you-chic-ui の中身はこのリポジトリから変えない。固定先の更新は専用のコミットで、前後のハッシュを記録する（`project.md` の Forbidden・Mandated）。サブモジュールの固定先を含む変更は、短命のブランチから `develop` へ fast-forward で統合してよい（`team.md`）。
- 依存は lockfile で版を固定する（`project.md` の Mandated）。
- コミットは実行の前に提案して承認を得る。`git push` とプルリクエストの操作は依頼者が行う（`team.md`・`project.md`）。
- team.md は本来、practices-discovery の確かめの場で直すファイルだが、今回は依頼者の依頼（「team.mdも修正する」）により、承認を得たうえで直接直す。

## 前提

- A1 [assumption] FR1.2 の伏せ字の形は、`@` の無い値・空のローカル部では `***`、値が無いときはキーを載せない。
- A2 [assumption] 初期管理者の INFO の文言は変えないため、README の起動の確かめの手順（215 行）はそのまま使える。
- A3 [assumption] 固定先 `077f5b4` で、E2E の 100 の2件が当たらなくなる（コードの調査では見立て。FR3.2 の E2E の実測で確かめる）。
- A4 [assumption] Dependabot のプルリクエスト #5・#18・#19・#20 は、統合の後に依頼者が GitHub で閉じる。
- A5 [assumption] jackson-bom は `tools.jackson:jackson-bom` の1つの宣言だけで足り、ほかの Jackson の部品は Spring Boot の BOM が管理する（K-14 の見立て）。

## 範囲の外

- 監査の記録の失敗の ERROR に載る、メールアドレス以外の項目（接続元 IP・User-Agent など）の扱い。今のまま（外部エクスポートでは伏せる）。
- typescript-eslint の ignore、TypeScript 7 の取り込みと、それに伴う `@typescript-eslint/*` の peer の範囲（`typescript <6.1.0`）の問題 [decided]。
- `@types/node` の大きな版（26）と実行の Node（24）のずれ（K-14。今回は #18 のパッチの取り込みだけ）。
- Dependabot の ignore の方針を team.md に書くこと [Q4]。
- `ms-check-p95` 以外の警報（`ms-login-p95`・`ms-refresh-p95` はしきい値 1000 ms で境界にある）。

## 未解決の点

- 伏せ字にする処理の置き場（共通の部品にして初期管理者と監査で使うか）は Code Generation の計画で決める。
- E2E の 100 で、hover の文字が白に替わった後の組ごとのコントラストの実測値（README 974〜988 行の表の値）は、Code Generation で E2E を流して確かめる。

## Sources

- 依頼の文: `aidlc/spaces/default/intents/260929-log-deps-cleanup/project-description.json`
- 質問と答え: `aidlc/spaces/default/intents/260929-log-deps-cleanup/inception/requirements-analysis/requirements-analysis-questions.md`
- コード知識ベース: `aidlc/spaces/default/codekb/mastersmith2/business-overview.md`（所見 K-10〜K-16 の一覧）、`aidlc/spaces/default/codekb/mastersmith2/architecture.md`（K-15 と Interaction Diagrams 1、ログの伏せ字は外部エクスポートの出口だけ）、`aidlc/spaces/default/codekb/mastersmith2/code-structure.md`（今回に関わるファイル）、`component-inventory.md`（K-11〜K-13）、`dependencies.md`（K-14）、`code-quality-assessment.md`（K-10・K-16）
- 開発担当のスキャン: `aidlc/spaces/default/intents/260929-log-deps-cleanup/inception/reverse-engineering/developer-scan.md`
- 決まり: `aidlc/spaces/default/memory/team.md`・`project.md`
