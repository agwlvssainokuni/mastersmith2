# Infrastructure Design — Questions（U2 認証 / u2-authentication）

U2 の基盤の設計で、これまでの段階で決まっていない点を確認します。

決定済みの事項（再確認はしません）: U2 は U1 の基盤（コンテナ1台、`docker compose`、Temurin JRE 25、`/app/data` のボリューム、`.env` から環境変数、`./gradlew verify`、GitHub Actions、pre-commit）の上で動き、独自のコンテナ・サービスを持たない（U1 の `infrastructure-design/infrastructure-specification.md`）。署名鍵と初期管理者の設定は環境変数で渡す。使い終わったリフレッシュトークンの削除は1日1回（既定 3 時 30 分）。パスワードの照合は bcrypt の cost 12 で、1回 100〜500 ミリ秒、同時 10 件のログインで 95% が1秒以内（U2 の NFR1.1）。

### Q1. コンテナのタイムゾーンは、どれにしますか？

使い終わったトークンの削除の時刻（既定 3 時 30 分）と、ログの時刻の表記（U1 の決まり: タイムゾーン付きの ISO 8601）が、この設定で決まります。U1 の基盤の設計では決めていません。アプリの中で保存する時刻（有効期限・ロックの解除・監査ログの日時）は、タイムゾーンに依存しない形（UTC の時点）で扱います。

- A. `Asia/Tokyo`（日本時間）。削除の時刻とログの時刻が、利用者の感覚と一致する
- B. `UTC`。コンテナの既定のまま。ログの時刻は UTC で表記され、削除は日本時間の 12 時 30 分に動く
- X. Other (please specify)

[Answer]: A. `Asia/Tokyo`（日本時間）。削除の時刻とログの時刻が、利用者の感覚と一致する

### Q2. コンテナの CPU の上限と、照合の重さ（bcrypt の cost）の兼ね合いは、どうしますか？

U1 の基盤の設計はコンテナの CPU の上限を 2 としています。bcrypt の cost 12 の照合は CPU を1つ使って約 0.25 秒かかる見込みのため、同時 10 件のログインを CPU 2 つで処理すると、最後の要求は約 1.25 秒かかり、目標（95% が1秒以内）を超えるおそれがあります（U2 の `nfr-design/performance-design.md` は CPU 4 つ程度を仮定しています）。

- A. コンテナの CPU の上限を 4 にする（U1 の基盤の設計の資源の値を、U2 の要件のために上書きする）。cost は 12 のまま
- B. CPU の上限は 2 のままとし、Performance Validation で測る。目標を超えたら cost を 11 に下げる（1回 約 0.13 秒。NFR2.1 の 100〜500 ミリ秒の範囲に収まる）
- C. CPU の上限を設けない（開発者の PC の CPU をすべて使えるようにする）
- X. Other (please specify)

[Answer]: A. コンテナの CPU の上限を 4 にする（U1 の基盤の設計の資源の値を、U2 の要件のために上書きする）。cost は 12 のまま

## Consolidated Summary Confirmation

- タイムゾーン（Q1）: コンテナのタイムゾーンを `Asia/Tokyo` にする（compose の環境変数 `TZ` と JVM のタイムゾーンの設定）。使い終わったトークンの削除は日本時間の 3 時 30 分、ログの時刻は `+09:00` で表記される。保存する時刻（有効期限・ロックの解除・監査ログの日時）は UTC の時点として扱い、タイムゾーンに依存しない
- CPU の上限と照合の重さ（Q2）: コンテナの CPU の上限を 4 にする（U1 の基盤の設計の「CPU 2」を U2 の要件のために上書きする）。bcrypt の cost は 12 のまま。Performance Validation で同時 10 件のログインを測って確かめる
- そのほか（U1 の基盤をそのまま使う）: U2 は独自のコンテナ・サービスを持たない。署名鍵（Base64、32 バイト以上）と初期管理者のメールアドレス・パスワードを `.env` から環境変数で渡し、`.env.example` に名前と鍵の作り方（例: 乱数 32 バイトを Base64 にする手順）を書く

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
