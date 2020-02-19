package protogen;

import clojure.lang.*;
import protogen.generated.People;

import java.util.Iterator;

public class LikeProtoMap extends APersistentMap {
    private static final Keyword _DESC  = Keyword.intern("desc");
    private static final Keyword _LEVEL = Keyword.intern("level");

    private final People.Like like;

    public static People.Like mapToProto(IPersistentMap map) {
        if (map instanceof LikeProtoMap) {
            return ((LikeProtoMap)map).getProto();
        }
        People.Like.Builder builder = People.Like.newBuilder();
        IMapEntry desc = map.entryAt(_DESC);
        if (desc != null && desc.key() != null) {
            builder.setDesc((String) desc.getValue());
        }
        IMapEntry level = map.entryAt(_LEVEL);
        if (level != null && level.key() != null) {
            Keyword levelKw = (Keyword)level.getValue();
            builder.setLevel(People.Level.valueOf(levelKw.getName()));
        }
        return builder.build();
    }

    public LikeProtoMap(People.Like like) {
        this.like = like;
    }

    public People.Like getProto() {
        return like;
    }

    @Override
    public boolean containsKey(Object key) {
        return _DESC.equals(key) || _LEVEL.equals(key);
    }

    @Override
    public IMapEntry entryAt(Object key) {
        if (!containsKey(key)) {
            return null;
        }

        Object val = valAt(key);
        return new MapEntry(key, val);
    }

    @Override
    public IPersistentMap assoc(Object key, Object val) {
        if (_DESC.equals(key)) {
            if (val == null || val.getClass() != String.class) {
                throw new IllegalArgumentException("wrong value type");
            }
            String desc = (String) val;
            return new LikeProtoMap(like.toBuilder().setDesc(desc).build());
        }
        if (_LEVEL.equals(key)) {
            if (val.getClass() == Keyword.class) {
                Keyword level = (Keyword)val;
                return new LikeProtoMap(like.toBuilder().setLevel(People.Level.valueOf(level.getName())).build());
            }
            if (People.Level.class.isAssignableFrom(val.getClass())) {
                People.Level level = (People.Level)val;
                return new LikeProtoMap(like.toBuilder().setLevel(level).build());
            }
            throw new IllegalArgumentException("wrong value type");
        }
        throw new IllegalArgumentException("can't assoc that key");
    }

    @Override
    public IPersistentMap assocEx(Object key, Object val) {
        if(containsKey(key))
            throw new IllegalArgumentException("Key already present");

        return assoc(key, val);
    }

    @Override
    public IPersistentMap without(Object key) {
        throw new UnsupportedOperationException("can't dissoc from a proto map.");
    }

    @Override
    public Object valAt(Object key) {
        return valAt(key, null);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == null) {
            return notFound;
        }
        if (key.equals(_DESC)) {
            String desc = like.getDesc();
            if (desc == null) {
                return notFound;
            }
            return desc;
        }
        if (key.equals(_LEVEL)) {
            People.Level level = like.getLevel();
            if (level == null) {
                return notFound;
            }
            return Keyword.intern(level.name());
        }
        return notFound;
    }

    @Override
    public int count() {
        return 2;
    }

    @Override
    public IPersistentCollection empty() {
        return new LikeProtoMap(People.Like.newBuilder().build());
    }

    @Override
    public ISeq seq() {
        return ArraySeq.create(
                new MapEntry(_DESC,  valAt(_DESC)),
                new MapEntry(_LEVEL, valAt(_LEVEL)));
    }

    @Override
    public Iterator iterator() {
        return new ArrayIter<>(
                new MapEntry(_DESC, valAt(_DESC)),
                new MapEntry(_LEVEL, valAt(_LEVEL)));
    }
}
