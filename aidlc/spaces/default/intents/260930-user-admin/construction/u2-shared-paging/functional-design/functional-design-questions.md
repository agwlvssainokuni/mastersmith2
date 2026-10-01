# Functional Design の質問 — u2-shared-paging

単位 U2（`aidlc/spaces/default/intents/260930-user-admin/inception/units-generation/unit-of-work.md`、種類 library）の機能設計のための質問です。受け持つのは US1.1 のページ送りの計算（従。主は U3・U5、`unit-of-work-story-map.md`）で、関わる受け入れ基準は AC1.1.1（1ページ 20 件・全体の件数とページの番号）・AC1.1.5（ページの番号の検証と、最後のページより後の空の一覧）・AC1.1.7（20 件で1ページ・21 件で2ページ）です（`aidlc/spaces/default/intents/260930-user-admin/inception/user-stories/stories.md`）。要件は FR1.3・FR1.5、部品は Paging・UiPaging（ADR-004）、契約は C2・C5（`aidlc/spaces/default/intents/260930-user-admin/inception/contract-design/contract-summary.md`）です。既存のコードは `backend/src/main/java/cherry/mastersmith/invitation/domain/InvitationPaging.java` とその使い手（`invitation/service/InvitationService.java`）、テスト（`backend/src/test/java/cherry/mastersmith/invitation/domain/InvitationPagingTest.java`・`invitation/repository/InvitationRepositoryIT.java`）、境界のテスト（`invitation/InvitationBoundaryArchitectureTest.java`・`ArchitectureTest.java`）、画面の `frontend/src/features/invitation/paging.ts` とその使い手（`InvitationList.tsx`・`useInvitationAdmin.ts`）・テスト（`paging.test.ts`）を確かめました。

## 設計の要点（案）

上流の決定とコードの確認から導ける、この単位の機能設計の見通しです。この単位には新しく決める論点が無いため、質問の番号（Qn）を添える点はありません。

