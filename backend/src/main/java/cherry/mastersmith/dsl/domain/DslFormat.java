/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cherry.mastersmith.dsl.domain;

import java.time.Duration;

/**
 * DSL の書式の版と、読み込みの上限（契約 C4 の {@code DslFormat}、U2 の NFR 要件の NFR2.1〜NFR2.4・NFR3.6・NFR5.2）。
 *
 * <p>大きさの上限は 10MB（10,485,760 バイト）。承認済みの要件（NFR2）・機能設計（BR1.1）・契約 C4 の 5MB は、U3 の NFR 要件の
 * 決定で 10MB に上げた（U2 の NFR 要件の NFR2.1）。
 */
public final class DslFormat {

    /** アプリが対応する書式の版。 */
    public static final int CURRENT_VERSION = 1;

    /** 本文の大きさの上限（バイト。10MB = 10 × 1024 × 1024）。 */
    public static final int MAX_BYTES = 10 * 1024 * 1024;

    /** YAML の入れ子の深さの上限（文書の根から数えた、対応表と並びの段の数）。 */
    public static final int MAX_DEPTH = 50;

    /** コレクション（対応表・並び）を指す別名の数の上限。 */
    public static final int MAX_COLLECTION_ALIASES = 100;

    /** 別名を展開した後の節の数の上限。 */
    public static final int MAX_EXPANDED_NODES = 1_000_000;

    /** 正規表現（{@code pattern}）の長さの上限（文字）。 */
    public static final int MAX_PATTERN_LENGTH = 1_000;

    /** 正規表現1件の確かめ（組み立て）の待ちの上限。 */
    public static final Duration PATTERN_CHECK_TIMEOUT = Duration.ofMillis(100);

    /** 誤りに埋める、利用者が書いた値の長さの上限（文字）。 */
    public static final int MAX_ARGUMENT_LENGTH = 100;

    /** 同梱の JSON Schema の正本のクラスパスの場所。 */
    public static final String SCHEMA_RESOURCE = "dsl/dsl-schema-v1.json";

    /** 同梱の JSON Schema を公開する URL のパス（ログインなしで取れる。Infrastructure Design の決定 D）。 */
    public static final String SCHEMA_PUBLIC_PATH = "/dsl/dsl-schema-v1.json";

    private DslFormat() {}
}
