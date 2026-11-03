# コード生成のまとめ — U5 u5-menu-icons-home

作業ブランチ: `feature/261102-timezone-ui-polish-b6`（`develop` の `102608aa` から作成）。**コミット・push・タグはまだしていない**（統合の前に、依頼者の承認を取る）。計画: `code-generation-plan.md`（承認済み）、テスト手順: `unit-test-instructions.md`。

## 1. 作った・直した・消したもの

### サーバー（`navigation`）
| ファイル | 内容 |
|---|---|
| `domain/NavTableKind.java`（新規） | 列挙 `TABLE`・`VIEW` |
| `domain/NavNode.java` | `tableKind`（nullable）を足す。持てるのは移れる項目だけ。`tableKind` を持たない作り方も残す |
| `domain/MenuFilterInput.java` | テーブルの組から種類を返す操作を足す（既存の 2 引数の作り方は常に `null` を返す形で残す） |
| `domain/MenuFilter.java` | 移れる項目を作るときだけ種類の操作を呼ぶ |
| `service/NavigationService.java` | 1 回読んだ DSL の `findTable`（`DslTable.view`）から種類を決める操作を作って渡す |
| `web/NavNodeResponse.java` | `tableKind` を足す（`toString` は変えない） |

### 画面
| ファイル | 内容 |
|---|---|
| `app/navigation/navIcon.ts`（新規） | `resolveItemIcon`（D10 の 4 手順・D13） |
| `app/navigation/navDestination.ts`（新規） | `firstNavigablePath`（D17） |
| `app/navigation/navigationApi.ts`・`buildNavSections.ts` | `tableKind` を読む（知らない値は `null`）／業務の項目のアイコンを `resolveItemIcon` で決める |
| `app/pages/homeCards.ts`（新規）・`HomeCard.tsx`・`HomeCardList.tsx`・`HomeCards.css`（新規）・`HomePage.tsx` | ホームの構成（作業ロールの 1 行・業務のカード・管理のカード・案内） |
| `app/layout/ShellLayout.tsx`・`ShellLayout.css` | 上のバーのロゴ（`AppIcon`）。480px 未満では隠す（4 節の (b)） |
| `app/i18n/messages/{ja,en}.ts` | ホームの文言 |
| `features/{useradmin,invitation,groupadmin,roleadmin,roletransfer,dsl}/registration.ts` | 管理の 6 項目のアイコン（`user`・`mail`・`users`・`shield`・`download`・`edit`） |
| `features/admin/`（全 11 ファイル） | 削除（`git rm` でステージ済み） |

### E2E・文書
`e2e/030`（ホームの管理のカードから開く流れ）・`090`（`/admin` は「見つからない」、実在の管理の道で 403）・`130`（20 組を DSL の管理の画面へ移す）・`160`（ホームのカード・アイコン・ロゴの axe とはみ出し）、`040`・`180`・`190`、`support/{adminLogin,userAdminDiagnostics,navTreeFixtures,businessDataFixtures}.ts`、`README.md`（ホームとアイコンの段落、スモークテストの文言）。ファイルの完全な一覧は `source-manifest.json`。

## 2. 主な実装の判断
- 種類の操作は `MenuFilter` の入力として渡し、`MenuFilter` は移れる項目を作る瞬間にだけ呼ぶ（見られない項目に印が付かない）。
- `registrationModules.ts` は触れない（実物は `import.meta.glob` で、`features/admin/` を消せば読み込みも消える）。
- 許すアイコンの一覧（`allowed-icons.txt`・`allowedIcons.ts`）は変えない（24 個でそろっている）。
- E2E の補助 `openSidebarItem` を `navigation` の領域に絞り、ホームのカードを押す `openHomeCard` を足した。

## 3. テストとカバレッジ（開発担当の実測。私は結果を読んだ。再実行は Build and Test）
| 項目 | 結果 |
|---|---|
| `navigation` のサーバー | 単体 69 件（9 クラス）・結合 30 件（9 クラス）、失敗 0 |
| 画面の全体（Vitest） | 268 ファイル・2,783 件通過（手を入れる前は 263 ファイル・2,750 件）。`tsc`・oxlint・ESLint・Stylelint・Prettier・`license:check` も通過 |
| E2E（`./gradlew e2eTest`） | 1 回目 261 通過・16 失敗（15 分 43 秒）。原因は 4 節の (a)〜(c)。直して、失敗した 8 ファイル（030・060・120・135・140・160・180・190）を再実行し 123 件通過（7 分 45 秒）。**全体を 1 本で通し直してはいない** |
| `:backend:cleanTest :backend:cleanIntegrationTest verify` | BUILD SUCCESSFUL、28 分 52 秒（42 分の線の内）。パッケージごとの下限を含めて通過 |
| カバレッジ（行 / 分岐） | `navigation.service` 100 / 100、`navigation.domain` 100 / 93.3、`navigation.web` 96.2 / 100。画面の全体 95.32 / 90.65 |
| スリープ | `caffeinate -dims` で包み、`pmset -g log` に今回の時間帯の Sleep・DarkWake は無し |

