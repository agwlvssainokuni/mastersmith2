# Security Design — U2 ページ送りの共通化（u2-shared-paging）

U2 のセキュリティの設計です。承認済みの NFR 要件 `aidlc/spaces/default/intents/260930-user-admin/construction/u2-shared-paging/nfr-requirements/security-requirements.md`（NFR3.1・NFR5.1・NFR5.2・NFR9.1〜NFR9.5・NFR11.1、残る危険 R1・R2、承認の場の決定 R-01〜R-05）と `tech-stack-decisions.md`（NFR9.6〜NFR9.11）を満たす作りを決めます。U2 は種類 library の単位のため、性能・観測の作りは別の文書にせず、この文書の 6節に置きます。部品の一覧と障害の範囲は `logical-components.md` にあります。

U2 は、招待の一覧のページ送りの計算（サーバーの `InvitationPaging`、画面の `paging.ts`）を、口と振る舞いを変えずにサーバーの `cherry.mastersmith.common.paging.Paging` と画面の `frontend/src/shared/paging/paging.ts`（UiPaging）へ移すだけの単位です。状態も保存するデータも持たない純粋な関数で、新しい API・新しい依存・スキーマの変更はありません。

この段の質問 `nfr-design-questions.md` の設計の要点 1〜9 と答え（Q1 A）、まとめの確認（Looks correct）で決めました。プラットフォームの視点（配備先は開発者の PC 上のコンテナ）は 8節に重ねて書きました。

出典の略号: NR は `nfr-requirements/security-requirements.md`、TS は `nfr-requirements/tech-stack-decisions.md`、FS は `functional-design/functional-spec.md`、BR は `functional-design/rules.md` の決まり、要点 n はこの段の `nfr-design-questions.md` の設計の要点、CS は `aidlc/spaces/default/intents/260930-user-admin/inception/contract-design/contract-summary.md`、TM は `aidlc/spaces/default/memory/team.md`、PM は `aidlc/spaces/default/memory/project.md`。コードのパスは `backend/src/main/java/cherry/mastersmith/` の下を `cherry.mastersmith` からのパッケージ名で書きます。

## 1. 信頼の境界と守りの配置

| 境界 | 入ってくるもの | 守り | 置き場 |
|---|---|---|---|
| 一覧の API の問い合わせ（招待の `GET` の一覧、後の U3 の利用者の一覧） | 利用者が送った page の文字列（長さの上限は要求の行の上限だけ） | `Paging.parsePage` の1か所で検証し、空なら呼び出し元が 400 `VALIDATION_FAILED` にする | `common.paging.Paging`（2節）と呼び出し元の業務処理・controller |
| 呼び出し元の業務処理から Paging（アプリの中の呼び出し、契約 C2） | 検証済みの page・位置 | 1 未満は IllegalArgumentException（プログラムの誤り）。offsetOf は long で計算する | `common.paging.Paging`（3節） |
| 一覧の応答を受けた画面から UiPaging（画面の中の呼び出し、契約 C5） | サーバーが返した page・total・行の数 | 計算だけで、サーバーの検証の代わりにしない | `frontend/src/shared/paging/paging.ts`（2節） |
| 機能の間の依存 | import | `common.paging` と `src/shared/paging/` はどの機能にも依存しない | 既存の ArchUnit の境界テストと画面の import の向き（5節） |

Paging・UiPaging は状態を持たず、内部DB・外部・設定値に触れません。守りの中心は、利用者が送った page の文字列をサーバーの1か所で正しく拒否することと、移す途中で招待の振る舞いを変えないことです。

## 2. page の入力の検証（NFR9.1・NFR9.3、要点 2）

