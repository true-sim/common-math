package com.truesim.common.math;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;

/** Layouts shared by the value types. Accessors are unaligned, so any segment and offset can be used. */
final class Memory {

    static final ValueLayout.OfDouble DOUBLE = ValueLayout.JAVA_DOUBLE_UNALIGNED;
    static final ValueLayout.OfFloat FLOAT = ValueLayout.JAVA_FLOAT_UNALIGNED;

    private Memory() {
    }

    static StructLayout doubles(String... names) {
        MemoryLayout[] fields = new MemoryLayout[names.length];
        for (int i = 0; i < names.length; i++) {
            fields[i] = ValueLayout.JAVA_DOUBLE.withName(names[i]);
        }
        return MemoryLayout.structLayout(fields);
    }

    static StructLayout floats(String... names) {
        MemoryLayout[] fields = new MemoryLayout[names.length];
        for (int i = 0; i < names.length; i++) {
            fields[i] = ValueLayout.JAVA_FLOAT.withName(names[i]);
        }
        return MemoryLayout.structLayout(fields);
    }
}
