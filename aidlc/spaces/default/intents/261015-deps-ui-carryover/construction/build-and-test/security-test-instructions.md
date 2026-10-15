# セキュリティの検査の手順（Intent 261015-deps-ui-carryover）

## 統合の前の関門（`./gradlew verify` の中）

| 検査 | 止める基準 | この Intent での確かめ |
|---|---|---|
| Gitleaks（秘密情報の検出） | 検出があれば止める | `develop` の先頭の verify で検出なし |
| SpotBugs ＋ FindSecBugs | priority 1 と、`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority にかかわらず止める | 通過 |
| OSV-Scanner | 実行時の依存の High 以上、成果物を作る道具（`config/npm-build-tools.txt`）と悪意のあるパッケージ（`MAL-`）は開発時でも止める | 止める指摘 0・警告 18（`braces` の1件と `vendor/make-you-chic-ui` の開発用 17 件。どちらも直った版が無いか開発時だけ） |

実行: `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`。OSV-Scanner だけは `./gradlew osvScan`。

## 漏えいの確かめ（この Intent で足した・変えたもの）

- `DslSecretLeakIT`・`NavigationSecretLeakIT`（TRACE を有効にして、目印の値がアプリのログに出ないこと）。どちらも直す前に落ちることを確かめてから直した（`project.md` の Mandated）。
- 伏せた範囲: `DslError`・`DslErrorItem`・navigation の6つの record・`DslModel` の `toString`。`DslSchema`・`DslTable`・`DslMenuItem`・`DisplayName` などの下位の record は伏せていない。D-10 の A（スキーマ名・テーブル名・表示名は業務の名前で、個人に関する値ではない）のとおり受け入れ、守られているのは `DslSecretLeakIT`・`NavigationSecretLeakIT` が通る経路である（コード生成のレビューの R-04、依頼者が承認の場で受け入れた推奨 b）。

## 信頼できない入力（DSL と権限の YAML）

- 1行の長さの上限 65,536 コードポイントを足した。上限ちょうどは受け付け、超えると DSL は 422 `DSL_INVALID`（`SIZE_LIMIT`）、権限の YAML は 413 `PAYLOAD_TOO_LARGE` で拒否し、部品の例外のメッセージを応答に含めない。確かめは `SafeYamlParserTest`・`DslAdminApiIT`・`RoleTransferLimitsApiIT`。
- 行の区切りは `\n`・`\r` だけで、YAML 1.1 の改行（U+0085・U+2028・U+2029）は行の中の文字として数える（厳しい側。記録済みの差）。

## 見送ったもの

- axe-core 4.14.0（WCAG 2.5.3 の規則が既定で動き、既存の画面の違反で E2E が落ちるため）。違反は後の Intent に回す。脆弱性の直しを含む更新ではない。
