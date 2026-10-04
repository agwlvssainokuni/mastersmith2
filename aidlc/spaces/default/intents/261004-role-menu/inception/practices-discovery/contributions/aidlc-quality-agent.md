**Collaborator:** aidlc-quality-agent

## Contribution

品質担当（aidlc-quality-agent）の独立した確かめ。リードの下書き（`team-practices.md`・`discovered-rules.md`・`evidence.md`）を、`aidlc/spaces/default/memory/team.md`・`project.md`、コード知識ベース（`aidlc/spaces/default/codekb/mastersmith2/` の `code-quality-assessment.md`・`architecture.md`・`api-documentation.md`）、`backend/build.gradle.kts`（カバレッジの節）、`frontend/vitest.config.ts`・`frontend/playwright.config.ts`・`frontend/package.json`、`frontend/e2e/` の一覧、`backend/src/test/java/cherry/mastersmith/access/` の一覧と突き合わせた。gradlew・docker・npm は実行していない。件数はファイルの名前で数えた値で、テストを実行した値ではない。

### 1. 基準と今の設定の突き合わせ（品質の範囲）

| 項目 | 基準（team.md） | 今の設定 | 判定 |
|---|---|---|---|
| Methodology・Ordering | test-after、層ごとに実装→その層のテスト | 変える根拠は見当たらない | 一致。変更の候補にしない |
| バックエンドのカバレッジ | 全体とパッケージごとに行 80%・分岐 70% | `jacocoTestCoverageVerification` に全体の rule とパッケージの rule（`excludes = packagesJudgedByTotal`）の2つ。値は 0.80・0.70 | 一致 |
| `packagesJudgedByTotal` | 2026-10-04 の時点で 7 個 | `backend/build.gradle.kts` の一覧は同じ 7 個（`access.service` を含む）。コメントの外した履歴も team.md と合う | 一致 |
| 計測の除外 | 起動クラス・設定値だけのクラス・自動生成・`vendor/` に限る | `coverageExclusions` は `MastersmithApplication*`・`*Properties.class`・`*Properties$*.class` の3つだけ | 一致 |
| 画面のカバレッジ | 行 80%・分岐 70% | `vitest.config.ts` の `thresholds` は lines 80・branches 70。除外は `src/main.tsx`・`*.d.ts`・テストのファイル | 一致（画面はパッケージごとの下限を持たない。team.md もバックエンドだけに当てる読み方で、食い違いではない） |
| E2E | Playwright・`verify` と CI の外・1つのアプリと内部DB を共有して順に流す | `playwright.config.ts` は `workers: 1`・`fullyParallel: false`・`retries: 0`・`forbidOnly: true`。13 本（010〜130） | 一致 |
| 性質ベースのテスト | jqwik・fast-check を純粋な関数に一部 | jqwik を使うテストのファイル 25、fast-check を使う画面のファイル 18（検索） | 一致 |

リードの「食い違いは見つからなかった」に同意する。

### 2. リードの候補への意見（品質に関わるもの）

- **P1（役割・権限の必須のテスト）**: 足すことに賛成。リードの案に、次の4つを足すことを勧める（品質の見地で穴になりやすい点）。
  - Q-1 **認可の網羅の検査**: 今の認可のテスト（`access/web/AdminAccessIT`・`AdminPathBoundaryIT` など）は API を人が列挙しており、新しい API に認可の行を書き忘れても落ちる検査が無い（`RequestMappingHandlerMapping` などで API を数え上げるテストは見当たらない）。役割を入れると「どの API にどの権限が要るか」の組が増えるため、アプリの API をすべて数え上げ、期待する権限の表に無い API があれば落ちるテストを1つ置く（表に無い API は既定で拒否されることも確かめる）。表の持ち方（テストの中か、本番の定義を読むか）は設計で決める。
  - Q-2 **いちばん近い権限での 403**: 「その権限を持たない（403）」を確かめる利用者は、権限が何も無い利用者ではなく、**要る権限だけを欠き、ほかの権限をすべて持つ利用者**にする（権限が1つしか無い今の 2 値の形では区別が無いが、役割が増えると、何も持たない利用者での 403 は判定の誤りを見逃す）。API ごとの組は、パラメーターを使うテストで表として書く。
  - Q-3 **権限の解決とメニューの絞り込みの性質ベースのテスト**: 役割から権限の集合を求める関数（サーバー側）と、権限でメニューの枝を落とす関数（画面側）は純粋な関数にし、jqwik・fast-check で性質（持たない権限の API・項目が出ない、役割を足しても権限は減らない、など）を確かめる。P4 の (4) と同じ考えをサーバー側にも当てる。
  - Q-4 **既存の 2 値のテストの読み替えの表**: 既存の `AdminTestUsers`・`PublicApiTestRules`（`access/testsupport/`）と、認可・管理者の印・最後の管理者の保護の結合テストは「管理者なら 200・そうでなければ 403」を前提にしている。権限の形が決まった後のコード生成の計画で、既存のテストの一つずつを「そのまま・読み替え・置き換え」に振り分けた表を作り、消すテストがあれば代わりのテストを示す（テストを消して網羅を下げない）。
