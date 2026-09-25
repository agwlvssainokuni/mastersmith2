# Code Generation の計画（260925-storage-memory-fixes）

## 概要

- Intent: 260925-storage-memory-fixes（scope `bugfix`、深さ Minimal、Test Strategy Minimal、Brownfield）。Unit の分割は無い（ゼロ Unit）ため、この計画は段の全体（`construction/code-generation/`）に1つだけ置く。
- 扱う要件: `aidlc/spaces/default/intents/260925-storage-memory-fixes/inception/requirements-analysis/requirements.md` の FR1〜FR5・NFR1〜NFR6。要件の確認で受け入れた指摘 R-01（詰め直しの時間の上限・打ち切りの条件）と R-02（失敗の時の動きと戻し方）も扱う。
- この段での依頼者の決定（`code-generation-questions.md`）:
  - Q1: A。計画を書く前にメモリの内訳を測り、計画の承認の場でメモリの目標を決める。
  - Q2: A。詰め直しの操作の入口は JMX だけにする。管理者向けの HTTP API は作らない。
  - Q3: X。「JMX ならば HikariCP の標準の機能だけで実現できる。それならアプリのログと監査ログは差し込めないので、出さなくてよい」。
- 計画の前の測定と前提の確かめの記録: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/baseline-measurement.md`（以下「測定の記録」）。
- 計画の中心は、アプリのコードを足すことではなく、(a) HikariCP の JMX と一時停止を有効にする設定、(b) HikariCP の標準の操作で詰め直されることを確かめる再現の結合テスト、(c) コンテナの中の JVM に JMX でつなぐ手順と道具（外に公開しない）、(d) README の手順（再開を必ず行う・時間の目安・再開し忘れた時の影響と戻し方）である。あわせて FR2（メモリ）・FR3（`AccessTokenApiIT`）・FR4（`perf/dsl-timing.sh` の説明）を扱う。

## 要件との差（承認済みの要件は書き換えない）

依頼者の決定 Q2・Q3 により、承認済みの要件と次の差がある。要件の文書は書き換えず、ここに明記する（project.md の決まり: 確定済みの設計と違う決定は、その段の成果物に差を書き、README などの手順を決定に合わせて直す）。

| 要件 | 承認済みの要件 | この計画での扱い | 根拠 |
|---|---|---|---|
| FR1.1 | 管理者向けの API に、すべての接続を閉じて詰め直す操作を足す | アプリのコードは足さない。HikariCP の標準の MBean（`com.zaxxer.hikari:type=Pool (mastersmith-db)`）の操作（一時停止 `suspendPool` → 接続の破棄 `softEvictConnections` → 0 本になるのを待つ → 再開 `resumePool`）を JMX で呼ぶ。最後の接続が閉じた時に、接続先の `DEFRAG_ALWAYS=TRUE` で H2 が詰め直す | Q2: A・Q3。測定の記録 3.4 節で、実際のアプリのコンテナで 229.1MiB → 15.3MiB・448.5MiB → 15.3MiB になることを確かめた |
| FR1.3 | 入口は `/api/admin/**`。未認証 401・管理者でない 403・管理者 200 をサーバー側のテストで確かめる | HTTP の入口は作らない。401・403・200 のテストも作らない。操作できるのは、この PC で Docker を使える人だけ（JMX は外に公開せず、同じ利用者の番号で JVM に attach する） | Q2: A |
| FR1.5 | 詰め直しの間の要求は待たせ、接続の待ちの上限（5 秒）を超えたら今と同じ失敗 | HikariCP の一時停止の間の要求は、再開まで上限なしで待たされ、再開の後に成功する（測定の記録 3.4 節の J-A で 7.6 秒、J-C で 53.5 秒待って 200）。5 秒の上限は効かない。扱いは承認の場で決める（D3） | HikariCP 7.0.2 の作り（一時停止の間の借りる要求は上限なしで待つ） |
| FR1.6 | 詰め直しの操作を監査ログに残す（誰が・いつ・結果・前と後の大きさ） | 監査ログに残さない。アプリのログにも出さない。前と後の大きさと時間は、道具（D4）の出力で操作した人が見る | Q3 |
| FR1.7 | 詰め直しの最中のほかの監査の記録の失敗は、今の監査の決まりに従う | 変えない。ただし一時停止の間は失敗ではなく待ちになる（ログインの監査の記録も再開まで待つ） | Q3・FR1.5 の差 |
| FR1.10 | 詰め直しの操作を同時に複数受け付けない。重い DSL の操作との重なりは設計で決める | アプリでは受け付けを制御できない（標準の MBean のため）。道具（D4）が同時に1つだけ動くようにする。重い DSL の操作は一時停止の間は待ち、借りている接続があれば道具は 0 本になるまで待つ（上限は D5） | Q2・Q3 |
| NFR1 | 詰め直しの間もプロセスは止めない。かかる時間を測り README に書く | 変えない。一時停止が約 30 秒を超えるとコンテナの健全性が unhealthy になること（測定の記録 3.4 節の J-C）も README に書く | 測定の記録 3.4 節 |
| NFR3 | 詰め直しの操作は管理者だけ。サーバー側で検査する | サーバー側の管理者の検査は無い。代わりに、JMX を外に公開せず、この PC の Docker を使える人だけが操作できる | Q2: A |

## Testing Contract

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "bugfix",
  "test_strategy": "minimal",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストをどこで実行するかは、Build and Test で `./gradlew verify` の時間と colima の VM のメモリを実測してから決める。決まるまでは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- 画面からの一連の操作を確かめるテスト（E2E）は、代表的な流れ1〜2本に絞って入れる（まずは「ログイン → 管理画面に入れるか → ログアウト」）。道具は設計のステージで決める。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。不安定なテストは放置せず、原因を直すまで統合しない。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、管理者フラグなし（403）、管理者（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22)"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25)"
    }
  ],
  "obligations": {
    "strategy": "minimal",
    "strategy_volume": [
      "One verifiable test per requirement at the narrowest effective level.",
      "At least one happy-path unit test per component.",
      "Unit tests are the default; a bugfix/security scope floor may require an integration or E2E regression when that is the narrowest level that reproduces the defect."
    ],
    "scope_floor": [
      "Include a targeted regression for the bug or vulnerability.",
      "Keep the existing test suite green."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "test-after",
    "runner_step": "Verify the existing test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Verify the existing test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - implement.",
      "Data model / database behavior - write and run its tests after implementation.",
      "Repository / data access - implement.",
      "Repository / data access - write and run its tests after implementation.",
      "Business logic - implement.",
      "Business logic - write and run its tests after implementation.",
      "API / endpoint - implement.",
      "API / endpoint - write and run its tests after implementation.",
      "Frontend behavior - implement.",
      "Frontend behavior - write and run its tests after implementation.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:ca38afbc6a9ad2e0d83880b692894707681858e8c386e946df7b7e626f954ede",
  "contract_sha256": "sha256:9d04ca47e36e783b91a447ca74799e2a01e59c685384107037d450c6f57668fd"
}
```

- 上の Testing Contract は `aidlc engine testing-posture render` の出力をそのまま貼ったものである。methodology は `test-after` で、層ごとに「実装 → その層のテストを書いて実行 → すべて通ってから次の層」の順にする（下の「手順」）。
- この計画では、アプリの Java のコードを足さない（D2 で M2・M3 を選んだときだけ、既存のパッケージのコードを変える）。そのため、`plan_profile.steps` のうち、リポジトリ・業務処理・API・画面の層は「該当なし」とし、その理由を各 Step に書く。

## 計画の前の測定と前提の確かめ（要約）

詳しくは測定の記録を見る。

- メモリ（FR2.1）: 前の Intent と同じ `dslMixed` の条件で、`anon` 1,893.5MiB（上限 2g の 92.5%。前の Intent の 1,894MiB を再現）、`memory.events` の `max` 1,535 回、`OOMKilled` false。内訳はヒープ 1,536MiB（最大まで広がった）とヒープ以外 318MiB（NMT）と NMT の外 約 39MiB。止まっている間の生きているものは 84MiB だけで、93% はアプリが持ち続けるデータではなく JVM の大きさの設定（最大ヒープ 75%）による、と読める。最大ヒープを 50% にすると `anon` 1,412.9MiB（69.0%）・`max` 0、`-Xmx768m` では 1,143.3MiB（55.8%）・`max` 0（完全な GC が 120 秒で 20 回）。k6 の失敗はどれも 0 件。
- 詰め直し（FR1）: 要件の前提（H2 は接続が1本でも開いている間は詰め直さない。すべて閉じた時に `DEFRAG_ALWAYS=TRUE` で詰め直す）は正しかった。HikariCP の標準の操作だけで、実際のアプリのコンテナで詰め直せた。一時停止の間の要求は上限なしで待つ。一時停止が約 30 秒を超えると健全性が unhealthy。
- `AccessTokenApiIT`（FR3）: colima が Testcontainers のコンテナの番号を IPv4 の全アドレスで転送している番号を、Java の HTTP のサーバー（IPv6 の全アドレス）が重ねて取れること、その時に `localhost` への要求が `HTTP/1.1 header parser received no bytes` になること（1回目の失敗と同じ文言）を再現した。`-Djava.net.preferIPv4Stack=true` では重ねて取れなかった。実際の1回目の失敗がこれで起きたかは確かめられない。
- テストの実行の道具: `./gradlew :backend:test --tests 'cherry.mastersmith.config.H2DefragOnCloseTest' --rerun`（3 件成功）と `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.config.DataSourcePoolIT' --rerun`（3 件成功）が動くことを確かめた。

## 承認の場で決める点

各点に選択肢と、勧める案を書く。依頼者は計画の承認の前に選んだ（`code-generation-questions.md` の Q4）。各見出しの直後に決定を書いた。選んだ結果に合わせて、下の「手順」の条件つきの Step を行うか行わないかが決まる。

### D1 メモリの数値の目標（FR2.2）

- **決定（依頼者、この段の質問 Q4）**: T1（`anon` の最大が 1,536MiB 以下、かつ `memory.events` の `max` が 0）

判定の条件は、どの案も前の Intent と同じ `dslMixed`（測定の記録 1 節）で、`checks` がすべて成功し、`OOMKilled` が false であることを含む。

- T1（勧める）: `anon` の最大が上限 2g の 75%（1,536MiB）以下、かつ `memory.events` の `max` が 0。根拠: 最大ヒープ 50% で 1,412.9MiB（69.0%）・`max` 0。余裕は約 123MiB。
- T2: `anon` の最大が 60%（1,229MiB）以下、かつ `max` が 0。根拠: `-Xmx768m` で 1,143.3MiB（55.8%）・`max` 0。ただし完全な GC が多く、重い操作の最中の生きている量（最大 454MiB）に対して余裕が小さい。M2・M3 が要る。
- T3: `anon` の数値の目標を置かず、`max` が 0 かつ `OOMKilled` が false。

### D2 メモリを減らす手段（FR2.3）

- **決定（依頼者、この段の質問 Q4）**: M1 だけ（`Dockerfile` の `MaxRAMPercentage` を 50.0 にする）。条件つきの Step 7・Step 8 は行わない

- M1 だけ（勧める。T1・T3 のとき）: `Dockerfile` の `-XX:MaxRAMPercentage=75.0` を 50.0 にする。設定だけの変更で、`anon` が約 480MiB 下がる（実測）。
- M1＋M2: M1 に加えて、`DslContent` の作る時と `yamlBytes()` のたびの本文の複写（10MB の `clone()`）を減らす。1回の操作あたり 10〜30MiB 程度の一時的な確保の減（見積もり。仮説）で、目標への効き目は小さい。
- M1＋M2＋M3（T2 のとき）: さらに投入の処理で読み込みの途中の形（節の木・Jackson の木・`PositionMap`）を早く手放す。効き目は未検証で、コード生成の中で 10MB の投入の最中のヒープの中身（`GC.class_histogram -all`）を測ってから手を入れる。DSL の信頼できない入力の決まりは弱めない。
- M1 の割合の値は、T2 を選んだ時は 37.5（最大ヒープ 768MiB 相当）とする。

### D3 一時停止の間の要求の扱い（FR1.5 との差）

- **決定（依頼者、この段の質問 Q4）**: W1（再開まで待たせる。道具が一時停止の長さに上限を置き、必ず再開する）

- W1（勧める）: HikariCP の既定のまま、一時停止の間の要求は再開まで待たせる。道具（D4）が一時停止の長さに上限を置き（D5）、必ず再開する。要求は失敗しない。
- W2: HikariCP の標準のシステムプロパティ `com.zaxxer.hikari.throwIfSuspended=true` を JVM に渡し、一時停止の間の要求はすぐ失敗させる（今の接続の待ちの失敗と同じ 500 になる見込み。未検証）。依頼者の回答 F2（待たせる）と違う。
- W3: アプリの側に門を作り、待ちを 5 秒に限る。アプリのコードが要り、Q3 と違うため勧めない。

### D4 JMX の呼び方（道具）

- **決定（依頼者、この段の質問 Q4）**: J1（`docker/hikari-pool.sh` と `docker/jmx/HikariPoolControl.java`）

- J1（勧める）: リポジトリに小さな道具を置く。`docker/hikari-pool.sh`（入口）と `docker/jmx/HikariPoolControl.java`（依存なしの Java の1ファイル）。同じ版の JDK のイメージ（`eclipse-temurin:25.0.4_7-jdk-noble`、`perf/README.md` の NMT の手順で既に使っている）の一時のコンテナを、アプリのコンテナと PID とネットワークの名前空間を共有して `-u 10001:10001` で動かし、JVM（PID 1）に attach して、JVM の中だけの JMX の接続で HikariCP の Pool の MBean の標準の操作だけを呼ぶ。JMX は外に公開しない。副コマンドは `status`（接続の本数とファイルの大きさ）・`compact`（一時停止 → 破棄 → 0 本を待つ → ファイルが落ち着くのを待つ → 再開。どこで失敗・中断しても必ず再開を試みる）・`resume`（戻すための再開だけ）。測定の記録 3.4 節の確かめと同じ形。
- J2: 道具は置かず、README に `docker run … jshell` などの手で打つ手順だけを書く。再開の書き忘れ・打ち間違いが起きやすい。
- J3: JMX の遠隔の接続（`com.sun.management.jmxremote.*`）を有効にし、`127.0.0.1` だけに公開して jconsole などでつなぐ。JVM の引数と compose の公開の番号・認証の設定が要り、公開の範囲が広がるため勧めない。

### D5 詰め直しの手順の時間の上限と打ち切り（R-01）

- **決定（依頼者、この段の質問 Q4）**: A（0 本の待ち 10 秒・落ち着くのを待つ 30 秒・全体 45 秒。引数で変えられる）

- A（勧める）: 道具の `compact` は、借りている接続が 0 本になるまでの待ちを 10 秒で打ち切る（打ち切った時は破棄の済んだ接続だけが閉じ、DB は開いたまま。すぐ再開し、「詰め直していない」と表示して失敗で終わる）。ファイルが落ち着くまでの待ちは 30 秒を上限とする。一時停止から再開までの全体の上限は 45 秒とし、超えそうなら再開を優先する。
- B: 0 本の待ちを 30 秒、全体を 60 秒にする（重い操作の最中でも打ち切りにくいが、健全性が unhealthy になりうる）。
- 上限の値は道具の引数で変えられるようにする。測定の記録 3.4 節では、借りている接続が 0 本の時、破棄から約 0.7 秒以内に詰め直しが終わった。

### D6 詰め直しの直後の大きさの余裕（FR1.4）

- **決定（依頼者、この段の質問 Q4）**: G1（250MiB）

- G1（勧める）: 250MiB（本文の論理的な最大 約 210MB に約 40MiB の余裕。ほかの表・索引・監査ログの分を含める）。
- G2: 詰め直しの直前の本文の合計に 32MiB を足した値（本文の合計を数える手順が要る）。
- G3: 230MiB（約 10% の余裕）。
- 測定の記録 3.2 節では、乱数の本文 21 件で 210.4MiB、3.4 節の文字の本文では 15.3MiB だった。Build and Test の判定（FR5）は、圧縮の効かない悪い側の条件でも満たすかを見る。

### D7 `AccessTokenApiIT` の扱い（FR3.2・FR3.3）

- **決定（依頼者、この段の質問 Q4）**: 直す（テストの JVM に `-Djava.net.preferIPv4Stack=true`。再現の手順と単体テストを同じコミットに入れる）

- 直す（勧める）: 測定の記録 4 節で、同じ例外の文言を仕組みごと再現できたことを「原因が確かめられた」とみなす。テストの JVM に `-Djava.net.preferIPv4Stack=true` を渡し（`backend/build.gradle.kts` のすべての `Test` のタスク）、テストのアプリが colima の転送と同じ番号を重ねて取れないようにする。再現の手順と、重ねて取れないことを確かめる単体テストを同じコミットに入れる。
- 直さない: 実際の1回目の失敗がこれで起きたかは確かめられないため、調べた結果だけを記録し、要件 FR3.3 のとおり「不安定と確かめられていない」扱いで統合する。
- 別の直し方: テストの `HttpTestClient` の送り先を `[::1]` にする（Linux の CI での IPv6 の有無に左右されうる）、colima の VM の一時的な番号の範囲を変える（リポジトリの外の設定で、ほかの PC に効かない）。

### D8 JMX と一時停止を有効にする範囲

- **決定（依頼者、この段の質問 Q4）**: 既定で有効（テストの既定では MBean の登録を無効にする）

- 既定で有効（勧める）: `application.yaml` の `spring.datasource.hikari` に `register-mbeans: true` と `allow-pool-suspension: true` を置く。配備・使い捨ての環境の両方で使える。JMX は外に公開されない（遠隔の接続を有効にしない）。一時停止を許すと、接続を借りるたびに小さな数を数える処理が1つ増える（HikariCP の作り。影響は小さい見込み）。
- 環境変数で切り替え、既定は無効: 使う時だけ `.env` で有効にし、コンテナを作り直す。詰め直しのたびにアプリを作り直すことになり、「止めずに詰め直す」目的と合わない。
- テストの既定では MBean の登録を無効にする（同じ名前のプールを持つ Spring の文脈が同じ JVM に複数あると、HikariCP が「JMX name … is already registered」を ERROR で出すため）。再現の結合テストだけが、重ならないプールの名前で有効にする。

### D9 コミットの分け方と統合の方法

- **決定（依頼者、この段の質問 Q4）**: fast-forward（短命のブランチ `fix/260925-storage-memory-fixes` のコミットを分けたまま `develop` へ fast-forward で進める。team.md の squash とは違う依頼者の決定）

- コミットは、コード生成の後に依頼者の承認を得て分けて行う（project.md の Change Control）。案は下の「コミットの分け方」。
- 統合（勧める）: 短命のブランチ `fix/260925-storage-memory-fixes` を `develop` から作り、`develop` へ squash マージで戻す（team.md）。今回はサブモジュールの固定先を変えないため、前回の fast-forward の理由（サブモジュールの専用のコミットを残す）は当たらない。
- 代わり: 前回と同じ fast-forward（コミットを分けたまま `develop` に残る）。

## 手順

Testing Contract の `plan_profile.steps` の順に並べ、該当しない層はその理由を書く。条件つきの Step は、承認の場の決定（D1〜D9）で行うかが決まる。各 Step の終わりに、その Step のテストを書いて実行し、すべて通ってから次へ進む。

### Step 1: 本番の設定の骨組み（HikariCP の JMX と一時停止）

- [x] `backend/src/main/resources/application.yaml` の `spring.datasource.hikari` に `register-mbeans: true` と `allow-pool-suspension: true` を足す（D8）。コメントで、JMX を外に公開しないこと、詰め直しの手順（README の節）、`DEFRAG_ALWAYS=TRUE` が要ることを書く。プールの名前 `mastersmith-db`（MBean の名前に入る）は変えない。
- [ ] D3 が W2 の時だけ: `Dockerfile` の既定の JVM の引数に `-Dcom.zaxxer.hikari.throwIfSuspended=true` を足す（Step 13 と同じコミット）。
- 要件: FR1.1、FR1.2（自動では行わない。設定を足すだけで、何もしなければ今と同じ動き）、FR1.8（表の形を変えない）。

### Step 2: テストの実行の道具の確認と、この変更に絞ったコマンドの記録

- [x] 既存のテストの道具（JUnit 5・Spring Boot Test・AssertJ・Awaitility 4.3.0・組み込みの H2）で足りることを確かめる。新しい依存は足さない。
- [x] 計画の前に確かめたコマンド（`./gradlew :backend:test --tests 'cherry.mastersmith.config.H2DefragOnCloseTest' --rerun`、`./gradlew :backend:integrationTest --tests 'cherry.mastersmith.config.DataSourcePoolIT' --rerun`）を、`unit-test-instructions.md` のこの変更のテストのクラスに絞ったコマンドに置き換えて記録する（colima の PC では README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡す）。
- [x] テストの既定で MBean の登録を無効にする置き場を決める（D8）。既存の共通の設定（`backend/src/test/java/cherry/mastersmith/common/testsupport/TestDatabase.java` の `registry.add`、68 のテストのクラスが使う）に `spring.datasource.hikari.register-mbeans=false` を足すか、テストの設定のファイルを足すかを、既存のテストが ERROR のログを確かめる作りと合わせて決める。

### Step 3: データモデル・DB の振る舞い — 実装

- [x] 該当なし。表の形・移行（Flyway）・H2 の接続先は変えない（FR1.8、DSL の表への移行を足さない）。詰め直しは H2 の既存の指定（`DEFRAG_ALWAYS=TRUE`）と HikariCP の標準の操作だけで行う。

### Step 4: データモデル・DB の振る舞い — テストを書いて実行（不具合を再現する結合テスト）

- [x] `backend/src/test/java/cherry/mastersmith/config/HikariJmxSettingsTest.java`（単体テスト）: `application.yaml` を読み（既存の `H2DefragOnCloseTest` と同じ読み方）、`register-mbeans` と `allow-pool-suspension` が true、接続先の既定に `DEFRAG_ALWAYS=TRUE` があることを確かめる。設定を消すと失敗する（FR1.1 の再発防止）。
- [x] `backend/src/test/java/cherry/mastersmith/config/H2CompactionByPoolSuspensionIT.java`（結合テスト。不具合の再現。FR1.11 により直しと同じコミットに入れる）:
  - 準備: 内部DB は一時ディレクトリのファイルの H2 で、接続先に `;DEFRAG_ALWAYS=TRUE` を付ける（今のテストの既定の接続先には付いていないため、このクラスで上書きする）。プールの名前はこのクラスだけの名前にし、MBean の登録を有効にする。本文は固定の種の乱数の約 2MB とし、DSL の保存の業務処理（`DslRecordStore` の投入と適用）で 12 回重ね、プレビューも1件置く（アプリと同じ SQL の道でファイルを伸ばす）。
  - 伸びる（再現）: 重ねた後のファイルが、始めより本文の合計の分以上大きいこと。何も操作しなければ縮まないこと（FR1.2）。
  - 縮む: プラットフォームの MBean サーバーから HikariCP の Pool の MBean（`com.zaxxer.hikari:type=Pool (<プールの名前>)`）を JMX の代理で取り、`suspendPool` → `softEvictConnections` → `TotalConnections` が 0 になるのを待つ（Awaitility、上限つき）→ ファイルが縮むのを待つ（上限つき）→ `resumePool`。縮んだ後の大きさが、残っている本文の合計に余裕（D6 の考え方をこのテストの本文の大きさに当てはめた値）を足した値以下であること（FR1.1・FR1.4）。
  - 本文が壊れていない: 再開の後に、残っている履歴とプレビューの本文の SHA-256 が記録の `dsl_hash` と一致すること（FR1.8）。
  - 再開の後に使える: 再開の後に内部DB を使う処理（DSL の状態の読み取り）が成功すること。
  - 一時停止の間の要求（D3）: W1 なら、一時停止の間に別のスレッドで始めた内部DB を使う処理が、再開まで終わらず（`CountDownLatch` で、一時停止の間に終わっていないことを確かめる）、再開の後に成功すること。W2 なら、すぐ失敗すること。
  - 借りている接続がある時: 一時停止の前に借りた接続は、破棄の後も使え、返した時に閉じて 0 本になること（R-01 の打ち切りの前提）。
  - 後始末: どの場合も `@AfterEach` で `resumePool` を呼ぶ（テストの失敗でプールが止まったままにならないように）。
- [x] 2つのテストを実行し、すべて通ることを確かめる（`unit-test-instructions.md` のコマンド）。設定を足す前（Step 1 の前の状態）にこの結合テストが失敗すること（MBean が無い）も一度確かめ、結果を `code-summary.md` に書く。
- 要件: FR1.1、FR1.2、FR1.4、FR1.5（差あり）、FR1.8、FR1.11。

### Step 5: リポジトリ・データアクセス — 実装

- [x] 該当なし。DB アクセスのコードは変えない（Q3: HikariCP の標準の機能だけ）。

### Step 6: リポジトリ・データアクセス — テストを書いて実行

- [x] 該当なし（Step 5 で変えないため）。既存の `DslManageRepositoryIT` が通ることは Step 15 で確かめる。

### Step 7: 業務処理 — 実装（D2 で M2・M3 を選んだ時だけ）

- [ ] M2（条件つき）: `backend/src/main/java/cherry/mastersmith/dslmanage/domain/DslContent.java` の本文の複写を減らす（読み出しの道で本文の配列を1つにする）。呼び出し側（`DslPreviewRepository`・`DslAppliedRevisionRepository`・`DslLifecycle`・`DslAdminController` のダウンロード）が配列を書き換えないことを確かめる。本文の識別（SHA-256）とダウンロードのバイト単位の一致を保つ。
- [ ] M3（条件つき、T2 の時）: 使い捨ての環境で 10MB の投入の最中に `jcmd 1 GC.class_histogram -all` を取り、上位の形を確かめてから、`SafeYamlParser`・`YamlTreeConverter`・投入の処理で途中の形を早く手放す変更を入れる。上限・タグ・重複キー・誤りの行と列の決まりは変えない。
- [x] M2・M3 を選ばない時は該当なし（アプリの業務処理のコードは変えない）。

### Step 8: 業務処理 — テストを書いて実行（条件つき）

- [ ] M2 の時: `DslContent` の単体テスト（既存のテストのクラスがあれば足す）で、本文のバイト列と識別が変わらないこと、`equals`・`toString` が今と同じであることを確かめる。
- [ ] M2・M3 の時: DSL の信頼できない入力のテスト（大きさ・入れ子の深さ・別名・タグ・重複キー・`$ref`・拒否の応答。team.md の Testing Posture の一覧）を含む `cherry.mastersmith.dsl` と `cherry.mastersmith.dslmanage` のテストを実行し、すべて通ることを確かめる（FR2.3）。
- [x] 選ばない時は該当なし。

### Step 9: API — 実装

- [x] 該当なし。管理者向けの HTTP API は作らない（Q2: A、要件との差の FR1.3）。

### Step 10: API — テストを書いて実行

- [x] 該当なし（401・403・200 のテストは作らない。要件との差の FR1.3）。

### Step 11: 画面 — 実装

- [x] 該当なし（要件 FR1.3 でも画面は作らない）。

### Step 12: 画面 — テストを書いて実行

- [x] 該当なし。

### Step 13: 環境・ビルドの設定と道具

- [x] FR2（M1、D1・D2）: `Dockerfile` の `-XX:MaxRAMPercentage=75.0` を 50.0（T2 の時は 37.5）にし、コメントの 75% を直す。`docker/check-container-limits.sh` の確かめ（既定で 75%）を新しい割合に合わせる。`./docker/check-container-limits.sh` を `mastersmith:storage-memory-fix` などの別のタグのイメージで流し、すべて通ることを確かめる（配備のタグ `local` は上書きしない）。
- [x] FR1 の道具（D4 が J1 の時）: `docker/hikari-pool.sh` と `docker/jmx/HikariPoolControl.java` を足す（Apache License 2.0 のヘッダー、2026、agwlvssainokuni）。
  - `status`: 接続の本数（`ActiveConnections`・`IdleConnections`・`TotalConnections`・`ThreadsAwaitingConnection`）と内部DB のファイルの大きさ（`/proc/1/root/app/data/mastersmith.mv.db`）を出す。
  - `compact`: 同時に1つだけ動く（PC の側のロックのファイル。FR1.10）。一時停止 → 破棄 → 0 本を待つ（上限 D5）→ ファイルが落ち着くのを待つ（上限 D5）→ 再開。前と後の大きさ・かかった時間・結果を出す。0 本の待ちが上限を超えたら、詰め直さずに再開して失敗で終わる。どこで失敗・中断（Ctrl-C を含む）しても、再開を必ず試みる。再開に失敗したら、`resume` と `docker compose restart app` の手順を表示する（R-02）。
  - `resume`: 再開だけ行う（再開し忘れ・道具の途中の終わりからの戻し）。
  - 対象のコンテナの名前を引数で選べる（既定は配備の `mastersmith-app-1`。使い捨ての環境は `mastersmith-perf-app-1`）。
  - 出力に秘密情報を含めない（接続の本数と大きさだけ。NFR2）。
- [x] 道具の確かめ: 使い捨ての環境（`perf/README.md` の手順）で、10MB の投入と適用を重ねた後に `compact` を流し、縮むこと・本文が壊れていないこと・`resume` だけでも戻せることを確かめる（測定の記録 3.4 節と同じ確かめ）。結果を `code-summary.md` に書く。
- [x] FR5 の準備（勧める）: `perf/dsl-timing.sh` に `--compact` を足す。`--storage` の回（投入と適用を `STORAGE_ROUNDS` 回、Build and Test では 40 回）の後、止める前に道具の `compact` を使い捨てのアプリに対して流し、前と後の大きさ・時間を `storage.tsv` と別のファイルに記録し、その後の `restore_all_match` で本文が壊れていないかを確かめる。`perf/README.md` の表に足す。
- [x] FR3（D7 で直す時）: `backend/build.gradle.kts` のすべての `Test` のタスクの JVM の引数に `-Djava.net.preferIPv4Stack=true` を足し、理由（colima の転送との番号の重なり）をコメントに書く。`backend/src/test/java/cherry/mastersmith/common/testsupport/LoopbackPortCollisionTest.java`（単体テスト）: IPv4 の全アドレスで番号を1つ取った状態で、テストの JVM の既定の待ち受け（全アドレス）が同じ番号を取れないこと（`BindException`）を確かめる。直す前の設定では、この PC で失敗することを一度確かめて `code-summary.md` に書く。
- [x] FR3 の切り分けの記録（D7 に関わらず行う）: (a) `AccessTokenApiIT` をクラスだけで 10 回（`--rerun`）、(b) `./gradlew :backend:integrationTest` を2回、その間 colima の `ssh` の待ち受けの番号（`lsof`）とテストのアプリの番号（テストのログの「Tomcat started on port」）を記録し、重なりがあったかを見る。(c) 結果（再現したか、どの候補を支持・否定したか）を `code-summary.md` に書く（FR3.1）。
- [x] FR4: `perf/dsl-timing.sh` 33 行の説明の「要件の条件は 1g」を、前の Intent の決定（条件は 2g、配備と同じ値）に合わせて直す。既定の値（55 行）と `perf/README.md` 109 行は変えない。確かめは `grep` で説明と既定の値が一致すること（FR4.1）。

### Step 14: 文書と追跡

- [x] `README.md` の既知の制約（452〜453 行、U4-STORAGE-RUN）を書き直す（FR1.9）: 動いている間はファイルが伸びること、アプリを止めずに JMX（道具の `compact`）で詰め直せること、いつ行うとよいかの目安（例: ファイルが 300MB を超えた時、利用の少ない時）、詰め直しの間の要求は再開まで待たされること（D3）、かかる時間の目安（Build and Test の実測で埋める。NFR1）、一時停止が約 30 秒を超えると健全性が unhealthy になること、再開し忘れた時の影響（内部DB を使う要求がすべて止まり続ける）と戻し方（`resume`、だめなら `docker compose restart app`）、詰め直しの前にバックアップを取る判断（既存の「内部DBのバックアップと戻し方」はアプリを止めるため、止めてよい時だけ）、ディスクの空き（詰め直しは残す分の大きさの新しいファイルを書く）、`MASTERSMITH_DB_URL` を上書きする時は `;DEFRAG_ALWAYS=TRUE` が要ること、監査ログ・アプリのログには残らないこと（Q3）。
- [x] `README.md` の JVM の説明（217・221・223・247 行の 75%）と `docker/check-container-limits.sh` の説明を、D1・D2 で決めた割合に合わせる。`perf/README.md` にヒープの割合の説明があれば合わせる。
- [x] `code-summary.md`（変えたファイル・要件との対応・計画との差・確かめた結果）と、段の定義が求める追跡の成果物（`traceability.json` など）を書く。要件との差（この計画の表）を `code-summary.md` にも写す。

### Step 15: 統合の前の関門

- [x] `./gradlew verify` を README の `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して流し、すべての検査を通す（NFR5）。テストの件数とカバレッジを報告する時は `:backend:cleanTest :backend:cleanIntegrationTest` を付けて流し直し、実測の数字だけを書く（project.md）。
- [x] カバレッジの下限（行 80%・分岐 70%）を下げず、除外も増やさない（NFR4）。この計画は新しい main のパッケージを作らない（M2・M3 も既存のパッケージの中）ため、パッケージごとの下限の対象は増えない。
- [x] 既存の結合テスト（ログイン・ロック・監査・DSL の管理・`AccessTokenApiIT`）と E2E が通ることを確かめる（NFR6）。

## 要件と手順の対応

| 要件 | 手順 | テスト・確かめ |
|---|---|---|
| FR1.1 | Step 1・Step 13（道具） | `HikariJmxSettingsTest`、`H2CompactionByPoolSuspensionIT`、使い捨ての環境での道具の確かめ |
| FR1.2 | Step 1 | `H2CompactionByPoolSuspensionIT`（操作しなければ縮まない） |
| FR1.3 | 作らない（要件との差） | なし（401・403・200 のテストは作らない） |
| FR1.4 | Step 4・Step 13（`--compact`） | `H2CompactionByPoolSuspensionIT`、Build and Test の 40 回の後の判定（D6） |
| FR1.5 | Step 1（W2 の時）・Step 4 | `H2CompactionByPoolSuspensionIT`（D3 のとおりの動き） |
| FR1.6・FR1.7 | 行わない（要件との差） | なし |
| FR1.8 | Step 3（変えない）・Step 4 | `H2CompactionByPoolSuspensionIT`（本文の一致）、既存の `DslManageRepositoryIT`・`DslAdminApiIT` |
| FR1.9 | Step 14 | README の確認 |
| FR1.10 | Step 13（道具のロック） | 道具の確かめ（同時に2つ動かすと2つ目が断る） |
| FR1.11 | Step 4 | `H2CompactionByPoolSuspensionIT` を直しと同じコミットに入れる |
| FR2.1 | 計画の前に済み | 測定の記録 2 節 |
| FR2.2 | 承認の場（D1） | なし |
| FR2.3 | Step 7・Step 8・Step 13 | `check-container-limits.sh`、DSL の信頼できない入力のテスト |
| FR2.4 | Build and Test に引き継ぐ | `dslMixed` の直した後の測定 |
| FR3.1 | Step 13（切り分けの記録） | 測定の記録 4 節、`code-summary.md` |
| FR3.2・FR3.3 | Step 13（D7） | `LoopbackPortCollisionTest`（直す時） |
| FR4.1 | Step 13 | `grep` の確かめ |
| FR5 | Build and Test に引き継ぐ | 下の「Build and Test に引き継ぐこと」 |
| NFR1 | Step 14・Build and Test | 詰め直しの時間の実測を README に書く |
| NFR2 | Step 13（道具の出力） | 道具の出力に秘密情報が無いこと |
| NFR3 | 要件との差 | JMX を外に公開しないこと（道具は attach だけ） |
| NFR4・NFR5・NFR6 | Step 15 | `./gradlew verify` |

## R-01・R-02 の扱い（詰め直しの時間の上限・失敗の時の動きと戻し方）

- 時間の上限（R-01）: HikariCP の標準の操作には時間の上限が無いため、道具の `compact` が上限を持つ（D5）。0 本の待ちが上限を超えたら、閉じる前に取りやめて再開する（何も壊さない）。詰め直し（H2 が閉じる処理）そのものは途中で止められないため、かかる時間を Build and Test で測り（NFR1）、README に目安を書く。測定の記録 3.4 節では1秒未満、3.2 節（PC の上、乱数の本文）では約 2 秒だった。
- 一時停止の間の影響: 内部DB を使う要求（ログイン・トークンの更新・監査の記録・DSL の操作・健全性の確認）は再開まで待つ（D3 が W1 の時）。一時停止が約 30 秒を超えると `/actuator/health` が 503 を返し続け、コンテナの健全性が unhealthy になる（再開で戻る）。既存の `TimeBoundedDbHealthIndicator` の WARN がアプリのログに出る（詰め直しの操作そのものの記録ではない）。
- 再開し忘れ・道具の途中の終わり（R-02）: 内部DB を使う要求がすべて止まり続け、処理の糸（Tomcat のスレッド）がたまる。戻し方は、(1) 道具の `resume`、(2) だめなら `docker compose restart app`（止める時に H2 が閉じ、`DEFRAG_ALWAYS` でも詰め直される）。
- 詰め直しの失敗（R-02）: H2 が閉じる処理で失敗した時や、再開の後に DB を開けない時は、アプリの要求が 500 になり、健全性が unhealthy になる。戻し方は、(1) `docker compose restart app`、(2) それでも開けない時は、README の「内部DBのバックアップと戻し方」で直前のバックアップを展開する（バックアップの後の記録は失われる）。そのため、大きな詰め直しの前には、アプリを止めてよい時にバックアップを取ることを README で勧める。
- 詰め直せない時（`MASTERSMITH_DB_URL` を `DEFRAG_ALWAYS` なしで上書きしている等）: 道具は前と後の大きさを出すため、縮まなかったことが分かる。README で接続先の指定を確かめるよう書く。

## Build and Test に引き継ぐこと（FR5）

この Intent には Performance Validation の段が無いため、負荷の試験の持ち主は Build and Test である（project.md の決まり）。

- `dslMixed`（測定の記録 1 節と同じ条件・同じ順序。直した後のイメージで、アプリのコンテナを作り直してから流す）で、D1 の目標を判定する（FR2.4）。直す前の値は測定の記録 2.1 節を使う。
- `dslCycle` と `perf/dsl-timing.sh`（`--storage`、`STORAGE_ROUNDS=40`、`--compact`）で、40 回の後の大きさと、詰め直しの直後の大きさを D6 の値で判定し（FR1.4）、詰め直しにかかった時間を測る（NFR1）。圧縮の効かない本文でも確かめる（悪い側の条件）。
- 詰め直しの最中にログインを送り、再開の後に成功すること（D3 が W1 の時）を確かめる。
- ヒープの割合を変えるため、`refresh` の場面（前の Intent で 2g で止まらないことを確かめた場面）も流し、止まらないことを確かめる。
- 配備先の環境が要る目標は無い見込み。あれば `Unverified` とし、持ち主の段を書く。
- 長い試験は `caffeinate -i` を付け、片付ける前に結果を確かめる（project.md）。

## コミットの分け方（案）

コミットはコード生成の後に、依頼者の承認を得てから行う（project.md の Change Control）。メッセージは日本語。

| 案 | 内容 | 要件 |
|---|---|---|
| C1 | HikariCP の JMX と一時停止の設定、再現の結合テストと設定のテスト、JMX の道具、README の詰め直しの手順（不具合を再現するテストを直しと同じコミットに入れる） | FR1 |
| C2 | 最大ヒープの割合（`Dockerfile`）、`docker/check-container-limits.sh`、README の JVM の説明（M2・M3 を選んだ時はそのコードとテスト） | FR2 |
| C3 | テストの JVM の IPv4 の指定と、重なりを確かめる単体テスト（D7 で直す時） | FR3 |
| C4 | `perf/dsl-timing.sh` の説明の直しと `--compact`、`perf/README.md` | FR4・FR5 の準備 |
| C5 | ワークフローの記録（この段の成果物） | なし |

統合は D9 のとおり、短命のブランチから `develop` へ squash マージする（勧める）。`origin` へのプッシュは依頼者が行う。

## 気になる点

- 詰め直しの操作の記録（誰が・いつ・前と後の大きさ）はどこにも残らない（Q3）。操作した人の端末の出力だけになる。後から「いつ詰め直したか」を確かめたい時は、ファイルの大きさの変化からしか分からない。
- 一時停止の間の待ちに上限が無いため、道具を使わずに JMX の操作を手で行うと、再開し忘れで内部DB を使うすべての要求が止まる。README では道具を使う手順だけを書き、手での操作は戻し（`resume`）だけにする。
- 最大ヒープの割合を下げると、完全な GC が増える場面がありうる（`-Xmx768m` では 120 秒で 20 回）。50% では2回だった。
- 測定の記録 3.2 節の試し A の途中の1回の観察（持ち続けたまま 50 秒空けた後、5 回重ねても伸びなかった）は説明がついていない。A2 では伸び続けたため、計画は「伸び続ける」前提で書いた。

## Sources

- 要件: `aidlc/spaces/default/intents/260925-storage-memory-fixes/inception/requirements-analysis/requirements.md`、質問と回答: 同じディレクトリの `requirements-analysis-questions.md`
- 要件の確認の指摘 R-01〜R-04: `aidlc/spaces/default/intents/260925-storage-memory-fixes/.aidlc-reviews/requirements-analysis/stage/1dc1bc9ea8bb59b7/1.json`
- この段の質問と回答（Q1〜Q3）: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/code-generation-questions.md`
- 計画の前の測定と前提の確かめ: `aidlc/spaces/default/intents/260925-storage-memory-fixes/construction/code-generation/baseline-measurement.md`
- コードの知識: `aidlc/spaces/default/codekb/mastersmith2/code-quality-assessment.md`（TD-1〜TD-4）、`aidlc/spaces/default/intents/260925-storage-memory-fixes/inception/reverse-engineering/developer-scan.md`
- 決まり: `aidlc/spaces/default/memory/org.md`・`team.md`・`project.md`・`phases/construction.md`

## Assumptions & Open Questions

- D1〜D9 は承認の場で依頼者が決める。勧める案で書いた Step は、決定に合わせて行うかを変える。
- `DslRecordStore` の投入と適用をテストから直接呼べる形か（引数の型・監査の出来事の扱い）は、Step 4 の中で確かめる。呼べない時は、同じ SQL の道（リポジトリ）を使う。
- テストの既定で MBean の登録を無効にする置き場（Step 2）は、既存のテストのログの確かめ方を見てから決める。
- `com.zaxxer.hikari.throwIfSuspended=true`（W2）で一時停止の間の失敗が今の接続の待ちの失敗と同じ応答になるかは未検証（W2 を選んだ時に Step 4 で確かめる）。
