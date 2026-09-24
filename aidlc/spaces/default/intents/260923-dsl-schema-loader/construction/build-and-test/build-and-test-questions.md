# Build and Test — Questions

前提（2026-09-24 の実測。`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`、コミット 39f9aeb）:
- 結果は成功。時間は 4分21秒。colima の VM（CPU 4・メモリ 6GiB）の使用量は最大 約 1.4GiB（配備したアプリを動かしたまま）。
- テストはバックエンドの単体 710 件・結合 375 件（対象DB 3種類を含む）、フロントエンド 313 件で、失敗は0件。
- カバレッジはバックエンドの全体で行 98.1%・分岐 94.1%、フロントエンドは行 97.9%・分岐 93.6%。新しいパッケージはすべて下限以上。既存の `auth.repository`（分岐 50.0%、1/2）と `common.health`（行 79.2%、42/53）は単独で下限を下回るが、全体の合計で判定する既存のパッケージの一覧に入っている。
- 測るべき目標は 106 件。

## Q1. 対象DB の結合テストをどこで実行するか（team.md の Testing Posture の決まり）

実測は 4分21秒、VM のメモリの最大は 約 1.4GiB で、余裕がある。

A. 今のまま、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）
B. `./gradlew verify` では1種類だけ実行し、3種類は CI と別のタスクで実行する
C. 3種類すべてを別のタスクに分け、統合の前に手で実行する
X. Other (please specify)

[Answer]: A

## Q2. 時間と資源の目標（NFR1.4〜1.8・1.10〜1.12・2.5・1.18〜1.20 など）をどの段で測るか

承認済みの設計は Build and Test に割り当てているが、この Intent の流れには Performance Validation の段がある。

A. すべてこの段で測る
B. 1回ずつの時間はこの段で測り、95 パーセンタイル（NFR1.10）と同時の実行（NFR1.12・接続プールの余裕）は Performance Validation に回す
C. 時間と資源はすべて Performance Validation に回す（この段では Unverified として持ち主を明記する）
X. Other (please specify)

[Answer]: B

## Q3. 測るための台本と環境をリポジトリに足すか

今の k6 の台本（`perf/k6/scenarios.js`）には DSL の場面が無く、使い捨ての環境に対象DB も無い。

A. k6 の DSL の場面と、3種類の対象DB（100 テーブル × 100 カラム）を用意する仕組みを足す
B. 足さずに、この段では手元の簡単な測り方（curl の時間など）だけで測る
C. Q2 で Performance Validation に回した分はその段で決める（この段では足さない）
X. Other (please specify)

[Answer]: A

## Q4. 資源の条件（コンテナのメモリの上限）

NFR1.12 の条件はコンテナの上限 1g だが、今の配備は 2g。

A. 今の配備と同じ 2g で測る（要件との差を明記する）
B. 要件どおり 1g で測る
C. 1g と 2g の両方で測る
X. Other (please specify)

[Answer]: A

## Q5. 画面からの一連の操作を確かめるテスト（E2E）に、DSL の管理画面の流れを足すか

依頼者の回答（最初の問い）: 「足す。なおE2Eテストのファイル名について、現有のものも含めて名前づけを見直したい。順番に実行されるように。」を受けて、問いを次のとおり出し直した。今の E2E は `frontend/e2e/` の `u1-skeleton.e2e.ts`・`u2-auth.e2e.ts`・`u3-admin-access.e2e.ts`（前の Intent の単位の番号）で、`playwright.config.ts` は `workers` を指定していない。

A. 01-skeleton.e2e.ts・02-auth.e2e.ts・03-admin-access.e2e.ts・04-dsl-admin.e2e.ts（番号＋中身の名前）にし、workers: 1 で番号の順に実行する
B. 010-skeleton・020-auth・030-admin-access・040-dsl-admin（10 刻みで、間に後から差し込める番号）にし、workers: 1 で順に実行する
C. E2E は足すが、名前の見直しはしない
D. E2E は足さない（画面の時間と英語の表示は手で確かめる）
X. Other (please specify)