1. **サーバーの口をそのまま移す（C2）**: `cherry.mastersmith.invitation.domain.InvitationPaging` を `cherry.mastersmith.common.paging.Paging` に移し、4つの口（`PAGE_SIZE`＝20・`parsePage(String)`→`OptionalInt`・`pageOf(long)`→`int`・`offsetOf(int)`→`long`）の名前・引数・結果・例外（1 未満で `IllegalArgumentException`）を変えない。`InvitationPaging` は消す（残して中で `Paging` を呼ぶ形にはしない）。
2. **サーバーの使い手の切り替え**: 本体の使い手は `InvitationService` の1つだけ（`pageOf` を招待の重複の応答の page に、`parsePage`・`offsetOf`・`PAGE_SIZE` を一覧に使う）。テストの使い手は `InvitationRepositoryIT`（`PAGE_SIZE`）と `InvitationPagingTest`。`InvitationPagingTest` は `backend/src/test/java/cherry/mastersmith/common/paging/PagingTest.java` に移し、今の事例（境目の値・拒否する入力 `0`・`-1`・`1.5`・`abc`・空・` 1`・`+1`・`9999999999`・1 未満の例外・性質ベースのテスト）をすべて残す。
3. **画面の口をそのまま移す（C5）**: `frontend/src/features/invitation/paging.ts` を `frontend/src/shared/paging/paging.ts` に移す。置き場は `src/shared/` の今の形（`api-client`・`format`・`validation` のように用途ごとの下位のフォルダー）にそろえる。すべての export（`PAGE_SIZE`・`PagerDirection`・`PageRange`・`pageCount`・`pageRange`・`correctedPage`・`pagerButtonDisabledAfter`）の名前・引数・結果を変えない。
4. **画面の使い手の切り替え**: 使い手は `InvitationList.tsx`（`PAGE_SIZE`・`pageRange`・`PagerDirection`）と `useInvitationAdmin.ts`（`correctedPage`・`pageRange`・`pagerButtonDisabledAfter`・`PagerDirection`）の2つだけで、import の場所だけを変える。`paging.test.ts` は `frontend/src/shared/paging/paging.test.ts` に移し、今の事例をすべて残す。招待の画面のほかのテストは、ページ送りの計算を差し替えていない（`vi.mock` で `./paging` を置き換えていない）ため、そのまま通る見込み。
5. **招待の振る舞いを変えない**: 移した後も、page の指定なしは 1、1〜9 桁の数字で 1 以上だけを受け、空・前後の空白・符号・小数・10 桁以上・0 は拒否（`VALIDATION_FAILED`）、ページの番号は位置 ÷ 20 の切り上げ、読み始めは (page − 1) × 20 のまま。最後のページより後のページは、呼び出し元が `offsetOf(page) >= total` で読まずに全体の件数つきの空の一覧を返す（招待の今の形、AC1.1.5、差7）。確かめは、招待の既存のテスト（サーバー・画面とも）が import の場所の変更だけで通ったままであること。
6. **「最後のページより後は空の一覧」の置き場**: `unit-of-work.md` の U2 の境界は Paging に「最後のページより後は空の一覧」を含めているが、その後のレビュー（R-03）で直した C2 は、この扱いを呼び出し元が `offsetOf` で行うとし、口を足さない。この設計は C2 に従い、rules.md に「呼び出し元（招待・U3 の一覧）が守る決まり」として書き、`unit-of-work.md` の書き方との差を functional-spec.md に記録する。
7. **成果物の形（library の種類）**: Paging・UiPaging は保存するデータを持たない純粋な関数のため、entities.md のエンティティは 0 件とし、その理由を書く（ページの番号・位置・読み始め・全体の件数は、関数の入力と出力の値として rules.md で扱う。`project.md` の「エンティティにはアプリが独自に持つデータだけを書く」）。rules.md はサーバーの計算を BR1.x、画面の計算を BR2.x、呼び出し元が守る決まりを BR3.x に分ける。functional-spec.md には状態の移り変わりは無く、招待の一覧の要求の流れ（検証 → 件数 → 読み始め → 空の一覧か行の読み出し）と、切り替えの手順を書く。traceability.json は AC1.1.1・AC1.1.5・AC1.1.7 のページ送りの部分を BR に結び、検索・画面の表示など U3・U5 が持つ部分は対象の外と書く。
8. **説明文を一般にする**: 移した先のクラスと関数の説明文（Javadoc・JSDoc）は、招待に限った書き方（「招待の一覧の」「BR2.3・BR5.2」「招待が載るページ」など）を、管理の一覧に共通の書き方と U2 の BR の番号に直す。画面の文言（`invitation.pager.status`・`invitation.action.prev`・`invitation.action.next` などの訳の鍵）は C5 の範囲に入っておらず、ADR-004 で「移す範囲（計算だけか、文言も含むか）はコード生成の計画で決める」とされているため、この段では移さない前提で書く。
9. **性質ベースのテスト（`team.md`）**: 既存の性質（サーバーの「位置が載るページは (p − 1) ÷ 20 + 1 で、位置がそのページの範囲に入る」、画面の「どの全体の件数・正しいページでも範囲と補正が範囲の中に収まる」）を残し、次の候補を足す。失敗のときは乱数の種を出す（jqwik は既定で種を出し、fast-check は seed と path を出す）。
   - サーバー（jqwik）: 1〜999,999,999 の整数 n で `parsePage(String.valueOf(n))` が n になる／数字以外を含む・10 桁以上・0 の文字列は空になる／1 以上のどの page でも `offsetOf(page + 1) − offsetOf(page)` が 20、`pageOf(offsetOf(page) + 1)` が page に戻る。
   - 画面（fast-check）: 全体の件数 total で、隣り合うページの範囲が隙間なくつながり（次のページの from が前のページの to + 1）、すべてのページの件数の和が total になる／`pageCount(total)` が「(pageCount − 1) × 20 < total ≤ pageCount × 20」を満たす／「次へ」が押せなくなるのは page が `pageCount(total)` 以上のときだけ／行が1件以上あれば `correctedPage` は補正しない。
