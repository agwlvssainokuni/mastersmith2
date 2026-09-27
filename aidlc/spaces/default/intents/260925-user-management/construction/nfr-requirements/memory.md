<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->


- 2026-09-27T02:48:25Z — 質問を2問（パスワードの変更の応答時間・今のパスワードの誤りの制限）に絞った; 機能設計の6節がこの段へ渡した論点のうち、判断が分かれるのはこの2つだけだった。カバレッジの範囲・V7 の後方互換・2本目の接続の負荷の試験は team.md・要件 NFR5・NFR10 と project.md の決まりで決まっているため、設計の要点と「決まっていること」に書いた。
<!-- aidlc-wave-memory:u2-user-preferences:c1393b3abd238f6759d08b363c15b9a32f589cec17cb59cbe24ae0ced0493fe6 -->

- 2026-09-27T02:48:25Z — パスワードの変更の成功は bcrypt を2回計算するため、要件 NFR6 の [assumption]（p95 1 秒）が同時 10 件では届かない見込みと読んだ; 照合1回 約 278 ms、ログインの同時 10 件の p95 が CPU 4 で 940 ms（colima-spec-up の実測）から、成功は約 1.9 秒と見積もった（未測定）。そのため Q1 で目標と測る負荷を尋ねた。
<!-- aidlc-wave-memory:u2-user-preferences:33e4ef633e04743ec97cb9cd8e0fb2ada3f794fbb5e035422e9674d5a2192ccc -->

- 2026-09-27T02:48:18Z — 質問は応答時間の目標の1問だけにし、ほかは NFR の要点（案）13 件として要約で確かめる形にした; キャッシュ（BR3.6）・値の検証とログ（BR1・BR2.1）は承認済みの機能設計で決まっており、回数の制限は既存の公開の /api/problems と前の Intent のログインの判断（設けない）の前例で決まると読んだ。NFR6 は U8 の API の値を決めていないため、数値の目標だけを新しく決める論点とした。
<!-- aidlc-wave-memory:u8-instance-appearance:06e3b3a904e03c4bf47fa0358f51e9bd6d32537ee9aa3816269ab1fea15aa146 -->

- 2026-09-27T02:48:18Z — HEAD は公開に足さず、未認証の HEAD は既存の 401 のままとする案にした; 公開の決まりをメソッド GET に限って足すため HEAD は当たらない見込みで、画面は GET しか使わない。実際の扱いはサーバー側のテストで確かめ、違えばコード生成で記録する。
<!-- aidlc-wave-memory:u8-instance-appearance:c86dca8594f96c8c6a8fd0faa979fb200cf46681cfb7eca0d6e8cbd6fb426a07 -->

- 2026-09-27T02:48:18Z — U4 の W2（見た目の設定の答えが出るまで描かず、待ちに上限なし）を U8 の信頼性の要件の根拠にした; U8 の応答が内部DB の接続を借りないこと（プールが尽きたときや詰め直しの一時停止でも待たされない）を要点 2 に置き、ArchUnit で確かめる案にした。
<!-- aidlc-wave-memory:u8-instance-appearance:c7ac0266c56eac4476f7d1738fc16e3e6f50af3bed7822e2d740be46a6dde8bf -->

- 2026-09-27T03:11:02Z — 枝番はこの単位の中で .1 から振った; 同じ Intent の U2 の成果物が単位ごとに .1 から振っているのに合わせた（前の Intent の dsl-schema-loader は単位の間で通しで振っていた）。各成果物の冒頭に「枝番はこの単位の中で振る」と書いた。
<!-- aidlc-wave-memory:u8-instance-appearance:5b663a49f0b0b97e8e0b57b786bd6ddb0bbeff2c353db4707b72f71039eabbe5 -->

- 2026-09-27T03:11:02Z — 要件に拡張性・信頼性・観測性の NFR が無いため、同時の要求と指標・トレースを NFR6、起動・応答・警告のログ・監査・健全性を NFR9 の枝番に寄せた; 前の Intent の U4 が同時の数と指標を NFR1（性能）に寄せた前例と同じ考え方。寄せ先は各成果物の冒頭と traceability.json の target に書いた。
<!-- aidlc-wave-memory:u8-instance-appearance:8d0809c1d07b34e7195bca580acf1a297c29a8bbab39659eaa45b6c5a4274539 -->

- 2026-09-27T03:11:02Z — 応答に元の文字列を載せない決まり（BR3.4）は NFR4.5、警告のログに設定された値を出さない決まり（BR2.1）は NFR9.4 に置いた; 公開の API が出してよい範囲は認可（公開の範囲）と一緒に確かめ、ログの中身はログの形と一緒に確かめるため。NFR2（個人情報とメール）は U8 が扱わないため N/A とし、理由に NFR4.5・NFR9.4 を示した。
<!-- aidlc-wave-memory:u8-instance-appearance:c50ae1f3166875cd37d8f8122c0929166643ddf9d59ce49a74f964d948ca9289 -->

