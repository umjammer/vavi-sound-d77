/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.midi.d77;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;

import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;


/**
 * TestCase.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-02-01 nsano initial version <br>
 */
@PropsEntity(url = "file:local.properties")
class TestCase {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    @Property(name = "midi")
    String midiFile = "src/test/resources/test.mid";

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
    void test1() throws Exception {
        Synthesizer synthesizer = new D77Synthesizer();
        synthesizer.open();
        assertNotNull(synthesizer);

        if (new File(midiFile).exists()) {
            Sequence sequence = MidiSystem.getSequence(new File(midiFile));
            Sequencer sequencer = MidiSystem.getSequencer(false);
            sequencer.open();
            sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());
            sequencer.setSequence(sequence);
            sequencer.start();

            while (sequencer.isRunning()) {
                Thread.sleep(1000);
            }
            sequencer.stop();
            sequencer.close();
        } else {
            System.err.println("Test MIDI file not found: " + midiFile);
        }

        synthesizer.close();
    }
}