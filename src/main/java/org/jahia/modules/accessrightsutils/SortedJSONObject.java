package org.jahia.modules.accessrightsutils;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class SortedJSONObject extends JSONObject {

    private static final Logger logger = LoggerFactory.getLogger(SortedJSONObject.class);

    public SortedJSONObject() {
        super();
    }

    public SortedJSONObject(String source) throws JSONException {
        super(source);
    }

    @Override
    protected Set<Map.Entry<String, Object>> entrySet() {
        final Set<Map.Entry<String, Object>> set = new TreeSet<>(Map.Entry.comparingByKey());
        set.addAll(super.entrySet());
        return set;
    }
}
