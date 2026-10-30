# コード生成の計画 — U4 u4-perf-evidence-redactor

## Summary

- Builds: k6 の結果の JWT を伏せて記録（evidence/）に置く Node の 1 ファイルの道具（JSON の口・`--text` の口・`--scan` の口）と、verify の安全の検査の段の `perfEvidenceScan`、perf/README.md の伏せ字の節（Bolt B5）
- Touches: `perf/redact-k6-evidence.mjs`（新規）・`frontend/src/perfEvidenceRedactor*.test.ts`・`frontend/src/perfEvidenceScan.test.ts`（新規）、`build.gradle.kts`、`frontend/scripts/check-license-header.mjs`、`perf/README.md`
- Tests: Vitest の単体テスト 約 80 件（fast-check の性質ベース 8 件、子のプロセスで道具を起動する例 3 件を含む）を 3 ファイルに置く。既存のテストは変えない

## 1. 前提と範囲

- 単位 U4 `u4-perf-evidence-redactor`（library・S）。Bolt は B5 の 1 つ（`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/delivery-planning/bolt-plan.md`）。ほかの単位に依存しない。U5（B6）は、この単位が `develop` に統合された後に README の整理を始める（U5 の `nfr-design/security-design.md` 5.1節）。
- Test Strategy は Standard（部品ごとに 5〜8 件）、範囲は classic。方法は test-after（下の Testing Contract が正）。層は「純粋な関数（置換・残存の検査・木の置換）」→「コマンドの口（JSON・テキスト）」→「evidence の検査の口」の順に、実装してからその層のテストを書いて流す。
- ブランチ: `develop` の今の先頭から `feature/261029-reference-lookup-cleanup-b5` を作り、`develop` へ squash で統合し、統合の後にブランチを消す（`team.md` の Way of Working）。`git push` は依頼者が行う。
- **統合の前の E2E は要らない**: このブランチが変えるのは `perf/`・ルートの `build.gradle.kts`・`frontend/scripts/`・`frontend/src` の**テストのファイルだけ**で、`frontend/src` の本体（テストのファイルを除くもの）にも、認証・権限・業務データのパッケージの `src/main` にも触れない。そのため `team.md` の決まりどおり `./gradlew verify` だけで統合し、E2E は Intent の最後（Build and Test）とリリースの前に 1 回流す。`frontend/src` に補助の非テストのファイル（`testing/` など）を置かないのは、この読み方を崩さないためでもある（5.1節 P1）。
- 依存を足さない: 道具は Node の組み込み（`node:fs`・`node:path`・`node:url`・`node:process`・`node:os`・`node:crypto`）だけを読む。テストは既存の Vitest（`^5.0.5`）と fast-check（`^4.10.2`）だけを使う。`frontend/package.json`・`frontend/package-lock.json`・`frontend/.npmrc` を変えない（NFR9.61〜NFR9.63）。
- アプリ（WAR）・内部DB・`.env`・コンテナ・CI（`.github/workflows/ci.yml`）に触れない（`infrastructure-design/cicd-pipeline.md` 2.4節）。

## Testing Contract

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

（上の JSON の塊は `aidlc engine testing-posture render` の出力の `## Testing Contract` の塊を、一字も変えずに写したもの。`plan_profile.steps` の層のうち、データのモデル・DB・リポジトリ・API（HTTP）・画面は、この単位に無いため省く。「Business logic」を純粋な関数、「API / endpoint」をコマンドの口と読み替える。）

## 3. 決めること（名前・定数）

| 項目 | 決めた値 | 理由 |
|---|---|---|
| 道具のファイル | `perf/redact-k6-evidence.mjs` | `logical-components.md` の仮の名前のまま。契約 C7 の `node perf/<伏せ字の道具>.mjs …` の形に合う |
| JSON の口 | `node perf/redact-k6-evidence.mjs <入力の summary json> <evidence/ の中の出力>` | 契約 C7 のまま（NFR7.61） |
| テキストの口 | `node perf/redact-k6-evidence.mjs --text <入力> <evidence/ の中の出力>` | 機能設計 BR3.4 |
| evidence の検査の口 | `node perf/redact-k6-evidence.mjs --scan <根のディレクトリ>` | 仮の旗 `--scan` のまま。`--text` と同じ形の旗で、README の行で見分けやすい |
| Gradle のタスク | `perfEvidenceScan`（`verifySecurity` の一員） | `logical-components.md` LC8 の仮の名前のまま |
| 出力の字下げ | 2（`JSON.stringify(tree, null, 2)` に末尾の改行 1 つ） | `frontend/.prettierrc.json` の `tabWidth` と同じ。k6 の summary json も 4 でなく 2 で読みやすい大きさに収まる |
| 印 | `JWT_MARK = '[REDACTED-JWT]'`・`SETUP_MARK = '[REDACTED-SETUP]'` | BR1.2・BR1.7 |
| 入力の上限 | `MAX_INPUT_BYTES = 64 * 1024 * 1024` を名前付きで公開 | NFR8.66 |
| バイナリの拡張子の一覧 | `BINARY_EXTENSIONS = ['.png', '.jpg', '.jpeg', '.gif', '.webp', '.svgz', '.gz', '.zip', '.pdf']`（9 つ、凍結した配列）を公開 | `security-design.md` 8.3節（Q1: A）。足すときは依頼者の承認が要る |
| 表示の位置の件数 | 1 ファイルにつき先頭の 10 件まで、残りは件数だけ | `security-design.md` 8.2節 |
| 一時ファイルの名前 | `.<出力の名前>.<プロセス番号>.<乱数 8 桁の 16 進>.tmp`（`wx` で作る） | `security-design.md` 5.3節。README に形と消し方を書く |
| 公開する名前 | `JWT_MARK`・`SETUP_MARK`・`MAX_INPUT_BYTES`・`BINARY_EXTENSIONS`・`redactJwt`・`findResidual`・`redactTree`・`findUnredactedSetup`・`checkAndWrite`・`main`。`export default` は使わない | `logical-components.md` 2節（`checkAndWrite` は書き出しの段の関数で、残存の検査の独立をテストで確かめるために足す。8節 Step 9・10）。`team.md` のエクスポートの決まり（ESLint が当たらないため 5.1節 P6 のテストで確かめる） |

## 4. 置換の形の実装と処理の時間（`security-design.md` 2.1節）

- 形の定義は道具の中の 1 か所に置く。正規表現の定数 `JWT_SHAPE`（`/(?<![A-Za-z0-9])eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g`）を「定義」とし、置換（`redactJwt`）と残存の検査（`findResidual`）は同じ見つけ方の関数（内部の `scanJwtShapes(text)`。見つけた区間の始まりと終わりの位置だけを返す）を使う。`findResidual` は `redactJwt` を呼ばない（BR2.1）。
- 処理の時間の見立て: `-eyJ-eyJ…` のように `.` が無く始まりの候補が続く文字列では、正規表現の試しが入力の長さの 2 乗に近づく（候補ごとに区間の文字を末尾まで読んでから戻るため）。1 MiB ではおよそ 20 万の候補がそれぞれ数十万字を読み、Vitest の既定の時間の上限（5 秒）に収まらない見込みが高い。
- 手順: Step 4 でまず正規表現の形で作り、Step 8 で 1 MiB の例のテストを流して、上限を超えたら（見込みどおりなら）**形の定義を保ったまま 1 回のなめ**（文字を左から 1 度だけ読み、区間の長さを数えて 3 区間を判定する関数）に `scanJwtShapes` を替える。置換と検査の両方が同じ関数を使うため、替えるのは 1 か所。時間の上限は延ばさない。
- 定義を保つことの確かめ: テストのファイルの中に同じ正規表現を「突き合わせの正解」として置き、短いランダムな文字列で、1 回のなめの結果（区間の一覧）と正規表現の結果が一致する性質のテストを足す（正規表現の文字列は JWT の形の値ではないため、NFR8.71 に触れない）。
- 64 MiB に近い同じ形の入力での `--scan` の所要時間は、Step 13 で一度測って `code-summary.md` に書き、値と切り替えの判断を依頼者に伝える（例のテストには入れない）。

