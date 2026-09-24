# 要件定義 — 前の Intent で後に回した小さな修正7件

## 意図の分析

- 依頼: 前の Intent（260923-dsl-schema-loader）の振り返りで後に回した小さな修正を、まとめて行う。対象は、Loki のキーと値・同時の初めてのログインの 500・負荷の試験の台本・Hibernate の複数行のログ・メモリの上限の既定・管理者のパスワードの環境変数・make-you-chic-ui の Modal と Alert の7件 [desc]。
- 種類: 不具合の修正（Workflow-selected scope: bugfix）[scope]。深さは Minimal。
- 目的: 運用と試験で見つかった食い違いや不具合を直す。あわせて、手元の監視で絞り込めない・起動のログが決まりから外れる・秘密情報が要らない場所に渡る、といった運用上の問題をなくす。新しい機能は足さない。
- 出どころ: 前の Intent の振り返り（`aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/feedback-optimization/feedback-loop.md` の束 1〜3）。今回のコードの調査の結果は `aidlc/spaces/default/codekb/mastersmith2/code-quality-assessment.md` の TD-1〜TD-7・C-1〜C-6 にある。
- 依頼の番号（1〜7）と、要件の番号（FR1〜FR7）は同じ順に対応させる。

## 機能要件

### FR1 手元の監視（Loki）でログのキーと値を絞り込めるようにする（1件目）

- **FR1.1** 外部への送り出しを有効にしたとき、ログのキーと値（`dsl.operation`・`dsl.outcome` など）を OTLP のログの属性として送る。これにより、Loki で絞り込めるようにする [desc][Q1]。
  - 合格の条件: 送り出しを有効にした結合テストで、送られたログ（`/v1/logs`）に、キーと値が属性として入っている。
- **FR1.2** 送り出すときに、メールアドレス・送り元の IP・User-Agent の値を伏せる（別の値に置き換える）。対象のキーは少なくとも `email`・`enteredEmail`・`sourceIp`・`userAgent` とする。これらを出しているのは `user/service/InitialAdminInitializer.java`（初期管理者の INFO）と `audit/service/AuditEventListener.java`（監査の書き込みの失敗の ERROR）[Q1]。
  - 合格の条件: 結合テストで、送られたログのこれらの属性に元の値が入っていない。キーそのものは残し、伏せたことが分かる値にする。
- **FR1.3** 標準出力の JSON のログは今のままとし、伏せない [Q1]。
  - 合格の条件: 既存の構造化ログのテストが変更なしで通る。
- **FR1.4** パスワード・アクセストークン・リフレッシュトークン・署名鍵の値が、送られたログに入らないことを確かめる（project.md の Forbidden）。
  - 合格の条件: 送られたログの中身を調べる結合テストがある。今の `ExternalExportIT` はトレースだけを調べている。
- **FR1.5** 手元の監視の警報とダッシュボード（`docker/monitoring/`）は、本文の文字列で絞り込む今の式のままとする。キーと値で絞る形への書き換えは、この Intent の範囲の外とする [assumption]。

### FR2 ロックの状態の行が無い利用者が同時に初めてログインしても 500 にしない（2件目）

- **FR2.1** ロックの状態の行が無い利用者に、同時に複数のログインが来ても、500（`INTERNAL_ERROR`）を返さない。行が1つだけ作られ、どのログインもふだんどおり判定する（成功・失敗・失敗の回数の数え方・ロックのしきい値を変えない）[desc]。
  - 合格の条件: 行が無い利用者に同時のログインを送る結合テストで、500 が0件である。行が1つだけできる。失敗のログインの数が、失敗の回数に漏れなく数えられている。
- **FR2.2** 不具合を再現するテストを、直しと同じコミットに入れる（project.md の Mandated）。認証の変更のため、失敗の場合（パスワードの誤り、ロック）のテストも入れる。
- **FR2.3** 直し方（重複を「既にある」として扱う、行の作成を別のトランザクションにする、など）は、コード生成の段で決める。既存の監査の決まり（確定の後に記録する）と、既存の結合テストを保つ [assumption]。

