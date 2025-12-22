package dev.sixik.mcsr.rework.paletted_container;

import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;

import java.util.List;
import java.util.function.Predicate;

public class ConcurrentHashMapPalette<T> implements Palette<T> {
    private final IdMap<T> registry;
    private final PaletteResize<T> resizeHandler;
    private final int bits;

    // Используем volatile, чтобы другие потоки мгновенно видели новую версию карты
    private volatile CrudeIncrementalIntIdentityHashBiMap<T> values;

    public ConcurrentHashMapPalette(IdMap<T> idMap, int i, PaletteResize<T> paletteResize) {
        this.registry = idMap;
        this.bits = i;
        this.resizeHandler = paletteResize;
        this.values = CrudeIncrementalIntIdentityHashBiMap.create(1 << i);
    }

    private ConcurrentHashMapPalette(IdMap<T> idMap, int i, PaletteResize<T> paletteResize, CrudeIncrementalIntIdentityHashBiMap<T> crudeIncrementalIntIdentityHashBiMap) {
        this.registry = idMap;
        this.bits = i;
        this.resizeHandler = paletteResize;
        this.values = crudeIncrementalIntIdentityHashBiMap;
    }

    public ConcurrentHashMapPalette(IdMap<T> idMap, int i, PaletteResize<T> paletteResize, List<T> list) {
        this(idMap, i, paletteResize);
        list.forEach(this.values::add);
    }

    @Override
    public int idFor(T object) {
        // 1. Пытаемся получить ID без блокировок
        int id = this.values.getId(object);
        if (id != -1) return id;

        // 2. Если блока нет, заходим в синхронизированный блок для добавления
        synchronized (this) {
            // Двойная проверка (Double-checked locking)
            id = this.values.getId(object);
            if (id == -1) {
                // Создаем копию карты перед модификацией (Copy-On-Write)
                CrudeIncrementalIntIdentityHashBiMap<T> nextValues = this.values.copy();
                id = nextValues.add(object);

                // Проверяем, не пора ли расширять весь контейнер (Bit Expansion)
                if (id >= 1 << this.bits) {
                    // Внимание: onResize создаст новый контейнер и новую палитру
                    return this.resizeHandler.onResize(this.bits + 1, object);
                }

                // Обновляем ссылку на карту
                this.values = nextValues;
            }
        }
        return id;
    }

    @Override
    public boolean maybeHas(Predicate<T> predicate) {
        for (int i = 0; i < this.getSize(); i++) {
            if (predicate.test(this.values.byId(i))) {
                return true;
            }
        }

        return false;
    }

    @Override
    public synchronized T valueFor(int i) {
        // Чтение абсолютно свободно от блокировок
        T object = this.values.byId(i);
        if (object == null) {
            throw new MissingPaletteEntryException(i);
        }
        return object;
    }

    @Override
    public void read(FriendlyByteBuf friendlyByteBuf) {
        this.values.clear();
        int i = friendlyByteBuf.readVarInt();

        for (int j = 0; j < i; j++) {
            this.values.add(this.registry.byIdOrThrow(friendlyByteBuf.readVarInt()));
        }
    }

    @Override
    public void write(FriendlyByteBuf friendlyByteBuf) {
        int i = this.getSize();
        friendlyByteBuf.writeVarInt(i);

        for (int j = 0; j < i; j++) {
            friendlyByteBuf.writeVarInt(this.registry.getId(this.values.byId(j)));
        }
    }

    @Override
    public int getSerializedSize() {
        int i = VarInt.getByteSize(this.getSize());

        for (int j = 0; j < this.getSize(); j++) {
            i += VarInt.getByteSize(this.registry.getId(this.values.byId(j)));
        }

        return i;
    }

    @Override
    public int getSize() {
        return this.values.size();
    }

    @Override
    public Palette<T> copy() {
        return new ConcurrentHashMapPalette<>(this.registry, this.bits, this.resizeHandler, this.values.copy());
    }

    public static <A> Palette<A> create(int i, IdMap<A> idMap, PaletteResize<A> paletteResize, List<A> list) {
        return new ConcurrentHashMapPalette<>(idMap, i, paletteResize, list);
    }
}
