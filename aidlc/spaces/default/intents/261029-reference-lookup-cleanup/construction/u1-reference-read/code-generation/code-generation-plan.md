# コード生成の計画 — U1 u1-reference-read

## Summary

- Builds: 他テーブル参照のサーバー側の読み取り（REFERENCE の選択肢・LOOKUP のピッカーの検索とラベル・U2 向けの `check`／`countState`）と2つの新しい設定、perf の種と k6 の場面（B1・B2 の2つの Bolt）
- Touches: `backend/src/main/java/cherry/mastersmith/businessdata/`（domain・service・repository・web）・`targetdb/`（config・domain・service）、`application.yaml`・`.env.example`・`README.md`・`perf/`
- Tests: 単体テスト 約 110 件（jqwik の性質ベース 約 12 件を含む）と結合テスト 約 130 件（3種類の対象DB の 約 75 件を含む）、既存のテスト 約 12 ファイルの書き換え

## 1. 前提と範囲

- 単位 U1 `u1-reference-read`（service・XL）。Delivery Planning（`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/delivery-planning/bolt-plan.md`）で前半 B1・後半 B2 に分けた。設計の段は1つの単位として通した（`project.md` の学び）。
- Test Strategy は Standard（部品ごとに 5〜8 件）、範囲は classic（行 80%・分岐 70% の下限を全体とパッケージごとに当てる。判定は Build and Test の全件で行う）。方法は test-after（2節の Testing Contract が正）。
- この単位は画面を持たない。frontend のソースは変えない（定義の応答に任意の欄 `reference` を足すだけで、今の画面は `purpose` を送らないため応答は `reference: null` を足しただけになる。BR12.1）。
- 移行ファイル・DSL の JSON Schema・依存（`backend/gradle.lockfile`・`gradle/libs.versions.toml`・npm）を変えない（NFR7.1・NFR9.1）。
- Construction の前の作業（Dependabot の #81・#82 の取り込み）は単位の外で、B1 を始める前に済んでいることを確かめる（済んでいなければ止めて指揮役に伝える）。

### 1.1 Bolt の分け方

| Bolt | ブランチ | 中身 | 受け持つストーリー（U1 の分） |
|---|---|---|---|
| B1 | `feature/261029-reference-lookup-cleanup-b1` | 2つの設定と `ReadPurpose.REFERENCE`・`TargetReadAccess.referenceReadTotal()`、domain の部品（`ReferenceReadSpec`・判定の純粋な関数・U2 との継ぎ目 `ReferenceReadDeadline`・`ReferenceReadFailure`・`ReferenceFailureReason`）、行の SQL の部品への参照の読み取りの指定、定義の取得の `purpose` と `reference`（REFERENCE の選択肢・LOOKUP のピッカーの情報）、定義の経路の3種類の DB・接続の数・時間切れの結合テスト | US1.1・US1.2・US1.3（定義の分）・US1.4（定義の分）・US3.4・US3.5（定義の分） |
| B2 | `feature/261029-reference-lookup-cleanup-b2` | 参照先の検索とラベルの API（`POST /api/tables/reference/rows`）、同じ応答の 403、本文の上限、U2 向けの `check`・`countState`（`Failed(ReferenceReadFailure)`）、認可の表・漏えい・トレース・接続の数・時間切れの結合テスト、perf の種・DSL・k6 の場面と `perf/README.md` | US2.1・US2.3（サーバー側）・US1.3／US1.4（検索・ラベル・確かめの分）・US3.5（検索・ラベルの分） |

### 1.2 U2 との順（NFR 設計 U2 R-05 の直し）

U2（Bolt B3）は、U1 が作る次の型と口に依存する。どれも **B1（`ReferenceReadDeadline`・`ReferenceReadFailure`・`ReferenceFailureReason`・`TargetReadAccess.referenceReadTotal()`）と B2（`ReferenceCheckResult.Failed(ReferenceReadFailure)`・`ReferenceCountState.Failed(ReferenceReadFailure)`・`ReferenceLookup.check`・`ReferenceLookup.countState`）で `develop` に統合した後に** B3 を始める。名前と置き場は NFR 設計の `reliability-design.md` 5節・`logical-components.md` 5節のとおりで、変えない。`ReferenceReadDeadline.narrow` の単体テスト（T-T2）は U1 が持ち、U2 では重ねない。U2 の計画もこの順を前提にする。

## Testing Contract

（下の JSON の塊は `aidlc engine testing-posture render` の出力の `## Testing Contract` の塊を、一字も変えずに写したもの。）

