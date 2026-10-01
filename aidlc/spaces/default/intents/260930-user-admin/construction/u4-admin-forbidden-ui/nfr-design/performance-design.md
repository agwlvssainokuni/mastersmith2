# Performance Design — U4 管理の画面の 403 の共通の扱い（u4-admin-forbidden-ui）

U4 の性能の設計です。承認済みの NFR 要件 `aidlc/spaces/default/intents/260930-user-admin/construction/u4-admin-forbidden-ui/nfr-requirements/performance-requirements.md`（NFR5.1・NFR9.1〜NFR9.4）を満たす作りを決めます。U4 は画面の単位（種類 ui）で、自分の API を持たず、サーバーのコードと依存を変えません。そのため、拡張性・信頼性・観測の設計の文書は作りません（段の定義の `produces_kinds`）。セキュリティの作りは `security-design.md`、部品の一覧・境界・テストと関門・実際のブラウザの検査 130 は `logical-components.md` にあります。

この段の質問 `nfr-design-questions.md` の設計の要点 1〜9 と答え（Q1 A・Q2 B・Q3 A）、まとめの確認（Looks correct）で決めました。性能に関わるのは要点 2 です。

出典の略号: PR は `nfr-requirements/performance-requirements.md`、SR は `nfr-requirements/security-requirements.md`、TS は `nfr-requirements/tech-stack-decisions.md`、FS は `functional-design/functional-spec.md`（D・W は同じ文書の決まりと流れ）、FC は `functional-design/frontend-components.md`、要点 n はこの段の `nfr-design-questions.md` の設計の要点、TM は `aidlc/spaces/default/memory/team.md`、PM は `aidlc/spaces/default/memory/project.md`。コードのパスは `frontend/src/` の下を書きます。

## 1. 性能の考え方

U4 の処理は、どれもブラウザの中の1つのタブで、利用者1人の操作に対して1回だけ動きます。重い計算も、たくさんの要求も、データの保持もありません。そのため、性能の設計の中心は「速くする仕組みを足すこと」ではなく、次の3つを決まった形で守ることです。

| 守ること | 中身 | 要件 |
|---|---|---|
| 待たない | 403 を受けたら、その描画の中で S6 に置き換える。読み直しの結果を待たない | NFR9.1 |
| 重ねない | ログインの状態の読み直し（トークンの更新の API）を、決まった回数より多く呼ばない | NFR9.2・NFR5.1 |
| 重くしない | 新しい依存を足さず、初回の JavaScript の大きさを既存の目安のまま見張る | NFR9.4 |

画面の時間の数値の目標は置きません（NFR9.3、4節）。

## 2. 403 から S6 までの時点（NFR9.1）

### 2.1 置き換えの順序

管理の画面が 403 を受けてから S6 が出るまでの順序を、次のとおりに固定します（FS の W1、FC の 3.3）。

| 順 | 起きること | 置き場 | 待ち |
|---|---|---|---|
| 1 | 画面の失敗の扱いの入口が、失敗と API の根のパスを `useAdminForbidden` の関数に渡す | 各画面（`features/*`） | なし |
| 2 | `isAdminForbidden` で判定する（純粋な関数、副作用なし） | `shared/api-client/adminForbidden.ts` | なし |
| 3 | Provider の `report` が `forbiddenRef` と状態 `forbidden` を今の URL にする | `app/admin-forbidden/AdminForbiddenProvider.tsx` | なし |
| 4 | 同じ `report` の中で、読み直しの約束を `inFlightRef` に置く（呼ぶだけで待たない） | 同上 | なし |
| 5 | 状態が変わった次の描画で、ShellLayout が子の画面の代わりに `AdminForbiddenView` を描く。画面の部品と開いていた Modal は外れる | `app/layout/ShellLayout.tsx` | なし |
| 6 | 読み直しの結果は、後でログイン状態の知らせ（`subscribe`）として届く。印が外れていれば管理のメニューが消え、振り分けが ForbiddenByRoute に移る | 既存の `LoginStateGate`・`app/routing/` | 更新の API の時間（NFR5.1） |

