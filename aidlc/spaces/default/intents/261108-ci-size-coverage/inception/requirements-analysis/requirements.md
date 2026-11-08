# 要件定義（Intent 261108-ci-size-coverage）

## 1. 目的

CI（GitHub Actions）の実行のたびに、プログラムの規模・テストの件数・カバレッジを、実行の要約（Summary）の画面で読めるようにする。手元で数え直さなくても、Bolt ごとの規模の増え方と、カバレッジの値（パッケージごとの下限との近さを含む）を CI の画面で追えるようにすることが狙いである。

- 種類: 既存の CI への報告の追加（検査の中身は変えない）
- 範囲: `.github/workflows/ci.yml` の `verify` の job、新しい台本 `scripts/program-size.mjs`（と、要約を組み立てる台本）、`docs/verification.md`
- 深さ: Minimal（依頼と会話で中身がほぼ決まっている）

## 2. 機能の要件

### FR1: プログラムの規模の台本

- **FR1.1** 依頼者の手元の Python の台本を Node に移し、`scripts/program-size.mjs` に置く。数え方・外すもの・分類の決まり・報告の表は移す元のとおりとする（下の FR1.2〜FR1.4）。
- **FR1.2** 数えるのは git が管理するファイルだけとし、cloc で code（空行とコメントを除いた行）・comment・blank を数える。外すもの: `aidlc/`・`vendor/`・AI の道具の設定（`.claude/`・`.codex/`・`.kiro/`・`.cursor/`・`.agents/`・`.aidlc/`・`.github/skills/`）・`.idea/`・lockfile（`package-lock.json`・`*.lockfile`）。
- **FR1.3** 分類（上から順に当て、最初に当たったもので決める）: 文書（`.md`）→ サーバーの製品（Java。機能と層）→ サーバーの資源（移行・メールのテンプレート・設定など）→ サーバーのテスト（結合テスト・テストの手伝い・単体テスト・テストの資源。機能ごと）→ 画面（製品と単体テストを、ファイル名とパスの決まりで分ける。領域ごと）→ E2E（流れ・補助・補助の単体テスト）→ 画面の静的な素材 → 負荷の試験 → ビルド・検査・運用（E2E に関わる設定を注記）。
- **FR1.4** 報告は Markdown とし、移す元と同じ節を出す: 数えた対象、要点（製品とテストの行数と比、サーバー・画面・E2E の内訳、ライセンスヘッダーの見込みの行数）、大分類・中分類・中分類と言語・サーバーの製品の層と機能・サーバーのテストの種類・サーバーの機能ごとの製品とテスト・画面の製品とテストの領域の表。
- **FR1.5** 報告に cloc の版を載せる。
- **FR1.6** 報告と台本に、依頼者の手元の置き場のパスを書かない（`project.md` の Mandated）。台本の先頭に Apache License 2.0 のヘッダー（2026、agwlvssainokuni）を付ける。
- **FR1.7** 「数えた対象」の欄は CI と手元の両方で正しく出す: ブランチは CI では `GITHUB_REF_NAME`、手元では今のブランチ。`main` の先頭は `origin/main`（無ければ「—」）。移す元の「未コミットの変更の件数」は外す。
- **FR1.8** 比べる元のコミットを渡すと（例: `--base <commit>`）、そのコミットの中身を `git worktree` で取り出して同じ決まりで数え、大分類と中分類の表に差の列（コードの行・ファイルの数）を足す。取り出した作業ツリーは終わったら消す。
- **FR1.9** 手元では、リポジトリのルートで流すと標準出力に Markdown を出す（移す元と同じ使い方）。

### FR2: カバレッジの要約

- **FR2.1** バックエンドの JaCoCo の XML（`jacocoTestReport`）から、全体の行・分岐のカバレッジを読んで載せる。XML は外部の DTD を読まない形で読む（`jacocoQuickReport` と同じ扱い）。
- **FR2.2** 画面の `coverage-summary.json` から、全体の行・分岐・関数・文のカバレッジを読んで載せる。
- **FR2.3** バックエンドのパッケージごとの行・分岐の値の表を載せる。各パッケージに下限（行 80%・分岐 70%）との差を並べ、`packagesJudgedByTotal` のパッケージには印を付ける。
- **FR2.4** 画面の領域ごと（`features/<機能>`・`app/<名前>`・`shared/<名前>`）の表を載せる。値は `coverage-summary.json` のファイルごとの値を足し合わせて求める。
- **FR2.5** パッケージごと・領域ごとの表は `<details>` で畳む。

### FR3: テストの件数

- **FR3.1** バックエンドの単体テストと結合テストの件数（通った・失敗・飛ばした）を、JUnit の XML の結果から読んで載せる。画面（Vitest）の件数は載せない。

### FR4: CI の手順