```json
{
  "version": 1,
  "methodology": "test-after",
  "source": "team",
  "ordering": "テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。",
  "scope": "classic",
  "test_strategy": "standard",
  "project_type": "brownfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: test-after\n- **Ordering**: テスト可能な層（ドメイン・業務処理・API・画面部品）ごとに実装を書き、同じ Bolt の中でその層のテストを書いて実行し、すべて通ってから次の層へ進む。\n- テストは各 Bolt の成果物の一部であり、テストのない機能は完成とみなさない。テスト量はワークフローの Test Strategy に従い、以下の下限はそれに追加される。\n- カバレッジの下限は、すべての Intent に共通で **行カバレッジ 80% 以上、分岐カバレッジ 70% 以上** とする。バックエンド・フロントエンドの両方に適用し、下回ったらビルドを失敗させる。道具はバックエンドが JaCoCo、フロントエンドが `@vitest/coverage-v8`（`thresholds` 設定）。\n- バックエンドでは、全体の合計に加えて、すべてのパッケージごとにも同じ下限（行 80%・分岐 70%）を当てる。ただし、既存のパッケージに単独で下限を下回るものがあれば、パッケージごとの下限は新しく作るパッケージだけに当てる（既存のパッケージは全体の合計で判定する）。どちらになるかは、パッケージごとの下限を入れる Bolt で既存のパッケージのカバレッジを実測して決め、結果を記録する。\n- 全体の合計で判定している既存のパッケージ（`backend/build.gradle.kts` の `packagesJudgedByTotal` の一覧。2026-10-16 の時点でも 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。`user.domain`・`user.repository`・`user.service` は Intent 260925-user-management で一覧から外れ、含まない）に手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外してパッケージごとの下限の対象に必ず戻す。「手を入れる」は、そのパッケージの本体のソース（`src/main`）の変更のすべて（説明文だけの直しを含む）を指し、テストだけの変更は含めない。手を入れる見込みのパッケージとそれに伴う作業は、Delivery Planning とコード生成の計画で、各パッケージの今の値を実測して見積もる。カバレッジは、その Bolt のテストを足した後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測し、値を記録する。一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない。\n- フロントエンドのカバレッジの下限は、今の形（全体の合計に当てる `thresholds`）のままとする。新しい機能のフォルダ（`src/features/<featureId>/`）のカバレッジの値は、Build and Test で記録する。\n- カバレッジの計測から外すのは、アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/` 配下に限る。除外を後から増やして実質的に下限を下げることはしない。パッケージごとの下限を満たすために除外を増やすこともしない。\n- DB を使うテストは、本番と同じ種類の DB をコンテナで起動して使う（Testcontainers）。そのため、ローカルと CI にコンテナの実行環境があることを前提条件として文書化する。テストごとにデータを用意して巻き戻し、実行順に依存させない。表を作る・消す操作（DDL）がその場で確定して巻き戻せない DB（MySQL・MariaDB など）で表を作るテストは、巻き戻す代わりに、テスト（またはテストのクラス）ごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）を作り、終わったら消す。対象DB の業務データを読むだけのテストでも表を作るため、この決まりを当てる。作る名前には実行ごとに一意の値を入れ、途中で止まった前の実行の残りに頼らない。\n- 対象DB へ書き込むテスト（業務データの作成・更新・削除を範囲に入れる場合）は、アプリの対象DB への接続とテストの接続が別のため、アプリのトランザクションの巻き戻しに頼らない。3種類の対象DB のすべてで、テストのクラスごとに名前の重ならないスキーマ（MySQL・MariaDB ではデータベース）と表を作って行を用意し、終わったら消す。書いた後の値は、アプリとは別の接続で読み直して確かめる。\n- 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）。Intent `260923-dsl-schema-loader` の Build and Test で `verify` の時間と colima の VM のメモリを実測したうえで、この形に決めた。コンテナの実行環境が無いときの扱いは Way of Working のとおり。\n- テストを足した Bolt の後と Build and Test で、`./gradlew verify` の時間と CI の時間を測って記録する。CI の上限（`timeout-minutes: 60`）の7割の約 42 分を超えたら、テストの分け方を依頼者に諮る。\n- 外へ送るメール（SMTP）を使う機能のテストは、送信の部品をモックで置き換えて済ませず、テストの中で JVM の中に起動するテスト用の SMTP の受け手で実際に受け、宛先・件名・本文（HTML）・言語を確かめる。受け手にコンテナは使わない（コンテナの実行環境が無いときに飛ばしてよいテストは、対象DB のテストだけのままとする）。送信の失敗（受け手が接続を拒む・応答しない）のテストも必ず入れる。実在の宛先へは送らない。受け手の具体の道具は、採用の前のライセンスの確認とあわせて設計の段で決める。\n- 画面からの一連の操作を確かめるテスト（E2E）は Playwright（`./gradlew e2eTest`）で書き、`./gradlew verify` と CI の外に置く。本数は代表的な流れに絞り、機能の Intent ごとに代表の流れを1本まで足す。新しく足す流れは、前のテストが作った状態に頼らず、利用者などの前提を自分で作る。複数の機能の Intent を1つに束ねた Intent では、束ねた元の Intent ごとに数える（Intent 261004-role-menu は最大2本）。既存の E2E を新しい形（権限の形など）に合わせて書き換えることは、本数に数えない。利用者の状態を変える操作（利用停止・管理の権限の変更・ロックなど）は、その流れで自分で作った利用者だけを対象にし、初期管理者の状態は変えない。役割・権限を変える操作も、その流れで自分で作った利用者と役割だけを対象にし、初期管理者は変えない（E2E は1つのアプリ・内部DB・初期管理者を全ファイルで共有して順に流すため）。E2E をどのブランチで統合の前に流すかは Way of Working のとおりとし、リリースの前にも手元で実行する。\n- 業務データを表示する E2E は、見本の対象DB（PostgreSQL の1種類）がコンテナで動いていることを前提にする。`./gradlew e2eTest` は始める前に対象DB に届くかを確かめ、届かなければ起動の手順を示して失敗する（Mailpit と同じ扱い。起動・停止はしない）。流れが使う行は、その流れで自分で作るか、流れの専用のスキーマに置き、見本の行は架空の値だけにする。対象DB の資格情報は Playwright の `webServer.env` に置かずプロセスの環境変数で渡し、報告の json を文字列で検索して資格情報・接続先の値が入っていないことを確かめる。Intent 261016-business-data-views で足す E2E は業務データの代表の流れ1本までとし、持ち越しの直し（make-you-chic-ui の固定先の更新・axe-core の取り込み）と既存の E2E の書き換えは本数に数えない。\n- 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を、純粋な関数（ロック判定の回数計算、有効期限の判定、入力の検証など）に一部適用する。道具は Java が jqwik、フロントエンドが fast-check。失敗時の乱数の種を記録して再現できるようにする。\n- テストの説明文（テスト名、`describe` / `it`、`@DisplayName`）は英語で書く。テストデータは日本語でよい。\n- リポジトリは公開のため、テストデータのメールアドレスは予約のドメイン（`example.com` など）だけにし、実在しそうな氏名・宛先を置かない。\n- フロントエンドのテストは対象と同じ場所に `*.test.ts` / `*.test.tsx` として置き、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe を使う。画面部品ごとにアクセシビリティ検査を1件入れる。\n- 画面のテストで、描画の後（`useEffect` などの効果）に反映される値は、操作の直後に同期で確かめず、`waitFor` で待って確かめる。テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばさない。\n- Java のテストは `src/test/java` に、対象と同じパッケージ構成で置く。単体テストは `XxxTest`、Spring や DB を起動する結合テストは `XxxIT` とし、分けて実行できるようにする。\n- 時刻に依存する処理（有効期限、ロックの解除など）は注入可能な時計（`Clock` 等）から現在時刻を取得し、テストで `sleep` や実時刻に依存しない。\n- 不安定なテストと CI の失敗は、次の1つの決まりで扱う。\n  - 手元で再現した不安定なテストは、原因を直すまで統合しない（次へ進まない）。\n  - 手元で再現しないものは、再現の試みに先に時間の上限を決め、見立てと試みの範囲を記録したうえで、CI の再実行で通れば進めてよい（不安定と確かめられていない扱い）。\n  - 同じテストが二度目に落ちたら、原因を直すまで次へ進まない。\n  - 失敗を直さずに次の Intent へ持ち越すのは、依頼者の決定があるときだけとし、決まりとの差を記録する。\n  - 不安定なテストを直したときは、二段で確かめる。疑った競り合い・待ちの抜けを待ち合わせや順の入れ替えで確実に起こせたときは、直す前のテストが落ち、直した後のテストが通ることを記録する。起こせないときは、上の「手元で再現しないもの」と同じく、試みの時間の上限を決めて試み、見立てと試みの範囲を記録する。\n- 認証・認可・監査に関わる機能では、次のテストを必ず書く。★印の項目は要件（しきい値や動作）が未確定のため、要件定義で決めてからテストを書く。この一覧の「管理者」は管理の権限を持つ利用者を、「管理の権限」はその判定に使う権限を指し、権限の形（真偽値1つか役割・権限か）によらずに読む。権限の具体の形（役割・権限の名前など）は、要件で決まった後に書き足す。\n  - アカウントロック: 失敗回数のしきい値の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロック中は正しいパスワードでも拒否、ログイン成功時の失敗回数の扱い。★しきい値、回数を数える期間、ロックの解除方法\n  - ログイン: 存在しないユーザーとパスワード誤りで、応答からユーザーIDの存在を推測できないこと\n  - トークン: 有効期限の境界（直前は有効／直後は無効）、署名の改ざん、署名方式の指定を悪用した改ざん（`alg: none` 等）、ログアウト後のリフレッシュトークンの拒否、ログアウト後もアクセストークンが有効期限まで使えること（決定済みの仕様として明示する）。★各トークンの有効期限の値、リフレッシュトークンを使うたびに作り直すか\n  - 初期管理者の自動作成: 2回目以降の起動で重複作成しない、設定が無い／不正なときの動作、パスワードがログに出ない。★設定が無いときに起動を止めるか\n  - 認可: 未認証（401）、その権限を持たない（403）、持つ（200）をサーバー側のテストで確かめる（画面で管理メニューを隠すことはサーバー側の検査の代わりにしない）\n  - 監査ログ: 対象イベントごとに必須項目が記録されること、存在しないユーザーIDでのログイン失敗も記録されること。★監査ログの書き込みに失敗したときに操作を失敗させるか\n  - 秘密情報の漏えい: ログ・監査ログの出力にパスワード・トークンの値が含まれないこと\n  - 構造化ログ・分散トレース: 決めた形式で出る、トレースIDがログに含まれる、外部エクスポートが既定で無効であること\n  - 画面: ログイン画面のアクセシビリティ検査、ロック時のメッセージ表示、ログアウトでトークンが破棄されること\n  - 招待と登録の完了: 招待の有効期限の境界（直前は有効／直後は無効）、使い終えた招待・取り消した招待の再使用の拒否、改ざんした招待・存在しない招待の拒否（応答から利用者の存在を推測できないこと）、招待中（登録が終わっていない）の利用者はログイン・トークンの更新・アクセストークンの認証のどれでも拒否されること。★招待の有効期限の値、再送・取り消しの扱い\n  - パスワードの変更: 今のパスワードの確かめ、パスワードの規則の境界、変更の後のリフレッシュトークンの扱い（決めた側の動作を明示したテストにする）。★パスワードの規則、変更の後にほかの端末のリフレッシュトークンを無効にするか\n  - 利用停止: 停止中の利用者は、ログインの照合・トークンの更新・アクセストークンの認証の3つの入口のすべてで拒否されることを、入口ごとにサーバー側のテストで確かめる。停止を解いた直後は3つの入口のすべてで受け付けること。停止の前に出したリフレッシュトークン・アクセストークンの扱いは、決めた側の動作を明示したテストにする。★停止を応答から推測できてよいか、停止の前に出したトークンの扱い\n  - 管理の権限の変更: 管理の権限を与えた直後・外した直後の次の要求で、管理の API の 403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。権限を外す前に出したトークンの扱いと、自分の管理の権限を外す操作の扱いは、決めた側の動作を明示したテストにする。★権限を外す前に出したトークンの扱い、自分の管理の権限を外す操作の扱い\n  - ロックの解除: 解除の直後に正しいパスワードで入れること、解除の後の失敗回数の境界（しきい値−1回ではロックされない／しきい値ちょうどでロックされる）、ロックの状態の行が無い利用者の解除。時刻は注入した時計で動かし、実時刻と `sleep` に頼らない。★解除の後の失敗回数の扱い、ロックの状態の行が無いときの応答\n  - 最後の管理者の保護: 最後の有効な管理者（管理の権限を持つ利用者）から管理の権限を外す・利用を止める操作は拒否され、拒否の後に状態が変わっていないこと。2人の管理者が同時に互いの管理の権限を外す・利用を止めても、有効な管理者が 0 人にならないこと。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★最後の管理者の数え方（止めた管理者を数えるか）\n  - 管理の API の認可: 足す管理の API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。停止中の管理者は管理の API を呼べないこと。\n  - 要求の改ざん: 管理の API の外（`/api/me` など）から、要求の本文の値を変えて自分の管理の権限や利用者の状態を変えられないこと（一括代入の防止）。\n  - 管理の操作の監査: 利用者の権限・状態を変える操作（管理の権限の付け外し・利用停止と再開・ロックの解除）ごとに、操作した人・対象の利用者・結果が記録されること。★拒否した操作（403・最後の管理者の拒否など）を記録するか\n  - 利用者の管理の漏えい: 一覧・詳細の応答と監査の行に、パスワードのハッシュ値・リフレッシュトークン・ロックの判定の内部の値が含まれないこと。TRACE のログを有効にして一覧・詳細を読んでも、メールアドレス・氏名がアプリのログに出ないこと（既存の `*SecretLeakIT` と同じ形で確かめる）。\n- 役割・権限の機能では、次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 権限ごとの API の認可: 足す・変える API のすべてについて、未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。403 を確かめる利用者は、権限が何も無い利用者ではなく、要る権限だけを欠く利用者（ほかの権限は持つ）にする。API と権限の組は、パラメーターを使うテストで表として書く。停止中の利用者は、権限があっても通らないこと。\n  - API の分類の網羅: アプリが持つすべての API が「公開・ログインだけ・権限が要る（どの権限か）」のどれかに明示して分類されていることを確かめるテストを置き、分類の無い API があれば失敗させる（`/api/**` の既定は「ログインだけ」で、権限の決まりを書き忘れるとログイン中の誰でも呼べるため）。足した API を分類に足し忘れると落ちる形にする。分類の持ち方と仕組みの置き場は設計の段で決める。\n  - 割り当ての変更の反映: 役割・権限を付けた直後・外した直後の次の要求で、403／200 がサーバー側で切り替わること（画面が持つ古い権限に頼らない）。★変更の前に出したトークンの扱い\n  - 権限の昇格・一括代入・IDOR の防止: 操作する人が持たない権限を、自分や他人に与えられないこと。要求の本文の値を変えて自分の役割・権限を変えられないこと、役割の作成・変更の本文に許していない項目（ID・組み込みの印・作成者など）を足しても反映されないこと。役割・割り当ての ID を差し替えた要求（他人の割り当て・存在しない役割など）は拒否され、状態が変わらないこと。★組み込みの役割の有無と、その削除・権限の取り上げの保護\n  - 自分自身への操作と最後の管理者の読み替え: 自分の役割を外す操作と、最後の有効な管理者を役割でどう数えるかは、決めた側の動作を明示したテストにする。同時の重なりはスレッドの数に頼らず、待ち合わせで確実に作る。★自分の役割を外す操作の扱い、最後の有効な管理者の役割での数え方\n  - 監査: 割り当ての変更ごとに、操作した人・対象・変えた中身・結果が記録されること。★役割そのものの作成・変更・削除を記録するか、拒否した操作（403・昇格の拒否など）を記録するか\n  - 画面の出し分け: メニュー・画面を隠すことを、サーバー側の検査の代わりにしない。権限の無い画面へ直接移ったときに 403 の表示になること。\n  - 性質ベースのテスト: 役割から権限の集合を求める関数（サーバー側）と、権限でメニューの枝を落とす関数（画面側）は純粋な関数にし、jqwik・fast-check で性質（持たない権限の API・項目が出ない、役割を足しても権限は減らない など）を確かめる。\n- 業務データの一覧・詳細（と、範囲に入れるなら作成・更新・削除）の機能では、次のテストを必ず書く（役割・権限の機能と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。具体的な上限の値は設計の段で決め、決めた値の境界で確かめる。\n  - 権限ごとの操作の可否: テーブルの主権限と補助権限の組ごとに、業務データの API の未認証（401）・その権限を持たない（403）・持つ（200）をサーバー側のテストで確かめる。API と権限の組は、パラメーターを使うテストで表として書き、403 を確かめる利用者は要る権限だけを欠く利用者にする。作業ロールを切り替えた直後・割り当てを変えた直後の次の要求で切り替わること。\n  - 主キーとビュー: 主キーの無いテーブルとビューでは、行を1件に決められない操作（更新・削除など）を、画面で隠すだけでなくサーバー側で拒否する。主キーが複数のカラムのテーブルも確かめる。★主キーの無いテーブルで詳細を見せるか\n  - カラムの出し分け: カラムの主権限が NONE（読めない）のカラムは、画面で隠すだけでなく API の応答に含めないことを確かめる。DSL の表示の設定（`list.visible`・`detail.visible`）で隠すだけのカラムの扱いは設計の段で決め、決めた側の動作を明示したテストにする。\n  - IDOR と一括代入: スキーマ名・テーブル名・主キーの値・並べ替えの列・検索の列を差し替えた要求（適用中の DSL に無い名前、権限の無いテーブル、存在しない行）は拒否され、状態が変わらないこと。作成・更新の本文に許していないカラムを足しても反映されないこと。\n  - 403 と 404 の使い分け: 権限の無いテーブル・DSL に無いテーブル・存在しない行の応答の状態コードと code をそろえるか。★使い分け（決めた側の動作を明示したテストにする）\n  - SQL の組み立て: 識別子は適用中の DSL にある名前と照らし、値はバインド変数で渡すこと。識別子・検索の値・並べ替えに `'`・`\"`・`;`・`--`・バッククォート・改行を含めても、SQL として解釈されないこと（Code Style の SQL の組み立ての決まりを確かめる）。\n  - 3種類の対象DB の境界の値: 一覧・詳細（と書き込み）を、MySQL・MariaDB・PostgreSQL のすべてで結合テストで確かめる。型の違い・識別子の大文字と小文字・NULL の並びと、境界の値（最大の整数・桁の多い小数・NULL）を含める。\n  - 行の数の上限と時間切れ: 一覧のページの大きさの上限の境界（上限ちょうどは受け付け、超えると拒否）。JDBC の取り出しの上限（`setMaxRows` か問い合わせの上限）を置き、上限を超える行を内部で読まないこと。応答しない対象DB での時間切れの応答（既存の ServerSocket の方式）。\n  - 部分一致の検索: 部分一致の検索を設けるなら、検索の値の `%`・`_`・エスケープの文字が一致の条件として働かない（エスケープして渡す）こと。\n  - 画面に出す値: 業務データの値とカラムの表示名を信頼できない入力として扱い、`<script>`・`<img onerror>`・`\"`・`'`・`javascript:` を含めても、一覧・詳細で文字として出る（HTML として描かない、リンクの先にしない）こと。\n  - 道に乗る主キーの値: 画面の道に乗る主キーの値（複合の主キー、`/`・`%`・`?`・`#`・空白・引用符を含む値）をエンコードし、サーバー側でデコードの後に照らすこと。\n  - 接続の数: 業務データの API が1要求で使う対象DB の接続と内部DB の接続の数を、既存の `*ConnectionUsageIT`（`NavigationConnectionUsageIT` など）と同じ形の結合テストで決定的に確かめる。\n  - 性質ベースのテスト: テーブルの主キーの有無・ビューか・主権限・補助権限から許す操作の集合を求める関数と、識別子の照合の部品は純粋な関数にし、jqwik・fast-check で性質（権限を足しても許す操作は減らない、主キーの無いテーブルに行を1件に決める操作が出ない、DSL に無い名前は拒否される、引用符・`;`・改行を含む任意の文字列でも SQL の構文として解釈されない など）を確かめる。\n  - 漏えい: 業務データの行の値（主キーの値を含む）と対象DB の接続情報が、アプリのログ（TRACE を有効にした `XxxSecretLeakIT`）・トレースの属性（外部へ送る設定を有効にした結合テスト）・エラー応答に含まれないこと。JDBC の例外のメッセージが応答にもログにも出ないこと。\n  - 監査: ★業務データの操作（読み取りを含めるか）を監査に残すか、残す中身、対象DB の確定と監査の行の一方だけが残ったときの扱い。決まった側の動作を明示したテストにする。\n  - 画面: 一覧・詳細の画面ごとのアクセシビリティ検査、権限の無いテーブルへ直接移ったときの 403 の表示、操作の可否に合わせてボタンが押せない（または出ない）こと。\n- axe-core を上げて新しい規則の違反が出たときは、規則を外して検査を通さず、違反をすべて直してから上げる（固い制約は `project.md` の Forbidden）。違反の要素が make-you-chic-ui の部品のものなら依頼を出し、固定先の更新で取り込んでから上げる。上げたときは、実際のブラウザの axe（E2E）と画面のテストの vitest-axe の両方を流し、E2E の全件の通過と違反 0 件を件数で記録する。あわせて、axe-core の版が lockfile で1つにそろっているかを確かめる。\n- N 階層のメニュー（ナビゲーションの木）の画面では、次のテストを必ず書く。深さの上限の具体的な値は設計の段で決め、決めた値の境界で確かめる。\n  - 開閉と今の項目: 開閉の状態（`aria-expanded`）と今の項目（`aria-current`）が正しく出ること、キーボードだけでたどれること。\n  - 深さの上限: 上限ちょうどは受け付け、上限を超えると拒否されること。\n  - 表示の木を作る関数: 定義の木から表示の木を作る純粋な関数（権限で枝を落とす・並べる）に、fast-check の性質ベースのテストを当てる（例: 権限の無い項目が出ない、子の無い枝が残らない）。\n  - 信頼できない入力: メニューの定義（`label`・`icon`・`table`）を信頼できない入力として扱う。表示名に `<`・`>`・`&`・`\"`・`'` を含めても文字として出る（HTML として描かない）こと、`icon` を許す名前の一覧と照らすこと、`table` の名前から組み立てる道をエンコードし、アプリの中の決めた形から外れない（外部の URL・`javascript:` の道を作らない）こと。★一覧に無い `icon` を拒否するか既定のアイコンにするか\n  - make-you-chic-ui の部品に頼る振る舞い: 開閉・`aria-current`・キーボードなど、make-you-chic-ui の部品に頼る振る舞いも、このリポジトリの画面のテストと実際のブラウザの axe で確かめる（make-you-chic-ui のテストはこのリポジトリの CI の対象外のため）。axe は、展開した状態・深い階層・長い名前で、ブランドカラーとテーマのすべての組について行う。\n- 利用者へメールを送る機能では、Code Style と `project.md` のメールの決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。★印の項目は要件が未確定のため、要件定義で決めてからテストを書く。\n  - 本文のエスケープ: 差し込む利用者の値（氏名・メールアドレスなど）に `<`・`>`・`&`・`\"`・`'` を含めても、タグや属性としてそのまま出ないこと。利用者の値をエスケープしない差し込み（`{{{ }}}`・`{{& }}`）で入れていないこと、属性の値を二重引用符で囲んでいること（エンジンの `{{ }}` は `'` を置き換えないため）\n  - テンプレートの描画: 日本語と英語のすべてのテンプレートを描けて、件名が空でないこと、差し込み漏れ（`{{` の残り）が無いこと、ライセンスヘッダーが描いた本文に出ないこと\n  - 招待の URL: 設定したベース URL だけから組み立て、要求の Host ヘッダーから組み立てないこと。ベース URL の設定が無いときに要求の Host を変えても、招待の URL に出ないこと。★ベース URL の設定が無いときの動作\n  - トークンと URL の漏えい: 招待のトークンと招待の URL が、アプリのログ・監査ログ・トレースの属性・エラー応答に含まれないこと（既存の `*SecretLeakIT` と同じ形で確かめる）\n  - ヘッダーへの差し込み: 宛先・件名・差し込む値に改行（CR・LF）を含めても、メールのヘッダーが増えず拒否されること\n  - 送信の失敗: 送信が失敗したとき（受け手が接続を拒む・応答しない）に、宛先のメールアドレス・SMTP の応答・資格情報が応答とログに含まれないこと。★送信が失敗したときの業務の動作（招待を失敗させるか、再送に回すか）\n- 利用者が投入する DSL（YAML）を読み込む機能では、Code Style の「信頼できない入力」の決まりを確かめる次のテストを必ず書く（認証・認可・監査と同じ扱い）。上限の具体的な数値は設計の段で決め、決めた値の境界で確かめる。\n  - 大きさ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 入れ子の深さ: 上限ちょうどは受け付け、上限を超えると拒否される\n  - 別名（アンカー）: 展開の数が上限を超えると拒否され、別名の展開の爆発で処理が止まらない\n  - タグ: 任意の型を作るタグ（`!!` など）を含む DSL は拒否され、型が作られない\n  - 重複キー: 同じキーが重なる DSL はエラーになり、後の値で黙って上書きされない\n  - JSON Schema の `$ref`: 外部の URL を取りに行かない\n  - 拒否の応答: Problem Details の形で返り、YAML・JSON Schema の部品の例外のメッセージを含まない\n- 性能・資源の目標の測り方（物差し）を変えるのは、要件定義で依頼者が明示に決めたときだけとし、変える理由を記録する。変えた後も、少なくとも1回は前の物差しの値を並べて記録する。\n- 負荷の試験の使い捨ての環境の対象DB の行は、perf の台本（`perf/` の下）で決めた種から作る（行の数は NFR 要件の段で決める）。k6 の場面の setup は、前の場面が残した状態に頼らない。\n\n- 内部DB（組み込みの H2）を使うテストは、コンテナではなく本番と同じ組み込みの H2 で行う。Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う。Walking Skeleton の「DB を使うテスト1件以上（本番と同じ種類の DB をコンテナで起動する）」も、内部DBについてはこの読み方とする。 (learned 2026-09-22) \n\n- 他テーブル参照（REFERENCE・LOOKUP）の機能では、次のテストを必ず書く（業務データの一覧・詳細と同じ扱い）。参照先の権限の当て方（参照元の権限だけを持ち参照先の権限を欠く利用者の結果）は要件定義で決め、決めた動作を明示したテストにする。具体的な上限の値は設計の段で決め、決めた値の境界で確かめる。\n  - (a) 参照先の読み取りの認可: 参照先を読む API は、未認証（401）・要る権限だけを欠く利用者（403）・権限あり（200）をサーバー側のテストで、パラメーターを使う表として確かめる。参照元の権限だけを持ち参照先の権限を欠く利用者には、選択肢を返さない（参照先の主権限が NONE のカラムも応答に含めない）。選択肢の取り出しの件数と検索の上限を置き、上限の境界（上限ちょうどは受け付け、超えると拒否）と対象DB の時間切れの応答を確かめる。\n  - (b) 識別子の照合: 参照先のスキーマ・テーブル・値のカラム・表示のカラム・検索の列は、読み出しのたびに適用中の DSL の名前と照らす。DSL に無い名前に差し替えた要求は拒否され、状態が変わらないこと。\n  - (c) 件数と時間切れ: 選択肢・ピッカーの取り出しの件数の上限と、JDBC の取り出しの上限を置き、上限を超える行を内部で読まないこと。\n  - (d) 漏えい: 参照先の行の値・ラベル・主キーの値と対象DB の接続情報が、アプリのログ（TRACE を有効にした `XxxSecretLeakIT`）・トレースの属性・エラー応答に含まれないこと。参照の値を道（URL）に載せない。\n  - (e) 検索のエスケープ: ピッカーの部分一致の検索で、`%`・`_`・エスケープの文字が一致の条件として働かないこと。\n  - (f) 画面に出す値: ラベルと値を信頼できない入力として扱い、`<script>`・`<img onerror>`・`\"`・`'`・`javascript:` を含めても、文字として出る（HTML として描かない、リンクの先にしない）こと。\n  - (g) 参照元の書き込みでの検証: 参照先に存在しない値、権限の無い参照先の値、他の参照先を指す差し替え（IDOR）は拒否され、状態が変わらないこと。\n  - (h) 参照先の主キーの形: 複合の主キー・主キーの無いテーブル・ビュー・自己参照のときの扱いを、決めた側の動作を明示したテストにする。\n  - (i) 3種類の対象DB: 選択肢の取り出しを MySQL・MariaDB・PostgreSQL のすべてで結合テストで確かめる。識別子の大文字と小文字・引用符・NULL の並びと、境界の値を含める。\n  - (j) 接続の数: 選択肢の取り出しが1要求で使う対象DB の接続と内部DB の接続の数を、既存の `*ConnectionUsageIT` と同じ形の結合テストで決定的に確かめる。\n  - (k) 境界テストの許可の一覧: `java.sql.Connection` の `prepareStatement`・`createStatement` を呼んでよい場所、SpotBugs の除外、既存の境界テストが、参照先の本実装の前後で緩まず変わらないこと。\n- 参照ピッカー（LOOKUP）の画面の E2E を、この Intent の機能の代表の流れとして1本足す。流れは、自分で作った参照先と参照元の前提（利用者・役割・専用のスキーマの行）だけを対象にし、見本の行と初期管理者を変えない。axe は、ピッカーを開いた状態・長い名前で、ブランドカラーとテーマのすべての組について行う。ピッカーが make-you-chic-ui の部品でないときは、キーボード操作と `aria` の属性を画面のテストで確かめる。参照先の権限を欠く利用者の確かめは E2E ではなく結合テストで行う。i18next 系の更新と既存の E2E の書き換えは本数に数えない。\n- 負荷の試験（k6 の `--summary-export` など）の結果に入る認証の値（JWT・リフレッシュトークン・Authorization ヘッダー）を伏せる仕組みを入れるときは、仕組みそのものに伏せ字のテスト（JWT の形が残らない、すでに伏せた入力に流しても結果が変わらない（冪等）、入れ子の JSON・配列・長い文字列の中でも伏せる、JWT 以外の値（数値・場面名）を壊さない）を付け、`./gradlew verify` と CI に入れる。記録（`evidence/`）に置いたものには `gitleaks dir` を流して確かめる。仕組みの置き場は設計の段で決める。"
    },
    {
      "layer": "project",
      "text": "- テストの件数やカバレッジを報告するときは、`./gradlew verify` がテストのタスクを UP-TO-DATE で飛ばすことがあるため、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実行し直し、実測の数字だけを報告する。 (learned 2026-09-23) \n- 負荷の環境や配備先が決まらないと測れない目標（応答時間のパーセンタイル、運用の指標、ファイルの権限など）は、Build and Test で `Unverified` とし、持ち主の段（performance-validation・observability-setup・deployment-execution）を明記して引き継ぐ。目標を緩めて「満たした」ことにはしない。 (learned 2026-09-23) \n- 負荷の試験は、配備した環境とは別の使い捨ての環境（仮の署名鍵・仮の利用者、終わったら消す）で行い、本物のデータと監査ログを汚さない。手順は perf/README.md。 (learned 2026-09-23) \n- 負荷の試験で、アプリが止まる・極端に遅いなどの結果が出たときは、環境を起動し直して再現させ、原因をログと状態（OOMKilled など）で確かめてから記録する。 (learned 2026-09-23) \n- 同時の重なりを確実に作るため、本番のコードを変えずに、監査の書き込みの時間を測る LongSupplier（AuditEventListener で2本目を借りる直前に呼ばれる）をテストで差し替えて待ち合わせる方式にした。既存の LoginConcurrencyIT は 8 スレッドでプールの 10 に届かず、前回の失敗のログインで尽きなかった理由の1つと見られる。 (learned 2026-09-23) \n- Intent の流れに Performance Validation の段が無く、負荷の環境（使い捨ての環境）を手元で用意できるときは、k6 の試験と NMT の測定の持ち主を Build and Test とし、Unverified で引き継がずにその段で実行する。 (learned 2026-09-23) \n- 修正の前の設定（例: 上限 1g）も修正の後の環境（例: CPU 4 の VM）で流し（pre1g）、要件の前提（VM を上げても F3 が起きる）を実測で裏付ける。 (learned 2026-09-23) \n- パッケージごとのカバレッジの下限と SpotBugs の SQL_ の関門を U1 の計画に入れた。team.md の決まりだが今のビルドに無く、この Intent で最初に作る単位のため。U1 の設計の文書には無い作業。 (learned 2026-09-24) \n- U4 で既存の AuditSecretLeakIT の列の一覧に V6 の4列を足し、テストの JVM のヒープを 1g にした。前者は承認済みの V6 と必ず食い違うため、後者は構造の検査がクラスを持ち続け U3 の 10MB 超えのテストでヒープが尽きたため。どちらも計画に無い変更で、依頼者に確かめる。 (learned 2026-09-24) \n- 応答しない対象DB の TIMEOUT の確かめに、テストの中で開いた ServerSocket（受け付けて何も返さない）を使う。外の端末に頼らず確実に再現できる代わりに、本物の DB の遅延ではない。 (learned 2026-09-24) \n- パッケージごとのカバレッジの下限は新しいパッケージだけに当てた。実測で audit.service（行 77.2%）・common.health（行 79.2%）・auth.repository（分岐 50.0%）が単独で下回ったため（team.md の決まりどおり）。既存の 22 パッケージを一覧で外し、新しいパッケージは自動で対象になる。 (learned 2026-09-24) \n- 負荷の試験で接続プールが尽きたかを確かめるときは、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics の hikaricp の値を読み、数秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断する（配備したアプリの公開の範囲は変えない）。 (learned 2026-09-24) \n- k6 などの長い試験は caffeinate -i を付けて流し、PC の自動のスリープで要求が止まって結果が崩れるのを防ぐ（バッテリー駆動のまま 414 秒スリープし、要求が 6分52秒止まった）。内部DB に SQL で直接入れた試験用の利用者はロックの状態の行が無いため、同時のログインを流す前に1人ずつログインさせて行を作る。 (learned 2026-09-24) \n- 軽い API の性能は、投入を重ねて内部DB のファイルが膨らんだ状態（悪い側の条件）のまま測る。メモリの最大（memory.peak）を比べる試験の前は、アプリのコンテナを作り直して前の最大の値を消す。 (learned 2026-09-24) \n- Performance Validation の段が無いため、2件目の直しの負荷の試験での確かめ（Q5: A）の持ち主を Build and Test とした（project.md の学びどおり）。 (learned 2026-09-24) \n- colima の PC で ./gradlew verify を流すときは、README の DOCKER_HOST と TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE をシェルに渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗した。付けて流し直した結果を基準とした。 (learned 2026-09-25) \n- unit-test-instructions.md の k6 inspect のコマンドは --include-system-env-vars が無いと場面の名前が undefined になり確かめにならなかった。承認済みの文書は変えず、実際には付けて流し、code-summary.md に差を記録した。 (learned 2026-09-25) \n- 1回目の verify で AccessTokenApiIT の 6 件が接続の失敗で落ちた。変更の経路に触れず、同じ時刻に Gradle の作業プロセスとの接続も時間切れだったため PC の負荷による一時的な失敗と見立て、段の中の直しの1回目としてコードを変えずに verify を流し直して通した。原因は確かめていない。 (learned 2026-09-25) \n- 依頼者の判断で、試験のイメージを mastersmith:local ではなく mastersmith:followup-fixes で作り、perf/README の手順（local を作り直す）から外れた。配備したアプリは k6 のあいだ止め、約 17 分後に同じコンテナで起動し直した。 (learned 2026-09-25) \n- 試験の後にロックの状態の行の数を数える問い合わせの列の名前を誤り、使い捨ての環境を消した後で取り直せなかった。合格の条件（ログインの checks と 500 の件数）で判定した。消す前に確かめの結果を見てから片付けるべきだった。 (learned 2026-09-25) \n- Q4（確かめられたときだけ直す）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違いうるため、追加の質問 F3 で、再現できなければ不安定と確かめられていない扱いとして統合してよいことを確かめた。 (learned 2026-09-25) \n- 要件 FR2.2（目標は計画の承認の場で決める）を満たすため、依頼者の決定（Q1: A）で、計画を書く前に dslMixed でメモリの内訳を測ることにした。測る間は配備したアプリを止める。 (learned 2026-09-25) \n- 生成で、テストの既定で MBean の登録を無効にする置き場を、計画の候補の TestDatabase ではなくテストの EnvironmentPostProcessor にした。TestDatabase を使わずに Spring を起動するテストのクラスが 15 あり、漏れるため。再現の結合テストは 12 回では伸びが小さく判定がはっきりしないため、24 回・履歴の上限 3 にした。 (learned 2026-09-25) \n- AccessTokenApiIT は、同じ例外の文言を仕組み（colima の IPv4 の転送と Java の IPv6 の待ち受けの番号の重なり）ごと再現できたことを原因の確認とみなし、依頼者の決定（D7）でテストの JVM に preferIPv4Stack を付けて直した。実際の1回目がこれで起きたかは確かめられず、今回の繰り返しでは重なりは起きなかった。 (learned 2026-09-25) \n- Test Strategy は Minimal だが、要件 FR5 と計画の「Build and Test に引き継ぐこと」のため、結合・性能・セキュリティの手順書も作り、この段で dslMixed・--storage --compact（40 回）・詰め直しの最中のログイン・refresh・dslCycle を流した。 (learned 2026-09-25) \n- 計画の dslCycle を最初の台本に入れ忘れ、配備したアプリをもう一度（約 4 分）止めて流した。負荷の試験の台本を書く前に、計画の「Build and Test に引き継ぐこと」の項目を一つずつ台本の手順と突き合わせる。 (learned 2026-09-25) \n- 実際のブラウザのアクセシビリティの検査（axe）は、誤りを出した状態と、現実に近いデータ（2語の氏名など）で、ブランドカラーとテーマのすべての組について行う。user-management の U7 の 080 で初めて、make-you-chic-ui の Avatar（頭文字が2文字のとき）と dark の FormField の誤りの文字のコントラスト不足が出た（050〜070 は誤りの状態を見ず、氏名が1語だったため出なかった）。 (learned 2026-09-28) \n- Playwright の webServer.env に置いた値は json の報告に残る。E2E の仮の資格情報はプロセスの環境変数で渡し、報告の部品の確かめと json の文字列の検索で、パスワード・トークン・メールアドレスが入っていないことを確かめる（user-management の U4 で見つかった）。 (learned 2026-09-28) \n- CI（66fe981）の verify が2回とも別々のテストの時間切れ（H2CompactionByPoolSuspensionIT の接続の待ち 10 秒、InvitationAdminPage.test.tsx の既定 5 秒）で失敗し、U4〜U7 の NFR7.1 が Not Met だったが、依頼者の決定「次のintentでコントラストと一緒に直す。このintentではこのまま進める。」で受け入れた失敗とした。team.md の「CI が失敗したら次に進む前に直す」と食い違うため test-results.md 8.1 に差を記録した。 (learned 2026-09-28) \n- k6 の登録の完了と取り消しの場面は、決定の文言（招待を VU の数だけ）ではなく既定 100 回・招待 100 件とした。トークンは1回しか使えず 10 件では p95 の意味が薄いため、U3 の NFR6.4 の「流す回数以上の招待を用意する」に合わせた。承認の場で確かめる。 (learned 2026-09-28) \n- caffeinate -i は場面ごとではなく、試験の台本全体を包む形で付ける。場面ごとに起こし直すと切れ目で守りが外れ、PC が眠る（user-management の Performance Validation で 92 秒眠り、要求が 1 分 29 秒遅れた）。遅れが出たときは pmset -g log でスリープを確かめる。 (learned 2026-09-28) \n- 応答しないメールの受け手は、Mailpit を docker pause で一時停止して作る（接続は受け付け、何も返さない）。コンテナを止めると接続の失敗になり、時間切れの確かめにならない（user-management の Performance Validation）。 (learned 2026-09-28) \n- Q1 で原因を確かめずに上限を延ばす答え（C）が team.md の「不安定なテストは原因を直すまで統合しない」と食い違うため追加の質問 F1 で確かめ、延ばしたうえで失敗時の診断を足し、差を記録する形（B）になった。Q3 の「すべて試す」は verify で判定できない Temurin 26・logback-appender の扱いを F2 で確かめ、見送り（C）になった。 (learned 2026-09-29) \n- 診断の確かめで、上限 1 ミリ秒は Awaitility の問い合わせの間隔（100 ミリ秒）より短く設定の誤りで弾かれ、150 ミリ秒では手元で接続がすぐ 0 本になり時間切れにならなかった。上限 500 ミリ秒と、待ちの条件を一時的に満たせない形（== -1）にして時間切れを起こした。確かめた後に git checkout で戻した。 (learned 2026-09-29) \n- 警報が鳴ることは、しきい値を 50 ms に下げた警報の決まりの写しを使い捨ての環境に読み込ませて確かめた（Q1: A）。本当に遅い応答を作るより早く PC の負荷も小さいが、しきい値の値そのもので鳴ることは確かめていない。途中で ms-check-p95 のしきい値が 1000 ms ではなく 300 ms（バケットの境界に無い）と分かり、要件の前提 A3 と食い違った。 (learned 2026-09-29) \n- 表示の設定を画面の操作の直後に同期で確かめるテストが、負荷の高い CI で2件（ShellLayout・PreferencesPage）落ちた。描画の後の効果（useEffect）で反映される値は waitFor で待って確かめる。同じ形の RegistrationPage も合わせて直した。 (learned 2026-09-29) \n- Delivery Planning でのカバレッジの実測は、./gradlew verify 全体ではなく :backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport で行い、jacocoTestReport.xml から手を入れる見込みのパッケージの値を読む（user-admin で約 5 分）。 (learned 2026-10-01) \n- 接続プールの見積もりを確かめる負荷の試験には、上限に届く形（プールの上限を下げた場面など）を含める。上限に届かない負荷では、見積もりが誤っていても合格する（user-admin の U3 の NFR 要件のレビュー R-02）。 (learned 2026-10-01) \n- 画面のはみ出しの確かめ（E2E・axe）では、画面全体の横のスクロールだけでなく、開いたメニュー・ポップアップなど画面に固定で置く部品が画面の中に収まることも確かめる。表の右端に置く make-you-chic-ui の Dropdown は placement に bottom-end を指定する（user-admin の配備の後のスモークテストで、行の「操作」のメニューが右へはみ出していたのを E2E 120 が拾えなかった）。 (learned 2026-10-03) \n- k6 の http_req_duration が、colima の VM の時計が負荷の間にホストより約 2 秒ずれて合わせ直されるため一部崩れた（待ちの最小 0 ms、繰り返しより長い約 3 秒の値）。1回の繰り返しが要求1つの loginSuccess では、単調な時計の iteration_duration とサーバー側の最大が合うため、判定は iteration_duration で行い、http_req_duration は並べて記録した。前の Intent の 939.6 ms も同じ影響を受けていた可能性がある。 (learned 2026-10-04) \n- 1〜6回目で繰り返しの時間の p95 が2回 1 秒を超え、依頼者が VM の時計を合わせて測り直しを求めた（F1・F2 の Other）。測り直しの7〜9回目は 1 秒を下回ったため、1〜6回目を FR2.2a の条件がそろわない回として外した（承認の場で確かめる）。時計のずれを ssh の前後のホスト時刻の中間と比べる誤った測り方で 2.2 秒と読み、測り方を直して範囲（VM−終わり、VM−始め）で記録し直した。配備したアプリは2回止めた（約 12 分と約 4 分）。 (learned 2026-10-04) \n- k6 の判定は1回の繰り返しに要求1つの場面の iteration_duration で行い、トークンは setup() で取って場面を 3 分にした。時計のずれに強い代わりに、操作が2つ以上の場面は op のタグが iteration_duration に付くかを台本で確かめる必要がある（group のレビューの R-01 を先に避けた）。 (learned 2026-10-05) \n- 上限を下げた k6 の場面を置かず、接続の数は結合テスト NavigationConnectionUsageIT で決定的に確かめる。読み取りだけで1要求1本のため詰まりが起きず、role のレビューの R-02 の「k6 では見積もりの誤りを見分けにくい」を避けられる。代わりに、負荷の下での待ちの長さは測らない。 (learned 2026-10-05) \n- 違反のテストは先の側を確定させてから書く待たない違反、上限切れは H2 の上限まで放さない形にした（Q3: A）。時間の境に合否を預けない代わりに、待った後の違反の経路は区分の単体テストだけで確かめる。 (learned 2026-10-06) \n- 長いテスト（verify・E2E・k6）は caffeinate -dims で包み、流す前に pmset -g assertions で眠らない状態か、流した後に pmset -g log で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す。DarkWake の Thermal Emergency のスリープは caffeinate -i では止められず、role-menu の B8 で画面のテストが約 660 秒止まって時間切れになった。 (learned 2026-10-13) \n- コード生成の計画の段で、変える型・表・移行の番号・API・画面の骨組みを使っている既存のテストと E2E を検索で洗い出し、影響の範囲に入れる。role-menu では移行の最後の版を前提にしたテスト（AuditMigrationCompatibilityIT）、SQL の文の数を数えるテスト（GroupAdminListQueryCountIT）、狭い幅のはみ出し（E2E の 375px）で計画の外の失敗が起き、生成が止まった。 (learned 2026-10-13) \n- 木の画面の単体テスト・E2E・スモークテストでは、本物と同じ形の応答（子の有無の印を含む）を使い、すべての段の開閉を押して確かめる（role-menu の配備の後のスモークテストで、「自分の権限を見る」の木でテーブルの開閉を押すと自分自身を子に持って固まる不具合が見つかった。単体の見本のテーブルは子が無い形で本物と違い、E2E は選ぶだけで開閉を押していなかった）。 (learned 2026-10-14) \n- 画面の不具合を使い捨ての環境で再現するときは、依頼者がした操作（どのボタン・どの開閉を押したか、画面の文言とその場所）を先に聞き、同じ操作を試す。role-menu の S8 で、選ぶだけで開閉を押さずに再現せず「環境の差」と誤って見立て、依頼者の再試行で条件が分かった。 (learned 2026-10-14) \n- k6 の場面の setup は、前の場面が残した状態（ロールの割り当てなど）に頼らず、自分で前提を作るか片付けてから始める形で書く（role-menu の Performance Validation で、workRoleRead・myPermissionsTree・roleAssignmentsRead が前の場面の割り当てで 409 ROLE_NO_CHANGE や確かめの不一致になり、割り当てを外して流し直した）。 (learned 2026-10-15) \n- 接続の待ちの最大のように数 ms の目標は1回の値が揺れるため、合否の回を複数回流すことを負荷の試験の計画に入れる（role-menu の U4-NFR2.7b で1回目 23.55 ms、作り直した流し直しで 8.55 ms となり、原因を確かめないまま Not Met で持ち越した）。 (learned 2026-10-15) \n- 大きな入力（10 MiB の DSL の投入など）のヒープの見積もりは、設計の段で試しの測定で裏付ける（role-menu の Performance Validation で GC の後でも 573〜746 MB あり、見積もりの 256〜384 MB を超えた。dslMixed の memory.peak は上限 2g の 87%）。 (learned 2026-10-15) \n- 依頼者の決定で、E2E をブランチごとの統合の前ではなく Intent の最後（Build and Test）に1回だけ流すことにした。① と ② は統合の前に流して通し、③・④ は ./gradlew verify だけで統合した。team.md の「画面・認証に関わる変更は統合の前に E2E」との差。 (learned 2026-10-15) \n- CI の 776738e の1回目が AdminForbiddenProvider.test.tsx の1件（描画の後の効果の回数を、効果が済む前に数える形）で落ち、2回目の試行で通った。手元で10回流して再現しなかったため、team.md の「手元で再現しないものは CI の再実行で通れば進めてよい」の扱いとし、見立てを記録した（同じテストが二度落ちたら直す）。 (learned 2026-10-15) \n- dslMixed の memory.peak が 94.4%・取り直し 93.1% で線（90% 未満）を超えた。peak の時の内訳は anon 68.3%・回収できるページキャッシュ 23.7% で、依頼者の決定「持ち越す」で設定を変えず Not Met のまま記録した。物差しを anon に替えると Met になるが、目標の測り方を後から緩めることになるため選ばなかった。 (learned 2026-10-15) \n- 1回目の使い捨てのアプリのログの WARN 43 行を、内訳を読む前に環境を消したため取り直せなかった（project.md の「確かめの結果を見てから片付ける」の漏れ）。memory.peak の取り直しの回では、消す前に WARN を数える指示を最初から書いた。 (learned 2026-10-15) \n- business-data-views の Delivery Planning の実測（約 13 分）で、全体の合計で判定している7パッケージのうち下限に足りないのは common.health（行 79.2%）だけで、audit.repository は数える行が無かった（全体の合計 行 98.0%・分岐 92.6%）。 (learned 2026-10-18) \n- U1・U2・U3 で捨ての試しを先に行い、前提が崩れた点（U1 は MySQL・MariaDB の重さ、応答しない MySQL の 50 秒、bit と空間の型。U2 は perf の MySQL の OOMKilled、PG の TEMP の権限、更新できないビュー、JDK 25 で重くならない式。U3 は測り終わりの時点、T3 が代わりの案にならない）を追加の質問（F）で依頼者に確かめてから文書に反映した。 (learned 2026-10-18) \n- U2 の Q7: A で perf の MySQL 1792m を短い捨ての試しで確かめ、プールを埋めた状態で上限に張りつき（anon 90%）、プール以外の量も見立ての約 614 MiB でなく約 840 MiB と分かったため、追加の質問 F1 で 2304m にした。U1 の文書の値は承認の場でそろえる。 (learned 2026-10-18) \n- U1 のコードのレビューの R-01（承認の場で直す決定）を、段の承認の場の前に直した: U1 の traceability.json の NFR1.1・NFR1.2・NFR1.3・NFR2.4・NFR3.2 を OK から Deferred（Performance Validation）にし、target に場面と台本・設定の置き場を書いた（AC1.3.7 と同じ扱い）。測っていない目標を満たした扱いで読まないため。 (learned 2026-10-20) \n- 依頼者の決定（Q1: B）で、B6 と CI の直しを合わせた develop の今の形では verify と E2E の全件を流さず、各 Bolt の作業ブランチの上の結果と CI（d220447 の1回目で成功）を正とした。流す時間（約 40 分）を省く代わりに、合わせた形の手元の確かめは CI だけになる。 (learned 2026-10-20) \n- 対象DB を docker pause で止めても、503 は BUSINESS_TARGET_DB_TIMEOUT ではなく、すべて BUSINESS_TARGET_DB_UNAVAILABLE（借りる待ち 3 秒の時間切れ）になった。接続の確かめに失敗して作り直す接続が借りられないため。TIMEOUT の経路は、つないだ接続で問い合わせの最中に止めないと起きない見込み。 (learned 2026-10-25) \n- develop の 9292432 の CI の1回目が frontend/src/features/auth/logoutIntent.test.tsx「leaves the intent while staying on the same path and clears it on the next path change」で落ちた（2回目の試行で通過）。同じテストは B7 のレビューの直しの後の verify でも落ちており二度目のため、team.md の決まりと依頼者の決定 A で、この段の途中で短命のブランチ fix/261016-business-data-views-logout-intent で直した。手順の入れ替え（最初の描画の効果の前に印を立てる）で CI と同じ形（peekLogoutIntent が偽）を3回とも起こし、最初の描画の「印を消す」効果が印より後に流れることが原因と確かめた。直しは、印を立てる前に効果が流れ終わったことを待つ形（テストだけの変更）。負荷で描画が分かれて効果が遅れること自体は起こせていない（見立て）。直した後は単独 10 回・画面のテストの全件 1 回が通過。 (learned 2026-10-25) \n- k6 の summary json には使い捨ての環境のアクセストークン（JWT、eyJ… の形）が入る。記録（evidence/）に置く前に [REDACTED-JWT] に置き換え、資格情報の確認は token の語だけでなく eyJ… の形の検索と gitleaks dir でも行う（perf-targets-review の Build and Test で 12 ファイル 120 件が記録に入り、コミット前の verify は履歴を見る gitleaksScan を通ったが、プッシュ後の CI で落ちて amend と force-push になった）。 (learned 2026-10-29)"
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Keep the existing test suite green.",
      "This scope adds no extra new-test floor beyond the selected test strategy."
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
  "input_sha256": "sha256:52ac142afb0d7f13a2fc2b10d150c41842aa2b99323600f98919badc668b59a0",
  "contract_sha256": "sha256:24838a1efe7aa8c479269b6ab9cc20fe64d40a37da95bdd7679a3990f269e229"
}
```

この単位の層の順（plan_profile の当てはめ）: 「Data model / database behavior」は内部DB の表を持たないため当たらない（移行が無い）。「Frontend behavior」は画面を持たないため当たらない。残りを、targetdb（設定と読み取りの口）→ businessdata.domain → repository → service → web の順に、各層を実装した後にその層のテストを書いて流し、通ってから次の層へ進む。

## 3. 新しいクラス・口と、依頼者の承認が要るもの

### 3.1 新しいクラス（BR11.3・ADR-001。計画の承認で承認を得る）

| パッケージ | クラス | Bolt | 役目 |
|---|---|---|---|
| `businessdata.domain` | `DefinitionPurpose`（enum `VIEW`・`CREATE`・`UPDATE`） | B1 | 定義の用途 |
| 同 | `ReferenceTarget`・`ReferenceStatus`・`ReferenceField`・`ReferenceOption`・`PickerInfo`・`PickerSearchField`・`PickerListColumn` | B1 | 参照先と定義の参照の項目（entities.md） |
| 同 | `ReferenceReadSpec` | B1 | `search` に渡す指定（値のカラム・表示の列・NULL の除外・長さの条件 10,000・固定の並び・件数の文の有無・読む行・読み飛ばす数。BR11.4・performance-design.md 1.1） |
| 同 | `ReferencePolicy`（final class、static の純粋な関数） | B1 | `accessOf`・使える条件を選ぶ・同じ値を落とす・固定の並びを作る・`sameReferenceValue`（jqwik の対象） |
| 同 | `ReferenceReadDeadline`・`ReferenceReadFailure`・`ReferenceFailureReason` | B1 | U2 との継ぎ目（reliability-design.md 5節） |
| 同 | `ReferenceRowsQuery`・`ReferenceCondition`・`ReferenceValue`・`ReferenceRows`・`ReferenceRow`・`ReferenceCheckResult`・`ReferenceCountState`・`ReferenceRowsRequestValidator` | B2 | 検索とラベルの要求・応答、U2 向けの結果の型、本文の形の確かめ（BR1.4） |
| `businessdata.service` | `ReferenceLookup` | B1（定義の分）・B2（検索・ラベル・`check`・`countState`） | 流れ・締め切り・失敗の写し・診断の WARN の1行。トランザクションを開かない |
| 同 | `ReferenceRowsResult`（sealed） | B2 | 検索・ラベルの結果（行・同じ応答の拒否・値の型の 400・失敗） |
| `businessdata.repository` | `RowSearchResult.Rows`（既存の sealed interface に場合を1つ足す） | B1 | 件数を数えない読み取りの結果 |
| `businessdata.web` | `ReferenceFieldResponse`・`ReferenceOptionResponse`・`PickerInfoResponse`・`PickerFieldResponse` | B1 | 定義の応答の `reference` |
| 同 | `ReferenceRowsRequest`・`ReferenceConditionRequest`・`ReferenceRowsResponse`・`ReferenceRowResponse` | B2 | 検索 API の要求と応答（`toString` で値を伏せる） |
| テスト（`businessdata/testsupport`） | `ReferenceTables` | B1 | 参照先・参照元の表をクラスごとの一意のスキーマに作って消す |

値を持つ型（`ReferenceOption`・`ReferenceRow`・`ReferenceRows`・`ReferenceRowsQuery`・`ReferenceCondition`・`ReferenceValue`・`ReferenceField`・web の要求と応答）は `toString` を上書きして値を伏せる（BR10.1）。`ReferenceTarget` はテーブルの名前と kind だけを出す。

### 3.2 既存のクラスに足す口・変える形

| クラス | 変えること | Bolt |
|---|---|---|
| `targetdb.domain.ReadPurpose` | `REFERENCE` を足す | B1 |
| `targetdb.config.TargetDbProperties` | `QueryTimeout.reference`（既定 5s、`longest()` に入れる）、`referenceReadTotal`（既定 10s） | B1 |
| `targetdb.config.TargetDbSettings` | `QUERY_TIMEOUT_REFERENCE`・`REFERENCE_READ_TOTAL` の点検（1 秒未満は `Invalid`、項目名だけ WARN、起動は続く）、`Usable.queryTimeout` の `case REFERENCE`、`Usable.referenceReadTotal()` | B1 |
| `targetdb.service.TargetReadAccess`・`JdbcTargetReadAccess` | `withRead` が `REFERENCE` を受ける。`Duration referenceReadTotal()` を足す（`withRead` の work の中でだけ呼ぶ。`Usable` でないときは `IllegalStateException`。NFR 設計 U1 R-05） | B1 |
| `businessdata.repository.RowDialect`・`MysqlFamilyRowDialect`・`PostgresRowDialect` | `lengthWithin`・`sortExpression` を足す（式を作る口。新しい SQL のメソッドではない） | B1 |
| `RowSqlExecutor.search` | 引数に参照の読み取りの指定（`Optional<ReferenceReadSpec>`）を足す。メソッドの数は増やさない。無いときは今と同じ文（BR12.4） | B1 |
| `RowSqlBuilder` | 指定があるときの選ぶ列・条件・並び・`LIMIT`／`OFFSET` | B1・B2 |
| `TableAccessResolver`・`TableAccessResolution.Resolved` | 取った `PermissionSnapshot` を判定の材料と一緒に返す。同じ写しで参照先の判定の材料を作る口を足す（BR2.2） | B1 |
| `BusinessDataReadService`・`DefinitionResult` | `purpose` を受け、`CREATE`・`UPDATE` で `ReferenceLookup` を呼ぶ | B1 |
| `DefinitionRequest`・`ColumnDefinitionResponse`・`TableDefinitionResponse` | 任意の `purpose`、カラムの `reference`（`VIEW` では `null`）、`CREATE`・`UPDATE` では `formPart` を DSL の値で返す（D2） | B1 |
| `BusinessDataReadController`・`BusinessDataPaths`・`BusinessDataWebConfig` | 検索 API のメソッドを既存のコントローラーに足す（クラスの `@ApiAccess(AUTHENTICATED)` に入る）、道 `REFERENCE_ROWS`、本文の上限 65,536 バイトの道 | B2 |

### 3.3 境界テスト（ArchUnit）と SpotBugs の除外

- **変えない。** `BusinessDataBoundaryArchitectureTest`・`TargetDbBoundaryArchitectureTest`・`RoleBoundaryArchitectureTest`・`ArchitectureTest`・`ApiAccessArchitectureTest` を緩めず、規則も足さない（今の規則が新しいクラスにそのまま当たる。`businessdata` → `targetdb.service`・`targetdb.domain`・`role`・`dsl` は今も許されている）。`prepareStatement`・`createStatement` を呼ぶ場所は `RowSqlExecutor.search`・`detail` と書き込みの5つのまま増えない。
- `backend/config/spotbugs-exclude.xml` は変えない。`search` の本体はすでに除外の範囲（クラス・メソッドの名前・パターン名）にある。`spotbugsGate` で新しい `SQL_` の指摘が出たら、除外を足さずに組み立ての形を直す。直せないときは止めて、依頼者の承認を求める（`team.md`）。
- 確かめ: 各 Bolt の終わりに `git diff develop -- backend/config/spotbugs-exclude.xml backend/src/test/java/cherry/mastersmith/**/*ArchitectureTest.java` が空であること。

### 3.4 `packagesJudgedByTotal` との突き合わせ（NFR5.2）

手を入れるパッケージ: `businessdata.domain`・`businessdata.service`・`businessdata.repository`・`businessdata.web`・`targetdb.config`・`targetdb.domain`・`targetdb.service`（Java 以外は `perf/`・`README.md`・`.env.example`・`application.yaml`）。`backend/build.gradle.kts` の一覧の7パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）には**当たらない**（`common.web` の `RequestBodyLimitRoute` は使うだけで変えない）。そのため一覧から外す作業と事前の実測は無い。手を入れる7パッケージはすでにパッケージごとの下限（行 80%・分岐 70%）の対象で、Bolt の終わりの `verify` の jacoco の報告で値を読んで記録する。各 Bolt の終わりに `git diff --name-only develop...HEAD` で一覧のパッケージに触れていないことを確かめ直す（触れていたら止めて指揮役に伝える）。

## 4. 影響を受ける既存のテスト（検索で洗い出したもの。`project.md` の学び）

| 変えるもの | 影響を受けるテスト | 扱い |
|---|---|---|
| `TargetDbProperties`・`QueryTimeout` の構成子 | `TargetDbPropertiesTest`・`TargetDbSettingsTest`・`TargetDataSourceConfigTest`・`TargetDbWriteSettingsTest`・`TargetWriteDataSourceConfigTest`・`JdbcTargetReadAccessTest`・`JdbcTargetWriteAccessTest`・`TargetSchemaReaderTest` | 引数を足して直す（中身の確かめは変えない） |
| `TargetReadAccess` に口を足す | `businessdata/testsupport/FakeBusinessDataPorts` | 差し替えの実装に `referenceReadTotal()` を足す |
| `RowSqlExecutor.search` の引数 | `RowSqlExecutorTest`、`BusinessDataReadService` | 既定（指定なし）を渡す形に直す。文の文字は変わらないことを確かめる |
| `TableAccessResolution.Resolved` | `TableAccessResolverTest`・`BusinessDataReadServiceTest`・`BusinessDataWriteService`（本体）・`FakeBusinessDataPorts` | 写しを持つ形に直す。書き込みの振る舞いは変えない |
| `BusinessDataReadService` の構成子・`DefinitionResult` | `BusinessDataReadServiceTest`・`BusinessDataServiceFixture`・`BusinessDataReadControllerTest` | `ReferenceLookup` と `Clock` を渡す |
| `DefinitionRequest`・`ColumnDefinitionResponse` | `BusinessDataReadControllerTest`・`ColumnDefinitionWriteItemsConsistencyTest`・`AbstractBusinessDataReadIT` | `purpose` を足す。既存の確かめは変えずに通す（T-C1） |
| `ReadPurpose` に値を足す | `TargetDbSettingsTest`・`JdbcTargetReadAccessTest`（目的の表） | `REFERENCE` の行を足す |
| E2E（`frontend/e2e`） | 定義の API を使う流れ | 変えない。`purpose` を送らないため応答は `reference: null` を足すだけ。統合の前に全件を流して確かめる |

計画の外の既存のテストが落ちたときは、直す前に止めて依頼者に確かめる（前の Intent の G1 と同じ扱い）。

## 5. 承認の場の決定・上流との差の反映

| 出どころ | 決定・差 | この計画での扱い |
|---|---|---|
| 機能設計 D1〜D9 | ページは 1 から、`formPart` は `CREATE`・`UPDATE` だけ DSL の値、`FAILED(理由)`、ラベルと `check` は `search`、409 `MISMATCH`、演算子は `EQUALS`・`CONTAINS`、`countState`、厳密な一致、`valueType` | Step 5・9・11・15・18・20 |
| NFR 設計 F1: A・Q1: A | 文字の型のラベルと値は先頭 100 字で並べる。値は `SUBSTRING(…,1,10001)` で読み、`CHAR_LENGTH(…) <= 10000` で除く（BR7.3・BR11.4 との差は performance-design.md のとおり） | Step 7・8、T-B2〜T-B9 |
| NFR 設計 Q2: A・R-01・R-02 | 2つの設定。1 秒未満は `Invalid` で起動は続く | Step 3・4（T-T4） |
| NFR 設計 R-03 | 診断の WARN は `ReferenceLookup` で1行、変換の WARN は既存の別の行 | Step 9、Step 21 の `ReferenceSecretLeakIT` |
| **NFR 設計 U1 R-05（直してほしい）** | `referenceReadTotal()` は `withRead` の work の中でだけ呼び、`Usable` でないときは `IllegalStateException` | Step 3 の説明文と実装、Step 4 の単体テスト（T-T4 に1件） |
| **NFR 設計 U2 R-05（直してほしい）** | U1 の継ぎ目が U2 より先に入る順 | 1.2節。B1・B2 の統合の後に B3 |
| NFR 設計 R-04（1038 の既存の一覧での再現） | 持ち主は Build and Test | この計画では行わない |
| **基盤の設計 R-01** | 設定の誤りの検知は配備の手順とスモークテスト（既存の一覧の 200） | Code Generation の分は `README.md`・`.env.example` の説明に「1 秒未満は対象DB 全体が使えなくなる」を書くこと（Step 3）。配備の手順は Deployment Pipeline |
| **基盤の設計 R-02** | パネル 46・47 の式は `uri` を確かめるまで仮。Observability Setup が確かめてから `mastersmith-overview.json` に書く | 設計どおり、この計画ではダッシュボードを変えない（9節の Q2 で確かめる） |
| **基盤の設計 R-03** | perf の読み取りのプール 5 の行と、`hikaricp.connections.max` が 5 でなければ場面を流さない中止の条件 | Step 22 の `perf/README.md` |
| 基盤の設計 2.2 | `application.yaml`・`.env.example`（「読み取りの全体の上限は無い」を直す）・`README.md` の環境変数の表と読み取りの API の節 | Step 3・11・20 |
| 基盤の設計 4.2・後の段 | 種と DSL の台本・k6 の場面・`perf/README.md` は Code Generation | Step 22。Bolt の計画・単位の定義の「k6 の場面は Build and Test で書く」との差（9節の Q1） |

## 6. 手順

各手順は終わったらチェックを付ける。長いテスト（`verify`・E2E）は `caffeinate -dims` で包み、流した後に `pmset -g log` で Sleep・DarkWake が無かったかを確かめ、入っていたら結果を使わずに流し直す（`project.md`）。結合テストの前に colima が動いていることを確かめ、`DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` を付ける（付けないと対象DB のテストが SKIPPED になる）。

### B1 U1 前半

- [x] **Step 1: 準備**（ストーリー: 全体）
  - `develop` が最新で作業フォルダに未コミットの変更が無い（`aidlc/` の記録は先にコミットしてある）ことを確かめ、`git switch -c feature/261029-reference-lookup-cleanup-b1 develop` を作る。
  - E2E の報告（`frontend/playwright-report`・`frontend/test-results`）など git の対象外の生成物を消す（`project.md` の学び）。
  - Dependabot の #81・#82 の取り込みが `develop` に入っていることを確かめる。
- [x] **Step 2: テストの実行の口の確かめ**（Testing Contract の runner_step）
  - `./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.config.TargetDbSettingsTest'` と、上の環境変数を付けた `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.PostgresBusinessDataReadIT'` が今のまま通ることを確かめ、`unit-test-instructions.md` のコマンドが流せることを記録する。
  - 3.4節の突き合わせを確かめる。
- [x] **Step 3: targetdb の実装（設定と読み取りの口）**（US3.5、BR8.1、NFR3.4・NFR3.5、T-T4、U1 R-05）
  - `ReadPurpose.REFERENCE`、`TargetDbProperties`（`QueryTimeout.reference` 既定 5s・`longest()` に入れる、`referenceReadTotal` 既定 10s）、`TargetDbSettings`（点検・`case REFERENCE`・`Usable.referenceReadTotal()`）、`TargetReadAccess.referenceReadTotal()`（説明文に「`withRead` の work の中でだけ呼ぶ。`Usable` でないときは `IllegalStateException`」）、`JdbcTargetReadAccess`（`REFERENCE` を受け付ける、`referenceReadTotal()` の実装）、`FakeBusinessDataPorts`。
  - `backend/src/main/resources/application.yaml`: `query-timeout:` の `detail` と `write` の間に `reference: ${MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE:5s}`、`query-timeout:` の後に `reference-read-total: ${MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL:10s}`。コメントに範囲（1 秒以上）、最悪の値（約 11 秒＋写しの読み取り＋借りる待ち最大 3 秒）、「4つの上限」を「5つの上限」に直す。
  - `.env.example`: `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_DETAIL` の次にコメントアウトした2行と説明。「読み取りの全体の上限は無い」を「参照先を読む定義の取得だけは全体の上限がある（既定 10s）」に直す。1 秒未満では対象DB 全体が使えなくなることを書く（R-01）。
  - `README.md` の環境変数の表に2行（既定・1 秒以上・何の上限か）。
- [x] **Step 4: targetdb のテスト**（T-T4）
  - `TargetDbPropertiesTest`: 既定 5s・10s、結び付けで変えた値、`longest()` が `reference` を含む（`reference` を 30s にすると最長が 30s）。
  - `TargetDbSettingsTest`: `reference`・`referenceReadTotal` の 0・負・`500ms` で `Invalid` と項目名、ちょうど 1 秒は `Usable`、WARN に値が出ない、`queryTimeout(REFERENCE)` が `reference` を返す。
  - `JdbcTargetReadAccessTest`: `REFERENCE` を受け付け上限が 5 秒の session が渡る、`referenceReadTotal()` が `Usable` の値を返す、`NotConfigured`・`Invalid` で `IllegalStateException`（R-05 の1件）。
  - 4節の8つのテストの構成子を直す。`./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.*'` と `:backend:integrationTest --tests 'cherry.mastersmith.targetdb.config.*'` を流して通す。
