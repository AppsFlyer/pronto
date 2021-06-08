package pronto;

import com.google.protobuf.GeneratedMessageV3;

public interface TransientProtoMap<T extends GeneratedMessageV3> extends ProtoMap<T> {
    void setInTransaction(boolean inTransaction);

    boolean isInTransaction();
}
