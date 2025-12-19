package net.minecraft.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import org.apache.commons.lang3.mutable.MutableObject;

import java.util.Objects;
import java.util.Optional;

public class ExtraCodecs {

    public static <A> Codec.ResultFunction<A> orElsePartial(final A object) {
        return new Codec.ResultFunction<>() {
            public <T> DataResult<Pair<A, T>> apply(DynamicOps<T> dynamicOps, T objectx, DataResult<Pair<A, T>> dataResult) {
                MutableObject<String> mutableObject = new MutableObject<>();
                Objects.requireNonNull(mutableObject);
                Optional<Pair<A, T>> optional = dataResult.resultOrPartial(mutableObject::setValue);
                return optional.isPresent() ? dataResult : DataResult.error(() -> "(" + mutableObject.getValue() + " -> using default)", Pair.of(object, objectx));
            }

            public <T> DataResult<T> coApply(DynamicOps<T> dynamicOps, A objectx, DataResult<T> dataResult) {
                return dataResult;
            }

            public String toString() {
                return "OrElsePartial[" + object + "]";
            }
        };
    }
}
