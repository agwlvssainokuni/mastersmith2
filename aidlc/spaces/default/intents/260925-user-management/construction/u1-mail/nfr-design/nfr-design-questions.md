# NFR Design の質問 — u1-mail（メールの描画と送信、library）

U1 は、アプリの中の部品（パッケージ `cherry.mastersmith.mail`）として契約 C1（`MailSender` の `isConfigured` と `send`）を提供する library の単位です。library の単位のため、この段の成果物は `security-design.md`・`logical-components.md`・`traceability.json` です（性能・拡張性・信頼性・観測の設計の文書は service・ui の単位だけ。段の定義の `produces_kinds`）。性能・信頼性・観測に当たる設計（NFR5・NFR6 と、下の Q2 の観測）は、NFR 要件の段の置き方（`tech-stack-decisions.md` の2節）にならい、`logical-components.md` の節に置きます。

非機能の作りのほとんどは、承認済みの NFR 要件（Q1 A・Q2 B・Q3 X・Q4 A・F1 B・F2 A・F3 A・F4 A・F5 A）と機能設計、既存のコードの前例で決まっています。そのため、まず設計の要点（案）を示し、上流から1つに決まらない2点（描画と送信の内部をメソッドの追跡から外す作り、送信の観測の単位）だけを質問にしました。

読んだ上流:

- この単位の承認済みの NFR 要件 `construction/u1-mail/nfr-requirements/`（`security-requirements.md` の NFR1.1〜NFR2.9、`tech-stack-decisions.md` の NFR5.1・NFR6.1〜NFR6.5・NFR8.1〜NFR8.3・NFR9.1〜NFR9.5・NFR11.1・NFR11.2、`traceability.json`、`nfr-requirements-questions.md`）と、承認の場の決定（Minor の R-01: F5 A で残る平文の危険を README に書くことを B1 の完了の条件にする、R-02: B1 で確かめる前提（環境変数から点を含む鍵への結び付き、composite build の5条件）が崩れたときに ADR-010 の切り替えを行うことを確かめる。どちらも Accepted risk）
- この単位の承認済みの機能設計 `construction/u1-mail/functional-design/`（`rules.md` の BR1.1〜BR6.4、`functional-spec.md` の1〜11節、`entities.md`）
- 契約 `inception/contract-design/contract-summary.md` の C1・C10、部品の一覧 `inception/domain-design/components.md`（Mail の `depends_on` は空、使うのは Invitation だけ）、`inception/domain-design/decisions.md` の ADR-009・ADR-010
- 決まり `aidlc/spaces/default/memory/team.md`・`project.md`（NFR 要件の段の学び: 「Maven Central だけ」は依存の解決に当てる、NFR の枝番は単位の中で .1 から振る）
- 既存のコード: `backend/src/main/java/cherry/mastersmith/common/observability/TraceAspect.java`（追跡の対象の式）・`backend/src/main/resources/application.yaml`（`mastersmith.trace` の ENTER・EXIT の文言、`logging.level` の対象DB のドライバーの OFF）・`backend/src/main/java/cherry/mastersmith/targetdb/config/`（`TargetDataSourceConfig`・`TargetDbSettings` の起動時の点検の前例）・`backend/src/test/java/cherry/mastersmith/ArchitectureTest.java`・`dsl/DslBoundaryArchitectureTest.java`（機能ごとの境界テストの前例）
- java-mustache-processor（`vendor/java-mustache-processor` はまだ無いため、隣のリポジトリ `../java-mustache-processor` のタグ `0.1.0` 相当の `cherry-mustache-core` を読み取りだけで確かめた。公開の API は `Mustache.compile` と `Template.render` だけで、`Mustache.compile(String)` は空の部分テンプレートの解決（`MapPartialResolver`）を使い、解決できない部分テンプレートは何も出さずに飛ばす。部品のログは debug・warn で、差し込む値を出さない）

## NFR 設計の要点（案）

### 部品の構成（`logical-components.md`）

