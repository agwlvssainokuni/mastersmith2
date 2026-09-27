# Functional Design の質問 — u1-mail（メールの描画と送信、library）

単位の定義は `aidlc/spaces/default/intents/260925-user-management/inception/units-generation/unit-of-work.md`（U1）、主に受け持つストーリーは US3.1（`aidlc/spaces/default/intents/260925-user-management/inception/user-stories/stories.md`）、契約は C1（`MailSender`）と C10（招待メール）です（`aidlc/spaces/default/intents/260925-user-management/inception/contract-design/contract-summary.md`）。

## 設計の要点（案）

上流ですでに決まっていることから導ける、この単位の機能設計の見通しです。質問の答えで変わる点は（→ Qn）と書きます。

### エンティティ（値の型）

1. U1 は内部DB に何も保存しない（部品の一覧の Mail は `entities: []`、契約 C1 の「内部DB に触れない」）。`entities.md` には、アプリが独自に持つ値の型だけを書く。SMTP の接続先・資格情報・時間切れなどの設定値はエンティティにせず、決まり（BR）として書く（`project.md` の学び）。
2. 値の型は次の4つとする。
   - **MailRequest**（送信の依頼）: templateId・language（ja・en）・to（正規化済みのメールアドレス）・variables（名前と文字列の値の組）。契約 C1 のとおり。
   - **MailTemplate**（描く前のテンプレート）: templateId と language の組で1つに決まる。ja・en の両方がそろって1つの templateId になる（→ Q1）。
   - **RenderedMail**（描いた結果）: 件名・HTML の本文・言語。件名は本文の `<title>` の文面から作る。
   - **MailSendResult**（送信の結果）: 成功（Sent）か失敗（Failed と失敗の種類）。失敗の種類は契約 C1 の NOT_CONFIGURED・CONNECTION_FAILED・TIMEOUT・REJECTED・INVALID_INPUT・TEMPLATE_ERROR の6つに限る。例外のメッセージ・SMTP の応答・宛先は持たない。

### 決まり（rules の見通し）

3. **入力の確かめ（INVALID_INPUT）**: 宛先・差し込む値のどれかに改行（CR・LF）があれば、描かず・送らずに INVALID_INPUT を返す（AC3.1.7。受け手が受けるメールは0通）。宛先は既存の `EmailAddress` の決まり（正規化済み・254 文字まで・形式）に合わなければ INVALID_INPUT。差し込む値の名前の確かめ方は → Q4。
4. **描画**: 差し込む値はエスケープされる差し込み（`{{ }}`）だけで入れる。テンプレートに `{{{ }}}`・`{{& }}` を書かず、属性の値に差し込む箇所は二重引用符で囲む（エンジンの `{{ }}` は `'` を置き換えないため。`project.md` の Forbidden、AC3.1.4・AC3.1.6）。ライセンスヘッダーは `{{! ... }}` で書き、描いた本文に出ない（`team.md`、AC3.1.5）。
5. **件名**: 描いた本文の `<title>` 要素の文面を件名とする（FR2.1）。文面は HTML の文字参照（`&amp;` など）を元の文字に戻し、連続する空白と改行を1つの空白にまとめ、前後の空白を除いた文字列とする。`<title>` が無い・空になる場合は送らずに TEMPLATE_ERROR を返す（AC3.1.1 の「空でない」）。
6. **言語**: 依頼の language のテンプレートだけで描き、ほかの言語へ切り替えない（CR1.4）。描いた本文の `<html lang>` が依頼の language と違えば TEMPLATE_ERROR とする（AC3.1.10 の守り）。テンプレートが見つからない・描けないときも TEMPLATE_ERROR。
7. **メールの形**: HTML だけのメール（`Content-Type: text/html`、文字コード UTF-8）とし、テキストの部分は付けない（AC3.1.1 が `text/html` を求めるため）。件名に日本語が入るため、ヘッダーは規格どおりに符号化する。
8. **設定**: SMTP の接続先と資格情報は環境変数（`.env`）だけから受け取り、接続先が無ければ送らずに NOT_CONFIGURED を返す（`project.md` の Mandated）。`isConfigured` は、送るのに要る設定がそろっているかだけを返し、設定の値は返さない（契約 C1。差出人と暗号化の扱いは → Q2・Q3）。
9. **送信**: 送信は1回だけで、自動の再試行はしない（契約 C1）。接続と読み取り（書き込みを含む）に時間切れを持つ（値は NFR 要件の段）。失敗は例外にせず結果で返す。接続できない → CONNECTION_FAILED、時間切れ → TIMEOUT、SMTP が拒否の応答を返す → REJECTED。想定外の失敗（部品の不具合など）だけを例外にする（契約の共通の決まり）。
10. **秘密と個人情報**: 宛先のメールアドレス・SMTP の応答の文面・例外のメッセージ・資格情報を、結果・ログ・トレースの属性に含めない。`mail.debug` は常に無効とし、設定で有効にする口も作らない（`project.md` の Forbidden、NFR2、CR5）。失敗のログは U1 の中で1回だけ、templateId・language・失敗の種類・例外の型の名前だけを WARN で出す（スタックトレースなし。`team.md` の例外のログの決まり）。成功のログは templateId・language だけを出す。
11. **トランザクション**: U1 は内部DB に触れず、トランザクションを持たない。呼び出し元（U3）がトランザクションの外で呼ぶ（ADR-009、契約 C1）。