- [x] **Step 5: businessdata.domain の実装（B1 の分）**（US1.1〜US1.4・US3.4、BR2.4・BR2.5・BR3.1・BR3.2・BR3.5・BR3.6・BR4.2〜BR4.4・BR5.1・BR7.6・BR8.4・BR9.5・BR10.1・BR11.4）
  - 3.1節の B1 の型。`ReferencePolicy` に `accessOf`・`usableSearchFields`・`distinctByValue`・`fixedOrder`・`sameReferenceValue`。`ReferenceReadDeadline`（`start`・`narrow`・`MIN_REMAINING`）、`ReferenceReadFailure`（`of(RowQueryFailure)`・`budgetExceeded()`）、`ReferenceFailureReason`。
- [x] **Step 6: businessdata.domain のテスト（B1 の分）**
  - `ReferencePolicyTest`（各欠ける条件で `NO_PERMISSION`、バイナリ・password の値のカラム、LOOKUP のラベルのカラムの選び方 BR2.5、条件の選び方 BR3.2、並びの組み立て、ラベルと値が同じカラムで重ねない）。
  - `ReferencePolicyPropertyTest`（jqwik、失敗の種を記録: 権限を足しても `AVAILABLE` が `NO_PERMISSION` にならない、NONE の列が条件と一覧に出ない、出力の値に重なりが無く最初の出現の順を保つ、`sameReferenceValue` が反射的・対称的、大文字と小文字・末尾の空白の違いは一致しない、`1.0` と `1` は一致する）。
  - `ReferenceReadDeadlineTest`（T-T2: 注入した時計で残り 0.5 秒 → 空、1.0 秒 → 上限 1、2.3 秒 → min(5, 3) = 3、同じ接続と壊れた印を共有）と `ReferenceReadDeadlinePropertyTest`（絞った上限が 1 以上で session の上限を超えない）。
  - `ReferenceReadFailureTest`（T-S1: 理由の写し、`INVALID_VALUE`・`UNEXPECTED` は `IllegalArgumentException`、`budgetExceeded()` の形、`toString` にメッセージが無い）。
  - 値を持つ型の `toString` の伏せ字のテスト（BR10.1）。
  - `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.domain.*'` を流して通す。
