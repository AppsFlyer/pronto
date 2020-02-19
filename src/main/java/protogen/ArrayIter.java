package protogen;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class ArrayIter<E> implements Iterator<E> {
    private int cursor;
    private final E[] a;

    public ArrayIter(E... a) {
        this.a = a;
    }

    @Override
    public boolean hasNext() {
        return cursor < a.length;
    }

    @Override
    public E next() {
        int i = cursor;
        if (i >= a.length) {
            throw new NoSuchElementException();
        }
        cursor = i + 1;
        return a[i];
    }
}