### FR3 負荷の試験の台本で、利用者が VU の間で重ならないようにする（3件目）

- **FR3.1** `perf/k6/scenarios.js` の `dslMixed` で、ログインの VU ごとに別々の試験用の利用者を選ぶ。今は、場面をまたいだ通しの番号を使っているため、同じ `perf-userNN` を2つの VU に割り当てることがある [desc]。
  - 合格の条件: `dslMixed` のログインの VU が使う利用者が、互いに重ならない。台本を読んで確かめ、流したときの記録でも確かめる。
- **FR3.2** 単独の場面（`loginSuccess`・`refresh` など）の利用者の選び方は変えない。

### FR4 起動のときの Hibernate の案内を1行1件のログにする（4件目）

- **FR4.1** ログのメッセージに改行が入っていても、標準出力の JSON のログは1件を1行に出す。対象はすべてのロガーとし、改行は別の記号に置き換える [Q2]。
  - 合格の条件: 起動したときのログで、Hibernate の案内が1行1件の JSON になっている。改行を含むメッセージを出すテストで、出力が1行である。
- **FR4.2** 案内は出したままにする（ロガーの水準は変えない）[Q2]。
- **FR4.3** 例外のスタックトレースの項目の書き方は変えない。改行を置き換えるのはメッセージの項目だけとする [assumption]。

### FR5 アプリのコンテナのメモリの上限の既定を 2g にする（5件目）

- **FR5.1** `compose.yaml` のアプリのメモリの上限の既定を 1g から 2g にする。10MB の DSL を扱うには 2g が要るため [desc]。
- **FR5.2** 既定を書いている次の場所を、2g に合わせて直す。
  - `docker/perf/compose.yaml` 57 行
  - `.env.example` 24〜26 行
  - README 204〜208・230 行
  - `docker/check-container-limits.sh` 20・93 行（既定 1g を期待する確かめ）
  - 合格の条件: これらの場所に、既定 1g と書いた箇所が残っていない。`docker/check-container-limits.sh` を変数なしで流すと、2g（2147483648）を期待して通る。
- **FR5.3** `.env` でメモリの上限を変える口（`MASTERSMITH_CONTAINER_MEMORY`）は今のまま残す。

### FR6 アプリのコンテナに見本の対象DB の管理者のパスワードを渡さない（6件目）

- **FR6.1** 見本の対象DB のための値を、別の環境ファイル（`.env.targetdb`）に分ける。対象は、管理者のパスワード `MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD` と、読み取りのアカウントのパスワード `MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD`。見本は、値を空にした `.env.targetdb.example` として置く [Q3]。
- **FR6.2** `compose.yaml` の `app` は `.env` だけを読む。見本の対象DB の3つのサービス（PostgreSQL・MySQL・MariaDB）は `.env.targetdb` を読む [Q3]。
  - 合格の条件: `docker compose config` で、`app` の環境変数に `MASTERSMITH_SAMPLE_TARGETDB_*` が無い。見本の対象DB のサービスには、それらの値がある（値そのものは表示・記録しない）。
- **FR6.3** 今 `.env` にこれらの値を入れている人のために、`.env.targetdb` へ移す手順を README に書く。`.gitignore` と Gitleaks が `.env.targetdb` を秘密情報として扱うことを確かめる（`.env.*` の決まりで既に外れているかを確かめ、足りなければ足す）[Q3]。
- **FR6.4** アプリが対象DB を読むときの値（`MASTERSMITH_TARGET_DB_*`）は、今のまま `.env` で渡す。

### FR7 make-you-chic-ui の Modal・Alert の直しを取り込む（7件目）

