<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-24T00:04:43Z — U1 で3種類の DB をすべて読めるようにする計画にした; Bolt の計画は「最初の1種類（3種類は B3）」としていたが、後で承認された NFR 設計（読み手を U1 に2つ置く）と基盤の設計（NFR12.1、3種類を verify で毎回）に合わせた。型の分類と 30 秒の確かめは U3 に残す。
- 2026-09-24T00:04:43Z — 設定の型のポートと種類を文字列で受けることにした; 数・列挙で受けると不正な値で Spring の結び付けが起動を止め、BR1.3（不正でも起動を続ける）を守れないため。
- 2026-09-24T02:58:19Z — U2 の計画の前に、各設計の段の承認の場の決定（監査ログの Approve の文言）を洗い出した; U1 で決定 B を見落とした反省から。U2 に関わるのは決定 D（JSON Schema を許す設定は足さない）と、10MB への引き上げ。
- 2026-09-24T02:58:19Z — U2 の JSON Schema の複写を bootWar ではなく processResources の出力に対して行う計画にした; 結合テスト（クラスパスで起動）でも公開の道を確かめるため。WAR の中の置き場は基盤の設計と同じになる。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-24T00:04:43Z — パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた; team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。
- 2026-09-24T00:04:43Z — compose の app のサービスに対象DB の環境変数を足さず、.env.example に足すだけにした; app はすでに .env を env_file で読むため。基盤の設計（cicd-pipeline.md 3節）の書き方とは違う。
- 2026-09-24T02:15:27Z — U1 の TargetDbType.length を契約 C1 の int ではなく Long にした; MySQL の longtext の長さ 4294967295 が int に収まらないため。契約の文書は書き換えず、code-summary.md に差を記録した。
- 2026-09-24T02:15:27Z — 3つの JDBC ドライバー自身のログを OFF にした; MariaDB のドライバーが認証の失敗をユーザー名つきで WARN に出し、NFR4.4 に反したため。失敗は読み取りの口が原因の種類と SQLState だけで出す。
- 2026-09-24T04:36:27Z — U2 で深さ・別名・タグを SnakeYAML の LoaderOptions ではなく Parser を包む部品で数えた; 2.6 の上限は位置を持たない例外で止まり、TagInspector は !custom・!!str を通すため。LoaderOptions にも同じ上限を置いて二重に守る。
- 2026-09-24T04:36:27Z — AC2.2.3（同じテーブル・カラムの二重の定義）は意味の誤りではなく DUPLICATE_KEY になる; BR1.5 のとおり。受け入れ基準の文言とは違うため、承認の場で伝える。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-24T00:04:43Z — 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う; 外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。
- 2026-09-24T02:15:27Z — パッケージごとのカバレッジの下限は新しいパッケージだけに当てた; 実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。
- 2026-09-24T02:15:27Z — PostgreSQL の主キー・外部キーを pg_constraint から読むことにした; information_schema.table_constraints は SELECT だけの権限のアカウントに制約を返さず、読み取り専用のアカウントで成功させる基準（AC1.1.10）を満たせないため。
- 2026-09-24T04:36:27Z — U2 の Validation の値を数（number）と文字（text）の2つに分けた; YAML の値の型をそのまま保ち、後続の Intent が型を判定し直さずに済む代わりに、entities.md の value 1つの形とは違う。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-24T02:15:27Z — U3 で tinyint(1)・bit(1) を BOOLEAN と見分けるには、U1 の写しの DATA_TYPE と精度だけでは足りない（tinyint・精度 3 になる）。U3 の計画で COLUMN_TYPE を足すかを決める。

