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
package cherry.mastersmith.targetdb.config;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 対象DB の設定が「使える」ときだけ一致する条件。対象DB の接続（{@code targetDataSource}）は、このときだけ作る（BR1.2・BR1.3）。
 *
 * <p>点検は {@link TargetDbSettings#inspect} と同じで、ログは出さない（WARN は {@link TargetDataSourceConfig} が1回だけ出す）。
 */
class TargetDbUsableCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        TargetDbProperties properties = Binder.get(context.getEnvironment())
                .bind(TargetDbProperties.PREFIX, TargetDbProperties.class)
                .orElse(null);
        return properties != null && TargetDbSettings.inspect(properties) instanceof TargetDbSettings.Usable;
    }
}