### 流れ（functional-spec の見通し）

12. **送信の流れ**: (1) 設定を確かめる（無ければ NOT_CONFIGURED）→ (2) 依頼を確かめる（改行・宛先・差し込む値の名前。誤りは INVALID_INPUT）→ (3) templateId と language のテンプレートで描く（誤りは TEMPLATE_ERROR）→ (4) `<title>` から件名を作り `<html lang>` を確かめる（誤りは TEMPLATE_ERROR）→ (5) SMTP で1回だけ送る（CONNECTION_FAILED・TIMEOUT・REJECTED）→ (6) Sent を返す。(1)〜(4) で止まったときは SMTP に接続しない。
13. **状態の移り変わり**: U1 は状態を持つ対象が無い（送信の依頼ごとに完結する）。functional-spec には送信の流れの手順と、失敗の種類ごとの分かれ道を書く。

### 境界と受け入れ基準の受け持ち

14. 招待メールのテンプレートの中身（文言・リンクの置き方・差し込む値の一覧）と、招待の URL の組み立ては U3 が持つ（`unit-of-work.md` の U1 の境界、契約 C10）。U1 は描画と送信の仕組みだけを持ち、U1 のテストはテスト用のテンプレートで行う。
15. US3.1 の受け入れ基準の受け持ちの案:
    - U1 の決まりで満たす: AC3.1.1（HTML・UTF-8・件名は `<title>`）、AC3.1.4（エスケープの仕組み）、AC3.1.5（すべてのテンプレートを描けること、件名が空でない、ヘッダーと `{{` の残りが出ない）、AC3.1.6（テンプレートの中身の検査）、AC3.1.7（改行の拒否）、AC3.1.10（`<html lang>` の確かめ）
    - U3 に回す（テンプレートの中身と URL の組み立て）: AC3.1.2・AC3.1.3・AC3.1.8・AC3.1.9。U1 の traceability では Deferred（U3）とする
    - AC3.1.5・AC3.1.6 の確かめは、テンプレートの置き場（→ Q1）にあるすべてのテンプレートを数え上げて調べる形にし、U3 が招待のテンプレートを足すと自動で対象になるようにする
16. 送信の失敗の受け入れ基準（AC1.1.5・AC1.1.7・AC1.1.12・AC2.2.8）とヘッダーへの差し込み（AC1.1.3）は US1.1・US2.2 の基準で、主な持ち主は U3。U1 はその土台として、失敗の種類の返し方（決まり 9）と漏らさない決まり（決まり 10）を持つ。

## 決まっていること（質問にしない）