- **FR7.1** サブモジュール `vendor/make-you-chic-ui` の固定先を、`5258c8b` から `edb1f94` に更新する。専用のコミットで行い、更新の前後のハッシュを記録する（project.md の Mandated）[Q4]。
- **FR7.2** 確認の表示（`frontend/src/features/dsl/DslConfirmDialog.tsx` の Modal）の閉じるボタンの名前を `closeLabel` で渡す。結果の知らせ（`frontend/src/features/dsl/DslAdminPage.tsx` の Alert）の閉じるボタンの名前を `dismissLabel` で渡す。名前は画面の言語に合わせて、日本語「閉じる」・英語「Close」とする [Q4]。
  - 合格の条件: 英語の表示で、閉じるボタンの名前が「Close」である。日本語の表示では「閉じる」である。画面部品のテストで確かめる。
- **FR7.3** 確認の表示の本文が、`aria-describedby` でダイアログの説明として結ばれる（`edb1f94` の Modal が自動で行う）。
  - 合格の条件: 画面部品のテストで、ダイアログの説明として本文が読める。vitest-axe の検査が通る。
- **FR7.4** 閉じるボタンを名前「閉じる」で探している既存のテスト（`frontend/src/features/dsl/DslAdminPage.test.tsx` 535 行）が、新しい渡し方でも通ることを確かめる。

### FR8 負荷の試験で2件目の直しを確かめる

- **FR8.1** 試験の手順から「先に1人ずつログインしてロックの状態の行を作る」を外す。`perf/README.md` と、関係する手順の文書を直す [Q5]。
- **FR8.2** 行が無い試験用の利用者のまま同時のログインを流し、500 が0件であることを確かめる [Q5]。この Intent には Performance Validation の段が無い。そのため、この確かめの持ち主は Build and Test とし、使い捨ての環境で行う（project.md の Testing Posture の学び）。
  - 合格の条件: k6 の結果で、ログインの `checks` の率が 1（500 が0件）である。

## 非機能要件

- **NFR1 秘密情報と個人に関する値**: パスワード（平文・ハッシュ値とも）・トークン・署名鍵を、ログ・監査ログ・外部への送り出し・エラー応答に含めない（project.md の Forbidden）。外部へ送るログでは、メールアドレス・IP・User-Agent を伏せる（FR1.2）。`.env`・`.env.targetdb` の値をコミット・記録・表示しない。
- **NFR2 品質の下限の維持**: カバレッジの下限（行 80%・分岐 70%）を下げず、除外も増やさない。パッケージごとの下限も同じ（team.md の Testing Posture）。
- **NFR3 統合前の関門**: `./gradlew verify` のすべての検査を通してから統合する。対象DB のテストを飛ばした状態では統合しない（team.md の Way of Working）。
- **NFR4 1行1件のログ**: 標準出力の JSON のログは、どのロガーのメッセージでも1件を1行に出す（FR4）。
- **NFR5 アクセシビリティ**: 画面部品ごとの vitest-axe の検査が通る（FR7）。
- **NFR6 既存の動作の維持**: ログイン・ロック・監査・DSL の管理の、既存の結合テストと E2E が変更の後も通る。

## 制約

- `vendor/make-you-chic-ui` の中身をこのリポジトリから変更しない。直しはサブモジュールの固定先の更新だけで取り込む（project.md の Forbidden）。
- 配備先は開発者の PC 上のコンテナだけとする。クラウドの基盤は作らない（team.md の Deployment）。
- DB のスキーマの変更は前進のみとし、1つ前の版のアプリが動く後方互換を保つ。この Intent ではスキーマの変更を見込まない [assumption]。
- `origin` へのプッシュは依頼者が行う（team.md の Way of Working）。

## 前提

