# 要件（261025-deps-perf-carryover）

依頼: 前の Intent 261016-business-data-views の振り返りの第1の束（依存の更新と小さな直し）と第2の束（MySQL・MariaDB の読み取りの性能の直し）を1つの Intent で行い、あわせて Spring Boot を 4.1.1 から 4.1.2 に上げる [desc]
範囲: bugfix（深さ Minimal、Test Strategy Minimal） [scope]

## Intent の分析

前の Intent の振り返り（`feedback-loop.md` 2節）で持ち越した項目のうち、優先 1 の第1の束と優先 2 の第2の束を、Spring Boot 4.1.2 への引き上げと合わせて1つの Intent で片付ける [desc]。

- **目的1（土台を新しく保つ）**: たまった Dependabot の知らせ 15 件と、Dependabot が見ない固定先を取り込み、Spring Boot を 4.1.2 に上げて、固定している部品のうち要らなくなったものを外す（A1・A4・Boot）。
- **目的2（小さな直しを閉じる）**: 前の Intent のコードの指摘・手順のつまずきを直す（C1〜C7・G2・G4・G9）。
- **目的3（MySQL・MariaDB の読み取りの基準を取り直す）**: 部分一致の検索で表の全部を2回読むことによる Not Met 4 件に対し、読み取りのプールを 10 に上げ、索引の案内を書き、直した後に3種類の対象DB で負荷の試験を流し直して、Met になった件・ならなかった件を件数で記録する（B1〜B6・G1・P5〜P7）。件数は正確なままにするため、表の全部を2回読む重さは変わらず、Not Met が残りうる。残った件は緩めずに持ち越す [Q1]。この Intent は「Not Met を必ず解消する」のではなく、プール 10 の効果を測って記録することを約束する。

利用者から見えるものや契約が変わるのは、次の6つに限る。(1) 読み取りのプールの既定の値（5 → 10 [Q3]）、(2) DSL の検索の設定の説明の文言 [Q4]、(3) DSL の型の名前に符号なしを足すこと（FR3.1。既存の DSL は変更なしで読める後方互換を保つ）、(4) 範囲の外の整数の応答が 409 から 400 に変わること（FR3.1）、(5) `GET /api/me/table-access` の削除（FR3.3）、(6) パスワードの変更と表示の設定の画面の成功の知らせ（FR3.5）。上の6つ以外の画面の機能と API の応答の形は変えない。

## 機能の要件

### FR1 依存の更新と固定先のそろえ（A1。部品 `build-and-verify`・`container-runtime`・`perf-and-monitoring`・`frontend-*`）

- **FR1.1** 今開いている Dependabot の知らせ 15 件（#54〜#68）を、手元で版と lockfile をまとめて更新して取り込む。GitHub の画面ではマージせず、統合の後にプルリクエストを閉じる [memory:M1]。
  - イメージ: `otel/opentelemetry-collector` 0.162.0 → 0.163.1（#54）、`axllent/mailpit` v1.31.5 → v1.31.6（#55）、`grafana/otel-lgtm` 0.36.0 → 0.37.0（#56）
  - Gradle: `com.github.spotbugs` 6.5.12 → 6.5.13（#65）
  - npm（`frontend/`）: `jsdom`（#68）、`vitest`（#67）、`oxlint`（#66）、`@playwright/test`（#64）、`i18next`（#63）、`@fontsource/noto-serif-jp`（#62）、`eslint`（#61）、`@testing-library/user-event`（#59）、`react-i18next`（#58）、`@typescript-eslint/parser`（#57）
  - Actions: `actions/setup-java` 6.0.1 → 6.1.0（#60）
- **FR1.2** イメージは、版のタグと複数アーキテクチャの index のダイジェストで固定する。Dependabot が見ない固定先も同じ版とダイジェストにそろえ、`.github/dependabot.yml` の固定先の一覧を実際と一致させる [memory:M1]。
- **FR1.3** 取り込むと `./gradlew verify` か E2E が通らない更新は、その件だけ見送ってよい。見送るときは `.github/dependabot.yml` の `ignore` に理由と外す時期を書く。重大度 High 以上の脆弱性の直しを含む更新は見送らず、直して取り込む [memory:M1]。見送った件の記録先は、`.github/dependabot.yml` の `ignore`（理由と外す時期のコメントつき）と、取り込みのコミットのメッセージ（見送った件の番号と理由）の2か所とし、そのプルリクエストは見送りの理由を書いて閉じる。取り込めた件はコミットのメッセージに番号を書き、プルリクエストを閉じる。
- **FR1.4** Playwright（#64）を上げた後も、E2E の全件が通り、報告の json に資格情報が入らない（`webServer.env` を使わない）ことを確かめる [memory:M5]。
- **FR1.5（A2・A3 の扱い）** make-you-chic-ui の lockfile の `braces` 3.0.3 は、上流の `main` の先頭（`ecb07e9`、今の固定先）が同じ 3.0.3 のままで、固定先を上げて直せない。警告のまま記録に残す。依頼（5）（Table の横スクロールの枠の名前、1ページの件数の選択）に当たるコミットは上流に公開されていないため、自前の `ScrollRegion`・`PageSizeSelect` は置き換えない。固定先は上げない [desc]。

