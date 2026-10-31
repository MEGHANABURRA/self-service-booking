package com.phenom.selfservicebooking.notification;

import java.util.Locale;

@FunctionalInterface
interface PhraseTranslator {

    String translate(String phrase, Locale targetLocale);
}
