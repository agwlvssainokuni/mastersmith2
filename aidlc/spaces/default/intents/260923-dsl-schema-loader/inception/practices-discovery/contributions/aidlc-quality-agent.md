**Collaborator:** aidlc-quality-agent

## Contribution

品質（テスト・カバレッジ・品質の関門）の観点からの独立レビュー。初稿（`team-practices.md`・`discovered-rules.md`・`evidence.md`）と、`aidlc/spaces/default/memory/team.md`・`project.md`、コード知識ベース（`aidlc/spaces/default/codekb/mastersmith2/code-quality-assessment.md`・`technology-stack.md`）、`backend/build.gradle.kts`・`build.gradle.kts`・`frontend/vitest.config.ts`・`frontend/playwright.config.ts`・`backend/src/test/resources/junit-platform.properties`・`.github/workflows/ci.yml` を読んだ。`./gradlew`・`npm`・`docker` は実行しておらず、件数・時間の実測値は無い。

### 1. 既存の基準で足りている点（変えなくてよい）

- **Methodology / Ordering**: test-after と「層ごとに実装→その層のテスト→すべて通ってから次の層」は、今回の Intent（DSL のスキーマ・読み込み・検証・生成・プレビュー→適用）にもそのまま当てはまる。変える理由は見当たらない。
- **カバレッジの道具と下限**: バックエンドは JaCoCo（`test.exec` と `integrationTest.exec` を合わせて `jacocoTestCoverageVerification` で行 80%・分岐 70%）、画面は `@vitest/coverage-v8` の `thresholds`（行 80・分岐 70）で、`team.md` と一致している。除外はバックエンドが `MastersmithApplication*`・`*Properties`、画面が `src/main.tsx`・`*.d.ts`・テストのファイルだけで、`team.md` の「除外を後から増やさない」に沿っている。
- **単体と結合の分け方**: `*Test` は `test`、`*IT` は `integrationTest` と名前で分かれ、`verify` の段 5・段 6 に入っている。jqwik の乱数の種は `exceptionFormat = FULL` と `jqwik.database` で残る。ArchUnit の層の決まりもある。今回の新しいコードもこの型に乗せればよい。
- **E2E**: Playwright を `./gradlew e2eTest` で `verify` と CI の外に置く決定は、`project.md`（意図して CI の外に置く検査はその旨と代わりの実行の場を明記）と整合している。

### 2. 初稿に足すべき点（品質の観点で依頼者に確かめたいもの）

面談の質問の候補として、初稿の Q-A〜Q-N に次を足す（番号は仮）。