- 2026-09-27T02:53:05Z — 「依存の取得元は Maven Central だけ」は依存の解決の決まりで、ビルドのプラグインには当たらないと読んだ; ルートの settings.gradle.kts に pluginManagement が無く、アプリ自身のプラグインも今すでに Gradle Plugin Portal から取っている。そのため java-mustache-processor のビルドの設定が Plugin Portal のプラグイン（dependency-check 10.0.4 は Maven Central に無い）を使うことは決まりを崩さないとし、B1 の確かめの条件に Gradle 9.7.1 で動くことを入れた。
<!-- aidlc-wave-memory:u1-mail:c8afbe32c990e473b341e54e30704d359596aafa0f407b47fb7b7e3b6c64a8fa -->

- 2026-09-27T02:53:05Z — 性能・キャッシュ・暗号化の方式・固定先のコミットは質問にせず要点にした; テンプレートは起動時に準備済み（BR2.3）、暗号化は BR1.4・BR1.5 で決まり、固定先はタグ 0.1.0 が既定のブランチの先頭（8d44c36）と一致するため。質問は判断が分かれる時間切れの値・変えられるか・送信の部品の組み込み方・テスト用の受け手の4問に絞った。
<!-- aidlc-wave-memory:u1-mail:063637c08ab34564ee5eee2b2316abfebc57a172e4320817bfb87a988111e121 -->

- 2026-09-27T03:07:32Z — Q3 X の「送らない」を、送信の部品を ObjectProvider で受け、部品が無い・接続先が空白だけ・差出人が無いときに NOT_CONFIGURED とする形で満たすと読んだ; Spring Boot 4.1.1 の MailSenderAutoConfiguration は spring.mail.host があるときだけ JavaMailSenderImpl を作り、OnPropertyCondition は空の文字列も「ある」とするため。application.yaml に空の既定の spring.mail.host を置かず、.env.example の SMTP の行はコメントにする案にした。
<!-- aidlc-wave-memory:u1-mail:caf845cfc26f475e6883ca875b23df755dab593adc2a0f8b196ebbdb2b9fff46 -->

- 2026-09-27T03:45:42Z — library の単位で性能・信頼性の文書が無いため、NFR5・NFR6 を tech-stack-decisions.md の2節、NFR8・NFR9・NFR11 を3〜5節に置き、冒頭に要件の節と場所の対応表を付けた; セキュリティ（NFR1・NFR2）と上流との差は security-requirements.md。ヘルスチェックから外す要件は、SMTP の状態がアプリの応答と健全性を左右しない点で NFR6.4 にした。
<!-- aidlc-wave-memory:u1-mail:a82be8ce1f337b1323563ea2432746651ef9d76f47e1d46dce0ceebd291864e6 -->

- 2026-09-27T03:45:42Z — 枝番は依頼どおり単位の中で .1 から振った（要件 27 件、traceability は OK 7・N/A 4）; 前の Intent（dsl-schema-loader）は NFR1 を単位の間で通し番号にしていたため、この Intent では後の単位と同じ NFR1.1 などが重なる。単位のディレクトリで区別できる前提。
<!-- aidlc-wave-memory:u1-mail:280964040dccbd1208061ffb7ecb2caf2548d5b4edfb2a4a26de03a4629f5dc5 -->

- 2026-09-27T03:54:09Z — 応答の時間の差はそろえない方針を要点に置いた; リンクの確かめ・完了の時間の差から分かるのはトークンが有効かだけで、256 ビットのトークンを持つ人にしか意味が無いため。質問にはせず、要点 9 に根拠を書いて依頼者の確認に委ねた。
<!-- aidlc-wave-memory:u3-invitation:2f0401008182f0d39268594d0305016fd2f601fdb9c67f78937873cffcdc5845 -->

- 2026-09-27T03:54:09Z — 招待・送り直しの 5 秒は U1 の時間切れ 3 秒で収まるとして質問にしなかった; U1 の NFR6.1 で決まっており、遅れ続ける受け手の既知の限界（U1 の NFR6.3）を引き継ぐだけにした。
<!-- aidlc-wave-memory:u3-invitation:6968927f674de0132d8de5ce387ad06aa2dca83d5c8791e14723f1ab20ef7e90 -->

- 2026-09-27T03:54:10Z — 質問は最初の描画の時間の目標の置き方（Q1）と NFR7 の確かめ方（Q2）の2問にし、ほかは NFR の要点（案）14 件として要約で確かめる形にした; ブラウザの保存・受け渡し・公開の API・CSP・フォントの採用と ADR・make-you-chic-ui の固定先の更新は承認済みの機能設計と team.md・project.md で決まっていると読んだ。NFR7 の確かめ方はストーリーの「後の段に回す点」でこの段が持ち主とされており、新しく決める論点とした。
<!-- aidlc-wave-memory:u4-display-foundation:4b097ea9924419acee53c51ffd38da6d441766dfa72eb89a86f502cb39aef8cb -->

- 2026-09-27T03:54:10Z — 最初の描画の待ちは、並べ読み（W2）のため2つの API の遅いほう（更新 p95 1 秒）にほぼ等しいと読んだ; API の時間は U2 の NFR6.5・U8 の NFR6.1 で押さえ済みで、画面の側の時間を置くかだけを Q1 で問うた。前の Intent の DSL の管理画面の前例（E2E で測って記録し、関門にしない）を推奨の根拠にした。
<!-- aidlc-wave-memory:u4-display-foundation:b63135b23482141125221ae3e958c55174cfaa4fb3b69ca51db5b7741d962779 -->