- 部品 Mail（パッケージ `mail`）がテンプレートの描画と SMTP の送信を持ち、Invitation が呼ぶ。今後のメールも使う（ADR-002、`domain-design-questions.md` の Q2: A）。
- テンプレートは java-mustache-processor（サブモジュール `vendor/java-mustache-processor`、Gradle の composite build）で描く。取得元を Maven Central だけにする決まりと、推移依存を lockfile と脆弱性検査の対象に含めることを最初の Bolt（B1）で確かめ、満たせなければ Maven Central への公開に切り替える（`team.md`、ADR-010、`bolt-plan.md` の B1）。部品はテンプレートを実行時に読んで描く形（`Mustache.compile` と `Template.render`）で、欠けた差し込みは Mustache の仕様どおり空で描かれる（隣のリポジトリ `../java-mustache-processor` の `README.md` と `cherry-mustache-core` で確かめた）。
- HTML のメールで、件名は描いたテンプレートの `<title>` の文面（FR2.1、依頼文）。
- 招待メールは招待の言語で送る（FR7.3、CR1.4）。言語は ja・en の2つ（FR1.2、NFR8）。
- 契約 C1 の形: `isConfigured` と `send(MailRequest) → MailSendResult`。失敗の種類は6つ。送信は1回だけで自動の再試行はしない。呼び出し元はトランザクションの外で呼ぶ。内部DB に触れない（`contract-summary.md` の C1、`contract-design-questions.md` の「決まっていること」、要件の Q4: B）。
- 送信に失敗しても招待は残り、管理者が送り直す（FR2.4、ADR-009）。送信の失敗は監査ログに残さない（FR9.2）。
- SMTP の接続先と資格情報は環境変数（`.env`）だけから受け取り、接続先が無ければ送らない。ベース URL か SMTP の接続先が無いときは、アプリは起動するが招待を使えない（`project.md` の Mandated、FR1.8、要件の Q12: B）。
- 配備先が決まるまで、実在の宛先や外部の SMTP へは送らない。手元では compose の profile で起動する受け手で見る（`project.md` の Forbidden、`team.md` の Deployment、NFR11）。
- 差し込む値はエスケープされる差し込みだけで入れる。メールアドレス・SMTP の応答・例外のメッセージをログとエラー応答に出さない。`mail.debug` を有効にしない（`project.md` の Forbidden、NFR2）。
- テンプレートのライセンスヘッダーは `{{! ... }}` で書き、ライセンスヘッダーの検査の対象にバックエンドのメールのテンプレートを加える（`team.md` の Code Style）。
- テストは JVM の中で起動するテスト用の SMTP の受け手で実際に受けて確かめ、接続を拒む・応答しない（テストの中の ServerSocket）失敗も入れる。受け手の道具とライセンスの確認は NFR 要件・コード生成で決める（`team.md` の Testing Posture、ADR-010、`stories.md` の「合否の判定の仕方」）。
- SMTP の時間切れの値は NFR 要件の段で決める（契約 C1、`stories.md` の「後の段に回す点」）。
- SpotBugs の関門への `PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` の追加、手元の受け手の compose の profile は、この単位の作業だが機能設計ではなく基盤の設計・コード生成で扱う（`unit-of-work.md`、`bolt-plan.md` の B1）。
- 招待メールの中身（有効期限は「このリンクは 24 時間有効です」とだけ書く、招待した管理者の氏名は載せない、ボタンと URL の文字の両方を載せる、リンクは `https://<ベース URL>/register#token=<トークン>`）は U3 のテンプレートで扱う（契約 C10、`user-stories-questions.md` の M1: C・M5: A、`contract-design-questions.md` の Q1: A、`mockups.md` の E1）。
- 新しいパッケージ `mail` は、パッケージごとのカバレッジの下限（行 80%・分岐 70%）の対象になる（`team.md`、`unit-of-work.md`）。

## Q1. テンプレートの置き場と読み込みの時点

契約 C1 と C10 で「テンプレートの置き場（言語ごとの持ち方）と templateId の決め方」が U1 の機能設計に回されています。java-mustache-processor はテンプレートを実行時に読んで描く部品です。招待のテンプレートの中身は U3 が作ります。

A. バックエンドのリソースの `mail/templates/<templateId>/<language>.html`（例: `mail/templates/invitation/ja.html`・`en.html`）に置く。templateId は英小文字・数字・ハイフンだけ。アプリの起動時にすべてのテンプレートを読んで描ける形に準備し、ja・en のどちらかが欠けている・壊れているときは起動を止める（テンプレートはコードと同じく WAR に入るもので、設定の不足とは違うため）
B. 置き場は A と同じ。起動時には読まず、最初に使うときに読んで覚えておく。欠けている・壊れているときは起動を止めず、送信の結果を TEMPLATE_ERROR にする
C. 置き場を1つの階層に並べる（`mail/templates/<templateId>_<language>.html`）。読み込みの時点は A と同じ（起動時に準備し、欠けていれば起動を止める）
D. テンプレートを使う機能のパッケージの側に置き（例: 招待のテンプレートは invitation の置き場）、起動時にその機能が Mail に登録する。欠けていれば起動を止める
X. Other (please specify)

[Answer]: C

理由: 契約で U1 の機能設計に回された論点で、ファイルの置き方と、テンプレートの誤りを起動時に止めるか送信の時に結果で返すかで、U3 の作り方とテストの形が変わるため。

## Q2. 差出人（From）の決め方と「設定がある」の範囲

メールには差出人のメールアドレスが要りますが、上流では接続先と資格情報しか決まっていません。差出人が無いと受け手が拒むことがあります。`isConfigured` の答えは、管理画面の「招待を使えない理由」（FR1.8）の SMTP の側に効きます。

