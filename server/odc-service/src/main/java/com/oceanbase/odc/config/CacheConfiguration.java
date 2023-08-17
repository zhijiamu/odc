/*
 * Copyright (c) 2023 OceanBase.
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
package com.oceanbase.odc.config;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.oceanbase.odc.service.iam.OrganizationService;
import com.oceanbase.odc.service.iam.model.Organization;
import com.oceanbase.odc.service.iam.util.FailedLoginAttemptLimiter;
import com.oceanbase.odc.service.regulation.ruleset.RuleService;
import com.oceanbase.odc.service.regulation.ruleset.model.Rule;

@Configuration
@EnableCaching
public class CacheConfiguration {

    private static final int FAILED_LOGIN_ATTEMPT_TIMES = 5;
    /**
     * 10 minutes lock if failed login attempt FAILED_LOGIN_ATTEMPT_TIMES times <br>
     * 10 * 60 * 1000L
     */
    private static final long FAILED_LOGIN_ATTEMPT_LOCK_TIMEOUT = 10 * 60 * 1000L;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    @Lazy
    private RuleService ruleService;

    @Bean("clientAddressLoginAttemptCache")
    public LoadingCache<String, FailedLoginAttemptLimiter> clientAddressLoginAttemptCache() {
        return Caffeine
                .newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofHours(1L))
                .build(key -> new FailedLoginAttemptLimiter(FAILED_LOGIN_ATTEMPT_TIMES,
                        FAILED_LOGIN_ATTEMPT_LOCK_TIMEOUT));
    }

    @Bean("userId2OrganizationsCache")
    public LoadingCache<Long, List<Organization>> userId2OrganizationsCache() {
        return Caffeine
                .newBuilder().maximumSize(10000).expireAfterWrite(Duration.ofMinutes(2L))
                .build(organizationService::listOrganizationsByUserId);
    }


    @Bean("rulesetId2RulesCache")
    public LoadingCache<Long, List<Rule>> rulesetId2RulesCache() {
        return Caffeine
                .newBuilder().maximumSize(10000).expireAfterWrite(Duration.ofMinutes(2L))
                .build(ruleService::listAllFromDB);
    }

    @Bean("defaultCacheManager")
    public CacheManager defaultCacheManager() {
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(
                Duration.ofSeconds(600));
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setAllowNullValues(false);
        cacheManager.setCaffeine(caffeine);
        return cacheManager;
    }

}
