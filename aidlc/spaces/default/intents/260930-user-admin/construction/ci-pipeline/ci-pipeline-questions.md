# CI Pipeline — Questions

前提（2026-10-03 時点、読み取りだけで確かめた）:

- この Intent では、CI の定義（`.github/workflows/`）と Dependabot の設定（`.github/dependabot.yml`）を変えていません。CI に関わって変わったのは次の2つです。
  - `./gradlew verify` の中身（`backend/build.gradle.kts`）: パッケージごとのカバレッジの下限の対象を増やした（`packagesJudgedByTotal` から B1 で `auth.domain`・`auth.repository`・`access.domain`、B4 で `common.error.web`・`common.observability` を外し、12 から 7 へ）。
  - Gitleaks の除外（`.gitleaks.toml`、f299400）: 監査ログのレビューのフォルダーの ID の誤検知を、パスと値の形の両方で絞って除外した。
- そのため、この段は既にある CI の記録として書きます（`project.md` の Deployment）。
- 次のことは決まっているか、確かめて問題が無かったため、質問にしません。
  - **CI の結果**: B1〜B5 の統合の後と、承認の後の push の CI（run 37106195579）は、すべて success です。時間は 9〜15 分で、U3 の計画 Q-C の目安（CI は 30 分以内）に入っています。
  - **この Intent の途中の CI の失敗**: 要件定義・契約設計・NFR 要件の記録の push（3c800d3・eb7c982・7f3de43）の CI は、`gitleaksScan` が監査ログの誤検知で失敗していました。B1 の前に別のブランチで除外を足して直し（f299400）、その後の CI はすべて success です。既知の失敗として記録します。
  - **E2E は CI の外に置く**: `team.md` の Testing Posture のとおりです。代わりの実行の場は、画面・認証に関わる変更の統合の前とリリースの前の手元の実行です（この Intent では各 Bolt の関門で流した）。
  - **脆弱性の関門**: `./gradlew verify` の OSV-Scanner だけです（前の Intent の CI Pipeline の Q2: B、`team.md` の Way of Working）。
  - **Dependabot**: 開いたままのプルリクエストは 0 件です。最後の Dependabot の実行（2026-09-29）はすべて success です。
  - **ブランチの流れ**: `develop` へ統合し、push をきっかけに CI を流します。B5 はサブモジュールの固定先の更新を含むため fast-forward で統合しました（`team.md` の Way of Working）。

新しく決める論点が無いため、質問は作らず、まとめを確かめます（`project.md` の Way of Working の学び）。

## Consolidated Summary Confirmation

この段では次のとおり進めます。

- **CI の記録**: 既にある CI の記録として `ci-config.md` と `quality-gates.md` を書きます。
  - CI の定義は変えていません。この Intent で `verify` に増えた中身（パッケージごとのカバレッジの下限の対象、Gitleaks の除外）と、Build and Test が記録した検査と CI の段との対応を書きます。
  - B1〜B5 の統合の後と承認の後の push の CI の結果と時間を記録します。
  - この Intent の途中の CI の失敗（Gitleaks の誤検知、f299400 で解消）を、既知の失敗として記録します。
  - E2E を CI の外に置くことと、代わりの実行の場を明記します。
- **段の境目の確かめ**: Construction から Operation への確かめの結果を `verification/phase-check-construction.md` に書きます。
  - 単位をまたいだ網羅は、覆われていない ID 0 件（条件つき）です。
  - Build and Test の Not Met 1 件（U5-NFR7.1、N-19）と Unverified 22 件は、Build and Test の承認の場で、後の Intent への持ち越しと後の段への引き継ぎとして承認済みです。境目の確かめでは、未解決の指摘ではなく承認済みの持ち越しとして記録します。

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
