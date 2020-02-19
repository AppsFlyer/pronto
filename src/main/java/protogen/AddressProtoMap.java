package protogen;

import clojure.lang.*;
import protogen.generated.People;

import java.util.Iterator;

public class AddressProtoMap extends APersistentMap {
    private static final Keyword _CITY       = Keyword.intern("city");
    private static final Keyword _STREET     = Keyword.intern("street");
    private static final Keyword _HOUSE_NUM  = Keyword.intern("house_num");

    private final People.Address address;

    public static People.Address mapToProto(IPersistentMap map) {
        if (map instanceof AddressProtoMap) {
            return ((AddressProtoMap)map).getProto();
        }
        People.Address.Builder builder = People.Address.newBuilder();
        IMapEntry city = map.entryAt(_CITY);
        if (city != null && city.key() != null) {
            builder.setCity((String) city.getValue());
        }
        IMapEntry street = map.entryAt(_STREET);
        if (street != null && street.key() != null) {
            builder.setStreet((String) street.getValue());
        }
        IMapEntry houseNum = map.entryAt(_HOUSE_NUM);
        if (houseNum != null && houseNum.key() != null) {
            builder.setHouseNum((Integer) houseNum.getValue());
        }
        return builder.build();
    }

    public AddressProtoMap(People.Address address) {
        this.address = address;
    }

    public People.Address getProto() {
        return address;
    }

    @Override
    public boolean containsKey(Object key) {
        return _CITY.equals(key) || _STREET.equals(key) || _HOUSE_NUM.equals(key);
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
        if (_CITY.equals(key)) {
            if (val == null || val.getClass() != String.class) {
                throw new IllegalArgumentException("wrong value type");
            }
            String city = (String) val;
            return new AddressProtoMap(address.toBuilder().setCity(city).build());
        }
        if (_STREET.equals(key)) {
            if (val.getClass() != String.class) {
                throw new IllegalArgumentException("wrong value type");
            }
            String street = (String)val;
            return new AddressProtoMap(address.toBuilder().setStreet(street).build());
        }
        if (_HOUSE_NUM.equals(key)) {
            if (val.getClass() != Integer.class) {
                throw new IllegalArgumentException("wrong value type");
            }
            Integer houseNum = (Integer) val;
            return new AddressProtoMap(address.toBuilder().setHouseNum(houseNum).build());
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
        if (key.equals(_CITY)) {
            String city = address.getCity();
            if (city == null) {
                return notFound;
            }
            return city;
        }
        if (key.equals(_STREET)) {
            String street = address.getStreet();
            if (street == null) {
                return notFound;
            }
            return street;
        }
        if (key.equals(_HOUSE_NUM)) {
            return address.getHouseNum();
        }
        return notFound;
    }

    @Override
    public int count() {
        return 3;
    }

    @Override
    public IPersistentCollection empty() {
        return new AddressProtoMap(People.Address.newBuilder().build());
    }

    @Override
    public ISeq seq() {
        return ArraySeq.create(
                new MapEntry(_CITY,      valAt(_CITY)),
                new MapEntry(_STREET,    valAt(_STREET)),
                new MapEntry(_HOUSE_NUM,   valAt(_HOUSE_NUM)));
    }

    @Override
    public Iterator iterator() {
        return new ArrayIter<>(
                new MapEntry(_CITY, valAt(_CITY)),
                new MapEntry(_STREET, valAt(_STREET)),
                new MapEntry(_HOUSE_NUM, valAt(_HOUSE_NUM)));
    }
}