## 5. 設計が計画に回した義務の決定

### 5.1 `logical-components.md` 4節 P1〜P7

| # | 決定 | 理由・確かめ方 |
|---|---|---|
| P1 テストのパス | `frontend/src/perfEvidenceRedactor.test.ts`（純粋な関数と性質）・`frontend/src/perfEvidenceRedactorCli.test.ts`（JSON の口とテキストの口）・`frontend/src/perfEvidenceScan.test.ts`（evidence の検査の口）。`frontend/src` の直下に置く | `unit` の `include` の `src/**/*.test.{ts,tsx}` に当たり、`exclude`（`e2e/**/*.e2e.ts`・`node_modules/**`・`dist/**`・`src/eslintImportRules.test.ts`）に当たらない。`frontend/` の外のファイルを確かめるテストを `src` の直下に置く前例（`src/eslintImportRules.test.ts` が `frontend/eslint.config.js` を確かめる）に合わせた。補助の関数（JWT の形の組み立て・性質の確かめ・一時のディレクトリ）は各テストのファイルの中に置き、`src` に非テストのファイルを作らない（カバレッジの分母に入れない前例の考え方と、1節の E2E の読み方のため）。3 ファイルで `makeJwtLike` などの小さな関数が重なるのは受け入れる |
| P2 `vitest.config.ts` | **変えない**（`include`・`exclude`・`coverage` とも） | P1 のパスが今の `include` に当たるため。`perf/` にテストを置かないので、`fast-check`・`vitest` は `frontend/node_modules` から解決できる。Step 3 で 1 件以上が passed、skipped・todo が 0 で流れることを確かめる |
| P3 道具の読み方 | 前例（`src/eslintImportRules.test.ts`）と同じく、テストの中で `pathToFileURL(resolve(import.meta.dirname, '..', '..', 'perf', 'redact-k6-evidence.mjs'))` を `await import(/* @vite-ignore */ url)` で読み、テストのファイルの中の `interface PerfEvidenceRedactorModule` に当てる | `frontend/` の外のファイルを Vitest の中で読めるかは Step 3 で確かめる。読めないときは止めて、ADR-006 の切り替え先（道具の本体を `frontend/scripts/perf-evidence/` に置き、`perf/redact-k6-evidence.mjs` を本体を読み込むだけの薄い入口にして契約 C7 の呼び方を保つ）を依頼者に諮る（自分の判断で切り替えない） |
| P4 実行の環境 | 3 ファイルとも先頭に `// @vitest-environment node` を置く | `unit` の既定は `jsdom`。前例（`src/eslintImportRules.test.ts`）と同じ指定。各ファイルに `typeof window === 'undefined'` を確かめる 1 件を置く |
| P5 `coverage.include` | **変えない**（道具の `.mjs` を `verifyCoverage` の計測に入れない） | `coverage.include` は画面の本体（`src/**/*.{ts,tsx}`）の計測で、`frontend/` の外のファイルを入れるには `coverage.allowExternal` の設定も要り、変える所が増えるため。下限（行 80%・分岐 70%）と除外は変えない（NFR5.63）。代わりに、この単位の道具だけを計測する単位の確かめのコマンド（`unit-test-instructions.md` 4節。同じ下限が道具だけに当たる）を置き、値を `code-summary.md` に書く |
| P6 各検査への入れ方 | 道具のファイル 1 つを、(a) Prettier（新しいタスク `perfToolFormatCheck` を `verifyFormat` に足す）、(b) oxlint（新しいタスク `perfToolLint` を `verifyLint` に足す）、(c) ライセンスヘッダー（`frontend/scripts/check-license-header.mjs` が `perf/` の下の `.mjs`・`.js` も歩く）に入れる。ESLint には入れない | (a)(b) は `frontend/package.json` の `scripts` を変えずに（NFR9.62 の合否「`frontend/package.json` の差が無い」を守るため）、ルートの `build.gradle.kts` から `frontend/node_modules/.bin/` の道具を設定のファイルを明示して呼ぶ。対象は新しい道具のファイルだけにし、既存の `perf/*.mjs`（7 ファイル）には広げない（書式とリンタが前から当たっておらず、広げると今の Bolt の外の直しが要りうるため）。(c) は既存の `perf/` の `.mjs`・`.js`（8 ファイル）も対象になるが、先頭の行を読み取りで見た範囲ではどれもヘッダーを持つ。Step 16 で、組み込む前に流して通ることを確かめ、通らないものがあれば対象を道具のファイルだけに絞るかを依頼者に諮る。ESLint は `files` が `**/*.{ts,tsx}` で、設定のディレクトリの外のファイルは基の道の外として扱えないため入れない。ESLint が受け持つ決まりのうち道具に効くのは `export default` の禁止だけで、これは P1 のテストで公開する名前の一覧が 3節のとおりで `default` が無いことを確かめて代える（`enum` は JavaScript に無く、`no-implied-eval` は道具が `setTimeout` の文字列を使わず、`eval`・`new Function` は oxlint の `no-eval`・`no-new-func` が受け持つ）。`frontend/scripts/*.mjs` も今 ESLint の対象外で、同じ扱い |
| P7 型の検査 | 道具の `.mjs` は `tsc` の対象にしない（`tsconfig.json` を変えない）。テストは 3節の公開の名前を持つ `interface PerfEvidenceRedactorModule`（`any` を使わず、JSON の値は `unknown`）で型を付け、`tsc --noEmit` の対象（`src`）として型の検査を通す。道具の中は JSDoc で引数と戻り値の型を書く（読み手のため。検査はしない） | `allowJs`・`checkJs` を足すと `tsconfig.json` の全体の設定が変わり、`include` の下の JavaScript の扱いが広がるため。前例（`eslint.config.js`・`frontend/scripts/*.mjs`）も `tsc` の対象外。型と実体の食い違いは、公開の名前の一覧のテストと、すべての口を実際に呼ぶテストで落ちる形にする |

- ADR-006 の切り替え先は選ばない（道具は `perf/` に置く）。そのため契約 C7 の呼び方は変わらない。

### 5.2 `perfEvidenceScan` の組み込み（`infrastructure-design/cicd-pipeline.md` 2.2節）

```text
// 考え方（Gradle の Kotlin DSL。実装は Step 15）
val perfEvidenceScan = tasks.register<Exec>("perfEvidenceScan") {
    description = "aidlc/ の下の記録（evidence/）に JWT の形などが残っていないかを調べる（見つかれば失敗）。"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(checkToolchain)
    commandLine("node", "perf/redact-k6-evidence.mjs", "--scan", "aidlc")
}
```

