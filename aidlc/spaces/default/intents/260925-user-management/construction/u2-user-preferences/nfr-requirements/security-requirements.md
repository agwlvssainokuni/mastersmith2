# Security Requirements — U2 利用者のプリファレンスとパスワードの変更（u2-user-preferences）

U2 のセキュリティの要件です。要件定義の NFR2（個人情報とメール）・NFR4（認可）・NFR8（多言語のエラーの説明文）・NFR9（必須のテスト）を、この単位の3本の API（契約 C4）、利用者の作成と読み取り（契約 C2）、ログイン・更新の応答（契約 C3）、パスワードの変更の監査（契約 C8）に当てます。

出典の略号は `performance-requirements.md` と同じ。加えて、PM は `aidlc/spaces/default/memory/project.md`、TM は `aidlc/spaces/default/memory/team.md`、前 U2 の NFR は `aidlc/spaces/default/intents/260922-auth-audit-base/construction/u2-authentication/nfr-requirements/`。

## 守るもの

| 守るもの | 置き場 | 守り方 |
|---|---|---|
| パスワード（今・新しい・確かめ）の値 | 要求の本文だけ（保存しない） | 既存の `Password` の型で扱い、文字列にすると伏せる。ログ・監査・トレース・エラー応答に出さない（NFR2.2） |
| パスワードのハッシュ | 内部DB の利用者の行 | UserAccount の外へ出さない。利用者の要約・応答・監査に含めない（NFR2.2） |
| アクセストークン・リフレッシュトークン | 既存の Authentication の持ち物 | U2 は触れない（BR4.5）。値を出さない（NFR2.2） |
| メールアドレス | 内部DB の利用者の行 | アプリのログとエラー応答に出さない。プリファレンスの応答にも含めない（NFR2.1） |
| 利用者の氏名と表示の設定 | 内部DB の利用者の行 | 本人だけが読み書きできる（NFR4.3） |

## 要件

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR4.1 | 3本の API は、アクセストークンが無い・無効なら 401 AUTHENTICATION_REQUIRED を返し、何も読み書きしない | サーバー側の結合テスト（code-generation） | NFR4、BR8.1、CR4 |
| NFR4.2 | 3本の API は、管理者でないログインした利用者でも処理する（取得・保存は 200、パスワードの変更は 204）。管理者の権限を要しない API のため 403 の場面は無い | サーバー側の結合テストで、管理者でない利用者の 200・204 を確かめる（code-generation） | NFR4、BR8.1、機能設計の7.1 の D1 |
| NFR4.3 | 対象の利用者はアクセストークンの本人の利用者 ID だけで決め、URL・本文・クエリで利用者 ID やメールアドレスを受け取らない。ほかの利用者の値を読み書きする経路を持たない | 結合テストで、本文に別の利用者の識別を入れても本人の値だけが変わることを確かめる。API の定義（契約 C4）に利用者の識別の項目が無いことをレビューで確かめる（code-generation） | NFR4、BR8.1 |
| NFR4.4 | 3本の API は `Authorization` のベアラーで認証し、クッキーを使わないため、トークンの更新・ログアウトのような Origin の確かめは足さない（既存の `/api/` の扱いのまま） | SecurityFilterChain の設定のレビュー（NFR 設計・code-generation） | NFR4、前 U2 の NFR の Q4、要点 8 |
| NFR4.5 | 今のパスワードの誤りが続いても、回数の制限・ロックを設けない（BR4.5 のとおり）。誤りをログインの失敗回数に数えない。残る危険は下の「残る危険」の R1 に記録する | 結合テストで、誤りを6回以上続けても7回目に正しい今のパスワードで変更でき、ログインのロックの状態が変わらないことを確かめる（code-generation） | Q2 A、BR4.5 |
| NFR2.1 | メールアドレスを、アプリのログとエラー応答に含めない。プリファレンスの応答（C4）にも含めない | 既存の `*SecretLeakIT` の形で、3本の API の成功・誤りのログと応答にメールアドレスが無いことを確かめる（code-generation） | NFR2、PM の Forbidden、BR3.1、BR8.4 |
| NFR2.2 | パスワード（今・新しい・確かめ、平文・ハッシュとも）・アクセストークン・リフレッシュトークンを、アプリのログ・監査ログ・トレースの属性・外部へのエクスポート・エラー応答に含めない。NFR2 の個人情報の扱いと同じ扱いで PM の Forbidden を当てる | `*SecretLeakIT` の形で、パスワードの変更の成功・今のパスワードの誤り・入力の誤りのそれぞれで確かめる。監査の列の一覧に V7 の2列（target_user_id・target_invitation_id）を足して、監査の行にも無いことを確かめる（code-generation） | NFR2、PM の Forbidden、BR7.4、BR8.4、TM の Testing Posture（秘密情報の漏えい） |
| NFR2.3 | 入力の誤りの応答は、項目の名前と理由だけを返し、入れた値（パスワード・氏名）を返さない。内部の例外のメッセージとスタックトレースを載せない | 結合テストで応答の本文を確かめる（code-generation） | NFR2、BR8.4、TM の Code Style |
| NFR8.1 | エラーの説明文は ja・en を用意し、要求の Accept-Language で選ぶ（それ以外は既定の ja）。新しい code PASSWORD_CURRENT_MISMATCH（400）にも ja・en の説明を付け、1つの code に1つの状態コードに固定する | 結合テストで ja・en の説明文を確かめる。code の重複は既存の起動時の検査（code-generation） | NFR8、BR8.2、BR8.3 |
| NFR9.1 | 新しいパスワードは既存の PasswordPolicy の作成時の規則（12 コードポイント以上、UTF-8 で 72 バイト以内）で検証する。UTF-8 で 72 バイトを超える今のパスワードは照合の仕組みに渡さず不一致とする（例外にしない） | 境界のテスト（11・12 コードポイント、72・73 バイト）と、性質ベースのテスト（jqwik）で確かめる（code-generation） | NFR9、FR6.2、BR4.1、BR4.2、TM の Testing Posture（パスワードの規則の境界） |
| NFR9.2 | 氏名は前後の空白を除いた後で 1〜254 コードポイント、制御文字（Cc）と見えない書式の文字（Cf）を拒否する。要求の本文は既存の上限（1MB）のまま | DisplayName の境界のテストと性質ベースのテスト（jqwik）（code-generation） | NFR9、BR1.1〜BR1.4、`application.yaml` の `max-request-body-size` |
| NFR9.3 | 既存の静的解析の関門（SpotBugs ＋ FindSecBugs の priority 1、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority にかかわらず）と、秘密情報の検出・依存関係の脆弱性検査を、そのまま通す。除外を足さない | `./gradlew verify`（code-generation・build-and-test） | NFR9、TM の Code Style、PM の Mandated |

