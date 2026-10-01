# Performance Requirements — U4 管理の画面の 403 の共通の扱い（u4-admin-forbidden-ui）

U4 の性能の要件です。U4 は画面の単位（種類 ui）で、自分の API を持たず、サーバーのコードも変えません。ここでは、403 を受けてから S6 に置き換わるまでの時点、ログインの状態の読み直しの回数と時間、画面の時間の目標を置かないこと、初回に読み込む JavaScript の大きさを扱います。答えは `nfr-requirements-questions.md` です（Q1: B、Q2: A、Consolidated Summary Confirmation: Looks correct）。

出典の略号:
- NFR・FR は要件 `aidlc/spaces/default/intents/260930-user-admin/inception/requirements-analysis/requirements.md` の ID
- D・W・4節〜8節は、この単位の `construction/u4-admin-forbidden-ui/functional-design/functional-spec.md`
- 「部品 n節」は、同じフォルダの `frontend-components.md` の節
- Q1・Q2 と「要点 n」は、この段の `nfr-requirements-questions.md`（Q と「設計の要点（案）」の番号）
- U1 の NFRx.y は、この Intent の `construction/u1-user-suspension/nfr-requirements/`
- 前 U2 の NFR6.5 は `aidlc/spaces/default/intents/260925-user-management/construction/u2-user-preferences/nfr-requirements/performance-requirements.md`

## 枝番の振り方と置き場

枝番はこの単位の中で .1 から振ります（ほかの単位と同じ番号でも別の要件です）。要件の NFR5 は API の応答時間を決めるものです。そのため、API の時間に当たる読み直しの要件だけを NFR5 の枝番にし、画面の側の時点・回数・大きさは、上流に当たる ID が無いため、テストと検査の関門で確かめる NFR9 の枝番に寄せました（`project.md` の学び、質問の文書の「設計の要点（案）」の冒頭）。

| 成果物 | 置く要件 |
|---|---|
| この文書 | 読み直しの API の時間（NFR5.1）、画面の側の性能（NFR9.1〜NFR9.4） |
| `security-requirements.md` | 画面の判定とサーバーの判定（NFR1.1〜NFR1.5）、画面に出す値と報告（NFR3.1・NFR3.2）、静的検査と CSP（NFR9.5） |
| `tech-stack-decisions.md` | アクセシビリティ（NFR7.1〜NFR7.3）、多言語（NFR8.1・NFR8.2）、依存・make-you-chic-ui・テスト・境界（NFR9.6〜NFR9.12） |

## 前提

- S6 への置き換えは、管理の画面が失敗を `useAdminForbidden` の関数に渡したその描画の中で終わります（D3）。待ちが入るのは、その後のログインの状態の読み直し（既存のトークンの更新の API）と、その結果による管理のメニューの消え方だけです（D7・D8、W3）。
- 読み直しに使うトークンの更新の API（POST `/api/auth/session/refresh`）は、この Intent の U1 が停止の判定を足します。その性能の要件は U1 の NFR5 の持ち物で、U4 は API と処理を変えません。
- 画面の時間は、統合の関門にはしない扱いです（前の Intent の画面の単位と同じ、`team.md` の「不安定なテストは統合しない」）。U4 では、Q2: A により画面の数値の目標そのものを置きません。

## 1. 要件

