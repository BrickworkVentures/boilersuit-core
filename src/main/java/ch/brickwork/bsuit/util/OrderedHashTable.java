package ch.brickwork.bsuit.util;

import ch.brickwork.bsuit.database.Value;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * Created by marcel on 09.07.15.
 */
public class OrderedHashTable<V> implements Iterable<String> {

    private final class Entry {
        public String key;
        public V val;
    }

    private final boolean keyIgnoresCase;
    private LinkedHashMap<String, Entry> linkedHashMap = new LinkedHashMap<String, Entry>();

    public OrderedHashTable(boolean keyIgnoresCase) {
        this.keyIgnoresCase = keyIgnoresCase;
    }

    public void put(String key, V val) {
        Entry entry = new Entry();
        entry.key = key;
        entry.val = val;

        if(keyIgnoresCase)
            linkedHashMap.put(key.toLowerCase(), entry);
        else
            linkedHashMap.put(key, entry);
    }

    public V get(String key) {
        if(keyIgnoresCase) {
            if(linkedHashMap.get(key.toLowerCase()) != null)
                return linkedHashMap.get(key.toLowerCase()).val;
            else
                return null;
        }
        else {
            if(linkedHashMap.get(key) != null)
                return linkedHashMap.get(key).val;
            else
                return null;
        }
    }

    public V get(int index) {
        return linkedHashMap.get(new ArrayList<>(linkedHashMap.keySet()).get(index)).val;
    }

    public Set<String> keySet() {
        return linkedHashMap.keySet();
    }


    public int size() {
        return linkedHashMap.size();
    }


    public String getKeyInCase(String key) {
        if(keyIgnoresCase) {
            if(linkedHashMap.get(key.toLowerCase()) != null)
                return linkedHashMap.get(key.toLowerCase()).key;
            else
                return null;
        }
        else {
            if(linkedHashMap.get(key) != null)
                return linkedHashMap.get(key).key;
            else
                return null;
        }
    }

    /**
     * returns iterator over original keys, e.g. with case
     * @return
     */
    public Iterator<String> iterator() {
        final Iterator<String> iterator = linkedHashMap.keySet().iterator();
        return new Iterator<String>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public void remove()
            {
                throw new UnsupportedOperationException("Not supported here!");
            }

            @Override
            public String next() {
                String key = iterator.next();
                return linkedHashMap.get(key).key;
            }
        };
    }

    public Iterator<V> getValuesIterator()
    {
        final Iterator<Entry> iterator = linkedHashMap.values().iterator();
        return new Iterator<V>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public void remove()
            {
                throw new UnsupportedOperationException("Not supported here!");
            }

            @Override
            public V next() {
                return iterator.next().val;
            }
        };
    }

}
