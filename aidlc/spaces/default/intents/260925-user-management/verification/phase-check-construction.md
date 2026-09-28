# Phase Check — Construction → Operation

**判定: 条件付きで合格（Pass with conditions）**

- 8 単位すべてが作られ、試されました。
- 各単位の `traceability.json` に、未解決（GAP・ORPHAN）はありません。
- 単位をまたぐ FR・NFR・AC の網羅は合格しました（未網羅 0 件）。
- CI の関門は、Build and Test が記録した build と test のコマンドを、同じ `./gradlew verify` で実行します。

ただし、次の2つの理由で条件を付けます。
- **CI での実行は、`66fe981` で2回とも失敗しました**（別々のテストの時間切れ）。依頼者の決定で、直すのは次の Intent です。
- **Build and Test の目標のうち、受け入れた失敗が Not Met 5 件、後の段が持ち主の Unverified が 36 件あります。**

点検の概要は次のとおりです。
- 点検日：2026-09-28
- 点検した記録：
  - `construction/build-and-test/cross-unit-traceability.md`
  - 各単位の `construction/*/code-generation/traceability.json`（8つ）
  - `construction/build-and-test/test-results.md`・`build-and-test-summary.md`
  - `construction/ci-pipeline/ci-config.md`・`quality-gates.md`
- 点検の対象のコミット：`66fe981`（Build and Test の判定の元）

## 1. 8単位が作られ試されたこと

| 単位 | 種別 | コード生成の記録 | 状態 |
|---|---|---|---|
| U1 メールの描画と送信 | library | `u1-mail/code-generation/` | 作成・試験済み |
| U2 利用者のプリファレンスとパスワードの変更 | service | `u2-user-preferences/code-generation/` | 作成・試験済み |
| U3 招待と登録の完了 | service | `u3-invitation/code-generation/` | 作成・試験済み |
| U4 表示の設定の土台 | ui | `u4-display-foundation/code-generation/` | 作成・試験済み |
| U5 招待の管理の画面 | ui | `u5-invitation-ui/code-generation/` | 作成・試験済み |
| U6 登録の完了の画面 | ui | `u6-registration-ui/code-generation/` | 作成・試験済み |
| U7 プリファレンスとパスワードの変更の画面 | ui | `u7-preferences-ui/code-generation/` | 作成・試験済み |
| U8 インスタンスの見た目の設定 | service | `u8-instance-appearance/code-generation/` | 作成・試験済み |

全体の実測（`test-results.md`）は次のとおりです。
- **verify**：`./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` が成功（6分28秒）。単体 1,234・結合 562・画面 732 件がすべて通りました（失敗 0・飛ばし 0）。
- **E2E**：90 件すべて通りました（3分25秒）。
- **カバレッジ**：
  - バックエンドの全体は行 98.8%・分岐 94.4% です。パッケージごとに判定する 35 パッケージも、すべて下限以上です。
  - 画面は行 97.44%・分岐 92.67% です。

## 2. 各単位の traceability.json の未解決

| 単位 | 上流の ID の数 | OK | Deferred | N/A | GAP | ORPHAN | reverse |
|---|---|---|---|---|---|---|---|
| u1-mail | 67 | 63 | 4 | 0 | 0 | 0 | 0 件 |
| u2-user-preferences | 96 | 71 | 25 | 0 | 0 | 0 | 0 件 |
| u3-invitation | 164 | 143 | 21 | 0 | 0 | 0 | 0 件 |
| u4-display-foundation | 77 | 41 | 36 | 0 | 0 | 0 | 0 件 |
| u5-invitation-ui | 69 | 60 | 9 | 0 | 0 | 0 | 0 件 |
| u6-registration-ui | 61 | 49 | 12 | 0 | 0 | 0 | 0 件 |
| u7-preferences-ui | 58 | 45 | 10 | 3 | 0 | 0 | 0 件 |
| u8-instance-appearance | 41 | 38 | 3 | 0 | 0 | 0 | 0 件 |
| **合計** | **633** | **510** | **120** | **3** | **0** | **0** | **0 件** |

**GAP・ORPHAN は 0 件です。** Deferred の 120 件は、次の2つに分かれます（`cross-unit-traceability.md`）。
- **ほかの単位で OK になっているもの**（AC 64 件など）：持ち主の単位を付け替えた記録です。連鎖は閉じています。
- **後の段が持ち主のもの**：NFR5・NFR6・NFR9・NFR10 の枝番（計 24 項目）です。
  - performance-validation：p95・接続プール
  - observability-setup：指標・警報
  - deployment-pipeline・deployment-execution：戻しの練習・バックアップ

## 3. 単位をまたぐ FR・NFR・AC の網羅

`cross-unit-traceability.md` の判定は**合格**です。
- 要件の FR 43・NFR 11 と、ストーリーの AC 84 の計 138 件が、すべて status `OK` と実在する target につながりました。
- NFR5・NFR6・NFR9・NFR10 には、後の段への持ち越しがあります（2 節）。

記録上の注意です（網羅の判定は変えません）。
- U4〜U7 の AC・CR の target は、確かめたテストではなく本番のソースを指しています。そのうち 12 ファイルには、同じ場所にテストがありません。
- FR4.3・FR5.5・FR7.1・FR7.4 は、文書の記述でたどった連鎖です。

## 4. CI の関門が Build and Test のコマンドを実行すること

- Build and Test の `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` に当たる検査は、CI のジョブ `verify` が同じ `./gradlew verify` で実行します（`quality-gates.md` の 4 節）。
- E2E・負荷の試験は、意図して CI の外に置いています。代わりの実行の場は `quality-gates.md` の 5 節にあります。
- CI での実行の結果：`66fe981` で**2回とも失敗**しました（`ci-config.md` の 6 節）。
  - 1回目：`H2CompactionByPoolSuspensionIT` の接続の待ち 10 秒の時間切れ
  - 2回目：`InvitationAdminPage.test.tsx` の1件の既定の 5 秒の時間切れ
  - サブモジュールの取得は、どちらも成功しました。

## 5. 条件（Operation の段と次の Intent に引き継ぐもの）

| 条件 | 持ち主 |
|---|---|
| CI の2つの時間切れを直し、CI を通す | 次の Intent（依頼者の決定） |
| U4〜U7 の NFR7.1（コントラスト 4.5:1）を、make-you-chic-ui の固定先の更新（`7865c28`・`310e1ec`）で満たす | 次の Intent（Q7: A・F1: A） |
| Unverified 36 件（p95・接続プール・指標・警報・SLO・戻しの練習・バックアップほか） | performance-validation・observability-setup・deployment-pipeline・deployment-execution・feedback-optimization |
| Dependabot のプルリクエスト 11 件の取り込み | 次の Intent（CI Pipeline の Q3: A） |
| `.github/dependabot.yml` の `exclude-paths` の効き目の確かめ | 依頼者のプッシュの後の次の Dependabot の実行 |

## Sources

- `construction/build-and-test/cross-unit-traceability.md`・`test-results.md`・`build-and-test-summary.md`
- `construction/u*/code-generation/traceability.json`（8つ）と `code-summary.md`
- `construction/ci-pipeline/ci-config.md`・`quality-gates.md`・`ci-pipeline-questions.md`
- `.claude/knowledge/aidlc-shared/verification.md`

## Assumptions & Open Questions

None.