1. **パッケージの構成**: 新しいパッケージ `cherry.mastersmith.mail` に次を置く（用途名の下位パッケージの置き方は → Q1。下は推奨の A の形）。
   - `mail.service`: 契約 C1 の `MailSender`（インターフェース）とその実装の Bean（`SmtpMailSender`）。U3 が使う入口。
   - `mail.domain`: 契約の値の型（`MailRequest`・`MailSendResult`・`MailFailureKind`）と、依頼の確かめ（BR3.1〜BR3.4）の純粋な関数。U3 が使う。
   - `mail.config`: 差出人の設定の型（`mastersmith.mail.from`・`from-name`、Spring Boot の `MailProperties` と名前が重ならないよう `MastersmithMailProperties`）、起動時の点検の結果（`MailSettings`）、それを作る `@Configuration`。
   - `mail.template`: テンプレートの一覧（BR2.2、`MailTemplateCatalog`）、起動時の準備と描画（`MailTemplateRegistry`）、描いた結果（`RenderedMail`）と件名・lang の取り出し（BR4.2・BR4.3）。
   - `mail.transport`: メールの組み立て（BR5.1）・1回だけの送信（BR5.2）・失敗の分類（BR5.3）。Jakarta Mail と Spring の `JavaMailSender` を触るのはここだけ。
   - `web`・`repository` は作らない（API と内部DB を持たない。NFR5.1、BR5.4）。
2. **境界の検査（ArchUnit）**: 既存の `DslBoundaryArchitectureTest` と同じ形で `MailBoundaryArchitectureTest` を足す。(a) `mail` はほかの機能（`user`・`auth`・`audit`・`invitation` など）と `web`・`repository` の層に依存しない（`components.md` の `depends_on` が空。`common` だけは使ってよい）、(b) `mail` の中に `@Transactional` を使わない（NFR5.1）、(c) `mail` の外から使えるのは `mail.service` と `mail.domain` だけ（`mail.config`・`mail.template`・`mail.transport` は内部）、(d) `jakarta.mail`・`org.springframework.mail` を使うのは `mail.transport` と `mail.config` だけ、`cherry.mustache` を使うのは `mail.template` だけ（外部の部品の依存を1か所に閉じる。`components.md` の Mail の理由、ADR-002）。既存の ArchUnit の決まりは緩めない。
3. **U3 との境目**: U3 は `MailSender` の `isConfigured`・`send` と `mail.domain` の型だけを使い、トランザクションの外で呼ぶ（ADR-009）。招待のテンプレート（`mail/templates/invitation_ja.html`・`invitation_en.html`）の中身と、`MailTemplateCatalog` の invitation の行（差し込みは `registrationUrl`・`validityHours`）は、U3 の Bolt（B3）で U1 の置き場に足す（BR2.2、C10）。U1 の B1 ではテスト用のテンプレートだけで作り、一覧の本番の行は空から始める（空でも起動する）。

### 起動時の点検（`security-design.md`・`logical-components.md`）

4. **設定の点検の場所**: 既存の `targetdb` の前例（`TargetDataSourceConfig` の `@Bean` で `TargetDbSettings.inspect` を1回だけ呼び、欠け・不正なら項目の名前だけの WARN を1件出す）にならい、`mail.config` の `@Bean` で `MailSettings` を1回だけ作る。送信の部品は Spring Boot の自動設定が作る `JavaMailSenderImpl` を `ObjectProvider` で「あれば使う」形で受ける（NFR2.5）。`MailSettings` は3つの形（`NotConfigured`（項目が1つも無い、WARN なし）・`Invalid`（問題の項目の名前の一覧）・`Usable`（送信の部品と、組み立て済みの差出人））の封じた型とし、`Usable` は資格情報の値を持たない（有無だけを点検に使う）。点検の項目と順序は NFR2.5（接続先の有無と空白）・BR1.3（差出人の形・改行・資格情報の片方・範囲の外のポート）・NFR2.6（方式の分類と、資格情報と NONE の組、知らない `protocol`）・NFR6.2（使う方式の時間切れの3つが1以上の整数か）のとおりで、U1 は部品の設定を読むだけで書き換えない。`isConfigured` は `MailSettings` が `Usable` かだけを返す（BR1.7）。
5. **テンプレートの準備の場所**: `MailTemplateRegistry` の Bean を作るときに、クラスパスの `mail/templates/*.html` を数え上げ（実行可能 WAR の中でも数え上げられることを B1 の結合テストと手元の起動で確かめる）、BR2.1〜BR2.3 のとおり名前と一覧を照らして ja・en を `Mustache.compile` で準備する。欠け・壊れ・名前の誤りは例外で起動を止め、ログは templateId・language・原因の種類だけ（部品の例外のメッセージは出さない）。準備した `Template` は変わらない表に持ち、動いている間は差し替えない（NFR6.5）。部分テンプレートは使わない（空の解決で準備し、`{{>` を書かない）。

### 描画・送信・秘密（`security-design.md`）