## 脅威と扱い

| 脅威 | 扱い | 要件 |
|---|---|---|
| ほかの利用者の設定・パスワードを変える | 本人の ID をトークンだけから取り、識別を受け取らない | NFR4.3 |
| 未認証の呼び出し | 既存の `/api/` のログイン必須に乗り、401 | NFR4.1 |
| クロスサイトの要求の偽造（CSRF） | ベアラーの認証のため、ブラウザが自動で付けるクッキーに頼らない | NFR4.4 |
| 今のパスワードの総当たり | 制限しない。残る危険 R1 として受け入れる | NFR4.5 |
| 秘密・個人情報の漏えい（ログ・監査・トレース・応答） | 型で伏せる、出さない列の決まり、漏えいのテスト | NFR2.1〜NFR2.3 |
| 長すぎる入力・見えない文字の混入 | 上限と文字の分類の検証 | NFR9.1・NFR9.2 |

## 残る危険

| ID | 危険 | 受け入れる根拠 | 見直す時点 |
|---|---|---|---|
| R1 | ログインしたままの画面や、漏れたアクセストークン・リフレッシュトークンを使えば、ログインのロック（既定 5 回・30 分）を通らずに、パスワードの変更の API で今のパスワードを何度でも試せる（照合1回 約 278 ms） | (1) 依頼者の決定（Q2 A）で、前の Intent で IP ごとの回数の制限をしなかったのと同じく、利用者 50 名の社内向けとして受け入れた。(2) アクセストークンの有効期限は短い（既定 5 分、`mastersmith.auth.access-token-ttl`）。ただし、リフレッシュトークン（既定 24 時間）が漏れていれば更新で取り直せるため、試せる時間の上限はリフレッシュトークンの有効期限までになる。(3) 今のパスワードの誤りは1回ずつ監査に残る（eventType `PASSWORD_CHANGED`・result `FAILURE`・failureReason `CURRENT_PASSWORD_MISMATCH`、BR7.2）ため、監査ログで見つけられる | 利用者の規模が増えたとき、社外に公開する配備先が決まったとき、または監査で誤りの連続が見つかったとき |

## 上流との差

| ID | 上流 | 上流の記載 | この段の要件 | 理由と扱い |
|---|---|---|---|---|
| S-D1 | この段の Consolidated Summary Confirmation の Q2 A の要約 | 「監査の PASSWORD_CHANGE_FAILED で見つけられる」 | 監査の出来事の名前は、承認済みの機能設計のとおり eventType `PASSWORD_CHANGED`・result `FAILURE`・failureReason `CURRENT_PASSWORD_MISMATCH` と書いた（R1） | 承認済みの機能設計（BR7.2・BR7.5）と契約 C8 に `PASSWORD_CHANGE_FAILED` という値は無い。要約の呼び方と承認済みの設計が食い違うため、承認済みの設計を正とし、差をここに記録する（`project.md` の Way of Working） |
| S-D2 | 要件 NFR4 | 「どれもサーバー側のテストで 401・403・200 を確かめる」 | 3本の API では 401 と 200・204 を確かめ、403 は当てはまらない（NFR4.1・NFR4.2） | 機能設計の7.1 の D1 と同じ読み。この段で新しく変えた点ではない |
