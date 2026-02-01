/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.midi.d77;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.spi.MidiDeviceProvider;

import static java.lang.System.getLogger;


/**
 * D77MidiDeviceProvider.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 260201 nsano initial version <br>
 */
public class D77MidiDeviceProvider extends MidiDeviceProvider {

    private static final Logger logger = getLogger(D77MidiDeviceProvider.class.getName());

    /** TODO endian */
    public final static int MANUFACTURER_ID = 0x002055;

    /** */
    private static final MidiDevice.Info[] infos = new MidiDevice.Info[] { D77Synthesizer.info };

    @Override
    public MidiDevice.Info[] getDeviceInfo() {
        return infos;
    }

    @Override
    public MidiDevice getDevice(MidiDevice.Info info) throws IllegalArgumentException {

        if (info == D77Synthesizer.info) {
logger.log(Level.DEBUG, "★1 info: " + info);
            D77Synthesizer synthesizer = new D77Synthesizer();
            return synthesizer;
        } else {
logger.log(Level.DEBUG, "★1 here: " + info);
            throw new IllegalArgumentException();
        }
    }
}
