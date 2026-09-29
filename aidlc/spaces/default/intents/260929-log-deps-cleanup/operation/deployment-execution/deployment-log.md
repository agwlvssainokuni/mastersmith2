# 配備の記録（deployment-log）

Intent 260929-log-deps-cleanup の配備の記録です。手順は `aidlc/spaces/default/intents/260929-log-deps-cleanup/operation/deployment-pipeline/cd-config.md`、確かめ方は `deployment-strategy.md` のとおり。関門の結果は `aidlc/spaces/default/intents/260929-log-deps-cleanup/construction/build-and-test/test-results.md`（手元の verify と CI 36615809940 が成功）を正とした。

## 1. 版

| 項目 | 値 |
|---|---|
| 配備したソース | `develop` の `7fd674c`（アプリのソースは `8489241` と同じ。差はワークフローの記録のコミットだけ） |
| 配備したイメージ | `mastersmith:local`（`sha256:c77c1bb2…`、2026-09-29 20:41:43 UTC 作成） |
| 前のイメージ（戻し先） | `mastersmith:pre-log-deps-cleanup`（`sha256:252bc44e…`、前の Intent 260928-quality-followup で配備した版） |
| `main` | `c1ed553` から `7fd674c` へ fast-forward（タグは付けない。Q1: A） |

## 2. 流れ（UTC、2026-09-29）

| 時刻 | 段 | 結果 |
|---|---|---|
| — | 関門 | アプリのソースに未コミットの変更なし。`develop` は `origin/develop` より記録のコミット2つだけ先。`main` は `develop` の祖先 |
| 20:41:41 | 戻し先のタグ | `docker tag mastersmith:local mastersmith:pre-log-deps-cleanup` |
| 20:41:41〜20:41:57 | 入れ替え | `docker compose --profile targetdb-postgres up -d --build app`。healthy まで約16秒 |
| 20:41〜20:42 | S1〜S3 | 通過（`smoke-test-results.md`） |
| 20:42〜20:43 | S4・S5 | 依頼者のログインとログアウト。監査で裏付け（通過） |
| 20:43 | 監査の確かめと配備の後のバックアップ | アプリを数秒止めて内部DB を複写し、`~/.mastersmith-backup/mastersmith-data-202609300543-after-log-deps.tgz` に置いた。展開した一時のファイルは消した。アプリは起動し直して healthy |
| — | `main` への取り込み | 依頼者の承認を得て fast-forward |

## 3. 行わなかったこと

- 内部DB のバックアップ（配備の前）と戻しの練習: スキーマと `.env` の変更が無いため（`cd-config.md`）。
- 配備の前の k6: 性能に関わる変更が無いため。
- 見本の対象DB と手元の監視（lgtm）の作り直し: 変更が無いため。

## 4. 残っていること

- `origin` へ `develop` と `main` をプッシュする（依頼者）。
- Dependabot の #22 を閉じる（依頼者）。

## Sources

- `aidlc/spaces/default/intents/260929-log-deps-cleanup/operation/deployment-pipeline/cd-config.md`
- `aidlc/spaces/default/intents/260929-log-deps-cleanup/operation/deployment-pipeline/deployment-strategy.md`
- `aidlc/spaces/default/intents/260929-log-deps-cleanup/construction/build-and-test/test-results.md`
- `deployment-execution-questions.md`（Q1 とまとめの確認）