- 2026-09-27T03:54:10Z — フォントの重さは dist の実測（Noto Sans JP は japanese のサブセットが太さごとに woff2 約 1.0MB、合計 約 9.8MB）と CacheControlFilter の immutable の扱いから、要点として書いた; font-display: swap のため描画は止まらず、2回目以降は読み直さない。dist と WAR の大きさには上限を置かず、コード生成で増えを測って記録する案にした。
<!-- aidlc-wave-memory:u4-display-foundation:34ac0eff09a5282d7756af17d3867ce33a02f1e9011272ccf50eb693dcb006da -->

- 2026-09-27T04:36:02Z — 画面の側のセキュリティ（公開の API・要求のヘッダー・応答の値・CSP）と依存・テストを NFR9 の枝番（NFR9.1〜NFR9.11）に寄せた; 要件の NFR1〜NFR11 に当たる ID が無く、同じ Intent の U8 が当たる ID の無い要件を NFR9 に寄せた前例に合わせた。NFR4 は要約の確認どおり N/A とし、画面の側の扱いは NFR9.1 に置いた。
<!-- aidlc-wave-memory:u4-display-foundation:7e4e6ac2fce4556859db8b080a62bc118d9186d0a8a4e21007b0c9444a1ac83b -->

- 2026-09-27T04:36:02Z — アクセシビリティ（NFR7）・多言語（NFR8）・テスト（NFR9.8〜NFR9.11）は ui の単位で作る成果物に専用の文書が無いため tech-stack-decisions.md の3〜5節に置き、各文書の冒頭の表で置き場を示した; security-requirements.md はレビューの対象のため、セキュリティの要件だけに絞った。
<!-- aidlc-wave-memory:u4-display-foundation:71a95b2ae7fa42a370f7c01e2b97473a83ec78dae53c02fadb74faf6570484e0 -->

- 2026-09-27T04:36:02Z — 依存の版は npm の公開の登録簿を読み取りだけで確かめた（@fontsource/noto-serif-jp 5.3.0 OFL-1.1、axe-core 4.13.0 MPL-2.0、どちらも推移依存なし）; axe-core は今の lockfile の vitest-axe の推移依存と同じ版で、明示にしても版は1つにそろう見込み。make-you-chic-ui の固定先の前後のハッシュ（edb1f94…→735ef04…、8 コミット、peerDependencies は同じ）を tech-stack-decisions.md に記録した。
<!-- aidlc-wave-memory:u4-display-foundation:2ba5be8e8dea41746b35ca9b3c754af7f3f295c6c0e80b6787c79494ee519935 -->

- 2026-09-27T05:03:38Z — 狭い幅の確かめ方を U5 の質問（Q1）にした; U4 の tech-stack-decisions.md は狭い幅を検査に足すかを B5 の計画で依頼者に確かめるとしていた。U5 はこの Intent で最も横に広い表（8 列）を持ち、横に動く領域へのキーボードの届き方は実際のブラウザでしか確かめられないため、B5 の計画を待たずにこの段で決める形にした。
<!-- aidlc-wave-memory:u5-invitation-ui:111d72310ad92cb52567ec953fa668b4d0a409a64d419b8513f157f8489a3980 -->

- 2026-09-27T05:03:38Z — 招待の画面の時間の目標を Q2 にした; U4 の NFR6.1 はログインの画面だけに時間の目標を置き、ログインした後の画面には無い。前の Intent の DSL の管理画面の前例（NFR1.18、E2E で測って記録し関門にしない）に合わせる案を推奨にした。
<!-- aidlc-wave-memory:u5-invitation-ui:a1249fc909d12dd1d751f0094d047ce6f24a41fe93f82cfd8d2ec19a94d16757 -->

- 2026-09-27T05:03:20Z — トークンのリファラーの扱いは質問にせず要点にした; フラグメントはブラウザの決まりで Referer に載らず、既存の Referrer-Policy: same-origin と CSP の 'self' で外部の読み込みも無いため。新しい手当ては足さず、console の見張りを画面部品のテストに加える案にした。
<!-- aidlc-wave-memory:u6-registration-ui:14688e2ec0acf09ad12e7c0a24c8bc0841ff13ccaee67170030f5df3943f16e1 -->

- 2026-09-27T05:03:20Z — 二重の送信の防止・パスワードの画面の側の扱いは質問にしなかった; 機能設計の W8・D8・D9 で決まっているため、確かめ方（要求1回・本文の値が変わらない）だけを要点に書いた。
<!-- aidlc-wave-memory:u6-registration-ui:162e2507bf6132f3355545ccdee3fcca61883eeb43fbc27a8d7d5f34b271d865 -->

- 2026-09-27T05:03:20Z — E2E-1 の流れ全体の時間の目標は置かない案にした; 流れの成否を見るテストで、画面の時間は Q1 で別に測るため。リンクの取り出し方と SMTP の設定の渡し方は infrastructure-design の持ち主のまま。
<!-- aidlc-wave-memory:u6-registration-ui:544ffa3f2dc253da131f077579a7132e969b788984d1500ec13c37c71a2442ec -->

