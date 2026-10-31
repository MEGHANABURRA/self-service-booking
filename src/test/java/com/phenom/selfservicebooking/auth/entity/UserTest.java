package com.phenom.selfservicebooking.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserTest {

    @Test
    void assignsRandomUuidBeforeInsert() {
        User first = new User();
        User second = new User();
        first.assignUuid();
        second.assignUuid();

        assertThat(first.getUserUuid()).isNotNull().isNotEqualTo(second.getUserUuid());
    }

    @Test
    void keepsExistingUuid() {
        UUID uuid = UUID.randomUUID();
        User user = new User();
        ReflectionTestUtils.setField(user, "userUuid", uuid);

        user.assignUuid();

        assertThat(user.getUserUuid()).isEqualTo(uuid);
    }
}
