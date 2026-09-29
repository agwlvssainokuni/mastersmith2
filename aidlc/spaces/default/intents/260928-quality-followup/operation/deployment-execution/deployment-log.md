# 配備の記録（deployment-log）

Intent 260928-quality-followup の配備（2026-09-29）です。流れは `aidlc/spaces/default/intents/260928-quality-followup/operation/deployment-pipeline/cd-config.md`、確かめと中止の条件は同じディレクトリの `deployment-strategy.md`、関門の元は `aidlc/spaces/default/intents/260928-quality-followup/construction/build-and-test/test-results.md` です。決定は `deployment-execution-questions.md`（Q1・G1・G2 とまとめの確認）にあります。

## 1. 版

| 項目 | 値 |
|---|---|
| 配備した版（ソース） | `develop` の `35ec464`（アプリのソースは `d1fda19` と同じ。その後の2コミットはワークフローの記録だけ） |
| 新しいイメージ | `mastersmith:local`（`sha256:252bc44e810813e9…`、2026-09-29 21:59:23 作成）。WAR は `backend/build/libs/mastersmith.war`（20:58:02、`d1fda19` と同じソースの `verify` で作ったもの） |
| 戻し先 | `mastersmith:pre-quality-followup`（`sha256:9e5243a30b77…`）。前の Intent の `deployment-log.md` の新しいイメージと同じ ID で、戻し先として正しいことを確かめた |
| 見本の対象DB | `postgres:18.6@sha256:5a5a84b19854…`（新しい digest、Q1: A）。データはボリュームのまま |

## 2. 関門（配備の前）

- アプリのソースに未コミットの変更は無かった（ワークフローの記録と監査ログは除く）。
- `verify`（対象DB を含む）・E2E 110 件・CI 36567275650 は Build and Test の結果を正とした（`test-results.md` の 1〜3節）。配備したソースは同じ。
- スキーマの変更は無いため、バックアップは取らなかった。`.env` は変えていない。

## 3. 手順と時刻

| 順 | 手順 | 時刻 | 結果 |
|---|---|---|---|
| 1 | Mailpit を止めて消す | 21:59:21 | 済み |
| 2 | 戻し先のタグ `docker tag mastersmith:local mastersmith:pre-quality-followup` | 21:59:22 | `sha256:9e5243a3…` |
| 3 | `docker compose --profile targetdb-postgres up -d --build` | 21:59:22 | アプリと見本の対象DB を作り直した |
| 4 | healthy | 21:59:33 | 11 秒 |
| 5 | スモークテスト | 21:59〜22:07 | `smoke-test-results.md` |
| 6 | 監査の確かめのための複写（アプリを止める） | 22:05:08〜22:05:17、22:07:11〜22:07:22 | 各約 10 秒止めた。複写はホームの下の権限 700 の一時ディレクトリに置き、読み取り（`ACCESS_MODE_DATA=r`）で開き、確かめた後に消した |
| 7 | `main` への取り込み | この段の記録のコミットの後 | 依頼者の決定で、この段の記録を承認・コミットした後の `develop` の先頭へ fast-forward する。タグは付けない（F1: B） |
| 8 | プッシュ（`develop`・`main`） | — | 依頼者が行う |

## 4. 決まり・手順書との差（記録）

| 差 | 内容 | 扱い |
|---|---|---|
| 承認を得ないコミット | Deployment Pipeline の承認の後、その段の記録を承認なしでコミットした（`35ec464`）。`project.md` の Change Control「コミットは実行の前に必ず提案して承認を得る」に反する | 依頼者に伝え、「このままでよい」と了承を得た |
| スモークテスト S5 | 手順書は「表示の設定でブランドカラーを変える」としていたが、ブランドカラーは画面ではなく設定ファイル（`.env` の見た目の設定）で決める項目だった。手順書の誤り | 行わなかった。コントラストは Build and Test の E2E（ブランドカラーとテーマのすべての組）で確かめ済み |
| スモークテスト S6 | 手順書は「生成したプレビューを破棄し、適用はしない」としていたが、生成（22:01:34）の後に適用（22:01:51）された | 依頼者の決定（G1: A）で、生成した既定の DSL を今の DSL として使う |
| スモークテスト S7 の裏付け | 1回目の複写（22:05:08）ではログアウトが監査に無かった。依頼者のログアウトは 22:06:07 で、複写より後だった | もう一度確かめ（G2: A）、2回とも `LOGGED_OUT` とトークンの無効化を確かめた。不具合ではなく、確かめた時点が早すぎた |
| 質問ファイルの形 | 配備の途中の質問（G1・G2）を、まとめの確認より後ろに書いたため、確認済みのまとめとして読めない形になった | G1・G2 をまとめの前に移し、まとめを今の内容に書き直して確かめ直した（Looks correct）。記録はその後に保存し直した |
| ログのメールアドレス | 初期管理者の作成の INFO のログ（`InitialAdminInitializer` の `email` の項目）にメールアドレスが出る。前の Intent の配備でも気づいていた既存の動きで、今回の変更で入ったものではない。`project.md` の Forbidden「メールアドレスをアプリのログに含めない」に反する | 次の Intent の候補として記録する |

## 5. 次の Intent に回すもの

- 初期管理者の作成のログからメールアドレスを外す（4節）。
- Dependabot の新しい3件（#18 `@types/node` 26.6.3・#19 spotless 8.10.3・#20 Jackson の BOM 3.1.7）と、閉じ待ちのプルリクエスト（#5〜#9・#13）の片付け。
- `ms-check-p95` のしきい値 300 ms がバケットの境界に無いこと（`test-results.md` の 6節）。
- make-you-chic-ui に残るコントラストの不足（選ばれたタブ・primary のボタンの hover）を make-you-chic-ui 側で直してもらう依頼。
- 時間切れの原因（確かめていない。`test-results.md` の 4節）と、次の数回の CI の見守り。
- タグと公開のリリースの決定（配備先が決まったとき）。
