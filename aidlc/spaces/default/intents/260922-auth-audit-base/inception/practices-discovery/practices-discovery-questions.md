# Practices Discovery — Questions

主担当（リリースエンジニア）の初稿と、品質・開発・セキュリティの3者の独立レビューをもとに、このプロジェクトの進め方を決めます。決めた内容は、承認時に `aidlc/spaces/default/memory/team.md`（チームの進め方）と `aidlc/spaces/default/memory/project.md`（必ず守る／絶対にしない制約）に反映され、以後の全Intentで使われます。

各質問の「（推奨）」は、初稿とレビューで推奨された案です。

- 初稿: `aidlc/spaces/default/intents/260922-auth-audit-base/inception/practices-discovery/team-practices.md`、`discovered-rules.md`、`evidence.md`
- レビュー: `aidlc/spaces/default/intents/260922-auth-audit-base/inception/practices-discovery/contributions/`

---

## 1. 進め方（ブランチと統合）

### Q1. 日々の作業をまとめる先（統合先）のブランチはどれにしますか？

現在はすべて `develop` に直接コミットしています。`main` はリモートに最初のコミットだけがあり、ローカルにはありません。

- A. `develop` を統合先にする。`main` はリリース（確定版）の置き場とし、リリース時に承認のうえ `develop` から取り込む（推奨）
- B. `main` だけを統合先にする。今の `develop` の履歴を `main` に取り込み、以後 `develop` は使わない
- X. Other (please specify)

[Answer]: A. `develop` を統合先にする。`main` はリリース（確定版）の置き場とし、リリース時に承認のうえ `develop` から取り込む

### Q2. 作業単位（Bolt）ごとの作業ブランチを統合先に戻すとき、どうまとめますか？

Bolt は、実装段階で1回にまとめて作る作業の単位です。

- A. 1つのコミットにまとめて戻す（squash）。細かい履歴は作業ブランチと監査ログに残る
- B. 個々のコミットを残したまま戻す（マージコミット）。これまでの「こまめなコミット」が統合先の履歴にも残る
- X. Other (please specify)

[Answer]: A. 1つのコミットにまとめて戻す（squash）。細かい履歴は作業ブランチと監査ログに残る

### Q3. テストや検査に通らない変更が統合先に入るのを、何で止めますか？

コミットして `origin` にプッシュした後に動く CI（自動ビルド・テスト）だけでは、失敗を後から知らせることしかできず、統合を止められません。

- A. GitHub のプルリクエストを使い、CI が通らないと統合できない設定（ブランチ保護）にする
- B. プルリクエストは使わない。CI と同じ検査をローカルで1コマンドで実行し（プッシュ前に自動実行するフックも可）、通ってから統合する。CI は統合後の再確認とする（推奨）
- C. A と B の両方
- X. Other (please specify)

[Answer]: B. プルリクエストは使わない。CI と同じ検査をローカルで1コマンドで実行し（プッシュ前に自動実行するフックも可）、通ってから統合する。CI は統合後の再確認とする

### Q4. `origin` へのプッシュは、コミットと同じく毎回承認を取りますか？

- A. 毎回、実行前に承認を取る（推奨）
- B. 承認済みのコミットはまとめてプッシュしてよい（プッシュ自体の承認は不要）
- C. プッシュはあなた自身が行う（AI はプッシュしない）
- X. Other (please specify)

[Answer]: C. プッシュはあなた自身が行う（AI はプッシュしない）

### Q5. GitHub のこのリポジトリは公開（public）ですか、非公開（private）ですか？

使えるセキュリティ検査の道具（GitHub の CodeQL や秘密情報のスキャンなど）が、公開なら無料、非公開なら有償になります。

- A. 公開
- B. 非公開
- C. 今は非公開で、いずれ公開する予定
- X. Other (please specify)

[Answer]: A. 公開

---

## 2. 最初の土台

### Q6. 最初に端から端まで通る薄い版を作りますか？ walking skeleton とは、全体が一通り動く最小限の版のことで、本格的な機能を入れる前に、部品同士がつながることを確かめるために最初に作ります。

- A. 作る。最初の Bolt は単独で進め、あなたの承認を得てから残りの Bolt に進む（推奨）
- B. 作らない。最初の Bolt から通常どおり機能を作る
- X. Other (please specify)

[Answer]: B. 作らない。最初の Bolt から通常どおり機能を作る