- 入力・出力を宣言しない（毎回流れる）。`outputs.upToDateWhen { false }` は付けない（出力を宣言しない `Exec` は元々 `UP-TO-DATE` にならない。基盤の設計の R-03）。`isIgnoreExitValue` を付けず、終わりのコード 0 以外（1・2・4）でタスクが失敗する。`frontendInstall`・`vendorBuild` に依存しない。
- `build.gradle.kts` で変える所は次の **2 か所の両方**（基盤の設計の R-03 の直し）:
  1. `verifyStages` の `verifySecurity` の一員の一覧: `listOf(backendProject.tasks.named("spotbugsGate"), osvScan, gitleaksScan, perfEvidenceScan)`
  2. `verifySecurity` の段の説明の文言: `"8 安全の検査（SpotBugs＋FindSecBugs・OSV-Scanner・Gitleaks は重大度 High 以上で失敗。記録（evidence/）の JWT の形の検査は 1 件でも失敗）"`
- 段と段の並びは既存の `mustRunAfter` の仕組みのまま。CI は同じ `./gradlew verify` を呼ぶため変えない。
- 同じ Step で足す 2 つのタスク（5.1節 P6 の (a)(b)）:

```text
// 考え方（実装は Step 15）
val perfToolFormatCheck = tasks.register<Exec>("perfToolFormatCheck") {
    description = "perf の伏せ字の道具のフォーマットを確かめる（Prettier。画面と同じ設定）。"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(frontendInstall)
    commandLine("frontend/node_modules/.bin/prettier", "--check", "--config", "frontend/.prettierrc.json", "perf/redact-k6-evidence.mjs")
}
val perfToolLint = tasks.register<Exec>("perfToolLint") {
    description = "perf の伏せ字の道具のリンタを実行する（oxlint。画面と同じ設定）。"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(frontendInstall)
    commandLine("frontend/node_modules/.bin/oxlint", "-c", "frontend/.oxlintrc.json", "perf/redact-k6-evidence.mjs")
}
// verifyFormat の一覧に perfToolFormatCheck、verifyLint の一覧に perfToolLint を足し、両方の段の説明の文言に道具を足す。
// verifyLicense の段の説明の文言に「perf/ の .mjs・.js も check-license-header.mjs で確かめる」を足す。
```

### 5.3 evidence の検査の口の判定（`security-design.md` 8節。Q1: A と承認の場の R-01）

- 根の下を記号のリンクをたどらずに歩く（`readdir` の `withFileTypes`。`.` で始まる名前も飛ばさない。一時ファイル `.<名前>.….tmp` を拾うため）。根からのパスのどこかに名前がちょうど `evidence` のディレクトリがある通常のファイルを対象にする。リンクはパスを表示して数え、失敗にしない。
- 1 ファイルの判定の順: (1) 大きさが `MAX_INPUT_BYTES` を超える → 入力の誤り（中身を読まない）。(2) 読めない → 入力の誤り（`code` だけ）。(3) バイト列のどこかに NUL がある → 拡張子（名前の最後の `.` の後ろ、大文字と小文字を区別しない）が `BINARY_EXTENSIONS` にあれば、バイト列を **latin1** で読んで `findResidual` だけを当てる（見つかれば検出、位置はバイトの位置。見つからなければ「バイナリとして読んだ」と数えて通る）。一覧に無い（拡張子なし・`.gitkeep` のような名前を含む）→ 入力の誤り。(4) それ以外 → UTF-8 として読み（不正なバイトは置き換えの文字）、`findResidual` を当てる。
- 対象のファイルの根からの相対のパスにも `findResidual` を当て、見つかれば検出として数える。表示するパスはすべて `redactJwt` を通す。
- 終わりに、調べた数・バイナリとして読んだ数・リンクの数・検出の数・入力の誤りの数を表示する。終わりのコードは、検出があれば 4、無くて入力の誤りがあれば 2、どちらも無ければ 0。根が無い・ディレクトリでない・引数の数が違えば 1。

## 6. 既存のファイル・設定の変更（計画の承認で依頼者の承認が要るもの）

| # | ファイル | 変更 | 承認が要る理由 |
|---|---|---|---|
| A1 | `build.gradle.kts`（ルート） | `perfEvidenceScan`・`perfToolFormatCheck`・`perfToolLint` の 3 つのタスクを足し、`verifySecurity`・`verifyFormat`・`verifyLint` の一員の一覧と、その 3 つと `verifyLicense` の段の説明の文言を直す（5.2節） | 統合の前の関門（verify）と CI の中身が変わるため。`verifySecurity` は一覧と説明の 2 か所の両方を変える |
| A2 | `frontend/scripts/check-license-header.mjs` | 歩く根に `frontend/` に加えて `perf/`（`.mjs`・`.js` だけ。`.sh`・`.md`・`.env` は今の決まりのまま対象外）を足し、表示のパスをリポジトリの根からの相対にそろえる。先頭の説明のコメントも直す | 既存の検査の対象が広がる（`perf/` の既存の 8 ファイルも対象になる）ため。通らないものが出たら道具のファイルだけに絞るかを諮る（5.1節 P6） |
| A3 | `perf/README.md` | 末尾に H2 の節「記録（evidence/）に置く前の伏せ字」を 1 つ足す（8節 Step 18） | 依頼者のファイル（README）を変えるため。既存の節の文は変えない |
| — | 変えないもの | `frontend/vitest.config.ts`・`frontend/tsconfig.json`・`frontend/eslint.config.js`・`frontend/.oxlintrc.json`・`frontend/.prettierrc.json`・`frontend/.prettierignore`・`frontend/package.json`・`frontend/package-lock.json`・`frontend/.npmrc`・`.github/workflows/ci.yml`・`.pre-commit-config.yaml`・`.gitleaks.toml`・`perf/k6/scenarios.js`（読み取りだけ） | 5.1節の P2・P5・P6・P7 の決定による。`.pre-commit-config.yaml` の Prettier の確かめは `frontend/` の下だけのままで、道具の書式はコミットの前ではなく verify で守る |

- 既存の ArchUnit の境界テスト・SpotBugs の除外・カバレッジの下限と除外には触れない。

## 7. 影響を受ける既存のテスト・検査（検索で洗い出したもの）

| 対象 | 影響 | 扱い |
|---|---|---|
| `frontendTest`・`frontendCoverage`（`vitest run`） | 新しい 3 ファイルが `unit` の対象に加わり、2 回流れる（単体テストの段とカバレッジの段） | 64 MiB の境界の例は 1 件ずつに絞り、作るのが速い中身（`a` の続き・空白）と疎なファイル（`truncate`）で作る。時間の上限は延ばさない |
| `src/eslintImportRules.test.ts` | `src` の直下にテストのファイルが増えるが、機能の import の制限は `src/features/`・`src/shared/` だけに当たる | 影響なし（`unit` の `exclude` のまま、`eslint-config` の project は 1 ファイルだけ） |
| `frontendLicenseCheck`（A2） | `perf/` の 8 ファイルが対象に加わる | Step 16 で組み込む前に流す |
| `gitleaksScan`（`gitleaks git`）・pre-commit の Gitleaks | テストのソースに完成した JWT の形を置くと落ちる | 置かない（NFR8.71）。Step 19 で検索する |
| `frontendLint`（`oxlint .`）・`frontendFormatCheck`（`prettier --check .`） | 新しいテストのファイル（`frontend/src`）が対象に加わる | 通るまで直す |
| `frontendTypecheck`（`tsc --noEmit`） | 新しいテストのファイルが対象に加わる（`include` の `src`） | `any` を使わない（5.1節 P7） |
| `verify` の時間と CI の時間 | `perfEvidenceScan`（今の `evidence/` は 2 つ・約 100 ファイル）と新しいテストの分が増える | Step 20 で測って記録する（`team.md`。42 分の線） |

