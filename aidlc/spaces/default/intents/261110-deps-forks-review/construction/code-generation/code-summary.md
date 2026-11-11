# コード生成のまとめ（Intent 261110-deps-forks-review）

計画 `code-generation-plan.md`（Step 1〜16）の結果。細かい記録（版の配布元での確かめ・コマンド・時間）は `generation-notes.md`。

## 1. ブランチと統合

| まとまり | ブランチ | `develop` の統合のコミット | 統合の前の確かめ |
|---|---|---|---|
| ① 実行環境と依存（Step 1〜9） | `feature/261110-deps`（消した） | `888a4cc1` | 全件の `verify`（clean 付き、`-PitForks=3`、15分23秒）・E2E の全件 298 件 |
| ② CI・イメージ・道具（Step 10〜13） | `feature/261110-ci-images`（消した） | `ed0c8675` | 全件の `verify`（同、15分3秒）・E2E の全件 298 件・入れ替えたコンテナの `docker inspect` |
| ③ 決まりの文書（Step 14〜15） | `docs/261110-rules`（消した） | `7373085f` | `verifyQuick` |

どれも squash で統合した。`git push` はしていない（依頼者が行う）。

## 2. 版の前後

| 対象 | 前 | 後 |
|---|---|---|
| Spring Boot | 4.1.2 | 4.1.2（最新の安定版。4.2.0 は RC2 まで） |
| JRE のイメージ | `eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a5…` | 同じ（タグもダイジェストも変わらない。25.0.6 は未公開） |
| JDK のイメージ | `eclipse-temurin:25.0.5_8-jdk-noble@sha256:95e350ad…` | 同じ |
| H2 | 2.4.240 | 2.4.240 |
| OpenTelemetry（api） | 1.62.0 | 1.62.0 |
| Jackson | 3.1.7 | **3.1.8**（networknt 3.0.9 の推移。依頼者の決定 G1: B） |
| networknt json-schema-validator | 3.0.6 | **3.0.9** |
| opentelemetry-logback-appender | 2.28.1-alpha | 2.28.1-alpha（固定を残す） |
| Gradle Wrapper | 9.8.2 | **9.9.1** |
| Spotless | 8.10.4 | **8.11.0** |
| Node | 26（手元 26.13.0） | 同じ（26 系の最新） |
| 画面の依存 | — | 小さな版をまとめて最新に。大きな版: i18next 27・react-i18next 18・jsdom 31・stylelint 18・stylelint-config-standard 41・`@playwright/test` 1.65.0 |
| axe-core | 4.14.0 | 4.14.0（最新。lockfile で1つの版） |
| `actions/setup-java` | v6.1.0 | **v6.2.0** |
| OSV-Scanner（CI） | 2.7.0 | **2.7.1**（手元の Homebrew は 2.7.0 のまま。依頼者の決定 G6: A） |
| ほかの Actions・Gitleaks | — | 最新のまま |
| otel-collector | 0.164.0 | **0.165.0** |
| otel-lgtm | 0.39.0 | **0.40.0** |
| Mailpit | v1.33.0 | **v1.33.1** |
| postgres・mysql | 18.6・26.10.0 | 同じタグの組み直しの新しいダイジェスト |
| mariadb・k6 | 13.0.2・2.3.0 | 同じ |

**FR5（負荷の試験）の分岐の材料**: Spring Boot・H2・JRE のイメージは、どれも変わらない。変わったのは Jackson（3.1.7 → 3.1.8、パッチ）と画面の依存。

## 3. 見送ったもの

| 対象 | 理由（判定基準） | 外す時期 |
|---|---|---|
| TypeScript 7 | 基準 3（上流の未対応）: `@typescript-eslint/parser` の最新 8.72.2 が TypeScript 6.1 未満だけを受け付ける | typescript-eslint が 7 に対応したとき（`dependabot.yml` の既存の `ignore` のまま） |

make-you-chic-ui の部品が原因の見送り（FR1.4a）は無く、依頼の文面は置いていない。

## 4. 連動する固定の結果（FR2）

