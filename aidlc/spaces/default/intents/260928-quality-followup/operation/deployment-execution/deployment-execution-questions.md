# Deployment Execution の質問（Intent 260928-quality-followup）

## 配備の前の確かめ（済み）

- 関門: アプリのソースに未コミットの変更は無い。`d1fda19`（Build and Test と CI が通った版）の後のコミットは、ワークフローの記録2つ（`b445b6b`・`35ec464`）だけで、アプリのソースの差は無い。CI 36567275650 は success。
- 今動いている版: `mastersmith:local` は `sha256:9e5243a3…` で、前の Intent の `deployment-log.md` の新しいイメージと同じ ID だった（戻し先として正しい）。
- スキーマの変更: 無い（Flyway の新しい版は無い）。バックアップは要らない。
- 頼っているサービス: 見本の対象DB（PostgreSQL）と Mailpit は動いている。Mailpit は配備の前に止めて消す。
- 承認を得ずに Deployment Pipeline の記録をコミットした（`35ec464`）。依頼者に伝え、「このままでよい」と了承を得た。

## Q1. 配備を始める時点

配備では、アプリを入れ替えるあいだ（起動の数十秒）止まり、見本の対象DB も作り直します。スモークテストの S4〜S7（★）は依頼者の操作で、配備した内部DB と監査に行が残ります（ログイン・DSL の生成と破棄・ログアウト）。

A. 今すぐ始める（`deployment-strategy.md` の 3節の順）。
B. 時間を指定して後で始める（時刻を書く）。
X. Other (please specify)

[Answer]: A

## G1.（配備の途中で見つかった点）スモークテスト S6 で DSL が適用された

アプリのログ（`DslOperationMetrics`）では、S6 の2件目の操作は破棄ではなく適用（22:01:51、`dsl.operation: apply`、`dsl.source: GENERATED`）でした。手順書（`deployment-strategy.md` の S6）は「確かめた後はプレビューを破棄し、適用はしない」としていたため、今の DSL が、見本の対象DB から生成した既定の DSL に置き換わっています。適用の前の DSL は履歴に残っている見込みです。

A. このままでよい（生成した既定の DSL を今の DSL として使う）。記録に差を書く。
B. 履歴から適用の前の DSL に戻す（依頼者が画面の履歴から戻して適用する。監査に行が残る）。記録に差を書く。
X. Other (please specify)

[Answer]: A

## G2.（配備の途中で見つかった点）ログアウトが監査で裏付けられない

依頼者はログアウトしてログイン画面に戻ることを確かめた。しかし、配備の後の内部DB の複写（22:05:08 に取得）では、次のとおりだった。

- ログイン（22:00:39）で出したリフレッシュトークンが有効のまま（`revoked_at` が空）
- `LOGGED_OUT` が監査に無い

`LogoutService` は、Cookie のリフレッシュトークンが有効なときだけ無効にして `LOGGED_OUT` を記録する。Cookie が無い・無効でも、応答は 204 で Cookie を消す。アプリのログには、ログアウトに関わる WARN は無かった。

A. もう一度ログインしてログアウトしていただき（`http://localhost:8080` で）、アプリを数秒止めて複写し、監査とトークンの状態を確かめ直す。再現したら、原因を調べるかを改めて決める。
B. 確かめ直さず、「画面ではログイン画面に戻ったが、サーバー側でトークンが無効になったことは裏付けられなかった」と記録し、次の Intent の候補にする。
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Q1: A。今すぐ配備を始めた。Mailpit の片付け → 戻し先のタグ `mastersmith:pre-quality-followup` → `docker compose --profile targetdb-postgres up -d --build` → スモークテスト S1〜S8（S4〜S7 は依頼者の操作、AI は監査とログで裏付け）。
- G1: A。S6 で適用された既定の DSL を、今の DSL としてそのまま使う。差を記録する。
- G2: A。ログアウトを確かめ直し、2回とも `LOGGED_OUT` とトークンの無効化を確かめた（1回目の複写はログアウトの前だった）。
- `main` への取り込み: この段の記録を承認・コミットした後の `develop` の先頭へ fast-forward する（依頼者の決定）。タグは付けない。プッシュは依頼者。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
