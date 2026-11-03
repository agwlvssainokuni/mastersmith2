# Units Generation の質問（Intent 261102-timezone-ui-polish）

Domain Design の部品16個（`inception/domain-design/components.md`）を、作る単位（Unit）にまとめます。この段は、何が何に頼るか（依存の図）だけを決め、作る順は Delivery Planning が決めます。

## 決まっていること（質問にしない）

- **配備の形**: アプリは1つの実行可能 WAR で、フロントエンドのビルド結果を同梱する（`team.md`）。どの単位も、別々に配備せず、同じ WAR に入る（埋め込み）。
- **ドメイン設計の決定**: 変換は画面の側の1か所（ADR-003）、表示のタイムゾーンは利用者の設定の列を足す（ADR-001）、サーバーに新しい入力の形は足さない（ADR-002）。これらに反するまとめ方はしない。
- **単位の名前**: `u{n}-{説明}` の形（小文字・数字・ハイフン）。単位ごとに種類（`service`・`spec`・`ui`・`packaging`・`library`）を付ける。
- **ストーリーの割り当て**: ストーリー17件（US1.1〜US5.3）を、実装する単位に対応づける。横断のストーリー（アクセシビリティ US5.2・互換と戻し US5.3）は、画面を持つ単位にまたがる。

## Q1. [設計] 単位の粒度とまとめ方

**推奨: A。** 理由: 目的（タイムゾーン・メニューとホーム・アイコン素材）ごとにまとめ、純粋な変換（性質ベースのテストの対象）と対象DB の型の直し（3種類の結合テストの対象）は、確かめ方が違うため別にします。単位が多すぎると、設計の段（機能設計・NFR 設計など）を通す回数が増えます。

A. 6単位にする（下の案）
B. 8単位にする: A の `u2-display-time-zone`（設定の API と画面）を「API」と「画面」に、`u5-menu-icons-home`（メニューの応答とホーム）を「メニューの応答」と「ホームと枠」に分ける。単位は小さくなるが、設計の段を通す回数が増え、境界の契約が増える
C. 3単位にする: タイムゾーン全体（変換・設定・対象DB・業務データの画面）、メニューとホームとアイコン、アプリのアイコン素材。単位が少なく済むが、タイムゾーンの単位が非常に大きくなる
X. Other (please specify)

[Answer]: A

### A の案（6単位）

| 単位 | 種類 | 持つ部品 | ストーリー |
|---|---|---|---|
| u1-time-zone-conversion | library | TimeZoneConversion | US2.5（往復の部分）、US2.3・US2.1 の変換の部分 |
| u2-display-time-zone | service | UserPreferences・TimeZoneNamePolicy・DisplayTimeZoneContext・OtherScreenTimes（設定の API・プリファレンスの選択・管理の画面の時刻） | US1.1、US1.2、US5.3 の一部 |
| u3-target-db-time | library | TargetDbSession・BusinessDataValueCodec・DslDefaultGenerator | US2.2（接続の時差）、US5.1、US2.5（サーバー側の往復） |
| u4-business-data-time | ui | BusinessDataTimeView | US2.1・US2.2・US2.3・US2.4・US2.5 |
| u5-menu-icons-home | ui | NavigationMenu・AppIconPolicy・HomeEntrance・ShellFrame | US3.1・US3.2・US3.3、US4.3・US4.4、US5.3 の一部 |
| u6-app-icon-assets | packaging | AppIconAssets・LoginFrame | US4.1、US4.2 |

## Q2. [デリバリー] 依存の図の上で、独立した単位をどう扱うか

依存の上では、独立した単位（例: u1・u3・u6）は同時に作れます。作る順（経済的な順）は Delivery Planning が決めますが、この段で前提を決めます。

**推奨: A。** 理由: `team.md` は、作業を同じ作業フォルダの短命のブランチで行い、worktree を使わないと決めています。並行して作る前提にすると、依存の図の「独立」の条件（各単位が他の単位の作業を含まない状態で通ること）が厳しくなります。

A. 依存の順に1つずつ作る前提にする。独立して作れる単位は、依存の図に「並行できる」とだけ記録する
B. 独立した単位は同時に作る前提にし、各単位が他の単位の作業を含まない状態でも通るようにする
X. Other (please specify)

[Answer]: A
