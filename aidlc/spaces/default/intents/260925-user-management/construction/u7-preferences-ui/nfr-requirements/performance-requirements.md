# Performance Requirements — U7 プリファレンスとパスワードの変更の画面（u7-preferences-ui）

U7 の性能の要件です。U7 は画面の単位のため、自分の API を持ちません。ここでは、2つの画面（S4 プリファレンス・S5 パスワードの変更）の画面の時間の目標、送信の間の表示と二重送信の防止、初回に読み込む JavaScript の大きさを扱います。答えは `nfr-requirements-questions.md`（Q1: A、Consolidated Summary Confirmation: Looks correct）です。

出典の略号:
- NFR・FR は `aidlc/spaces/default/intents/260925-user-management/inception/requirements-analysis/requirements.md`
- D・W・8節・9節・10節は、この単位の `construction/u7-preferences-ui/functional-design/functional-spec.md`
- 「部品 7節」は、同じフォルダの `frontend-components.md` の 7節
- Q1 と「要点 n」は、この段の `nfr-requirements-questions.md`（Q と「NFR の要点（案）」の番号）
- 「共通の決定」は、U5〜U7 の画面に共通する依頼者の決定（`construction/u5-invitation-ui/nfr-requirements/` の段で出たもの。狭い幅の検査と、画面の時間の目標を置くこと）

枝番はこの単位の中で振ります（ほかの単位と同じ番号でも別の要件です）。この単位の成果物と置き場は次のとおりです。

| 成果物 | 置く要件 |
|---|---|
| この文書 | 性能（NFR6.1〜NFR6.6） |
| `security-requirements.md` | 個人に関する値（NFR2.1）、パスワードの値・応答の値・今のパスワードの誤り・骨組みの変更（NFR9.1〜NFR9.4） |
| `tech-stack-decisions.md` | アクセシビリティ（NFR7.1〜NFR7.5）、多言語（NFR8.1・NFR8.2）、テストと依存（NFR9.5〜NFR9.10） |

## 前提

- 2つの画面が呼ぶ API の目標は、U2 で決まっています（`construction/u2-user-preferences/nfr-requirements/performance-requirements.md`）。どれも同時 10 件のときの値で、Performance Validation の段の k6 で測ります。
  - プリファレンスの取得（GET `/api/me/preferences`）・保存（PUT）: p95 1 秒（U2 の NFR6.1・NFR6.2）
  - パスワードの変更の成功（POST `/api/me/password`、204）: p95 2 秒（U2 の NFR6.3。bcrypt を2回計算するため）
  - 今のパスワードの誤り・入力の誤り（400）: p95 1 秒（U2 の NFR6.4）
- プリファレンスの画面は開くたびに GET を1回だけ送り、読み終わるまでフォームを出しません（D1、W2）。パスワードの変更の画面は開いても API を呼びません（W9）。
- 画面の時間は、手元の PC のブラウザ（Playwright の Chromium）で、`./gradlew :backend:bootWar` で作った WAR を相手に測ります（既存の `frontend/playwright.config.ts`。U4 の NFR6.1 と同じ）。1人の操作で測るため、ふつうは API の目標（同時 10 件の値）より短くなります。

