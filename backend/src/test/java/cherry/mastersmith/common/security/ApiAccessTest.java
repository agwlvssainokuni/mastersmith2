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
package cherry.mastersmith.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * API の分類の印 {@link ApiAccess} と値 {@link ApiAccessLevel} の形の確かめ（機能設計 ENT-001・ENT-002、計画 D-1）。
 *
 * <p>保持が実行時でないと、実行時の検査が印を読めずに空振りするため、形を先に確かめる。
 */
class ApiAccessTest {

    @Test
    @DisplayName("the mark is retained at runtime so that the runtime check can read it")
    void retainedAtRuntime() {
        Retention retention = ApiAccess.class.getAnnotation(Retention.class);

        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }

    @Test
    @DisplayName("the mark can be put on a controller class or on a handler method only")
    void targetsTypeAndMethod() {
        Target target = ApiAccess.class.getAnnotation(Target.class);

        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactlyInAnyOrder(ElementType.TYPE, ElementType.METHOD);
    }

    @Test
    @DisplayName("the level has exactly the three values PUBLIC, AUTHENTICATED and ADMIN")
    void exactlyThreeLevels() {
        assertThat(ApiAccessLevel.values())
                .containsExactly(ApiAccessLevel.PUBLIC, ApiAccessLevel.AUTHENTICATED, ApiAccessLevel.ADMIN);
    }
}