- 検証はサーバーの `Paging.parsePage(String raw)` だけを正とする。規則は今の `InvitationPaging.parsePage` のまま変えない（BR1.2・BR1.5）。`null`（指定なし）は 1、正規表現 `[0-9]{1,9}` に全体が合い 1 以上の値だけを受け、それ以外（`0`・負の数・小数・数字でない・空・前後の空白・符号つき・10 桁以上）は空を返す。9 桁までに限るため `Integer.parseInt` は桁あふれの例外を起こさない。
- 空のとき、呼び出し元の業務処理は件数を数えずに想定内の失敗（招待では今の `ListResult` の誤りの場合）を返し、controller が `switch` で既存の `VALIDATION_FAILED`（Problem Details、`code` つき）の `BusinessException` に変える。新しい code は足さない。応答に例外のメッセージとスタックトレースを載せないのは、既存の `@RestControllerAdvice` の仕組みのまま（TM の Code Style）。
- 拒否は監査に残さない。読み取りの一覧の操作で、利用者の権限・状態を変えないため（BR3.1）。
- 画面の UiPaging は、サーバーが返した値から表示と補正を計算するだけで、page の検証をしない。画面の値の誤りはサーバーの検証で拒否される（TM の Code Style「検証はサーバー側を正とする」）。

説明のための流れ（呼び出し元の業務処理。招待の今の形と同じ）:

```text
parsed = Paging.parsePage(rawPage)          // 唯一の検証
if parsed is empty -> 誤りの結果（件数を数えない・監査なし）
page   = parsed の値
total  = 条件に当たる件数を数える
rows   = Paging.offsetOf(page) >= total ? 空
         : PageRequest.of(page - 1, Paging.PAGE_SIZE) で読む
```

## 3. 桁あふれと読み取りの位置（NFR9.2、要点 3）

- `offsetOf(int page)` は `(long) (page - 1) * PAGE_SIZE` で計算し、`Integer.MAX_VALUE` の page でも負の位置や桁あふれを起こさない。`pageOf(long position)` は `Math.toIntExact` で int に戻す。どちらも 1 未満は IllegalArgumentException で、parsePage を通った値では起きない（今の実装のまま）。
- 「行を読まずに空にする」判定（`offsetOf(page) >= total`）と実際の読み取りの位置（`PageRequest.of(page - 1, PAGE_SIZE)`）は、同じ `page` と同じ `PAGE_SIZE` から導く。呼び出し元で別の値を使わない。招待では今の `InvitationService#list` のまま参照先だけを変え、U3 の利用者の一覧も同じ形にする（BR3.2、NR の残る危険 R2）。
- 確かめは、移した `PagingTest` に `offsetOf(Integer.MAX_VALUE)` と 1 未満の例外の事例を残し、jqwik の性質（offsetOf(page + 1) − offsetOf(page) が 20、pageOf(offsetOf(page) + 1) が page に戻る）を足す。判定と読み取りの位置の一致は、招待の一覧の既存の結合テスト（最後のページと、その次の空の 200）と、U3 の「最後のページより後」の結合テストで確かめる。

## 4. 個人に関する値と TRACE のログ（NFR3.1、要点 4・5、Q1 A）

### 4.1 口の型（要点 5）

Paging・UiPaging の口は、parsePage の引数の page の文字列を除き、page・total・行の数・位置・ページの向き（`'prev' | 'next'`）の整数と列挙だけを受け渡します。メールアドレス・氏名・トークン・検索の文字を持たないため、`toString` の伏せ字と `*SecretLeakIT` は U2 では足しません。検索の文字（`q`）の伏せ字の型 SearchText は U3 の受け持ちです（CS の C3）。

### 4.2 TRACE のログに出うる範囲（要点 4）

`common.observability.TraceAspect` は Spring AOP の助言で、`web`・`service`・`domain`・`repository` の層に当たる Spring の部品のメソッドの呼び出しだけを包みます（`TraceAspect.EXPRESSION`）。Paging は素のクラスの静的なメソッドで、Spring の部品でも、4つの層の名前のパッケージでもないため、包まれません。UiPaging は画面のコードで、サーバーのログに関係しません。

page の文字列が TRACE のログに出うるのは、それを受ける呼び出し元の引数だけです。

| 呼び出し元 | 引数 | 出うる条件 |
|---|---|---|
| 招待 `invitation.web.InvitationAdminController#list(String page)` | page の文字列 | そのクラスのロガーを TRACE にしたとき |
| 招待 `invitation.service.InvitationService#list(String rawPage)` | 同じ文字列 | 同上 |
| 後の U3 の利用者の一覧の controller と業務処理 | 同じ形で page の文字列を受ける | 同上 |