## 1. 目標

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR6.1 | 画面を開く時間: ユーザーメニューで項目を選んでから、プリファレンスの画面のフォーム（4つの値が入った状態）が出るまで 2 秒以内。同じく、パスワードの変更の画面の3つの項目が出るまで 2 秒以内。どちらも画面の遅延読み込みを含み、プリファレンスは GET 1回を含む | Build and Test で、実際のブラウザ（Playwright）で場面ごとに 5 回測って記録する。ログインした後の画面から、ユーザーメニューの項目を選んだ時点を始まりとし、プリファレンスはフォームの保存のボタンが見えるまで、パスワードの変更は「変更する」のボタンが見えるまでを終わりとする。5 回すべてが 2 秒以内であることを目標にする。時間は統合の関門にしない（テストの成否にしない） | Q1 A、共通の決定、U5 の一覧 2 秒 |
| NFR6.2 | 保存の時間: プリファレンスの画面で「保存する」を押してから、保存の結果が画面に当たり（`applyUserPreferences` の後の描画）Toast が出るまで 1.5 秒以内（PUT 1回を含む） | NFR6.1 と同じく Build and Test で 5 回測って記録する。「保存する」を押した時点から、Toast「保存しました」（言語を変えたときはその言語の文言）が見えるまで。関門にしない | Q1 A、共通の決定、U5 の次のページ 1.5 秒、W8・D12 |
| NFR6.3 | パスワードの変更の時間: 「変更する」を押してから、Toast「パスワードを変更しました」が出るまで 2.5 秒以内（POST 1回を含む。API の目標 p95 2 秒に 0.5 秒の余裕を足した値） | NFR6.1 と同じく Build and Test で 5 回測って記録する。測るたびにパスワードが変わるため、測りのテストが自分で作った利用者で、変更の前と後のパスワードを交互に使う。関門にしない | Q1 A、共通の決定、U2 の NFR6.3、W10 |
| NFR6.4 | 画面の待ちの API の時間は、U2 の目標（取得・保存 p95 1 秒、パスワードの変更の成功 p95 2 秒、誤り p95 1 秒）で押さえ、U7 では測らない。プリファレンスの画面は開くたびに GET を1回だけ送り、パスワードの変更の画面は開いても API を呼ばない | API の時間は Performance Validation の k6（U2 の持ち物）。要求の回数は画面部品のテストで、画面を開いたときに GET が1回だけ呼ばれること、パスワードの変更の画面を開いても API の関数が呼ばれないことを見る（コード生成） | 要点 1・2、U2 の NFR6.1〜NFR6.4、D1、W2・W9 |
| NFR6.5 | 送信の間の表示と二重送信の防止: 送信の間は主な操作のボタンに make-you-chic-ui の Button の `loading` を渡し（`aria-disabled`・`aria-busy`、文言「保存しています」「変更しています」）、ボタンを押しても・文字の項目で Enter を押しても、要求は1回だけにする。送信の間は文字の項目を `readOnly` にし、ラジオの選択を受け付けない。画面の側に独自の時間切れ・再送は足さない（既存の ApiClient のまま） | 画面部品のテスト（部品 7節の「送信中」）。偽の API に答えを返さない状態で、ボタンを続けて押す・Enter を押しても API の関数の呼び出しが1回だけであることを見る | 要点 3、D10、CR6.3 |
| NFR6.6 | 初回の読み込みの JavaScript を増やさない。2つの画面は既存の画面と同じく遅延読み込み（`React.lazy`）で登録する。既存の目安（入口のファイルと、そこから静的に読み込まれるファイルの合計が gzip で 500KB 以内、超えたら警告で統合は止めない）をそのまま当てる | 既存の `frontend/scripts/check-bundle-size.mjs`（`./gradlew verify` の中）。`registration.ts` の画面が `lazy` で読まれることをコード生成で確かめ、U7 の変更の前後の値を記録する | 要点 4、前の Intent の U1 の NFR1.5 |

## 2. 測り方の注意

- NFR6.1〜NFR6.3 はブラウザと PC の状態に左右されます。そのため統合の関門にはせず、測った値を Build and Test の結果に記録します（U4 の NFR6.1、U5 の画面の時間と同じ扱い。`team.md` の「不安定なテストは統合しない」）。
- 目標を超えたときは、目標を緩めて「満たした」ことにはしません（`project.md` の Testing Posture）。API の時間か、遅延読み込みか、描画かを切り分け、結果とともに依頼者に相談します。
- 測りのテストは、流れの E2E ではありません。そのため `team.md` の「機能の Intent ごとに代表の流れを1本まで足す」の本数に数えず、前のテストが作った状態に頼らず、利用者などの前提を自分で作ります（U4 の NFR9.11 と同じ読み方）。測りの置き場（`./gradlew e2eTest` の中の測りのファイルか、Build and Test の手順か）は、B5 のコード生成の計画で決めます。
- E2E の WAR は内部DB が空の状態から起動します。測りは、ヘルスチェックが通り、ログインを済ませた後に始めます。パスワードの変更は監査に残りますが、E2E の使い捨ての内部DB のため本物の監査ログは汚しません。
- 長い計測は `caffeinate -i` を付けて流し、PC のスリープで値が崩れるのを防ぎます（`project.md` の Testing Posture）。

## 3. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|
| 画面の時間の目標（開く 2 秒・保存 1.5 秒・パスワードの変更 2.5 秒）を置く（Q1 A、共通の決定） | 要件の NFR6（`inception/requirements-analysis/requirements.md`） | API の応答時間だけを決めており、画面の時間の目標は無い | 追加（NFR6.1〜NFR6.3）で、食い違いではない。統合の関門にせず、測って記録する |
| パスワードの変更の画面の時間 2.5 秒 | 要件の NFR6 | 「パスワードの変更の API は 95 パーセンタイルで 1 秒以内 [assumption]」 | API の目標は U2 の段で p95 2 秒に決まっている（U2 の NFR6.3 と、その上流との差 P-D1）。U7 は U2 の値に余裕を足した画面の目標を置いた（NFR6.3） |
| 初回の JavaScript の目安 500KB | 前の Intent の U1 の NFR1.5 | 目安（警告だけ） | そのまま当てる（NFR6.6）。変更は無い |
