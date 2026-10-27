# Deployment Execution の質問（Intent 261025-deps-perf-carryover）

## 決まっていること（質問にしない）

- **配備の手順**: Deployment Pipeline の `cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md` のとおり（流れの順 1〜14、スモークテスト S1〜S9）。
- **配備の前の検査**: verify・osvScan・E2E 256 件・CI は Build and Test の結果を正とし、流し直さない（`9bf6b70` の CI は 1 回目の試行で success）。配備の前の k6 は流さない（`cd-config.md` 3.3 に決まりとの差を記録済み）。
- **内部DB と `.env`**: 移行なし（V13 のまま）、`.env` の変更なし。配備の前の内部DB の複写と戻しの練習は行わない。配備の後の複写（監査の件数の確かめと配備の後のバックアップ）は取る。
- **見本の PG**: 入れ替えの停止の間に、上乗せ無しで作り直して `127.0.0.1:15432` を閉じる（Deployment Pipeline の Q1: A）。ボリュームは残る。
- **周辺のコンテナ**: Mailpit（v1.31.5）と止まっている otel-lgtm（0.34.0）は作り直さない（Q2: A）。
- **戻し方**: イメージだけ（戻し先 `mastersmith:pre-deps-perf-carryover` = `4cec01e79026`）。
- **スモークテスト**: 小さな範囲（Q3: A）。初期管理者のパスワードは変えない。
- **`main` への取り込み**: `develop` から WAR を作って配備し、スモークテストの後に、取り込みの前の CI の試行ごとの確かめと依頼者の承認を経て `main` を fast-forward する（Q4: A）。push は依頼者が行う。

## 質問

### Q1. 救済の見込み（初期管理者）

新しい版の起動のたびに、初期管理者の設定の利用者が停止中・管理者の印なし・パスワードが `.env` の値と違う、のどれかに当たると、自動で救済が働きます（初期管理者のパスワードが `.env` の値に戻り、ログイン中の端末はログインし直しになる）。前の配備（Intent 261016-business-data-views）の後に、初期管理者について次のことをしましたか？ Build and Test の後に `docker compose start app` で起動し直しているため、救済が働くならその時点で働いています。

A. どれもしていない（画面でパスワードを変えていない・止めていない・印を外していない）
B. 画面で初期管理者のパスワードを変えた（救済で `.env` の値に戻ることを受け入れる）
C. 初期管理者を止めた、または印を外した（救済で戻ることを受け入れる）
D. 分からない（配備の後の複写で `INITIAL_ADMIN_RESCUED` の件数と停止・印の有無を数えて確かめる）
X. Other (please specify)

[Answer]: A

### Q2. 配備を始める時期とアプリの停止

入れ替えの停止の間（約 1〜2 分の見込み）に、見本の PG の作り直し（十数秒）とイメージの作り直しを行うため、アプリは止まります。配備の後の複写で、さらに約 10 秒止まります。

A. 今から始める（上の停止を受け入れる）
B. 時期を指定する（Other に時刻を書く）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- Q1: A — 前の配備の後に、初期管理者のパスワードを画面で変えていない・止めていない・印を外していない（救済は働かない見込み。配備の後の複写で `INITIAL_ADMIN_RESCUED` の件数を数えて裏付ける）。
- Q2: A — 今から配備を始める（入れ替えで約 1〜2 分、配備の後の複写で約 10 秒のアプリの停止を受け入れる。停止の直前に、もう一度承認を得る）。
- 決まっていること: Deployment Pipeline の手順のとおり（停止の前の WAR の作り直しと確かめ、戻し先のタグ、見本の PG の番号を閉じる、入れ替え、health、スモークテスト S1〜S9、配備の後の複写と監査の件数、`main` への fast-forward は承認を得てから、push は依頼者）。移行なし。戻しはイメージだけ。

Does this all look correct before I execute the deployment?

- Looks correct
- Request changes

[Answer]: Looks correct
