package ch.supsi.musicplayer.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LastFmService {
    private static final String BASE_URL = "http://ws.audioscrobbler.com/2.0/";
    private final HttpClient httpClient;
    
    // Pattern per estrarre artista e titolo da nomi file comuni
    private static final Pattern[] FILENAME_PATTERNS = {
        // Pattern per "Artist - Title"
        Pattern.compile("(.+?)\\s*-\\s*(.+)"),
        // Pattern per "Title (Official Video)"
        Pattern.compile("(.+?)\\s*\\([^)]+\\)"),
        // Pattern per "Title [Official Video]"
        Pattern.compile("(.+?)\\s*\\[[^\\]]+\\]"),
        // Pattern per "Title - Artist"
        Pattern.compile("(.+?)\\s*-\\s*(.+)")
    };

    private static final String API_KEY = loadApiKey();

    public LastFmService() {
        this.httpClient = HttpClient.newHttpClient();
    }

    public Optional<SongMetadata> getTrackInfo(String artist, String title) {
        // Prima prova con i parametri esatti
        Optional<SongMetadata> result = searchTrack(artist, title);
        
        // Se non trova nulla, prova a cercare solo per titolo
        if (result.isEmpty()) {
            result = searchTrack("", title);
        }
        
        return result;
    }
    
    private Optional<SongMetadata> searchTrack(String artist, String title) {
        try {
            // Pulisci il titolo da eventuali estensioni e caratteri speciali
            title = cleanTitle(title);
            
            String url = String.format("%s?method=track.search&api_key=%s&track=%s&format=json",
                    BASE_URL, API_KEY, encode(title));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonObject results = jsonResponse.getAsJsonObject("results");
                
                if (results != null && results.has("trackmatches")) {
                    JsonObject trackmatches = results.getAsJsonObject("trackmatches");
                    if (trackmatches.has("track") && trackmatches.getAsJsonArray("track").size() > 0) {
                        JsonObject track = trackmatches.getAsJsonArray("track").get(0).getAsJsonObject();
                        
                        String trackName = track.get("name").getAsString();
                        String artistName = track.get("artist").getAsString();
                        
                        // Se abbiamo un artista specifico, verifica che corrisponda
                        if (!artist.isEmpty() && !artistName.toLowerCase().contains(artist.toLowerCase())) {
                            return Optional.empty();
                        }
                        
                        // Ottieni i dettagli completi della traccia
                        return getTrackDetails(artistName, trackName);
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }
    
    private Optional<SongMetadata> getTrackDetails(String artist, String title) {
        try {
            String url = String.format("%s?method=track.getInfo&api_key=%s&artist=%s&track=%s&format=json",
                    BASE_URL, API_KEY, encode(artist), encode(title));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonObject track = jsonResponse.getAsJsonObject("track");

                if (track != null) {
                    String albumName = track.getAsJsonObject("album") != null ?
                            track.getAsJsonObject("album").get("title").getAsString() : "Unknown Album";
                    
                    int duration = track.has("duration") ? 
                            track.get("duration").getAsInt() : 0;

                    return Optional.of(new SongMetadata(
                            track.get("name").getAsString(),
                            track.get("artist").getAsJsonObject().get("name").getAsString(),
                            albumName,
                            duration
                    ));
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }
    
    private String cleanTitle(String title) {
        // Rimuovi l'estensione del file
        title = title.replaceFirst("[.][^.]+$", "");
        
        // Rimuovi caratteri speciali e parole comuni
        title = title.replaceAll("(?i)\\(official.*?\\)", "")
                    .replaceAll("(?i)\\[official.*?\\]", "")
                    .replaceAll("(?i)\\(lyrics.*?\\)", "")
                    .replaceAll("(?i)\\[lyrics.*?\\]", "")
                    .replaceAll("(?i)\\(audio.*?\\)", "")
                    .replaceAll("(?i)\\[audio.*?\\]", "")
                    .replaceAll("(?i)\\(hd.*?\\)", "")
                    .replaceAll("(?i)\\[hd.*?\\]", "")
                    .trim();
        
        return title;
    }
    
    public static String[] parseFilename(String filename) {
        String cleanName = filename.replaceFirst("[.][^.]+$", ""); // Rimuovi estensione
        
        for (Pattern pattern : FILENAME_PATTERNS) {
            Matcher matcher = pattern.matcher(cleanName);
            if (matcher.find()) {
                String first = matcher.group(1).trim();
                String second = matcher.groupCount() > 1 ? matcher.group(2).trim() : "";
                
                // Determina quale gruppo è artista e quale è titolo
                if (second.isEmpty()) {
                    return new String[]{"", first}; // Solo titolo
                } else if (first.toLowerCase().contains("official") || first.toLowerCase().contains("video")) {
                    return new String[]{"", second}; // Il secondo gruppo è il titolo
                } else {
                    return new String[]{first, second}; // Primo gruppo è artista, secondo è titolo
                }
            }
        }
        
        // Se nessun pattern corrisponde, restituisci il nome del file come titolo
        return new String[]{"", cleanName};
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String loadApiKey() {
        try (java.io.InputStream input = LastFmService.class.getClassLoader().getResourceAsStream("lastfm.properties")) {
            java.util.Properties prop = new java.util.Properties();
            if (input != null) {
                prop.load(input);
                return prop.getProperty("lastfm.api.key");
            } else {
                throw new RuntimeException("lastfm.properties file not found in resources");
            }
        } catch (java.io.IOException ex) {
            throw new RuntimeException("Error loading lastfm.properties", ex);
        }
    }

    public static class SongMetadata {
        private final String title;
        private final String artist;
        private final String album;
        private final int duration;

        public SongMetadata(String title, String artist, String album, int duration) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.duration = duration;
        }

        public String getTitle() { return title; }
        public String getArtist() { return artist; }
        public String getAlbum() { return album; }
        public int getDuration() { return duration; }
    }
} 