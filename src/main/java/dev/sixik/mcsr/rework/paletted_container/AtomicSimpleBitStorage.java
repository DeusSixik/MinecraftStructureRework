package dev.sixik.mcsr.rework.paletted_container;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.IntConsumer;

public class AtomicSimpleBitStorage implements BitStorage {
    private static final VarHandle VOLATILE_ACCESS = MethodHandles.arrayElementVarHandle(long[].class);

    // Магия из оригинального SimpleBitStorage для совместимости индексации
    private static final int[] MAGIC = new int[]{-1, -1, 0, Integer.MIN_VALUE, 0, 0, 0x55555555, 0x55555555, 0, Integer.MIN_VALUE, 0, 1, 0x33333333, 0x33333333, 0, 0x2AAAAAAA, 0x2AAAAAAA, 0, 0x24924924, 0x24924924, 0, Integer.MIN_VALUE, 0, 2, 0x1C71C71C, 0x1C71C71C, 0, 0x19999999, 0x19999999, 0, 390451572, 390451572, 0, 0x15555555, 0x15555555, 0, 0x13B13B13, 0x13B13B13, 0, 306783378, 306783378, 0, 0x11111111, 0x11111111, 0, Integer.MIN_VALUE, 0, 3, 0xF0F0F0F, 0xF0F0F0F, 0, 0xE38E38E, 0xE38E38E, 0, 226050910, 226050910, 0, 0xCCCCCCC, 0xCCCCCCC, 0, 0xC30C30C, 0xC30C30C, 0, 195225786, 195225786, 0, 186737708, 186737708, 0, 0xAAAAAAA, 0xAAAAAAA, 0, 171798691, 171798691, 0, 0x9D89D89, 0x9D89D89, 0, 159072862, 159072862, 0, 0x9249249, 0x9249249, 0, 148102320, 148102320, 0, 0x8888888, 0x8888888, 0, 138547332, 138547332, 0, Integer.MIN_VALUE, 0, 4, 130150524, 130150524, 0, 0x7878787, 0x7878787, 0, 0x7507507, 0x7507507, 0, 0x71C71C7, 0x71C71C7, 0, 116080197, 116080197, 0, 113025455, 113025455, 0, 0x6906906, 0x6906906, 0, 0x6666666, 0x6666666, 0, 104755299, 104755299, 0, 0x6186186, 0x6186186, 0, 99882960, 99882960, 0, 97612893, 97612893, 0, 0x5B05B05, 0x5B05B05, 0, 93368854, 93368854, 0, 91382282, 91382282, 0, 0x5555555, 0x5555555, 0, 87652393, 87652393, 0, 85899345, 85899345, 0, 0x5050505, 0x5050505, 0, 0x4EC4EC4, 0x4EC4EC4, 0, 81037118, 81037118, 0, 79536431, 79536431, 0, 78090314, 78090314, 0, 0x4924924, 0x4924924, 0, 75350303, 75350303, 0, 74051160, 74051160, 0, 72796055, 72796055, 0, 0x4444444, 0x4444444, 0, 70409299, 70409299, 0, 69273666, 69273666, 0, 0x4104104, 0x4104104, 0, Integer.MIN_VALUE, 0, 5};

    private final long[] data;
    private final int bits;
    private final long mask;
    private final int size;
    private final int valuesPerLong;
    private final int divideMul;
    private final int divideAdd;
    private final int divideShift;

    public AtomicSimpleBitStorage(int bits, int size, int[] is) {
        this(bits, size);
        int k = 0;

        int l;
        for (l = 0; l <= size - this.valuesPerLong; l += this.valuesPerLong) {
            long m = 0L;

            for (int n = this.valuesPerLong - 1; n >= 0; n--) {
                m <<= bits;
                m |= is[l + n] & this.mask;
            }

            this.data[k++] = m;
        }

        int o = size - l;
        if (o > 0) {
            long p = 0L;

            for (int q = o - 1; q >= 0; q--) {
                p <<= bits;
                p |= is[l + q] & this.mask;
            }

            this.data[k] = p;
        }
    }

    public AtomicSimpleBitStorage(int bits, int size) {
        this.bits = bits;
        this.size = size;
        this.mask = (1L << bits) - 1L;
        this.valuesPerLong = 64 / (bits == 0 ? 1 : bits);
        int arrayLength = (size + valuesPerLong - 1) / valuesPerLong;
        this.data = new long[arrayLength];

        int k = 3 * (this.valuesPerLong - 1);
        this.divideMul = MAGIC[k + 0];
        this.divideAdd = MAGIC[k + 1];
        this.divideShift = MAGIC[k + 2];
    }

    public AtomicSimpleBitStorage(int bits, int size, long[] data) {
        this.bits = bits;
        this.size = size;
        this.mask = (1L << bits) - 1L;
        this.valuesPerLong = 64 / bits;

        int k = 3 * (this.valuesPerLong - 1);
        this.divideMul = MAGIC[k + 0];
        this.divideAdd = MAGIC[k + 1];
        this.divideShift = MAGIC[k + 2];

        int expectedLen = (size + valuesPerLong - 1) / valuesPerLong;
        if (data != null) {
            this.data = data;
        } else {
            this.data = new long[expectedLen];
        }
    }

    private int cellIndex(int i) {
        long l = Integer.toUnsignedLong(this.divideMul);
        long m = Integer.toUnsignedLong(this.divideAdd);
        return (int)((long)i * l + m >> 32 >> this.divideShift);
    }

    @Override
    public int get(int i) {
        int idx = cellIndex(i);
        long val = (long) VOLATILE_ACCESS.getAcquire(data, idx);
        int shift = (i - idx * this.valuesPerLong) * this.bits;
        return (int)(val >> shift & this.mask);
    }

    @Override
    public void set(int i, int j) {
        getAndSet(i, j);
    }

    @Override
    public int getAndSet(int i, int j) {
        int idx = cellIndex(i);
        int shift = (i - idx * this.valuesPerLong) * this.bits;
        long bitMask = this.mask << shift;
        long newValueBits = ((long)j & this.mask) << shift;

        while (true) {
            long oldLong = (long) VOLATILE_ACCESS.getVolatile(data, idx);
            long nextLong = (oldLong & ~bitMask) | newValueBits;

            if (VOLATILE_ACCESS.compareAndSet(data, idx, oldLong, nextLong)) {
                return (int)(oldLong >> shift & this.mask);
            }
            Thread.onSpinWait();
        }
    }

    @Override
    public long[] getRaw() {
        // Чтобы рендер работал, возвращаем ссылку на массив (как в оригинале)
        // НО: это небезопасно для конкурентного чтения, поэтому лучше
        // сделать Mixin в сетевой части, чтобы она делала копию.
        return this.data;
    }

    // Остальные методы (getSize, getBits, copy, unpack)
    // реализуются так же, как в оригинале, но с использованием getAcquire
    @Override
    public int getSize() { return this.size; }

    @Override
    public int getBits() { return this.bits; }

    @Override
    public void getAll(IntConsumer consumer) {
        for (int i = 0; i < this.size; i++) {
            consumer.accept(this.get(i));
        }
    }

    @Override
    public void unpack(int[] target) {
        for (int i = 0; i < this.size; i++) {
            target[i] = this.get(i);
        }
    }


    @Override
    public BitStorage copy() {
        return new AtomicSimpleBitStorage(this.bits, this.size, this.data.clone());
    }
}
