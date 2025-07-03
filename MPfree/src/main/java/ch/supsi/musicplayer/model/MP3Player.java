package ch.supsi.musicplayer.model;

import javafx.application.Platform;
import javafx.beans.property.*;
import javazoom.jl.player.Player;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

public class MP3Player {
    private static MP3Player instance;
    
    private final SimpleObjectProperty<SongModel> currentTrack = new SimpleObjectProperty<>();
    private final BooleanProperty isPlaying = new SimpleBooleanProperty(false);
    private final DoubleProperty currentTime = new SimpleDoubleProperty(0);
    private final DoubleProperty totalDuration = new SimpleDoubleProperty(0);
    private final StringProperty currentTimeString = new SimpleStringProperty("00:00");
    private final StringProperty totalTimeString = new SimpleStringProperty("00:00");
    
    private double volume = 0.5;  
    private double volumeBeforeMute = 0.5; 
    private boolean isMuted = false;  
    private File currentFile;
    private boolean isPaused = false;
    private long startTimeMillis = 0;
    private double pausedTimePosition = 0;
    private long bytesSkipped = 0;
    
    private final ExecutorService playerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });
    
    private final ScheduledExecutorService timerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });
    
    private ScheduledFuture<?> progressTask;
    private Player jlPlayer;
    private Future<?> playerFuture;
    private boolean isShutdown = false;
    
    private List<FloatControl> volumeControls = new ArrayList<>();
    
    private Playlist currentPlaylist;
    private int currentPlaylistIndex = -1;

    private final SimpleObjectProperty<Playlist> currentPlaylistProperty = new SimpleObjectProperty<>();
    private boolean isShuffleEnabled = false;
    private List<Integer> shuffleOrder = new ArrayList<>();

    private MP3Player() {
        initializeVolumeControls();
        setVolume(0.5); 
    }

    public static MP3Player getInstance() {
        if (instance == null) {
            instance = new MP3Player();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }
    
    /**
     * Inizializza i controlli di volume per tutti i dispositivi audio disponibili.
     * Cerca e configura i controlli del volume sia per le linee di input che di output.
     * Gestisce diversi tipi di controlli (VOLUME e MASTER_GAIN) per garantire
     * la massima compatibilità con diversi sistemi audio.
     */
    private void initializeVolumeControls() {
        try {
            Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
            
            for (Mixer.Info mixerInfo : mixerInfos) {
                try {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    processMixerLines(mixer, mixer.getTargetLineInfo());
                    processMixerLines(mixer, mixer.getSourceLineInfo());
                } catch (Exception e) {
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize volume controls: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processMixerLines(Mixer mixer, Line.Info[] lineInfos) {
        for (Line.Info lineInfo : lineInfos) {
            try {
                Line line = mixer.getLine(lineInfo);
                line.open();
                
                if (line.isControlSupported(FloatControl.Type.VOLUME)) {
                    FloatControl volumeControl = (FloatControl) line.getControl(FloatControl.Type.VOLUME);
                    volumeControls.add(volumeControl);
                } else if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                    volumeControls.add(gainControl);
                }
            } catch (Exception e) {
            }
        }
    }

    /**
     * Carica un file audio nel player e ne calcola la durata.
     * Gestisce sia il caricamento del file che l'aggiornamento dell'interfaccia utente.
     * Se il file ha già una durata nota, la utilizza, altrimenti la calcola dal file.
     * Gestisce anche gli errori di caricamento e aggiorna l'interfaccia di conseguenza.
     */
    public void load(File file) {
        stop();
        
        try {
            this.currentFile = file;
            SongModel song = new SongModel(file);
            Platform.runLater(() -> currentTrack.set(song));
            Platform.runLater(() -> {
                currentTime.set(0);
                currentTimeString.set("00:00");
            });

            if (song.getDuration() > 0) {
                updateDuration(song.getDuration());
            } else {
                calculateDurationFromFile(file);
            }
        } catch (Exception e) {
            System.err.println("Error loading file: " + file.getName());
            e.printStackTrace();
            Platform.runLater(() -> {
                totalDuration.set(0);
                totalTimeString.set("00:00");
            });
        }
    }

    private void updateDuration(double durationInSeconds) {
        Platform.runLater(() -> {
            totalDuration.set(durationInSeconds);
            totalTimeString.set(formatTime(durationInSeconds));
        });
    }

    /**
     * Calcola la durata di un file audio in due modi:
     * 1. Leggendo i frame MP3 e calcolando la durata esatta
     * 2. Se non è possibile leggere i frame, stima la durata basandosi sulla dimensione del file
     *    assumendo un bitrate medio di 128kbps
     */
    private void calculateDurationFromFile(File file) {
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            
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
                
                updateDuration((totalFrames * msPerFrame) / 1000.0);
            } else {
                long fileSize = file.length();
                updateDuration(Math.max(fileSize / (128 * 1024 / 8), 1));
            }
        } catch (Exception e) {
            long fileSize = file.length();
            updateDuration(Math.max(fileSize / (128 * 1024 / 8), 1));
        }
    }

    public void play() {
        if (currentFile == null) return;
        
        // Se è in pausa, riprendi dalla posizione in cui ci siamo lasciati
        if (isPaused) {
            resume();
            return;
        }
        
        stopPlayback();
        playFileFromBeginning();
    }
    
    private void playFileFromBeginning() {
        if (isShutdown) return;
        
        playerFuture = playerExecutor.submit(() -> {
            try (FileInputStream fis = new FileInputStream(currentFile);
                 BufferedInputStream bis = new BufferedInputStream(fis)) {
                
                jlPlayer = new Player(bis);
                
                startTimeMillis = System.currentTimeMillis();
                pausedTimePosition = 0;
                bytesSkipped = 0;
                isPaused = false;
                
                Platform.runLater(() -> isPlaying.set(true));
                startProgressTimer();
                jlPlayer.play();
                
                if (!Thread.currentThread().isInterrupted() && !isShutdown) {
                    Platform.runLater(() -> {
                        isPlaying.set(false);
                        currentTime.set(totalDuration.get());
                        currentTimeString.set(totalTimeString.get());
                        
                        if (currentPlaylist != null && !isPaused && !isShutdown) {
                            playNextInPlaylist();
                        }
                    });
                }
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted() && !isShutdown) {
                    e.printStackTrace();
                    Platform.runLater(() -> isPlaying.set(false));
                }
            }
        });
    }

    public void pause() {
        if (!isPlaying.get() || jlPlayer == null) return;
        
        // Calcola la posizione corrente prima di mettere in pausa
        pausedTimePosition = getCurrentTimePosition();
        isPaused = true;
        
        stopPlayback();
        stopProgressTimer();
        
        Platform.runLater(() -> isPlaying.set(false));
    }

    public void resume() {
        if (isPlaying.get() || !isPaused || currentFile == null) return;
        
        playFromPosition(pausedTimePosition);
    }
    
    /**
     * Riproduce un file audio da una posizione specifica.
     * Gestisce il salto dei byte iniziali per riprendere dalla posizione corretta,
     * aggiorna il timer di inizio e gestisce la riproduzione asincrona.
     * Gestisce anche gli errori di riproduzione e l'interruzione del thread.
     */
    private void playFromPosition(double positionInSeconds) {
        if (isShutdown) return;
        
        playerFuture = playerExecutor.submit(() -> {
            try (FileInputStream fis = new FileInputStream(currentFile)) {
                double pausePercent = Math.min(0.98, positionInSeconds / totalDuration.get());
                long fileSize = currentFile.length();
                bytesSkipped = (long)(fileSize * pausePercent);
                
                if (bytesSkipped > 0) {
                    fis.skip(bytesSkipped);
                }
                
                try (BufferedInputStream bis = new BufferedInputStream(fis)) {
                    jlPlayer = new Player(bis);
                    
                    startTimeMillis = System.currentTimeMillis() - (long)(positionInSeconds * 1000);
                    isPaused = false;
                    
                    Platform.runLater(() -> isPlaying.set(true));
                    startProgressTimer();
                    
                    jlPlayer.play();
                    
                    if (!Thread.currentThread().isInterrupted() && !isShutdown) {
                        Platform.runLater(() -> {
                            isPlaying.set(false);
                            currentTime.set(totalDuration.get());
                            currentTimeString.set(totalTimeString.get());
                            
                            if (currentPlaylist != null && !isPaused && !isShutdown) {
                                playNextInPlaylist();
                            }
                        });
                    }
                }
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted() && !isShutdown) {
                    e.printStackTrace();
                    Platform.runLater(() -> isPlaying.set(false));
                }
            }
        });
    }

    public void stop() {
        stopPlayback();
        stopProgressTimer();
        
        isPaused = false;
        pausedTimePosition = 0;
        bytesSkipped = 0;
        
        Platform.runLater(() -> {
            isPlaying.set(false);
            currentTime.set(0);
            currentTimeString.set("00:00");
        });
    }

    private void stopPlayback() {
        try {
            if (jlPlayer != null) {
                jlPlayer.close();
                jlPlayer = null;
            }
            
            if (playerFuture != null && !playerFuture.isDone()) {
                playerFuture.cancel(true);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception e) {
        }
    }

    public void seek(double percent) {
        if (currentFile == null || percent < 0.0 || percent > 1.0) return;
        
        // JLayer non supporta il seek diretto, quindi dobbiamo ripartire dalla nuova posizione
        boolean wasPlaying = isPlaying.get();
        
        stopPlayback();
        
        double duration = totalDuration.get();
        double seekTime = duration * percent;
        
        // Aggiorna il tracciamento della posizione
        pausedTimePosition = seekTime;
        
        Platform.runLater(() -> {
            currentTime.set(seekTime);
            currentTimeString.set(formatTime(seekTime));
        });
        
        if (wasPlaying) {
            playFromPosition(seekTime);
        } else {
            isPaused = true;
        }
    }

    public void volumeUp() {
        if (volume < 1.0) {
            // Incrementa di esattamente 0.1 (10%)
            double newVolume = volume + 0.1;
            // Arrotonda a 1 decimale per evitare problemi di precisione
            newVolume = Math.round(newVolume * 10) / 10.0;
            // Assicura che non superi 1.0
            setVolume(Math.min(newVolume, 1.0));
        }
    }

    public void volumeDown() {
        if (volume > 0.0) {
            // Decrementa di esattamente 0.1 (10%)
            double newVolume = volume - 0.1;
            // Arrotonda a 1 decimale per evitare problemi di precisione
            newVolume = Math.round(newVolume * 10) / 10.0;
            // Assicura che non scenda sotto 0.0
            setVolume(Math.max(newVolume, 0.0));
        }
    }

    /**
     * Imposta il volume del player gestendo diversi tipi di controlli audio:
     * - Controlli VOLUME standard
     * - Controlli MASTER_GAIN con curva di potenza per una migliore percezione
     * - Controllo del volume di sistema su Windows se non sono disponibili controlli Java
     * Arrotonda il volume a 1 decimale per evitare problemi di precisione.
     */
    public void setVolume(double value) {
        value = Math.min(1.0, Math.max(0.0, Math.round(value * 10) / 10.0));
        volume = value;
        
        for (FloatControl control : volumeControls) {
            try {
                if (control.getType() == FloatControl.Type.VOLUME) {
                    control.setValue((float) volume);
                } else if (control.getType() == FloatControl.Type.MASTER_GAIN) {
                    float min = control.getMinimum();
                    float max = control.getMaximum();
                    
                    if (volume <= 0.01) {
                        control.setValue(min);
                        continue;
                    }
                    float gain = min + (max - min) * (float)Math.pow(volume, 0.5);
                    control.setValue(Math.min(max, Math.max(min, gain)));
                }
            } catch (Exception e) {
                System.err.println("Failed to set volume on control: " + e.getMessage());
            }
        }
        
        if (volumeControls.isEmpty() && System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                int windowsVolume = (int)(65535 * volume);
                Runtime.getRuntime().exec("nircmd.exe setsysvolume " + windowsVolume);
            } catch (Exception e) {
            }
        }
    }
    
    private double getCurrentTimePosition() {
        if (isPaused) {
            return pausedTimePosition;
        } else if (isPlaying.get()) {
            return (System.currentTimeMillis() - startTimeMillis) / 1000.0;
        } else {
            return 0;
        }
    }
    
    private void startProgressTimer() {
        stopProgressTimer();
        
        progressTask = timerExecutor.scheduleAtFixedRate(() -> {
            if (isPlaying.get()) {
                double currentSeconds = getCurrentTimePosition();
                double duration = totalDuration.get();
                
                // Controlla se abbiamo raggiunto la fine
                if (currentSeconds >= duration) {
                    Platform.runLater(() -> {
                        currentTime.set(duration);
                        currentTimeString.set(totalTimeString.get());
                    });
                    return;
                }
                
                Platform.runLater(() -> {
                    currentTime.set(currentSeconds);
                    currentTimeString.set(formatTime(currentSeconds));
                });
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
    }
    
    private void stopProgressTimer() {
        if (progressTask != null && !progressTask.isDone()) {
            progressTask.cancel(false);
        }
    }
    
    private String formatTime(double timeInSeconds) {
        int minutes = (int) (timeInSeconds / 60);
        int seconds = (int) (timeInSeconds % 60);
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    public void shutdown() {
        isShutdown = true;
        stopPlayback();
        stopProgressTimer();
        playerExecutor.shutdownNow();
        timerExecutor.shutdownNow();
    }
    
    public SimpleObjectProperty<SongModel> currentTrackProperty() {
        return currentTrack;
    }
    
    public SongModel getCurrentTrack() {
        return currentTrack.get();
    }
    
    public BooleanProperty isPlayingProperty() {
        return isPlaying;
    }
    
    public boolean isPlaying() {
        return isPlaying.get();
    }
    
    public DoubleProperty currentTimeProperty() {
        return currentTime;
    }
    
    public DoubleProperty totalDurationProperty() {
        return totalDuration;
    }
    
    public StringProperty currentTimeStringProperty() {
        return currentTimeString;
    }
    
    public StringProperty totalTimeStringProperty() {
        return totalTimeString;
    }

    public void loadPlaylist(Playlist playlist) {
        if (playlist == null || playlist.getSongs().isEmpty()) {
            return;
        }
        
        this.currentPlaylist = playlist;
        this.currentPlaylistIndex = 0;
        
        Platform.runLater(() -> currentPlaylistProperty.set(playlist));
        
        SongModel firstSong = playlist.getSongs().get(0);
        load(firstSong.getFile());
    }

    public boolean toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled;
        if (isShuffleEnabled && currentPlaylist != null) {
            generateShuffleOrder();
        }
        return isShuffleEnabled;
    }

    public boolean isShuffleEnabled() {
        return isShuffleEnabled;
    }

    /**
     * Genera un nuovo ordine casuale per la playlist corrente.
     * Mantiene la traccia corrente come prima nella sequenza per una migliore esperienza utente.
     * Gestisce il caso in cui la playlist sia vuota o non sia stata caricata.
     */
    private void generateShuffleOrder() {
        if (currentPlaylist == null) return;
        
        int size = currentPlaylist.getSongs().size();
        shuffleOrder.clear();
        
        for (int i = 0; i < size; i++) {
            shuffleOrder.add(i);
        }
        
        Collections.shuffle(shuffleOrder);
        
        if (currentPlaylistIndex >= 0) {
            int currentIndex = shuffleOrder.indexOf(currentPlaylistIndex);
            if (currentIndex > 0) {
                Collections.swap(shuffleOrder, 0, currentIndex);
            }
        }
    }

    /**
     * Riproduce la traccia successiva nella playlist.
     * Gestisce sia la modalità normale che la modalità shuffle.
     * In modalità shuffle, mantiene l'ordine casuale e lo rigenera quando necessario.
     * Gestisce il wrapping della playlist (torna all'inizio quando si raggiunge la fine).
     */
    public void playNextInPlaylist() {
        if (currentPlaylist == null || currentPlaylistIndex < 0) {
            return;
        }
        
        if (isShuffleEnabled) {
            int currentShuffleIndex = shuffleOrder.indexOf(currentPlaylistIndex);
            if (currentShuffleIndex >= 0 && currentShuffleIndex < shuffleOrder.size() - 1) {
                currentPlaylistIndex = shuffleOrder.get(currentShuffleIndex + 1);
            } else {
                generateShuffleOrder();
                currentPlaylistIndex = shuffleOrder.get(0);
            }
        } else {
            currentPlaylistIndex++;
            if (currentPlaylistIndex >= currentPlaylist.getSongs().size()) {
                currentPlaylistIndex = 0;  
            }
        }
        
        SongModel nextSong = currentPlaylist.getSongs().get(currentPlaylistIndex);
        load(nextSong.getFile());
        play();
    }

    /**
     * Riproduce la traccia precedente nella playlist.
     * Gestisce sia la modalità normale che la modalità shuffle.
     * In modalità shuffle, mantiene l'ordine casuale e lo rigenera quando necessario.
     * Gestisce il wrapping della playlist (va alla fine quando si raggiunge l'inizio).
     */
    public void playPreviousInPlaylist() {
        if (currentPlaylist == null || currentPlaylistIndex < 0) {
            return;
        }
        
        if (isShuffleEnabled) {
            int currentShuffleIndex = shuffleOrder.indexOf(currentPlaylistIndex);
            if (currentShuffleIndex > 0) {
                currentPlaylistIndex = shuffleOrder.get(currentShuffleIndex - 1);
            } else {
                generateShuffleOrder();
                currentPlaylistIndex = shuffleOrder.get(shuffleOrder.size() - 1);
            }
        } else {
            currentPlaylistIndex--;
            if (currentPlaylistIndex < 0) {
                currentPlaylistIndex = currentPlaylist.getSongs().size() - 1; 
            }
        }
        
        SongModel prevSong = currentPlaylist.getSongs().get(currentPlaylistIndex);
        load(prevSong.getFile());
        play();
    }

    public Playlist getCurrentPlaylist() {
        return currentPlaylist;
    }

    public void playTrackAtIndex(int index) {
        if (currentPlaylist == null || 
            index < 0 || 
            index >= currentPlaylist.getSongs().size()) {
            return;
        }
        
        currentPlaylistIndex = index;
        
        SongModel song = currentPlaylist.getSongs().get(index);
        load(song.getFile());
        play();
    }

    public SimpleObjectProperty<Playlist> currentPlaylistProperty() {
        return currentPlaylistProperty;
    }

    public double getVolume() {
        return volume;
    }

    public boolean toggleMute() {
        if (isMuted) {
            isMuted = false;
            setVolume(volumeBeforeMute);
        } else {
            isMuted = true;
            volumeBeforeMute = volume;
            setVolume(0.0);
        }
        return isMuted;
    }
    
    public boolean isMuted() {
        return isMuted;
    }

    public void clearCurrentPlaylist() {
        stop();
        this.currentPlaylist = null;
        this.currentPlaylistIndex = -1;
        this.shuffleOrder.clear();
        Platform.runLater(() -> currentPlaylistProperty.set(null));
    }
} 