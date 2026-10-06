# NFR Design の質問 — U2 dsl-v2

単位 U2 dsl-v2（library。画面 `features/dsl` の小さな変更を含む）の NFR 設計の前に、決まっていない点を確かめます。読んだもの:

- この単位の承認済みの NFR 要件 `construction/dsl-v2/nfr-requirements/`（`security-requirements.md`・`tech-stack-decisions.md`・`traceability.json`）と、その読み直しの記録 `.aidlc-reviews/nfr-requirements/units/dsl-v2/0921d0fe911cb972/1.json`（直していない R-03〜R-09）
- この単位の承認済みの機能設計 `construction/dsl-v2/functional-design/`（`functional-spec.md`・`rules.md`）
- 契約 `inception/contract-design/contract-summary.md`（C3）、部品 `inception/domain-design/components.md`（DslDefinition・DslManagement・DslAdminUi）
- コード（読み取りだけ）: `dsl/parse/SafeYamlParser.java`・`LimitingParser.java`・`YamlTreeConverter.java`、`dsl/service/ActiveDslModelStore.java`・`DefaultDslReader.java`、`dslmanage/service/DslStartupLoader.java`・`DslLifecycle.java`、`frontend/package-lock.json`

library の単位のため、成果物は `security-design.md`・`logical-components.md`・`traceability.json` の3つです（段の定義の `produces_kinds`）。性能・信頼性・観測の設計は、`security-design.md` と `logical-components.md` の中で扱い、その扱いを冒頭に書きます。

## 決まっていること

### 部品の置き方（承認済みの機能設計・ADR-007・契約 C3）

- **安全な読み込みの口**: `dsl.service` に口（インターフェースと実装）を置き、上限の値（4つ）を持つ型を引数で受ける。
  - 既存の `SafeYamlParser`・`LimitingParser`・`YamlTreeConverter` は、上限を `DslFormat` の定数ではなく引数で受ける形に変える。
  - DSL の読み込み（`DefaultDslReader`）は `DslFormat` の値を渡す。
  - `dsl.parse`・`dsl.validate` は外へ出さない（`DslBoundaryArchitectureTest` を変えない）。
- **起動時の読み方**: `DslReader` に起動時の読み方を足す。意味の検証で深さの確かめだけを外し、深すぎる枝を落とす純粋な関数（`dsl.domain`）でモデルを作り、落とした数を添えて返す。読み直しの順と WARN の値は機能設計の BR2.3 と NFR5.3 のとおり。
- **読めないプレビュー**:
  - プレビューの表示の前と、適用で本文を履歴へ移す前に、読み直しを置く（BR3.3・BR3.4）。
  - どちらもキャッシュにモデルが無いとき（再起動の後）だけ読み直す。そのため、ふだんの適用（キャッシュがある）の時間は変わらない（NFR2.6 の適用の 95% 1 秒に影響しない）。

### 手当てする読み直しの指摘（承認済みの文書は書き換えず、設計に書く）

- **R-05 安全な読み込みの口の入力**: 本文は `byte[]` で受ける。`TraceAspect` は引数を文字列にするが、バイト列は `[B@…` の形で中身が出ない。TRACE のテスト（NFR1.11）で、結果だけでなく入力の側も目印の文字列がログに出ないことを確かめる。
- **R-06 別名の爆発と時間**:
  - 別名の爆発する入力（少ない別名で多数に展開する形）が、DSL の上限の値（展開後の節 1,000,000）と小さな上限の値のどちらでも、数秒の内に拒否されることを確かめるテストを1件ずつ足す。時間の上限は試し（下の「捨ての試し」）の実測から決める。
  - 別名 0 を SnakeYAML の `LoaderOptions` に渡したときの振る舞いは、捨ての試しで確かめる。
