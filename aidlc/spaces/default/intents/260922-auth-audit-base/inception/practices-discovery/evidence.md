# Evidence — Practices Discovery（主担当の初稿・DRAFT）

> 主担当（aidlc-pipeline-deploy-agent）が初稿の作成にあたって調べたこと、そこから推測したこと、
> 面談で決める必要がある論点をまとめる。支援エージェント3者の独立レビューと面談の結果は、
> 最終統合時に追記する。

## 前提

- プロジェクト種別: 新規（Greenfield）。アプリケーションのコードはまだない
  （`aidlc/spaces/default/intents/260922-auth-audit-base/aidlc-state.md`）。
- 既定値の出どころ: `aidlc/spaces/default/memory/org.md` の5節（Way of Working / Walking Skeleton /
  Testing Posture / Deployment / Code Style）を「推奨の既定値」として扱った。チームで確定した事実ではない。
- `aidlc/spaces/default/memory/team.md`: 全節が空（確定済みのチーム慣行はまだない）。再実行ではない。
- `aidlc/spaces/default/memory/project.md`: 既存のルール（Change Control のコミットの進め方、
  Code Style の相対パス表記、Mandated の `reference/` の扱いとライセンスヘッダー、Tech Stack の
  make-you-chic-ui、Decided の3項目）を確認し、初稿がこれらと矛盾しないようにした。
- 会話言語: 日本語（依頼文の `Conversation language:` 行による）。

## 調べたもの

| # | 対象 | 分かったこと |
|---|---|---|
| E1 | `git branch -avv` | 作業ブランチは `develop`（`origin/develop` を追跡）。ローカルに `main` ブランチは存在しない。リモートには `origin/main` があり、`origin/HEAD` は `origin/main` を指す |
| E2 | `git log`（全17コミット） | `origin/main` は初回コミット（`2673bd5`）のみ。以降の16コミットはすべて `develop` に直接積まれている。機能ブランチやマージコミットはない |
| E3 | コミットの粒度とメッセージ | 数分〜十数分おきに、回答確定・成果物作成・承認などの区切りごとにコミットしている。メッセージはすべて日本語（project.md の Change Control と一致） |
| E4 | `git remote -v` | リモートは GitHub 上のリポジトリ（`origin`） |
| E5 | `.gitignore` | AI-DLC の既定の除外設定に加え、Node 系（`node_modules`、`dist` 等）、エディタ系（`.idea`、`.vscode/*` 等）、`reference/` を除外している |
| E6 | `.gitmodules`、`git submodule status` | デザインシステム make-you-chic-ui を `vendor/make-you-chic-ui` にサブモジュールとして取り込み、同リポジトリの `main` の特定コミットに固定している |
| E7 | CI・ビルド・テスト | CI の設定（`.github/` 等）、ビルドファイル、テストはいずれも存在しない |
| E8 | IDE の設定（Git管理外の `.idea/`） | IntelliJ IDEA の Java モジュールとして開かれており、JDK は Temurin 25（言語レベル JDK_25）に設定されている。バックエンドを Java で書く想定と整合する |
| E9 | `.claude/scopes/aidlc-auth-audit-foundation.md` | `skeleton: on`、`change_control: strict` を宣言。Operation フェーズの主要ステージが実行予定 |
| E10 | `aidlc-state.md` | Test Strategy は `Standard`。Change Control は `relaxed (set by you)` と記録されている |
| E11 | Ideation の成果物（intent-statement.md、scope-document.md、initiative-brief.md） | バックエンド（JWT 認証、構造化ログ、分散トレース、OTEL エクスポート）と make-you-chic-ui を使う最小限の画面が対象。体制は依頼者1名＋AI、期限なし。「テスト・CI が一切ない新規リポジトリのため、品質を確かめる仕組みを一から作る必要がある」ことがリスクとして認識済みで、対策は Practices Discovery 等で具体化するとされている |
| E12 | make-you-chic-ui の設定（読み取りのみ） | Prettier（セミコロンなし、シングルクォート、末尾カンマ all、1行100文字、インデント2）、oxlint（correctness を error、react / jsx-a11y / typescript 等）＋ ESLint（react-hooks の推奨ルールのみ）、Stylelint（stylelint-config-standard）、Vitest（jsdom）＋ Testing Library ＋ vitest-axe、テストは対象と同じ場所の `*.test.tsx`（25ファイル）。カバレッジの設定はない |
| E13 | make-you-chic-ui のコミット履歴（読み取りのみ） | `main` のみで運用。コミットメッセージは日本語。ライセンスヘッダーのコメント記法を `/**` から `/*` に揃える修正を行っている |

