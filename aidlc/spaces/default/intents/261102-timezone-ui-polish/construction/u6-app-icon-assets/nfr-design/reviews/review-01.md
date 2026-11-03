## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-03
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u6-app-icon-assets/nfr-design/security-design.md > SD6 と SD4.2 L3 の verifyFaviconInWar | 新しい検査 verifyFaviconInWar を「build.gradle.kts の Gradle のタスク」と書くが、隣に置くとした verifyDslSchemaInWar は backend/build.gradle.kts にあり、verifyArtifact の一覧は根の build.gradle.kts にある。frontend/public/ はバックエンドのプロジェクトから見て根の側のパスで、どちらに置くか・public/ をどう参照するか（rootProject.layout.projectDirectory）が決まっていない | 置き場を backend/build.gradle.kts に定め、verifyArtifact の一覧へ backendProject.tasks.named で加えること、public/ は根のプロジェクトのパスで参照することを SD6 に書く | New |
| R-02 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u6-app-icon-assets/nfr-design/security-design.md > 上流との差の 4・7、SD3、SD6、Assumptions | コード生成の計画で必ず拾うべき作業（ICO を rsvg-convert と Node で詰める、frontendBuild の inputs に frontend/public/ を足す、check-license-header.mjs の collectFiles・SKIP_DIRS を export する、素材の最終の名前と点数の決定）が、複数の節に分散している。traceability.json は上流の NFR ID だけを持ち、これらの作業は持たないため、計画の段で読み落とす余地がある（collectFiles と SKIP_DIRS は現状 export されておらず、実物の check-license-header.mjs の変更が要る） | 末尾に「コード生成へ引き継ぐこと」の一覧を1つ置き、各項目に SD の番号・持ち主・確かめ方を書く。collectFiles と SKIP_DIRS が現状は export されていないことも明記する | New |
| R-03 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u6-app-icon-assets/nfr-design/security-design.md > SD1.2.1 | SVG の長さの上限 4096 文字は、ヘッダー（約 560 文字）を含む1ファイル全体にかかる。図柄に使える量は約 3.4 KiB で、金床とハンマーの輪郭が収まるかは未確認。収まらないと、検査の上限を広げる判断が NFR2.1.1 の変更になる | 図柄の確定（コード生成の計画）で、ヘッダー込みの大きさを先に測り、収まらないときは上限を広げる前に依頼者に諮る旨を SD1.2.1 か Assumptions に1行足す | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| SD1 の抜け道の机上確認（大文字小文字・接頭辞・コメント・CDATA・属性値の中の `>`・処理命令・文字参照・data URI・DOCTYPE と ENTITY・href を外したこと） | すべて許可リストか値の文法で落ちる | 名前の完全一致、`<?` と `<!` の全拒否、値の文法が `& < > :` を許さないため、切り出しがずれても通らない。テストの種（SD1.6・SD7.1・SD7.1a・SD7.1b）も足りる |
| 実物の確認: check-license-header.mjs・config/license-header.txt | 一致 | HTML 形のひな形に `--`・`<`・`&` が無く、SD1.3 の先頭コメントの決まりと両立する。HTML_EXTENSIONS に .svg を足せば SD2 は成り立つ |
| 実物の確認: SecurityConfig・WebConfig・CacheControlFilter・application.yaml | 設計の読みと一致 | anyRequest().permitAll()、contentTypeOptions、SpaFallbackResourceResolver が API と actuator 以外の見つからない道に index.html を返すこと、/assets/ 以外は no-cache、CSP の img-src 'self' data:、add-mappings:false はいずれも設計のとおり |
| 実物の確認: build.gradle.kts の frontendBuild の inputs・verifyArtifact・backend の verifyDslSchemaInWar | 設計の指摘どおり | inputs に public/ が無い抜けは実在し、SD6 の対処が必要。.gitattributes の `*.png binary`・`*.ico binary` も既にある |
| 実物の確認: Playwright の設定 | 一致 | E2E は組み上がった WAR を java -jar で起動するため、L4 は本物の dist・WAR・セキュリティの決まりを通る |
| traceability.json と本文の突き合わせ | 一致 | NFR1.1・3.1・3.2・5.3 は OK、NFR2.1 は N/A で NFR2.1.1 を別に扱う（前の段の R-03 を解いている）。他の単位の枝番は traceability.json の N/A の行にだけ出て、本文の散文には出ない |
| 未実施 | 未実行 | git・gradlew・docker は使わない指示のため、L1〜L4 と各検査の実行はしていない。コードの読みだけの確認 |

### Summary

前の段の Major 2 件は解けている。SD1 の許可リストと fail-closed の走査は、列挙された抜け道に耐え、実装できる粒度でテストの種も足りる。L1〜L4 は役割が重ならず、K-102 の「見つからないと index.html が 200 で返る」を L2・L3 で検出し、L4 で本物の WAR を通す形になっていて、実物のコードの読みも正しい。Minor 3 件（verifyFaviconInWar の置き場、コード生成への引き継ぎ一覧、SVG の大きさの余裕）は設計の補足で直せ、判定は READY。