## 8. 手順

各 Step は終えたら箱に印を付ける。テストのコマンドは `unit-test-instructions.md` のとおり（この単位のファイルに絞ったもの）。

### 準備

- [ ] **Step 1. ブランチと作業フォルダの用意**: `develop` の先頭から `feature/261029-reference-lookup-cleanup-b5` を作る。`git status` で `aidlc/` の外に未コミットの変更が無いことを確かめる（`aidlc/` の未コミットの変更は、統合の前にコミットしておく。`project.md` の学び）。git の対象外の E2E の生成物（`frontend/playwright-report`・`frontend/test-results`）があれば消しておく（段の途中で消すと承認の場が止まる。`project.md` の学び）。
- [ ] **Step 2. 道具のファイルの骨組み**（Project structure）: `perf/redact-k6-evidence.mjs` を作り、Apache License 2.0 のヘッダー（`/* ... */`、2026、agwlvssainokuni。NFR9.65）、日本語の説明のコメント（3 つの口と終わりのコードの一覧）、3節の定数と公開の名前、ファイルが直接起動されたときだけ `main(process.argv.slice(2), 本物の io)` の結果を `process.exitCode` に置く起動の部分（`import.meta.url` と `process.argv[1]` の比べ。`frontend/scripts/check-license-header.mjs` と同じ形）を置く。`import` は `node:` だけ（NFR9.61）。この時点の関数は、引数の誤りでコード 1 を返すだけの形でよい。
- [ ] **Step 3. テストの実行の確かめ**（runner readiness）: `frontend/src/perfEvidenceRedactor.test.ts` を作り、先頭に `// @vitest-environment node`、5.1節 P3 の読み方と `interface PerfEvidenceRedactorModule`、`fast-check` の `import`、次の 3 件だけを置く: (a) `window` が無い、(b) 公開の名前の一覧が 3節のとおりで `default` が無い、(c) 定数の値（印・上限・9 つの拡張子）。`unit-test-instructions.md` 3.1節のコマンドで流し、3 件が passed・skipped と todo が 0 であることを確かめる（P1〜P4 の確かめ）。読み込みの誤りで流れなければ止めて、5.1節 P3 の切り替え先を依頼者に諮る。

### 層 1: 純粋な関数（置換・残存の検査・木の置換）

- [ ] **Step 4. 形の定義と置換**（LC1。BR1.1・BR1.2・BR1.4）: `JWT_SHAPE` と内部の `scanJwtShapes(text)`（区間の始まりと終わりだけを返す）、`redactJwt(text)`（`{ text, count }` を返す。左から順にすべての件を `JWT_MARK` に替える）を作る。4節のとおりまず正規表現の形で作る。
- [ ] **Step 5. 残存の検査**（LC2。BR2.1・BR2.4・BR2.3）: `findResidual(text)` は `redactJwt` を呼ばずに `scanJwtShapes` だけで調べ、`{ count, positions: [{ line, column }] }`（行は LF で数え、桁は 1 始まりの文字の位置）を返し、値と前後の文字を返さない。latin1 で読んだ文字列に当てたときに位置をバイトの位置として使えるよう、文字の通し番号（`offset`）も返す。`findUnredactedSetup(tree)` は、上の階層がオブジェクトで自分自身のキー `setup_data` を持つときだけ、その下を明示の積み上げでたどり、`SETUP_MARK` でない文字列の件数と道（キー名と配列の位置。キー名は `redactJwt` を通す）を返す。
- [ ] **Step 6. 木の置換**（LC3。BR1.3・BR1.5・BR1.6・BR1.7）: `redactTree(value)` を明示の積み上げ（再帰を使わない）で作る。文字列の値・配列の要素・キー名に `redactJwt` を当て、数・真偽・null は触らない。上の階層の `setup_data` の下の文字列（値そのものが文字列ならそれも）は `SETUP_MARK` に替え、キー名には `redactJwt` だけを当てる。深い所の `setup_data` は特別に扱わない。オブジェクトは元の並び（`JSON.parse` の並び）のとおりに組み直す。同じオブジェクトの中で置換の後の名前が重なれば、`{ ok: false, collisionPath }`（置換の後の名前でたどった道）を返す。成功は `{ ok: true, tree, replacedCount, setupRedactedCount }`。
- [ ] **Step 7. 層 1 のテスト**（test-after）: `frontend/src/perfEvidenceRedactor.test.ts` に次を足し、3.2節のコマンドで流して通す。
  - 補助の関数: `makeJwtLike(seed)`（`'eyJ'` と base64url の区間を実行の時につなぐ。完成した JWT の形をソースに書かない。NFR8.71）、`assertProperty(prop, params)`（`fc.check` の結果を受け、失敗のときは `seed`・`counterexamplePath`・`numRuns` だけを載せて投げる。反例の値を出さない。NFR5.61。再現の手順をファイルの先頭のコメントに書く）、性質のための入力の作り方（任意の JSON・半分は上の階層に `setup_data`、JWT の形と JWT に近い文字列（2 区間・`eyJ` の前に英数字・末尾の `=`・`Bearer ` の前置き・`.` が 4 つ以上）を値・配列の要素・キー名に混ぜる）。
  - 例のテスト（BR5.2 の関数の分、約 20 件）: 2 区間は残る、`eyJ` の前が英数字なら残る、1 つの文字列の複数の件がすべて替わる、`Bearer` の後ろだけが替わる、末尾の `=` は残る、`.` が 4 つ以上は先頭の 3 区間だけ、キー名の件が替わる、キーの衝突で `collisionPath` が返り値を含まない、数 MB（2 MiB）の文字列の中の件が替わる、深い入れ子（1 万段）と大きな配列でも止まらない、`setup_data` の下の招待のトークンに似た架空の文字列（JWT の形でない）と JWT が `SETUP_MARK` に替わり数・真偽・null は残る、`setup_data` の値そのものが文字列・配列のとき、深い所の `setup_data` は特別に扱わない、`findResidual` が置換を通さない文字列で件数と位置だけを返す（値を返さない）、`findUnredactedSetup` が伏せていない文字列の件数と道だけを返す、整数の形のキーの並びは `JSON.parse` の並び、印 `JWT_MARK`・`SETUP_MARK` が形に当たらない、k6 の summary json に似た見本（`metrics`・`root_group`・`setup_data`。中の値は架空）で `metrics` の数・`root_group` の名前・check の名前が前後で同じ。
  - 性質のテスト（fast-check、JSON の 5 つ）: (1) 置換の後の木の `JSON.stringify` に `findResidual` が 0 件、(2) 冪等（2 回と 1 回が JSON として同じ。`setup_data` の内と外）、(3) `setup_data` の外の JWT を含まない値は変わらない、(4) 衝突の無い入力で、読み直した木のキーの集合と並び・値の型が入力（`JSON.parse` した木）と同じ、(5) 上の階層の `setup_data` の下の文字列はすべて `SETUP_MARK`、数・真偽・null は同じ。
  - 性質のテスト（テキスト、3 つ。BR5.4）: (t1) `redactJwt` の後に `findResidual` が 0 件、(t2) 冪等（文字単位で同じ）、(t3) JWT の形を含まないテキスト（任意の Unicode・LF・CRLF・空白・長い行・JSON に似た断片・JWT に近い文字列）は文字単位で変わらない。
  - 定義の突き合わせの性質（4節）: 短いランダムな文字列で `scanJwtShapes` の区間の一覧が、テストの中に置いた同じ正規表現の結果と一致する。