- [x] **Step 7: repository の実装**（BR4.2・BR7.3・BR8.6・BR11.2・BR11.4・BR12.4、NFR2.6〜NFR2.8、F1: A）
  - `RowDialect.lengthWithin`・`sortExpression`（STRING・OTHER は先頭 100 字、ほかはカラムのまま）を両方の方言に。
  - `RowSqlBuilder`: 指定があるとき、値のカラムを `SUBSTRING(…,1,10001)`、表示の列を `selectExpression(…, LIST_TEXT_LIMIT)`（バイナリ・password は選ばない）、`IS NOT NULL` と長さの条件、固定の並び（`sortExpression` を `orderTerm` に渡す）、主キーで結ぶ形・結ばない形、`LIMIT ? OFFSET ?`。
  - `RowSqlExecutor.search`: 指定があるとき件数の文を作らず、`setMaxRows` に読む行を当て、`RowSearchResult.Rows` を返す。文ごとの上限は今までどおり `session.queryTimeoutSeconds()`。
- [x] **Step 8: repository のテスト**
  - `RowDialectTest`（2つの口を両方の方言・型ごとに）、`RowSqlExecutorTest`（指定なしの文が今と同じ文字であること〈BR12.4〉、指定ありの文の `LIMIT`・並び・長さの条件・`SUBSTRING 10001`・件数の文が無いこと、`setMaxRows` が 201、識別子は既知の名前の一覧だけで引用され `'`・`"`・`;`・`--`・バッククォート・改行の名前は文を作らずに失敗する）。
  - `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.repository.*'` を流して通す。