## 4. 計画との差・判断（依頼者に確かめたいものを含む）
- (a) ホームのカードと同名のリンクが増え、strict モード違反になった（030: ユーザーメニューのボタンが一覧の行の「操作」と同名。180・190: サイドバーのリンク）。040 のリンクの探し方をサイドバーに絞った。030・180・190・040 は計画の影響の範囲に無かったファイルで、小さな直し。
- (b) **ロゴが 360px の幅で横にはみ出した**（`scrollWidth` 372 > 360）。`ShellLayout.css` で 480px 未満のときだけロゴを隠した。機能設計 D27 の「全画面で左端に出す」との差で、「375px でも横スクロールを出さない」を優先した。**480px 未満でロゴを出したいなら、別の詰め方が要る（依頼者に確かめたい）。**
- (c) E2E 160 に、ホームのカードの状態（axe・はみ出し・アイコンが飾り・ロゴ）を足した。
- (d) `buildNavSections` の旧テスト 1 件を直した（一覧に無い `icon` の「まとまり」は `list` でなく D10 の手順 3 で `folder`）。
- (e) E2E の見本（`navTreeFixtures`・`businessDataFixtures`）と一部の単体テストの見本に、必須になった `tableKind` を足した。
- (f) 作業ロールの 1 行は `useMessages` にヘルパーが無いため、`react-i18next` の `useTranslation` を使った（既存の `ShellLayout` と同じ形）。
- (g) `forbiddenHeading.test.ts` の `/admin` は自前の見本の登録で、直していない。
- (h) **計画の Step 2（手を入れる前の基準）は未完。** 画面側は実測した（13 ファイル・137 件通過）が、サーバー側は先に本体を直し始めたため、手を入れる前の実測が無い（stash 等が禁止のため戻して取り直していない）。
- (i) **手順違反の記録**: 開発担当が `navigationApi.test.ts` への追記と新規テスト 4 ファイル（`navIcon.test.ts`・`navIcon.property.test.ts`・`navDestination.test.ts`・`navDestination.property.test.ts`）をシェルのヒアドキュメントで書いた（Write/Edit の指示に反する）。内容の確認はしている。
- (j) README の make-you-chic-ui の固定先の記載（`cae5fed`）が実物（`d112795`）と古いまま。範囲外なので手をつけていない。
- (k) 設計との差として、機能設計 `frontend-components.md` の「`registrationModules.ts` の読み込みを外す」は不要だった（計画の前提に記録済み）。

## 5. 検査の移しの件数（Build and Test で最終の記録をする）
| 項目 | 移す前 | 移した後 |
|---|---|---|
| E2E の本数 | 277 | 277（足した流れ・消した流れは無し） |
| `features/admin/` の単体テスト | 30 件（5 ファイルの `it`） | 0（呼ぶ画面が無くなったため外した。ホーム・サイドバー・「見つからない」の確かめは `realRegistrations`・`decideRoute`・`HomePage`・`ShellLayout` のテストへ移した） |
| E2E 130（権限が無い表示の axe） | 20 組（`/admin`、`/api/admin/check` の 403） | 20 組（DSL の管理の画面、`/api/admin/dsl/` の GET の 403） |

## 6. 書いたが、この段で実行していないテスト・未検証
- E2E 全体を 1 本で通した結果は無い（1 回目 261 通過 + 修正後の 8 ファイル 123 通過の合算）。Build and Test で全体を 1 回通す。
- 戻し（イメージだけ）、k6 の性能（`navMenu`・`navMenuBaseline`）、480px 未満でのロゴの見え方の人の目での確認。
- CI は未実行（`origin` への push は依頼者）。
- スモークテスト（S1〜S7）は配備の後の段。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 計画・手順: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/code-generation/code-generation-plan.md`・`unit-test-instructions.md`
- 設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u5-menu-icons-home/functional-design/functional-spec.md`・`nfr-design/`・`infrastructure-design/`
- 開発担当の報告（この会話の中）と、`git status` による変更の一覧の確認
- 進め方: `aidlc/spaces/default/memory/team.md`・`project.md`

## Assumptions & Open Questions

- [assumption] 開発担当の報告のテスト結果は、私が再実行して確かめたものではなく、報告の値を写した。変更の一覧だけ `git status` で突き合わせた（報告と一致）。
- 未解決の点: 4 節の (b)（480px 未満でロゴを隠す）の扱いを依頼者に確かめる。