- [ ] **Step 8. 処理の時間の確かめと切り替え**（`security-design.md` 2.1節）: `-eyJ` が続き `.` が無い 1 MiB の文字列に `redactJwt` と `findResidual` を当てる例のテストを足して流す。Vitest の既定の上限の中で終わらなければ、4節のとおり `scanJwtShapes` を 1 回のなめに替え、Step 7 と Step 8 のテストをすべて流し直して通す（時間の上限は延ばさない）。正規表現の形の所要時間と替えたかどうかを `generation-notes.md` に書く。

### 層 2: コマンドの口（JSON の口・テキストの口）

- [ ] **Step 9. 読み書きの守りと報告とコマンドの口**（LC4・LC5・LC7。BR2.2・BR2.3・BR3.1〜BR3.4・NFR8.64〜NFR8.67・NFR6.61・NFR7.61）: `main(argv, io)` を作る。`io` は表示（`stdout`・`stderr` の 1 行ずつ）・読み書き（`stat`・`readFile`・`realpath`・`open`（`wx`）・`write`・`rename`・`unlink`）・中断の登録（`onSignal(handler)`）を差し替えられる口とし、既定は本物の `node:fs` と `process`。
  - 引数（`security-design.md` 5.1節）: 最初の引数が `--text` ならテキストの口、`--scan` なら検査の口（Step 11）、それ以外は JSON の口。引数の数の誤り・出力の親が無い・親を `realpath` で解いたパスに名前がちょうど `evidence` のディレクトリが無い・入力と出力が同じ（入力の `realpath` と、出力の親の `realpath` に名前をつないだもの）→ コード 1。テキストの口は出力の拡張子 `.json`（大文字と小文字を区別しない）もコード 1。
  - 入力: 読む前に `stat` の大きさで `MAX_INPUT_BYTES` を超えればコード 2（上限ちょうどは受ける。読んだバイト数が上限を超えてもコード 2）。JSON の口は `TextDecoder('utf-8', { fatal: true })` で読み、空・空白だけ・UTF-8 の誤り・`JSON.parse` の誤り・深さの `RangeError` はコード 2。テキストの口は NUL を 1 つでも含む・空・空白だけ・`TextDecoder('utf-8', { fatal: true, ignoreBOM: true })` の誤り → コード 2、中身が JSON として読めて上の階層がオブジェクトで `setup_data` を持てばコード 1。
  - 置換: JSON の口は `redactTree` と `JSON.stringify(tree, null, 2) + '\n'`（衝突はコード 3）。テキストの口は中身全体に `redactJwt` を 1 回当て、BOM・改行・末尾の改行を変えない（BR1.8）。
  - 残存の検査と書き出しは 1 つの関数 `checkAndWrite(text, { json }, outputPath, io)` にまとめ、JSON の口とテキストの口の両方が呼ぶ。残存の検査: 書き出す直前の文字列に `findResidual`、`json` のときはあわせてその文字列を `JSON.parse` し直した木に `findUnredactedSetup`。1 件でもあれば一時ファイルを作る前にコード 4。
  - 書き出し: 出力と同じディレクトリに 3節の名前の一時ファイルを `wx` で作って書き、名前を替える。どこかで失敗すれば一時ファイルを消してコード 5。書いている間だけ `SIGINT`・`SIGTERM` の片付けを登録し、受けたら一時ファイルを消してから終える。
  - 報告の部品: 決めた文言の番号・件数・位置・パス・道だけを受け、任意の文字列を受ける口を持たない。パスと道は表示の前に `redactJwt` を通す。`JSON.parse` の誤りは文言の中の `position <数>` の数だけを抜いて位置として出し、ファイルの誤りは `code` だけを出す。文言は日本語だけ（NFR6.61）。成功の表示は置き換えた JWT の件数・`SETUP_MARK` にした件数・出力のパスだけ。
- [ ] **Step 10. 層 2 のテスト**（test-after）: `frontend/src/perfEvidenceRedactorCli.test.ts` を作り（先頭に `// @vitest-environment node`、5.1節 P3 の読み方）、各テストで `os.tmpdir()` の下に一時のディレクトリと `evidence/` を作り、終わりに消す。表示は `io` の差し替えで捕まえる。3.3節のコマンドで流して通す。
  - JSON の口（約 15 件）: 契約 C7 の引数 2 つで成功し、出力が JSON として読め、字下げ 2・LF・末尾の改行 1 つ・BOM なし（NFR7.61）、成功の表示に件数と出力のパスだけ。コード 1（引数が 1 つ・3 つ、出力が `evidence/` の外、出力の親が無い、入力と出力が同じ、記号のリンクで `evidence/` の外へ向く出力の親）、コード 2（入力が無い・空・空白だけ・JSON でない・不正な UTF-8・深すぎる入れ子・上限＋1 バイト（疎なファイル））、コード 3（キーの衝突）、コード 4（置換と検査の独立の確かめ: 書き出しの段の関数 `checkAndWrite` に、置換を通していない JWT の形を含む文字列と、`setup_data` の下に伏せていない文字列を持つ JSON の文字列を直接渡し、どちらもコード 4 で出力も一時ファイルも作られない。あわせて `main` の JSON の口・テキストの口が `checkAndWrite` を通ることを、成功の例の出力で確かめる）、コード 5（`io.rename` を失敗させる。一時ファイルが消え、前からあった出力が変わらない）。各失敗で、出力のパスが起動の前と同じ（無ければ無い・あれば同じバイト列）で、`evidence/` に一時ファイルが残らない（NFR8.65）。
  - テキストの口（約 10 件）: LF・CRLF・末尾の改行の有無・先頭の BOM が変わらない、1 行に複数の件と `Bearer` の後ろが替わる、JSON の口と同じ文字列で `redactJwt` の結果と一致する（BR5.4 の「同じ関数」）、`--text` の後が 2 つでない・出力が `evidence/` の外・出力の拡張子 `.json`・`.JSON`・上の階層に `setup_data` を持つ JSON の入力でコード 1、空・空白だけ・不正な UTF-8・NUL を含む入力でコード 2、`io.rename` の失敗でコード 5、上限ちょうど（`a` が続く 64 MiB）は受ける。
  - 表示（NFR8.64、約 3 件）: 成功・コード 1〜5 の各場合で捕まえた標準出力と標準エラーに、組み立てた JWT の形の値・その 3 区間のどれか・招待のトークンに似た架空の値が含まれない。`JSON.parse` の誤りの表示に入力の断片が出ない（位置の数だけ）。名前に JWT の形を含む出力のパスが `[REDACTED-JWT]` に替わって表示される。
  - 中断（1 件）: `io.onSignal` で登録された片付けを呼ぶと、一時ファイルが消える。
  - 子のプロセス（2 件）: `node:child_process` の `spawnSync(process.execPath, [道具, …])` で JSON の口とテキストの口を本物のプロセスとして起動し、終わりのコード（0 と 1 の 2 通り）が `main` の戻り値と同じになる（`security-design.md` 7.1節）。

### 層 3: evidence の検査の口

