package com.phenom.selfservicebooking.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleCloudTranslationClientTest {

    @Test
    void translatesAndCachesPhraseByTargetLanguage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://translation.googleapis.com/language/translate/v2?key=test-key"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "q": "Booking confirmed",
                          "source": "en",
                          "target": "fr",
                          "format": "text"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "translations": [
                              {"translatedText": "Réservation confirmée"}
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        GoogleCloudTranslationClient client = new GoogleCloudTranslationClient(builder, "test-key");

        assertThat(client.translate("Booking confirmed", Locale.FRENCH))
                .isEqualTo("Réservation confirmée");
        assertThat(client.translate("Booking confirmed", Locale.CANADA_FRENCH))
                .isEqualTo("Réservation confirmée");

        server.verify();
    }

    @Test
    void requiresAnApiKeyForNonEnglishTranslation() {
        GoogleCloudTranslationClient client =
                new GoogleCloudTranslationClient(RestClient.builder(), "");

        assertThatThrownBy(() -> client.translate("Booking confirmed", Locale.FRENCH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_TRANSLATE_API_KEY");
    }
}
