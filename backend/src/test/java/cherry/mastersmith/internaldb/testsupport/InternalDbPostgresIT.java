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
package cherry.mastersmith.internaldb.testsupport;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 内部DB の PostgreSQL の結合テストのクラスに付ける注釈（BR6.1）。JUnit のタグ {@value #TAG} を付け、通常の
 * {@code integrationTest} から外し、専用のタスク {@code internalDbPostgresTest} だけで流す。
 *
 * <p>拡張の登録の順は、ログの取り込み（{@link OutputCaptureExtension}）を先、コンテナの実行環境の確かめ
 * （{@link InternalDbPostgresRuntimeCheck}）を後にする。確かめが先だと、届かずに飛ばしたとき、ログの取り込みが始まらないまま後処理が
 * 失敗する（{@code targetdb} の {@code ExtensionOrderArchitectureTest} と同じ決まり）。順は境界テストで確かめる。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag(InternalDbPostgresIT.TAG)
@ExtendWith({OutputCaptureExtension.class, InternalDbPostgresRuntimeCheck.class})
public @interface InternalDbPostgresIT {

    /** JUnit のタグの名前。 */
    String TAG = "internal-db-postgres";
}
