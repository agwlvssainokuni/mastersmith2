# CI Pipeline の質問

CI の仕組み（GitHub Actions の `.github/workflows/ci.yml`、`./gradlew verify` の1コマンド）は前の Intent から実装済みで、この Intent では変えていない。この段の文書は、新しい設計ではなく、今あるものとこの Intent の差の記録として書く（project.md の学び）。

## 決まっていること（質問にしない）

- CI の道具・きっかけ・段の並び: GitHub Actions、`develop` への push で `./gradlew verify` を流す（フォーマット → リンタ → ライセンスヘッダー → ビルド → 単体テスト → 結合テスト（3種類の対象DB）→ カバレッジの下限 → セキュリティ検査）。`v*` のタグは配備先が決まるまで付けない（team.md）。
- 分岐の戦略: 日々の統合先は `develop`、Bolt ごとの短命のブランチを squash（固定先の更新を含むときは fast-forward）で戻す。プルリクエストは使わず、統合の前の関門はローカルの `./gradlew verify`（team.md）。
- 統合の前の関門の基準: 重大度 High 以上で止める。SpotBugs の `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority にかかわらず止める。カバレッジは全体と新しいパッケージごとに行 80%・分岐 70%（team.md）。
- 成果物: フロントエンドを同梱した実行可能 WAR。版はコミットのハッシュで識別する。配備先が決まるまで、レジストリと SBOM は置かない（team.md）。
- E2E（`./gradlew e2eTest`）は CI の外に置き、統合の前（`frontend/src` と認証・権限・業務データの `src/main` に触れるブランチ）と Build and Test・リリースの前に手元で流す（team.md）。
- この Intent の差: CI の設定（`ci.yml`）は変えていない。`build.gradle.kts` に E2E の対象DB の準備と片付けのタスク（`e2eTest` の前後）を足し、`frontend/vitest.config.ts` で ESLint の設定のテストを先に流す project に分け、`.github/dependabot.yml` の axe-core の見送りを外した。結合テストに業務データの IT が加わり、CI の検査の job は 28〜33 分になった（42 分の線の内）。
- CI の確かめ: Build and Test で B5 の後のコミットを試行ごとに記録した。その後に push された 288736d・22de65f・31d434c は、先頭の 31d434c の CI（実行中）で確かめ、この段の記録に足す。

この段で新しく決める論点は無いため、質問を作らず、要点の確認だけを行う（project.md の学び）。

## Consolidated Summary Confirmation

**ci-config**
- 今ある CI（GitHub Actions、`develop` への push、`./gradlew verify` の段の並び）を記録する。固定している版（Actions・JDK・Node・コンテナのイメージ）と、成果物（WAR）・版の識別を書く。
- この Intent の差（`ci.yml` は変えていない、`build.gradle.kts` の E2E のタスク、vitest の project の分け、Dependabot の axe-core）を書く。
- CI の時間の推移（U1 の後の verify 18分32秒 → B7 の後 23分24秒、CI の検査の job 28〜33 分）と、42 分の線との比べを書く。

**quality-gates**
- 統合の前の関門（ローカルの verify）と CI の関門の基準を、Build and Test が記録した検査の一覧（コマンド）と1つずつ対応づける。CI の外に置く E2E は、その旨と代わりの実行の場を書く。
- B5 の後のすべてのコミットの CI の試行ごとの結果（1152202 の3回の失敗とその直し、以降は1回目で成功）と、31d434c の結果を書く。

**phase-check-construction**
- 5つの単位の build と test、各 traceability.json、Build and Test の網羅の確かめ（覆われていない ID 0 件、Deferred は Performance Validation・Observability Setup・次の Intent）を確かめ、Construction から Operation への境の判定を書く。

Does this all look correct before I generate the CI pipeline artifacts?

- Looks correct
- Request changes

[Answer]: Looks correct
