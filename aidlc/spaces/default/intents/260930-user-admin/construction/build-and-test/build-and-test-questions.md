# Build and Test の質問（260930-user-admin）

コード生成の段で、B1〜B5 の5つの Bolt はそれぞれ統合の前の関門（`:backend:cleanTest :backend:cleanIntegrationTest verify`・`osvScan`・E2E）を通して `develop` に統合済みです。最後の B5 の関門（`81d2423`）では、verify・osvScan・E2E の全体（13 ファイル・152 件）が通りました。承認の場の前に入れた U3 の小さな直し（`b126bdc`、監査の組み立てに失敗したときのログの値）は、作業ブランチの上で clean 付きの verify（単体 1508 件・結合 689 件、行 98.9%・分岐 94.8%）と osvScan を通しています。`develop` の先頭 `7689ade` は `b126bdc` に記録だけを足したもので、push 済み、push の後の CI（run 37106195579）はこの段で確かめます。

決まっていること（質問にしません）:
- 性能の目標（API の p95・画面の時間・接続プール）は、この Intent の流れにある Performance Validation の段が持ち主です。この段では手順書を作り、`Unverified` として持ち主を明記して引き継ぎます（`project.md` の Testing Posture の学び）。
- 段全体のレビューの R-02（どの単位も OK にしていない AC3.2.9・AC3.2.10・AC2.2.6）は、この段の網羅の確認で `UserAdminListApiIT`・`UserAdminOperationsApiIT`・E2E 110 を根拠に判定し、R-03（最新の実測を1か所へ）はこの段の test-results.md にまとめます（承認の場の決定）。
- 後の Intent への持ち越し（N-19 など）は、`construction/code-generation/gate-decisions.md` の4節のとおりです。

## Question 1（develop での verify）
`develop` の先頭 `7689ade` のアプリのソースは、clean 付きの verify を通した U3 の直しの作業ブランチ（`b126bdc`）と同じ中身です。この段で verify をもう一度流しますか？

A. 流し直さず、U3 の直しの作業ブランチの clean 付きの実測（`b126bdc` と同じ中身）を、この段の実測として記録する
B. `develop` で `:backend:cleanTest :backend:cleanIntegrationTest verify` と `osvScan --rerun-tasks` を流し直し（約 11 分）、その値を記録する
X. Other (please specify)

[Answer]: A

## Question 2（E2E の流し直し）
E2E の全体（13 ファイル・152 件）は B5 の関門（`81d2423`）で通りました。その後の変更は U3 の直し（バックエンドの監査の組み立てに失敗したときのログの値だけ）で、画面と API の応答は変わっていません。E2E をこの段で流し直しますか？

A. 流し直さず、B5 の関門の結果を正とする（変わったのは本番では起きない経路のログだけのため）
B. `develop` で E2E の全体を流し直す（約 7 分、Mailpit を使う）
X. Other (please specify)

[Answer]: A

## Question 3（U5 の2回目のレビューの R-03）
U5 の2回目のレビューで、送信中は氏名の欄が読み取り専用になったが、言語の選択（ラジオ）は送信中も変えられ、送った値と画面の値がずれうると指摘されました（Minor）。どう扱いますか？

A. 後の Intent への持ち越しに足す（N-19 と同じ画面の直しとしてまとめる）
B. この段で直す（短命のブランチで `EditProfileDialog`・`useUserAdmin` を直してテストを足し、verify と E2E 110・120 を流してから統合する）
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

- `develop`（`7689ade`）では verify を流し直さず、アプリのソースが同じ U3 の直しの作業ブランチ（`b126bdc`）の clean 付きの verify（単体 1508 件・結合 689 件、行 98.9%・分岐 94.8%）と osvScan（失敗の条件 0・警告 16）を、この段の実測として記録する（Q1: A）。
- E2E は流し直さず、B5 の関門（`81d2423`、13 ファイル・152 件がすべて expected）を正とする（Q2: A）。
- push の後の CI（run 37106195579）の結果を、この段で確かめて記録する。
- U5 の2回目のレビューの R-03（送信中も言語の選択を変えられる）は、後の Intent への持ち越しに足す（Q3: A）。
- 性能の目標（API の p95・画面の時間・接続プール）は、Performance Validation の段を持ち主として `Unverified` で引き継ぐ。
- 段全体のレビューの R-02（AC3.2.9・AC3.2.10・AC2.2.6）は、この段の網羅の確認で `UserAdminListApiIT`・`UserAdminOperationsApiIT`・E2E 110 を根拠に判定し、R-03（最新の実測を1か所へ）は test-results.md にまとめる。
- Test Strategy は Standard のため、結合の手順書を作り、性能・セキュリティの手順書も、この段で確かめる検査と引き継ぐ検査の記録として作る（前の Intent と同じ形）。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
