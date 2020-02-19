package protogen;

import clojure.lang.*;
import protogen.generated.People;

import java.util.*;

public class PersonProtoMap extends APersistentMap {
    private static final Keyword _ID      = Keyword.intern("id");
    private static final Keyword _NAME    = Keyword.intern("name");
    private static final Keyword _EMAIL   = Keyword.intern("email");
    private static final Keyword _ADDRESS = Keyword.intern("address");
    private static final Keyword _LIKES   = Keyword.intern("likes");

    private final People.Person person;

    public static People.Person mapToProto(IPersistentMap map) {
        if (map instanceof PersonProtoMap) {
            return ((PersonProtoMap)map).getProto();
        }
        People.Person.Builder builder = People.Person.newBuilder();
        IMapEntry id = map.entryAt(_ID);
        if (id != null && id.key() != null) {
            builder.setId((Integer) id.getValue());
        }
        IMapEntry name = map.entryAt(_NAME);
        if (name != null && name.key() != null) {
            builder.setName((String) name.getValue());
        }
        IMapEntry email = map.entryAt(_EMAIL);
        if (email != null && email.key() != null) {
            builder.setEmail((String) email.getValue());
        }
        IMapEntry address = map.entryAt(_ADDRESS);
        if (address != null && address.key() != null) {
            builder.setAddress(AddressProtoMap.mapToProto((IPersistentMap) address.getValue()));
        }
        IMapEntry likes = map.entryAt(_LIKES);
        if (likes != null && likes.key() != null) {
            Iterable<?> likeSeq = (Iterable<?>) likes.getValue();
            List<People.Like> protoLikes = new ArrayList<>();
            for (Object like : likeSeq) {
                protoLikes.add(LikeProtoMap.mapToProto((IPersistentMap)like));
            }
            builder.addAllLikes(protoLikes);
        }
        return builder.build();
    }

    public PersonProtoMap(IPersistentMap person) {
        if (person == null) {
            throw new IllegalArgumentException("person can't be null");
        }
        this.person = mapToProto(person);
    }

    public PersonProtoMap(People.Person person) {
        if (person == null) {
            throw new IllegalArgumentException("person can't be null");
        }
        this.person = person;
    }

    public People.Person getProto() {
        return person;
    }

    @Override
    public boolean containsKey(Object key) {
        return _ID.equals(key) || _NAME.equals(key) || _EMAIL.equals(key) || _ADDRESS.equals(key) || _LIKES.equals(key);
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
        if (_ID.equals(key)) {
            if (val == null || val.getClass() != Integer.class) {
                throw new IllegalArgumentException("wrong value type");
            }
            Integer id = (Integer)val;
            return new PersonProtoMap(person.toBuilder().setId(id).build());
        }
        if (_NAME.equals(key)) {
            if (val.getClass() != String.class) {
                throw new IllegalArgumentException("wrong value type");
            }
            String name = (String)val;
            return new PersonProtoMap(person.toBuilder().setName(name).build());
        }
        if (_EMAIL.equals(key)) {
            if (val.getClass() != String.class) {
                throw new IllegalArgumentException("wrong value type");
            }
            String email = (String)val;
            return new PersonProtoMap(person.toBuilder().setEmail(email).build());
        }
        if (_ADDRESS.equals(key)) {
            if (val.getClass() == People.Address.class) {
                People.Address address = (People.Address)val;
                return new PersonProtoMap(person.toBuilder().setAddress(address).build());
            }
            if (val.getClass() == AddressProtoMap.class) {
                People.Address address = ((AddressProtoMap)val).getProto();
                return new PersonProtoMap(person.toBuilder().setAddress(address).build());
            }
            if (IPersistentMap.class.isAssignableFrom(val.getClass())) {
                IPersistentMap map = (IPersistentMap) val;
                return new PersonProtoMap(person.toBuilder().setAddress(AddressProtoMap.mapToProto(map)).build());
            }
            throw new IllegalArgumentException("wrong value type");
        }
        if (_LIKES.equals(key)) {
            if (Iterable.class.isAssignableFrom(val.getClass())) {
                Iterable<?> likes = (Iterable<?>) val;
                List<People.Like> protoLikes = new ArrayList<>();
                for (Object like : likes) {
                    if (like.getClass() == People.Like.class) {
                        protoLikes.add((People.Like) like);
                    }
                    if (like.getClass() == LikeProtoMap.class) {
                        protoLikes.add(((LikeProtoMap)like).getProto());
                    }
                    if (IPersistentMap.class.isAssignableFrom(like.getClass())) {
                        IPersistentMap likeMap = (IPersistentMap) like;
                        protoLikes.add(LikeProtoMap.mapToProto(likeMap));
                    }
                    throw new IllegalArgumentException("bad element type inside collection");
                }

                return new PersonProtoMap(
                        person.toBuilder().
                                clearLikes().
                                addAllLikes(protoLikes).
                                build());
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

        if (key.equals(_ID)) {
            return person.getId(); // primitive val, can't be null
        }
        if (key.equals(_NAME)) {
            String name = person.getName();
            if (name == null) {
                return notFound;
            }
            return name;
        }
        if (key.equals(_EMAIL)) {
            String email = person.getEmail();
            if (email == null) {
                return notFound;
            }
            return email;
        }
        if (key.equals(_ADDRESS)) {
            People.Address address = person.getAddress();
            if (address == null) {
                return notFound;
            } else {
                return new AddressProtoMap(address);
            }
        }
        if (key.equals(_LIKES)) {
            List<People.Like> likes = person.getLikesList();
            if (likes == null) {
                return notFound;
            } else {
                List<LikeProtoMap> protoLikes = new ArrayList<>();
                for (People.Like like : likes) {
                   protoLikes.add(new LikeProtoMap(like));
                }
                return PersistentVector.create(protoLikes);
            }
        }
        return notFound;
    }

    @Override
    public int count() {
        return 5;
    }

    @Override
    public IPersistentCollection empty() {
        return new PersonProtoMap(People.Person.newBuilder().build());
    }

    @Override
    public ISeq seq() {
        return ArraySeq.create(
                new MapEntry(_ID,      valAt(_ID)),
                new MapEntry(_NAME,    valAt(_NAME)),
                new MapEntry(_EMAIL,   valAt(_EMAIL)),
                new MapEntry(_ADDRESS, valAt(_ADDRESS)),
                new MapEntry(_LIKES,   valAt(_LIKES)));
    }

    @Override
    public Iterator iterator() {
        return new ArrayIter<>(
                new MapEntry(_ID, valAt(_ID)),
                new MapEntry(_NAME, valAt(_NAME)),
                new MapEntry(_EMAIL, valAt(_EMAIL)),
                new MapEntry(_ADDRESS, valAt(_ADDRESS)),
                new MapEntry(_LIKES, valAt(_LIKES)));
    }
}