| ID | 要件 | 確かめ方と持ち主の段 | 出典 |
|---|---|---|---|
| NFR5.1 | ログインの状態の読み直しは、既存のトークンの更新の API を `refreshSessionOnce` で1回呼ぶ形で行い、新しい API を足さない。読み直しの時間の目標は、更新の API の既存の目標（同時 10 件で p95 1 秒以内、前 U2 の NFR6.5）をそのまま当てる。U4 は更新の API と処理を変えないため、U4 のために測り直さない | 更新の API の時間は、performance-validation の既存の k6 の更新の場面で測る範囲のまま（U1 の NFR5 の持ち物）。U4 では、`refreshSessionOnce` が登録済みの更新を呼ぶだけで新しい要求の経路を作らないことを、コード生成で `apiClient.test.ts` によって確かめる | 要点 3、Q2 A、D7、前 U2 の NFR6.5 |
| NFR9.1 | 管理の画面が 403（`/api/admin/` の下・`ACCESS_DENIED`）を渡したら、同じ描画の中で S6 に置き換え、置き換えの前に新しい要求も待ち（読み直しの結果の待ちを含む）も挟まない。読み直しは S6 を出した後に始まり、失敗を渡した画面はその結果を待たない。時間の経過（決まった間隔の確かめ・時間切れ）で表示や印を判定しない | コード生成で、`AdminForbiddenProvider.test.tsx` と `ShellLayout.test.tsx` によって、偽物の更新に答えを返さない（保留のままの）状態でも、`report` の直後の描画で S6 が出ていることを確かめる。決まった間隔の処理が無いことは、偽の時計を進めても更新が呼ばれないことで確かめる | 要点 1、D3・D7・D8、W1・W3 |
| NFR9.2 | 読み直しの回数を次のとおりにする。同じ URL で 403 を重ねて受けても（DSL の管理が並べて読む3つの要求など）読み直しは1回。URL に結び付かない 403（D4）は、骨組みが呼んだ読み直しがまだ終わっていない間は新しく呼ばない。読み直しと 401 の更新が重なったときは、ApiClient の今の仕組みで更新は1回にまとまる | コード生成で、`AdminForbiddenProvider.test.tsx`（同じ URL の重なり・終わる前の結び付かない 403）と `apiClient.test.ts`（401 の更新との重なり）によって、ApiClient に登録した偽物の更新の呼ばれた回数を数えて確かめる | 要点 2、D7、6節、部品 3.3・5.2 |
| NFR9.3 | 403 を受けてから S6 の見出しが見えるまで、管理のメニューが消えるまでの画面の時間に、数値の目標を置かない。S6 は待ちなしで出るため（NFR9.1）、測る値がほぼ更新の API の時間と同じになり、その時間は NFR5.1 で押さえる。実際のブラウザの検査（`tech-stack-decisions.md` の NFR7.3）でも時間は測らない | 確かめの対象にしない（目標を置かない決定の記録）。目標を置く必要が出たときは、目標を緩めた形にせず、依頼者に諮って新しい要件として決める | Q2 A、要件 NFR5（API だけを決める） |
| NFR9.4 | 初回の読み込みの JavaScript（入口のファイルと、そこから静的に読み込まれるファイル）は、gzip で 500KB 以内を目安とする。超えたら警告を出すだけで、統合は止めない（既存の目安のまま）。U4 は新しい実行時の依存を足さず、足すのは小さな部品と関数だけとする | 既存の `frontend/scripts/check-bundle-size.mjs`（`./gradlew verify` の中）。U4 の変更の前後の値をコード生成で測って記録する | 要点 4、前の Intent の画面の単位の目安 |

## 2. 測り方の注意

- NFR9.1・NFR9.2 は、時間ではなく「描画の順序」と「呼ばれた回数」で確かめます。偽物の更新は答えを保留できる形にして、読み直しが終わる前の状態を確実に作ります。実時刻と `sleep` に頼らず、描画の後に反映される値は `waitFor` で待ちます（`team.md` の Testing Posture）。
- テストの時間の上限（Vitest の既定 5 秒など）は、原因を確かめずに延ばしません（`team.md` の Testing Posture）。
- 更新の API の時間（NFR5.1）が目標を超えたと performance-validation で分かったときも、目標を緩めて「満たした」ことにはしません（`project.md` の Testing Posture）。

## 3. 上流との差

承認済みの文書は書き換えません（`aidlc/spaces/default/memory/project.md` の Way of Working）。

| 決定 | 文書 | 承認済みの記述 | この段での扱い |
|---|---|---|---|
| 画面の時間の数値の目標を置かない（Q2: A） | 要件の NFR5、前の Intent の画面の単位（招待の画面などの画面の時間の目標） | 要件は API の応答時間だけを決める。前の Intent では画面を開いてから表示までの目標を置き、E2E で測って記録した | U4 では置かない（NFR9.3）。S6 は待ちなしで出て、測る値がほぼ API の時間と同じになるため。食い違いではなく、置かない理由の記録 |
| 画面の側の性能を NFR9 の枝番に寄せる | 要件の NFR1〜NFR11 | 画面の側の時点・回数・大きさに当たる ID が無い | NFR9.1〜NFR9.4 に寄せた（冒頭のとおり）。traceability.json の NFR9 の target に書く |