### 4.3 受け入れた残る危険（Q1 A）

長い page の文字列が、TRACE を有効にしたときに呼び出し元の引数としてそのままログに出ることを、受け入れた残る危険とします（10節の R3）。根拠は次の3つです。

1. page の文字列は個人に関する値・秘密ではない。PM の Forbidden（パスワード・トークン・メールアドレスなど）に当たらない。
2. TRACE は既定で無効で、対象のクラスのロガーを TRACE にしたときだけ出る（`backend/src/main/resources/application.yaml` の `mastersmith.trace` の説明）。
3. 長さは要求の行の上限で頭打ちになる見込み。`application.yaml` は `server.max-http-request-header-size` を変えておらず、Spring Boot の既定の 8KB が上限になる（コード生成のレビューで確かめる）。

招待の controller と業務処理の形（`String` で受ける）は変えません。長さで先に拒否する案（Q1 B）と、伏せ字の型で受ける案（Q1 C）は選びませんでした。B は controller の引数そのものは TRACE に出るため守りが業務処理の側だけになり、招待の拒否の場所が変わります。C は契約 C2 の口（`String` を受ける parsePage）の手前に型を足すことになり、純粋な関数の移設という U2 の範囲を超えます。

コード生成のレビューでは、出うる範囲が 4.2 の表の呼び出し元の引数だけであること（Paging が Spring の部品にされていないこと、page の文字列をほかのログ・トレースの属性・エラー応答に出していないこと）を確かめます（NR の承認の場の申し送り R-02）。

## 5. 構造の境界（NFR11.1、要点 1・6）

- サーバーの `common.paging` は JDK（`java.util.OptionalInt`・`java.util.regex.Pattern`）だけに依存し、`invitation`・`user` などの機能にも Spring にも依存しない。Spring の部品（`@Component`）にしない（TS の「選ばなかったもの」）。
- 既存の `ArchitectureTest` と `InvitationBoundaryArchitectureTest` は書き換えず、緩めない。`invitation` から `common` への依存は今も許されている向きのため、参照先を変えても境界の検査は変更なしで通る見込み（コード生成で `./gradlew verify` で確かめる）。U2 は新しい機能のパッケージではないため、`<機能>BoundaryArchitectureTest` は足さない。
- 画面は機能どうしで直接 import せず、`frontend/src/shared/paging/` から使う。`InvitationList.tsx`・`useInvitationAdmin.ts` の import の場所だけを変える。export は名前つきだけで、`export default`・`enum` を使わない（今の `paging.ts` のまま、ESLint で強制）。
- `common.paging` に `package-info.java` は置かない（TS の NFR9.9、NR の承認の場の決定 R-05）。

## 6. 性能と観測（NFR5.1・NFR5.2、要点 7）

| 観点 | 作り |
|---|---|
| 性能 | 数回の整数の計算と1回の正規表現の照合だけで、応答時間に効かない。内部DB の問い合わせは、呼び出し元の数える1回と読む1回のままで増やさない。最後のページより後と page の誤りでは行を読まない（誤りでは数えもしない）。新しい性能の目標と k6 の場面は足さない。招待の一覧の応答時間（同時 10 件で p95 1 秒）は、既存の k6 の場面 `invitationList` で Performance Validation が確かめる |
| 規模 | 状態を持たないため、同時の要求で共有するものは不変の定数（`PAGE_SIZE`、コンパイル済みの `Pattern`）だけ。`Pattern` はスレッド安全で、要求ごとの割り当ては照合の1つだけ |
| 信頼性 | 外部への接続・再試行・時間切れが無い。正しい呼び出しで例外を投げない（4つの口のうち例外は 1 未満の引数だけ）。失敗の扱いは呼び出し元の既存の仕組み（400 と Problem Details）に任せる |
| 観測 | 新しい指標・警報・ログを足さない。Paging の中でログを出さない。招待の一覧の既存の指標（`http.server.requests`）とログはそのまま。既存の警報の決まり（`docker/monitoring/provisioning/alerting/mastersmith.yaml`）を変えない |

