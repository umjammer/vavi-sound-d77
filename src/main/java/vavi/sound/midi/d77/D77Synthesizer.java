/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.midi.d77;

import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.sound.midi.Instrument;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Patch;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Transmitter;
import javax.sound.midi.VoiceStatus;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import static vavi.sound.SoundUtil.volume;


/**
 * D77Synthesizer.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-02-01 nsano initial version <br>
 */
public class D77Synthesizer implements Synthesizer {

    private static final Logger logger = System.getLogger(D77Synthesizer.class.getName());

    private static final D77Driver lib = D77Driver.INSTANCE;

    private boolean isOpen;
    private SourceDataLine line;
    private Thread renderThread;
    private volatile boolean running;
    private final ConcurrentLinkedQueue<MidiMessage> messageQueue = new ConcurrentLinkedQueue<>();

    private final String dataFilePath = System.getProperty("vavi.sound.midi.d77.datafile", "src/main/resources/dswebWDM.dat");

    static {
        try {
            try (InputStream is = D77Synthesizer.class.getResourceAsStream("/META-INF/maven/vavi/vavi-sound-d77/pom.properties")) {
                if (is != null) {
                    Properties props = new Properties();
                    props.load(is);
                    version = props.getProperty("version", "undefined in pom.properties");
                } else {
                    version = System.getProperty("vavi.test.version", "undefined");
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String version;

    private static class D77Info extends Info {

        protected D77Info() {
            super("WebSynth D-77", "M-HT / Roman Pauer", "Software Synthesizer for WebSynth D-77", "Version " + version);
        }
    }

    static final Info info = new D77Info();

    @Override
    public Info getDeviceInfo() {
        return info;
    }

    @Override
    public void open() throws MidiUnavailableException {
        if (isOpen) return;

        try {
            lib.D77_InitializePointerOffset();

            byte[] data = Files.readAllBytes(Paths.get(dataFilePath));
            Pointer pData = lib.D77_AllocateMemory(data.length);
            if (pData == null) {
                throw new MidiUnavailableException("Failed to allocate memory for data file");
            }
            pData.write(0, data, 0, data.length);

            Pointer settingsMemory = lib.D77_AllocateMemory(new D77Driver.D77_SETTINGS().size());
            if (settingsMemory == null) throw new MidiUnavailableException("Failed to allocate memory for settings");
            D77Driver.D77_SETTINGS settings = new D77Driver.D77_SETTINGS(settingsMemory);

            settings.dwSamplingFreq = 44100;
            settings.dwPolyphony = 64;
            settings.dwCpuLoadL = 60;
            settings.dwCpuLoadH = 90;
            settings.dwRevSw = 1;
            settings.dwChoSw = 1;
            settings.dwMVol = 100;
            settings.dwRevAdj = 95;
            settings.dwChoAdj = 70;
            settings.dwOutLev = 110;
            settings.dwRevFb = 95;
            settings.dwRevDrm = 80;
            settings.dwResoUpAdj = 40;
            settings.dwCacheSize = 3;
            settings.dwTimeReso = 80;

            lib.D77_ValidateSettings(settings);

            if (lib.D77_InitializeDataFile(pData, data.length - 4) == 0) {
                throw new MidiUnavailableException("Failed to initialize data file");
            }

            if (lib.D77_InitializeSynth(settings.dwSamplingFreq, settings.dwPolyphony, settings.dwTimeReso) == 0) {
                throw new MidiUnavailableException("Failed to initialize synth");
            }

            lib.D77_InitializeUnknown(0);
            lib.D77_InitializeEffect(D77Driver.D77_EFFECT_Reverb, settings.dwRevSw);
            lib.D77_InitializeEffect(D77Driver.D77_EFFECT_Chorus, settings.dwChoSw);
            lib.D77_InitializeCpuLoad(settings.dwCpuLoadL, settings.dwCpuLoadH);

            Pointer paramsMemory = lib.D77_AllocateMemory(new D77Driver.D77_PARAMETERS().size());
            if (paramsMemory == null) throw new MidiUnavailableException("Failed to allocate memory for parameters");
            D77Driver.D77_PARAMETERS params = new D77Driver.D77_PARAMETERS(paramsMemory);

            params.wChoAdj = (short) settings.dwChoAdj;
            params.wRevAdj = (short) settings.dwRevAdj;
            params.wRevDrm = (short) settings.dwRevDrm;
            params.wRevFb = (short) settings.dwRevFb;
            params.wOutLev = (short) settings.dwOutLev;
            params.wResoUpAdj = (short) settings.dwResoUpAdj;
            lib.D77_InitializeParameters(params);

            lib.D77_InitializeMasterVolume(settings.dwMVol);

            lib.D77_FreeMemory(settingsMemory, settings.size());
            lib.D77_FreeMemory(paramsMemory, params.size());

            AudioFormat format = new AudioFormat(settings.dwSamplingFreq, 16, 2, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, 8192); // Lower buffer size for lower latency/jitter
            line.start();

            running = true;
            renderThread = new Thread(this::renderLoop);
            renderThread.setDaemon(true);
            renderThread.start();

            isOpen = true;
        } catch (IOException | LineUnavailableException e) {
            throw new MidiUnavailableException(e.getMessage());
        }
    }

    private void renderLoop() {
        int samplesPerCall = lib.D77_GetRenderedSamplesPerCall();
        int bufferSize = samplesPerCall * 2 * 2; // stereo * 16bit
        Pointer sampleBuffer = lib.D77_AllocateMemory(bufferSize);
        byte[] byteBuffer = new byte[bufferSize];

        try {
            while (running) {
                MidiMessage message;
                while ((message = messageQueue.poll()) != null) {
                    if (message instanceof ShortMessage sm) {
                        int packed = (sm.getStatus() & 0xff) | ((sm.getData1() & 0x7f) << 8) | ((sm.getData2() & 0x7f) << 16);
                        lib.D77_MidiMessageShort(packed);
                    } else if (message instanceof SysexMessage sysex) {
                        byte[] data = sysex.getMessage();
                        Pointer p = lib.D77_AllocateMemory(data.length);
                        if (p != null) {
                            p.write(0, data, 0, data.length);
                            lib.D77_MidiMessageLong(p, data.length);
                            lib.D77_FreeMemory(p, data.length);
                        }
                    }
                }

                if (lib.D77_RenderSamples(sampleBuffer) != 0) {
                    sampleBuffer.read(0, byteBuffer, 0, bufferSize);
                    line.write(byteBuffer, 0, byteBuffer.length);
                } else {
                    Thread.yield();
                }
            }
        } finally {
            if (sampleBuffer != null) {
                lib.D77_FreeMemory(sampleBuffer, bufferSize);
            }
        }
    }

    @Override
    public void close() {
        if (!isOpen) return;
        running = false;
        try {
            if (renderThread != null) renderThread.join();
        } catch (InterruptedException e) {
            logger.log(Level.ERROR, e.getMessage(), e);
        }
        if (line != null) {
            line.stop();
            line.close();
        }
        isOpen = false;
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    @Override
    public long getMicrosecondPosition() {
        return line != null ? line.getMicrosecondPosition() : 0;
    }

    @Override
    public int getMaxReceivers() {
        return -1;
    }

    @Override
    public int getMaxTransmitters() {
        return 0;
    }

    @Override
    public Receiver getReceiver() throws MidiUnavailableException {
        return new D77Receiver();
    }

    @Override
    public List<Receiver> getReceivers() {
        return Collections.singletonList(getReceiverImpl());
    }

    private Receiver getReceiverImpl() {
        return new D77Receiver();
    }

    @Override
    public Transmitter getTransmitter() throws MidiUnavailableException {
        throw new MidiUnavailableException("No transmitters");
    }

    @Override
    public List<Transmitter> getTransmitters() {
        return Collections.emptyList();
    }

    @Override
    public int getMaxPolyphony() {
        return 256;
    }

    @Override
    public long getLatency() {
        return 0;
    }

    @Override
    public MidiChannel[] getChannels() {
        return new MidiChannel[0];
    }

    @Override
    public VoiceStatus[] getVoiceStatus() {
        return new VoiceStatus[0];
    }

    @Override
    public boolean isSoundbankSupported(Soundbank soundbank) {
        return false;
    }

    @Override
    public boolean loadInstrument(Instrument instrument) {
        return false;
    }

    @Override
    public void unloadInstrument(Instrument instrument) {
    }

    @Override
    public boolean remapInstrument(Instrument from, Instrument to) {
        return false;
    }

    @Override
    public Soundbank getDefaultSoundbank() {
        return null;
    }

    @Override
    public Instrument[] getAvailableInstruments() {
        return new Instrument[0];
    }

    @Override
    public Instrument[] getLoadedInstruments() {
        return new Instrument[0];
    }

    @Override
    public boolean loadAllInstruments(Soundbank soundbank) {
        return false;
    }

    @Override
    public void unloadAllInstruments(Soundbank soundbank) {
    }

    @Override
    public boolean loadInstruments(Soundbank soundbank, Patch[] patchList) {
        return false;
    }

    @Override
    public void unloadInstruments(Soundbank soundbank, Patch[] patchList) {
    }

    private class D77Receiver implements Receiver {

        @Override
        public void send(MidiMessage message, long timeStamp) {
            try {
                if (message instanceof ShortMessage sm) {
                    ShortMessage copy = new ShortMessage();
                    copy.setMessage(sm.getStatus(), sm.getData1(), sm.getData2());
                    messageQueue.offer(copy);
                } else if (message instanceof SysexMessage sm) {
                    byte[] data = sm.getData();
                    switch (data[0]) {
                        case 0x7f -> { // Universal Realtime
                            int c = data[1]; // 0x7f: Disregards channel
                            // Sub-ID, Sub-ID2
                            if (data[2] == 0x04 && data[3] == 0x01) { // Device Control / Master Volume
                                float gain = ((data[4] & 0x7f) | ((data[5] & 0x7f) << 7)) / 16383f;
logger.log(Level.DEBUG, "sysex volume: gain: %4.2f".formatted(gain));
                                volume(line, gain);
                                return;
                            }
                        }
                    }
                    SysexMessage copy = new SysexMessage();
                    copy.setMessage(sm.getMessage(), sm.getLength());
                    messageQueue.offer(copy);
                }
            } catch (Exception e) {
                logger.log(Level.ERROR, e.getMessage(), e);
            }
        }

        @Override
        public void close() {
        }
    }
}
