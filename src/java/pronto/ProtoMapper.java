package pronto;

import clojure.lang.IPersistentSet;

import com.google.protobuf.GeneratedMessageV3;

public interface ProtoMapper {
    String getNamespace();

    IPersistentSet getClasses();

    ProtoMap fromBytes(Class<? extends GeneratedMessageV3> clazz, byte[] bytes);

    ProtoMap fromProto(Class<? extends GeneratedMessageV3> clazz, GeneratedMessageV3 proto);

    ProtoMap getProto(Class<? extends GeneratedMessageV3> clazz);

    TransientProtoMap getTransient(Class<? extends GeneratedMessageV3> clazz);
}
