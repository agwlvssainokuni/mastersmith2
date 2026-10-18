# 性能の要件 — U4 u4-a11y-carryover

U4 は種類 `ui` の単位で、API を持たず、新しい画面も作らない。変えるのは、読み上げの名前と E2E の確かめだけ。そのため、性能の要件は次の2つに限る。

- 画面の描画と API の呼び出しを増やさないこと
- 検査（E2E・`./gradlew verify`・CI）の時間を記録すること

## 上流の ID との対応

| 上流 | この単位の扱い | この文書の ID |
|---|---|---|
| NFR1 応答時間（業務データの API） | N/A。API を持たない | — |
| NFR2 規模（対象DB・行の数） | N/A。対象DB の行を扱わない | — |
| NFR7 テスト（枝番なし） | 時間の記録をテストの要件として受け持つ | NFR7.1〜NFR7.3 |

NFR4〜NFR6 と、NFR7 のそれ以外の枝番は `security-requirements.md` に書く（NFR7.4〜NFR7.7）。

## 要件

| ID | 要件 | 確かめ方 | 持ち主の段 |
|---|---|---|---|
| NFR7.1 | 名前の関数 `rowActionsName` は純粋な関数で、文字列をつなぐだけにする。3つの行の操作の部品と ShellLayout は、名前を作るために、描画の回数も API の呼び出しも増やさない（props と状態は今のまま。機能設計の D5） | 画面の部品のテストで、一覧の API の呼び出しの回数が変わらないことを確かめる | Code Generation（B3） |
| NFR7.2 | E2E の全件（`./gradlew e2eTest`）の時間を、B3・B7・Build and Test で `caffeinate -dims` を付けて測る。前の Intent の 232 件・約 10分35秒と並べて記録する。上限は置かない（E2E は CI の外にあり、team.md の CI の 42 分の線は当たらない）。流した後の `pmset -g log` に Sleep・DarkWake が入っていたら、結果を使わずに流し直す | 件数・開始と終わりの時刻・眠りの有無の記録 | Code Generation（B3・B7）、Build and Test |
| NFR7.3 | `./gradlew verify` の時間と CI の時間を、B3・B7 の後に記録する（team.md）。件数とカバレッジは、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて流し直した実測だけを書く（project.md） | 記録 | Code Generation（B3・B7）、Build and Test |

- B7 で足す passes の確かめ（`security-requirements.md` の NFR5.3）は、既存の axe の E2E の中で、検査の結果をもう一度読むだけにする。新しく画面を開き直さない。
- axe-core の版を上げると、検査1回の時間が変わりうる。NFR7.2 で前の Intent の値と並べて見る。各 E2E の時間の上限（`frontend/playwright.config.ts` の 120 秒）は、原因を確かめずに延ばさない（team.md）。

## 上流との差

- **違反の要素の見立て**: 機能設計（`aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/functional-design/functional-spec.md` の 2節）は、前の Intent の 100 件の主な原因をトップバーの頭文字と見ている。前の Intent の記録（`aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/code-generation/generation-notes.md` の Step 10、`.github/dependabot.yml` の注記）では、違反の要素は Dropdown のトリガーと作業ロールのメニューの項目で、トップバーは出てこない。性能の要件には響かない。扱いは `security-requirements.md` の NFR5.4 に書いた。
- **機能設計の承認の場の決定**: 定義の API の `nullable` を U2 が持つこと、`formPart` の `lookup` を外すこと、U3 で欠けた値を形の崩れとして扱うこと。どれも U1〜U3 の継ぎ目の決定で、U4 には当たらない。

## 後の段に回すこと

- NFR7.1〜NFR7.3 の測りと記録は、Code Generation（B3・B7）と Build and Test で行う。
- U3 の新しい画面（B5・B6）を足した後の E2E の時間は、B7 と Build and Test の NFR7.2 で一緒に測る。

## Sources

- 要件: `aidlc/spaces/default/intents/261016-business-data-views/inception/requirements-analysis/requirements.md`（NFR1・NFR2・NFR7）
- 機能設計: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/functional-design/functional-spec.md`（D1・D5・D9・D10）、`frontend-components.md`（2節・3節）
- 質問と答え: `aidlc/spaces/default/intents/261016-business-data-views/construction/u4-a11y-carryover/nfr-requirements/nfr-requirements-questions.md`（決まっていること・まとめの確認）
- 前の Intent の E2E の時間: `aidlc/spaces/default/intents/261015-deps-ui-carryover/construction/build-and-test/test-results.md`（2節）
- コード: `frontend/playwright.config.ts`（`workers: 1`・`retries: 0`・各 120 秒）
- 決まり: `aidlc/spaces/default/memory/team.md`（Testing Posture）、`aidlc/spaces/default/memory/project.md`（Testing Posture の学び）

## Assumptions & Open Questions

None.