- 2026-09-27T05:03:20Z — 画面の側のセキュリティの要件（公開の API・回数の制限・入力・二重の送信・CSP）を NFR9.1〜NFR9.5 に寄せた; 要件に当たる ID が無く、U4・U8 の前例と同じ扱いにするため。トークン・メールアドレス・列挙の防止は NFR1・NFR2・NFR3 の枝番にした。
<!-- aidlc-wave-memory:u6-registration-ui:e6ce0bc84c09d687bac4fd9956670e5fa90ed0b29b54ef570bccae78f1f65693 -->

- 2026-09-27T05:03:20Z — U4 の実際のブラウザの検査で、登録の完了の画面はフォームと「リンクが使えない」の2つの状態を検査するとした; 状態で見た目が大きく変わるため。確かめ中・読み込めない・ログイン中の案内は vitest-axe に任せた。
<!-- aidlc-wave-memory:u6-registration-ui:2f08d16b6cf20941da1effe652b9fcb8b82ec51c071b81e73d43790b89c83523 -->

- 2026-09-27T05:02:19Z — 質問を作らず要点 13 件の要約の確認にした; API の時間は U2 の NFR6.1〜NFR6.4、アクセシビリティの確かめ方は U4 の NFR7.3・NFR7.5、送信中の表示と二重送信は機能設計の D10、パスワードの値の扱いは D13 と project.md の Forbidden で決まっている。project.md の「新しく決める論点が無いときは質問を作らない」に従った。
<!-- aidlc-wave-memory:u7-preferences-ui:9c71b9509704b0208d0fcc3abdeaa15ab0b16fe334655ff0da709ff9630f77a4 -->

- 2026-09-27T05:02:19Z — U7 の画面には画面の時間の数値の目標を置かないと読んだ; U4 の Q1 B（2 秒）はログインの画面の目標で、U7 の画面は開くたびの GET 1回と送信だけのため、U2 の API の目標で押さえる。要約の確認で依頼者が確かめられる形にした。
<!-- aidlc-wave-memory:u7-preferences-ui:63ced770f0051aed7c5cf8f154245a733bbb26abed15b0e60e478b88bacdb7a1 -->

- 2026-09-27T05:15:01Z — 画面の側のパスワードの値・応答の値・骨組みの変更の要件を NFR9 の枝番（NFR9.1〜NFR9.4）に寄せた; 要件に当たる ID が無く、どれもテストで確かめ方を決める要件のため。U4・U8 と同じ扱いで、氏名（初期値はメールアドレス）の扱いだけは NFR2.1 に置いた。
<!-- aidlc-wave-memory:u7-preferences-ui:f47d40bbb845b9a3d8dcd598cab565dad53df96cf2416fbe2d56afb4508892f7 -->

- 2026-09-27T05:15:01Z — 画面の時間の測り（NFR6.1〜NFR6.3）を、アクセシビリティの検査と同じく流れの E2E の本数に数えない読み方にした; U4 の NFR9.11 の前例に合わせた。測りの置き場（e2eTest の中か Build and Test の手順か）は B5 のコード生成の計画に残した。
<!-- aidlc-wave-memory:u7-preferences-ui:dbc9b4361607a122cddc440be09123a6e31d3fca76e17cfa31812bcee5b77706 -->
## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->


- 2026-09-27T03:08:50Z — NFR6 の [assumption] 1 秒と違い、パスワードの変更の成功を p95 2 秒にした（NFR6.3）; 依頼者の決定 Q1 A。要件の文書は書き換えず、performance-requirements.md の上流との差 P-D1 に記録した。
<!-- aidlc-wave-memory:u2-user-preferences:c675efe98681e65a6db88e6fa6076460a50aa23250773d6426da5dda7cd8242b -->

- 2026-09-27T03:08:50Z — まとめの確認の要約にあった監査の名前 PASSWORD_CHANGE_FAILED を使わず、承認済みの PASSWORD_CHANGED・FAILURE・CURRENT_PASSWORD_MISMATCH で書いた; その値は機能設計と契約 C8 に無いため、承認済みの設計を正とし、security-requirements.md の S-D1 に差を記録した（project.md の決まり）。
<!-- aidlc-wave-memory:u2-user-preferences:e41b84cae7dbbff0bdf9ea7105a83eecc76f6e9c2aeab1c5eb54d3146dcd3fb3 -->

- 2026-09-27T03:07:32Z — Q3 X と Q2 B により承認済みの BR1.1・BR1.2・BR1.3・BR1.5・BR1.8 と差が出るため、機能設計は書き換えず質問ファイルの要点 18 に差を並べた; 成果物（security-requirements.md・tech-stack-decisions.md）にも差として書く（project.md の決まり）。ヘルスチェックから外す設定は機能設計に無かった追加。
<!-- aidlc-wave-memory:u1-mail:7c3a63f8edf0c9728eae8361df9eead1e5acfe27e1553f6183b1e81a1a4647a4 -->