### FR2 Spring Boot 4.1.2（部品 `build-and-verify`）

- **FR2.1** Spring Boot を 4.1.1 から 4.1.2 に上げる（`gradle/libs.versions.toml` の `spring-boot`）。
- **FR2.2** 4.1.2 の BOM は Tomcat 11.0.27、Jackson の BOM 3.1.7、OpenTelemetry 1.62.0 を管理する（Maven Central の `org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom` の `tomcat.version`・`jackson-bom.version`・`opentelemetry.version` を読み取りで確かめた事実。依存の解決の結果は A1 のとおりコード生成で確かめる）。Tomcat（11.0.26）と Jackson（3.1.7）の上書きと、`.github/dependabot.yml` の Jackson の `ignore` を外す [Q5]。外した後に、`./gradlew verify` と OSV-Scanner で High 以上が出ないことを確かめる。
- **FR2.3** `opentelemetry-logback-appender`（2.28.1-alpha）と networknt（3.0.6）の固定は残す [Q5]。OpenTelemetry が 1.62.0 のままで Jackson が 3.1 系のままなため。appender は外部エクスポートを有効にして起動し、失敗しないことを確かめる（`verify` には現れない）。固定の説明のコメントと外す時期を、4.1.2 に合わせて更新する。
- **FR2.4** `build.gradle.kts` と `libs.versions.toml` の「Spring Boot を上げるときに見直す」コメントを、見直した結果（外した・残した理由）に更新する。

### FR3 小さな直し（C1〜C7・G2・G4・G9）

- **FR3.1（C1）** DSL の型の名前に符号なしを持たせ、MySQL・MariaDB の範囲の外の整数が、DSL の型の範囲の検証で 400 になるようにする（今は DB の拒否で 409 になる。G13）。合否の基準: (a) 符号なしを持たない既存の DSL（書式の版 2）が変更なしで読めて検証を通る、(b) JSON Schema と DSL の検証が符号なしの型の名前を受け付ける、(c) 符号なしの `INT UNSIGNED` の範囲の境界（最大ちょうどは受け付け、超えると 400）と、符号つきの範囲の外が従来どおりの応答で返ることを、3種類の対象DB の結合テストと単体テストで確かめる。直す前に再現のテストを書き、同じコミットに入れる [memory:M4]。
- **FR3.2（C2）** 文の時間切れの後に接続を捨てる。MySQL の件数の問い合わせが止まると次の1回も 503 になる現状を直す（G15）。合否の基準: 時間切れの1回の後の次の要求が、対象DB が正常なら 200 で返る。再現のテスト（ServerSocket の方式）を同じコミットに入れる [memory:M4]。
- **FR3.3（C3）** 使わなくなった `GET /api/me/table-access` とそのテストを消す。合否の基準: アプリの中にこの道を呼ぶコードが無いことを検索で確かめ、API の分類の網羅のテストと ArchUnit の境界テストが通り続ける。
- **FR3.4（C4）** `docker/hikari-pool.sh compact` が補充の途中に流すと取りこぼす点を直す。合否の基準: プールの補充の途中に流しても、取りこぼさず詰め直しが完了することを、使い捨ての環境で確かめて記録する（再現できないときは、見立てと試みの範囲を記録する）。
- **FR3.5（C5）** パスワードの変更と表示の設定の画面で、切り替えの直後の成功の知らせが出るようにする。合否の基準: 保存の成功の直後に知らせが出て、保存の後に画面を離れても誤動作しないことを、画面のテストで確かめる。描画の後に反映される値は `waitFor` で待って確かめる [memory:M5]。
- **FR3.6（C6）** `K6_IMAGE` の digest と H2 の jar の取り方の2か所書きを1か所にする。合否の基準: 該当の値の定義が1か所だけであることを検索で確かめる。
- **FR3.7（C7）** U1 のコードのレビュー（前の Intent の `.aidlc-reviews/code-generation/units/u1-businessdata-read/` の記録）の Minor の残り5件を、次の内容で直す。R-04 は既知の制約として受け入れ済みのため対象に入れない。R-03 は U2 で接続を作るときに付ける形に直す方針が決まっており、U1 は持ち越しのままとする。
  - R-02: `BusinessDataAuthorizationApiIT` と `AbstractBusinessDataReadIT` に、DSL に無いスキーマ名・テーブル名・並べ替えの列・検索の列の拒否（404 `BUSINESS_TABLE_NOT_FOUND`・404 `BUSINESS_DSL_NOT_APPLIED`・400）を API の結合テストで確かめる。
  - R-05: `StallingRelay` の「止める段」がドライバーの分け方に依存して壊れやすい点を、止める段を明示して確かめる形に直す。
  - R-06: `BusinessDataBoundaryArchitectureTest` の `onlyRowSqlExecutorCreatesStatements` が、`prepareCall` と `Statement` の `execute` 系の呼び出しも、`businessdata` の外を含めて見るようにする。
  - R-07: 検索の条件・並べ替え・主キーの名前が不正なとき、要求の名前をそのまま 400 の `fieldErrors` に返さない（長さの上限か伏せる形）。
  - R-08: 真偽値に読み替える `tinyint` が 0 と 1 以外のとき、`READ_CONVERSION`（409 `MISMATCH`）にする。
  - 各項目の合否の基準: 直す前に失敗する確かめ（再現のテスト）を同じコミットに入れ、直した後に通る [memory:M4]。
