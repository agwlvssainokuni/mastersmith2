# Code Generation — Questions（U1 アプリの骨格 / u1-app-skeleton）

U1 の作業計画（`code-generation-plan.md`）の7章で、決めていただく点と確認していただく点です。回答を計画に反映してから、計画の承認（Plan Approval）をお願いします。

### Q1. フレームワークの標準の 4xx（405・406・415・壊れた本文など）の扱いは、どうしますか？（計画 P1）

U1 の決まり BR5.6 を文字どおりに読むと、決めていない例外はすべて 500 / `INTERNAL_ERROR` になり、利用者の誤った要求でも ERROR のログとスタックトレースが出ます。

- A. 状態コードはそのままにし、専用の code（`METHOD_NOT_ALLOWED`・`NOT_ACCEPTABLE`・`UNSUPPORTED_MEDIA_TYPE`・`MALFORMED_REQUEST`）と日英の説明を付ける。ログは WARN でスタックトレースなし。パラメータの不足や型の不一致は `VALIDATION_FAILED` にまとめる
- B. BR5.6 を文字どおりに読み、すべて 500 / `INTERNAL_ERROR` にする
- X. Other (please specify)

[Answer]: A. 状態コードはそのままにし、専用の code（`METHOD_NOT_ALLOWED`・`NOT_ACCEPTABLE`・`UNSUPPORTED_MEDIA_TYPE`・`MALFORMED_REQUEST`）と日英の説明を付ける。ログは WARN でスタックトレースなし。パラメータの不足や型の不一致は `VALIDATION_FAILED` にまとめる

### Q2. ビルドした WAR で CSP を確かめる E2E（Playwright）を、どこで実行しますか？（計画 P2）

CSP の確かめ方は U1 の NFR 設計で決まっています（ビルドした WAR で Playwright を動かし、CSP 違反が出ないことを確かめる）。実行にはブラウザの用意と WAR の起動が要り、時間がかかります。

- A. 別の Gradle タスク（`e2eTest`）にし、`./gradlew verify` と CI には入れない。統合の前に手で実行する
- B. `./gradlew verify` と CI に入れる（承認済みの `cicd-pipeline.md` の段に E2E を加える変更になる）
- X. Other (please specify)

[Answer]: A. 別の Gradle タスク（`e2eTest`）にし、`./gradlew verify` と CI には入れない。統合の前に手で実行する

### Q3. make-you-chic-ui のビルドを、サブモジュールの中で行ってよいですか？（計画 C1）

組み込みガイドの手順どおり、`vendor/make-you-chic-ui` の中で依存関係を入れてビルドします。できるのは、サブモジュール自身が Git で無視する `node_modules/` と `dist/` だけで、サブモジュールの管理しているファイルは変えません。

- A. よい（「`vendor/make-you-chic-ui` の中身を変更しない」に反しない扱いとする）
- B. よくない。サブモジュールの外（例: `frontend/` の中）でビルドする方法を考える
- X. Other (please specify)

[Answer]: A. よい（「`vendor/make-you-chic-ui` の中身を変更しない」に反しない扱いとする）

### Q4. 後の単位が画面の文言を足せるよう、画面の登録（`FeatureRegistration`）に任意の項目 `messages` を加えてよいですか？（計画 C2）

U1 の `entities.md` には無い項目です。加えないと、U2・U3 が自分の文言を U1 の文言のファイルに書き足す必要があり、「後の単位は U1 のファイルを変えない」決まりに反します。

- A. 加える（承認済みの `entities.md` との違いとして記録する）
- B. 加えない。別の方法を考える
- X. Other (please specify)

[Answer]: A. 加える（承認済みの `entities.md` との違いとして記録する）

## Plan Approval

U1 の作業計画（`code-generation-plan.md`、埋め込んだ Testing Contract を含む）と、テストの手順書（`unit-test-instructions.md`）を、この内容で承認しますか？

[Approval Fingerprint]: sha256:v3:6dfe6081f1333705004ca34e8ff7e5c5cfffb6c5e0c3b07f4b3852e9c5cf32f4
[Planned Source]: 880efe69c7e565a4e5408b2a4d1dfd884c1b6ed9b64dd2d118054e626aa059da

- Approve Plan
- Request Changes

[Answer]: Approve Plan
