# 技術の選択 — U5 navigation

## 出典

- `functional-spec.md`・`rules.md`（この単位の承認済みの機能設計。1節の部品と依存、7節のテストの観点、BR2.7・BR4.4・BR7.1）
- `requirements.md`（NFR6.1〜NFR6.4、制約 C4）
- `contract-summary.md`（C1・C3・C5・C9）
- `technology-stack.md`（コード知識ベース。Spring Boot 4.1.1・ArchUnit・jqwik・JaCoCo・Vitest・k6）
- この段の答えとまとめの確認（性質ベースのテストに `id` の性質を2つ足す）。
- 機能設計の再レビューの R-05。

## ID の対応表

全体の表は `security-requirements.md` の「ID の振り方」にある。この成果物に書くのは次のもの。

| 上流の枝番 | この単位での扱い | この単位の ID |
|---|---|---|
| NFR6.1 必須のテスト | 部分（N 階層のメニューと役割・権限のサーバー側。開閉・`aria-current`・キーボード・axe は U7） | NFR6.1・NFR6.5・NFR6.7（アイコンの一覧の誤りは `reliability-requirements.md` の NFR6.8） |
| NFR6.2 性質ベースのテスト | 部分（サーバー側の絞る関数。画面側の fast-check は U7） | NFR6.2 |
| NFR6.3 E2E | 当たらない（流れ I は U7 app-frame-ui） | — |
| NFR6.4 カバレッジの下限 | 当たる | NFR6.4・NFR6.6 |

## 選択

| 項目 | 選択 | 理由 |
|---|---|---|
| 言語・枠組み | Java（今の版）・Spring Boot 4.1.1・Spring Security（今のまま） | 既存のアプリの中の新しい機能 `navigation` |
| 表・移行 | 足さない | 読み取りだけ（BR6.2） |
| 構造の検査 | ArchUnit（今のまま） | 境界テスト（NFR6.5） |
| 性質ベースのテスト | jqwik（今のまま） | 絞る関数（NFR6.2） |
| 画面のテスト | Vitest（今のまま） | アイコンの一覧の一致（NFR6.7） |
| 負荷の試験 | k6（今の `perf/k6/scenarios.js` に場面を足す） | `performance-requirements.md` の NFR2.2 |
| 新しい依存 | 無い | NFR6.6 |

## 要件

