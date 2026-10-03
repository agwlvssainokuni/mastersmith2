# Phase Check — Construction → Operation

**判定: 条件付きで合格（Pass with conditions）**

- 5 単位すべてが作られ、試されました。
- 各単位の `traceability.json` に、未解決（GAP・ORPHAN）はありません。
- 単位をまたぐ FR・NFR・AC の網羅は、未網羅 0 件で合格（条件つき）です。
- CI の関門は、Build and Test が記録した build と test のコマンドを、同じ `./gradlew verify` で実行します。B1〜B5 の統合の後と承認の後の push の CI は、すべて success です。

条件を付ける理由は次の2つです。どちらも未解決の指摘ではなく、承認済みの持ち越しです。
- **Not Met 1 件**（U5-NFR7.1、N-19。閉じた後のフォーカスの戻し先）：コード生成の承認の場で、後の Intent への持ち越しとして承認済みです（`construction/code-generation/gate-decisions.md` の2節・4節）。関わる AC 4 件（AC2.1.8・AC3.1.7・AC4.1.9・AC5.1.7）は網羅の表で「条件つき（N-19）」です。
- **Unverified 22 件**：負荷の環境・配備した環境が要る目標で、持ち主の段（performance-validation・observability-setup・deployment-pipeline・deployment-execution・feedback-optimization）がこの Intent の流れにあります。Build and Test の承認の場で、持ち主の段への引き継ぎとして承認済みです（`build-and-test-summary.md` の4節・5節）。目標は緩めていません。

点検の概要は次のとおりです。
- 点検日：2026-10-03
- 点検した記録：
  - `construction/build-and-test/cross-unit-traceability.md`
  - 各単位の `construction/*/code-generation/traceability.json`（5つ）
  - `construction/build-and-test/test-results.md`・`build-and-test-summary.md`
  - `construction/code-generation/gate-decisions.md`
  - `construction/ci-pipeline/ci-config.md`・`quality-gates.md`
- 点検の対象のコミット：`7689ade`（`develop` と `origin/develop` の先頭のアプリのソース。Build and Test の判定の元の `b126bdc` に記録だけを足したもの）

## 1. 5単位が作られ試されたこと

| 単位 | 種別 | Bolt | コード生成の記録 | 状態 |
|---|---|---|---|---|
| U1 利用停止の状態と3つの入口 | library | B1 | `u1-user-suspension/code-generation/` | 作成・試験済み |
| U2 ページ送りの共通化 | library | B2 | `u2-shared-paging/code-generation/` | 作成・試験済み |
| U3 利用者の管理の API | service | B3（前半）・B4（後半） | `u3-user-admin-api/code-generation/` | 作成・試験済み |
| U4 管理の画面の 403 の共通の扱い | ui | B2 | `u4-admin-forbidden-ui/code-generation/` | 作成・試験済み |
| U5 利用者の管理の画面 | ui | B5 | `u5-user-admin-ui/code-generation/` | 作成・試験済み |

全体の実測（`test-results.md`）は次のとおりです。
- **verify**：`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` が成功（`b126bdc`、10分27秒）。単体 1508・結合 689 件がすべて通りました（失敗 0・飛ばし 0）。画面は 109 ファイル・955 件が通りました（`81d2423`）。
- **E2E**：13 ファイル・152 件すべて expected（`81d2423`）。
- **カバレッジ**：
  - バックエンドの全体は行 98.9%・分岐 94.8% です。パッケージごとに判定するパッケージも、すべて下限以上です。
  - 画面は行 97.42%・分岐 92.85% です。
- **osvScan**：失敗の条件 0 件・警告 16 件（すべて npm の開発用）。

## 2. 各単位の traceability.json の未解決

各ファイルの `coverage` の status を数えました（読み取り）。

