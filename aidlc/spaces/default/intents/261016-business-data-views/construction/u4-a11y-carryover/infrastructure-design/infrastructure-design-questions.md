# Infrastructure Design の質問 — U4 u4-a11y-carryover

U4 は ui の単位です。配る形は変わりません。画面は `frontend/dist` を WAR に同梱したまま配ります。

## 前の段から持ち越した論点

- NFR 設計の「後の段に回すこと」は、すべてコード生成（B3・B7）が持ち主です。
- 基盤に関わる論点は次の3つです。
  - 検査の時間の記録
  - 依存と固定先の手順
  - NFR 設計のレビュー R-02（鍵の関数が vitest の対象に載らない）
- R-02 は「コード生成の計画で直す」と決めました。ただし、直し方によっては CI が流す `frontend/vitest.config.ts` が変わるため、ここで確かめます（→ Q2）。
- Dependabot の axe-core の `ignore` を B7 で外すことは、決まっています（bolt-plan）。外した後に axe-core の更新をどう受けるかは、決まっていません（→ Q1）。

## 決まっていること（質問にしない）

- **配る形**
  - バックエンド・内部DB・compose・Dockerfile・`.env.example`・`application.yaml` は変えない。戻すときはイメージだけを戻す。
  - **成果物に入るもの:** L1（行の操作の名前の関数）と L2（トップバーの名前）はアプリのコードで、`dist` に入る（NFR 設計のまとめの確認の文言と違う点。レビューの R-06）。
  - **成果物に入らないもの:** L3〜L6 と axe-core は入らない。`dist` に axe-core が無いことを、文字列の検索で確かめる。
- **固定先 a9309c4 → b0a1e79（B3）。** 読み取りで次のことを確かめた。
  - b0a1e79 は直後の1コミットで、手元の `origin/main` の先頭にある。
  - 差は Dropdown の3ファイルだけ。`package-lock.json`・`package.json` には差が無く、`hasInstallScript` の項も増えていない。
  - B3 の最初に、依頼者の承認を得て `git fetch` し、上流の `main` から辿れることを確かめ直す。
  - 統合は fast-forward で、専用のコミットにする。
- **axe-core**
  - 今は frontend の lockfile に 4.13.0 が1つだけ入っている（dev）。`vitest-axe` も同じものを使う。
  - 上げると、CI の画面のテストも新しい版で流れる。ただし jsdom ではこの規則を判定できないため、結果は変わらない見込み。
  - `ignore-scripts=true` のまま入れる。
- **CI**
  - `ci.yml` は変えない。
  - verify と CI の時間は、B3・B7 の後に試行ごとに記録する。42 分を超えたら諮る。
- **E2E**
  - B3 は統合の前に全件を流す。
  - B7 は E2E の全件と vitest-axe を流し、違反が 0 件であることを件数で記録する。
  - `caffeinate -dims` を付けて流す。
  - 時間の目安は 1.5 倍（約 15分53秒）で、記録の置き場は `generation-notes.md` と `test-results.md`。
- **監視:** 画面の側の監視は置かない。
- **上流との差**
  - NFR 設計は、Playwright の 120 秒を E2E ごとの上限と書いている。実際には、120 秒は webServer の起動の待ちで、テストごとの上限は各ファイルの `test.setTimeout`（120〜240 秒）にある。
  - これを差として記録する。

## Q1. B7 で `ignore` を外した後の、axe-core の更新の受け方

team.md では、依存の版と lockfile だけの更新は `./gradlew verify` だけで統合する。一方、axe-core を上げたときは、E2E と vitest-axe の両方を流すことを求めている。axe-core は小さな版の更新でも、既定で流れる規則が増えうる（4.14 がその例）。

A. `ignore` を外し、すべての版を知らせる。axe-core の更新だけは、lockfile だけの変更でも、統合の前に E2E の全件を流す（team.md の axe-core の決まりを優先する）。このことを `.github/dependabot.yml` のコメントに書く（推奨）。

B. 小さな版の更新（4.15 など）は `ignore` に残し、パッチの更新だけを知らせる。新しい規則は、計画した Intent でだけ取り込む。外す時期をコメントに書く。

C. `ignore` を外し、ほかの依存と同じく verify だけで統合する。E2E は Intent の最後とリリースの前に流す。

X. Other (please specify)

[Answer]: A

## Q2. 鍵の関数（L3）の置き場と、vitest の対象（R-02）

今の `vitest.config.ts` の対象は `src/**` だけで、`e2e/**` は外している。`axe.ts` は読み込んだ時点でファイルを読むため、単体のテストからはそのまま読み込めない。

A. 鍵の関数を、外の名前に頼らない純粋な関数として `frontend/e2e/support/` の別のモジュールに置く。vitest の対象に `e2e/support/**/*.test.ts` を足し、`exclude` をその分だけ狭める。カバレッジの対象と除外は変えない。CI では画面のテストの中で毎回流れる（推奨）。

B. `frontend/src/` の検査用の置き場に置き、vitest の設定は変えない（アプリは import しないため `dist` には入らない。ただし frontend のカバレッジの合計には入り、src に E2E 用のコードが混ざる）。

C. 単体の確かめを置かず、E2E の中の確かめだけにする（R-02 の推奨から外れる）。

X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

**infrastructure-specification**
- 画面は SPA のまま、WAR に同梱して配る。バックエンドと設定は変えない。戻しはイメージだけ。
- `dist` に入るもの・入らないもの
  - 入る: L1・L2。
  - 入らない: L3〜L6 と axe-core。入らないことは文字列の検索で確かめる。
- 固定先 b0a1e79 の手順
  - 辿れること・lockfile の差・`hasInstallScript`・前後のハッシュを確かめる。
  - 今回読み取りで確かめた差は、Dropdown の3ファイルだけ。
  - B3 の最初に、承認を得て fetch する。
- 共有するもの
  - 表示の組 20 組
  - L1・L5（U3 と共有）
  - E2E の WAR・内部DB・初期管理者

**monitoring-design**
- 画面の側の監視は置かない。
- 記録する値
  - E2E の件数と時間（目安 1.5 倍）
  - 区分ごとの passes・incomplete・violations
  - verify・CI の時間
- 警報と SLO は当たらない。

**cicd-pipeline**
- CI は変えない。
- 鍵の関数は、外の名前に頼らない純粋な関数として `e2e/support/` の別のモジュールに置く。vitest の対象に `e2e/support/**/*.test.ts` を足す。カバレッジの対象と除外は変えない（Q2: A）。
- axe-core の更新
  - Dependabot の `ignore` を外した後は、すべての版を知らせる。
  - axe-core の更新だけは、統合の前に E2E の全件を流す。そのことを `dependabot.yml` のコメントに書く（Q1: A）。
- B3
  - fast-forward で統合する。
  - 統合の前に E2E の全件を流す。
  - 4.14 は作業フォルダでだけ試し、4.13 に戻したことを差で確かめる。
- B7
  - `REQUIRED_RULES` に `label-content-name-mismatch` を足す。
  - E2E の全件と vitest-axe を流し、違反 0 件を件数で記録する。
- 秘密の検索に、U3 の見本の値を足す。
- 上流との差: 120 秒は webServer の起動の待ちで、テストごとの上限は各ファイルの `test.setTimeout`。

**traceability**
- U4 の NFR の 23 件に行を立てる。
- 基盤に当たらないものは N/A とし、確かめの置き場を書く。

Does this all look correct before I generate the infrastructure design?

- Looks correct
- Request changes

[Answer]: Looks correct