| # | 節 | 点 | 理由・根拠 |
|---|---|---|---|
| Q-Q1 | Testing Posture | **対象DB のテストのデータの巻き戻し方**。初稿の「テストごとにデータ（表・列の定義）を用意して巻き戻し」は、トランザクションの巻き戻しでは実現できない。MySQL・MariaDB は DDL（`CREATE TABLE` など）で暗黙に確定し、巻き戻せない。案: テストごとに一意の名前のスキーマ（MySQL・MariaDB はデータベース、PostgreSQL はスキーマ）を作って表を定義し、終わったら消す。コンテナはクラスをまたいで共有してよいが、スキーマは共有しない。 | `team.md` の「テストごとにデータを用意して巻き戻し、実行順に依存させない」を対象DB で守るための具体の手段が要る |
| Q-Q2 | Testing Posture | **対象DB のテストの実行記録をカバレッジに含めること**。対象DB のテストを別のタスク（初稿の案 (b)）にする場合、その `.exec` を `coverageExecutionData`（今は `test.exec`・`integrationTest.exec` だけ）と `jacocoTestReport` に加える。加えないと、DB ごとの分岐を確かめたテストがカバレッジに数えられず、下限に届かない、あるいは除外で逃げる誘因になる。 | `backend/build.gradle.kts` の `coverageExecutionData`。`team.md` の除外の禁止 |
| Q-Q3 | Testing Posture | **カバレッジの下限を新しいパッケージ単位でも当てるか**。今の JaCoCo の規則は全体（BUNDLE）の合計で1つだけなので、既存の高いカバレッジに埋もれて、新しい DSL・対象DB のコードが分岐 70% を下回っても関門を通りうる。案: (a) 今のまま全体だけ、(b) 今回足すパッケージ（例: `dsl`・`targetdb`）に PACKAGE 単位の同じ下限の規則を足す。画面も同様に、新しい機能のディレクトリに `thresholds` の glob 指定を足すか。 | 下限を上げる方向の追加で、緩める変更ではない。`backend/build.gradle.kts` の `violationRules` は `rule` が1つ |
| Q-Q4 | Testing Posture | **対象DB の版の範囲とテストの組み合わせ**。MySQL・MariaDB・PostgreSQL の「どの版」を対応とするか（例: MySQL 8.0／8.4、MariaDB 10.11／11.x、PostgreSQL 16／17）は要件で決め、テストで確かめる版はその範囲の代表（各1版か、最古と最新の2版か）を決める。初稿の Q-A（どの DB をどこで）とあわせて聞く。 | 版ごとにメタデータの返し方（型名・既定値の表し方・識別子の大文字小文字）が違いうる。版を決めないとテストの合否の基準が決まらない |
| Q-Q5 | Testing Posture | **コンテナが無いときに黙って飛ばさないこと**。Testcontainers の `@Testcontainers(disabledWithoutDocker = true)` や、`Assumptions` による飛ばしを使わない。コンテナの実行環境が無いときは、入れ方を示して失敗させる（初稿の Way of Working の（要確認）の案と同じ考え方）。あわせて、colima で Testcontainers を動かすための設定（`DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` など）を README の前提に書く。 | 飛ばしを許すと、ローカルでは緑で CI だけで落ちる／その逆が起き、「CI と同じ検査をローカルで1コマンド」の関門が崩れる |
| Q-Q6 | Testing Posture | **対象DB のテストのタイムアウトと待ち方**。コンテナの起動は固定の `sleep` でなく起動の完了の合図（ログや接続の確認）で待つ（初稿に記載済み）。加えて、対象DB のテストのタスクにタイムアウトを置くか（今の `integrationTest` には無い）。イメージの取得の失敗・遅延で `verify` が止まり続けないようにするため。 | `backend/build.gradle.kts` の `integrationTest` にタイムアウトの指定が無い（`evidence.md` の E4） |
| Q-Q7 | Testing Posture | **既定 DSL の生成の結果を、期待の DSL のファイル（ゴールデンファイル）と比べるテストを置くか**。DB ごとに、テストのコードから作ったスキーマから生成した DSL を、リポジトリに置いた期待の YAML と比べる。差が出たら内容を見て期待のファイルを更新する（自動で上書きしない）。置き場とヘッダーは初稿の Q-J と一緒に決める。 | 生成の結果は項目が多く、個別の検証だけでは抜けが出やすい。DB・版ごとの違いが差分として見える |
| Q-Q8 | Testing Posture | **性質ベースのテストの当て先を具体に書く**。初稿の3つ（検証、型の対応、読み込みと書き出しの往復）に加えて、(1) 生成した既定 DSL は必ず JSON Schema の検証に通る、(2) 検証に通った DSL の読み込みは例外で落ちず結果を返す、(3) 型の対応は、対応表にある型のすべてに結果を返し、対応表に無い型は決めた扱い（エラーか既定の型か）になる、を候補とする。生成器（ランダムな DSL・メタデータ）の作り方は設計の段で決める。 | jqwik・fast-check は既に入っており、道具の追加は要らない |
| Q-Q9 | Testing Posture | **Java と画面の両方で DSL を検証する場合の一致の確かめ方**。画面でも JSON Schema の検証をするなら、スキーマのファイルは1つを正とし、同じ見本の DSL（正しいもの・誤ったもの）の組を両方のテストで検証して、合否が一致することを確かめる。画面では検証しない（サーバーの結果を表示するだけ）なら不要。 | 2つの検証の部品の解釈の違い（`format`・`additionalProperties` の既定など）は実際に起きやすい。サーバー側の検証を正とする（画面の検証はサーバー側の代わりにしない） |
| Q-Q10 | Testing Posture | **プレビューと適用の必須テスト**（認証・認可・監査の必須テストの一覧と同じ扱いにするか）。候補: (1) プレビューは状態を変えない（プレビューの前後で適用済みの内容・内部DB・対象DB が同じ）、(2) 同じ内容の適用を2回しても結果が同じ、(3) 適用が途中で失敗したとき、一部だけ反映された状態を残さない、(4) プレビューの後に DSL が変わった場合の適用の扱い（拒否するか）、(5) 全上書きリセットの前後の内容。(3)(4) の動作そのものは要件で決める（★の扱い）。 | 2段階の反映は、途中の状態・古いプレビューの適用で不具合が出やすい |
| Q-Q11 | Testing Posture | **投入された DSL を信頼できない入力として扱うテスト**。初稿の Q-L（上限の基準）を採るなら、境界のテストを必須にする: 大きさの上限ちょうど／超え（要求の本文の上限 1MB との関係も含む）、入れ子の深さの上限、別名（アンカー）の展開の爆発（billion laughs）、YAML のタグによる任意の型の作成の拒否、文字コードの誤り。応答が Problem Details で、内部の例外メッセージを出さないことも確かめる。 | `code-quality-assessment.md` の C-7（1MB）。`team.md` のエラー応答の決まり |
| Q-Q12 | Testing Posture | **対象DB を変更しないことのテスト**。`discovered-rules.md` の Forbidden の候補（対象DB を変更しない）を採る場合、既定 DSL の生成とプレビューの前後で対象DB のスキーマ（表・列の定義）が変わらないことをテストで確かめる。適用が対象DB に書き込む設計になるなら、この候補とテストは成り立たず、書き込みの範囲のテストに置き換える（初稿の「未確定の点」と同じ）。 | 規則を置くなら、それを破ったら落ちるテストが要る |
| Q-Q13 | Testing Posture | **2つ目の `DataSource` を足した後の回帰の確かめ**。初稿の Code Style の（要確認）（既存の JPA・Flyway・ヘルスチェック・監査・名前の指定の無い `@Transactional` が内部DB を指し続ける）は、既存の結合テスト（46 クラス）がすべて通ることに加え、「既定の `DataSource`・`PlatformTransactionManager` が内部DB を指す」ことを直接確かめる結合テストを1件置く。対象DB の Bean を足す Bolt の中で、足す前と後の結合テストの件数を実測で比べる（`project.md` の `:backend:cleanTest :backend:cleanIntegrationTest`）。 | `code-quality-assessment.md` の C-2。既存のテストが減った・飛んだことに気づけるようにする |
| Q-Q14 | Testing Posture | **画面の新しい部品（コードエディター・差分の表示）を足す場合のテストのしかた**。CodeMirror・Monaco などは jsdom で動かしにくく、画面のテストが書けずにカバレッジの下限に届かない、あるいは部品を除外したくなる恐れがある。案: 部品を薄い包み（自前の部品）で囲み、包みの外の振る舞いを jsdom で確かめ、部品そのものの動きは E2E で確かめる。部品を足さず make-you-chic-ui の `Textarea`・`Table` で表示するなら不要。初稿の Q-K と一緒に聞く。 | 除外を増やして下限を下げないため（`team.md`） |