A. 差出人のメールアドレスを環境変数で必須とし、接続先と差出人の両方があるときだけ「設定がある」とする。差出人の表示名は固定の「MasterSmith」とする
B. A と同じく差出人を必須とし、表示名も環境変数で変えられるようにする（無ければ「MasterSmith」）
C. 差出人は環境変数で任意とし、無ければベース URL のホスト名から `no-reply@<ホスト名>` を作る。「設定がある」は接続先だけで決める
D. 差出人は環境変数で任意とし、無ければ固定の既定値（例: `no-reply@localhost`）を使う。「設定がある」は接続先だけで決める
X. Other (please specify)

[Answer]: B

理由: 差出人は上流のどの答えにも無く、必須にするか既定で補うかで、招待を使えない理由の出方と `.env.example` の項目が変わるため。

## Q3. SMTP の暗号化と資格情報の扱い

配備先が決まるまでは手元の受け手（暗号化・認証なし）にだけ送ります。一方で、資格情報を環境変数から受け取ることは決まっています（FR2.5）。暗号化なしの接続で資格情報を送ると、資格情報が経路上に平文で流れます。

A. 暗号化を環境変数で「なし・STARTTLS 必須・SMTPS」から選べるようにし、既定は「なし」（手元の受け手向け）。資格情報を設定したのに暗号化が「なし」のときは送らず、「設定がない」（NOT_CONFIGURED）と同じ扱いにする
B. A と同じく選べるようにし、既定を「STARTTLS 必須」にする。手元の受け手では `.env` で「なし」を明示する。資格情報と「なし」の組み合わせは A と同じく送らない
C. A と同じく選べるが、資格情報と暗号化「なし」の組み合わせも許す（配備先が決まったときに見直す）
D. 今は暗号化なし・認証なしの送信だけを作り、暗号化と認証は配備先が決まってから足す（資格情報の環境変数の口だけ用意する）
X. Other (please specify)

[Answer]: A

理由: 資格情報を平文で送らない守りを今の時点で入れるか、配備先が決まるまで作らないかは上流で決まっておらず、設定の項目と「設定がある」の判定に効くため。

## Q4. 差し込む値の名前の確かめ方

java-mustache-processor は Mustache の仕様どおり、テンプレートにある名前の値が渡されないと空のまま描きます。そのため、呼び出し側の名前の書き誤り（例: `registrationUrl` の綴りの誤り）があると、リンクの無い招待メールが「送信済み」として届きえます。team.md の必須テストの「差し込み漏れ（`{{` の残り）」では、この誤りは見つかりません。

A. テンプレートごとに受け取る差し込みの名前の一覧を U1 に持たせ（例: invitation は registrationUrl）、送信の依頼の名前が一覧と一致しなければ（欠け・余分とも）描かずに INVALID_INPUT を返す。あわせて、テンプレートの中の名前と一覧が一致することをテストで確かめる
B. A と同じ一覧を持つが、欠けだけを拒否し、余分な名前は無視する
C. 送信の時には確かめない。すべてのテンプレートを描くテストと、U3 の招待メールのテスト（受け手で受けてリンクがあることを確かめる）で見つける
X. Other (please specify)

[Answer]: A

理由: 欠けた値を空で描く部品の動きは上流で扱っておらず、送信の時に拒否するかテストだけに任せるかで、MailRequest の確かめと U3 の書き方が変わるため。

---

## Consolidated Summary Confirmation

答えのまとめ:

- 設計の要点（案）は冒頭の「設計の要点（案）」の 16 件のとおり（エンティティにしない値の型、入力の確かめ・描画・件名・言語・メールの形・設定・送信・秘密と個人情報・トランザクションの決まり、流れ、受け入れ基準の受け持ち）
- Q1 C: テンプレートは `mail/templates/<templateId>_<language>.html` の1つの階層に置き、アプリの起動時にすべて読んで描ける形に準備する。ja・en のどちらかが欠けている・壊れているときは起動を止める
- Q2 B: 差出人のメールアドレスを環境変数で必須とし、接続先と差出人の両方があるときだけ「設定がある」とする。差出人の表示名も環境変数で変えられる（無ければ MasterSmith）
- Q3 A: 暗号化を環境変数で「なし・STARTTLS 必須・SMTPS」から選べ、既定は「なし」。資格情報を設定したのに暗号化が「なし」のときは送らず、設定がない（NOT_CONFIGURED）と同じ扱いにする
- Q4 A: テンプレートごとに差し込む名前の一覧を U1 が持ち、送信の依頼の名前が一覧と一致しなければ（欠け・余分とも）描かずに INVALID_INPUT を返す。テンプレートの中の名前と一覧の一致もテストで確かめる

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
