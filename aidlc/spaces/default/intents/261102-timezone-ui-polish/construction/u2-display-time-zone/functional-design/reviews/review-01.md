## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-11-03
**Iteration:** 1
**Verdict Dictated:** no

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Major | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/functional-spec.md > 5 上流との差・Assumptions & Open Questions（E2E の書き換え） | 配布がログイン後に GET /api/me/preferences を1回足すことと、応答の形の確かめに5つ目を足すことの E2E への影響が、「管理の画面の期待の書き換え」としか書かれておらず過小。実物では frontend/e2e/support/preferencesFixtures.ts の見本と hasPreferencesShape が項目4つちょうどを前提にしており、5つ目の無い見本を配布が読むと error 状態になる。frontend/e2e/080-preferences-accessibility.e2e.ts は差し替えた GET の回数を toBe(1) で確かめており、配布の読み取りで回数が変わる。100 と 110 も同じ GET を差し替えている | 影響する既存のテストと E2E（preferencesFixtures・080・100・110、プリファレンスと管理の画面のテストの提供元の差し込み）を functional-spec の差の節に名前で挙げる。見本に displayTimeZone を足すこと、差し替えの回数の数え方（配布の読み取りを含めるか）、本物の応答との形の比べに5つ目を含めることを決めとして書く。件数の洗い出しは Code Generation の計画に回すことを明記する | New |
| R-02 | Major | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/rules.md > BR2.22 と functional-spec.md > 5 ストーリー・要件との差「無い」 | AC1.2.4 は、保存済みの名前を解釈できないとき「画面の時刻を見る」画面で、設定を解釈できないことを画面に出すと定めている。BR2.22 は管理の画面に案内の行を足さず、案内はプリファレンスと業務データの画面だけにする。管理の画面は黙ってブラウザのタイムゾーンで出す。traceability.json は AC1.2.4 を OK にし、差の節も「ストーリー・要件との差: 無い」と書いており、食い違いが記録されていない | 管理の画面にも案内を出す形に直すか、案内を省くことを上流との差として functional-spec の差の節に正直に書き（理由は時差が時刻に付く点）、traceability の AC1.2.4 の target に「管理の画面の案内は足さない」と明記して承認の場で依頼者に確かめる | New |
| R-03 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/rules.md > BR2.4 と BR2.9 | BR2.4 は入れた値を応答・ログに載せないとし、BR2.9 は要求と応答の toString でタイムゾーン名を伏せずに出してよいとする。TRACE のログには無効な入力の文字列がそのまま載りうる（長さの検証は toString の後）。決まりどうしが食い違う | BR2.9 を「検証を通った名前だけを出す」か「要求の toString では5つ目も伏せる」に直す。TRACE を有効にした SecretLeakIT で無効な入力が出ないことをテストの対象に挙げる | New |
| R-04 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/rules.md > BR2.13 の violation_behaviour と functional-spec.md > 5（formatDateTime・formatLockedUntil を残すか消すか） | ブラウザの既定のタイムゾーンを暗に使わない検査を「ESLint の制限か単体テスト」と未決のままにしている。formatDateTime は timeZone 省略でブラウザの既定を使う形で、残すと AC1.2.6 の暗黙の既定が再び入る経路になる | 構造の検査を1つに決める（例: Intl.DateTimeFormat の timeZone 省略と resolvedOptions の呼び出しを配布の1か所以外で止める）。使わなくなった整形は消す方針にするか、残すなら使われないことを確かめる検査を書く | New |
| R-05 | Minor | aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u2-display-time-zone/functional-design/traceability.json > AC5.3.3・AC5.3.4・AC5.3.2 | AC5.3.3 の本文は「性能の目標を変えない・問い合わせの数を増やさない」で、BR2.14（ログイン後の GET の追加）に OK で結んでいるが、目標の確かめは NFR 要件に回しており測っていない。AC5.3.2・AC5.3.4 は U2 が主の単位のストーリーなのに「delivered by 他の単位」の N/A になっている | AC5.3.3 は確かめを NFR 要件に回す扱い（Deferred 相当）にして target に持ち主の段を書く。AC5.3.2・AC5.3.4 は、他の単位が受け持つ旨と、U2 が確かめない理由を target に残す | New |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| 読み取りによる突き合わせ（Read と検索のみ。git・gradlew・docker・npm は未使用） | 実行済み | 既存の PreferencesRequest・PreferencesResponse・UserPreferencesService・MeController・PreferencesValidation（INVALID_VALUE と REQUIRED）、移行 V13 が最後であること、U1 の formatInstant と canInterpretTimeZone の公開を確かめ、契約 C2・C3 と ADR-003 に対して矛盾が無いことを確かめた。一括の保存・3状態・400 INVALID_VALUE・本人だけ・監査なし・前の版との互換は既存コードと整合する。名前の完全一致は ZoneId の集合の側が広く、ブラウザが解釈できない名前は saved-unresolvable で受けるため成り立つ。C2 への error 状態と差し替えの口の追加は functional-spec の5節に正直に記録されている |

### Summary

契約 C2・C3 と既存コードの整合は取れており、後方互換（NULL 可の列、5つ目を送らない前の版の PUT は保存済みの値を変えない）も妥当で、READY とする。ただし Major が2件あり、E2E の見本と差し替えの回数への影響の見積もり（preferencesFixtures と 080・100・110）と、AC1.2.4 に対する管理の画面の案内の扱いの記録は、承認の場の前に直すことを勧める。
