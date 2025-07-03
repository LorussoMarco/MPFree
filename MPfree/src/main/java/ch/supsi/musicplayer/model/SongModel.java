package ch.supsi.musicplayer.model;

import ch.supsi.musicplayer.service.LastFmService;
import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.util.concurrent.CompletableFuture;

public class SongModel {
    private final String title;
    private String artist;
    private String album;
    private int duration;
    private final File file;
    private final LastFmService lastFmService;
    
   
    public SongModel(File file) {
        this.file = file;
        this.lastFmService = new LastFmService();
        
        // Estrai artista e titolo dal nome del file
        String[] metadata = LastFmService.parseFilename(file.getName());
        this.artist = metadata[0].isEmpty() ? "Unknown Artist" : metadata[0];
        this.title = metadata[1];
        this.album = "Unknown Album";
        this.duration = 0;
        
        fetchMetadata();
    }
    
    private void fetchMetadata() {
        CompletableFuture.runAsync(() -> {
            // Se abbiamo un artista dal nome del file, usalo
            if (!artist.equals("Unknown Artist")) {
                lastFmService.getTrackInfo(artist, title).ifPresent(this::updateMetadata);
            } else {
                // Altrimenti cerca solo per titolo
                lastFmService.getTrackInfo("", title).ifPresent(this::updateMetadata);
            }
        });
    }
    
    private void updateMetadata(LastFmService.SongMetadata metadata) {
        this.artist = metadata.getArtist();
        this.album = metadata.getAlbum();
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getArtist() {
        return artist;
    }
    
    public String getAlbum() {
        return album;
    }
    
    public int getDuration() {
        if (duration == 0) {
            try {
                FileInputStream fis = new FileInputStream(file);
                BufferedInputStream bis = new BufferedInputStream(fis);
                try {
                    javazoom.jl.decoder.Bitstream bitstream = new javazoom.jl.decoder.Bitstream(bis);
                    javazoom.jl.decoder.Header header = bitstream.readFrame();
                    
                    if (header != null) {
                        int totalFrames = 0;
                        double msPerFrame = header.ms_per_frame();
                        
                        while (header != null) {
                            totalFrames++;
                            bitstream.closeFrame();
                            header = bitstream.readFrame();
                        }
                        
                        duration = (int)((totalFrames * msPerFrame) / 1000.0);
                    }
                } finally {
                    try {
                        bis.close();
                        fis.close();
                    } catch (Exception e) {
                        // Ignore close errors
                    }
                }
            } catch (Exception e) {
                // In case of error, return 0
                duration = 0;
            }
        }
        return duration;
    }
    
    public File getFile() {
        return file;
    }
    
    @Override
    public String toString() {
        return title + " - " + artist;
    }
} 