- **FR3.8（G2）** 監視の確かめ用に、待ちを入れた少ない要求の台本を足す（負荷の試験の場面を流すと lgtm が止まったため）。合否の基準: その台本（1 VU・繰り返しごとに 1〜2 秒の待ち）で lgtm が止まらず、監視の式の値が確かめられる。
- **FR3.9（G4）** 内部DB の複写を数える道具で、列の名前を `INFORMATION_SCHEMA` で先に確かめ、誤りの文を捨てて件数だけ出す形にする（リポジトリの外の道具の手順は `README` か手順書に書く）。合否の基準: 存在しない列の名前を渡しても、誤りの文（個人に関する値を含みうる）を表示せず、件数だけが出る。
- **FR3.10（G9）** リリースの前の手順に、`osvScan --rerun` と、`dist` に axe-core が入らないことの確かめを入れる。合否の基準: 手順書にコマンドと期待の出力が書かれ、この Intent の取り込みの前に実際に流して結果を記録する。
- **FR3.11** `perf/README.md` (c) の節の `grep -h -o '"checks": {[^}]*}'`（1021 行目）が、複数行の JSON で何も出さない見立てを、流して確かめ、直す。合否の基準: 複数行の k6 の報告に対して `checks` の値が出る形（`jq` など）に直り、手順どおりに流して確かめた結果を記録する。

### FR4 読み取りの性能の直し（B1〜B6・G1。部品 `businessdata`・`targetdb`・`perf-and-monitoring`）