- [x] **Step 9: service の実装（定義の分）**（US1.1〜US1.3・US3.4・US3.5、BR1.1・BR1.5・BR2.1〜BR2.3・BR3.3・BR3.4・BR4.1〜BR4.7・BR8.2〜BR8.5・BR8.7・BR12.1）
  - `TableAccessResolver`: 写しを `Resolved` と一緒に返し、同じ写しで参照先の判定の材料を作る口を足す（内部DB の接続は写しの後に返す）。
  - `ReferenceLookup`（定義の分）: 参照の項目ごとに `ReferenceTarget`、入力できないカラムは `NO_PERMISSION`、`accessOf`、LOOKUP のピッカーの情報、REFERENCE が1つ以上あるときだけ `withRead(REFERENCE, …)` を1回、その中で `ReferenceReadDeadline.start(clock, readAccess.referenceReadTotal())` と文ごとの `narrow`、同じ参照先は1回、201 行目で `TOO_MANY`、同じ値を落とす、失敗の後は読まない、診断の WARN を1行（`purpose`・`reason`・`sqlState`・`vendorCode`・`exceptionClass`・`budgetExceeded`）。`Clock` は既存の bean を注入する。
  - `BusinessDataReadService.definition` に `purpose` を通し、`VIEW` では今と同じ。失敗を既存の code（`TIMEOUT`・`UNAVAILABLE`・`MISMATCH`）に写す。
