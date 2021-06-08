package pronto;

import clojure.lang.IPersistentVector;
import clojure.lang.ISeq;
import clojure.lang.ITransientMap;
import clojure.lang.RT;

import java.util.Map;

public class TransientMapHelpers {

    /**
     * Code taken from Clojure, see https://github.com/clojure/clojure/blob/b1b88dd25373a86e41310a525a21b497799dbbf2/src/jvm/clojure/lang/ATransientMap.java
     */
    public static ITransientMap conj(ITransientMap map, Object o) {
        if (o instanceof Map.Entry) {
            Map.Entry e = (Map.Entry) o;

            return map.assoc(e.getKey(), e.getValue());
        } else if (o instanceof IPersistentVector) {
            IPersistentVector v = (IPersistentVector) o;
            if (v.count() != 2)
                throw new IllegalArgumentException("Vector arg to map conj must be a pair");
            return map.assoc(v.nth(0), v.nth(1));
        }

        ITransientMap ret = map;
        for (ISeq es = RT.seq(o); es != null; es = es.next()) {
            Map.Entry e = (Map.Entry) es.first();
            ret = ret.assoc(e.getKey(), e.getValue());
        }
        return ret;
    }
}