- [ ] **Step 11. evidence の検査の口**（LC6。BR4.2・NFR8.68・NFR8.66）: `--scan <根>` を `main` に足す（5.3節の判定の順・表示・終わりのコード）。
- [ ] **Step 12. 層 3 のテスト**（test-after）: `frontend/src/perfEvidenceScan.test.ts` を作り（先頭に `// @vitest-environment node`）、`os.tmpdir()` の下に作った根に `evidence/` を置いて、`security-design.md` 8.5節の表の行をすべて例のテストにする（約 15 件）: JWT の形を含む `.md`・`.log`・`.csv`・拡張子なし → コード 4 でパスと位置が出て値が出ない、名前に JWT の形を含むファイル → コード 4 で表示のパスが `[REDACTED-JWT]`、NUL を含む `.json`・`.md`・`.txt`・`.log`・`.tsv`・`.csv`・`.yaml`・拡張子なし・`.gitkeep` → コード 2 でパスが出て中身が出ない、NUL を含む `.png`・`.PNG`・`.zip`・`.tar.gz`・`.pdf`（JWT の形なし）→ バイナリとして数えてコード 0、NUL を含み ASCII の JWT の形がそのまま入った `.png` → コード 4 でバイトの位置が出て値が出ない、NUL を含まない `.png`（中身が JWT の形のテキスト）→ テキストとして調べコード 4、上限＋1 バイト（疎なファイル）→ コード 2 でパスと大きさだけ、記号のリンク → たどらずパスが出てコード 0、`evidence` の外のファイル → 対象にしない、検出と NUL 混じりが両方 → コード 4 で両方の件数、根が無い → コード 1、1 ファイルに 11 件以上 → 位置は 10 件まで、`.` で始まる一時ファイルの名前も調べる。子のプロセスで `--scan` を起動する例を 1 件置く。3.4節のコマンドで流して通す。
- [ ] **Step 13. 64 MiB の所要時間と見せるもの**（`security-design.md` 2.1節・Bolt B5 の「見せるもの」）: リポジトリの外（`os.tmpdir()` の下）の `evidence/` に、`.` が無く `-eyJ` が続く 64 MiB に近いファイルを作り、`time node perf/redact-k6-evidence.mjs --scan <その根>` で所要時間を 1 回測る。値（と Step 8 で替えたかどうか）を `code-summary.md` に書き、依頼者に伝える。あわせて、架空の JWT の形を実行の時に組み立てて入れた架空の summary json を JSON の口に通した前後の件数（置き換えた件数・`SETUP_MARK` の件数・検査の 0 件）と、残存の検査が失敗する例（コード 4）を、値を表示せずに件数と終わりのコードで記録する。作ったファイルは消す。

### 組み込み・文書

- [ ] **Step 14. 導入の前の一度の実行**（`security-design.md` 8.6節・機能設計 3節・レビュー R-02 の決定）: `node perf/redact-k6-evidence.mjs --scan aidlc` を単独で流し、調べた数・バイナリとして読んだ数とそのパス・リンクの数・検出 0 件・入力の誤り 0 件を `generation-notes.md` と `code-summary.md` に書く。検出、または飛ばしてはいけない NUL 混じり・上限超え・読めないファイルが 1 件でもあれば、**そこで止めて依頼者に諮る**（過去の記録を自分の判断で置き直さない）。表示の確かめのために中身を `cat`・`grep` で表示しない。
- [ ] **Step 15. Gradle への組み込み**（LC8。A1。NFR8.68）: 5.2節のとおり `build.gradle.kts` に 3 つのタスクを足し、`verifySecurity` の一員の一覧と段の説明の文言の 2 か所、`verifyFormat`・`verifyLint` の一覧と説明、`verifyLicense` の説明を直す。`./gradlew perfEvidenceScan perfToolFormatCheck perfToolLint` が通ることを確かめる（`spotlessCheck` の Gradle の Kotlin DSL のヘッダーの検査も通す）。
- [ ] **Step 16. ライセンスヘッダーの検査の対象を広げる**（A2。NFR9.65）: 変える前に、変えた後と同じ判定を `perf/` の `.mjs`・`.js`（8 ファイル）に当てて通ることを確かめる（通らないものがあれば止めて、対象を道具のファイルだけに絞るかを依頼者に諮る）。`frontend/scripts/check-license-header.mjs` を直し、`./gradlew frontendLicenseCheck` が通ること、道具のファイルの先頭を一時に崩した写しが落ちること（作業フォルダの外の写しで確かめ、元に戻す）を確かめる。
- [ ] **Step 17. 台本の読み取りの確かめ**（NFR8.70・AC4.1.9）: `perf/k6/scenarios.js` の `console.log`・`console.error`（今 6 か所）・タグの値・check の名前・group の名前・`setup()` の返す値を読み取りだけで確かめ、秘密の値（トークン・Cookie・Authorization ヘッダー・招待のトークン・パスワード）を `setup_data` の外と標準出力・ログに出していないことと、リフレッシュトークン（Cookie `mastersmith_refresh` は VU ごとの状態だけ）と Authorization ヘッダー（要求ごとに組み立て）が summary json に入らないことを、行の番号で `code-summary.md` に書く（値は書かない）。出しているものが見つかれば止めて依頼者に諮る（BR6.2）。
- [ ] **Step 18. README の節**（LC10。A3。BR6.1・NFR8.61・NFR8.69・NFR8.70・AC4.1.7・AC4.1.8）: `perf/README.md` の末尾に H2「記録（evidence/）に置く前の伏せ字」を 1 つ足し、その下に H3 で次を書く。U5 が整理した README にそのまま収められるよう、**Intent の名前（`26` で始まる 6 桁）と日付を書かない**（U5 の Q5）。`evidence/` を含む行は道具の呼び方の行と検査（`--scan`・`gitleaks dir`）の行だけにし、`cp`・`mv`・`>`・`>>`・`tee` を同じ行に書かない（U5 の Q2）。
  1. 伏せる対象: k6 の summary json。JWT の形は全体で `[REDACTED-JWT]`、`setup()` の返す値（`setup_data`）の下の文字列はすべて `[REDACTED-SETUP]`（数は残る）。
  2. 記録に置く唯一の入口と 2 つの呼び方（JSON の口・`--text` の口）、終わりのコード 0〜5 の表、`cp`・手での編集・貼り付けで置かないこと、手で置いても verify の `perfEvidenceScan` で落ちること（JWT の形のとき）。
  3. summary json の対象外（k6 の標準出力とログ）の伏せ方: 使い捨ての置き場に受ける `k6 run … > build/perf-results/<場面>.log 2>&1`、`node perf/redact-k6-evidence.mjs --text build/perf-results/<場面>.log <evidence/ の中のパス>.log`、`setup_data` の伏せ字は当たらないこと、`.json` の出力と summary json の入力は受け付けないこと。
  4. 置いた後の確かめ: `node perf/redact-k6-evidence.mjs --scan <置いた evidence/ のディレクトリ>` と `gitleaks dir --redact --no-banner --config .gitleaks.toml --exit-code 1 <置いた evidence/ のディレクトリ>` の両方で 0 件を確かめてからコミットを提案する。
  5. 台本の決まり: 秘密の値を `setup_data` の外（タグ・check・指標・group の名前）と標準出力・ログに出さない。JWT の形でない秘密は置換でも検査でも拾えず、この決まりと `gitleaks dir` に頼ること。リフレッシュトークンと Authorization ヘッダーが summary json に入らないことを確かめた結果（Step 17）。
  6. 一時ファイルの名前の形（`.<出力の名前>.<プロセス番号>.<乱数>.tmp`）と、強制の終了で残ったときの消し方、evidence の検査がそれも調べること。バイナリの拡張子の一覧（9 つ）と、足すときは依頼者の承認が要ること。