- [x] **Step 10: service のテスト（定義の分）**
  - `ReferenceLookupTest`（差し替えの口と `SteppingClock`）: `VIEW` で読まない、入力できないカラムは読まず `NO_PERMISSION`、LOOKUP は対象DB を読まない、REFERENCE が 0 なら借りない、同じ参照先は1回、200・201 行、失敗の後に残りを読まず壊れた印、T-T3（3つの2つ目で締め切り → 3つ目を読まず `TIMEOUT`・`budgetExceeded`）、WARN がちょうど1行で値が無い。
  - `TableAccessResolverTest`・`BusinessDataReadServiceTest` を直し、写しを1回だけ取ることを足す。
  - `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.service.*'` を流して通す。
- [x] **Step 11: web の実装（定義の分）**（BR1.1・BR4.8・BR12.1〜BR12.3、D2）
  - `DefinitionRequest` に任意の `purpose`（列挙に無い値は 400 `VALIDATION_FAILED`）、`ColumnDefinitionResponse.reference`、3.1節の応答の型、`formPart` の扱い（D2）。
  - `README.md` の「業務データの読み取りの API」の節の定義の行に「`purpose` が `CREATE`・`UPDATE` で参照の項目があるときだけ読み取りの接続を1本借りる」を足す。
- [x] **Step 12: web のテストと定義の結合テスト**（T-B1〜T-B3・T-B6・T-B8・T-B9・T-B10 の定義の分、T-T1・T-F1・T-C1〜T-C3、(c)(h)(i)(j) の定義の分）
  - `BusinessDataReadControllerTest`（`purpose` の読み取り・400）、`ColumnDefinitionWriteItemsConsistencyTest` を通す。
  - `AbstractReferenceReadIT` と `PostgresReferenceReadIT`・`MysqlReferenceReadIT`・`MariadbReferenceReadIT`（クラスごとに一意の名前のスキーマ〈MySQL・MariaDB はデータベース〉を作って消す。`ReferenceTables`）: 参照先 199・200・201・10,000 行、長い値が混ざる 200・201 行（T-B2・T-B3）、値 10,000・10,001 字（半角・日本語・補助面）と 100・101 字の値が切れない、ラベル 100・101 字、NULL のラベルは最後・NULL の値は除く、BIGINT の最大・桁の多い DECIMAL、引用符を含む名前、同じ値の行、複合の主キー・主キーの無い表・ビュー・自己参照、先頭 100 字が同じ行の並び（T-B8）、2,000 行に約 10,000 字の 200 行（T-B9、1クラスで1回作る）、`<script>` などの値とラベルが同じ文字列で返る、`NO_PERMISSION` で 200（AC1.3.1）。
  - `ReferenceConnectionUsageIT`（定義の分。REFERENCE 0・1・3・5 個、同じ参照先を指す2項目で文 1、`VIEW` で借りない、一覧・詳細の文の数が今と同じ〈T-C2〉、応答しない対象DB を待つ間の内部DB の使用中が 0）。
  - `ReferenceTimeoutIT`（定義の分。`SilentServer` で 503 `BUSINESS_TARGET_DB_TIMEOUT`、応答とログに例外のメッセージが無い。型の食い違いで 409）。
  - T-C1: `purpose` を送らない応答が `reference: null` を除いて今と同じ。既存の `*BusinessDataReadIT` を変えずに通す。T-C3: `BusinessDataProblemTypeCatalogTest` に code の数と状態コードの組が変わらない確かめを足す。
  - 上の環境変数を付けて `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.*Reference*IT' --tests 'cherry.mastersmith.businessdata.web.*BusinessDataRead*IT'` を流して通す。