- **FR4.1（B1）** 部分一致の検索の件数は正確なままにする [Q1]。件数を「多数」と出す案、上限つきの件数の案は採らない。Not Met 4 件（U1-NFR1.1・1.3・3.2・2.4）に対して、この Intent が約束するのは、プール 10（FR4.2）で3種類の対象DB を測り直し、4件それぞれが Met になったか Not Met のままかを、場面ごとの値とともに件数で記録することである。見込みの根拠: プール 5 では 10 VU の借りる待ちが 3 秒を超えて 503 になったため（前回の `nfr-validation-matrix.md` の U1-NFR2.4）、プール 10 なら 10 VU を同時に処理できる。ただし MariaDB の深い OFFSET（`bizSearchLast`）は、プールの大きさでは変わらない。Met にならなかった件は FR4.9 のとおり持ち越す。
- **FR4.2（B2）** 読み取りのプールの既定（`TargetDbProperties.Pool.maximumSize`）を 5 から 10 に上げる [Q3]。`.env.example`・README の説明を合わせる。3種類の対象DB の負荷の試験で、対象DB のコンテナの OOMKilled が false で `oom_kill` が 0 であること（合格の条件）を確かめ、対象DB の `anon` の最大を並べて記録する。線は、前の Intent の `nfr-validation-matrix.md` 4節と同じ、`anon` の最大 85%（諮る線。前回は MariaDB 85.01%・MySQL 90.3% でこの線を超え、合格の条件で Met とした）とする。対象DB の使い捨ての環境のメモリの上限は、前回に決めた値（MySQL は 2688m。MariaDB・PostgreSQL は `docker/perf/compose.yaml` の既定 512m。実際の値は Build and Test の計画で確かめて記録する）を使う。動作: (a) OOMKilled か `oom_kill` が 0 でないとき、プール 10 は採らず、既定を 5 に戻して Q3 の判断を依頼者とやり直す。(b) OOMKilled なしで `anon` が 85% の線を超えたときは、合格の条件で Met とし、超えた値を並べて依頼者に諮る（前回と同じ扱い）。
- **FR4.3（B1）** 部分一致の列に索引が効かないことを、README・運用の手順書と、DSL の検索の設定の説明（JSON Schema の `description`）に書く。画面は変えない [Q4]。
- **FR4.4** プールを 10 にしたときの接続の数は、既存の `*ConnectionUsageIT` と同じ形の結合テストで確かめ直す（1要求が使う対象DB と内部DB の接続の数）[memory:M9]。
- **FR4.5（B3・B4）** `dslMixed` の `memory.peak` の余裕（約 55〜62 MiB。出典: 前の Intent の `construction/build-and-test/memory-peak-measurement.md` 3節）を測り直して記録する。`RETENTION_TIME=0`（H2 の保持の設定。同じ記録の B4）を配備物に入れるかは、測った結果を添えて依頼者に諮る。
- **FR4.6（B5・G1）** `perf/README.md` の 2B の MySQL の種の流し方に `--default-character-set=utf8mb4` を足す。試験の値（README・基盤の設計 2.1）を実際の表の大きさに合わせる。種を入れた後に、目印の語の当たり・最長の文字数を数えてから表の大きさを測る。書き込みの場面の後の読み取りが悪い側になる流す順を、計画に書いて見直す。
- **FR4.7（B6）** 画面の時間（U3-NFR1.7）を測り直す。
- **FR4.8（P5〜P7）** 前の Intent（261004-role-menu）の `feedback-loop.md` 2節から持ち越した3件を、次の内容で扱う。
  - P5 任意の重なり: DSL の投入と権限の YAML の確かめを重ねる k6 の場面（dsl-v2 の NFR 設計 R-07）を台本に足して流し、結果を件数で記録する。見積もりのままで測っていなかった項目を、測った値で置き換える。
  - P6 置き場の p95 の目標の見直し: 目標（実測は約 30 分の 1）を締める側の検討とする。この Intent では、測り直した値を記録して締める案を提案するだけで、目標の数値は依頼者が明示に決めたときだけ変える（緩めない）。
  - P7 単体テストの JVM のヒープ 1536m（navigation R-02）: 単体テストが尽きていないかを `verify` の実測で確かめて記録する。尽きたときだけ、境界テストの読み込みを共有する直しを入れる。
- **FR4.9** 直した後に、3種類の対象DB（MySQL・MariaDB・PostgreSQL）で業務データの負荷の試験を流し直し、Not Met 4 件を含む目標の満たし方を件数で記録する。目標の数値は緩めない。直した後も Not Met が残った件は、Not Met のまま記録して次の Intent へ持ち越す [Q1]。
  - 配備したアプリを止める時間は約 4 時間（前回 4 時間 27 分）。長い試験は `caffeinate -dims` で包む [memory:M6]。

### FR5 配備

- **FR5.1** 配備はイメージの入れ替えで行う。スキーマと `.env` の必須の変更は無い（読み取りのプールの既定の変更は、`.env` を変えなくても効く）。戻しは直前の版のイメージの再配備（前の版でもプール 5 で動く）[memory:M8]。
- **FR5.2** 配備の前に、WAR の SHA-256 と中身を確かめる。次の配備で `main` の先頭から WAR を作り直して配備し、配備した版と `main` をそろえる（A4）。配備の後に起動確認とスモークテストを行う。
- **FR5.3** `main` への取り込みの前に、前の取り込みの後のすべてのコミットの CI を試行ごとに確かめ、再実行で通ったものは1回目の失敗とそのテストを記録する [memory:M10]。

## 非機能の要件

