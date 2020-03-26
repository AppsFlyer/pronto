package pronto;

import clojure.lang.ArityException;
import clojure.lang.Compiler;

public class Utils {
    public static Object throwArity(Object obj, int n) {
        String name = obj.getClass().getSimpleName();
        throw new ArityException(n, Compiler.demunge(name));
    }
}