## 7. 静的解析・テスト・カバレッジ（NFR9.4〜NFR9.11、要点 8）

| 項目 | 作り | 要件 |
|---|---|---|
| 静的解析 | SpotBugs ＋ FindSecBugs（priority 1 と `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION`）、oxlint・ESLint のセキュリティ系のルール、Gitleaks、OSV-Scanner を、除外を足さずに通す。Paging は SQL・乱数・メールに触れない | NFR9.4 |
| 振る舞いを変えない | 招待のテスト（サーバー・画面とも）は import と参照先の変更だけで通す。中身を書き換えない。移すテストは今の事例をすべて残す | NFR9.5 |
| 性質ベースのテスト | jqwik は `@Property(tries = 500)` に、fast-check は既定の 100 回にそろえ、FS の 6節の性質（jqwik 3つ・fast-check 4つ）を足す | NFR9.6 |
| 乱数の種 | jqwik は失敗の報告の seed を `@Property(seed = "...")` で、fast-check は seed と path を `fc.assert` の第2引数で再現する。既存の設定（`junit-platform.properties`・`backend/build.gradle.kts` のテストの出力）は変えない。移すテストの先頭の説明文に再現の仕方を書く | NFR9.7 |
| カバレッジ（サーバー） | `invitation.domain` はコード生成の計画で今の値を実測し、切り替えの後に `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で測り直す。下回ったら除外を増やさず、ほかのクラスのテストを足す。`common.paging` は新しいパッケージとして自動でパッケージごとの下限の対象で、移す単体テストで parsePage の分かれ道と2つの例外を通る。`packagesJudgedByTotal` は変えない | NFR9.8・NFR9.9 |
| カバレッジ（画面） | 全体の合計（`frontend/vitest.config.ts` の `thresholds`）のまま判定し、`src/shared/paging/` を計測から外さない | NFR9.10 |
| 依存 | 新しい依存を足さない。`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json` を変えない。テストの置き場は `backend/src/test/java/cherry/mastersmith/common/paging/PagingTest.java` と `frontend/src/shared/paging/paging.test.ts`、説明文は英語 | NFR9.11 |

## 8. プラットフォームの視点（配備先は開発者の PC 上のコンテナ）

- U2 は WAR の中のクラスと画面の束（`dist`）の中のモジュールが移るだけで、イメージ・`compose.yaml`・`.env`・ボリューム・JVM の設定に変更がありません。基盤の設計（infrastructure-design）に渡す論点はありません（PM の Deployment の学び）。
- スキーマの変更が無いため、戻しは直前の版のイメージだけで済み、内部DB のバックアップの要否に影響しません。
- 要求の行の上限（4.3 の根拠 3）は、アプリの前に入れる仕組み（逆向きのプロキシなど）が無い今の配備では、アプリの組み込みのサーバーの既定値で決まります。配備先が決まり前段に別の上限が入っても、上限が小さくなる向きのため、この設計の判断は変わりません。

## 9. セキュリティのテストの対応

| 確かめること | テスト | 要件 |
|---|---|---|
| 拒否する入力（`0`・`-1`・`1.5`・`abc`・空・` 1`・`+1`・`9999999999`）が空になる。指定なしは 1 | `PagingTest`（移す事例）と jqwik の性質（1〜999,999,999 は受ける、数字以外・10 文字以上・`"0"` は空） | NFR9.1 |
| 招待の一覧の API が不正な page を 400 `VALIDATION_FAILED` で拒否し、本文の項目が変わらない | 招待の一覧の既存の 400 の結合テスト（変更なしで通す） | NFR9.1・NFR9.3 |
| offsetOf の桁あふれが無い・1 未満は例外 | `PagingTest`（`offsetOf(Integer.MAX_VALUE)`・1 未満の例外）と jqwik の性質 | NFR9.2 |
| 空にする判定と読み取りの位置が食い違わない | 招待の一覧の既存の結合テスト（最後のページとその次の空の 200）、U3 の「最後のページより後」の結合テスト | NFR9.2 |
| 境界が崩れない | 既存の `ArchitectureTest`・`InvitationBoundaryArchitectureTest`（変更なしで通す） | NFR11.1 |
| 画面の範囲・補正・ボタンの計算 | `paging.test.ts`（移す事例）と fast-check の性質 | NFR9.5・NFR9.6 |

## 10. 残る危険

| ID | 危険 | 扱い | 見直す時点 |
|---|---|---|---|
| R1 | `invitation.domain` のパッケージごとのカバレッジが下がりうる | NR の R1 のまま。除外を増やさず、ほかのクラスのテストで満たす | U2 のコード生成の計画を書くとき |
| R2 | 最後のページより後を空にする決まり（BR3.2）は Paging の口で強制できない | NR の R2 のまま。3節の形を呼び出し元で守り、U3 は結合テストを足す | U3 のコード生成の計画を書くとき |
| R3 | TRACE を有効にすると、長い page の文字列が呼び出し元の controller と業務処理の引数としてそのままログに出る | 受け入れる（Q1 A、4.3 の根拠3つ）。招待の形は変えない | U2 のコード生成のレビュー（出うる範囲と要求の行の上限を確かめる）。U3 の一覧の controller を設計・生成するとき |
| R4 | TRACE のログに出る page の文字列は検証の前の生の値で、改行などの制御文字を含みうる。ログの出力の形によっては、偽のログの行を作られうる（ログの行の偽造） | 申し送る（承認の場の決定の A-02）。ログは `backend/src/main/resources/logback-spring.xml` の1行1件の JSON が既定で、TRACE も既定で無効のため実害は小さい見込み | U2 のコード生成の計画を書くとき |

## 11. 上流との差

| ID | 上流 | 上流の記載 | この設計 | 理由 |
|---|---|---|---|---|
| S-1 | NR の NFR3.1 | 長い文字列がそのままログに出ることを受け入れてよいかを、コード生成のレビューで確かめる | 受け入れるかどうかはこの段で決めた（Q1 A、4.3）。コード生成のレビューでは、出うる範囲と要求の行の上限だけを確かめる | NR のレビューの R-06 を受け、この段の質問で依頼者が決めたため。NR の文書は書き換えない |

ほかに上流（要件・機能設計・契約 C2・C5・NFR 要件）と違う作りはありません。

## 承認の場の決定（Request Changes、2026-10-02）

レビュー（Iteration 1、READY）の指摘 R-01〜R-04 は、すべて Minor でした。依頼者の決定は次のとおりです。

- 直すもの: ありません（1〜11節の設計の中身は変えていません）。
- 受け入れたもの: ありません。
- 申し送るもの: 4件すべてを U2 のコード生成の計画で扱います。下の表のとおりです。

| ID | レビューの指摘 | 申し送り先 | コード生成の計画で行うこと |
|---|---|---|---|
| A-01 | R-01 要求の行の上限 8KB（4.3 の根拠3）が見込みのまま | U2 のコード生成の計画とレビュー | Spring Boot 4 の組み込みのサーバーの既定の上限が 8KB で、要求の行にも効くかを確かめ、結果を記録する。崩れたときは、根拠1・2だけで受け入れ（Q1 A）が成り立つことを書き足す |
| A-02 | R-02 TRACE のログに出る生の page の文字列による、ログの行の偽造の危険 | U2 のコード生成の計画 | ログの出力が制御文字（改行など）をエスケープする形（`logback-spring.xml` の1行1件の JSON）であることを確かめるか、残る危険として記録する。10節の R4 に一言足した |
| A-03 | R-03 サーバーと画面の `PAGE_SIZE`（20）の一致が運用の約束だけ | U2 のコード生成の計画 | 食い違いを機械で見つける確かめ（E2E や契約の確かめ）を足すか、画面が応答の `pageSize` を使う形にするかを決める |
| A-04 | R-04 `InvitationPaging` が抜けた後の `invitation.domain` のパッケージごとのカバレッジの見込みが無い | U2 のコード生成の計画 | 移す前の値と、`InvitationPaging` を除いた見込みの値を実測して並べ、足すテストの量を見積もる（10節の R1） |