### Q7. 最初の土台に含める範囲はどれにしますか？

- A. 次の一式（推奨）: バックエンドの起動・内部DB接続・ヘルスチェック／構造化ログの出力／make-you-chic-ui を組み込んだフロントエンドのビルドとログイン画面の枠の表示／フォーマット・リンタ・ライセンスヘッダー・秘密情報の検出を含む検査とテストを1コマンドで実行／カバレッジの計測と下限の検証／DB を使うテスト1件以上／共通のエラー応答の仕組み／CI 上でも同じ検査が通る
- B. 最小限: 起動・DB接続・ヘルスチェック・ログイン画面の枠の表示と、ビルドとテストが1コマンドで通ることだけ。検査類は後の Bolt で足す
- X. Other (please specify)

[Answer]: A. 次の一式（推奨）

---

## 3. テスト

### Q8. テストはいつ書きますか？

- A. 実装の後に書く（test-after）: 層ごとに実装を書き、同じ Bolt の中でその層のテストを書いて通してから次の層へ進む
- B. 重要なロジックだけ先に書く（custom）: パスワード照合・ロック判定・トークンの発行と検証・管理者権限の判定・監査記録は、先に失敗するテストを書いてから実装する。それ以外は実装の後に書く（推奨）
- C. すべて先に書く（TDD）: 先に失敗するテストを書き、通す最小限の実装を書き、整理する
- X. Other (please specify)

[Answer]: A. 実装の後に書く（test-after）

### Q9. テストで実行されたコードの割合（カバレッジ）の下限をどうしますか？

下限は、このチームの以後の全Intentに共通でかかります。アプリの起動クラス、設定値だけのクラス、自動生成コード、`vendor/make-you-chic-ui` は計測から外します。

- A. 行カバレッジ 80% 以上、分岐カバレッジ 70% 以上（推奨）
- B. 行カバレッジ 80% 以上のみ
- C. A に加え、認証・トークン・権限判定・監査記録の部分は行・分岐とも 90% 以上
- X. Other (please specify)

[Answer]: A. 行カバレッジ 80% 以上、分岐カバレッジ 70% 以上

### Q10. DB を使うテストでは、本番と同じ種類の DB を使いますか？

- A. 本番と同じ種類の DB をコンテナで起動して使う（ローカルと CI にコンテナの実行環境が必要）（推奨）
- B. 組み込みの軽量な DB で代用する
- X. Other (please specify)

[Answer]: A. 本番と同じ種類の DB をコンテナで起動して使う（ローカルと CI にコンテナの実行環境が必要）

### Q11. 画面からの一連の操作を確かめるテスト（E2E）を入れますか？

- A. 入れる。「ログイン → 管理画面に入れるか → ログアウト」の1〜2本に絞る
- B. 今は入れない（推奨）
- X. Other (please specify)

[Answer]: A. 入れる。「ログイン → 管理画面に入れるか → ログアウト」の1〜2本に絞る

### Q12. 入力と出力の性質をランダムな入力で確かめるテスト（性質ベースのテスト）を使いますか？

make-you-chic-ui では純粋な関数に一部適用しています。

- A. 使う。ロック判定の回数計算・有効期限の判定・入力の検証などの純粋な関数に一部適用する（推奨）
- B. 使わない
- X. Other (please specify)

[Answer]: A. 使う。ロック判定の回数計算・有効期限の判定・入力の検証などの純粋な関数に一部適用する

### Q13. テストの説明文（テスト名）は何語で書きますか？

- A. 英語（make-you-chic-ui と同じ）。テストデータは日本語でよい
- B. 日本語
- X. Other (please specify)

[Answer]: A. 英語（make-you-chic-ui と同じ）。テストデータは日本語でよい

---

## 4. 配備

### Q14. 当面の配備先はどうしますか？

- A. 当面は開発者のPC上のコンテナでの起動・確認のみ。クラウドへの配備は後で決める（推奨）
- B. 検証環境を1つ用意して統合ごとに自動で配備し、本番環境はあなたの手動承認があるときだけ配備する
- X. Other (please specify)

[Answer]: A. 当面は開発者のPC上のコンテナでの起動・確認のみ。クラウドへの配備は後で決める

### Q15. CI（自動ビルド・テスト）はどこで動かしますか？

- A. GitHub Actions（推奨）
- B. 当面は CI を置かず、ローカルの1コマンド検査のみ
- X. Other (please specify)