- 2026-09-27T03:26:07Z — F1 B・F2 A・F3 A で BR1.3（数でないポートで起動が止まる、protocol の点検）・BR1.4（NONE は分類の結果で判定）・BR1.5（587 の既定が無くなり 25・465 になる）・BR1.8（TLS を弱める値も README の運用で扱う）の差が増えたため、要点 18 に足した; README に書く運用で設定しない値の一覧を要点 8 に置いた。
<!-- aidlc-wave-memory:u1-mail:c3bbc40eda21167319ef4d6c68a8f553f5def846d777c9d546a0e83fe2bb195d -->

- 2026-09-27T04:34:05Z — 要件 NFR6 の招待の 5 秒を、受け手が正常なときの目標とした（performance-requirements.md の P-D1）; U1 で全体の上限を作らないと決まっており、遅れ続ける受け手では数倍になりうるため。要件の文書は書き換えていない。
<!-- aidlc-wave-memory:u3-invitation:09c692b84f634b75119002ade3a99937d0a8054f543167cb766a10d03eae07ce -->

- 2026-09-27T04:34:05Z — 登録の完了の同じメールアドレスの利用者がいる拒否（BR7.4）を、1 秒の目標（NFR6.5）の対象から外した; 今の createUser は重なりを確かめる前にハッシュを計算しうるため、成功と同じ重さになる。同時の登録でしか起きないまれな場合。
<!-- aidlc-wave-memory:u3-invitation:231c1147fe8a4a9d0d2731f28dfcb9cc25d9531bdeea44004c3733f50f82b63f -->

- 2026-09-27T04:36:02Z — NFR7.3 の横のはみ出しの確かめは既定の表示の幅（Desktop Chrome）だけにし、interaction-spec.md の狭い幅（768px 未満）は検査に入れなかった; Q2 B の答えは組の数だけを決めており、幅を足すと組が倍になるため。上流との差に書き、B5 の計画で依頼者に確かめることにした。
<!-- aidlc-wave-memory:u4-display-foundation:d8adff5c142cf3011217b0db76ed29225205f7d6ba21ef44a47eea28d395aef2 -->

- 2026-09-27T05:10:31Z — U4 の NFR7.3（既定の幅だけ）に幅 375px の6組を足した（NFR7.4）; 依頼者の決定（Q1: B）で、U4 が B5 の計画に回していた点をこの段で決めた。U4 の文書は書き換えず、tech-stack-decisions.md の上流との差に追加として書いた。
<!-- aidlc-wave-memory:u5-invitation-ui:62e7daa5960dae197eabe49b961bef7d5712bc0e1e7e53f4137ad8a61b80151c -->

- 2026-09-27T05:10:31Z — 画面の側のセキュリティと依存の要件を NFR9 の枝番に寄せた; 要件に当たる ID が無いため、U4・U8 の前例と同じく NFR9.1〜NFR9.5 とし、security-requirements.md の上流との差と traceability.json の NFR9 の target に書いた。
<!-- aidlc-wave-memory:u5-invitation-ui:39b8f5b97dbe48423a42186f1d1949b89ee705314eef2d9991f382cc7ab83fc4 -->

- 2026-09-27T05:03:20Z — U4 の NFR7.3 に幅 375px の6組と答えの差し替えを足したが、U4 の文書は書き換えなかった; 承認済みの文書を書き換えない決まりのため、tech-stack-decisions.md の上流との差に記録した。E2E-1 の中でリンクを 5 回開く計測の手順も W13 との差として記録した。
<!-- aidlc-wave-memory:u6-registration-ui:bdfab1e7b8b685e2dbae2f25a0f29cc6f851dacfbb9b76e9596ffd0fb43f608a -->

- 2026-09-27T05:15:01Z — U4 の NFR7.3（既定の幅だけ）に幅 375px の6組を足す形を、U4 の文書を書き換えずに U7 の NFR7.4 と上流との差に記録した; U5 の段の共通の決定による。B5 のコード生成の計画で U4 の検査に足す。
<!-- aidlc-wave-memory:u7-preferences-ui:49b01a7c24529ae706b4984263ab32d6806997a3421a2caf10729c1aeb969eb1 -->
## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->


- 2026-09-27T03:08:50Z — 今のパスワードの誤りを制限せず、残る危険 R1 として受け入れた（NFR4.5、Q2 A）; 仕組みが増えない代わりに、漏れたトークンでロックを通らずに試せる。アクセストークンは 5 分だが、リフレッシュトークン（24 時間）が漏れていれば試せる時間はその有効期限までになることも R1 の根拠に書き添えた。
<!-- aidlc-wave-memory:u2-user-preferences:ce87b5f1ece1908bde0064eaf54de5447c3f31870e32a671ee9734307fb6ec48 -->

- 2026-09-27T03:08:50Z — 上流の NFR の枝番が無い行（パスワードの秘密・入力の上限・監査の失敗）を、NFR2（個人情報の扱いと同じ扱い）と NFR9（必須のテスト）の枝番に寄せた; traceability の検査を通すため、どの行にも上流の ID を付けた。NFR2.2 のパスワードの秘密は出典に project.md の Forbidden を並べた。
<!-- aidlc-wave-memory:u2-user-preferences:c95a8aac52d77aff23692e71f4c8673d0f90b4e37d1834cc2d9e30216adbe814 -->