### 3. 初稿の（要確認）への品質の観点からの意見

- **Q-A（どの DB をどこで）**: 案 (c)「`verify` では1種類だけ、残りは CI だけ」は、`team.md` の Way of Working（CI と同じ検査をローカルで1コマンドで実行し、すべて通ってから統合する）と食い違う。ローカルの関門を通っても CI で落ちる形になり、カバレッジの値もローカルと CI で変わる。品質の観点では (a) か (b) を勧める。(b) の場合も `verify` から呼び、Q-Q2 のとおりカバレッジに含める。
- **Q-C（同時に1つだけ）**: 同時に起動するコンテナを1つにするなら、Gradle のテストの並列（`maxParallelForks`）と JUnit の並列を有効にしない前提も一緒に書く（今は `junit-platform.properties` に並列の設定が無く、既定は直列）。
- **Q-D（CI の時間）**: 分けるかどうかの判断のため、Build and Test で対象DB のテストのタスクの時間を実測して記録することを基準に含める（初稿の「未確定の点・懸念」に記載済みの方針と同じ）。
- **Q-E（最初の Bolt の最小の通し）**: 賛成。加えて、その Bolt で Q-Q1（スキーマの作り方と消し方）・Q-Q2（カバレッジへの取り込み）・Q-Q5（飛ばさない）までを入れておくと、後の Bolt がテストの仕組みを作り直さずに済む。
- **Q-F（E2E の2本目）**: E2E を足すなら、対象DB は1種類だけ（コンテナ1つ）でよい。DB ごとの違いは結合テストで確かめ、E2E では画面の流れ（生成→検証→プレビュー→適用）だけを見る。E2E は `verify` と CI の外のままにし、実行の場（Build and Test で手元で実行）を明記する。
- **Q-G（接続情報の漏えい）**: 必須のテストに加えることを勧める。確かめる出口は、ログ・監査ログ・トレースの属性・エラー応答・生成した DSL・プレビューの応答・設定の型の `toString`（`TraceAspect` の TRACE の出力、C-9・TD-4）。既存の「秘密情報の漏えい」の必須テストの対象に「対象DB のパスワード・資格情報を含む JDBC の URL」を足す形が、既存の書き方に揃う。