[Answer]: A. GitHub Actions

### Q16. 成果物の版はどう付けますか？

- A. 成果物にコミットのハッシュを付け、リリース時にタグを付ける（推奨）
- B. 意味のある版番号（例: 1.2.0）を付け、リリース時にタグを付ける
- X. Other (please specify)

[Answer]: A. 成果物にコミットのハッシュを付け、リリース時にタグを付ける

---

## 5. コード規約

### Q17. リポジトリ内のバックエンドとフロントエンドの置き場所はどうしますか？

- A. ルート直下に `backend/`（Java）と `frontend/`（React + TypeScript）を分けて置く（推奨）
- B. 1つのディレクトリにまとめる
- X. Other (please specify)

[Answer]: A. ルート直下に `backend/`（Java）と `frontend/`（React + TypeScript）を分けて置く。ただし、ビルドの成果物は実行可能WARとし、frontendのコンポーネント(dist配下)もWARにパッケージングする。開発時はdev serverのproxyを介してbackendのAPIを呼び出す。ビルド成果物は実行可能WARのAPIを呼び出す。つまり、SPAは配信元と同じサーバのAPIを呼び出す形とし、CORS設定なしでAPIを呼べる構成とする。

### Q18. バックエンドのビルドツールは何にしますか？

フォーマッタ・静的検査・カバレッジの設定方法がこれで決まるため、最初の Bolt より前に決める必要があります。

- A. Gradle（Kotlin DSL）
- B. Maven
- C. ここでは決めず、後の設計ステージで決める
- X. Other (please specify)

[Answer]: A. Gradle（Kotlin DSL）

### Q19. Java のルートパッケージ名は何にしますか？

- A. `io.github.agwlvssainokuni.mastersmith`
- B. 別の名前にする（自由記述してください）
- X. Other (please specify)

[Answer]: B. 別の名前にする（名前はQ31で確認）

### Q20. Java のパッケージの分け方はどうしますか？

- A. 機能ごとに分け（例: `auth`・`user`・`audit`）、各機能の中を画面入出力・業務処理・ドメイン・DBアクセスの層に分ける（推奨）
- B. 層ごとに分ける（`controller`・`service`・`repository` を最上位に置く）
- X. Other (please specify)

[Answer]: A. 機能ごとに分け（例: `auth`・`user`・`audit`）、各機能の中を画面入出力・業務処理・ドメイン・DBアクセスの層に分ける

### Q21. Java のフォーマッタ（コードを自動整形する道具）は何にしますか？

- A. palantir-java-format（インデント4、1行120文字）
- B. google-java-format（インデント2、1行100文字。フロントエンドと見た目が揃う）
- C. IntelliJ の既定スタイル（インデント4、1行120文字。CI での再現はしにくい）
- X. Other (please specify)

[Answer]: A. palantir-java-format（インデント4、1行120文字）

### Q22. コード中のコメントは何語で書きますか？

- A. コード中のコメントは英語、画面の文言・コミットメッセージ・ドキュメントは日本語（make-you-chic-ui と同じ）（推奨）
- B. コード中のコメントも日本語
- X. Other (please specify)

[Answer]: B. コード中のコメントも日本語

### Q23. 次の開発規約をまとめて採用しますか？

- フロントエンドは make-you-chic-ui と同じ Prettier・oxlint・ESLint・Stylelint・tsconfig の設定にする。設定は参照せず `frontend/` 側に複製し、画面に直接HTMLを埋め込む機能の禁止などのセキュリティ系ルールを足す。名前付きエクスポートのみ、`enum` を使わない
- エラー応答は標準形式（RFC 9457 Problem Details）に、画面が分岐に使う `code` 項目を足す。例外は1か所でまとめて応答に変換する
- Lombok は使わない（Java の `record` とコンストラクターでの受け渡しで代える）
- 層の境界（画面入出力の層は DB アクセスを直接呼ばない、トランザクションは業務処理の層だけ、DB の内容を画面にそのまま返さない、など）を決め、構造検査の道具（ArchUnit など）で自動確認する
- 文字コード UTF-8・改行 LF を `.editorconfig` と `.gitattributes` で統一する
- ライセンスヘッダーは `/* ... */` で書き、CI で有無を検査する
- フォーマッタ・リンタは `vendor/` を対象から外す

- A. まとめて採用する（推奨）
- B. 一部だけ採用する（採用しないものを自由記述してください）
- X. Other (please specify)