- 2026-09-27T03:11:02Z — 内部DB に触れないことを構造の検査（NFR5.1）に加え、接続を借りた回数が増えない結合テスト（NFR5.2）でも確かめる形にした; U4 の W2 で U8 の応答が全画面の最初の描画を待たせるため、認証の段階を含めて確かめる。測り方（HikariCP の指標など）の具体はコード生成で決める余地を残した。
<!-- aidlc-wave-memory:u8-instance-appearance:8161dafaa42446fb9f4f9ac294057ec19c55c23020dd4a8f4b757b8ae1ecf01d -->

- 2026-09-27T03:11:02Z — 回数の制限を設けない判断（NFR4.6）を、受け入れる危険（大量の要求でスレッドが占められる）と見直しの時点（配備先が決まったとき）つきで記録した; 設けないことを確かめるテストは無いため、記録だけにした。
<!-- aidlc-wave-memory:u8-instance-appearance:5e5866af4d61c0c1fa4e18cd57ee619dc1a8c82db621401d323fc5b2ddf912bd -->

- 2026-09-27T02:53:05Z — Spring Boot のメールの自動設定を使わない案を推した; spring.mail.properties.* が環境変数から mail.debug を有効にできる口になり BR1.8 を破り、メールのヘルスチェックが SMTP に接続するため。代わりに送信の部品を U1 が自分で組み立てる手間が増える。
<!-- aidlc-wave-memory:u1-mail:46129847504744a5d9286cd301e2e0a5ea9dac9f1c7cdfb2b723dc7a1b53e1fc -->

- 2026-09-27T02:53:05Z — テスト用の受け手に SubEtha SMTP を推した; GreenMail は STARTTLS を受け付けられず BR1.5 の STARTTLS の成功を確かめられず、JUnit 4（EPL 1.0）と Angus Mail をまとめた JAR を連れてくるため。GreenMail のほうが公開が新しく機能は多い。
<!-- aidlc-wave-memory:u1-mail:6da080dc117750f2bc6d679c4efc362956ddef61854da3c0e695f28180405d0b -->

- 2026-09-27T04:34:05Z — 回数の制限なし（Q2 A）と有効期限の上限なし（Q3 C）を、残る危険 R1・R2 として security-requirements.md に置いた; 仕組みを持たずに済む代わりに、未認証の要求で監査の行を増やせる点と、受信箱に残ったリンクが長く使える点が残る。
<!-- aidlc-wave-memory:u3-invitation:d5d3cb5270dc6fcade40339188dd649184920665d4b2937de081e1a61992ab23 -->

- 2026-09-27T04:34:05Z — 登録の完了は bcrypt の間も接続と行の排他を持つ（Q4 A、NFR5.2）; 契約 C2 と U2 の BR5.3 を変えずに済む代わりに、U2 のパスワードの変更と作りがそろわない。接続が足りるかは NFR5.3 の k6 で確かめる。
<!-- aidlc-wave-memory:u3-invitation:9535eaeddc17f305595fb91ab64cae69d711b5a5d288494af7353dfa78870f42 -->

- 2026-09-27T04:34:05Z — 負荷の試験の受け手に Mailpit を使い、登録の完了のトークンの用意（Mailpit の API で取り出すか、既知のハッシュで行を入れるか）を手順書の段に残した; 今の perf/README.md には受け手を起動する手順が無い。
<!-- aidlc-wave-memory:u3-invitation:434a75093248947e6421c1a06bd2e64173b6ef67dd4dea778273c17417882ff7 -->

- 2026-09-27T04:36:02Z — Playwright 用の包み（@axe-core/playwright）ではなく axe-core だけを明示で足す形にした; 依存を1つに抑え、vitest-axe と版をそろえられる代わりに、テストのページへの読み込みを自分で書く。アプリの CSP（script-src 'self'）は緩めず、読み込みの扱いは検査のブラウザのコンテキストだけで行う（NFR9.4）。具体の方法はコード生成で決める。
<!-- aidlc-wave-memory:u4-display-foundation:55c6b31ddc3f63ba495d04d7627c85f6d9948d9aa77006c311cd8f146cbda597 -->

- 2026-09-27T04:36:02Z — NFR6.1 の 2 秒は 5 回すべてが収まることを目標にし、統合の関門にはしない形にした; ブラウザと PC の状態で揺れるため、前の Intent の DSL の管理画面と同じく測って記録する。超えたときは目標を緩めず、どこが遅いかを切り分けて依頼者に相談する。
<!-- aidlc-wave-memory:u4-display-foundation:3261e5d13533f7c0adbb82cc67beab7ee4e8b40103e44750ba3b566f1d1e7566 -->

- 2026-09-27T05:03:38Z — 画面の側に要求の時間切れを置かない形を要点 3 として記録した; 既存の ApiClient は時間切れを持たず、待ちの上限はサーバーの SMTP の時間切れに任せる。受け手が遅れ続ける既知の限界（U1 の NFR6.3）の間は招待の Modal が閉じられないまま続くことを、画面の側でも引き継ぐ代わりに、設計を増やさない。
<!-- aidlc-wave-memory:u5-invitation-ui:97ec4a3f601c75839c1dceb21bb75d9787203a679378d3269934536742b8cebf -->