- [ ] **Step 19. 秘密の形の検索**（NFR8.71）: `unit-test-instructions.md` 5節の検索で、道具・3 つのテストのファイル・`perf/README.md` に完成した JWT の形が 0 件であることを件数だけで確かめ、`gitleaks dir` を新しいファイルに流して 0 件を確かめる。
- [ ] **Step 20. verify と時間**: `unit-test-instructions.md` 3.5節のテストを流して通し、4節の単位のカバレッジの確かめ（道具だけ）の値を記録する。続けて `./gradlew verify`（colima の環境変数を付け、`caffeinate -dims` で包む。流した後に `pmset -g log` で Sleep・DarkWake が無かったことを確かめる）を通し、U4 の 3 ファイルの passed の件数（skipped・todo 0）、`perfEvidenceScan` が流れて成功したこと、verify の時間を `code-summary.md` に書く（`team.md`。42 分の線）。バックエンドのソースは変えていないため、バックエンドのテストの件数は報告しない（報告するなら `:backend:cleanTest :backend:cleanIntegrationTest` を付けて流し直す。`project.md`）。

### 記録と統合

- [ ] **Step 21. 記録**: `code-summary.md`（作ったもの・決めた名前・P1〜P7 の結果・Step 8 と Step 13 の時間・Step 14 の件数・Step 17 の結果・計画との差・依頼者に確かめたいこと）、`source-manifest.json`（9節の変えたパスのすべて）、`traceability.json`（10節の対応）を書く。
- [ ] **Step 22. コミットと統合**: 9節の区切りでコミットを提案し、依頼者の承認を得てから積む。最初のコミットの時点で書式・リンタ・型の検査が通ることを確かめる。`aidlc/` の未コミットの変更を先にコミットしてから、`./gradlew verify` が通った状態で `develop` へ squash で統合し、ブランチを消す。統合の後の CI の確かめと結果の記録は Build and Test に引き継ぐ（11節）。コード生成の段の承認の場は、最後のコードのコミットの後、記録だけのコミットを積む前に開く（`project.md` の学び）。

## 9. コミットの区切り（提案の件名。積む前に依頼者の承認を得る）

| 区切り | 件名（案） | 中身 |
|---|---|---|
| C1 | k6 の結果の JWT を伏せて記録に置く道具とテストを足す | `perf/redact-k6-evidence.mjs`・`frontend/src/perfEvidenceRedactor.test.ts`・`frontend/src/perfEvidenceRedactorCli.test.ts`・`frontend/src/perfEvidenceScan.test.ts`（Step 2〜13） |
| C2 | verify に記録の JWT の形の検査と道具の書式・リンタ・ヘッダーの検査を足す | `build.gradle.kts`・`frontend/scripts/check-license-header.mjs`（Step 15・16） |
| C3 | perf の README に記録に置く前の伏せ字の節を足す | `perf/README.md`（Step 18） |
| 統合 | k6 の結果の JWT の伏せ字の道具と記録の検査を足す（Intent 261029-reference-lookup-cleanup の B5） | squash（`develop` の 1 コミット） |

変えるパスの一覧（`source-manifest.json` の元）: `perf/redact-k6-evidence.mjs`（新規）、`frontend/src/perfEvidenceRedactor.test.ts`（新規）、`frontend/src/perfEvidenceRedactorCli.test.ts`（新規）、`frontend/src/perfEvidenceScan.test.ts`（新規）、`build.gradle.kts`、`frontend/scripts/check-license-header.mjs`、`perf/README.md`。

## 10. ストーリー・要件と手順の対応

| ストーリー・要件 | 中身 | 手順 | 主なテスト・確かめ |
|---|---|---|---|
| AC4.1.1・BR1.1〜BR1.7・NFR8.62 | JWT の形の置換（値・配列・キー・長い文字列・`Bearer`）、衝突の失敗、`setup_data` | 4・6・7・9・10 | `perfEvidenceRedactor.test.ts` の例と性質 (1)〜(5)、`perfEvidenceRedactorCli.test.ts` のコード 3 |
| AC4.1.2・BR2.1・BR2.4・NFR8.63 | 置換と独立の残存の検査 | 5・7・9・10 | `findResidual`・`findUnredactedSetup` の例、コード 4 |
| AC4.1.3・BR2.2・BR3.3・NFR8.65 | 失敗で出力も一時ファイルも残さない | 9・10 | コード 1〜5 の出力のパスの前後と一時ファイル、中断の片付け |
| AC4.1.4・BR3.1・BR3.2・NFR8.66・NFR8.67 | 入力・引数・上限の誤り | 9・10・12 | コード 1・2、64 MiB の境界 |
| AC4.1.5・BR5.1・BR5.4・NFR5.61 | 性質ベースのテストと種の表示 | 7 | 性質 (1)〜(5)・(t1)〜(t3)・定義の突き合わせ、`assertProperty` |
| AC4.1.6・BR5.3・NFR5.62・NFR5.63 | verify と CI で飛ばされずに流れる、`gitleaks dir` | 3・20（実際の summary json と CI は 11節） | `frontendTest` の passed の件数、skipped・todo 0 |
| AC4.1.7・BR4.1・BR4.2・NFR8.61・NFR8.68 | 唯一の入口、verify の evidence の検査 | 11・12・14・15・18 | `perfEvidenceScan.test.ts`、導入の前の一度の実行、`perfEvidenceScan` |
| AC4.1.8・BR1.8・BR3.4・BR6.1 | 対象外（標準出力・ログ）の伏せ方の手順、テキストの口 | 9・10・18 | テキストの口の例と (t1)〜(t3)、README の節 |
| AC4.1.9・BR6.2・NFR8.70 | リフレッシュトークンと Authorization ヘッダーが入らないこと、台本の決まり | 17・18 | 読み取りの確かめの記録、README の節 |
| BR2.3・NFR8.64 | 表示に値を出さない | 9・10・12（実際のログは 11節） | 表示を捕まえる例のテスト |
| BR4.3・NFR8.69 | 置いた後の `--scan` と `gitleaks dir` | 18（実行は 11節） | README の節 |
| NFR8.71 | テストのソースに完成した JWT の形を書かない | 7・19 | 検索 0 件、`gitleaks dir`、`gitleaksScan` |
| NFR9.61〜NFR9.64 | 依存を足さない、Node 24 | 2・22 | `import` が `node:` だけ、`frontend/package.json` と lockfile の差が無い |
| NFR9.65 | ライセンスヘッダーとその検査 | 2・16 | `frontendLicenseCheck` |
| NFR7.61・NFR6.61 | C7 の呼び方を変えない、終わりのコードの固定 | 9・10 | 引数 2 つの例、コードごとの例、子のプロセスの例 |

## 11. Build and Test に引き継ぐこと

基盤の設計の承認の場で「直してほしい」と記録した R-05（NFR8.64 の実際のログでの確かめ）を含め、次をこの段では行わず Build and Test に引き継ぐ。`traceability.json` では該当の行を Deferred（Build and Test）にし、target にこの節を書く。