[Answer]: A. まとめて採用する

### Q24. 次のセキュリティ検査を段階的に入れる計画を採用しますか？

- 最初の土台で入れる: 秘密情報の検出（Gitleaks）、Java の静的解析（SpotBugs＋FindSecBugs）、依存関係の脆弱性検査と更新通知（Dependabot＋OSV-Scanner 等。`vendor/make-you-chic-ui` も対象）、`.gitignore` に秘密情報のファイル（`.env`・鍵ファイル）を追加
- 機能の開発と並行して入れる: より広い静的解析（CodeQL または Semgrep）
- 配備先が決まってから入れる: 部品表（SBOM）、起動したアプリへの検査（OWASP ZAP）、コンテナイメージの検査
- 統合を止める基準は、重大度 High 以上

- A. この計画で採用する（推奨）
- B. 一部だけ採用する（自由記述してください）
- C. 最初は秘密情報の検出だけにし、他は後で決める
- X. Other (please specify)

[Answer]: B. 一部だけ採用する（採用するものはQ32・Q33で確認）

### Q25. コミットの前に検査を自動実行する仕組み（pre-commit フック）を入れてよいですか？

秘密情報の検出やフォーマット検査を、コミットの直前に自動で実行します。

- A. 入れる（推奨）
- B. 入れない（ローカルの1コマンド検査と CI だけにする）
- X. Other (please specify)

[Answer]: A. 入れる

---

## 6. 必ず守ること・絶対にしないこと

ここで「明言する」と選んだものだけが、`project.md` の必ず守る／絶対にしない制約として記録されます。選ばなかったものは記録しません。

### Q26. 品質・配備について、明言するものはどれですか？（select all that apply）

- A. ALWAYS 統合前に、フォーマット・リンタ・ビルド・全テスト・秘密情報の検出・依存関係の脆弱性検査（High 以上）が通っていることを確認する
- B. ALWAYS 認証・認可・監査ログに関わる変更には、失敗の場合（拒否・ロック・無効なトークンなど）のテストを含める
- C. ALWAYS 不具合を修正するときは、その不具合を再現するテストを同じコミットに含める
- D. ALWAYS 本番環境への配備は、あなたの手動承認を得てから行う
- E. None
- X. Other (please specify)

[Answer]: A, B, C

### Q27. 秘密情報について、明言するものはどれですか？（select all that apply）

- A. NEVER 初期管理者のパスワードやトークンの署名鍵などの秘密情報を、ソースコードや設定ファイルに直接書かない
- B. NEVER パスワード（平文・ハッシュ値とも）・アクセストークン・リフレッシュトークン・署名鍵を、ログ・監査ログ・トレースの属性・外部へのエクスポート・エラー応答に含めない
- C. NEVER `.env` や鍵ファイルなど秘密情報を含む設定ファイルをコミットしない（見本は値を空にした `.env.example` として置く）
- D. NEVER セキュリティ検査の指摘を、理由と見直し期限を書かずに抑止・無効化しない
- E. None
- X. Other (please specify)

[Answer]: A, B, C

### Q28. サブモジュール・依存関係・テストについて、明言するものはどれですか？（select all that apply）

- A. NEVER `vendor/make-you-chic-ui` の中身をこのリポジトリから直接変更しない（変更は make-you-chic-ui のリポジトリ側で行う）
- B. ALWAYS サブモジュールの固定先の更新は、承認を得た専用のコミットで行い、更新前後のコミットハッシュを記録する
- C. ALWAYS 依存関係は lockfile で版を固定し、CI では lockfile どおりに入れる（`npm ci` など）
- D. NEVER テストを無効化した状態（`@Disabled`・`it.skip` など）で統合先へ統合しない
- E. None
- X. Other (please specify)

[Answer]: A, B, C

### Q29. CI・検査の運用について、明言するものはどれですか？（select all that apply）

- A. ALWAYS 秘密情報の検出を、コミット前と CI の両方で実行する
- B. ALWAYS （CI を GitHub Actions に置く場合）第三者製のアクションはコミットのハッシュで固定し、ワークフローの権限は必要最小限にする
- C. None
- X. Other (please specify)

[Answer]: A

---

## 追加確認

### Q30. （Q6とQ7の確認）Q6で「最初に薄い版は作らない」、Q7で「最初の土台の範囲は一式」と回答いただきました。どちらの意味ですか？

