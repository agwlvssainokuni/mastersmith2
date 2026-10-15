# 要件（261015-deps-ui-carryover）

依頼: 「第1の束と第2の束を同じintentで対応」 [desc]
範囲: bugfix（深さ Minimal、Test Strategy Minimal） [scope]

## Intent の分析

前の Intent 261004-role-menu の振り返りで、持ち越しを3つの束に分けた。今回はそのうち第1の束（依存の更新と性能の確かめ直し: U1・P1〜P7）と第2の束（画面・コードの小さな直し: K1〜K5）を、1つの Intent で片付ける [desc]。

- **目的1（土台を新しく保つ）**: たまった Dependabot の知らせ 20 件を取り込む。Dependabot が見ない固定先も同じ版にそろえ、イメージの版のずれを無くす（U1、所見 K-39）。
- **目的2（性能の基準を取り直す）**: 新しい土台のイメージで、前回確かめ切れなかった性能の項目を測り直し、記録と見積もりを今の姿に合わせる。対象は、ロールの書き込みの待ちの最大（P1）、大きな入力のヒープ（P2）、k6 の台本の独立（P3）、BUSY の結び付き（P4）。
- **目的3（小さな指摘を閉じる）**: make-you-chic-ui の新しい口を取り込み、アプリ側の回避（CSS の `:has()` での上書き）を外す（K1）。あわせて、読み直しで持ち越した画面・コードの指摘を直す（K2〜K5）。

上の目的は、どれも既存の機能の振る舞いを変えない直しと確かめである。例外は2つで、読み込みの部品に1行の長さの上限を足すこと（FR9.2）と、make-you-chic-ui の新しい口による画面の見た目・読み上げの変化（FR2）である。

## 機能の要件

### FR1 依存の更新（U1。部品 `build-and-verify`・`container-runtime`・`perf-and-monitoring`・`frontend-*`、所見 K-39）

- **FR1.1** 今開いている Dependabot の知らせ 20 件を、手元で版と lockfile をまとめて更新して取り込む。GitHub の画面ではマージせず、統合の後にプルリクエストを閉じる [memory:M1]。
  - イメージ: `eclipse-temurin` 25.0.4_7-jre-noble → 25.0.4.1_1-jre-noble（#38）、`grafana/otel-lgtm` 0.34.0 → 0.36.0（#36）、`axllent/mailpit` v1.31.2 → v1.31.5（#35）
  - Gradle: `gradle-wrapper` 9.8.0 → 9.8.1（#46）、`com.diffplug.spotless` 8.10.3 → 8.10.4（#47）、`net.jqwik:jqwik` 1.10.1 → 1.10.2（#37）
  - npm（`frontend/`）: `@vitejs/plugin-react`（#49）、`@typescript-eslint/parser`（#48）、`oxlint`（#45）、`@types/node`（#42）、`vite`（#41。成果物を作る道具）、`axe-core`（#39）、`stylelint`（#34）、`eslint`（#32）、`@vitest/coverage-v8`（#27）、`vitest`（#24）
  - Actions: `actions/download-artifact`（#44）、`actions/setup-node`（#43）、`actions/upload-artifact`（#40）、`gradle/actions/setup-gradle`（#25）
- **FR1.2** イメージは、版のタグと複数アーキテクチャの index のダイジェストで固定する。Dependabot が見ない固定先も同じ版とダイジェストにそろえる [Q2] [memory:M1]。
  - FR1.2a: `eclipse-temurin` の jdk のイメージ（`docker/hikari-pool.sh`・`perf/README.md`・`README.md`）を、jre と同じ版の jdk にそろえる。
  - FR1.2b: `README.md` の mailpit にダイジェストを足し、ほかの固定先と同じ版にそろえる。
  - FR1.2c: `grafana/k6` を、`./gradlew verify` の時点で公開されている最新の版に上げ、ダイジェストで固定する。
  - FR1.2d: `.github/dependabot.yml` の「Dependabot が見ない固定先」の一覧を、実際の固定先と一致させる。
