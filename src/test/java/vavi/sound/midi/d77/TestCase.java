/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.midi.d77;

import java.io.BufferedInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;

import vavi.util.Debug;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static vavi.sound.midi.MidiUtil.volume;


/**
 * TestCase.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-02-01 nsano initial version <br>
 */
@EnabledIf("localPropertiesExists")
@PropsEntity(url = "file:local.properties")
class TestCase {

    static {
        System.setProperty("javax.sound.midi.synthesiser", "#WebSynth D-77");
    }

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    static boolean onIde = System.getProperty("vavi.test", "").equals("ide");
    static long time = onIde ? 1000 * 1000 : 10 * 1000;

    @Property(name = "midi")
    String midiFile = "src/test/resources/test.mid";

    @Property(name = "vavi.test.volume.midi")
    float midiVolume = 0.2f;

    @Property(name = "vavi.sound.midi.d77.datafile")
    String dataFile = "src/test/resources/dswebWDM.dat";

    @BeforeEach
    void setup() throws Exception {
        if (localPropertiesExists()) {
            PropsEntity.Util.bind(this);
        }

        System.setProperty("vavi.sound.midi.d77.datafile", dataFile);
    }

    @Test
    @DisplayName("directly")
    void test1() throws Exception {
        Synthesizer synthesizer = new D77Synthesizer();
        synthesizer.open();
        assertNotNull(synthesizer);

        Path file = Paths.get(midiFile);
Debug.println("file: " + file);

        Sequence sequence = MidiSystem.getSequence(new BufferedInputStream(Files.newInputStream(file)));

        Sequencer sequencer = MidiSystem.getSequencer(false);
        sequencer.open();
        sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());

        CountDownLatch cdl = new CountDownLatch(1);
        MetaEventListener mel = meta -> {
Debug.println("META: " + meta.getType());
            if (meta.getType() == 47) cdl.countDown();
        };
        sequencer.setSequence(sequence);
        sequencer.addMetaEventListener(mel);
Debug.println("START");
        sequencer.start();
        volume(synthesizer.getReceiver(), midiVolume);
if (!onIde) {
 Thread.sleep(time);
 sequencer.stop();
Debug.println("STOP");
} else {
        cdl.await();
}
Debug.println("END");
        sequencer.removeMetaEventListener(mel);
        sequencer.close();

        synthesizer.close();
    }

    @Test
    @DisplayName("via spi")
    void test2() throws Exception {
        Synthesizer synthesizer = MidiSystem.getSynthesizer();
        synthesizer.open();
        assertInstanceOf(D77Synthesizer.class, synthesizer);

        Path file = Paths.get(midiFile);
Debug.println("file: " + file);

        Sequence sequence = MidiSystem.getSequence(new BufferedInputStream(Files.newInputStream(file)));

        Sequencer sequencer = MidiSystem.getSequencer(false);
        sequencer.open();
        sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());

        CountDownLatch cdl = new CountDownLatch(1);
        MetaEventListener mel = meta -> {
Debug.println("META: " + meta.getType());
            if (meta.getType() == 47) cdl.countDown();
        };
        sequencer.setSequence(sequence);
        sequencer.addMetaEventListener(mel);
Debug.println("START");
        sequencer.start();
        volume(synthesizer.getReceiver(), midiVolume);
if (!onIde) {
 Thread.sleep(time);
 sequencer.stop();
Debug.println("STOP");
} else {
        cdl.await();
}
Debug.println("END");
        sequencer.removeMetaEventListener(mel);
        sequencer.close();

        synthesizer.close();
    }
}