| 固定 | 結果 | 理由 |
|---|---|---|
| opentelemetry-logback-appender 2.28.1-alpha | 残す | 管理の OpenTelemetry が 1.62.0 のまま（最新の appender 2.33.1-alpha は api 1.67.0 を求める）。外部エクスポートの手元の起動の確かめは要らなかった |
| networknt | 3.0.9 に上げ、版の固定と `ignore` は続ける | 依頼者の決定 G1: B。3.0.9 は Jackson 3.1.8（3.1 系）を求める。3.2 系を求める版を避けるため固定は続ける |
| `-Dh2.compactThreads=1` | 残す | H2 が 2.4.240 のまま |

## 5. E2E の時差（FR2.5）

E2E で起動する WAR の JVM だけが時差を明示していなかった（PC の既定の Asia/Tokyo で動いていた）。依頼者の決定 G2: A で、`frontend/playwright.config.ts` の `webServer.command` に `-Duser.timezone=Asia/Tokyo` を足し、`team.md` の「4か所」をそろえた。E2E の全件で確かめた。

## 6. 古い記述の直し（FR3）

- `project.md` の Tech Stack: Jackson の行（上書きは無く、Spring Boot の BOM の版。networknt 3.0.9 で 3.1.8）と opentelemetry-logback-appender の行（Spring Boot 4.1.2・OpenTelemetry 1.62.0・外す時期）を書き換えた（G8）。前と後の文は `generation-notes.md` の Step 14。
- `.github/dependabot.yml`: gradle の説明の「Spring Boot 4.1.1」を 4.1.2 に、collector の見送りのタグを `0.165.0-386` に直し、見直しの結果を書いた。

## 7. テストとカバレッジ

- 新しいテストは置いていない（計画どおり）。
- ② の後の全件の `verify`: 単体 2,939 件・結合 1,716 件（飛ばし 10、前と同じ件数）、失敗 0。
- カバレッジ（① の後の全件の `verify`）: backend 全体は行 97.75%・分岐 91.52%、パッケージで最も低いのは `config` の分岐 76.7%（下限の内）。frontend 全体は行 95.49%・分岐 91.03%。
- E2E の全件: ①・② の後にそれぞれ 298 件通過。axe の違反 0（`KNOWN_VIOLATIONS` はどれも空）。
- どの長い検査も `caffeinate -dims` で包み、`pmset -g log` で眠りが無いことを確かめた。

## 8. 計画との差

- networknt: 計画の Step 5 (b) の条件（管理の Jackson が 3.2 系以上）を満たさなかったが、依頼者の決定 G1: B で 3.0.9 に上げた。
- コミットの区切り: networknt の直しは `libs.versions.toml` を共有するため、① の1つ目のコミット（Gradle・Spotless）に含めた。
- Step 11 の確かめ: 計画は対象DB の結合テストを Step 13 の全件の `verify` で確かめる形だったが、担当が Step 12 の後にイメージを変えた対象DB（PostgreSQL・MySQL）の結合テストを絞って先に流した（253 件通過）。
- colima が止まっていたため、Step 2 の前に担当が起動した。配備したアプリ（`mastersmith-app-1`）は、この段の前から止まったまま（触っていない）。

## 9. Build and Test に引き継ぐこと

- FR4（`-PitForks` の比べ）: 依頼者が `develop` をプッシュした後、同じコミットで CI の手動の実行を 2・3 交互に各3回。中央値で決め、`ci.yml` の `IT_FORKS_DEFAULT` とコメント・`docs/verification.md` を直す。
- FR5（負荷の試験）: Spring Boot・H2・JRE はどれも変わらないため、FR5.2 に当たり省く見込み（理由を Build and Test の記録に書く）。Jackson 3.1.8 への更新はパッチで、全件の `verify` と E2E で確かめた。
- CI の OSV-Scanner 2.7.1 の結果（手元は 2.7.0）を、プッシュの後の CI で確かめる。
- otel-lgtm 0.40.0 と collector 0.165.0 は起動していない（collector は設定の `validate` だけ）。手元の監視を起動して確かめる。
- E2E の全件と全件の `verify` の時間・CI の検査の job の時間の記録（Build and Test の決まり）。
- Dependabot の PR #99（Spotless 8.11.0）は ① で取り込み済み。閉じるかは依頼者に確かめている。

## 10. 依頼者に確かめたいこと

- Dependabot の PR #99 を、取り込み済みとして閉じてよいか（GitHub の公開の操作のため）。