- [x] **Step 13: B1 の確かめと統合**
  - `./gradlew spotlessApply` の後、時間を測って `caffeinate -dims` で `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（上の環境変数を付ける）。SKIPPED の対象DB のテストが無いこと、パッケージごとのカバレッジ（3.4節の7パッケージ）、`spotbugsGate`、3.3節の差が空、lockfile・移行ファイル・DSL の JSON Schema に差が無いことを確かめ、時間とカバレッジの値を記録する。42 分を超えたら依頼者に諮る（NFR5.3）。
  - 見本の対象DB（PostgreSQL）を起動して `./gradlew e2eTest` の全件を流す（業務データの `src/main` に触れるため。`team.md`）。
  - コミットは区切りごとに依頼者に提案し、承認を得てから積む（7節）。`aidlc/` の未コミットの変更を先にコミットしてから、`develop` へ squash で統合し、ブランチを消す。`git push` は依頼者が行う。push の後の CI を試行ごと（`gh run view --json attempt,jobs`）に確かめ、検査の job の時間を記録する。

### B2 U1 後半

- [x] **Step 14: 準備**
  - B1 の統合と CI の確かめの後、`git switch -c feature/261029-reference-lookup-cleanup-b2 develop`。E2E の報告を消す。
- [x] **Step 15: businessdata.domain の実装（B2 の分）**（US2.1・US2.3、BR1.3・BR1.4・BR5.3〜BR5.9・BR6.1〜BR6.4・BR7.1・BR9.1〜BR9.7・BR10.1）
  - 3.1節の B2 の型。`ReferenceCheckResult`（`Found`・`NotFoundOrDenied`・`Failed(ReferenceReadFailure)`）、`ReferenceCountState`（`Available`・`TooMany`・`NoPermission`・`Failed(ReferenceReadFailure)`）。`ReferenceRowsRequestValidator`（BR1.4: 名前の欠け、`conditions` と `value` の両方・どちらも無い、条件のカラムの欠け・重なり、空でない条件の値が無い、1,000 文字、ページ 1〜1,000,000。DSL を見ない）。
- [x] **Step 16: businessdata.domain のテスト（B2 の分）**
  - `ReferenceRowsRequestValidatorTest`（境界: 値 1,000・1,001 文字〈コードポイント〉、ページ 0・1・1,000,000・1,000,001、両方・どちらも無い、重なり、空の条件だけ）と `ReferenceRowsRequestValidatorPropertyTest`（jqwik: 知らない項目で結果が変わらない、上限以下の値は形の誤りにならない）、結果の型と値の型の `toString` の伏せ字。
- [x] **Step 17: repository の実装とテスト（B2 の分）**（BR5.5・BR5.7・BR6.3・BR8.6・BR9.4）
  - 指定に値の一致の条件（`comparisonTarget = ?`）と `CONTAINS` の既存の書き方を通し、ピッカーは `LIMIT 21 OFFSET (page-1)*20`、ラベルは 1、`check` は 201。
  - `RowSqlExecutorTest` に文の形と `setMaxRows` を足し、`./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.repository.*'` を流して通す。
- [x] **Step 18: service の実装（B2 の分）**（BR1.2・BR1.3・BR1.5・BR3.3・BR5.2〜BR5.9・BR6.1〜BR6.4・BR7.1・BR9.1〜BR9.7）
  - `ReferenceLookup.search`・`label`（写しを1回、同じ応答の 403、`EQUALS` の値の型の 400 `INVALID_VALUE`、1回借りて文1つ、20 件と 21 行目、ラベルの行が無ければ 403）。
  - `ReferenceLookup.check`・`countState`（U2 が借りた接続を受け、自分で借りない。`check` は 200 行まで型つきの厳密な一致、`countState` は選択肢と同じ読み方。WARN を出さない。値をログに出さない）。`ReferenceRowsResult`。
- [x] **Step 19: service のテスト（B2 の分）**
  - `ReferenceLookupTest` に足す: 403 の各場合で対象DB に問い合わせない、`EQUALS` の値の型の 400、REFERENCE に検索は 403、`check` の各場合（参照でない・`NO_PERMISSION`・型として読めない値は問い合わせない、厳密に一致しない行だけ・10,001 字の行だけなら `NOT_FOUND_OR_DENIED`）、`countState` の4つの場合と `Failed` の理由・壊れた印。
  - `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.service.*'` を流して通す。
- [x] **Step 20: web の実装（B2 の分）**（BR1.2〜BR1.4・BR10.3・BR10.4・BR11.1、NFR1.4・NFR1.13）
  - `BusinessDataReadController` に `POST /api/tables/reference/rows`（`BusinessDataPaths.REFERENCE_ROWS`）を足す。伏せる要求の型で受け、結果の型を `switch` で尽くして `BusinessException` に写す。403 は `fieldErrors` なしの同じ本文。
  - `BusinessDataWebConfig` に本文の上限 65,536 バイトの道を足す。
  - `README.md` の「業務データの読み取りの API」の道の表に `POST /api/tables/reference/rows` を足す。