6. **差し込みの確かめ**: 起動時は BR2.3 の欠け・壊れだけを確かめ、テンプレートの中の差し込みの名前と一覧の一致は BR2.6 のとおりテストで確かめる（部品の公開の API は構文木を出さないため、起動時に名前を数えるには部品の内部に頼ることになり、採らない）。BR2.4 の検査（三重の波かっこ・`{{&` を書かない）に、部分テンプレート `{{>` と区切りの変更 `{{=` を書かないことを足す（区切りを変えると三重の波かっこの検査をすり抜けうるため。承認済みの BR2.4 の趣旨を強める追加として `security-design.md` の上流との差に書く）。依頼ごとの確かめ（BR3.x）は描く前に `mail.domain` の純粋な関数で行い、性質ベースのテスト（jqwik）を当てる。
7. **件名と lang の取り出し**: テンプレートは WAR に入る信頼できる入力のため、HTML の解析の部品は足さず、描いた本文から title 要素と html 要素の lang 属性を決まった形で取り出し、文字参照は既存の依存（spring-web）の `HtmlUtils.htmlUnescape` で戻す（BR4.2・BR4.3。新しい依存を足さない）。
8. **組み立てと送信**: `mail.transport` が `MimeMessageHelper`（UTF-8、HTML だけ）で差出人（表示名を含めて UTF-8 で符号化）・宛先1人・件名を入れ、`JavaMailSender.send` を1回だけ呼ぶ（BR5.1・BR5.2、NFR6.3）。失敗は例外の原因の連なりの型で分類する: 時間切れの型（`SocketTimeoutException` など）があれば TIMEOUT、認証の失敗（Spring の `MailAuthenticationException`）と受け手の拒否の応答（SMTP の応答の番号を持つ Jakarta Mail の例外）は REJECTED、接続できない・暗号化を確立できない（STARTTLS（必須）を受け付けないときを含む）・途中で切れるは CONNECTION_FAILED。組み立ての失敗（Spring の `MailParseException`・`MailPreparationException`）と分類できないものは想定外として例外にする（BR5.3）。分類の規則は単体テストで表にして確かめ、受け手の側の場合は SubEtha SMTP の結合テストで確かめる（NFR9.2）。
9. **想定外の例外の包み方**: 想定外の例外をそのまま U3 に渡すと、既存の `@RestControllerAdvice` が 5xx として ERROR とスタックトレースを出し、部品の例外のメッセージ（宛先のメールアドレスや SMTP の応答を含みうる）がログに残る（`project.md` の Forbidden）。そのため U1 は、固定の文言と原因の連なりの型の名前だけを持つ U1 の例外（原因の例外そのものは付けない）に包んで投げる。調べるときは、型の名前と WARN・ERROR の時刻・トレースIDで絞る。
10. **ログ・結果・toString**: ログは BR6.2 のとおり SLF4J のキーと値（templateId・language・failureKind・exceptionType）で1件だけ出し、例外の物をロガーに渡さない（スタックトレースとメッセージを出さない）。`MailRequest` の `toString` は宛先と差し込む値を伏せ（NFR1.2）、`RenderedMail` の `toString` も件名・本文を伏せる（二重の守り）。`application.yaml` には NFR 要件で決めた値（`logging.level` の `org.eclipse.angus`・`jakarta.mail` を OFF、`spring.mail.properties` の `mail.debug: false`・時間切れの6つ・`checkserveridentity`・`ssl.protocols`、`management.health.mail.enabled: false`）を理由のコメントつきで置き、空の既定の `spring.mail.host` は置かない（NFR2.2〜NFR2.7・NFR6.1・NFR6.4）。java-mustache-processor のロガー（`cherry.mustache`）は差し込む値を出さないため、水準を変えない。
11. **同時の呼び出し**: `MailSettings`・準備したテンプレート・`JavaMailSenderImpl` は動いている間は変えず、依頼ごとの値は呼び出しの中だけで持つため、要求を処理する複数のスレッドから同時に呼ばれてもよい（NFR6.5）。送信の間に内部DB の接続を持たないことは、U1 がトランザクションを持たないこと（要点 2 の (b)）と U3 の呼び出しの順序で守る（NFR5.1）。

### B1 に引き継ぐこと