- **R-03 大きさの試算の式**: 試算の式を設計に書く。前の Intent の 1 カラム約 550 バイト × 10,000 カラム ＝ 約 5.3 MB に、字下げの増分（1 行 4 バイト × 1 カラムあたりの行数 × 10,000）とメニューの組の分（100 項目）を足す。実測は Build and Test のまま（NFR2.7）。
- **R-04 道具の書き換えの持ち主**:
  - 書き換えるのは `perf/dsl-timing.sh`・`make-large-dsl.mjs`・`make-pattern-dsl.mjs`・`perf/ui/*`・k6 の `perf/k6/scenarios.js` の DSL の入力。
  - B1 のコード生成で版 2 に書き換え、Build and Test と Performance Validation はそれを流すだけにする。
  - 機能設計の `functional-spec.md` の 8節の一覧と同じ扱い。
- **R-07 配備の段への引き継ぎ**: 「版 1 の適用中の DSL がある内部DB で入れ替えると、権限の対象と業務のメニューが空になる。入れ替えの後に版 2 の既定の DSL を生成し、プレビューを確かめて適用する」を、引き継ぎに1行足す（戻しは Q2）。
- **R-08 画面の道具の版**: `frontend/package-lock.json` の版を書く。fast-check 4.10.2・Vitest 5.0.2・vitest-axe 0.1.0・Testing Library React 16.3.3・user-event 14.6.7。
- **R-09 対応表の表記の不整合**: `tech-stack-decisions.md` の対応表の NFR1.5 の行は「NFR1.7〜NFR1.12」と範囲で書くが、正しい並びは `security-requirements.md`・`traceability.json` のとおり NFR1.7〜NFR1.10・NFR1.12・NFR1.14 である。NFR1.13（接続情報）は NFR1.6 の行の「部分」に、接続情報の守りとして含める。どちらも設計の文書の「上流との差」に記録し、承認済みの文書は書き換えない（`project.md` の決まり）。

### 捨ての試し（この段で、リポジトリの外で行う。`project.md` の学び）

部品の振る舞いに頼る設計の前提を、リポジトリの外の捨てのコード（SnakeYAML 2.7・networknt 3.0.6、`backend/gradle.lockfile` と同じ版）で先に確かめ、結果の要点（版・設定・数値・設計への意味）を成果物に写します。時間の上限は合わせて 30 分で、超えたら止めて分かった範囲を記録します。試しはまだ行っていません。

1. 別名 0 を `LoaderOptions.setMaxAliasesForCollections(0)` に渡したとき、対応表を指す別名1つで、`LimitingParser` の拒否（位置つき）が先に出るか、部品の例外が先に出るか。文字の値を指す別名は通るか。
2. 別名の爆発する入力（例: 9 段の別名で約 10 億の節に展開する形）を、展開後の節 1,000,000 と小さな上限で読ませたときに、拒否までにかかる時間とヒープ。
3. 版 2 の書式（`schemas` → スキーマ → `tables` の入れ子と `propertyNames`）で、10 MiB の DSL の構文の検証にかかる時間が、版 1 の形と比べて大きく変わらないか（NFR2.5 の 10 秒の内訳の見込み）。

質問は2問です。

---

## Q1 「適用中の DSL を今の書式で読めない」の印の決め方

背景: 機能設計の BR3.6 は、今の状態の応答の `appliedUnreadable` を「内部DB に適用中の版があり、提供口が Absent、または提供口の識別が適用中の識別と違う」ときに真とします。ただし、識別を比べる形には落とし穴があります。適用の確定の直後から提供口を差し替えるまでの短い間に今の状態を読むと、適用したばかりの正しい DSL でも、一瞬だけ真（読めない）と出ます。適用の応答そのものは差し替えの後に今の状態を作りますが、別の管理者の画面や同時の読み取りは、この間に当たりえます。新しいデータを持たずに、この誤りを避ける形を決めます。