- **FR1.3** 取り込むと `./gradlew verify` か E2E が通らない更新は、その件だけ見送ってよい。見送るときは、`.github/dependabot.yml` の `ignore` に見送る理由と外す時期を書き、プルリクエストを閉じた理由を記録する。ただし、重大度 High 以上の脆弱性の直しを含む更新は見送らず、直して取り込む [Q3] [memory:M1]。
- **FR1.4** 合否: 取り込んだ状態で `./gradlew verify`（3種類の対象DB の結合テストを含む）と E2E が通り、統合の後の CI が通る。取り込んだ件のプルリクエストは閉じている。見送った件は FR1.3 の記録がある。

### FR2 make-you-chic-ui の新しい口の取り込み（K1。部品 `make-you-chic-ui`・`frontend-app-layout-i18n`・`frontend-app-core`、所見 K-44）

- **FR2.1** `vendor/make-you-chic-ui` の固定先を `5bf1ffe` から `a9309c4` に上げる。上げるのは、承認を得た固定先の更新の専用のコミットとし、更新の前後のコミットハッシュを記録する [memory:M3]。
- **FR2.2** 表の右端などに置く Dropdown は、画面に収める新しい口を使う。アプリの CSS の `:has()` での上書きは外す。
- **FR2.3** 畳んだサイドバーで、今の項目の印を新しい口で出す。
- **FR2.4** トップバーの文言（畳むボタン、ユーザーメニューのトリガーの名前）を新しい口から渡し、日本語と英語の両方を出し分ける（今は日本語の固定）。
- **FR2.5** 合否: 画面の単体テストと E2E で、FR2.2〜FR2.4 を確かめる。Dropdown は、開いたメニューが画面の中に収まることを確かめる [memory:M5]。実際のブラウザの axe は、ブランドカラーとテーマのすべての組で、畳んだ状態と開いた状態の両方を見て違反が無い。

### FR3 dsl-v2 の持ち越しの指摘（K2。部品 `dsl`・`dslmanage`・`frontend-feature-dsl` ほか、所見 K-45）

- **FR3.1** DSL の誤り（`DslError`）を文字列にしても、利用者が書いた値の抜粋が出ないようにする（R-05）。直す前に、TRACE を有効にした `*SecretLeakIT` の形で、今は値が出ることを再現するテストを書き、直しと同じコミットに入れる [memory:M2] [memory:M4]。
- **FR3.2** ほかの管理の画面の 360px の幅での崩れ（R-04）、メニューの違いを比べないこと（R-07）、落とした枝を画面に出さないこと（R-08）を直す。対象と直し方は FR12 の手順で決める。
- **FR3.3** 合否: FR12.1 の一覧に書いた指摘ごとに、その一覧の「直った状態」を確かめるテストがあり、通る。画面の直しは、単体テストか E2E で確かめる。

### FR4 navigation の持ち越しの指摘（K3。部品 `navigation`、所見 K-45・K-46）

- **FR4.1** 個人に関する値や利用者の入力を持つ record の `toString` を上書きして、値を伏せる（R-03）。直す前に、TRACE を有効にした `*SecretLeakIT` の形の再現のテストを書く [memory:M2] [memory:M4]。
- **FR4.2** `MenuFilter` の Javadoc に、深さの前提（`DslFormat.MAX_MENU_DEPTH` = 5）を書く（R-04）。

### FR5 cross-cutting の持ち越しの指摘（K4。部品 `frontend-app-core`・`frontend-app-layout-i18n`、所見 K-47）

- **FR5.1** 共有の木が読み込みに失敗したときに、読み上げで失敗が伝わるようにする（R-02）。
- **FR5.2** 管理者でない利用者向けのサイドバーの区画を直す（R-03）。あわせて R-05〜R-07 を直す。対象と直し方は FR12 の手順で決める。
- **FR5.3** 合否: FR12.1 の一覧に書いた指摘ごとに、その一覧の「直った状態」を確かめる画面のテストがあり、通る。読み上げは、ロールと名前（`role`・`aria-live` など）を画面のテストで確かめる。

