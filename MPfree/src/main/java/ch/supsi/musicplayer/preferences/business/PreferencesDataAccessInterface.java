package ch.supsi.musicplayer.preferences.business;

import java.util.Properties;

public interface PreferencesDataAccessInterface {
    Properties getPreferences();
    boolean savePreferences(Properties preferences);
}