- 1〜5 は同じ操作の続きとして同期で進み、要求も約束の待ちも挟みません。待ちが入るのは 6 だけで、6 の前に S6 は出ています。
- 4 の約束は `report` の中で `await` しません。結果を受け取るのは `finally` で `inFlightRef` を空に戻す処理だけで、表示には使いません（表示は 6 のログイン状態の知らせから決まる）。
- 決まった間隔の確かめ（`setInterval`）・時間切れ（`setTimeout`）・時刻による判定は置きません。表示と印は、403 の知らせとログイン状態の知らせという出来事だけで変わります（FS の D8）。

### 2.2 描画の回数

| きっかけ | 描き直す範囲 | 描き直さない範囲 |
|---|---|---|
| `forbidden` が変わる | 状態用の context を読む ShellLayout（コンテンツの領域の置き換え） | 関数用の context だけを読む各画面の関数（`report` は同じ関数のまま、FC の 3.3） |
| URL のパスが変わる | Provider（その描画の中で `forbidden` を捨てる）と、今までどおりの振り分け | 追加の描画は1回（パスの比べで状態を直す React の形）。`useEffect` で捨てて2回描く形は取らない |
| ForbiddenByApi から ForbiddenByRoute へ移る | 振り分けの判断だけが変わる | ShellLayout・AppShell・`AdminForbiddenView` は同じ形の木のため作り直さない（FS の 4節、R-05） |

- Provider は状態用と関数用の2つの context に分けます。状態が変わっても `report` と `useAdminForbidden` が返す関数の同一性は変わらず、それを依存に入れた各画面の `useCallback`・`useEffect` が繰り返されません。読み込みが繰り返されないことは、性能（余分な要求を出さない）と正しさの両方の守りです。
- 自分の氏名と言語の反映（`applyOwnProfile`）も、Provider が生きている間は同じ関数にします（`security-design.md` 4.2、Q3 A）。U5 の画面がこれを依存に入れても描き直しを起こしません。

### 2.3 確かめ方

- `AdminForbiddenProvider.test.tsx`・`ShellLayout.test.tsx` で、ApiClient に登録した偽物の更新を「答えを返さない約束」（保留のまま）にし、`report` の直後の描画で S6 が出ていることを確かめます（PR の 2節）。
- 決まった間隔の処理が無いことは、Vitest の偽の時計を進めても更新が呼ばれないことで確かめます。実時刻と `sleep` に頼らず、描画の後に反映される値は `waitFor` で待ちます（TM の Testing Posture）。

## 3. 読み直しの回数と時間（NFR9.2・NFR5.1）

### 3.1 回数の決まり

読み直しの回数は、骨組みと ApiClient の2段で決めます。骨組みの側で回数を決め、ApiClient のまとめには最後の守りとしてだけ頼ります（FS の D7）。

| 場合 | 骨組み（Provider）の扱い | ApiClient の扱い | 更新の API の呼ばれる回数 |
|---|---|---|---|
| 同じ URL で 403 を重ねて受ける（DSL の管理が並べて読む3つの要求など） | 2つ目から `forbiddenRef` が同じ URL のため、何もせずに終える | — | 1 |
| URL に結び付かない 403（届く前に別の URL へ移った、D4） | `inFlightRef` が空のときだけ呼ぶ。終わる前なら呼ばない | — | 1 |
| 読み直しと 401 の更新が重なる | 骨組みは1回呼ぶ | 今の `refreshOnce` の仕組み（進行中の約束 `pendingRefresh` を共有する）で1回にまとまる | 1 |
| 読み直しが終わった後に、別の URL で新しく 403 を受ける | 新しい読み直しとして1回呼ぶ | — | 新しい URL ごとに 1 |

```text
// refreshSessionOnce の形（説明用。中身は今の refreshOnce と同じ）
export function refreshSessionOnce(): Promise<boolean> {
  return refreshOnce() // 登録が無ければ false。進行中の更新があれば同じ約束を返す
}
```

- `refreshSessionOnce` は、今の `apiClient.ts` の内部の `refreshOnce` を外へ出すだけの口です。新しい要求の経路・新しい API・新しい送り直しを作りません（NFR5.1、FC の 3.2・6.1）。
- 確かめ: `AdminForbiddenProvider.test.tsx`（同じ URL の重なり・終わる前の結び付かない 403）と `apiClient.test.ts`（401 の更新との重なり、登録が無ければ false）で、登録した偽物の更新の呼ばれた回数を数えます（PR の NFR9.2）。

### 3.2 読み直しの時間（NFR5.1）