- **P2（既存の文言の扱い）**: 推奨 A に条件つきで賛成。条件は、P1 をこの段で足すこと（足せば、文言を書き換えるまでの間も役割・権限の必須のテストが Testing Contract に入る）と、コード生成の計画に「管理者フラグ」「管理者の印」の読み替えを明記すること（Q-4 の表と同じ場所）。P1 を足さないなら B（今の文言を「その権限を持たない」に一般化）を勧める。一般化した文言は、権限の形がどちらに決まっても成り立ち、コード生成の時点の Testing Contract が古い前提のまま残らないため。
- **P3（E2E の本数と役割を変える操作）**: 推奨 A に賛成。足すことを勧めるのは1点。
  - Q-5 既存の E2E（`030-admin-access`・`110-user-admin-flow`・`130-admin-forbidden-accessibility` など）を新しい権限の形に合わせて**書き換えることは、本数に数えない**ことを明記する（project.md の学び「アクセシビリティの検査や画面の時間の測りは本数に数えない」と同じ扱い）。数えると、書き換えのために新しい代表の流れを足せなくなる。今回の Intent は画面と認証の両方に触れるため、E2E は Bolt ごとの統合の前に流す（既存の決まりどおり）。
- **P4（N 階層のメニューのテスト）**: 推奨 A に賛成。足すことを勧めるのは1点。
  - Q-6 P5 で make-you-chic-ui 側に入れ子のサイドバーを足してもらう場合、`vendor/make-you-chic-ui` のテストはこのリポジトリの CI の対象外（team.md の Deployment）である。そのため、このリポジトリが頼る振る舞い（開閉・`aria-current`・キーボード・言語に合わせた `aria-label`）は、上流のテストに任せきらず、このリポジトリの画面のテスト（jsdom で同梱版の部品を描く）と実際のブラウザの axe で少なくとも一度確かめる。
- **P6（`access` の境界テスト）**: 推奨 A に賛成。境界テストが無い既存の機能は今 `access` だけ（K-38）なので A と B の実質の差は小さいが、決まりとして残すほうが後の Intent で迷わない。
- **P7（DB の後方互換）**: 推奨 A に賛成。足すことを勧めるのは1点。
  - Q-7 既存の行を移す移行（例: `admin_flag` が真の利用者に管理の役割を与える）は、移行の前の版の表に行を用意して移行を当て、移行の後の行を確かめる結合テストを書く。あわせて、移行の後に**前の版のアプリが使う列**（`admin_flag` など）が一貫したまま読めることも確かめる（後方互換の決まりをテストで裏付ける）。今は表の形を確かめるテスト（`user/repository/UserSchemaIT` など）はあるが、行の移行を確かめる形のテストは見当たらない。
- **P5・P8・P9**: 品質の関門には直接関わらない。P8 の候補を足す場合は、P1 の必須のテストがその確かめになる（ALWAYS のサーバー側の判定は Q-1・Q-2、昇格の防止は P1 の「権限の昇格の防止」）。

### 3. 面談で決める必要がある穴（リードの候補に無いもの）

上の Q-1〜Q-7 のうち、P1・P3・P4・P7 の中に入れられないものは無い。新しい問いを立てるより、各候補の選択肢 A の案文に取り込むことを勧める。問いにするなら、次の2つに絞る。

- Q-1 の「認可の網羅の検査」を Testing Posture の決まり（今回以降のすべての API に当てる）にするか、この Intent の要件・設計だけで決めるか。
- Q-5 の「既存の E2E の書き換えは本数に数えない」を team.md に書くか、project.md の学びとして残すか。

### 4. 要件・設計の段に回す論点（品質の見地での追加）

- 権限を要求ごとに DB から読む今の形（K-32）を、役割と権限の表を結ぶ読み出しに変えると、すべての要求の時間に乗る。応答時間の目標と、k6 で測る場面（ログインと、権限の判定が要る API）、持ち主の段（Performance Validation が無ければ Build and Test。project.md の学び）を要件・NFR 要件で決める。
- Q-1 の表の持ち方、既定で拒否する仕組みの置き場、403 の理由の値は設計で決める。
- `access.service`（`packagesJudgedByTotal` に入っている）に手が入る見込みが高い。手が入れば team.md の決まりでテストを足して一覧から外す作業が付くため、Delivery Planning で今の値を実測して見積もる（project.md の学びのコマンド）。
- メニューの深さの上限の値と、その境界のテスト（上限ちょうど・超え）は、DSL の「信頼できない入力」のテストの決まりに沿って設計で決める。

## Positions

- AGREE: 基準（team.md）と今の設定に食い違いが無いこと — カバレッジの rule・一覧・除外・画面の `thresholds`・E2E の設定をそれぞれ確かめて合っていた。
- AGREE: Methodology（test-after）と Ordering を変えないこと — 今回の Intent に変える根拠が無く、Q-1 の網羅の検査で認可の書き忘れは拾える。
- AGREE: P1 を足すこと（Q-1〜Q-4 を案文に足して） — 2 値の前提のままでは、役割ごとの 403 の誤りと新しい API の認可の書き忘れを見逃す。
- AGREE: P2 の A（P1 をこの段で足す条件つき。足さないなら B） — P1 が入れば Testing Contract が古い前提のまま残らない。
- AGREE: P3 の A（Q-5 を添えて） — 束ねた元の Intent ごとの数え方は既存の決まりの趣旨に合い、既存の E2E の書き換えを本数に数えないことで書き換えが新しい流れを締め出さない。
- AGREE: P4 の A（Q-6 を添えて） — 上流の部品のテストはこのリポジトリの CI の対象外のため、頼る振る舞いはこちらでも確かめる必要がある。
- AGREE: P6 の A — 境界テストを足すのは検査を緩めることにならず、決まりとして残すほうが後で迷わない。
- AGREE: P7 の A（Q-7 を添えて） — 後方互換の決まりは、行の移行と前の版が読む列をテストで裏付けて初めて守れる。
