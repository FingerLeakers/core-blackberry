package blackberry.agent.im;

import java.util.Hashtable;

import blackberry.debug.Debug;
import blackberry.debug.DebugLevel;
import blackberry.evidence.DictMarkup;

public class LineMarkup extends DictMarkup {

    //#ifdef DEBUG
    private static Debug debug = new Debug("LineMarkup", DebugLevel.VERBOSE);
    //#endif
    
    Hashtable lineHash = new Hashtable();

    public LineMarkup(int agentId, byte[] aesKey) {
        super(agentId, aesKey);
    }

    public synchronized boolean put(String key, String line) {
 
        Object last = lineHash.put(key, line);
        if(!line.equals(last)){
            //#ifdef DEBUG
            debug.trace("put: serialize");
            //#endif
            return put(key, line.getBytes());
        }
        return true;
        
    }

    public synchronized String getLine(String key) {

        if (lineHash.contains(key)) {
            return (String) lineHash.get(key);
        }

        byte[] data = get(key);
        String line = null;
        if (data != null) {
            line = new String(data);
        }

        return line;

    }

}