### 4. テストの件数と構成の目安（Test Strategy: Standard）

- Standard は部品ごとに 5〜8 件、単体と結合が中心。今回は、DSL の検証・型の対応・生成の組み立てなど純粋な処理は単体テスト（性質ベースを含む）で厚くし、対象DB のテストは DB ごとの違い（メタデータの読み取り）と接続の失敗に絞る。対象DB のテストで単体テストの代わりをしない（コンテナのテストは遅いため、DB を使わずに確かめられる分岐は単体で確かめる）。
- 対象DB のメタデータの読み取りは、JDBC の `DatabaseMetaData` を読む部分と、読んだ結果から DSL を組み立てる部分を分けると、組み立ての分岐を単体テストで確かめられ、コンテナのテストの数と時間を抑えられる（設計の段で決める）。

## Positions

- AGREE: Methodology（test-after）と Ordering は既存の基準のまま変えない — 今回の Intent の層の構成にもそのまま当てはまる。
- AGREE: 内部DB は H2、Testcontainers は対象DB だけに使う読み方を今回の3種類の対象DB に当てる — `team.md` の学び（2026-09-22）と一致する。
- AGREE: コンテナの実行環境が無いときは黙って飛ばさず失敗させる（Way of Working の（要確認）） — 飛ばしを許すとローカルと CI の関門が食い違う（Q-Q5 で Testcontainers 側の飛ばしの指定も禁じる形に具体化した）。
- AGREE: 対象DB のイメージの版を固定し、1か所にまとめる — 版が変わると生成の結果（ゴールデンファイル）とテストの合否が揺れる。
- AGREE: 性質ベースのテストを DSL の検証・型の対応・往復に当てる — 道具（jqwik・fast-check）は既にあり、Q-Q8 で候補を足した。
- AGREE: 管理画面の API に 401／403／200 のテストを書く — 既存の認可の必須テストと同じ扱い。
- OBJECT: Testing Posture の「対象DB のテストでも、テストごとにデータ（表・列の定義）を用意して巻き戻し」 — MySQL・MariaDB の DDL は暗黙に確定して巻き戻せないため、「テストごとに一意のスキーマを作って消す」と手段を書き直すべき（Q-Q1）。
- OBJECT: Q-A の案 (c)（`verify` では1種類、残りは CI だけ） — `team.md` の「CI と同じ検査をローカルで1コマンド」と食い違い、カバレッジの値もローカルと CI で変わるため、選択肢に残すなら食い違いを明記して聞くべき。
- OBJECT: Q-A の各案の説明に、対象DB のテストの実行記録をカバレッジの計算に含めることが書かれていない — 別のタスクにすると今の `coverageExecutionData` に入らず、分岐の下限の見込み（初稿の推測）が崩れる（Q-Q2）。
