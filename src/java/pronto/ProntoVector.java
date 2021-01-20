package pronto;

import clojure.lang.RT;
import clojure.lang.*;
import com.google.protobuf.LazyStringArrayList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ProntoVector extends APersistentVector implements IObj, IEditableCollection, IReduce, IKVReduce {

    private final List base;
    private final ListFactory factory;
    private final Transformer transformer;
    private final IPersistentMap meta;
    private final PersistentVector tail;

    public interface ListFactory {
        List newList(List list, int capacityDiff);
    }

    public static final ListFactory DEFAULT_LIST_FACTORY = (list, capacityDiff) -> {
        ArrayList newList = new ArrayList(list.size() + capacityDiff);
        newList.addAll(list);
        return newList;
    };

    public static final ListFactory LAZY_STRING_LIST_FACTORY = (list, capacityDiff) -> {
        LazyStringArrayList lsal = new LazyStringArrayList(list.size() + capacityDiff);
        lsal.addAll(list);
        return lsal;
    };

    public ProntoVector(List base, ListFactory factory, Transformer transformer, IPersistentMap meta) {
        this(base, factory, transformer, PersistentVector.EMPTY, meta);
    }

    public ProntoVector(List base, ListFactory factory, Transformer transformer, PersistentVector tail, IPersistentMap meta) {
        this.base = base;
        this.factory = factory;
        this.transformer = transformer;
        this.meta = meta;
        this.tail = tail;
    }

    @Override
    public ITransientCollection asTransient() {
        return new Transient();
    }

    @Override
    public Object kvreduce(IFn f, Object init) {
        for (int i = 0; i < count(); i++) {
            Object obj = nth(i);
            init = f.invoke(init, i, transformer.fromProto(obj));
            if (RT.isReduced(init)) {
                return ((IDeref) init).deref();
            }
        }

        return init;
    }

    @Override
    public IObj withMeta(IPersistentMap meta) {
        return meta == meta() ? this : new ProntoVector(base, factory, transformer, tail, meta);
    }

    @Override
    public IPersistentMap meta() {
        return meta;
    }

    @Override
    public Object reduce(IFn f) {
        if (isEmpty()) {
            return f.invoke();
        }
        return reduce(f, nth(0));
    }

    @Override
    public Object reduce(IFn f, Object init) {
        for (int i = 0; i < count(); i++) {
            Object obj = nth(i);
            init = f.invoke(init, transformer.fromProto(obj));
            if (RT.isReduced(init)) {
                return ((IDeref) init).deref();
            }
        }

        return init;
    }

    public interface Transformer {
        Object toProto(Object item);
        Object fromProto(Object item);
    }

    @Override
    public IPersistentVector assocN(int i, Object val) {
        val = transformer.toProto(val);
        if (i < base.size()) {
            List newList = factory.newList(base, 1);
            newList.set(i, val);
            return new ProntoVector(newList, factory, transformer, tail, meta);
        } else {
            PersistentVector newTail = tail.assocN(tailIndex(i), val);
            return new ProntoVector(base, factory, transformer, newTail, meta);
        }
    }

    @Override
    public int count() {
        return base.size() + tail.count();
    }

    @Override
    public IPersistentVector cons(Object o) {
        PersistentVector tail = this.tail.cons(transformer.toProto(o));
        return new ProntoVector(base, factory, transformer, tail, meta);
    }

    @Override
    public IPersistentCollection empty() {
        return new ProntoVector(Collections.emptyList(), factory, transformer, meta);
    }

    @Override
    public IPersistentStack pop() {
        if (tail.isEmpty()) {
            List newList = factory.newList(base, 0);
            newList.remove(newList.size() - 1);
            return new ProntoVector(newList, factory, transformer, tail, meta);
        } else {
            PersistentVector newTail = tail.pop();
            return new ProntoVector(base, factory, transformer, newTail, meta);
        }
    }

    private int tailIndex(int i) {
        return i - base.size();
    }

    @Override
    public Object nth(int i) {
        Object obj;
        if (i < base.size()) {
            obj = base.get(i);
        } else {
            obj = tail.nth(tailIndex(i));
        }
        return transformer.fromProto(obj);
    }

    @Override
    public Iterator iterator() {
        if (tail.isEmpty()) {
            return base.iterator();
        }

        if (base.isEmpty()) {
            return tail.iterator();
        }

        Iterator itr = base.iterator();
        Iterator itr2 = tail.iterator();

        return new Iterator() {
            @Override
            public boolean hasNext() {
                return itr.hasNext() || itr2.hasNext();
            }

            @Override
            public Object next() {
                return itr.hasNext() ? itr.next() : itr2.next();
            }
        };
    }

    public class Transient implements ITransientCollection {
        private final ITransientCollection vector;

        public Transient() {
            this.vector = tail.asTransient();
        }

        @Override
        public ITransientCollection conj(Object o) {
            vector.conj(transformer.toProto(o));
            return this;
        }

        @Override
        public IPersistentCollection persistent() {
            PersistentVector tail = (PersistentVector) vector.persistent();
            return new ProntoVector(base, factory, transformer, tail, null);
        }
    }
}