### FR6 `useSlowNotice` の複製をまとめる（K5。部品 `frontend-feature-groupadmin`・`frontend-feature-roleadmin`・`frontend-feature-roletransfer`、所見 K-48）

- **FR6.1** 3つの機能（groupadmin・roleadmin・roletransfer）に複製された `useSlowNotice` を `frontend/src/shared/` の1つにまとめ、3つの機能から使う。機能どうしの import の禁止（ESLint の `no-restricted-imports`）は崩さない。
- **FR6.2** 合否: まとめた後のフックにテストがあり、3つの機能の画面のテストが通る。

### FR7 k6 の台本の setup の独立（P3。部品 `perf-and-monitoring`、所見 K-42）

- **FR7.1** ロールの3場面（`workRoleRead`・`myPermissionsTree`・`roleAssignmentsRead`）の setup を、前の場面が残した状態に頼らない形にする。割り当て済みでも受け入れ、ロールの数ではなく自分のロールの有無で確かめる [memory:M6]。
- **FR7.2** 合否: 3場面を手順書の順のまま流しても、前の場面の割り当てによる 409（`ROLE_NO_CHANGE`）や確かめの不一致で止まらない。単独で流しても同じ結果になる。

### FR8 ロールの書き込みの待ちの最大の確かめ直し（P1。部品 `role`・`audit`、所見 K-40）

- **FR8.1** ロールの書き込みで接続を2本使うときの待ちの最大を、合否の回を 5 回流して測る。新しい土台のイメージ（FR1）で測る [Q4] [memory:M6]。
- **FR8.2** 20 ms 以上の回の数と、その回のログ・指標の見立てを記録する。目標（20 ms 未満）は緩めない。
- **FR8.3** 原因が分かって直せるときだけ直す。直す形は、その時点で依頼者に諮る。原因が分からないときは、記録を残して `Not Met` のまま持ち越す。
- **FR8.4** 合否: 5 回すべてが 20 ms 未満なら `Met`、1回でも 20 ms 以上なら `Not Met` とする。眠りが入った回など条件のそろわない回は数えずに流し直し、外した回とその理由を記録する。

### FR9 大きな入力のヒープの見直し（P2。部品 `dsl`・`role`・`container-runtime`、所見 K-41）

- **FR9.1** 10 MiB の DSL の投入のヒープを、試しの測定で取り直す。直した見積もりを、記録（Build and Test の成果物と、関わる設計の文書の差の記録）に入れる [Q5]。
- **FR9.2** YAML の読み込みの部品（`SafeYamlParser`。DSL と権限の YAML の両方が使う）に、1行の長さの上限を足す。値はコード生成の計画で決める。
  - 値は、次のすべてが上限の内に収まるように決める: アプリが生成する既定の DSL（最大の 10 MB を含む）、権限の受け渡しの書き出しの YAML、配備済みの内部DB に保存されている DSL（起動のたびに読み込む）。保存済みの DSL は、配備の前に内部DB の複写で最も長い行を測って確かめる。
  - 上限ちょうどは受け付け、上限を超えると拒否する。
  - 生成した既定の DSL と書き出しの YAML が上限の内で読み込めることを、テストで確かめる。
  - 拒否の応答は Problem Details の形で返し、YAML の部品の例外のメッセージを含めない [memory:M7]。
  - 今の大きさ・深さ・別名の上限の確かめと同じ形で、境界のテストを書く。
- **FR9.3** メモリの上限（compose の既定と各 PC の `.env` の値）と、最大ヒープの割合（Dockerfile の `MaxRAMPercentage` 50）を、FR9.1 の測り直しをもとに見直す。変えるときは、変えた値と根拠を記録し、戻し方（設定の値を戻す）を配備の手順に書く。変えないときは、その判断と根拠を記録する。
- **FR9.4** 合否: 10 MiB の DSL の投入と悪い形の 5 MiB の権限の YAML で、ヒープが最大ヒープに届かない（OutOfMemoryError と JVM の停止が無い）。`dslMixed` の `memory.peak` が、FR9.3 の見直しの後のメモリの上限（設定を変えないときは今の上限 2g）の 90% 未満である。前の Intent の実測は 87% だった。

