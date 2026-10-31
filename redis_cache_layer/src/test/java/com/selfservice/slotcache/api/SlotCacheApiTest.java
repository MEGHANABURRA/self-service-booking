package com.selfservice.slotcache.api;

import com.selfservice.slotcache.EmbeddedRedis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End to end over HTTP, exactly the way the booking service will call it. */
@SpringBootTest(properties = "spring.data.redis.port=" + EmbeddedRedis.PORT)
@AutoConfigureMockMvc
class SlotCacheApiTest {

    static {
        EmbeddedRedis.start();
    }

    // Monday 14 Jan 2030, hosts in India: 10:00 IST = 04:30Z, 10:45 IST = 05:15Z.
    private static final String T1000 = "2030-01-14T04:30:00Z";
    private static final String T1045 = "2030-01-14T05:15:00Z";
    private static final String DAY = "from=2030-01-14T00:00:00Z&to=2030-01-15T00:00:00Z";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void flush() {
        try (var connection = redis.getRequiredConnectionFactory().getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    @Test
    void pageOpenCacheMissComputeBookAndInvalidate() throws Exception {
        // 1. Booking page opens: nothing cached yet.
        mvc.perform(get("/api/v1/drives/drive-1/slots?" + DAY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVE_NOT_LOADED"));

        // 2. Part 1, step 1: first request gets the compute lock ...
        mvc.perform(post("/api/v1/drives/drive-1/snapshots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hostIds\":[\"h1\",\"h2\",\"h3\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.compute").value(true))
                .andExpect(jsonPath("$.baseVersion").value(1));

        // ... a concurrent page open is told to wait, and reads see "loading".
        mvc.perform(post("/api/v1/drives/drive-1/snapshots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hostIds\":[\"h1\",\"h2\",\"h3\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compute").value(false));
        mvc.perform(get("/api/v1/drives/drive-1/slots?" + DAY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DRIVE_LOADING"));

        // 3. Part 1, step 2: booking service sends what it computed — slot → free hosts.
        mvc.perform(put("/api/v1/drives/drive-1/slots").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseVersion": 1, "slotLengthMinutes": 30, "slots": [
                                  {"start": "%s", "hostIds": ["h1", "h2", "h3"]},
                                  {"start": "%s", "hostIds": ["h1"]}
                                ]}""".formatted(T1000, T1045)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loadedHostSlots").value(4));

        // 4. Candidate sees the slots in their own timezone (London in January = UTC).
        mvc.perform(get("/api/v1/drives/drive-1/slots?" + DAY + "&timezone=Europe/London"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots", hasSize(2)))
                .andExpect(jsonPath("$.slots[1].start").value(T1045))
                .andExpect(jsonPath("$.slots[1].localStart").value("2030-01-14T05:15:00Z"));

        // 5. Three users take the three 10:00 hosts; the fourth is refused.
        for (String user : new String[]{"user-a", "user-b", "user-c"}) {
            mvc.perform(post("/api/v1/drives/drive-1/holds").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"holdId\":\"%s\",\"start\":\"%s\"}".formatted(user, T1000)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.end").value("2030-01-14T05:00:00Z"));
        }
        mvc.perform(post("/api/v1/drives/drive-1/holds").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holdId\":\"user-d\",\"start\":\"%s\"}".formatted(T1000)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));

        // 6. Part 2: h1's 10:00 booking is written; invalidate it (also drops h1 from 10:00 elsewhere).
        mvc.perform(post("/api/v1/hosts/h1/invalidations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"%s\",\"to\":\"2030-01-14T05:00:00Z\"}".formatted(T1000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drivesTouched").value(1))
                .andExpect(jsonPath("$.holdsCancelled").value(1));

        mvc.perform(get("/api/v1/drives/drive-1/slots?" + DAY))
                .andExpect(jsonPath("$.slots", hasSize(1)))
                .andExpect(jsonPath("$.slots[0].start").value(T1045));
    }

    @Test
    void stalePublishIsRejectedWithConflict() throws Exception {
        mvc.perform(post("/api/v1/drives/drive-1/snapshots").contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostIds\":[\"h1\"]}")).andExpect(status().isCreated());
        String body = """
                {"baseVersion": %d, "slotLengthMinutes": 30, "slots": [{"start": "%s", "hostIds": ["h1"]}]}""";
        mvc.perform(put("/api/v1/drives/drive-1/slots").contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted(1, T1000))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/drives/drive-1/snapshots").contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostIds\":[\"h1\"]}")).andExpect(status().isCreated());
        mvc.perform(put("/api/v1/drives/drive-1/slots").contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted(2, T1000))).andExpect(status().isOk());

        mvc.perform(put("/api/v1/drives/drive-1/slots").contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(1, T1045)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_SNAPSHOT"));
    }

    @Test
    void badInputIs400() throws Exception {
        mvc.perform(post("/api/v1/drives/drive-1/holds").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holdId\":\"bad|id\",\"start\":\"%s\"}".formatted(T1000)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/drives/drive-1/slots").param("timezone", "Not/AZone"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/drives/drive-1/snapshots").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hostIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void releaseIsIdempotent() throws Exception {
        mvc.perform(delete("/api/v1/drives/drive-1/holds/never-existed")).andExpect(status().isNoContent());
    }
}