- 2026-09-27T05:03:20Z — 狭い幅の確かめ（Q3）は U4 が「B5 の計画で確かめる」とした点を前に出して問うた; 招待メールから開く登録の完了の画面はスマートフォンで開かれやすく、ラジオ3組と lg の組み合わせが最も崩れやすいため。推奨は U6 の画面だけに足す B とし、U5・U7 に及ぼす C は単位をまたぐため推奨にしなかった。
<!-- aidlc-wave-memory:u6-registration-ui:013e88664d71fbf36e912a7be901eeb3b9bbf677b4f135b8e40138b44f2d9954 -->

- 2026-09-27T05:03:20Z — 狭い幅の Q3 を取り下げ、決まっていることへ移した; U5 の質問で U5〜U7 に共通する依頼者の決定（幅 375px、テーマ2×文字の大きさ3の6組、B5 ですべての画面）が出たため。幅は推奨にしていた 360px から 375px にそろえた。
<!-- aidlc-wave-memory:u6-registration-ui:3c8c450500622485e84fd77fdf09f52a4762726a227d18e1cc9b30b8cc3be710 -->

- 2026-09-27T05:03:20Z — フォームの状態の検査を要求の差し替えで出す（Q2 B）代わりに、本物の確かめの API の応答の形を通らない; 受け手の手段に頼らず検査を独立させるため。応答の形の食い違いは U3 の契約 C6 の結合テストと E2E-1 で見つける前提とし、tech-stack-decisions.md の 3.1 に記録した。
<!-- aidlc-wave-memory:u6-registration-ui:73c1ac71490fbaddb62ebe9402489cc99b4c287b6c23fca3d5c4654363530ea1 -->
## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

- 2026-09-27T02:48:25Z — V7 の displayName は既定の値なしの必須のため、1つ前の版のアプリが初期管理者を作る場面（利用者が1人もいないとき）だけ追記が失敗しうる; Flyway の既定が知らない新しい移行を無視すること、Hibernate の validate が余分な列を許すことは見込みで、まだ確かめていない。確かめは NFR 設計・基盤の設計に回した。
<!-- aidlc-wave-memory:u2-user-preferences:54c708038c60ab838959882552e9f33bda45b40763ffc2ba7009561886f474c4 -->

- 2026-09-27T02:48:25Z — Q2 で B か C を選ぶと、承認済みの BR4.5（誤りを数えない）と違う決まりになる; 機能設計の文書は書き換えず、成果物に上流との差として記録する必要がある（project.md の決まり）。B では V7 に回数の列が増える。
<!-- aidlc-wave-memory:u2-user-preferences:1d274f9b1bee392c4f67f5f5d3f1b04bd854058c579463136d9c0cd4ffc8edfe -->

- 2026-09-27T02:48:18Z — 観測性の要件に当てる要件の NFR の ID が無い; 起動時の警告のログ（BR2.1）や指標は FR8.2 由来で、NFR1〜NFR11 に合う ID が無い。成果物の段で、NFR9 などに寄せるか、FR8.2 を出典とした注記にするかを決める。
<!-- aidlc-wave-memory:u8-instance-appearance:f4b9718485946e0d2c45a6bec857dada4ff7c614fb94380e56d1ab81d79afbfc -->

- 2026-09-27T02:53:05Z — 送信の全体の上限を作らないため、遅れ続ける受け手と DNS の待ちでは時間切れの値を超えうる; 全体を打ち切るには別のスレッドが要り ADR-009 と合わないため既知の限界として記録する案にした。依頼者が全体の上限を求めるかは Q1 の答えとあわせて確かめる。
<!-- aidlc-wave-memory:u1-mail:a5478709c7e2d6eab2afd2c6454163fbf010014aa89aaa796bda42b8a24afabf -->

- 2026-09-27T03:07:32Z — 答えの分析で残った4点を追加の質問 F1〜F4 にした; F1 暗号化の指定（MailProperties に1つの項目が無く STARTTLS の 587 が自動にならない）、F2 数でないポート（Integer の結び付けで起動が止まり BR1.3 と食い違う）、F3 spring.mail.properties から TLS を弱める値・mail.debug を入れられたときの守り、F4 「取得元は Maven Central だけ」がビルドのプラグインにも当たるか。
<!-- aidlc-wave-memory:u1-mail:def2181de38e86093cdf2274847c0bf498268a23b9d0da7e437ea06e57fe7f19 -->

- 2026-09-27T03:07:32Z — 時間切れは spring.mail.properties に置き、U1 が起動時に getJavaMailProperties から読んで点検する案にした; 部品は読めない値を無制限として扱うおそれがあるため。環境変数から点を含む map の鍵へ結び付くかは B1 で確かめる。
<!-- aidlc-wave-memory:u1-mail:b78da26942c638b464ed81a0837d99a810243984c3c6e772577e99097af14f63 -->

- 2026-09-27T03:26:07Z — F1 B の説明（STARTTLS が必須でなければ「設定がない」）と F3 A（starttls.required=false を含む TLS を弱める値に守りを足さない）が、starttls.enable=true・required が true でない組でぶつかるため、追加の質問 F5 にした; 推奨は NONE と同じに分類し、資格情報があるときだけ BR1.4 で送らない案（資格情報を平文に流さない趣旨を保ちつつ、ほかは F3 A のとおり運用で扱う）。
<!-- aidlc-wave-memory:u1-mail:8f0caac8c82c35635587c8f3d7a560fcde71e1109ce9a2286ddd99450bc29f74 -->