12. **承認の場の R-01・R-02**: (R-01) README の SMTP の設定の説明に、`starttls.enable` だけの設定（資格情報なし）では受け手が STARTTLS を受け付けないとメールが平文で届き、本文の招待の URL（トークン）が守られないこと、配備先が決まったら STARTTLS（必須）か SMTPS を使うことを書き、B1 の完了の条件に入れる。(R-02) B1 で、環境変数から `spring.mail.properties` の点を含む鍵（例: `mail.smtp.connectiontimeout`）へ結び付くことと、composite build の5条件（NFR8.3 の (a)〜(e)）を確かめる。composite build の条件を満たせなければ ADR-010 のとおり Maven Central への公開に切り替える判断を B1 で記録する。環境変数の結び付きが成り立たなければ、既定の 3 秒のまま動くことを確かめたうえで、変え方（別の設定の口）を B1 で依頼者に諮る。この2点を `logical-components.md` の「B1 で確かめること」に並べる。

### 観測（`logical-components.md` の節）

13. **送信の観測**: ログは要点 10 のとおり。送信の時間を span と指標で分けて見るかは（→ Q2）。どの形でも、属性・タグは templateId・language・結果の種類だけにし、宛先・差し込んだ値・件名・本文・SMTP の応答を入れない（BR6.3、NFR1.1）。外部エクスポートは既定で無効のまま（`team.md` の Deployment）。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| Spring Boot のメールの自動設定を使い、接続先・ポート・資格情報・暗号化の方式は `spring.mail.*`、差出人と表示名は U1 の `mastersmith.mail.*`。送信の部品は `ObjectProvider` で受け、U1 は部品の設定を読んで点検する | NFR2.5・NFR2.6（Q3 X・F1 B） |
| 暗号化の方式の分類（SMTPS・STARTTLS（必須）・NONE）、資格情報と NONE の組は「設定がない」、`starttls.enable` だけの組は NONE | NFR2.6（F1 B・F5 A） |
| 時間切れは接続・読み取り・書き込みとも 3 秒で環境変数で変えられ、不正な値は「設定がない」。全体の上限と自動の再試行は持たない | NFR6.1〜NFR6.3（Q1 A・Q2 B） |
| SMTP をヘルスチェックから外し、起動時に SMTP へ接続しない | NFR6.4（Q3 X） |
| `mail.debug` と TLS を弱める値への U1 の守りは足さず、既定を安全な値にしてテストで確かめ、README の運用の決まりで扱う | NFR2.3・NFR2.4・NFR2.7（F3 A） |
| メールの部品のロガーを OFF に固定し、ログ・結果・トレースの属性に宛先・差し込んだ値・SMTP の応答・例外のメッセージ・資格情報を入れない | NFR1.1・NFR2.1・NFR2.2、BR6.1〜BR6.3 |
| `MailRequest` の `toString` は templateId と language だけを出す | NFR1.2 |
| テンプレートは起動時に準備し、欠け・壊れ・置き場の誤りで起動を止める。動いている間は差し替えない | BR2.3、NFR6.5、NFR8.1 |
| U1 は内部DB に触れずトランザクションを持たない。U3 がトランザクションの外で呼び、要求の中で結果を待つ | NFR5.1、BR5.4、ADR-009 |
| SpotBugs の関門に `PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` を足し、既存の1件を確かめる | NFR2.8・NFR2.9 |
| テスト用の受け手は SubEtha SMTP 7.2.2、手元の受け手は Mailpit を compose の profile `mail` で見たいときだけ起動 | NFR9.1、NFR11.1 |
| java-mustache-processor はタグ `0.1.0` をサブモジュールと composite build で取り込み、B1 で5条件を確かめ、満たせなければ ADR-010 の切り替え | NFR8.2・NFR8.3、ADR-010 |
| 用途名の下位パッケージを機能の中に置いてよく、機能の間の依存の向きは ArchUnit の境界テストで固定する。既存の境界テストを緩めない | `team.md` の Code Style |
| 新しいパッケージ `mail` はパッケージごとのカバレッジの下限の対象で、除外を足さない | NFR9.5 |

---

## Q1. 描画と送信の内部を、既存のメソッドの追跡（`TraceAspect`）からどう守りますか？

理由: 既存の `TraceAspect` は、`web`・`service`・`domain`・`repository` の層の Spring の Bean のメソッドについて、入るときに引数（`$[arguments]`）、出るときに戻り値（`$[returnValue]`）を、それぞれの `toString` で TRACE に出します（`application.yaml` の `mastersmith.trace`）。NFR1.2 で `MailRequest` の `toString` は伏せましたが、U1 の中で描画や組み立てを別の Bean に分けると、その Bean のメソッドの引数（差し込む値の `Map`）や戻り値（描いた本文、つまり招待の URL とトークン）が TRACE に出ます。TRACE は既定では出ませんが、環境変数でロガーの水準を上げると出る経路で、NFR1・`project.md` の Forbidden（招待のトークンをログに含めない）に当たります。既存の前例では、4つの層に当たらない用途名の下位パッケージ（`dsl/parse`・`dslmanage/generate`）は追跡の対象の外です。どの形でも、U3 が呼ぶ `MailSender` の入口の引数（`MailRequest`、伏せた `toString`）と戻り値（`MailSendResult`、種類だけ）は出てよい値です。