10. **境界と層**: `common.paging` は JDK だけに依存し、ほかの機能に依存しない。`InvitationBoundaryArchitectureTest` は `invitation` から `common` への依存を禁じておらず、`ArchitectureTest` の層の決まりにも触れない。`Paging` は Spring の部品ではない静的な関数で、パッケージ名が web・service・domain・repository に当たらないため、`TraceAspect` の TRACE の対象にもならない（扱う値は数と page の文字列だけで、個人に関する値は無い）。画面の `src/shared/` は機能どうしの直接の import の禁止（`team.md` の Code Style）に当たらない置き場。
11. **カバレッジ**: `common.paging` は新しいパッケージで、`packagesJudgedByTotal` に無いため、自動でパッケージごとの下限（行 80%・分岐 70%）の対象になる。移す単体テストで `parsePage` の3つの分かれ道（null・数字の形でない・0）と `pageOf`・`offsetOf` の例外を通るため、下限を満たす見込み。`invitation.domain` は一覧に無く（すでにパッケージごとの対象）、よく通っているクラスが抜けることで値が下がりうるため、コード生成でテストを足した後に実測して記録する。

## 決まっていること（質問にしない）

| 決まっていること | 出典 |
|---|---|
| サーバーのページ送りは `common` の下の新しいパッケージ `common.paging`（部品 Paging）、画面は `src/shared/`（部品 UiPaging）に移し、招待と利用者の管理で共有する | ADR-004、Domain Design の DQ4 A |
| 招待の振る舞いは変えない。招待の既存のテストが通ったままであることで確かめる | ADR-004、`unit-of-work.md` の U2、C2・C5 |
| サーバーの口は `InvitationPaging` の4つ（`PAGE_SIZE`・`parsePage`・`pageOf`・`offsetOf`）を名前・引数・結果・例外ともそのまま移し、`InvitationPaging` は消す | C2（レビューの R-03 で直した形） |
| 画面の口は `paging.ts` のすべての export を名前・引数・結果ともそのまま移す | C5（レビューの R-03 で直した形） |
| 1ページは 20 件。一覧の応答には全体の件数とページの番号を含める | FR1.3、AC1.1.1・AC1.1.7 |
| ページの番号が 1 未満・整数でない・空・桁あふれなら入力の誤り（`VALIDATION_FAILED`）。最後のページより後は拒否せず全体の件数つきの空の一覧（200） | FR1.5、AC1.1.5、ストーリーの差7 |
| 最後のページより後の空の一覧は、呼び出し元が `offsetOf` の結果で作る（Paging に口を足さない） | C2 の behaviour |
| ページ送りの計算は DB に触れず、時刻にも依存しない純粋な関数で、性質ベースのテスト（jqwik・fast-check）の対象 | C2・C5、`unit-of-work.md` の U2、`team.md` の Testing Posture |
| 画面の文言を移すかはコード生成の計画で決める | ADR-004 の Consequences（中立） |
| U2 は B2 で U4 と一緒に作り、統合は単位ごとの squash にしてよい（どちらにするかはコード生成の計画で決める） | `bolt-plan.md` の B2、`team.md` の Way of Working |
| `common.paging` は新しいパッケージとしてパッケージごとのカバレッジの下限の対象。`invitation` は一覧に無く、すでに下限の対象 | ADR-004、`unit-of-work.md` の U2 の注意、`backend/build.gradle.kts` |
| 招待の画面（InvitationUi）は B2 の中で一度だけ直す（ページ送りの切り替えと U4 の 403 の置き換えを同じ Bolt で行う） | `bolt-plan.md` の B2（Units Generation のレビューの R-01） |

---

新しく決める論点が無いため、質問は作りません。上の「設計の要点（案）」の確認（Looks correct / Request changes）で進めます（`project.md` の Way of Working の学び）。

## Consolidated Summary Confirmation

答えのまとめ:

- 質問は 0 問（C2・C5 で口の名前・振る舞いが既存のまま決まっており、置き場は ADR-004 で決まっているため）
- 設計の要点（案）は冒頭の 11 件のとおり（サーバーの口の移設と `InvitationPaging` の削除、サーバーの使い手の切り替え、画面の口の移設先 `src/shared/paging/`、画面の使い手の切り替え、招待の振る舞いを変えないこと、最後のページより後の扱いを呼び出し元の決まりとして書くこと、library の成果物の形、説明文の一般化と文言を移さない前提、性質ベースのテストの候補、境界と層、カバレッジ）

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