- 2026-09-27T03:26:07Z — F1 B で、SMTPS は protocol smtps と ssl.enabled の2通りを SMTPS に分類し、両方と STARTTLS の指定が重なるときは SMTPS を優先、知らない protocol は「設定がない」とする案にした; 時間切れの点検も使う protocol の鍵（mail.smtps.* か mail.smtp.*）で行う。質問にしなかったのは、Spring Boot と部品の動きから一通りに決まるため。
<!-- aidlc-wave-memory:u1-mail:35967e7e09c4ad321f57ccb7010d4c85e2c2285379f9472dd0bce8790266663a -->

- 2026-09-27T03:54:09Z — 登録の完了の bcrypt をトランザクションの中で計算するかを Q4 にした; 今の createUser は @Transactional の中で encode を呼び、BR7.3 と契約 C2 のままだと接続と招待の行の排他を約 278 ms 持つ。B を選ぶと C2 と U2 の承認済みの BR5.3 を変える差になる。
<!-- aidlc-wave-memory:u3-invitation:3ec1a92f9a8a583eb6a8bc726f1daa00ae2e5205678e5f7803552d9957cf7786 -->

- 2026-09-27T03:54:09Z — 有効期限の上限を Q3 にした; 承認済みの BR1.6 は下限だけで上限が無く、上限を足すと承認済みの決まりへの追加の差になる。
<!-- aidlc-wave-memory:u3-invitation:ab21e5993201a2882efbde3fac234613bcbf4c98979664186a95bd0a02a88c9e -->

- 2026-09-27T03:54:10Z — Q2 B（Playwright で axe-core を流す検査）は team.md の「E2E は Intent ごとに代表の流れを1本まで」と食い違いうる; 流れではない検査として本数に数えない読み方を選択肢に書いたが、依頼者が B を選んだときは、この読み方でよいかを追加の質問で確かめる必要がある。axe-core（MPL-2.0）は今も vitest-axe の推移依存として入っているが、採用の理由の記録は見当たらない。
<!-- aidlc-wave-memory:u4-display-foundation:364d2e488aec280bec630befd16bf63e3bd3a1b2fadc71ada9a1a367b1091be9 -->

- 2026-09-27T04:36:02Z — 上の点は依頼者が Q2 B とともに受け入れた（流れではない検査として本数に数えない）; tech-stack-decisions.md の NFR9.11 と上流との差に記録し、axe-core の採用の理由は同じ文書の 2.1 に ADR 形式で残した。Noto Serif JP のフォントのファイルの大きさは未測定のままで、NFR6.5 でコード生成が測る。
<!-- aidlc-wave-memory:u4-display-foundation:773f03052f7367af22f2bbd94e98f00df7e1eb68e697af49fc0182f5d7d47fda -->

- 2026-09-27T05:10:31Z — 画面の時間の測定（NFR6.1・NFR6.2）と行ありの検査（NFR7.3・NFR7.4）は、E2E の WAR で招待を使える設定に頼る; 今の frontend/playwright.config.ts は SMTP とベース URL を渡しておらず、招待を置けない。渡し方は infrastructure-design の持ち主として前提に書いた（U6 の E2E-1 と同じ前提）。
<!-- aidlc-wave-memory:u5-invitation-ui:a48c04aa66da5b431ea3f4f3a2b910f52ade1d703234ceb62839bc36fdf7f90d -->

- 2026-09-27T05:03:20Z — 実際のブラウザのアクセシビリティの検査でフォームの状態を出す手段（Q2）; 本物のトークンには受け手の手段（infrastructure-design で未定）が要るため、要求の差し替え（page.route）で出す B を推奨にした。依頼者の答えを待つ。
<!-- aidlc-wave-memory:u6-registration-ui:642e04ab65089be1ea7323d1303fd0ce951b41017e8e891346f391e04553e956 -->

- 2026-09-27T05:02:19Z — 狭い幅（768px 未満）を実際のブラウザの検査に足すかは、この段では決めなかった; U4 の NFR 要件の 6節で B5 のコード生成の計画で依頼者に確かめるとしたため。U5〜U7 の画面にまとめて当てる論点で、U7 だけで決めると単位ごとに扱いが割れる。
<!-- aidlc-wave-memory:u7-preferences-ui:d05a771c3dc43067e2e262417299fbd2bde78a275b7a5b4be0a141b593a90626 -->

- 2026-09-27T05:10:00Z — U5 の段の依頼者の決定で、狭い幅と画面の時間の扱いが決まり、質問のファイルを直した; 狭い幅は幅 375px の6組を検査に足して B5 で U7 にも当てる。画面の時間は目標を置いて Build and Test で5回ずつ測るため、「目標を置かない」とした読みをやめ、U7 の場面と値を Q1 として尋ねる形にした。
<!-- aidlc-wave-memory:u7-preferences-ui:c0f0e7c7caa24d5afc2800639544286d539e3f48843800c353dfd8e4b642bbf3 -->
