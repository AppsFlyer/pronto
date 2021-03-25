package pronto;

import clojure.lang.Symbol;
import clojure.lang.IPersistentSet;

public interface ProtoMapper {
    Symbol getSym();

    String getNamespace();
    
    IPersistentSet getClasses();
}
