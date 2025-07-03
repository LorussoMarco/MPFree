package ch.supsi.musicplayer.translations.application;

import java.util.List;

public interface TranslationsBusinessInterface {
    boolean isSupportedLanguageTag(String languageTag);
    boolean changeLanguage(String languageTag);
    String translate(String key);
    List<String> getSupportedLanguageTags();
}