### FR10 BUSY の結び付きの確かめ（P4。部品 `role`・`group`・`common-persistence`・`common-error`、所見 K-40）

- **FR10.1** 既存の結合テスト（`RoleBusyLogTraceIT`・`GroupBusyLogIT`）が、行の排他の待ちの上限切れのときのログ（L3）と応答（L4）の結び付きを確かめているかを確かめる [Q6]。
- **FR10.2** 足りなければ、結合テストに足す。`ROLE_BUSY`・`GROUP_BUSY` の両方で、応答の `code` と、ログの traceId と応答の結び付きを確かめる。k6 では上限切れを狙わない。
- **FR10.3** 合否: 2つの code について、結び付きを確かめる結合テストがあり、通る。

### FR11 配備

- **FR11.1** 統合した版を、開発者の PC 上のコンテナに配備する。配備の後のヘルスチェックとスモークテストが通るまで、配備の完了とはみなさない [memory:M8]。
- **FR11.2** スモークテストには、前の Intent までの項目に加えて今回の変化を入れる: Dropdown（表の右端の行の操作）が画面の中に収まる、畳んだサイドバーに今の項目の印が出る、英語の表示でトップバーの文言が英語になる、1行の長さの上限を超える DSL が Problem Details で拒否される。監査に残る操作は、期待の件数を手順に書く。
- **FR11.3** 戻し方は、直前の版のイメージでの再配備とする。イメージには画面のビルドの結果が同梱されているため、サブモジュールの固定先は戻さない（リポジトリの固定先は `develop` の履歴のまま）。FR9.3 で設定の値を変えたときは、値を戻す手を先に書く。

### FR12 特定できていない指摘の扱い（K2 の R-04・R-07・R-08、K4 の R-03・R-05〜R-07）

- **FR12.1** コード生成の計画で、前の Intent のレビューの記録から各指摘を特定し、指摘ごとに1行で「対象（画面・型・ファイル）」「直った状態（合否）」を一覧にする。同じ番号が複数のレビューにあるときは、どのレビューの指摘かを書く。
- **FR12.2** 一覧は計画の承認で依頼者が確かめる。
- **FR12.3** 1つの指摘が小さな直しで済まないとき（新しい画面・API・表が要る、ほかの機能の振る舞いを変える、など）は、今回の範囲の外へ出し、理由とともに記録して後の Intent に回す。

## 非機能の要件

- **NFR1 検査**: 各統合の前に、`./gradlew verify`（フォーマット・リンタ・ライセンスヘッダー・ビルド・全テスト・カバレッジ下限・秘密情報の検出・静的解析・依存関係の脆弱性検査）が通る。コンテナの実行環境が無くて対象DB のテストを飛ばした状態では統合しない [memory:M1]。
- **NFR2 カバレッジ**: 行 80%・分岐 70% の下限を、全体とパッケージごとに保つ。全体の合計で判定している既存のパッケージ（`packagesJudgedByTotal`）の本体のソースに手を入れたときは、テストを足してそのパッケージの下限を満たし、一覧から外す。値は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測して記録する [memory:M9]。
- **NFR3 性能**: 前の Intent までの性能の目標（ロールの書き込みの待ちの最大 20 ms 未満、各 API の p95 など）は緩めない。測れなかった目標は `Unverified`、外れた目標は `Not Met` として記録する [memory:M6]。
- **NFR4 漏えい**: 個人に関する値・秘密・利用者が書いた値の抜粋が、ログ・監査ログ・エラー応答に出ない（`project.md` の Forbidden）。FR3.1・FR4.1 は `*SecretLeakIT` で確かめる [memory:M2] [memory:M4]。
- **NFR5 画面**: 画面に関わる統合（ブランチ②）の前に、E2E を手元で流して通す。実際のブラウザの axe は、ブランドカラーとテーマのすべての組で違反が無い [memory:M5]。
- **NFR6 脆弱性**: OSV-Scanner の関門（実行時の依存の High 以上、成果物を作る道具と悪意のあるパッケージは開発時の依存でも止める）を通る [memory:M1]。

