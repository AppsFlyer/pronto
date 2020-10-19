package pronto;

import clojure.lang.Keyword;
import com.google.protobuf.GeneratedMessageV3;

public interface TransientProtoMap extends ProtoMap {
    void pmap_setInTransaction(boolean inTransaction);

    boolean pmap_isInTransaction();
}
