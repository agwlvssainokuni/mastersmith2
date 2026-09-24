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
package cherry.mastersmith.targetdb.domain;

/**
 * 読み取りの目的（NFR 設計の差: {@code readSchema(ReadPurpose)}）。目的ごとに問い合わせ1回の待ちの上限が決まる。読み取りの
 * 全体の上限は、生成・照合のどちらにも無い（照合の全体の上限は作らない。NFR 設計の承認の場の決定 B）。
 */
public enum ReadPurpose {
    /** 既定の DSL の生成（U3）。問い合わせ1回の上限は既定 20 秒。 */
    GENERATE,
    /** 適用中の DSL との照合（U4）。問い合わせ1回の上限は既定 5 秒。 */
    COMPARE
}
