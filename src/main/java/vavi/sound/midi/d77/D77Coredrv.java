/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.midi.d77;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;


/**
 * D77Coredrv.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-02-01 nsano initial version <br>
 */
public interface D77Coredrv extends Library {

    D77Coredrv INSTANCE = Native.load("d77_coredrv", D77Coredrv.class);

    @Structure.FieldOrder({
        "dwSamplingFreq", "dwPolyphony", "dwCpuLoadL", "dwCpuLoadH", "dwRevSw", "dwChoSw", "dwMVol",
        "dwRevAdj", "dwChoAdj", "dwOutLev", "dwRevFb", "dwRevDrm", "dwResoUpAdj", "dwCacheSize", "dwTimeReso"
    })
    class D77_SETTINGS extends Structure {
        public static class ByReference extends D77_SETTINGS implements Structure.ByReference {}
        public int dwSamplingFreq;
        public int dwPolyphony;
        public int dwCpuLoadL;
        public int dwCpuLoadH;
        public int dwRevSw;
        public int dwChoSw;
        public int dwMVol;
        public int dwRevAdj;
        public int dwChoAdj;
        public int dwOutLev;
        public int dwRevFb;
        public int dwRevDrm;
        public int dwResoUpAdj;
        public int dwCacheSize;
        public int dwTimeReso;

        public D77_SETTINGS() {
            super(ALIGN_GNUC); // #pragma pack(4) is usually default or ALIGN_GNUC
        }
    }

    @Structure.FieldOrder({
        "wChoAdj", "wRevAdj", "wRevDrm", "wRevFb", "wOutLev", "wResoUpAdj"
    })
    class D77_PARAMETERS extends Structure {
        public static class ByReference extends D77_PARAMETERS implements Structure.ByReference {}
        public short wChoAdj;
        public short wRevAdj;
        public short wRevDrm;
        public short wRevFb;
        public short wOutLev;
        public short wResoUpAdj;

        public D77_PARAMETERS() {
            super(ALIGN_NONE); // #pragma pack(2)
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("wChoAdj", "wRevAdj", "wRevDrm", "wRevFb", "wOutLev", "wResoUpAdj");
        }
    }

    int D77_EFFECT_Chorus = 0;
    int D77_EFFECT_Reverb = 1;

    int D77_InitializePointerOffset();

    void D77_ValidateSettings(D77_SETTINGS lpSettings);

    int D77_InitializeDataFile(Pointer lpDataFile, int dwLength);

    int D77_InitializeSynth(int dwSamplingFrequency, int dwPolyphony, int dwTimeReso_unused);

    void D77_InitializeUnknown(int dwUnknown_unused);

    void D77_InitializeEffect(int dwEffect, int bEnabled);

    void D77_InitializeCpuLoad(int dwCpuLoadLow, int dwCpuLoadHigh);

    void D77_InitializeParameters(D77_PARAMETERS lpParameters);

    void D77_InitializeMasterVolume(int dwMasterVolume);

    int D77_GetRenderedSamplesPerCall();

    int D77_MidiMessageShort(int dwMessage);

    int D77_MidiMessageLong(Pointer lpMessage, int dwLength);

    int D77_RenderSamples(short[] lpSamples);

    Pointer D77_AllocateMemory(int size);

    void D77_FreeMemory(Pointer mem, int size);
}