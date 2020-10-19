package pronto;

import clojure.lang.ArityException;
import clojure.lang.Compiler;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Utils {
    public interface PairXf {
        Object transformKey(Object key);
        Object transformVal(Object val);
    }

    public static Object[] mapToArray(Map in, PairXf xf) {
        Object[] res = new Object[in.size() * 2];
        int i = 0;
        for (Object entry : in.entrySet()) {
            Map.Entry e = (Map.Entry) entry;
            res[i] = xf.transformKey(e.getKey());
            res[i + 1] = xf.transformVal(e.getValue());
            i += 2;
        }

        return res;
    }

    public static Map transformMap(Map in, PairXf xf) {
        Map m = new HashMap(in.size());
        in.forEach((key, value) -> {
            m.put(xf.transformKey(key), xf.transformVal(value));
        });

        return m;
    }

    public static Object[] iterableToArray(Iterable iterable, int size) {
        Object[] arr = new Object[size];
        Iterator iter = iterable.iterator();
        for (int i = 0; i < size; i++) {
            arr[i] = iter.next();
        }
        return arr;
    }

    public static Object throwArity(Object obj, int n) {
        String name = obj.getClass().getSimpleName();
        throw new ArityException(n, Compiler.demunge(name));
    }
}