A. 起動時の読み直しで読めなかった版の識別（revision の ID）を、`dslmanage` の中の小さな置き場（メモリ）に覚える。`appliedUnreadable` は「今の適用中の版の ID が、覚えた ID と同じ」ときだけ真にする。新しい版を適用すれば ID が変わるため、自然に偽になる。提供口と `ActiveDsl` の形は変えない（推奨: 適用の途中の間に誤って真にならない。記録は起動のたびに作り直すため、内部DB の表は変えない。判定が「起動時に読めなかった」という BR3.6 の意味そのものになる）
B. BR3.6 のとおり、提供口の状態と識別を、今の状態を返すたびに比べる。適用の途中の一瞬の誤りは受け入れる（画面は次の読み直しで直る）
C. 提供口（`ActiveDsl`）に「読めなかった」の状態を足し、`Present`・`Absent` に並べる（契約 C3 の形を変えるため、U4・U5 の受け入れを確かめ直す必要がある）
X. Other (please specify)

[Answer]: A **Mode:** guided

## Q2 前の版のアプリへ戻したときに残る版 2 のプレビュー

背景: ストーリーの AC6.1.8 と要件との差 D2 は、前の版のアプリへ戻したときの扱いを、配備の段（戻しの手順と練習）に引き継いでいます。コードを確かめたところ、前の版のアプリには、この単位で足す「読めないプレビューの読み直し」（BR3.3・BR3.4）が無いことが分かりました。このため、版 2 のプレビューが内部DB に残ったまま戻すと、前の版のアプリではプレビューの表示が想定外の失敗（500）になり、適用は本文を履歴へ移した後に失敗します（`DslLifecycle` の `readValid`）。前の版のアプリは変えられないため、戻しの手順で避けるしかありません。どの手順を配備の段へ引き継ぐかを決めます。

A. 戻す前に、DSL の画面でプレビューを破棄する（プレビューがあれば）手順を、戻しの手順の最初に置く。戻した後は前の版で既定の DSL を生成し直して適用する（AC6.1.8）。手順の確かめ（戻しの練習）で、前の版の起動・DSL が無い状態・生成し直しの3つを確かめる。この内容を `logical-components.md` の「配備の段への引き継ぎ」に書く（推奨: データを消さずに済み、管理者の操作1つで 500 を避けられる。前の版のコードに手を入れずに済む）
B. A に加えて、版 2 の適用中の DSL も戻す前にダウンロードして手元に残す手順を足す（戻した後の作業には使えないが、もう一度上げるときに投入し直せる）
C. 手順を置かず、戻した後に 500 になったら破棄するよう、戻しの手順の注意に書くだけにする
X. Other (please specify)

[Answer]: A **Mode:** guided

## Consolidated Summary Confirmation

答えのまとめ（dsl-v2 の NFR 設計）:

- Q1 A: 起動時の読み直しで読めなかった版の ID を `dslmanage` の中（メモリ）に覚え、`appliedUnreadable` は今の適用中の版の ID が覚えた ID と同じときだけ真にする。提供口・`ActiveDsl` の形と内部DB の表は変えない。機能設計 BR3.6 の判定の形との差として成果物に記録する（承認済みの機能設計は書き換えない）。
- Q2 A: 前の版のアプリへ戻すときは、戻す前にプレビューがあれば DSL の画面で破棄し、戻した後に前の版で既定の DSL を生成し直して適用する。戻しの練習で、前の版の起動・DSL が無い状態・生成し直しの3つを確かめる。この内容を `logical-components.md` の「配備の段への引き継ぎ」に書く。
- 捨ての試しは、リポジトリの外で合わせて 30 分までとし、別名 0 を LoaderOptions に渡したときの拒否の順・別名の爆発する入力の拒否までの時間とヒープ・10 MiB の版 2 の DSL の JSON Schema の検証の時間の3点を確かめ、結果の要点を成果物に写す。
- 決まっていること（部品の置き方、読めないプレビューの読み直しはキャッシュに無いときだけ、読み直しの記録で直していない指摘 R-03〜R-09 の設計での手当て）は案のとおり。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
