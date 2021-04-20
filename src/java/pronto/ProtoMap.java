package pronto;

import clojure.lang.Keyword;
import com.google.protobuf.GeneratedMessageV3;

/**
   Base functionality implemented by any generated proto-map.

   Note: the ugly `pmap_` prefix in method names is a precaution to avoid
   a conflict with the `get<Field>` and `has<Field>` methods that are generated as part of
   the <Object>OrBuilder interfaces.
 **/
public interface ProtoMap<T extends GeneratedMessageV3> {
    boolean isMutable();

    boolean pmap_hasField(Keyword key);

    ProtoMap<T> clearField(Keyword key);

    T pmap_getProto();

    Keyword whichOneOf(Keyword key);

    GeneratedMessageV3.Builder pmap_getBuilder();

    ProtoMap<T> copy(GeneratedMessageV3.Builder builder);

    ProtoMap<T> remap(ProtoMapper mapper);

    ProtoMap<?> empty(Keyword keyword);
}
