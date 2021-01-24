package pronto;

import clojure.lang.Keyword;
import com.google.protobuf.GeneratedMessageV3;

public interface TransientProtoMap<T extends GeneratedMessageV3> extends ProtoMap<T> {
    void pmap_setInTransaction(boolean inTransaction);

    boolean pmap_isInTransaction();
}
