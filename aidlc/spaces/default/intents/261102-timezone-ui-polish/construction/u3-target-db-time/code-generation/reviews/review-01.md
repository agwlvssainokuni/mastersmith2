## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-03
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/code-generation/code-summary.md > 計画との差・決めたこと（読めない主キーの行） | 読めない日時の主キーのセルは DB の文字のまま key に入り（backend/src/main/java/cherry/mastersmith/businessdata/repository/RowSqlExecutor.java の readRow、約 472〜477 行）、送り返すと ColumnValueCodec.read が空を返して既存の 400 VALIDATION_FAILED になる。機能設計 BR3.12 の 409 とは違う。承認の場の決定として記録済みだが、Raw の主キーを送り返す専用テストが無く、挙動は「未確認」とされている。 | Build and Test で、ゼロ日付など読めない主キーを持つ行を詳細・更新・削除へ送り返して 400 になり状態が変わらないことを、1件の結合テストで確かめる。確かめられなければ、未確認のまま残るリスクを Build and Test の記録に引き継ぐ。 | New |
| R-02 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/code-generation/code-summary.md > 計画より縮めた結合テスト | Step 14〜16・19 を既存の AbstractBusinessDataReadIT・WriteIT の各1件に縮めた。抜けは正直に列挙されているが、桁の境界（DSL に桁が明示のときの 1 桁超の 400）、DB の丸めの動作、日時専用の SecretLeakIT・TraceExposureIT が無い。後者は行の値を扱う型の漏えい確認（Forbidden）に関わる。 | Build and Test で、少なくとも (a) 桁が明示の列の over-precision の境界の 400、(b) 読めない日時の値（Raw）が TRACE のログ・エラー応答に出ないことの確認、(c) D-P8 の DB の丸めの動作の記録、の3つを足すか、足さない理由を依頼者の判断として記録する。 | New |
| R-03 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/code-generation/code-summary.md > 未実行 | verify 全体（SpotBugs・OSV-Scanner・Gitleaks・全体のカバレッジの床）と E2E は未実行で、この確認も実行を伴わない読み取りだけである。build.gradle.kts・spotbugs-exclude.xml・境界テストを変更していないという記述は code-summary の自己申告で、manifest の書き込み一覧（build 設定や境界テストを含まない）とは矛盾しない。 | 統合の前に ./gradlew verify（cleanTest・cleanIntegrationTest 付き）と、team.md の規則に従い businessdata を触るため E2E を流して通す。 | New |
| R-04 | Minor | backend/src/main/java/cherry/mastersmith/businessdata/repository/ColumnValueCodec.java > fractionDigits(約 214〜232 行) | product 引数が使われなくなり互換のため残されている（code-summary に記載）。また ColumnValueCodec の古い定数（TIME・DATE_TIME・DATE_TIME_UTC・TIME_UTC）は format(BoundValue) で桁を固定しない書式として残り、読み取りの TemporalCells とは別の書式である。作成した主キーの応答の端数の出し方が読み取りの桁固定と食い違いうる。 | 次に手を入れる機会に未使用の引数を除く。作成した主キーの応答の端数が読み取りの桁ちょうどの形と一致するかを、Build and Test の書き込みの確認で一度見る。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 読み取りによる設計とコードの突き合わせ | 実行（テスト・ビルドは指示により未実行） | PostgreSQL の UTC 設定（TargetDataSourceConfig の connectionInitSql、読み取り・書き込みの両プールが同じ対応を引く）、結果の列情報の桁への固定（RowSqlExecutor の digitsOf・両方言の fractionDigits）、読めない日時セルだけ Raw で返す（readCell の catch は日時の型だけ Raw、他の型は Unreadable のまま行全体の失敗）、timetz の時差保持と比較値だけ UTC に直す（parseTimeTz・comparisonValue）、DSL 生成の dbType.precision（日時・時刻の分類だけ、製品の既定と違うときだけ。日付・interval は書かない）、桁の明示がないときは null で over-precision の 400 を出さない（ColumnValueCodec.fractionDigits）を、設計どおりと確認した。 |
| 行の値・接続情報の漏えい（Forbidden） | 問題なし | CellValue.Raw・Text は toString で値を伏せる。読めない値の例外は握り潰して例外クラス名だけを返し、メッセージを持たない。初期化の文は定数で利用者の入力・設定値を含まない。 |
| 境界テスト・SpotBugs 除外・build 設定 | 変更なしと見える | manifest の書き込み一覧にこれらは含まれない。SQL を組み立てる部品は変わらず RowSqlExecutor 内の既存メソッドに限られる。 |

### Summary

設計の BR3.x と NFR はコードで満たされており、漏えい・境界・build 設定を緩める変更も見当たらない。ブロックする指摘は無く、縮めた結合テストの抜けと読めない主キーの送り返しの確認を、Build and Test と統合前の verify・E2E で埋めるのが妥当である。
