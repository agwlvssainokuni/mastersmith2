# Deployment Pipeline の質問（Intent 261015-deps-ui-carryover）

## 決まっていること（質問にしない）

- **元の手順**: CI Pipeline・Infrastructure Design の段が無いため、前の Intent（261004-role-menu）の配備の手順（`aidlc/spaces/default/intents/261004-role-menu/operation/deployment-pipeline/`）を正とし、今回の差だけを書く（`project.md` の学び）。
- **スキーマと `.env`**: 内部DB の移行は無く（V12 のまま）、`.env` と `compose.yaml` の app の設定も変えない（メモリの上限 2g・`MaxRAMPercentage` 50 は Build and Test の決定で据え置き）。そのため、戻しはイメージだけ（戻し先のタグ `mastersmith:pre-deps-ui-carryover`）で、戻しの練習は行わない（前の Intent の扱い）。
- **配備の前の k6**: Build and Test で、配備するものと同じソースのイメージを配備と同じ上限で負荷の試験済みのため省く（`project.md` の学び）。`memory.peak` の Not Met は依頼者の決定で持ち越し。
- **配備の前の内部DB の複写**: D-5: A の「配備の前に配備済みの DSL の最も長い行を測り直す」のためにアプリを短く止めて複写する。この複写を配備の前のバックアップに兼ねる。
- **WAR**: `Dockerfile` は手元の WAR を写すだけのため、配備の前に `./gradlew :backend:bootWar` で作り直し、SHA-256 と中身（1行の長さの上限の定数など）を確かめる（`project.md` の学び）。
- **スモークテスト**: 前の手順の項目に、今回の変化（Dropdown が画面に収まる、畳んだサイドバーの今の項目の印、英語の表示のトップバーの文言、新しい書体の日本語の表示）を足す。新しい JRE のアプリに対して `docker/hikari-pool.sh status` が動くこと（Build and Test の T-14）も確かめる。
- **`main` への取り込み**: スモークテストの後、依頼者の承認で `develop` から `main` へ fast-forward する。タグは付けない。push は依頼者。
- **Dependabot**: 配備の後に、④ の #50〜#53 と開いたままの #47・#46・#34・#32 が閉じたかを確かめ、残りの閉じ方のコマンドを示す。

## 質問

### Q1. 動いている Mailpit のコンテナを新しい版に入れ替えるか

依存の更新で `compose.yaml` の Mailpit は v1.31.5 になりましたが、動いているコンテナ（`mastersmith-mailpit-1`、8 日前に起動）は v1.31.2 のままです。入れ替えると、Mailpit が持っているメール（手元で見るための受け手の中身）は消えます。otel-lgtm は profile で見たいときだけ起動するため、次に起動したときに新しい版になります。

A. 配備のときに Mailpit も入れ替える（`docker compose up -d mailpit`）。受け手の中身が消えることを受け入れる
B. 今は入れ替えず、次に Mailpit を作り直すときに新しい版にする
X. Other (please specify)

[Answer]: A

### Q2. 1行の長さの上限の拒否をスモークテストで確かめるか

要件 FR11.2 は、1行の長さの上限を超える DSL が拒否されることをスモークテストに入れるとしています。画面から長い1行の DSL を投入すると、監査に `DSL_SUBMISSION_REJECTED` が1行残ります（追記だけで消せない）。

A. 確かめる。AI が上限を1文字超える1行を持つ DSL のファイルを作り、依頼者が DSL の画面から投入して拒否の文言を確かめる。監査の期待の件数に入れる
B. 確かめない（結合テストの `DslAdminApiIT` で確かめ済みとし、FR11.2 との差を記録する）
X. Other (please specify)

[Answer]: B

## Consolidated Summary Confirmation

- Q1: A — 配備のときに Mailpit も v1.31.5 に入れ替える（受け手の中身が消えることを受け入れる）。
- Q2: B — 1行の長さの上限の拒否はスモークテストで確かめず、結合テスト（`DslAdminApiIT`・`RoleTransferLimitsApiIT`）で確かめ済みとし、要件 FR11.2 との差を記録する。
- 決まっていること: 前の Intent の手順を正として差だけを書く、移行と `.env` の変更が無いため戻しはイメージだけ（戻し先 `mastersmith:pre-deps-ui-carryover`）で戻しの練習なし、配備の前の k6 は省く、配備の前に複写して DSL の最も長い行を測り直しバックアップを兼ねる、WAR を作り直して SHA-256 を確かめる、スモークテストに今回の画面の変化と新しい JRE への `hikari-pool.sh` を足す、スモークの後に `main` へ fast-forward、Dependabot のプルリクエストの閉じ方を確かめる。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