A. 描画と送信の内部を用途名の下位パッケージ `mail.template`・`mail.transport` に置き、追跡の対象の層に置くのは `MailSender` とその実装、`mail.domain` の値の型と確かめの純粋な関数だけにする。あわせて `RenderedMail` の `toString` も伏せる（二重の守り）。`TraceAspect` は変えない。前例（`dsl/parse`・`dslmanage/generate`）と同じ形で、境界テストで内部を外から使わせない（要点 1・2）。代わりに、描画と送信の内部の呼び出しは TRACE で追えず、送信の WARN・INFO の1件と例外の型の名前で追う（推奨）
B. `TraceAspect` の式に `!within(cherry.mastersmith.mail..*)` を足し、`mail` 全体を追跡から外す（層の置き方は自由になるが、共通の部品 `common.observability` の変更になり、U3 から `MailSender` を呼んだ入口も追えなくなる）
C. 描画・送信の Bean も `service`・`domain` の層に置き、層の間で渡す値をすべて `toString` を伏せた型にする（差し込む値の `Map` をそのまま引数にしない決まりを設けてレビューで守る。構造では防げず、漏れはテストで見つけるしかない）
X. Other (please specify)

[Answer]: A

## Q2. 送信の時間を、トレースと指標でどこまで分けて見られるようにしますか？

理由: ADR-009 で送信は招待・送り直しの要求の中で行うため、招待の API の 95 パーセンタイル 5 秒（U3 の NFR6）のうち SMTP にかかった時間が、受け手が遅いときの手がかりになります。今のアプリには自前の span や指標を作る箇所が無く（HTTP の要求の span と Spring Boot・HikariCP の標準の指標だけ）、Spring の `JavaMailSender` も自分では span を作りません。BR6.3 は「送信の処理にトレースの属性を付けるなら templateId・language・結果の種類だけ」としていて、付けるかどうかは決まっていません。手元の監視（grafana/otel-lgtm）は compose の profile で見たいときだけ起動し、外部エクスポートは既定で無効です。

A. 自前の span も指標も作らない。送信の時間は、要求の span の時間と、U1 の INFO・WARN の1件の時刻から読む（作る物が最も少ない。SMTP の時間だけを取り出して見ることはできない）
B. Micrometer の Observation（名前は例えば `mastersmith.mail.send`）を送信ごとに1つ作り、属性（低い基数のタグ）は templateId・language・outcome・failureKind だけにする。1つの仕組みで、要求の span の子の span と、時間の指標（件数・合計・最大）の両方が得られ、手元の監視で SMTP の時間と失敗の種類ごとの件数を分けて見られる。このアプリで初めての自前の Observation になるため、属性に値を入れないことを `TestObservationRegistry` を使うテストで確かめる（推奨）
C. span は作らず、時間の指標（Timer、タグは B と同じ）だけを作る（トレースの上では SMTP の時間を分けられないが、指標で失敗の種類ごとの件数と時間を見られる）
X. Other (please specify)

[Answer]: B

---

## Consolidated Summary Confirmation

答えのまとめ（回答の後に書き直します）:

- 設計の要点（案）は冒頭の 13 件のとおり（部品の構成と境界の検査、起動時の点検とテンプレートの準備の場所、差し込みの確かめ、件名と lang の取り出し、組み立てと送信と失敗の分類、想定外の例外の包み方、ログ・結果・toString、同時の呼び出し、B1 に引き継ぐ R-01・R-02、観測）
- Q1 A: 描画と送信は用途名の下位パッケージ `mail.template`・`mail.transport` に置き（TraceAspect の対象の層の外）、`RenderedMail` の toString も伏せる。TraceAspect は変えない（前例は `dsl/parse`・`dslmanage/generate`）
- Q2 B: Micrometer の Observation を1つ（例 `mastersmith.mail.send`）作り、子の span と時間の指標の両方を得る。タグは templateId・language・outcome・failureKind だけで、宛先などの値が入らないことを TestObservationRegistry で確かめる

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