- **FR4.1** `verify` の job に、規模・テストの件数・カバレッジを `$GITHUB_STEP_SUMMARY` に書く手順を足す。並びは「規模 → テストの件数 → カバレッジ」とする。
- **FR4.2** 要約の手順は `if: always()` を付け、`./gradlew verify` が落ちた回にも流す。
- **FR4.3** CI の cloc は `apt-get install cloc` で入れる。
- **FR4.4** 比べる元は `github.event.before` とする。使えないとき（0 が並ぶ値・取り出せない・手動の実行（`workflow_dispatch`）・タグのプッシュ）は、差の列を「—」にし、要約に理由を1行書く。
- **FR4.5** 要約の手順は CI を失敗にしない。作れなかった欄（報告のファイルが無い・cloc が入らないなど）には理由を書き（例: 「報告なし（./gradlew verify が途中で落ちた）」）、警告（`::warning::`）を出す。
- **FR4.6** JaCoCo と Vitest の HTML の報告を、1つの成果物として 14 日保存する（`if: always()`。報告が無いときは失敗にしない）。

### FR5: 手順書

- **FR5.1** `docs/verification.md` に、手元での規模の数え方（前提の cloc、使い方、比べる元の渡し方）と、CI の要約の読み方（各欄の出どころ、「—」と「報告なし」の意味、HTML の報告の成果物の取り方）を書く。

## 3. 非機能の要件

- **NFR1（CI の時間）** 要約と報告の保存の手順を足したことによる `verify` の job の延びを、CI の実行で測って記録する。目安は 2 分以内とし、超えたら依頼者に諮る。CI の上限（`timeout-minutes: 60`）の7割の約 42 分の線（`team.md` の Testing Posture）も確かめる。
- **NFR2（検査を変えない）** `./gradlew verify` の段・カバレッジの下限・CI の合否の決まりを変えない。要約の手順の成否は CI の結果に響かない（FR4.5）。
- **NFR3（公開してよい中身だけ）** リポジトリは公開のため、要約と成果物には、ソースのパス・行数・テストの件数・カバレッジの値だけを載せ、秘密情報・個人に関する値を載せない。`.env` などの git の対象外のファイルは数えない（FR1.2 で git が管理するファイルだけを数える）。
- **NFR4（再現できる値）** 同じコミット・同じ cloc の版なら、手元と CI で規模の表の値が一致する。cloc の版が違うと値がずれうるため、報告に版を載せる（FR1.5）。
- **NFR5（移しの正しさ）** Node に移した台本が、手元で移す元の Python の台本と同じ表の値を出すことを、同じコミットで両方を流して確かめ、結果を記録する。

## 4. 制約

- 新しい依存の決まり（`team.md` の Code Style）: cloc（GPL-2.0）は CI で道具として動かすだけで WAR に含めない、という採用の理由を記録に残す。Node の台本は新しい npm の依存を足さない（Node の組み込みと git・cloc だけで動かす）。
- サードパーティの GitHub Actions を足すときは、既存の手順と同じくコミットのハッシュで固定する（`actions/upload-artifact` は既存の固定を使う）。
- CI のきっかけ（`develop` へのプッシュ・`v*` のタグ・手動の実行）と、`release` の job は変えない。

## 5. 前提

- `[assumption]` `ubuntu-latest` の実行環境で `apt-get install cloc` が使える（Reverse Engineering の K-124 では確かめていない。Build and Test の CI の実行で確かめる）。
- `[assumption]` JaCoCo の XML は `backend/build/reports/jacoco/test/jacocoTestReport.xml`、画面の要約は `frontend/coverage/coverage-summary.json` にある（Reverse Engineering の K-120 の見込み。コード生成で確かめる）。
- `[assumption]` 結合テストの JUnit の XML は `backend/build/test-results/integrationTest/`、単体テストは `backend/build/test-results/test/` にある（`docs/verification.md` と前の Intent の確かめの手順から）。

## 6. 決まりの読み方（依頼者の決定）

- `[Q4]` `[F1]` ルートの `scripts/` の台本（`scripts/program-size.mjs` を含む）には、単体テストとライセンスヘッダーの検査を足さない。依頼者の読み方: `team.md` の「テストのない機能は完成とみなさない」と express の要件ごとの単体テストは本体の機能に当てる決まりで、CI の報告の台本は本体の機能ではないため当たらない。確かめは NFR5（Python 版との一致）と Build and Test の CI の1回の実行の要約で行う。

## 7. 範囲の外

- ルートの `scripts/` を検査（ライセンスヘッダー・書式・リンタ・テスト）の対象に足すこと（`scripts/verify-graph-check.mjs` を含め、前の Intent からの持ち越しのまま）。
- 画面（Vitest）のテストの件数。
- cloc を版と SHA-256 で固定して入れること。
- カバレッジの下限・`packagesJudgedByTotal` の一覧の変更。
- 規模やカバレッジの推移をどこかに貯めること（前の回との差は git の履歴から毎回数える）。

## 8. 未解決の点

None.

## Sources

- Initial description: 依頼文（`aidlc engine workspace project-description` の description）`[desc]`
- Workflow-selected scope: express `[scope]`
- 質問の答え Q1〜Q6・F1（`inception/requirements-analysis/requirements-analysis-questions.md`）`[Q1]`〜`[Q6]` `[F1]`
- Reverse Engineering の知識ベース（`aidlc/spaces/default/codekb/mastersmith2/`、K-119〜K-126）
- `team.md`（Code Style の新しい依存のライセンス、Testing Posture の CI の時間の線）、`project.md`（Mandated の参考資料の置き場）
- 依頼者の手元の Python の台本（会話の中で読んだ。分類の決まりと報告の形）

## Assumptions & Open Questions

- 前提は 5 節のとおり。未解決の点は無い。