## 推測したこと

- I1（E1, E2, E3）: 実態としては「`develop` に直接コミットし、`main` は未使用」の運用である。
  org.md の既定（`main` へのトランクベース開発）とは統合先ブランチが異なる。
  Construction では Bolt ごとの作業ブランチ（worktree）の作成元と統合先を決める必要があり、
  現状のまま org.md の既定（`main`）を使うと、ローカルに `main` がないため作業ブランチの作成に失敗する。
- I2（E3, E13）: 依頼者は細かい単位で履歴を残すことを好む。squash マージにすると統合先の履歴は
  Bolt 単位に粗くなるため、統合方法（squash かマージコミットか）は好みの確認が必要。
- I3（E9）: スコープが `skeleton: on` のため、最初に端から端まで通る薄い土台を作るのが既定の流れになる。
- I4（E9, E10）: スコープ定義では Change Control が `strict`、状態ファイルでは `relaxed (set by you)` と
  食い違っている。本ステージの対象（5節）ではないが、後続ステージの進め方に影響するため記録しておく。
- I5（E9）: スコープ `auth-audit-foundation` は org.md のスコープ別テスト下限の一覧に含まれないため、
  明示しない限りカバレッジ下限がかからない。認証・監査という重要な基盤であり、下限を決める必要がある。
- I6（E8, E11）: バックエンドは Java（Spring 系）を想定している。ただし具体的な技術選定
  （ビルドツール、DB、ライブラリ）は後続ステージで決めるため、本ステージでは候補にとどめた。
- I7（E12, E13）: フロントエンドのコード規約は、依頼者自身の make-you-chic-ui の設定に揃えるのが
  最も自然と推測した。
- I8（E4, E7）: CI を置くなら GitHub Actions が自然だが、配備先は手がかりがなく、推測できない。

## 面談で決める必要がある論点

### Way of Working
- P1: 日々の統合先を `develop` にするか `main` にするか（`develop` にする場合、`main` の役割とローカルへの作成方法）
- P2: Bolt ブランチを統合するとき squash マージにするか、個々のコミットを残すマージコミットにするか
- P3: プルリクエストを使うか、ローカルで統合してプッシュするか
- P4: プッシュのタイミングと承認の要否

### Walking Skeleton
- P5: 最初に端から端まで通る薄い土台を作るか（スコープは `skeleton: on`）
- P6: 土台に含める範囲（起動・DB接続・ヘルスチェック・構造化ログ・ログイン画面の枠・ビルドとテスト・CI）
- P7: 2本目以降の Bolt を自動で続けるか、Bolt ごとに承認するか（希望のみ。正式な選択は最初の Bolt 完了時）

### Testing Posture
- P8: テストの書き方（実装後にテスト／先にテスト／重要ロジックだけ先にテストする混在）と、その順序
- P9: カバレッジの下限（行カバレッジ 80% を提案）と、CI での実行を統合の条件にするか
- P10: 必ず書くテストの範囲（認証・認可・監査の失敗系）
- P11: DB を使うテストで本番と同じ種類の DB を使うか

### Deployment
- P12: 配備先（ローカルのみ／検証環境＋本番環境／その他）
- P13: 本番配備の承認者（依頼者1名が兼ねるか）
- P14: CI の置き場所（GitHub Actions を候補）と、CI で実行する検査
- P15: 成果物の版の付け方とタグ付け

### Code Style
- P16: フロントエンドを make-you-chic-ui と同じ設定（Prettier / oxlint / ESLint / Stylelint）にするか
- P17: Java のフォーマッタ・静的検査の選択と、インデント幅・1行の文字数
- P18: ライセンスヘッダーのコメント記法を `/* ... */` に揃えるか
- P19: Java のルートパッケージ名とディレクトリ構成
- P20: コードコメント・Javadoc の言語（日本語／英語）

### 守るべき制約（discovered-rules.md）
- P21: 候補 M1〜M3、F1〜F3 のうち、人間が「必ず守る／絶対にしない」と明言するもの

## まだ不確かなこと

- 配備先に関する手がかりがリポジトリにないため、Deployment 節は候補の列挙にとどまる。
- バックエンドの技術選定が未確定のため、Java 側のテストの道具・コード規約は候補にとどまる。
- 支援エージェント3者の独立レビューはまだ行っていない（最終統合時に追記する）。
