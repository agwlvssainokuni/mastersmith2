# Deployment Pipeline の質問（261003-user-admin-followup）

## 決まっていること（質問にしない）

CI Pipeline・Infrastructure Design の段が無いため、前の Intent（260930-user-admin）の配備の手順（`operation/deployment-pipeline/cd-config.md`・`deployment-strategy.md`・`rollback-runbook.md`）を正とし、今回の差だけを書きます（project.md の Deployment の学び）。

- **方式**: 1台の置き換え（`docker compose --profile targetdb-postgres up -d --build app`）。承認は依頼者。
- **スキーマと `.env`**: どちらも変わらない（移行は V9 のまま。設定の変更は `application.yaml` の `org.hibernate.orm.jdbc.error: OFF` の1行だけで、イメージの中にある）。そのため、配備の前のバックアップと戻しの練習は行わず、戻しはイメージだけにする（project.md の学び）。
- **戻し先**: 今動いている `mastersmith:local`（`f386b55f16a8`、Intent 260930-user-admin の版）に `mastersmith:pre-user-admin-followup` のタグを付けて戻し先にする。戻しても利用停止の機能は残る（V9 のまま）。戻したときは、今回の直し（閉じた後のフォーカス・メニュー・言語の欄・一意の違反のログ）が無い状態に戻る。
- **配備の前の k6**: Build and Test で、配備するものと同じアプリのソースのイメージで負荷の試験を済ませた（T1、上限 4）。project.md の学び（試験済みのときは省く）により、配備の前の k6 は流さない。
- **README.md の古い固定先の記述**（38 行。`077f5b4` と書いてあるが今は `e82b651`）: Build and Test の承認の場の決定で、この段で直す。経緯に `3d9521a`（Intent 260930-user-admin）と `e82b651`（この Intent）を足す。文書だけの変更で、短命のブランチで verify を通してから `develop` へ squash で統合する。
- **完了の基準**（要件の確かめの指摘 R-02）: 健全性（`/actuator/health` が UP）、起動のログに ERROR が無い、依頼者のログイン、利用者の管理の一覧が出ること。どれも通るまで配備の完了としない。

## Question 1
配備するイメージをどう作りますか？（Build and Test の T1 では、`a1b41e7` のソースで `mastersmith:user-admin-followup`（`860fe66a90e7`）を作りました。その後の `develop` の先頭 `c50e64c` は、文書（`perf/README.md`）と記録だけが増えた版です）

A. 前の Intent と同じく、`develop` の先頭で `--build` して `mastersmith:local` を作り直す（WAR も作り直す）
B. T1 で使った `mastersmith:user-admin-followup` に `local` のタグを付けて、作り直さずに配備する
X. Other (please specify)

[Answer]: A

## Question 2
配備の後のスモークテストで、今回の直しを画面で確かめますか？（どれも管理の操作を確定させないため、監査には残りません）

A. 確かめる。依頼者が、利用者の一覧で右端の行の「操作」を開いてメニューが欠けないこと（K2）と、確かめの表示を開いて「やめる」で閉じた後にフォーカスが行の「操作」に戻ること（K1）を見る
B. 確かめない。E2E（153 件）の結果を正とし、スモークテストは健全性・ログ・ログイン・一覧だけにする
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- 「決まっていること」のとおり、前の Intent の配備の手順を正とし、1台の置き換え・バックアップと戻しの練習なし・戻しはイメージだけ（戻し先 `mastersmith:pre-user-admin-followup` = `f386b55f16a8`）・配備の前の k6 なし・README.md の古い固定先の記述をこの段で直す・完了の基準（健全性・起動のログ・ログイン・一覧）とする。
- Q1: A — `develop` の先頭で `--build` して `mastersmith:local` を作り直して配備する。
- Q2: A — スモークテストで、依頼者が右端の行の「操作」のメニューが欠けないこと（K2）と、確かめの表示を「やめる」で閉じた後にフォーカスが行の「操作」に戻ること（K1）を画面で見る（管理の操作は確定させない）。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
