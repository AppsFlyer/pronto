package pronto;

import clojure.lang.*;
import com.google.protobuf.ByteString;

import java.util.RandomAccess;

public class ByteStringColl implements IPersistentCollection, RandomAccess, IReduce, Indexed, ByteStringWrapper {
    private final ByteString bs;

    private static final ByteStringColl EMPTY = new ByteStringColl(ByteString.EMPTY);

    private ByteStringColl(ByteString bs) {
        this.bs = bs;
    }

    public static ByteStringColl fromByteString(ByteString bs) {
        if (bs == null) {
            throw new NullPointerException("Cannot create ByteStringColl from null");
        }
        if (bs.isEmpty()) {
            return EMPTY;
        }
        return new ByteStringColl(bs);
    }

    @Override
    public int count() {
        return bs.size();
    }

    @Override
    public IPersistentCollection cons(Object o) {
        byte b = ((Number) o).byteValue();
        return new ByteStringColl(bs.concat(ByteString.copyFrom(new byte[] { b })));
    }

    @Override
    public IPersistentCollection empty() {
        return EMPTY;
    }

    @Override
    public boolean equiv(Object o) {
        if (this == o) {
            return true;
        }

        if (o instanceof ByteStringWrapper) {
            return bs.equals(((ByteStringWrapper) o).getByteString());
        }

        if (o instanceof Sequential) {
            ISeq seq = RT.seq(o);

            if (seq instanceof Counted && ((Counted) seq).count() != bs.size()) {
                return false;
            }

            for (int i = 0; i < bs.size(); i++, seq = seq.next()) {
                if (seq == null || !Util.equiv(byteAt(i), seq.first())) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return bs.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return equiv(obj);
    }

    @Override
    public ISeq seq() {
        if (bs.size() == 0) {
            return null;
        }
        return new ByteStringSeq(null, 0);
    }

    private byte byteAt(int index) {
        return bs.byteAt(index);
    }

    @Override
    public Object nth(int i) {
        return byteAt(i);
    }

    @Override
    public Object nth(int i, Object o) {
        return i > 0 && i < count() ? nth(i) : o;
    }

    @Override
    public Object reduce(IFn f) {
        return reduce(f, 0);
    }

    private Object reduce(IFn f, int startOffset) {
        if (bs.isEmpty()) {
            return f.invoke();
        }

        Object ret = bs.byteAt(startOffset);

        for(int x = startOffset + 1; x < bs.size(); ++x) {
            ret = f.invoke(ret, byteAt(x));
            if (RT.isReduced(ret)) {
                return ((IDeref)ret).deref();
            }
        }

        return ret;
    }

    @Override
    public Object reduce(IFn f, Object start) {
        return reduce(f, start, 0);
    }

    public Object reduce(IFn f, Object start, int startOffset) {
        if (bs.isEmpty()) {
            return start;
        }

        Object ret = f.invoke(start, byteAt(startOffset));

        for(int x = startOffset + 1; x < bs.size(); ++x) {
            if (RT.isReduced(ret)) {
                return ((IDeref)ret).deref();
            }

            ret = f.invoke(ret, byteAt(x));
        }

        if (RT.isReduced(ret)) {
            return ((IDeref)ret).deref();
        } else {
            return ret;
        }
    }

    public ByteString getByteString() {
        return bs;
    }

    private class ByteStringSeq extends ASeq implements IndexedSeq, IReduce, Indexed, ByteStringWrapper {
        private final int i;

        ByteStringSeq(IPersistentMap meta, int i) {
            super(meta);
            this.i = i;
        }

        public Object first() {
            return byteAt(i);
        }

        public ISeq next() {
            return i + 1 < bs.size() ? new ByteStringSeq(meta(), i + 1) : null;
        }

        @Override
        public ISeq cons(Object o) {
            return ByteStringColl.this.cons(o).seq();
        }

        public int count() {
            return bs.size() - i;
        }

        public int index() {
            return i;
        }

        public ByteStringSeq withMeta(IPersistentMap meta) {
            return new ByteStringSeq(meta, i);
        }

        public Object reduce(IFn f) {
            return ByteStringColl.this.reduce(f, i);
        }

        public Object reduce(IFn f, Object start) {
            return ByteStringColl.this.reduce(f, start, i);
        }

        public int indexOf(Object o) {
            if (o instanceof Number) {
                int k = ((Number)o).byteValue();

                for(int j = i; j < bs.size(); ++j) {
                    if (k == byteAt(j)) {
                        return j - i;
                    }
                }
            }

            return -1;
        }

        public int lastIndexOf(Object o) {
            if (o instanceof Number) {
                int k = ((Number)o).intValue();

                for(int j = bs.size() - 1; j >= i; --j) {
                    if (k == byteAt(j)) {
                        return j - i;
                    }
                }
            }

            return -1;
        }

        @Override
        public Object nth(int i) {
            return ByteStringColl.this.nth(this.i + i);
        }

        @Override
        public Object nth(int i, Object o) {
            return ByteStringColl.this.nth(this.i + i);
        }

        public ByteString getByteString() {
            return bs.substring(i);
        }
    }
}
