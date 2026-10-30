# 技術の選び方 — U4 u4-perf-evidence-redactor

出典: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u4-perf-evidence-redactor/functional-design/functional-spec.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u4-perf-evidence-redactor/functional-design/rules.md`・`aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/requirements-analysis/requirements.md`（NFR6・NFR7・NFR9）。決定は `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/decisions.md` の ADR-004・ADR-006 のとおりで、この文書はその版と制約を要件にしたもの。

## 1. 選んだもの

| 対象 | 選んだもの | 理由 | 新しい依存か |
|---|---|---|---|
| 実行の環境 | Node 24（`frontend/package.json` の `engines` が `>=24 <25`、CI の `node-version: "24"`） | フロントエンドのビルドと verify が既に使う版で、perf の手順を流す PC にもある。版を別に持たない | いいえ |
| 道具の形 | `perf/` に置く ES モジュールの 1 ファイル（拡張子 `.mjs`。名前はコード生成で決める）。読み込むのは Node の組み込み（`node:fs`・`node:path` など）だけ | 依存の取り込み・lockfile・`ignore-scripts` の守りの外に出ない。既存の `perf/*.mjs`（`make-biz-dsl.mjs` など）と同じ形 | いいえ |
| JSON の読み書き | Node の組み込みの `JSON.parse`・`JSON.stringify` | 依存を足さない。キーの並びは入力の順を保つ（BR1.6） | いいえ |
| テスト | `frontend/` の Vitest（`^5.0.5`）と fast-check（`^4.10.2`） | どちらも既存の devDependencies で、verify の `verifyUnitTest` と CI に既に乗る（ADR-004） | いいえ |
| 秘密の検出 | 既存の Gitleaks（`gitleaks dir` を置いた後の手順に使う）と、この道具の evidence の検査（verify の `verifySecurity`） | 既存の道具の使い方を足すだけ | いいえ |

## 2. 要件

| ID | 要件 | 合否の確かめ方 | 決まり |
|---|---|---|---|
| NFR9.61 | 道具は Node の組み込みだけを読み込み、npm のパッケージを読み込まない。`frontend/package.json`・`frontend/package-lock.json`・`perf/` に依存を足さない | 道具のファイルの `import` が `node:` だけであること、lockfile の差が無いこと（コード生成のレビュー） | ADR-004・要件 NFR9 |
| NFR9.62 | テストは既存の Vitest と fast-check だけで書き、新しい devDependencies を足さない。足す必要が出たときは、採用の前にライセンスを確かめて依頼者に諮る（Apache License 2.0 と異なるときは理由を ADR に残す） | `frontend/package.json` の差が無いこと | 要件 NFR9・team.md Code Style |
| NFR9.63 | 依存の取り込みは今の lockfile どおり（`npm ci`）で、`frontend/.npmrc` の `ignore-scripts=true` を変えない | `.npmrc` と lockfile の差が無いこと | 要件 NFR9・project.md Mandated |
| NFR9.64 | 道具は Node 24 で動く構文と組み込みの口だけを使い、`engines` の範囲（`>=24 <25`）を変えない | verify（CI の Node 24）でテストが通ること | ADR-004 |
| NFR9.65 | 道具のファイルの先頭に Apache License 2.0 のヘッダー（`/* ... */` の形、年 `2026`、著作権者 `agwlvssainokuni`）を置き、ライセンスヘッダーの検査の対象に入れる | ライセンスヘッダーの検査が道具のファイルを見て通ること（入れ方は ADR-006 の切り替え先を含めコード生成で決める） | project.md Mandated・team.md Code Style |
| NFR7.61 | 契約 C7 の既存の呼び方（`node perf/<伏せ字の道具>.mjs <入力の summary json> <evidence/ の中の出力>`、引数 2 つ）を変えない。テキストの置換の口（`--text`）と evidence の検査の口は足す変更として加え、「非 0」の失敗を 1〜5 に分けるのは C7 の範囲の中の細分とする | 引数 2 つの呼び方の例のテストが通ること | 契約 C7・functional-spec.md 10節 |
| NFR6.61 | 終わりのコード（0 成功・1 呼び出しの誤り・2 入力の誤り・3 キーの衝突・4 残存の検出・5 書き出しの失敗）は一度決めたら意味を変えず、1 つのコードに 1 つの意味を固定する。evidence の検査は検出で非 0 を返す。表示の文言は開発者向けの道具のため日本語だけとし、画面の多言語の決まりは当てない | 各コードの例のテスト（security-requirements.md の NFR8.65・NFR8.67） | BR3.3・要件 NFR6 |

## 3. 選ばなかったもの

| 案 | 選ばなかった理由 |
|---|---|
| k6 の台本の `handleSummary` で伏せる | `--summary-export` を上書きできるか未検証で、既存の結果や標準出力・ログに効かない（ADR-004） |
| シェル（`jq`・`sed`）で置換する | 性質ベースのテストを verify に載せにくく、キー名・入れ子の置換と残存の検査を分けて確かめにくい |
| JSON の流し読みの部品（npm）を足して大きな入力に備える | 新しい依存を足さない（NFR9.61）。summary json は数 MB で、64 MiB の上限（security-requirements.md の NFR8.66）で足りる |

## 4. 対象外とした NFR

| 上流 | 理由 |
|---|---|
| NFR1〜NFR4 | 業務データの API・対象DB・画面の要件で、この単位は持たない（security-requirements.md の 3節） |

## Sources

- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/domain-design/decisions.md`（ADR-004・ADR-006）
- `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C7）
- `frontend/package.json`（`engines`・Vitest・fast-check の版）と `.github/workflows/ci.yml`（`node-version`）を読み取りで確かめた
- `aidlc/spaces/default/codekb/mastersmith2/technology-stack.md`
- この段の質問の「決まっていること」: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u4-perf-evidence-redactor/nfr-requirements/nfr-requirements-questions.md`
