# 単位をまたいだ要件の対応 — Intent 261102-timezone-ui-polish

`aidlc engine sensor-traceability --stage build-and-test`（2026-11-04、`fix/261102-timezone-ui-polish-bt1` の `2a6e05f7` の上）の出力から書いた。

## 判定

**部分的に合格（道具の判定は fail）**。受け入れ基準 88 件のうち 87 件、非機能要件 14 件のすべて、機能要件 43 件のうち 38 件が、どこかの単位の作ったファイルに対応している。対応の無い6件（下の表）は、どれもストーリーにせず Build and Test・Code Generation へ Deferred にしたもので、この段の記録で手当てする。

| 区分 | 数 | 対応あり | 対応なし |
|---|---|---|---|
| 機能要件（FR） | 43 | 38 | 5（FR8.1〜FR8.4・FR11.4） |
| 非機能要件（NFR） | 14 | 14 | 0 |
| 受け入れ基準（AC） | 88 | 87 | 1（AC3.3.4） |

## 対応の無い ID（承認の場で示す）

| ID | 中身 | 持ち主の決まり | この段の手当て | 判定 |
|---|---|---|---|---|
| AC3.3.4 | 管理の仮の画面で確かめていた検査（E2E 030・090・130 の 403 の表示・アクセシビリティ・確認の API）を別の画面・テストへ移し、移す前と後の件数を Build and Test で記録する | `inception/user-stories/stories.md` の注記（Build and Test へ Deferred）。U5 の追跡でも Deferred | 下の「検査の移しの件数」に記録した | 記録で満たした |
| FR8.1〜FR8.3 | 管理の仮の画面を外し、道は「見つからない」、サーバーの管理の権限の確認と 403 は残す | U5 が作った（AC3.3.1〜AC3.3.3 は U5 で対応あり）。道具は、同じ要件に結ぶ AC3.3.4 が「作っていない」ため FR8.1〜FR8.3 全体を未対応とした | AC3.3.4 を上で記録した | 記録で満たした |
| FR8.4 | 仮の画面の検査を移して残し、件数を記録する | ストーリーにせず Build and Test へ Deferred（stories.md の注記） | 下の「検査の移しの件数」 | 記録で満たした |
| FR11.4 | 許すアイコンの名前の一覧（サーバーと画面の2か所）を make-you-chic-ui の固定先のアイコンとそろえる | ストーリーにせず Code Generation へ Deferred（stories.md の注記） | U5 の機能設計 D7 と code-summary.md のとおり、`allowed-icons.txt`・`allowedIcons.ts` の 24 個が固定先 `d112795` の登録とそろっている。`allowedNavIcons.test.ts`（サーバーと画面の一致）は Build and Test の `verify` で通過 | 満たした（追跡の行が無いだけ） |

## 検査の移しの件数（AC3.3.4・FR8.4）

| 項目 | 移す前（U5 の前） | 移した後（Build and Test の E2E） |
|---|---|---|
| E2E の全体の本数 | 277（U5 の code-summary.md 5節） | 298（U4 の流れ 200 の 1 件と、185 の時刻の状態の 20 件を足した。277 + 21 = 298） |
| 030（管理の入口の流れ） | 1 | 1（ホームのカードから本物の管理の画面を開く形に書き換え） |
| 090（招待と登録の流れ） | 1 | 1 |
| 130（権限が無い表示の axe） | 20 組（`/admin`・`/api/admin/check` の 403） | 20 組（DSL の管理の画面・`/api/admin/dsl/` の GET の 403）、すべて通過 |
| `features/admin/` の単体テスト | 30 件 | 0（ホーム・サイドバー・「見つからない」の確かめは `realRegistrations`・`decideRoute`・`HomePage`・`ShellLayout` のテストへ移した） |
| サーバーの管理の権限の確認 API（401・403・200） | 結合テストあり | 変更なしで通過（`verify`） |

## 単位ごとの対応（受け入れ基準の主な持ち主）

| ストーリー | 主な単位 |
|---|---|
| US1.1・US1.2（表示のタイムゾーン） | u2-display-time-zone（変換は u1-time-zone-conversion） |
| US2.1〜US2.5（業務データの時刻） | u4-business-data-time（サーバーの値は u3-target-db-time、変換は u1） |
| US3.1〜US3.3（メニュー・ホーム・管理の仮の画面の廃止） | u5-menu-icons-home |
| US4.1・US4.2（アプリのアイコン） | u6-app-icon-assets（ロゴの使い先は u5） |
| US4.3・US4.4（メニューのアイコン） | u5-menu-icons-home |
| US5.1（対象DB のタイムゾーン） | u3-target-db-time |
| US5.2（アクセシビリティ） | u4・u5・u6（u2 は vitest-axe） |
| US5.3（互換と性能） | u2・u3・u5 |

非機能要件 NFR1.1〜NFR6.1 は、すべての ID がどれかの単位の作ったファイルに対応している（NFR2.1 は u1〜u5、NFR3.1 は u4〜u6 など）。測る目標の合否は `build-and-test-summary.md` の Target Verification Matrix に書く。

## Sources

- `aidlc engine sensor-traceability --stage build-and-test` の出力
- `inception/user-stories/stories.md`（FR8.4・FR11.4 の注記）
- `construction/u5-menu-icons-home/code-generation/code-summary.md`（5節）・`functional-design/functional-spec.md`（D7）
- `test-results.md`（E2E の件数）
