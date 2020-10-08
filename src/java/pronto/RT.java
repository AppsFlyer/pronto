package pronto;

import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Message;

public class RT {

    public static GeneratedMessageV3 getProto(ProtoMap map) {
        return map.pmap_getProto();
    }

    public static boolean isBuilder(Object o) {
        return o instanceof Message.Builder;
    }
}
