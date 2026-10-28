# 要件の網羅（cross-unit traceability）

この Intent は単位の分割が無い（zero-unit）ため、要件 `requirements.md` の FR・NFR と Code Generation の `traceability.json` を直接突き合わせる。Code Generation で `Deferred` とした項目は、この段で確かめた結果を「この段の判定」に書く（承認済みの `traceability.json` は書き換えない）。

| ID | Code Generation | 持ち主・成果物 | この段の判定 | 根拠 |
|---|---|---|---|---|
| FR1.1 | OK | `perf/k6/scenarios.js` の `BIZ_P95_LIMIT_MS` | Met | `thresholds-test.sh` 全件 ok。測り直しで新しい線の判定（k6 の終了コード 0） |
| FR1.2 | OK | `perf/dsl-mixed-memory.sh` | Met | `dsl-mixed-memory-test.sh` 全件 ok（0.9500 の境界を含む）。dslMixed 3 回の判定 |
| FR1.3 | OK | `perf/README.md` | Met | 対象DB の合格の条件を README に記載。測り直しで anon 最大 71.54%・69.57% を値で並べて判定 |
| FR1.4 | OK | `perf/k6/thresholds-test.sh` | Met | 3 種類の DB に同じ線を場面の名前で当てる。テスト通過 |
| FR1.5 | OK | `perf/README.md`・`test-results.md` | Met | 新旧の線を並べた確認表（test-results.md 1 節） |
| FR2.1 | Deferred | Build and Test | Met | 測り直した場面は MySQL・MariaDB の 3 場面、dslMixed、対象DB の anon のみ。PostgreSQL ほかは前回の結果を使うと明記 |
| FR2.2 | Deferred | Build and Test | Met | 全行を新旧の線で Met / Not Met として並べた |
| FR2.3 | Deferred | Build and Test | N/A（該当なし） | 新しい線で Not Met が 0 件のため持ち越しの諮りは不要 |
| FR2.4 | Deferred | Build and Test | Met | 配備アプリを止めて実行、`caffeinate -dims`、窓に眠り 0 件 |
| FR2.5 | OK | `perf/README.md` | Met | 各 2 回・悪い側で判定、dslMixed は差 1.91 ポイントで 3 回目を足した。読み取りだけで書き込みは流していない |
| FR2.6 | OK | `perf/k6/thresholds-test.sh` | Met | `k6 inspect` で場面ごとの線を照合 |
| FR3.1 | Deferred | Build and Test | Met | 開いている PR は #80 の 1 件のみ（`gh pr list`） |
| FR3.2 | Deferred | Build and Test | N/A | 取り込む更新は無い（#80 は見送り） |
| FR3.3 | OK | `.github/dependabot.yml` | 一部（PR を閉じる操作が未実施） | `ignore` に理由と外す時期を記載済み。PR #80 を閉じるのは外部操作のため依頼者に確認してから行う（Deployment Pipeline 以降） |
| FR3.4 | OK | `.github/dependabot.yml` | Met | `-386` は linux/386 だけであることを `docker buildx imagetools inspect` で確認済み |
| FR4.1 | Deferred | Deployment Pipeline・Execution | 未完（次の段が持つ） | Q5: B に従い通常どおり配備する。WAR は現行のイメージと SHA-256 が同じ（`ff50498b…`）で、作り直しても中身は変わらない見込み |
| FR4.2 | Deferred | Deployment Execution | 未完（次の段が持つ） | `main` への fast-forward は依頼者の承認後。`git push` は依頼者が行う |
| NFR1 | OK | `perf/dsl-mixed-memory-test.sh` | Met | 緩和は FR1 に限る。他の p95 の線（1000）・anon 85%・カバレッジの下限は変えていない |
| NFR2 | Deferred | Build and Test | Met | 測っていない範囲（PostgreSQL、書き込みを含む anon）を明記。Not Met を Met と書いていない |
| NFR3 | Deferred | Build and Test | Met | verify の OSV-Scanner を含む検査が通過。記録にパスワード・メール・利用者名 0 件 |
| NFR4 | Deferred | Build and Test | Met | verify 成功（cleanTest 付き）、E2E 256 件通過、カバレッジ 行 97.7%/分岐 91.4%（バックエンド）。CI は develop の先頭で 1 回目成功 |
| NFR5 | Deferred | Build and Test | Met | 画面・API・スキーマは変えていない（変更は `perf/`・`.github/` と aidlc の記録だけ）。戻しはイメージでの再配備 |

## 未完の項目

- FR3.3 の PR #80 を閉じる操作、FR4.1・FR4.2 は次の段（Deployment Pipeline・Deployment Execution）が持つ。
- 新しい線で Not Met の項目は 0 件。
