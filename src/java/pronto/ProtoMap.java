package pronto;

import clojure.lang.Keyword;
import com.google.protobuf.GeneratedMessageV3;

public interface ProtoMap<T extends GeneratedMessageV3> {
    boolean pmap_isMutable();

    boolean pmap_hasField(Keyword key);

    ProtoMap<T> pmap_clearField(Keyword key);

    T pmap_getProto();

    Keyword pmap_whichOneOf(Keyword key);

    GeneratedMessageV3.Builder pmap_getBuilder();

    ProtoMap<T> pmap_copy(GeneratedMessageV3.Builder builder);

    ProtoMap<T> remap(ProtoMapper mapper);
}
