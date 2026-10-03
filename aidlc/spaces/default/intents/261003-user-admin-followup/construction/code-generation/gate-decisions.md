# Code Generation の承認の場の決定（261003-user-admin-followup）

2026-10-04、依頼者が Approve を選んだ。承認の場で示した扱いを、次のとおり記録する（承認済みの成果物は書き換えない）。

## 1. 承認の場で示した扱い

- **R-01（G2、言語の選択肢で Enter を押して送信したときのフォーカス）**: 送信中はフォーカスが body に落ちる（観察 10 回中 10 回）。送信の後は行の「操作」へ戻る（21 回とも）。この Intent では直さず、次の Intent へ持ち越す。
- **G1 の範囲**: 表示が閉じ終わってから読み直す口（`frontend/src/shared/modal/afterModalClosed.ts`）は、招待の画面だけに当てる。DSL の管理の画面と利用者の管理の画面は、同じ競争が無いと判断して変えない。
- **不安定かもしれないテスト**: `frontend/src/features/preferences/PasswordChangePage.test.tsx` の「drops the answer when the screen is left while sending」が、verify の1回で落ちた。単独で 10 回、全体で 2 回流し直しても再現しなかった。team.md の決まりどおり、不安定と確かめられていない扱いとする。CI でも落ちたときは同じ決まりで扱い、2回目なら原因を直すまで進まない。

## 2. レビューの指摘（Minor、記録として残す）

- R-02〜R-05 は、そのまま受け入れる。
- R-06（FR4.1 は場面 (B) を直す要件だが、実装は新しい場面 `userAdminPoolLimit` を足した）は、要件の文言との差としてここに記録する。既存の `userAdminOps`・`userAdminPool` は、前の Intent の記録と比べられるように残した（計画 Step 17）。
- R-07（FR4.2・FR9.1 は Deferred）は、Build and Test と配備の段で扱う。

## 3. コミット

C2〜C4 の分け方を、計画の C2〜C7 から変えた（依頼者の承認、2026-10-04）。同じファイル（`EditProfileDialog.tsx`・E2E 120）に複数の直しが入ったため、画面の直しを1つにまとめ、G1 を別にした。

| コミット | 中身 |
|---|---|
| `a272fd3` | C1: make-you-chic-ui の固定先 3d9521a → e82b651 |
| `a7fdc2c` | 利用者の管理の画面の直し（K1・K2・K3）と E2E |
| `685f049` | 招待の画面で、閉じ終わってから読み直す（G1） |
| `fcf5dee` | 一意の制約の違反の文をログに出さない（S1） |
| `efbd109` | T2・T3 のテスト |
| `d288e0e` | 負荷の場面と手順（T1・T4） |

## Sources

- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/code-summary.md`（5節 依頼者に確かめたいこと）
- `aidlc/spaces/default/intents/261003-user-admin-followup/.aidlc-reviews/code-generation/stage/20cd6d3137116bec/1.review.md`
