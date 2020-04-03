package pronto;

import clojure.lang.Keyword;
import com.google.protobuf.GeneratedMessageV3;

public interface ProtoMap {
    boolean isMutable();

    boolean hasField(Keyword key);

    ProtoMap clearField(Keyword key);

    GeneratedMessageV3 getProto();

    Keyword whichOneOf(Keyword key);
}