- 読み直しの時間は、既存のトークンの更新の API（POST `/api/auth/session/refresh`）の時間そのものです。その目標（同時 10 件で p95 1 秒以内、前の Intent の U2 の NFR6.5）をそのまま当て、U4 のために測り直しません。更新の API は、この Intent の U1 が停止の判定を足すため、その性能は U1 の NFR5 の持ち物で、performance-validation の既存の k6 の更新の場面で測ります。
- U4 が増やす更新の API の要求は、管理者の印を外された利用者1人につき、403 を受けた URL ごとに1回です。印の変更はまれな管理の操作のため、更新の API の負荷の見積もりは変わりません。
- 更新の API が目標を超えたと分かったときも、目標を緩めて「満たした」ことにはしません（PM の Testing Posture）。

## 4. 画面の時間の目標を置かない（NFR9.3）

- 403 を受けてから S6 の見出しが見えるまで、管理のメニューが消えるまでの画面の時間に、数値の目標を置きません（NFR 要件の Q2 A、PR の NFR9.3）。S6 は待ちなしで出るため（2節）、測る値はほぼ更新の API の時間と同じになり、それは 3.2 で押さえます。
- 実際のブラウザの検査 130（`logical-components.md` 6節）でも時間は測りません。前の Intent の画面の単位の検査（060 など）は画面の時間を記録していましたが、130 は記録もしません。
- 目標を置く必要が出たときは、目標を緩めた形にせず、依頼者に諮って新しい要件として決めます（PR の NFR9.3）。

## 5. 初回の JavaScript の大きさ（NFR9.4）

| 項目 | 設計 |
|---|---|
| 足すもの | 部品3つ（`AdminForbiddenProvider`・`AdminForbiddenView`・`useApplyOwnProfile`）、純粋な関数2つ（`isAdminForbidden`・`forbiddenHeadingKey`）、口1つ（`refreshSessionOnce`）、文言の鍵3つ（ja・en）、CSS 1ファイル |
| 足さないもの | 実行時・開発時の依存（NFR9.6）。make-you-chic-ui の新しい部品（今の固定先の `Alert` だけを使う、NFR9.7） |
| 読み込みの分け方 | 骨組みの部品のため初回の読み込みに入る。管理の画面を遅れて読む分け方（`lazy`）は足さない（足すと 403 の後に S6 を描くまでに読み込みの待ちが入り、2節の「待たない」を崩すため） |
| 見張り | 既存の `frontend/scripts/check-bundle-size.mjs`（`./gradlew verify` の中、gzip で 500KB を超えたら警告だけ、統合は止めない）。コード生成で U4 の変更の前と後の値を測って記録する |

増える量は、文言と小さな部品だけのため、数 KB（gzip）までと見込みます。見込みは確かめの代わりにしません。実際の値はコード生成の記録で確かめます。

## 6. 足さない仕組み

| 仕組み | 足さない理由 |
|---|---|
| キャッシュ（印・権限が無い URL の保存） | 印の正はサーバーで、画面の側の印はトークンの応答からだけ入る（`security-design.md` 2.3）。権限が無い URL は1つだけを状態に持ち、URL が変わったら捨てる（FS の D5）。ブラウザの保存にも書かない |
| 読み直しの再試行・時間切れ・決まった間隔の確かめ | 読み直しの失敗は、今のトークンの更新の失敗と同じ扱い（FS の D9）。時間の経過で判定しない（FS の D8） |
| 接続プール・非同期のキュー | ブラウザの中の処理で、内部DB の接続を使わない（要件の NFR6 は N/A） |
| 遅れて読む分け方（`lazy`） | 5節のとおり |
| 画面の時間の計測と送信 | 目標を置かないため（4節）。観測の指標も足さない（U4 は観測の設計の対象外） |

## 7. プラットフォームの視点

配備先は開発者の PC 上のコンテナです（PM の Deployment）。U4 の変更は、WAR に同梱するフロントエンドのビルドの結果（`dist`）の中身だけで、コンテナ・JVM・メモリの上限・CSP の設定（`backend/src/main/resources/application.yaml`）・compose の設定に変化はありません。配信の仕組み（同じオリジンの静的なファイル）も変わらないため、配信の性能の見積もりは変わりません。

## 8. 上流との差

性能の設計で、承認済みの NFR 要件・機能設計から変えたものはありません。差と残る危険は `security-design.md` の 8節・9節にまとめました。
