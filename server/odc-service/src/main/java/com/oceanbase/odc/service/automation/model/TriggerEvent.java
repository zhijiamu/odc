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
package com.oceanbase.odc.service.automation.model;

import org.springframework.context.ApplicationEvent;

import com.google.common.collect.ImmutableSet;

import lombok.NonNull;

public class TriggerEvent extends ApplicationEvent {

    public static final String OAUTH_2_FIRST_TIME_LOGIN = "OAuth2FirstTimeLogin";

    public static final String PUBLIC_ALIYUN_FIRST_TIME_LOGIN = "PublicAliyunFirstTimeLogin";

    public static final String USER_CREATED = "UserCreated";

    public static final String USER_UPDATED = "UserUpdated";

    public static final String LOGIN_SUCCESS = "LoginSuccess";

    public static boolean isUserChangeEvent(String eventName) {
        return ImmutableSet.of(USER_CREATED, USER_UPDATED, LOGIN_SUCCESS).contains(eventName);
    }


    private final String eventName;

    /**
     * Constructs a prototypical Event.
     *
     * @param source The object on which the Event initially occurred.
     * @throws IllegalArgumentException if source is null.
     */
    public TriggerEvent(@NonNull String eventName, Object source) {
        super(source);
        this.eventName = eventName;
    }

    public String getEventName() {
        return eventName;
    }
}
