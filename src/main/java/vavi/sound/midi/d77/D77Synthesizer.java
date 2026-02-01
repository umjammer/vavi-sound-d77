/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.midi.d77;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
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
import com.sun.jna.Memory;
import com.sun.jna.Pointer;


/**
 * D77Synthesizer.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-02-01 nsano initial version <br>
 */
public class D77Synthesizer implements Synthesizer {

    private static final D77Coredrv lib = D77Coredrv.INSTANCE;

    private boolean isOpen;
    private SourceDataLine line;
    private Thread renderThread;
    private volatile boolean running;

    private String dataFilePath = System.getProperty("vavi.sound.midi.d77.datafile", "src/main/resources/dswebWDM.dat");

    private static class D77Info extends Info {
        protected D77Info() {
            super("WebSynth D-77", "M-HT / Roman Pauer", "Software Synthesizer", "0.0.1");
        }
    }

    private static final Info info = new D77Info();

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

            if (lib.D77_InitializeDataFile(pData, data.length - 4) == 0) {
                throw new MidiUnavailableException("Failed to initialize data file");
            }

            int sampleRate = 44100;
            int polyphony = 64;
            if (lib.D77_InitializeSynth(sampleRate, polyphony, 80) == 0) {
                throw new MidiUnavailableException("Failed to initialize synth");
            }

            lib.D77_InitializeUnknown(0);
            lib.D77_InitializeEffect(D77Coredrv.D77_EFFECT_Reverb, 1);
            lib.D77_InitializeEffect(D77Coredrv.D77_EFFECT_Chorus, 1);
            lib.D77_InitializeCpuLoad(60, 90);
            lib.D77_InitializeMasterVolume(100);

            AudioFormat format = new AudioFormat(sampleRate, 16, 2, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
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
        short[] buffer = new short[samplesPerCall * 2];
        byte[] byteBuffer = new byte[buffer.length * 2];

        while (running) {
            if (lib.D77_RenderSamples(buffer) != 0) {
                for (int i = 0; i < buffer.length; i++) {
                    byteBuffer[i * 2] = (byte) (buffer[i] & 0xff);
                    byteBuffer[i * 2 + 1] = (byte) ((buffer[i] >> 8) & 0xff);
                }
                line.write(byteBuffer, 0, byteBuffer.length);
            } else {
                Thread.yield();
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
            e.printStackTrace();
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
        return null;
    }

    @Override
    public Transmitter getTransmitter() throws MidiUnavailableException {
        throw new MidiUnavailableException("No transmitters");
    }

    @Override
    public List<Transmitter> getTransmitters() {
        return null;
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

    private static class D77Receiver implements Receiver {
        @Override
        public void send(MidiMessage message, long timeStamp) {
            if (message instanceof ShortMessage) {
                ShortMessage sm = (ShortMessage) message;
                int packed = (sm.getStatus() & 0xff) | ((sm.getData1() & 0x7f) << 8) | ((sm.getData2() & 0x7f) << 16);
                lib.D77_MidiMessageShort(packed);
            } else if (message instanceof SysexMessage) {
                SysexMessage sysex = (SysexMessage) message;
                byte[] data = sysex.getMessage();
                Pointer p = new Memory(data.length);
                p.write(0, data, 0, data.length);
                lib.D77_MidiMessageLong(p, data.length);
            }
        }

        @Override
        public void close() {
        }
    }
}