[Answer]: B

## Q6. 手での確認（アクセシビリティと、コンテナの実行環境が無いときの動き）

Tab の順・フォーカスの見え方・200% の拡大・767px の幅・VoiceOver の読み上げと、colima を止めて「警告を出して対象DB のテストを飛ばす」動き（NFR12.3）。

A. この段で、依頼者が手で確かめる（AI は手順と記録の表を用意する）
B. アクセシビリティは依頼者が後で確かめ（Unverified で残す）、コンテナが無いときの動きは AI がこの段で確かめる
C. どちらも後に回す（Unverified で残す）
X. Other (please specify)

[Answer]: B

## Q7. traceability.json の食い違い（U1〜U3）

U1〜U3 の一部（NFR1.1・1.2・6.2・6.3・4.8・9.2・12.1〜12.3）が、確かめたテストではなく本番のソースや設定を指している（U4 の A1 と同じ種類）。

A. この段では直さず、cross-unit-traceability.md に差として記録する
B. 直す（Code Generation の記録を直すことになる）
X. Other (please specify)

[Answer]: A

## Q8. 監視（ダッシュボードの式・ログの絞り込み）の確かめ

A. この段で確かめる
B. Observability Setup に引き継ぐ（この段では Unverified として持ち主を明記する）
X. Other (please specify)

[Answer]: B

## Q9. 既存のパッケージのカバレッジ（`auth.repository` の分岐 50.0%・`common.health` の行 79.2%）

A. 今のまま（全体の合計で判定する既存のパッケージ。team.md の決まりどおり）
B. この段でテストを足し、単独で下限に届かせる
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

回答をまとめると、この段では次のとおり進める。

1. 対象DB の結合テストは、今のまま3種類すべてを `./gradlew verify` の中で毎回実行する（Q1: A）。実測（4分21秒・VM のメモリ最大 約 1.4GiB）を記録する。
2. 時間の目標は、1回ずつの時間をこの段で測る。95 パーセンタイル（NFR1.10）と同時の実行（NFR1.12・接続プールの余裕）は Performance Validation に回し、この段では Unverified として持ち主を明記する（Q2: B）。
3. k6 の台本に DSL の場面を足し、3種類の対象DB（100 テーブル × 100 カラム）を使い捨ての環境で用意する仕組みをリポジトリ（`perf/`）に足す（Q3: A）。
4. 測るときのコンテナの上限は、今の配備と同じ 2g にし、要件（1g）との差を明記する（Q4: A）。使い捨ての環境は配備したアプリとは別に立て、VM のメモリに余裕があるため配備したアプリは止めない。
5. E2E は `010-skeleton.e2e.ts`・`020-auth.e2e.ts`・`030-admin-access.e2e.ts` に名前を変え、DSL の管理画面の流れ（ログイン → DSL の管理 → 投入 → プレビュー → 適用）を `040-dsl-admin.e2e.ts` として足す。`playwright.config.ts` を `workers: 1` にして番号の順に実行し、README の説明も直す（Q5: B）。
6. アクセシビリティの手での確認は、依頼者が後で行う（この段では Unverified）。コンテナの実行環境が無いときの動き（NFR12.3）は、AI がこの段で確かめる。colima を止めると配備したアプリも止まるため、Docker の接続先を存在しない場所に向けて「届かない」状態を作って確かめる（Q6: B）。
7. U1〜U3 の traceability.json の食い違い（本番のソースや設定を指すもの）は直さず、cross-unit-traceability.md に差として記録する（Q7: A）。
8. 監視（ダッシュボードの式・ログの絞り込み）は Observability Setup に引き継ぐ（Q8: B）。
9. 既存のパッケージのカバレッジは今のまま（Q9: A）。
10. 古い記述との差（既定の DSL は約 5.3MB ではなく約 6.7MB、U4 の要件の「接続の待ち 5 秒」は実装では 3 秒、決定 B による照合の最悪 23〜28 秒）は、目標の値を緩めずに測り、成果物に差として明記する。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
