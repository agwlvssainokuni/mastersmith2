# Code Generation の質問（260925-storage-memory-fixes）

## Question 1
要件 FR2.2（Q3: B）では、メモリの数値の目標を「内訳を測った結果を見て、コード生成の計画の承認の場で決める」としました。そのためには、計画を承認する前に、前の Intent と同じ `dslMixed` の条件で内訳（ヒープ・ヒープ以外・ページキャッシュ）を測る必要があります。測定は `perf/README.md` の手順どおり使い捨ての環境で行い、その間は配備したアプリ（`mastersmith-app-1`）を止めます（前回は約 17 分）。どうしますか？

A. 計画を作る段階で測る（配備したアプリを止め、測り終えたら起動し直す）。計画の承認の場で、測った内訳を見て目標を決める
B. 計画には「測定」と「目標を決める確認」の手順を入れ、コードを変える前に測る。目標は計画の承認ではなく、測定の後の確認で決める（FR2.2 と決める時期がずれる）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Question 2
依頼者の指示「Connection を全部 close する API は、JMX で操作できるようになっていれば OK」を受けて、詰め直しの操作の入口はどうしますか？
承認済みの要件（FR1.3）では、入口は管理者向けの HTTP API（`/api/admin/**`、未認証 401・管理者でない 403）としていました。

A. JMX の操作だけにする（HTTP の API は作らない。要件との差をこの段の成果物に明記し、README に JMX での呼び方を書く）
B. JMX の操作と、管理者向けの HTTP API の両方を作る
X. Other (please specify)

[Answer]: A **Mode:** guided

## Question 3
JMX だけにする場合、監査ログ（FR1.6: 誰が・いつ・結果・前と後の大きさ）の「誰が」はどう扱いますか？
JMX にはアプリのログインの利用者がいません。JMX の接続の認証（利用者名とパスワード）を使うか、接続そのものを手元（コンテナの中・127.0.0.1）だけに限るかでも変わります。

A. 監査ログには残すが、「誰が」は JMX による操作であること（固定の値）とし、JMX は手元からだけ接続できるようにする（外に公開しない）
B. JMX の接続に利用者名とパスワードを付け、その利用者名を「誰が」として監査ログに残す
C. JMX の操作は監査ログに残さず、アプリのログにだけ出す
X. Other (please specify)

[Answer]: X（依頼者の回答: JMXならば、HikariCPの標準機能だけで実現できると理解している。それならば、アプリログや監査ログを差し込むことはできないので、出さなくてOK） **Mode:** guided

## Question 4
計画の「承認の場で決める点」D1〜D9 の選択（選択肢は `code-generation-plan.md` の同じ見出しを参照）。

- D1 メモリの数値の目標: T1・T2・T3
- D2 メモリを減らす手段: M1 だけ・M1＋M2・M1＋M2＋M3
- D3 一時停止の間の要求の扱い: W1・W2・W3
- D4 JMX の呼び方: J1・J2・J3
- D5 時間の上限と打ち切り: A・B
- D6 詰め直しの直後の大きさの上限: G1・G2・G3
- D7 AccessTokenApiIT: 直す・直さない・別の直し方
- D8 JMX と一時停止を有効にする範囲: 既定で有効・環境変数で切り替え
- D9 統合の方法: squash・fast-forward
- X. Other (please specify)

[Answer]: D1: T1 / D2: M1 だけ / D3: W1 / D4: J1 / D5: A / D6: G1 / D7: 直す / D8: 既定で有効 / D9: fast-forward **Mode:** guided

## Plan Approval
`code-generation-plan.md`（埋め込みの Testing Contract を含む）と `unit-test-instructions.md` のこの内容で、コードの生成に進んでよいですか？

[Approval Fingerprint]: sha256:v3:502e2770cd8ee694a06798e8c8b39c5cf650df7913c08e16fdc989bf6de90298
[Planned Source]: fcd7697be4e842be034d28a6d4b9f2dcb82a50a33d8878e6ba359ccc11f5b8f1

- Approve Plan
- Request Changes

[Answer]: Approve Plan
