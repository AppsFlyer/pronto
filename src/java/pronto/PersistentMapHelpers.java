package pronto;

import clojure.lang.*;

import java.util.*;

public class PersistentMapHelpers {

    public static String toString(IPersistentMap map) {
        return clojure.lang.RT.printString(map);
    }

    public static IPersistentCollection cons(IPersistentMap map, Object o){
        if (o instanceof Map.Entry)
        {
            Map.Entry e = (Map.Entry) o;

            return map.assoc(e.getKey(), e.getValue());
        }
        else if(o instanceof IPersistentVector)
        {
            IPersistentVector v = (IPersistentVector) o;
            if(v.count() != 2)
                throw new IllegalArgumentException("Vector arg to map conj must be a pair");
            return map.assoc(v.nth(0), v.nth(1));
        }

        IPersistentMap ret = map;
        for(ISeq es = clojure.lang.RT.seq(o); es != null; es = es.next())
        {
            Map.Entry e = (Map.Entry) es.first();
            ret = ret.assoc(e.getKey(), e.getValue());
        }
        return ret;
    }

    public static boolean equals(IPersistentMap map, Object obj){
        return mapEquals(map, obj);
    }

    public static boolean mapEquals(IPersistentMap m1, Object obj){
        if(m1 == obj) return true;
        if(!(obj instanceof Map))
            return false;
        Map m = (Map) obj;

        if(m.size() != m1.count())
            return false;

        for(ISeq s = m1.seq(); s != null; s = s.next())
        {
            Map.Entry e = (Map.Entry) s.first();
            boolean found = m.containsKey(e.getKey());

            if(!found || !Util.equals(e.getValue(), m.get(e.getKey())))
                return false;
        }

        return true;
    }

    public static boolean equiv(IPersistentMap map, Object obj){
        if(!(obj instanceof Map))
            return false;
        if(obj instanceof IPersistentMap && !(obj instanceof MapEquivalence))
            return false;

        Map m = (Map) obj;

        if(m.size() != map.count())
            return false;

        for(ISeq s = map.seq(); s != null; s = s.next())
        {
            Map.Entry e = (Map.Entry) s.first();
            boolean found = m.containsKey(e.getKey());

            if(!found || !Util.equiv(e.getValue(), m.get(e.getKey())))
                return false;
        }

        return true;
    }

    public static Object invoke(IPersistentMap map, Object arg1) {
        return map.valAt(arg1);
    }

    public static Object invoke(IPersistentMap map, Object arg1, Object notFound) {
        return map.valAt(arg1, notFound);
    }

    public static boolean containsValue(IPersistentMap map, Object value){
        return values(map).contains(value);
    }

    public static Set entrySet(IPersistentMap map){
        return new AbstractSet(){

            public Iterator iterator(){
                return map.iterator();
            }

            public int size(){
                return map.count();
            }

            public int hashCode(){
                return map.hashCode();
            }

            public boolean contains(Object o){
                if(o instanceof Map.Entry)
                {
                    Map.Entry e = (Map.Entry) o;
                    Map.Entry found = map.entryAt(e.getKey());
                    if(found != null && Util.equals(found.getValue(), e.getValue()))
                        return true;
                }
                return false;
            }
        };
    }

    public static Object get(IPersistentMap map, Object key){
        return map.valAt(key);
    }

    public static boolean isEmpty(IPersistentMap map){
        return map.count() == 0;
    }

    public static Set keySet(IPersistentMap map){
        return new AbstractSet(){

            public Iterator iterator(){
                final Iterator mi = map.iterator();

                return new Iterator(){


                    public boolean hasNext(){
                        return mi.hasNext();
                    }

                    public Object next(){
                        Map.Entry e = (Map.Entry) mi.next();
                        return e.getKey();
                    }

                    public void remove(){
                        throw new UnsupportedOperationException();
                    }
                };
            }

            public int size(){
                return map.count();
            }

            public boolean contains(Object o){
                return map.containsKey(o);
            }
        };
    }

    public static Collection values(IPersistentMap map){
        return new AbstractCollection(){

            public Iterator iterator(){
                final Iterator mi = map.iterator();

                return new Iterator(){


                    public boolean hasNext(){
                        return mi.hasNext();
                    }

                    public Object next(){
                        Map.Entry e = (Map.Entry) mi.next();
                        return e.getValue();
                    }

                    public void remove(){
                        throw new UnsupportedOperationException();
                    }
                };
            }

            public int size(){
                return map.count();
            }
        };
    }
}