- **NFR1 性能の目標は緩めない**: U1 の目標（応答時間・接続の待ち・メモリの線）の数値は、前の Intent の `nfr-validation-matrix.md` のとおり変えない。直した後の値を前の値と並べて記録する。物差しは変えない（変えるのは依頼者が明示に決めたときだけ）[memory:M6]。
- **NFR2 安全**: 依存の脆弱性は、`./gradlew verify` の OSV-Scanner で High 以上が出ない状態で統合する。上書きを外した後（FR2.2）も同じ。パスワード・トークン・業務データの行の値・対象DB の接続情報を、ログ・監査・トレースの属性・エラー応答に含めない（既存の `*SecretLeakIT` が通り続ける）。
- **NFR3 品質の関門**: 統合の前に `./gradlew verify`（フォーマット・リンタ・ライセンスヘッダー・ビルド・全テスト・カバレッジ下限 行 80%・分岐 70%・秘密情報の検出・静的解析・依存関係の脆弱性検査）が通る。画面に触れるブランチは E2E も通す [memory:M5]。カバレッジの測り方は `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `verify` で実測する [memory:M9]。
- **NFR4 互換**: 画面の機能と API の応答の形は、「Intent の分析」に書いた6つの変更を除いて変えない。DSL は書式の版 2 のまま、符号なしを持たない既存の DSL が変更なしで読める。戻しは直前の版のイメージ（前の版でも読み取りのプール 5 で動く）。DB スキーマの変更は無い [memory:M8]。
- **NFR5 テストの扱い**: 不具合の直し（FR3.1・FR3.2・FR3.5）は再現のテストを同じコミットに含める [memory:M4]。不安定なテストが出たときは `team.md` の決まりで扱う。テストの説明文は英語。

## 制約

- 配備先は開発者の PC 上のコンテナの範囲。実在の宛先へメールを送らない [memory:M8]。
- `vendor/make-you-chic-ui`・`vendor/java-mustache-processor` の中身は直接変更しない [memory:M3]。
- `git push` は依頼者が行う。コミットは提案して承認を得てから積む [memory:M10]。
- 対象DB の接続情報・業務データの行の値を、ログ・エラー応答に含めない（`project.md` の Forbidden）。
- この Intent の流れに Performance Validation の段が無いため、k6 と測定は Build and Test で流す [memory:M6]。

## 前提

- **A1** 4.1.2 の BOM の管理する版（Tomcat 11.0.27・Jackson 3.1.7・OpenTelemetry 1.62.0）は、Maven Central の BOM の記述で確かめた。4.1.2 のリリースノートと、実際の依存の解決の結果は、コード生成で `./gradlew dependencies` で確かめる [assumption]。
- **A2** プールを 10 にすると、部分一致の 10 VU の借りる待ちは 3 秒を超えなくなる見込みだが、表の全部を2回読む重さは変わらない。測るまで Met とは言えない（F1 の扱いで持ち越しうる）[assumption]。
- **A3** 対象DB の `anon` の線 85% と、合格の条件（OOMKilled false・`oom_kill` 0）は、前の Intent の `nfr-validation-matrix.md` 4節の扱いを引き継ぐ（FR4.2）[assumption]。
- **A4** `perf/README.md` (c) の `grep` が複数行の JSON で何も出さない点は、読み取りの調べの見立てで、まだ流して確かめていない [assumption]。
- **A5** P5〜P7 と C7 の対象のファイルの場所は、コード生成の計画で `grep` で特定する（内容と合否の基準は FR3.7・FR4.8 に書いた）。特定できないものは、依頼者に諮る [assumption]。

## 範囲の外

- 件数を「多数」「N 件以上」と出す変更、画面での索引の注意の表示（Q1・Q4 で採らなかった案）。残った Not Met は持ち越す。
- A3（make-you-chic-ui の依頼（5））の取り込み、A2（`braces`）の固定先の更新（上流が未対応）。
- 第3の束（他テーブル参照 N1、`users.admin_flag` を縮める N2、N3〜N6）と、配備先の決定を待つもの（E1〜E9）、受け入れ済みの危険（F1〜F11、R1〜R3）。
- 読み取りのプール以外の性能の作り直し（SQL の方言の書き換え、深い OFFSET の作り直し）。

## 未解決の点

- 直した後も残る Not Met の件と、その持ち越し先（負荷の試験の結果で決まる。F1 では持ち越し）。
- `RETENTION_TIME=0` を配備物に入れるか（FR4.5。測った結果で依頼者に諮る）。
- C7 と P5〜P7 の対象のファイルの場所（A5。計画で特定する）。
- 4.1.2 で Tomcat・Jackson の上書きを外したとき、OSV-Scanner が新しい High を出さないか（FR2.2。外した後の `verify` で確かめる）。

## Sources

- 依頼の文: `aidlc/spaces/default/intents/261025-deps-perf-carryover/project-description.json` [desc]
- 範囲: `aidlc/spaces/default/intents/261025-deps-perf-carryover/aidlc-state.md` の Scope（bugfix） [scope]
- 質問と答え: `aidlc/spaces/default/intents/261025-deps-perf-carryover/inception/requirements-analysis/requirements-analysis-questions.md` [Q1]〜[Q5]（Q1: C、Q2: 該当せず、Q3: B、Q4: B、Q5: A、追加の質問 F1: A は FR4.9 と範囲の外に反映）
- 前の Intent の振り返り: `aidlc/spaces/default/intents/261016-business-data-views/operation/feedback-optimization/feedback-loop.md` 2節（第1の束 A1〜A4・C1〜C7・G2・G4・G9、第2の束 B1〜B6・G1・P5〜P7）
- 負荷の試験の結果: `aidlc/spaces/default/intents/261016-business-data-views/operation/performance-validation/nfr-validation-matrix.md`・`test-results.md`
- `memory.peak` の余裕と `RETENTION_TIME=0`: `aidlc/spaces/default/intents/261016-business-data-views/construction/build-and-test/memory-peak-measurement.md` 3節
- C7 の対象: `aidlc/spaces/default/intents/261016-business-data-views/.aidlc-reviews/code-generation/units/u1-businessdata-read/bb969d769be0cb2e/1.json`（R-02〜R-08）
- P5〜P7: `aidlc/spaces/default/intents/261004-role-menu/operation/feedback-optimization/feedback-loop.md` 2節
- Dependabot の知らせ: `gh pr list`（この段で確かめた、15 件）。make-you-chic-ui の上流: `git -C vendor/make-you-chic-ui fetch` の後の `origin/main`（先頭 `ecb07e9`）
- Spring Boot 4.1.2 の BOM: Maven Central の `spring-boot-dependencies` 4.1.1 と 4.1.2 の pom（読み取り）
- コード知識ベース: `aidlc/spaces/default/codekb/mastersmith2/business-overview.md`・`architecture.md`・`code-structure.md`
- 開発担当のスキャン: `aidlc/spaces/default/intents/261025-deps-perf-carryover/inception/reverse-engineering/developer-scan.md`
- 決まり:
  - [memory:M1] `aidlc/spaces/default/memory/team.md` Way of Working（Dependabot の受け方、1コマンドの検査、OSV-Scanner の関門）・Deployment（イメージの固定、フロントエンドの依存の脆弱性）
  - [memory:M3] `aidlc/spaces/default/memory/project.md` Forbidden（vendor の中身を直接変更しない）・Mandated（サブモジュールの固定先の更新は専用のコミット）
  - [memory:M4] `aidlc/spaces/default/memory/project.md` Mandated（不具合の再現のテストを同じコミットに含める）
  - [memory:M5] `aidlc/spaces/default/memory/team.md` Testing Posture（E2E、axe、`waitFor`）
  - [memory:M6] `aidlc/spaces/default/memory/project.md` Testing Posture の学び（Performance Validation の段が無いときの k6 の持ち主、`caffeinate -dims`、目標を緩めない）
  - [memory:M8] `aidlc/spaces/default/memory/team.md` Deployment（配備のたびの起動確認とスモークテスト、戻し方）
  - [memory:M9] `aidlc/spaces/default/memory/team.md` Testing Posture（カバレッジの下限、`*ConnectionUsageIT`）
  - [memory:M10] `aidlc/spaces/default/memory/project.md` Change Control（コミットの提案と承認、取り込みの前の CI の試行ごとの確かめ）、`team.md` Way of Working（`git push` は依頼者）

## Assumptions & Open Questions

- 前提は「前提」の節のとおり（`[assumption]` の A1〜A5）。A1 と A4 はコード生成で、A2 と A3 は Build and Test の負荷の試験で、A5 はコード生成の計画で確かめる。
- 未解決の点は「未解決の点」の節のとおり（残る Not Met の持ち越し、`RETENTION_TIME=0`、P5〜P7 の特定、上書きを外した後の OSV-Scanner）。