- A. 特別な手順としての「最初の薄い版」は設けない（最初の Bolt も他の Bolt と同じ流れで進める）。ただし Q7 の一式（検査・カバレッジ・CI・共通エラー応答など）は、最初の Bolt に含める
- B. 最初の薄い版は設けず、Q7 の一式も最初の Bolt に限定しない（必要になった Bolt で順次入れる）
- C. Q6 を「作る」に訂正する（最初の Bolt を Q7 の一式を含む薄い版として単独で進め、承認を得てから残りに進む）
- X. Other (please specify)

[Answer]: A. 特別な手順としての「最初の薄い版」は設けない（最初の Bolt も他の Bolt と同じ流れで進める）。ただし Q7 の一式（検査・カバレッジ・CI・共通エラー応答など）は、最初の Bolt に含める

### Q31. （Q19の確認）Java のルートパッケージ名を教えてください。

- A. （自由記述してください）
- X. Other (please specify)

[Answer]: A. `cherry.mastersmith`

### Q32. （Q24の確認）最初の Bolt と開発と並行して入れる検査のうち、採用するものはどれですか？（select all that apply）

- A. 秘密情報の検出（Gitleaks）と、`.gitignore` への秘密情報ファイル（`.env`・鍵ファイル）の追加
- B. Java の静的解析（SpotBugs＋FindSecBugs）
- C. 依存関係の脆弱性検査と更新通知（Dependabot＋OSV-Scanner 等。`vendor/make-you-chic-ui` も対象）
- D. より広い静的解析（CodeQL。リポジトリが公開のため無料）
- E. None
- X. Other (please specify)

[Answer]: A, B, C

### Q33. （Q24の確認）配備先が決まってから入れる検査と、統合を止める基準はどうしますか？（select all that apply）

- A. 部品表（SBOM）の生成を、配備先が決まってから入れる
- B. 起動したアプリへの検査（OWASP ZAP）を、配備先が決まってから入れる
- C. コンテナイメージの検査（Trivy）を、コンテナ化を決めた時点で入れる
- D. 検査の指摘で統合を止める基準は、重大度 High 以上とする
- E. None
- X. Other (please specify)

[Answer]: A, D

## Consolidated Summary Confirmation

- 進め方: 統合先は `develop`（`main` はリリース版の置き場）。Bolt の作業ブランチは squash で戻す。プルリクエストは使わず、CI と同じ検査をローカルの1コマンドで通してから統合し、CI は統合後の再確認。プッシュはあなた自身が行う（AI はプッシュしない）。リポジトリは公開
- 最初の土台: 特別扱いの「最初の薄い版」は設けない。ただし検査・カバレッジ・CI・共通エラー応答などの一式は最初の Bolt に含める
- テスト: 実装の後に書く（test-after）。行カバレッジ 80% 以上・分岐カバレッジ 70% 以上（全Intent共通）。DB テストは本番と同じ種類の DB をコンテナで起動。E2E は「ログイン → 管理画面 → ログアウト」の1〜2本を入れる。性質ベースのテストを純粋な関数に一部適用。テスト名は英語
- 配備: 当面はローカルのコンテナのみ。CI は GitHub Actions。版はコミットのハッシュ＋リリース時のタグ
- コード規約: `backend/` と `frontend/` に分け、成果物はフロントエンドの `dist` を同梱した実行可能 WAR（開発時は dev server の proxy、本番は同じサーバーの API を呼び CORS 不要）。Gradle（Kotlin DSL）。ルートパッケージ `cherry.mastersmith`、機能別のパッケージ構成。palantir-java-format。コメントは日本語。開発規約一式（Q23）を採用
- セキュリティ検査: 最初の Bolt で Gitleaks＋`.gitignore` 補強、SpotBugs＋FindSecBugs、依存関係の検査（Dependabot 等）。配備先決定後に SBOM。統合を止める基準は High 以上。CodeQL・ZAP・イメージ検査は採用しない。pre-commit フックを入れる
- 必ず守る（ALWAYS）: 統合前の全検査／認証・認可・監査の失敗系テスト／不具合の再現テスト／サブモジュール更新は専用コミット／lockfile で版を固定／秘密情報の検出をコミット前と CI の両方で
- 絶対にしない（NEVER）: 秘密情報の直書き／秘密情報のログ等への出力／秘密情報ファイルのコミット／サブモジュールの直接変更

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