| # | 確かめること | 要件 |
|---|---|---|
| H1 | **わざと残存を作った 1 回の verify（実際のログで値が出ないことの確かめ）**: 作業フォルダの `aidlc/` の下の `evidence/` に、実行の時に組み立てた架空の JWT の形の値（本物のトークンは使わない）を入れたファイルを一時に置き、`./gradlew perfEvidenceScan`（または `verify`）が終わりのコード 4 で失敗すること、**Gradle の失敗の要約（`Process 'command 'node'' finished with non-zero exit value 4` など）を含む手元の出力の全体に、その値とその 3 区間のどれも出ていないこと**を件数で確かめる。ファイルはコミットせず、確かめた後に消し、消したことと `git status` に残っていないことを記録する。この段の NFR8.64 の根拠は報告の部品の例のテスト（Step 10・12）までで、実際のログでの確かめはこの H1 が持ち主 | NFR8.64（基盤の設計 R-04・R-05） |
| H2 | U4 の 3 つのテストのファイルごとの passed の件数（skipped・todo 0、passed 1 以上）を、手元の verify と CI の結果から記録する | NFR5.62・AC4.1.6 |
| H3 | 統合の後の CI で `perfEvidenceScan` と U4 のテストが流れて成功したことを、試行ごと（`gh run view --json attempt,jobs`）に記録する | NFR8.68・AC4.1.6・`project.md` の CI の記録の決まり |
| H4 | 実際の k6 の summary json を道具の JSON の口で `evidence/` に置き、`--scan` と `gitleaks dir` で 0 件を確かめる | AC4.1.6・NFR8.69 |
| H5 | verify と CI の時間（42 分の線） | `team.md` |
| H6 | テストのソースに完成した JWT の形が 0 件であることの検索を、統合の後の `develop` でもう一度流す | NFR8.71 |
| H7 | Intent の最後の E2E を 1 回流す（この Bolt は統合の前に流さない。1節） | `team.md` |

## 12. 計画の承認で確かめたいこと（推奨と理由）

- **Q1. 書式とリンタの検査の入れ方（5.1節 P6・A1）。** 推奨: A（ルートの `build.gradle.kts` に `perfToolFormatCheck`・`perfToolLint` を足し、`frontend/node_modules/.bin/` の Prettier と oxlint を画面の設定のファイルで呼ぶ）。`frontend/package.json` の差を無くす NFR9.62 の合否を守れ、verify の段の中に収まるため。B は `frontend/package.json` の `format:check`・`lint` のコマンドに道具のパスを足す（変える所は少ないが NFR9.62 の合否と食い違う）。
- **Q2. ライセンスヘッダーの検査の対象（A2）。** 推奨: A（`perf/` の `.mjs`・`.js` のすべて、8 ファイル＋道具）。既存のファイルはどれもヘッダーを持つ見込みで、足す守りになるため（Step 16 で先に確かめ、通らなければ諮る）。B は道具のファイルだけを足す（既存のファイルに影響しないが、`perf/` の新しい `.mjs` が今後も検査の外に残る）。
- **Q3. 書式とリンタを既存の `perf/*.mjs` に広げるか。** 推奨: A（広げない。道具のファイルだけ）。既存の 7 ファイルは今まで Prettier と oxlint が当たっておらず、広げると Bolt の外の書式の直しが要りうるため。B は広げる（揃うが、差の大きいコミットが増える）。
- **Q4. 道具の型の検査（5.1節 P7）。** 推奨: A（`tsconfig.json` を変えず、道具は `tsc` の対象にしない。テストは型の interface で `any` なしに検査する）。全体の設定を変えずに済み、前例（`eslint.config.js`・`frontend/scripts/*.mjs`）と同じため。B は `tsconfig.json` に `allowJs` を足し、道具の先頭に `// @ts-check` を置いて JSDoc で検査する（道具も検査されるが、全体の設定が変わる）。
- **Q5. 1 回のなめへの切り替え（4節）。** 推奨: A（設計どおり、Step 8 で正規表現の形を 1 MiB で測り、上限を超えたら替える）。見立てでは替えることになるが、測った値で決める設計の決まりを守るため。B は最初から 1 回のなめで作る（手戻りは減るが、設計の「両方で決める」と食い違う）。
- **Q6. README の節の置き場（A3）。** 推奨: A（今の `perf/README.md` の末尾に H2 の節を 1 つ足し、Intent の名前と日付を書かない）。U5 が整理で見出しと中身をそのまま収められ、U5 の Q2・Q5 の検索に当たらないため。B は先頭の「手順」の節の中に入れる（読み手は見つけやすいが、U5 の整理で切り分けが要る）。
- 6節の A1〜A3（既存のファイルの変更）を承認するか。

## Sources

- 段の定義: `.claude/aidlc-common/stages/construction/code-generation.md`
- この単位の設計: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u4-perf-evidence-redactor/functional-design/`（`functional-spec.md`・`rules.md`・`entities.md`・`reviews/review-02.md`）、`nfr-requirements/`（`security-requirements.md`・`tech-stack-decisions.md`・`reviews/review-02.md`）、`nfr-design/`（`security-design.md`・`logical-components.md`・`nfr-design-questions.md`・`reviews/review-02.md`）、`infrastructure-design/`（`cicd-pipeline.md`・`reviews/review-02.md`）
- Inception: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/delivery-planning/bolt-plan.md`（B5）、`units-generation/unit-of-work.md`（U4）、`requirements-analysis/requirements.md`（FR5・NFR5・NFR8）、`user-stories/stories.md`（US4.1）、`contract-design/contract-summary.md`（C7）、`domain-design/decisions.md`（ADR-004・ADR-006）
- U5 との継ぎ目: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u5-container-perf-docs/nfr-design/security-design.md`（5.1節・5.2.1節・5.6節 Q2・Q5）
- 承認の場の決定: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/audit/sakura-local-4e42a93f87ce.md`（機能設計の R-01〜R-03 と R-04 の「直してほしい」、NFR 要件の R-03 の「直してほしい」と NFR 設計の Q1: A、NFR 設計の R-01〜R-03、基盤の設計の R-01〜R-04 の直しと R-05 の「直してほしい」）
- 既存のコード（読み取りだけ）: ルートの `build.gradle.kts`（`checkToolchain`・`npmScript`・`frontendTest`・`frontendCoverage`・`gitleaksScan`・`osvScan`・`verifyStages`）、`frontend/vitest.config.ts`・`frontend/vitest.setup.ts`・`frontend/tsconfig.json`・`frontend/eslint.config.js`・`frontend/.oxlintrc.json`・`frontend/.prettierrc.json`・`frontend/.prettierignore`・`frontend/package.json`・`frontend/scripts/check-license-header.mjs`・`frontend/src/eslintImportRules.test.ts`、`.pre-commit-config.yaml`、`.gitignore`、`perf/` のファイルの一覧と先頭の行、`perf/README.md` の見出し
- `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 12節の Q1〜Q6 は推奨の A で書いた。違う答えのときは該当の手順と 6節を直す。
- [assumption] Vitest は `frontend/` の外の `.mjs` を `import()` で読める（Step 3 で確かめ、読めなければ止めて諮る）。
- [assumption] Prettier と oxlint は、設定のファイルを明示すれば `frontend/` の外のファイルを検査できる（Step 15 で確かめる）。
- [assumption] テストの件数（Summary）は見積もりで、実際の数は Build and Test で実測する。