- [x] **Step 21: web のテストと検索・ラベル・確かめの結合テスト**（(a)〜(e)・(i)・(j)・(k)、T-B4〜T-B9 の残り、T-T1・T-F1 の残り、NFR1.2・NFR1.5・NFR1.15）
  - `BusinessDataReadControllerTest` に、形の 400 が 403 より前、写しの各場合。
  - `ReferenceAuthorizationApiIT`（パラメーターの表: 401、要る権限だけを欠く利用者〈参照元のカラムが FULL でない・参照先の表が読めない・値のカラムが NONE・ラベルのカラムが NONE・一覧の列が NONE・使える条件が残らない〉と値のカラムがバイナリ・password の形で 403、権限ありで 200、停止中は 401、作業ロールの切り替え・割り当ての変更の直後の次の要求で切り替わる）。
  - `ReferenceSameResponseIT`（403 の各場合〈権限の欠け・DSL に無い名前・参照でないカラム・REFERENCE に検索・`searchFields` に無いカラム・型として読めないラベルの値・行が無いラベル〉の状態コード・`code`・本文の構造とメッセージが同一、判定の順の表、差し替えた要求で SQL が1つも流れない〈(b)〉）。
  - `ReferenceRequestLimitIT`（本文 65,536・65,537 バイト、値 1,000・1,001 文字、ページの境界）。
  - `AbstractReferenceReadIT` に足す（3種類）: ピッカーの 20・21・40・41 行と長い値の行（T-B5）、最後のページの後、`%`・`_`・`\` のエスケープ（(e)）、`EQUALS` の型（`INT` と `BIGINT`、数字の文字列）、ラベルの1件読みの大文字と小文字だけ違う値（BR6.3 の DB の比べ方）、`check` の大文字と小文字・末尾の空白・`1.0` と `1`、MariaDB の PAD SPACE（T-B7）、値の長さの境界の `check`（T-B6）、`countState`（T-B4）、参照先の形（(h)）、T-B9 の残りの経路。
  - `ReferenceConnectionUsageIT` に検索・ラベル（借りる 1・文 1）と `check`・`countState`（渡した接続だけを使う）、待つ間の内部DB の使用中が 0。
  - `ReferenceTimeoutIT` に検索・ラベルの時間切れと失敗の種類。
  - `ReferenceSecretLeakIT`（TRACE を有効、定義の選択肢・検索・ラベル・`check`・`countState` の5経路で架空の目印がログに出ない、各失敗で `ReferenceLookup` のロガーの WARN がちょうど1行と `GlobalExceptionHandler` の WARN が1行で値・メッセージが無い、監査の行が増えない）。
  - `ReferenceTraceExposureIT`（外部へ送る設定を有効にして、道の属性が固定の道で値が無い）。
  - `ApiAccessArchitectureTest`・境界テストを変えずに通す。
  - 上の環境変数を付けて `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.web.*Reference*IT'` を流して通す。
- [x] **Step 22: perf の種・DSL・k6 の場面と README**（US3.5 の AC3.5.4 の準備、NFR2.1〜NFR2.4、基盤の設計 4節・R-03）
  - `perf/biz/make-biz-seed.sh`: 3種類の DB で同じ形の REFERENCE の参照先（ちょうど 200 行・201 行の短い文字のコード。行の番号だけから決まる架空の値）と参照元の小さな表、読み取りのアカウントへの `SELECT`。
  - `perf/make-biz-dsl.mjs`: REFERENCE（200 行・201 行の表を指す2つ）と LOOKUP（`biz_item`、検索の列 `c_010`）の options。
  - `perf/k6/scenarios.js`: `refDefinition200`・`refDefinition201`・`refPickerEquals`・`refLabel`・`refPickerContains`（1回の繰り返しに要求1つ、待ちなし、トークンは `setup()`、setup は前の場面の状態に頼らない、p95 の線は 1,000 ms・部分一致 3,000 ms を今の `thresholdsFor` の仕組みに足す）。`perf/k6/thresholds-test.sh` の期待の表に5つの場面を足す。
  - `perf/README.md` に U1 の場面の手順: 一時の環境ファイル `app-targetdb.env` に `MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE=5` を足す行、流す前に使い捨てのアプリの `/actuator/metrics/hikaricp.connections.max`（`pool=mastersmith-target-db`）を読み、**5 でなければ U1 の場面を流さず中止し、一時の環境ファイルを直して使い捨てのアプリを起動し直す**条件、種を入れた後の行の数（200・201・100,000）と目印の当たりの数の確かめ（MySQL は `--default-character-set=utf8mb4`）、流す順（U1 の読み取り → U2 の作成 → 深いページの参考）、`caffeinate -dims` と `pmset -g log`。
  - 確かめ: `bash -n perf/biz/make-biz-seed.sh`、`node --check perf/make-biz-dsl.mjs`、`bash perf/k6/thresholds-test.sh`、場面ごとに `SCENARIO=<名前> k6 inspect --include-system-env-vars perf/k6/scenarios.js` で場面と閾値の名前。種の流し込みと場面の実行は Build and Test。
- [ ] **Step 23: B2 の確かめと統合**
  - Step 13 と同じ（`verify` の時間・カバレッジ・`spotbugsGate`・3.3節の差・lockfile と移行の差、E2E の全件、コミットの提案、squash の統合、CI の試行ごとの確かめ）。
- [ ] **Step 24: 文書と追跡**
  - `README.md`・`.env.example`・`perf/README.md` の記述が実装と合うことを読み直す。2つの Bolt の `verify` の時間・カバレッジの値・計画との差を、指揮役がまとめる `code-summary.md` の材料として残す。U1 のコードのレビューは B2 の後に U1 全体（B1・B2）を1回で受ける（9節の Q3）。

## 7. コミットの区切り（提案の件名。積む前に依頼者の承認を得る）

各区切りの最初のコミットの時点でコンパイルが通ることを確かめる（import の向き: targetdb → domain → repository → service → web）。

| Bolt | 区切り | 件名（案） | 中身 |
|---|---|---|---|
| B1 | C1 | 対象DB の読み取りに参照先の目的と2つの時間の上限を足す | Step 3・4（targetdb・`application.yaml`・`.env.example`・`README.md` の表） |
| B1 | C2 | 参照先の読み取りの判定と締め切りの部品を足す | Step 5・6（businessdata.domain） |
| B1 | C3 | 行の SQL の部品に参照先の読み取りの指定を足す | Step 7・8（repository） |
| B1 | C4 | 定義の取得に REFERENCE の選択肢と LOOKUP のピッカーの情報を載せる | Step 9〜12（service・web・結合テスト・`README.md` の API の節） |
| B1 | 統合 | 他テーブル参照の読み取りの前半（REFERENCE の選択肢と定義の参照の項目）を足す（Intent 261029-reference-lookup-cleanup の B1） | squash |
| B2 | C1 | 参照先の検索とラベルの1件読みの API を足す | Step 15〜20 の検索・ラベル（domain・repository・service・web） |
| B2 | C2 | 保存前の確かめの口 check と countState を足す | Step 15・18・19 の確かめの口 |
| B2 | C3 | 参照先の読み取りの認可・同じ応答・漏えい・接続の数の結合テストを足す | Step 21 |
| B2 | C4 | perf に参照先の種と k6 の場面を足す | Step 22 |
| B2 | 統合 | 他テーブル参照の読み取りの後半（検索とラベルの API・保存前の確かめの口・perf の場面）を足す（Intent 261029-reference-lookup-cleanup の B2） | squash |

## 8. ストーリーと手順の対応

| ストーリー | 受け入れ基準（U1 の分） | 手順 | 主なテスト |
|---|---|---|---|
| US1.1 | AC1.1.1〜AC1.1.6・AC1.1.9 | 5・7・9・11・12 | `ReferencePolicy*Test`・`*ReferenceReadIT`・`ReferenceConnectionUsageIT`・`ReferenceTimeoutIT` |
| US1.2 | AC1.2.1〜AC1.2.5 | 5・7・9・12 | `*ReferenceReadIT`（199・200・201・10,000 行） |
| US1.3 | AC1.3.1・AC1.3.2・AC1.3.4・AC1.3.5・AC1.3.8 | 5・9・12・18・21 | `ReferencePolicyTest`・`ReferenceAuthorizationApiIT` |
| US1.4 | AC1.4.1〜AC1.4.7 | 5・7・15・18・21・3.3節 | `ReferenceSameResponseIT`・`ReferenceSecretLeakIT`・`*ReferenceReadIT`・境界テスト |
| US2.1 | AC2.1.1〜AC2.1.16 | 15〜21 | `ReferenceRowsRequestValidatorTest`・`ReferenceAuthorizationApiIT`・`ReferenceSameResponseIT`・`ReferenceRequestLimitIT`・`ReferenceTraceExposureIT`・`*ReferenceReadIT` |
| US2.3 | AC2.3.1・AC2.3.2・AC2.3.4 | 15・18・20・21 | `*ReferenceReadIT`・`ReferenceSameResponseIT` |
| US3.4 | AC3.4.1〜AC3.4.4 | 7・8・11・12 | `RowSqlExecutorTest`（既定の文）・T-C1・T-C2・既存の `*BusinessDataReadIT` |
| US3.5 | AC3.5.1〜AC3.5.3・AC3.5.5（AC3.5.4 は Step 22 で準備し測定は後の段） | 3・4・9・12・13・21・22・23 | `ReferenceConnectionUsageIT`・`ReferenceTimeoutIT`・`TargetDbSettingsTest`・`verify` のカバレッジ |

画面の側の受け入れ基準（AC1.1.7・AC1.1.8・AC1.1.10・AC1.3.3・AC1.3.6・AC1.3.7・AC2.3.3・AC3.4.5）は U3 の受け持ちで、この計画では作らない。

## 9. 計画の承認で確かめたいこと（推奨と理由）

- **Q1. k6 の場面・種・DSL を B2 で書くか。** 推奨: A（B2 の Step 22 で書く）。後で承認された基盤の設計（`infrastructure-specification.md` の「後の段に回すこと」）が Code Generation を持ち主にしており、Build and Test で流す前に `k6 inspect` と `thresholds-test.sh` まで確かめられるため。B は Bolt の計画と単位の定義どおり Build and Test で書く。
- **Q2. ダッシュボードのパネル 46・47 をこの段で書くか。** 推奨: A（書かない。Observability Setup が `uri` を確かめてから書く）。承認済みの `monitoring-design.md` の R-02 の決定が「確かめてから書く」で、仮の式をこの段で置くと承認済みの設計と食い違うため。B はこの段で仮の式を書き、Observability Setup で直す。
- **Q3. B1 を `verify` と E2E を通して先に統合し、U1 のコードのレビューを B2 の後に1回で受けるか。** 推奨: A（そうする）。単位ごとのレビューは1回で、前の Intent の U3（B5〜B7）と同じ形のため。指摘は直しの Bolt で直す。
- **Q4. 検索 API の置き場。** 推奨: A（既存の `BusinessDataReadController` にメソッドを足す）。クラスの `@ApiAccess(AUTHENTICATED)` にそのまま入り、新しいクラスが1つ減るため。B は新しい `ReferenceRowsController` を作る。
- 3.1節・3.2節の新しいクラスと口（BR11.3・ADR-001）を承認するか。境界テストと SpotBugs の除外は変えない（3.3節）。

## Sources

- 段の定義: `.claude/aidlc-common/stages/construction/code-generation.md`
- この単位の設計: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/`（`functional-spec.md`・`rules.md`・`entities.md`）、`nfr-requirements/`、`nfr-design/`（`logical-components.md`・`reliability-design.md`・`performance-design.md`・`security-design.md`・`observability-design.md`・`scalability-design.md`・`reviews/review-02.md`）、`infrastructure-design/`（`infrastructure-specification.md`・`cicd-pipeline.md`・`monitoring-design.md`）
- U2 の継ぎ目: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-design/reviews/review-02.md`（R-05）
- Inception: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/delivery-planning/bolt-plan.md`・`delivery-planning-questions.md`、`units-generation/unit-of-work.md`・`unit-of-work-story-map.md`、`requirements-analysis/requirements.md`、`user-stories/stories.md`、`contract-design/contract-summary.md`、`domain-design/components.md`
- 承認の場の決定: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/audit/sakura-local-4e42a93f87ce.md`（NFR Design・Infrastructure Design の承認と「直してほしい」の記録）
- 既存のコード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/businessdata/`・`targetdb/`、`backend/src/test/java/cherry/mastersmith/`、`backend/build.gradle.kts`（`packagesJudgedByTotal`）、`backend/config/spotbugs-exclude.xml`、`backend/src/main/resources/application.yaml`、`.env.example`、`perf/`
- `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 9節の Q1〜Q4 は推奨の A で書いた。違う答えのときは該当の手順を直す。
- [assumption] 参照先の検索 API の道は契約の仮の名前 `/api/tables/reference/rows` のまま。
- [assumption] テストの件数（Summary）は見積もりで、実際の数は Build and Test で実測する。