## 制約

- **作業の区切り** [Q1]: 3つの短命のブランチで、次の順に進める。統合の前に、それぞれ NFR1 を通す。
  - ① 依存の更新（FR1）: `develop` へ squash で統合する。
  - ② make-you-chic-ui の取り込みと、画面・コードの小さな直し（FR2〜FR6）: 固定先の更新の専用のコミットを残すため、`develop` へ fast-forward で統合する（`team.md` の例外）。
  - ③ k6 の台本の直しと性能の確かめ直し（FR7〜FR10）: `develop` へ squash で統合する。新しい土台のイメージ（①）で測る。
- `vendor/make-you-chic-ui` と `vendor/java-mustache-processor` の中身は、このリポジトリから直接変更しない [memory:M3]。
- `origin` への `git push` は依頼者が行う。コミットは、実行の前に提案して承認を得る [memory:M10]。
- 負荷の試験は、配備した環境とは別の使い捨ての環境で流し、配備したアプリを止める時間を記録する。長い試験は `caffeinate -dims` で包み、眠りが入った回は使わない [memory:M6]。
- Performance Validation の段が無いため、k6 と測定（FR7〜FR9）は Build and Test で流す [memory:M6]。

## 前提

- [assumption] A1: `eclipse-temurin` の jre の 25.0.4.1_1 と同じ版の jdk のタグ（`25.0.4.1_1-jdk-noble`）が公開されている。公開されていなければ、jdk の固定先は今の版のまま残し、その理由と、次にそろえる時期を記録する（FR1.2a）。
- [assumption] A2: make-you-chic-ui の `a9309c4` は、今使っている部品の口（`navSections` など）を壊さない。壊すときは、コード生成の計画で影響の範囲に入れる。
- [assumption] A3: 第1の束の P1・P4 の対象（ロールの書き込みの2本使い、行の排他）は、FR1 の依存の更新で振る舞いが変わらない。変わったときは FR8・FR10 の記録に書く。
- [assumption] A4: K2 の R-04・R-07・R-08 と K4 の R-03・R-05〜R-07 は、前の Intent のレビューの記録から対象を特定できる。小さな直しで済まないものは FR12.3 で範囲の外へ出す。

## 範囲の外

- 第1の束の P5（DSL の投入と権限の YAML の確かめを重ねる場面）、P6（置き場の p95 の目標の見直し）、P7（単体テストの JVM のヒープ）。記録だけ残す [Q7]。
- 第3の束（次の機能の Intent）と、配備先が決まるのを待つ項目。
- k6 で行の排他の上限切れを狙う場面（FR10 は結合テストで確かめる） [Q6]。

## 未解決の点

- 1行の長さの上限の値（FR9.2）と、メモリの上限・最大ヒープの割合を変えるかどうか（FR9.3）。前者はコード生成の計画で、後者は Build and Test の測り直しの後に決める。
- K2 の R-04・R-07・R-08 と K4 の R-03・R-05〜R-07 の対象のファイルと直し方（コード生成の計画で特定する）。
- P1 の原因（FR8）。測り直しで分からなければ `Not Met` のまま持ち越す。

## Sources

