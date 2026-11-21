## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-21T18:28:57Z
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-design/reliability-design.md > DD-5（確かめ）と DD-3 手順 2、security-design.md > 失敗のログの形（failureKind の決め方） | DD-5 は、外の接続が `flyway_schema_history` を握ると「2 秒台で `MIGRATION_FAILED`（版の番号つき）」になると約束し、確かめも 1 本置いている。しかし failureKind の決め方は「移行の文の失敗の例外があれば MIGRATION_FAILED」で、版の番号は移行のファイル名からしか取らない。履歴の表の読み書きで `lock_timeout` が切れる失敗は、どの移行の文でも起きない。この場合は分類が UNKNOWN になり、版の番号も付かず、DD-5 の確かめが落ちる見込みが高い。対象の表を握られた場合だけ、版の番号つきの MIGRATION_FAILED になる。質問 Q1 の説明も同じ約束をしている | 外の接続に握られた 2 つの場合（履歴の表・移行の対象の表）を分けて、各々の failureKind と migrationVersion の有無を決める。履歴の表の場合は、SQLState 55P03（ロックの待ちの切れ）を MIGRATION_FAILED（版の番号なし）に写すと決めるか、DD-5 の約束と確かめを「対象の表を握らせたとき」に合わせて直す。決めた分類を security-design.md の failureKind の決め方にも書く | New |
| R-02 | Major | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-design/reliability-design.md > DD-1（当てる条件 3 つ）、同 確かめ (1)〜(4) | 当てる条件の 1 つでも外れたら「何もしない」設計になっている。PostgreSQL の内部DB で Bean `dataSource` が `HikariDataSource` でないとき（別の部品が包んだ場合など）や、プール名 `mastersmith-db` が運用者の `spring.datasource.hikari.pool-name` で変わったときは、`lock_timeout` が入らないまま黙って起動する。NFR4.1 が破れても、ログにもテストにも出ない。確かめ (1)〜(4) は、入る場合と対象DB に入らない場合だけで、「当てるべき Bean に当たらなかった」場合を見ない。Bean 名 `dataSource` だけで内部DB のプールは一意に決まる（対象DB の Bean は `targetDataSource`・`targetWriteDataSource`）ため、(c) のプール名は絞り込みに足りる以上に脆い | Bean 名 `dataSource` かつ種類が PostgreSQL のときに `HikariDataSource` でなければ、`InternalDbStartupException`（`UNKNOWN`）で止めると決める（プール名は照合に使わないか、外れたら警告にとどめる）。確かめに「`dataSource` が Hikari でない・プール名が違うとき、黙って通らない」を足す | New |
| R-03 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-design/traceability.json > upstream_ids・coverage | NFR4 の枝番が NFR4.1〜NFR4.10 までで、`observability-requirements.md` にある NFR4.11（指標・警報の継続）と NFR4.12（TRACE のログ）が入っていない。両方とも observability-design.md が設計済みで、security-design.md も「NFR4.12 に対応」と書いている。件数 33 は NFR1・2・4・5・7 の全件ではない | NFR4.11・NFR4.12 を upstream_ids と coverage に足し（target は observability-design.md の指標・警報・ダッシュボードの節と TRACE のログの節）、33 件を 35 件に直す | New |
| R-04 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-design/ の全文書（上流との差の節が無い） | 機能設計への設計側の答え（R-01: 対象の絞り込み、R-03: 起動の入口が `InternalDbUrlResolver` を直接呼ぶ、R-04: EMPTY・UNSUPPORTED を報告するのは BPP）が、機能設計の W1（BPP が `InternalDbSettings` を使う）・BR5.1（報告は Flyway を呼ぶ入口）と違う形で決められている。承認の場まで機能設計を書き換えない決まりなのに、この違いを 1 か所で読める「上流との差」の節が無く、reliability-design.md の DD-1・DD-3 に散らばっている | logical-components.md か reliability-design.md の冒頭に「上流との差」の節を置き、R-01・R-03・R-04 の違い（機能設計の何を・どう変えたか・反映先）を一覧にする | New |
| R-05 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-design/security-design.md > Hikari 自身のログ（NFR2.6） | 「Hikari は ERROR・WARN を出さない」は 1 回の実測で、記録はリポジトリの外にあり成果物には要約しかない。起動時にプールが作れないとき Hikari が自分のロガーで例外つきの ERROR を出すのは一般に見られる動きで、実測と食い違う。絞らない方針は妥当（動作中の警告を隠さない）だが、根拠が確かめられない。また、NFR2.6 の要件は「閉じたポート」と「パスワード違い」の 2 通りを求めるのに、網のテストの設計は前者だけを書いている（後者は U3 側の結合テストの扱いになっている） | 網のテスト（全ロガーの出力の検査）を、閉じたポートとパスワード違いの両方に当てると決める。実測の再現手順（版・条件・見えた行）を成果物か knowledge に残す。落ちたときの扱いは今の記述のまま（依頼者に諮る）でよい | New |
| R-06 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-design/reliability-design.md > DD-3（起動の流れ） | 失敗の報告は「BPP」と「`FlywayMigrationStrategy`」の 2 か所だけで、プールへの最初の接続が Flyway になることを前提にしている。Flyway より前に別の部品（SQL 初期化の検出、ヘルスチェックの先取りなど）が最初の接続を取ると、その失敗は報告の対象を通らず、Spring の既定の失敗の文（ホスト・ポートを含みうる）で止まる | この前提を「前提」として書き、コード生成の起動の失敗のテスト（閉じたポート）が、報告の経路を通ったこと（ERROR が `Internal DB startup failed` の 1 件であること）まで確かめると明記する | New |
| R-07 | Minor | aidlc/spaces/default/intents/261114-internal-db-postgresql/construction/u1-internal-db-platform/nfr-design/（全文書）と functional-design/functional-spec.md > 9 次の段・後の単位に渡すこと | 機能設計が NFR 設計に渡した「依存の追加の確かめの記録の置き場（BR2.12・NFR8）」と「`META-INF/third-party-licenses` の一覧の決まりの確認」について、どの設計文書にも答えが無い（NFR8 は traceability にも出ない）。持ち主（コード生成か Build and Test か）も書かれていない | 持ち主の段を決めて、logical-components.md か traceability.json の持ち越し欄に書く | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 読み取りによる確かめ（traceability.json の 33 件と target の節の突き合わせ） | 33 件すべて OK で、target の節は実在する。NFR4.11・NFR4.12 だけが一覧の外 | 上の Minor（NFR4.11・NFR4.12）のとおり |
| 読み取りによる確かめ（`TargetDataSourceConfig`・`TargetWriteDataSourceConfig`・`application.yaml`） | 対象DB のプールは `new HikariDataSource()` で作る別 Bean（`targetDataSource`・`targetWriteDataSource`、プール名 `mastersmith-target-db`・`mastersmith-target-db-write`）。内部DB は `spring.datasource.hikari`（プール名 `mastersmith-db`）で束ねる `dataSource` | DD-1 の絞り込み自体は衝突しない。束ね込みは「初期化の前」の段のため、「初期化の後」に当てる順序も成り立つ。問題は外れたときの黙った素通しだけ |

### Summary

設計の中心（BPP の順序・対象DB のプールとの分離・Q1: A の反映・失敗の部品の分担と二重のログの防止）は成り立ち、Q1 の選択の外の形（別の接続・Flyway の `initSql`）は代替案の表にだけある。重いのは 2 点で、外の接続に握られたときの failureKind の分類が DD-5 の約束と合わないことと、`lock_timeout` が入らないまま黙って起動できる穴である。どちらも承認の場の判断で直せる範囲のため、判定は READY とし、この 2 点を直してから承認することを勧める。
