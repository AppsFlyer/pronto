package pronto;

import clojure.lang.Keyword;
import com.google.protobuf.GeneratedMessageV3;

public interface ProtoMap {
    boolean pmap_isMutable();

    boolean pmap_hasField(Keyword key);

    ProtoMap pmap_clearField(Keyword key);

    GeneratedMessageV3 pmap_getProto();

    Keyword pmap_whichOneOf(Keyword key);

    ProtoMap pmap_inflate();

    ProtoMap pmap_deflate();
}