- 依頼の文: `aidlc/spaces/default/intents/261015-deps-ui-carryover/project-description.json` [desc]
- 範囲: `aidlc/spaces/default/intents/261015-deps-ui-carryover/aidlc-state.md` の Scope（bugfix） [scope]
- 質問と答え: `aidlc/spaces/default/intents/261015-deps-ui-carryover/inception/requirements-analysis/requirements-analysis-questions.md` [Q1]〜[Q7]
- 前の Intent の振り返り: `aidlc/spaces/default/intents/261004-role-menu/operation/feedback-optimization/feedback-loop.md` 2節（第1の束 U1・P1〜P7、第2の束 K1〜K5）
- 前の Intent の持ち越しの指摘: `aidlc/spaces/default/intents/261004-role-menu/construction/build-and-test/build-and-test-summary.md` 5.4
- make-you-chic-ui への依頼（3）: `aidlc/spaces/default/intents/261004-role-menu/make-you-chic-ui-request-3.md`。上流の `main` の `a9309c4`（`git fetch` で確かめた）
- Dependabot の知らせ: `gh pr list`（この段で確かめた、20 件）
- コード知識ベース: `aidlc/spaces/default/codekb/mastersmith2/business-overview.md`（所見 K-39〜K-48 と束の項目の対応表）、`architecture.md`（Interaction Diagrams 5・6）、`code-structure.md`
- 開発担当のスキャン: `aidlc/spaces/default/intents/261015-deps-ui-carryover/inception/reverse-engineering/developer-scan.md`
- 決まり:
  - [memory:M1] `aidlc/spaces/default/memory/team.md` Way of Working（Dependabot の受け方、1コマンドの検査、OSV-Scanner の関門）・Deployment（イメージの固定、フロントエンドの依存の脆弱性）
  - [memory:M2] `aidlc/spaces/default/memory/team.md` Code Style（個人に関する値・秘密を持つ record の `toString`、`XxxSecretLeakIT`）
  - [memory:M3] `aidlc/spaces/default/memory/project.md` Forbidden（vendor の中身を直接変更しない）・Mandated（サブモジュールの固定先の更新は専用のコミット）、`team.md` Way of Working（fast-forward の例外）
  - [memory:M4] `aidlc/spaces/default/memory/project.md` Mandated（不具合の再現のテストを同じコミットに含める）
  - [memory:M5] `aidlc/spaces/default/memory/team.md` Testing Posture（E2E、axe）、`project.md` の学び（画面に固定で置く部品が画面の中に収まることを確かめる）
  - [memory:M6] `aidlc/spaces/default/memory/project.md` Testing Posture の学び（Performance Validation の段が無いときの k6 の持ち主、`caffeinate -dims`、k6 の setup の独立、目標を緩めない）
  - [memory:M7] `aidlc/spaces/default/memory/team.md` Code Style・Testing Posture（利用者が投入する DSL は信頼できない入力、上限の境界のテスト）
  - [memory:M8] `aidlc/spaces/default/memory/team.md` Deployment（配備のたびの起動確認とスモークテスト、戻し方）
  - [memory:M9] `aidlc/spaces/default/memory/team.md` Testing Posture（カバレッジの下限、`packagesJudgedByTotal`）
  - [memory:M10] `aidlc/spaces/default/memory/project.md` Change Control（コミットの提案と承認）、`team.md` Way of Working（`git push` は依頼者）

## Assumptions & Open Questions

- 前提は「前提」の節のとおり（`[assumption]` の A1〜A4）。A1 はブランチ①、A2・A4 はコード生成の計画、A3 は Build and Test で確かめる。
- 未解決の点は「未解決の点」の節のとおり（1行の長さの上限の値、メモリの設定を変えるか、特定できていない指摘の対象、P1 の原因）。
- レビューの指摘 R-01〜R-05 は、依頼者の Request Changes（推奨の案で反映）で反映した（R-01: FR3.2・FR3.3・FR5.2・FR5.3・FR12・A4、R-02: FR9.2、R-03: FR8.4、R-04: FR9.4、R-05: FR11.2・FR11.3）。R-06（P5〜P7 を外したこと）は、Q7: D の依頼者の決定のため対応しない。memory.peak の線 90% は推奨の案の値である。反映した版は、段のやり直しの後にもう一度レビューを受けた。
