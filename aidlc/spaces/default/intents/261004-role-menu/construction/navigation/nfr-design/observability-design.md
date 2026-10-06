# 観測の設計 — U5 navigation

## 出典

- この単位の承認済みの NFR 要件 `construction/navigation/nfr-requirements/observability-requirements.md`（NFR1.8、受け入れた制約）
- この単位の承認済みの機能設計 `construction/navigation/functional-design/rules.md`（BR4.2・BR5.5・BR7.3・BR7.4）
- この段の答え `nfr-design-questions.md`（まとめの確認の要点 4）
- 捨ての試しの結果: `reliability-design.md` の 1節（N2-d）
- コード: `common/observability/UrlQueryStrippingObservationFilter.java`・`common/observability/TraceAspect.java`・`common/error/web/GlobalExceptionHandler.java`・`backend/src/main/resources/application.yaml`

## 1. 使う仕組み（足さない）

- 指標: 既存の `http.server.requests`（`uri` は道の型、バケットは 100〜5000 ms）。独自の指標・警報は足さない。
- トレース: 既存の仕組み。`UrlQueryStrippingObservationFilter` が問い合わせの部分を属性とタグから取り除く。
- ログ: 業務のログ・アイコンの置き換えのログは出さない。4xx は既存の `GlobalExceptionHandler` の WARN の1行（code・状態・例外の型）だけ。
- 監査: 足さない。

## 2. 問い合わせの値を記録に出さない（NFR1.8）

- `NavigationQueryExposureIT`: 目印の文字列（例 `zz-marker-…`）を名前に持つテーブルを使い捨ての DSL に置き、置き場の問い合わせを 200・403・400（空の引数）で送る。次のどれにも目印が無いことを確かめる。
  - テストの中で集めたスパンの属性（既存の `TracingAndLoggingIT` と同じ、テストの集め手を使う）
  - `http.server.requests` のタグ
  - アプリのログ（INFO 以上。既存のログの集め手）
  - 応答の本文（403・400 の `instance` は道だけ）
- 監査の行の数が増えないことも同じテストで確かめる。
- 正しくない `%` の並びの要求（3節）は、このテストの対象にしない。扱いが決まった後に足す。

## 3. 正しくない `%` の並びで値がログに出る（試し N2-d、受け入れた制約）

- 事実: `GET …?schema=s&table=a%zz` のような要求は、Tomcat の引数の解読で `InvalidParameterException` になり、`GlobalExceptionHandler` が想定外として 500 `INTERNAL_ERROR` を返す。ERROR のログのスタックトレースの例外の文に `Parameter [table] with value [a%zz]` のように引数の名前と値が入る。応答の本文には値は入らない。
- この振る舞いは、引数を持つアプリのすべての API で今もある。navigation で新しく生じるものではないが、navigation の置き場は名前を引数で受けるため、細工した要求の値がログに出る経路が NFR1.8 と食い違う。
- 正しくエンコードした名前（試し N1）では起きない。画面（U7）は必ず正しくエンコードする。
- 認証の前か後か: 試しでは、正しくない `%` の並びをログインなしで通る道（`/trial/echo`）にだけ送った。ログインだけの道（`/api/…`）では送っていないため、未認証の要求が 401 になる前にこの 500 と ERROR が起きるか（認証の前か後か）は**未確認**。
- 扱い（依頼者の決定、案 1）: **受け入れた制約として記録する**。
  - ログに出るのは、攻撃者が送った崩れた値（送った本人の文字列）で、秘密でも個人に関する値でもない。応答の本文には値は入らない。
  - アプリ全体の今の振る舞いで、引数を持つすべての API で起きる。navigation で新しく生じるものではない。
  - アプリ全体の直し（共通の誤りの変換 `common.error.web` で `InvalidParameterException` を 400 `VALIDATION_FAILED` にし、例外の文をログに出さない）は、後の Intent に回す。この Intent の B7 では行わない。
  - そのため NFR1.8 は、正しくエンコードした要求について満たす「部分」とする（`traceability.json`）。

## 4. 手元の監視で見る式（受け入れた制約の補い）

- p95 の警報は足さない（承認済み）。Observability Setup で確かめる式を2つ名指しする。式は書く前に実際に起動して、指標とラベルの名前とバケットを確かめる（`project.md` の学び）。
  - メニュー: `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/api/me/navigation"}[5m])))`
  - 置き場: 同じ形で `uri="/api/me/table-access"`
- 1000 ms の境界がバケットにあるため、目標の 1 秒の超え方は読める。

## 5. 受け入れた制約（承認済み）

- p95 の警報が無い。目標の確かめは Performance Validation の1回に限られる。
- TRACE のログにスキーマ名・テーブル名が出る。
- 監査が無い（置き場の 403 も残さない）。
- 正しくない `%` の並びの引数は 500 になり、ERROR のログの例外の文に引数の名前と値が入る（3節。依頼者の決定で受け入れた。直しは後の Intent）。

## 承認の場の決定と直し

- 決定: 依頼者は承認の場で Request Changes を選び、決定の文は「推奨の案のとおり直す」（各単位の読み直しの Major と、単位の間でそろえる3点）。navigation で直すのは R-01・R-02 の2件。
- R-02（Major）: 正しくない `%` の並びで値が ERROR のログに出る件の扱いが決まっていなかった。依頼者の決定は案 1（受け入れた制約として記録する）。直し: 3節を受け入れた制約の書き方に改め、漏れるのは攻撃者が送った崩れた値で秘密ではないこと、アプリ全体の今の振る舞いであること、アプリ全体の直しは後の Intent に回すこと、認証の前か後かは未確認であることを書いた。5節に受け入れた制約を足した。`traceability.json` の NFR1.8 を「部分」にし、target に受け入れた制約の節を書いた。`security-design.md` の 7節・8節もそろえた。