| 単位 | 上流の ID の数 | OK | Deferred | N/A | GAP | ORPHAN | reverse |
|---|---|---|---|---|---|---|---|
| u1-user-suspension | 68 | 62 | 6 | 0 | 0 | 0 | —（項目なし） |
| u2-shared-paging | 44 | 34 | 10 | 0 | 0 | 0 | —（項目なし） |
| u3-user-admin-api | 155 | 116 | 36 | 3 | 0 | 0 | —（項目なし） |
| u4-admin-forbidden-ui | 39 | 31 | 8 | 0 | 0 | 0 | 0 件 |
| u5-user-admin-ui | 116 | 63 | 53 | 0 | 0 | 0 | 0 件 |
| **合計** | **422** | **306** | **113** | **3** | **0** | **0** | **0 件** |

**GAP・ORPHAN は 0 件で、5つのファイルはすべてあります。** Deferred の 113 件は、次の2つに分かれます（`cross-unit-traceability.md`）。
- **ほかの単位で OK になっているもの**：持ち主の単位を付け替えた記録です。連鎖は閉じています。ただし AC2.2.6・AC3.2.9・AC3.2.10 の3件は、どの単位でも OK になっていません（単位の間で Deferred が回っている）。Build and Test が段全体のレビューの R-02 の決定どおりテストのソースを読んで網羅と判定しました（3節）。
- **後の段が持ち主のもの**：NFR5（13 枝番）・NFR6（2 枝番）・NFR10（2 枝番）です。持ち主は performance-validation・observability-setup・feedback-optimization・deployment-pipeline・deployment-execution です。

N/A の3件は U3 の条件つきの要件（索引か列を足すと決めたとき、など）で、Build and Test は条件に当たらないことを差分で確かめています（`build-and-test-summary.md` の「判定に迷ったもの」）。

## 3. 単位をまたぐ FR・NFR・AC の網羅

`cross-unit-traceability.md` の判定は**合格（条件つき）、未網羅 0 件**です。
- 要件の FR・NFR とストーリーの AC の計 129 件が、すべて網羅（条件つき4件を含む）で、target のファイルはすべて実在しました。
- `traceability.json` の OK だけではつながらない3件（AC2.2.6・AC3.2.9・AC3.2.10）は、テストのソースを根拠に網羅としました。承認済みの `traceability.json` は書き換えていません。
- 条件つき4件（AC2.1.8・AC3.1.7・AC4.1.9・AC5.1.7）は N-19 によるもので、後の Intent への持ち越しとして承認済みです。
- AC2.2.6 は2つのテストの組み合わせでの判定です。1つのテストで続けて確かめるテストを足すことは、後の Intent への持ち越しとして Build and Test で決まりました。

## 4. CI の関門が Build and Test のコマンドを実行すること

