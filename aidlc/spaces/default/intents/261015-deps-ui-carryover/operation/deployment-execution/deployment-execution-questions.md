# Deployment Execution の質問（Intent 261015-deps-ui-carryover）

## 決まっていること（質問にしない）

- **配備の手順**: Deployment Pipeline の `cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md` のとおり（順 1〜10、スモークテスト S1〜S10）。
- **配備の前の検査**: verify・CI・E2E は Build and Test の結果を正とし、流し直さない。k6 は省く（同じソースで試験済み）。
- **内部DB の移行**: 無い（V12 のまま）。移行の実行は要らない。
- **依存するサービス**: 見本の対象DB（`mastersmith-targetdb-postgres-1`）は動いている。Mailpit は入れ替える（Deployment Pipeline の Q1: A）。
- **戻し方**: イメージだけ（戻し先 `mastersmith:pre-deps-ui-carryover`）。

## 質問

### Q1. 救済の見込み（初期管理者）

新しい版の起動のたびに、初期管理者の設定の利用者が停止中・管理者の印なし・パスワードが `.env` の値と違う、のどれかに当たると、自動で救済が働きます（初期管理者のパスワードが `.env` の値に戻り、ログイン中の端末はログインし直しになる）。前の配備（Intent 261004-role-menu）の後に、初期管理者について次のことをしましたか？

A. どれもしていない（画面でパスワードを変えていない・止めていない・印を外していない）
B. 画面で初期管理者のパスワードを変えた（救済で `.env` の値に戻ることを受け入れる）
C. 初期管理者を止めた、または印を外した（救済で戻ることを受け入れる）
D. 分からない（入れ替えの前の複写で、停止と印の有無を数えて確かめてから決める）
X. Other (please specify)

[Answer]: A

### Q2. 配備を始める時期とアプリの停止

入れ替えの前の複写のためにアプリを止めてから healthy になるまで約 2 分、配備の後の監査の確かめの複写で約 10 秒、アプリが止まります。

A. 今から始める（上の停止を受け入れる）
B. 時期を指定する（Other に時刻を書く）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Q1: A — 前の配備の後に、初期管理者のパスワードを画面で変えていない・止めていない・印を外していない（救済は働かない見込み。入れ替えの前の複写で停止と印の有無を数えて裏付ける）。
- Q2: A — 今から配備を始める（入れ替えで約 2 分、配備の後の確かめで約 10 秒のアプリの停止を受け入れる）。
- 決まっていること: Deployment Pipeline の手順のとおり（WAR の作り直し、戻し先のタグ、複写と DSL の最長の行の測り直し、アプリと Mailpit の入れ替え、スモークテスト S1〜S10、`main` への fast-forward は承認を得てから、push は依頼者）。移行は無い。戻しはイメージだけ。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