| ID | 要件 | 測り方・確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR6.1 | 機能設計の 7節のテストの観点（`team.md` の「役割・権限の機能」と「N 階層のメニュー」のうちサーバー側の部分）をすべて書く。<br>・権限ごとの API の認可と分類の網羅: `security-requirements.md` の NFR1.2・NFR1.3。<br>・割り当ての変更の反映: 作業ロールの切り替え・テーブルの主権限の変更・割り当ての外しの直後の次の要求で、メニューの項目と置き場の 200／403 が切り替わる。変更の前に出したアクセストークンのままで確かめる。<br>・深さの上限: 5 段ちょうどの木が欠けずに返る。<br>・信頼できない入力: `security-requirements.md` の NFR1.7。<br>・空の理由: NOT_CONFIGURED・NOTHING_VISIBLE・null の3つ | 結合テスト `NavigationApiIT`（反映・深さ・空の理由）と、各要件の行のテスト | Code Generation（B7） |
| NFR6.2 | 絞る関数（`MenuFilter`）に jqwik の性質ベースのテストを当てる。性質は機能設計 BR2.7 の6つに、`id` の2つを足した8つ。<br>(a) 移れる項目の実効はすべて READ・FULL。<br>(b) 子の無いまとまりが無い。<br>(c) 残った項目の親子と並びが元と同じ。<br>(d) すべて NONE なら空。<br>(e) どれかのテーブルを NONE から READ に強めても、残る項目は減らない。<br>(f) 深さは元を超えない。<br>(g) `id` は応答の中で一意。<br>(h) 同じ木で権限が違っても、両方に残る項目の `id` は同じ（機能設計の再レビューの R-05）。<br>入力の木は深さ 5 段まで、テーブルと子の組み合わせ、同じテーブルを指す項目の重なり、DSL に無いテーブルを指す項目を含む。失敗時の乱数の種を記録する | 単体テスト `MenuFilterPropertyTest`（jqwik）。種は jqwik の報告に残る | Code Generation（B7） |
| NFR6.4 | `navigation.domain`・`navigation.service`・`navigation.web` は、パッケージごとのカバレッジの下限（行 80%・分岐 70%）を満たす。`access.domain`・`auth.domain`・`dsl`・`role` は読むだけで本体（`src/main`）を変えないため、`packagesJudgedByTotal` の作業（下限を満たして一覧から外す）は付かない。要件 NFR6.4 の `access.service` にも手を入れない | `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する（`team.md`・`project.md` の学び） | Code Generation（B7）、Build and Test |
| NFR6.5 | `navigation` のテストに `NavigationBoundaryArchitectureTest` を置き、次を書く。既存の境界テストは緩めない。<br>・navigation が依存してよい機能: `dsl.service`・`dsl.domain`、`role.service`・`role.domain`、`access.domain`、`auth.domain`、`common`。<br>・どの機能も navigation に依存しない。<br>・`dsl.parse`・`dsl.validate`・`role.repository` に依存しない（BR7.1） | `./gradlew verify` の構造の検査 | Code Generation（B7） |
| NFR6.6 | 新しい依存（Gradle・npm）を足さない。k6 の台本と悪い側の DSL の生成の手順は、今の `perf/` の形（Node の標準の部品だけ）で書く | `backend/gradle.lockfile` と `frontend/package-lock.json` に navigation の Bolt での差が無いこと（コード生成の計画で確かめる） | Code Generation（B7） |
| NFR6.7 | アイコンの一覧のファイル（`backend/src/main/resources/navigation/allowed-icons.txt`）と、画面の `app/registry` のアイコンの一覧が、集まりとして一致することを画面のテストで確かめる（BR4.4、機能設計 Q5 B）。ずれたら `./gradlew verify` が落ちる | 画面のテスト `frontend/src/app/registry/allowedNavIcons.test.ts`（Vitest。ファイルを BR4.1 と同じ読み方で読む）。テストの説明文は英語 | Code Generation（B7） |

## 上流との差（コード生成の計画で記録を確かめる）

- 置き場の問い合わせは `resolve` を1回呼ぶ（機能設計 BR5.2 の「`snapshotFor` を1回」との差。この段の Q1 A。`security-requirements.md` の上流との差）。
- 性質ベースのテストの性質を 6 つから 8 つにした（`id` の性質を足した。機能設計 BR2.7 との差）。

## ほかの単位への引き継ぎ

- **U4 role**: navigation は、メニューで `snapshotFor` を1回、置き場で `resolve` を1回呼ぶ。`resolve` は1つの対象を `{schema, table, null}` で受ける。`NavigationConnectionUsageIT` と `NavigationDslSwapIT` は、解決の口の読み取りの途中で止める待ち合わせの口（テストだけの差し替え）を要る。role の待ち合わせの口（`RoleBarrier`）で足りるか、navigation の側の替え物で包むかはコード生成の計画で決める。
- **U7 app-frame-ui**: メニューの API と置き場の問い合わせの p95 は 1 秒。画面の移動のたびにメニューを読む見積もりは毎秒 10 要求ほど（`scalability-requirements.md` の NFR2.1）。置き場の 400 は引数が無い・空のときだけで、名前の長さでは拒否しない。`allowedNavIcons.test.ts` は B7 で置くため、B9 で make-you-chic-ui の固定先を上げてアイコンが変わったら、一覧のファイルと `app/registry` の一覧の両方を直す。