- Build and Test の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` と `osvScan --rerun-tasks` に当たる検査は、CI のジョブ `verify` が同じ `./gradlew verify` で実行します（`quality-gates.md` の4節）。CI はまっさらな環境のため、テストと `osvScan` は UP-TO-DATE にならず毎回走ります。
- E2E・負荷の試験・画面の時間の本番での判定は、意図して CI の外に置いています。代わりの実行の場は `quality-gates.md` の5節にあります。
- CI での実行の結果（`ci-config.md` の6節）：
  - B1〜B5 の統合の後と承認の後の push（`726c5ac`・`0402a53`・`493b4dc`・`c13e817`・`21a2fdd`・`335aba6`・`7689ade`）の7つの run は、すべて1回目で success でした（9〜15 分）。
  - この Intent の途中の `3c800d3`・`eb7c982`・`7f3de43` の失敗は `gitleaksScan` の誤検知で、B1 の前に `f299400` で解消済みです。

## 5. Build and Test の Not Met と Unverified（承認済みの持ち越し）

`build-and-test-summary.md` の Target Verification Matrix は 153 件で、Met 130 件・Not Met 1 件・Unverified 22 件です。

| 区分 | 件数 | 中身 | 扱いと承認 |
|---|---|---|---|
| Not Met | 1 | U5-NFR7.1（WCAG 2.1 AA のうちフォーカスの戻し先。実際のブラウザでは S3・S4 を閉じた後のフォーカスが body に移る。make-you-chic-ui の `useFocusTrap` が原因、N-19） | 後の Intent への持ち越し。コード生成の承認の場で承認済み（`gate-decisions.md` の2節・4節）。Build and Test の失敗の手順には入らない決定（`build-and-test-summary.md` の4節） |
| Unverified | 22 | performance-validation 16 件（p95・接続プール・規模、うち3件は observability-setup・feedback-optimization と共同）、observability-setup 3 件（指標の `uri`・`le`、既存の警報が拾う範囲、SLO の基準の値）、deployment-pipeline・deployment-execution 3 件（V9 の事後の裏付け、1つ前の版の動作、戻す前に停止中の利用者を確かめる手順） | 持ち主の段への引き継ぎ。Build and Test の承認の場で承認済み（`build-and-test-summary.md` の4節・5節） |

## 6. 条件（Operation の段と後の Intent に引き継ぐもの）

| 条件 | 持ち主 |
|---|---|
| U5-NFR7.1（N-19）：make-you-chic-ui の直った版へ固定先を上げる専用のコミット（C2′）と、E2E 110・120 の確かめの形の書き換え。U5 の2回目のレビューの R-03（送信中も言語の選択を変えられる）を同じ画面の直しとして扱う | 後の Intent |
| `gate-decisions.md` の4節の持ち越し（Q-H、出力を捕まえるテストの範囲の弱さ、V9 の後の戻し、8KB を超える要求の HTML の 400、U3 R-02 の確かめ直しの隙、`ms-pool-pending` の式の見直し）と、AC2.2.6 を1つのテストで確かめるテスト | 後の Intent |
| Unverified の性能・接続プール・規模（k6 の5つの場面と既存の `login`・`refresh`・`invitationList`。最初に `k6 inspect` を流し直す） | performance-validation |
| Unverified の指標・警報・SLO の基準の値 | observability-setup・feedback-optimization |
| 戻す前に停止中の利用者を確かめる手順（U1-NFR10.3）、スモークテスト（管理の操作は監査に残り利用者の状態を変えるため、入れるときは先に依頼者に伝える） | deployment-pipeline |
| V9 の事後の裏付け（既存の初期管理者でログインでき `/api/me` が通る）、1つ前の版の動作の前提 | deployment-execution |
| Build and Test とこの段の記録の push と、その後の CI の確かめ（`aidlc/` の下だけの変更） | 依頼者 |

## 7. 人が確かめるチェック

依頼者が承認の前に確かめる項目です。

- [ ] 5 単位の `traceability.json` がそろい、GAP・ORPHAN が 0 件であること（2節）
- [ ] `cross-unit-traceability.md` の判定（未網羅 0 件、条件つき4件と、テストのソースで判定した3件）を受け入れること（3節）
- [ ] CI が Build and Test の検査を同じ `./gradlew verify` で実行し、E2E・k6 を CI の外に置く扱いを受け入れること（4節、`quality-gates.md` の4節・5節）
- [ ] Not Met 1 件（U5-NFR7.1、N-19）と Unverified 22 件を、承認済みの持ち越しとして Operation へ進めること（5節・6節）
- [ ] `.gitleaks.toml` の記述の差（Build and Test の記録の「Intent の間で変わっていない」と `f299400`）の扱い（`ci-config.md` の Assumptions & Open Questions）。依頼者の決定（2026-10-03）: 承認済みの Build and Test の記録は書き換えず、`ci-config.md` に差と根拠を残す

## Sources

- `construction/build-and-test/cross-unit-traceability.md`・`test-results.md`・`build-and-test-summary.md`
- `construction/u*/code-generation/traceability.json`（5つ）と `code-summary.md`
- `construction/code-generation/gate-decisions.md`
- `construction/ci-pipeline/ci-config.md`・`quality-gates.md`・`ci-pipeline-questions.md`
- `inception/units-generation/unit-of-work-dependency.md`（単位の種別）
- `aidlc/spaces/default/intents/260925-user-management/verification/phase-check-construction.md`（形の手本）

## Assumptions & Open Questions

None.