- 外部への送り出しは既定で無効で、今の送り先は手元の監視（grafana/otel-lgtm）だけである（`.env.example` の `MASTERSMITH_OBSERVABILITY_EXPORT_ENABLED`）。
- ロックの状態の行は、ふだんは利用者と同時に作られる。行が無いのは、SQL で直接入れた利用者（負荷の試験の利用者など）である（`code-quality-assessment.md` の TD-2）。
- Hibernate の案内を出しているロガーの名前は、まだ確かめていない。FR4 はすべてのロガーに効く直し方のため、名前が分からなくても進められる。起動のログで確かめ、記録する。
- make-you-chic-ui の `edb1f94` は、そのリポジトリの `main` の最新で、origin にも送られている（依頼者の PC の隣のリポジトリで確かめた）。
- `.env.targetdb` の名前は例として質問に示したもので、依頼者は分ける方式（Q3: A）を選んだ。名前はこの要件で `.env.targetdb` に決める。

## 範囲の外

- 前の Intent の振り返りの束 2 のうち、内部DB のファイルの伸び（U4-STORAGE-RUN）と、10MB の DSL とログインを重ねたときのメモリ（2g で 93%）の設計の見直し。
- 束 3 のうち、古い版の起動の確かめ（U4-MIGRATION）、アクセシビリティの手での確認、障害の対応の未決の点、SLO の正式な値と配備先の決定。
- make-you-chic-ui に無い部品（ファイルの選択・違いの表の行の開閉・メニューの木）を make-you-chic-ui へ移すこと。
- 手元の監視の警報とダッシュボードを、キーと値で絞る形に書き換えること（FR1.5）。
- 監査の書き込みの失敗の ERROR に載せる項目を減らすこと（標準出力は今のまま。Q1: B）。

## 残る未確定の点

- 伏せた値の書き方（固定の文字列にするか、ハッシュにするか）と、伏せる場所（ログの送り出しの処理の中）は、コード生成で決める（FR1.2）。
- 同時の初めてのログインの直し方は、コード生成で決める（FR2.3）。
- 改行を置き換える記号は、コード生成で決める（FR4.1）。

## Sources

- [desc] Initial description: 前の Intent（260923-dsl-schema-loader）の振り返りで後に回した小さな修正をまとめて行う。(1) Loki でログのキーと値（dsl.operation など）を絞り込めない件（出力の設定を足し、送るキーと値に秘密情報が入らないことを確かめる）。(2) ロックの状態の行が無い利用者が同時に初めてログインすると 500 になる件。(3) 負荷の試験の台本 dslMixed が同じ利用者を2つの VU に割り当てる件。(4) 起動のときの Hibernate の案内が改行を含む1件のログになる件。(5) compose のアプリのメモリの上限の既定が 1g で、10MB の DSL では 2g が要る件。(6) アプリのコンテナが見本の対象DB の管理者のパスワードを環境変数で持っている件。(7) make-you-chic-ui 側で直した Modal・Alert（閉じるボタンの英語表示、aria-describedby）を取り込む（サブモジュールの固定先を更新し、画面で使う）。
- [scope] Workflow-selected scope: bugfix
- [Q1]〜[Q5]: `aidlc/spaces/default/intents/260924-followup-fixes/inception/requirements-analysis/requirements-analysis-questions.md` の回答
- コードの調査の結果: `aidlc/spaces/default/codekb/mastersmith2/business-overview.md`・`architecture.md`・`code-structure.md`・`code-quality-assessment.md`（TD-1〜TD-7、C-1〜C-6）
- 前の Intent の振り返り: `aidlc/spaces/default/intents/260923-dsl-schema-loader/operation/feedback-optimization/feedback-loop.md`（束 1〜3）

## Assumptions & Open Questions

- 手元の監視の警報とダッシュボードは書き換えない（FR1.5）。質問では決めていない。
- 直し方の選び方で、既存の監査の決まり（確定の後に記録する）と既存の結合テストを保つ（FR2.3）。
- 改行の置き換えはメッセージの項目だけとし、スタックトレースの項目は変えない（FR4.3）。
- この Intent では DB のスキーマの変更を見込まない（制約）。
- 伏せた値の書き方、同時のログインの直し方、改行を置き換える記号は、コード生成で決める（残る未確定の点）。
