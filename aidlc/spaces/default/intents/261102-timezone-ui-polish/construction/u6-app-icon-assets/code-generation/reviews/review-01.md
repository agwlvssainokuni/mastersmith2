## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-03
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | frontend/scripts/check-dist-assets.mjs > checkIconLinks（64〜74 行） | dist/index.html の favicon の link は、rel と href を引用符付きの形だけ読む。引用符なしの rel や、rel の無い link は黙って対象から外れる。今は Vite が出力する形なので実害は無いが、検査としては fail-closed ではない。 | 必須ではない。直すなら、引用符なしの link 属性を見つけたら問題として報告する。直さないなら現状（Vite の出力に限る前提）を docs/app-icon.md に一言書く。 | New |
| R-02 | Minor | backend/src/test/resources/static/favicon.svg・favicon.ico | バックエンドのテスト用の見本は frontend/ の外にあるため、SVG の中身の検査（svg:check）の対象外。内容は 8 px の小さな図と 92 バイトの ICO で安全だが、将来ここへ別の SVG が足されても検査は働かない。 | 対応は不要。足す場合は docs/app-icon.md に、テスト用の見本は検査の対象外だと書く。 | New |
| R-03 | Minor | frontend/src/shared/app-icon/AppIcon.tsx 26〜29 行・frontend/public/app-icon.svg 16 行 | 図柄の d が 2 か所に書かれている。AppIcon.test.tsx の一致テストが守るので正しさは保たれているが、U5 のロゴが別に写すと 3 か所になる。 | U5 は APP_ICON_PATHS を import して使う（写さない）ことを U5 の計画で確かめる。 | New |
| R-04 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u6-app-icon-assets/code-generation/code-summary.md（Unverified） | verify 全体・e2eTest 全件・SpotBugs・OSV-Scanner・Gitleaks は未実行と正直に記録されている。このレビューも git・gradlew・npm を使わず、テストの実行結果は code-summary.md の記述を信頼しただけで、独自には再確認していない。 | 統合の前に、呼び出し元が verify と e2eTest の全件を流して件数を記録する。team.md の「frontend/public/ に触れるブランチは統合の前に E2E」に当たる。 | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| check-svg-safety.mjs の読み取り確認 | 状態機械は SD1.1〜SD1.7 のとおり。大文字小文字は完全一致、名前空間の接頭辞・コメント・CDATA・処理命令・DOCTYPE・ENTITY・属性値の中の >・文字参照・data: ・href・style・class はすべて拒否される。 | 抜け道は見つからなかった。最初の違反で止まり、コードと位置だけを返す。 |
| svgSafetyCheck.test.ts の表（悪い例 70 件超） | 各行が違反の種別のコードまで確かめている（toMatchObject の code）。良い例の通過と、1 つ足すと落ちる境界、大きさ・深さの境界、時間の確かめもある。 | 常に通るテストではない。fast-check の性質ベースと jsdom の厳密な XML パーサーとの突き合わせも付いている。 |
| 素材 | favicon.svg 806 文字・app-icon.svg 811 文字・favicon.ico 2085 バイト。HTML コメント形のヘッダー、XML 宣言なし、script・on*・外部参照なし。 | 上限（SVG 4 KiB・ICO 32 KiB）の内側。ICO の作り方は docs/app-icon.md に道具・版・コマンドで再現できる形で書かれている。 |
| ビルドの組み込み | frontendSvgCheck は verifyLicense、frontendDistCheck は frontendBuild の後で verifyBuild、verifyFaviconInWar は verifyArtifact。frontendBuild の inputs に public/ を追加。依存・SpotBugs 除外・カバレッジ除外・lint の規則・vitest.config は変更なし。 | 設計の SD6 どおり。検査はどれも失敗で verify を止める形で、緩めた箇所は見つからなかった。 |
| AppIcon・index.html・LoginLayout | aria-hidden・focusable=false・currentColor。props は size のみ。dangerouslySetInnerHTML・className・style なし。link は .ico が先・SVG が後の 2 本。独自の色なし。 | SD5 どおり。 |
| StaticIconAssetsIT・E2E 050 の書き足し | 未認証で 200・image/・本文が HTML でない・nosniff・no-cache・素材が無い道は index.html に落ちる。E2E は ICO の先頭 4 バイトまで確かめ、新しい test() は足していない。 | 設計の前提（SD4.2 の L1・L4）を実際に確かめている。 |
| 計画との差 | ブランチ名、svgFixtures.ts の追加、no-script-url への対応（規則を外さず文字列を組み立てる）は code-summary.md に記録され、妥当。 | 隠れた逸脱は見つからなかった。 |

### Summary

SVG の許可リスト検査、素材、ビルドの組み込み、配信の確かめは設計（SD1〜SD7）どおりで、検査を緩めた箇所もなく、READY とする。残るのは Minor の改善提案のみで、verify と e2eTest の全件は統合の前に呼び出し元が流して確かめる。
