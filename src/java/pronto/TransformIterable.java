package pronto;

import java.util.Iterator;

public class TransformIterable implements Iterable {

    public interface Xf {
        Object transform(Object item);
    }

    static class Iter implements Iterator {

        private final Iterator i;
        private final Xf xf;

        Iter(Iterator i, Xf xf) {
            this.i = i;
            this.xf = xf;
        }

        @Override
        public boolean hasNext() {
            return i.hasNext();
        }

        @Override
        public Object next() {
            return xf.transform(i.next());
        }
    }

    private final Iterable inner;
    private final Xf xf;

    public TransformIterable(Iterable inner, Xf xf) {
        this.inner = inner;
        this.xf = xf;
    }

    @Override
    public Iterator iterator() {
        return new Iter(inner.iterator(), xf);
    }
}
