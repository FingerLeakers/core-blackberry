/* *************************************************
 * Copyright (c) 2010 - 2010
 * HT srl,   All rights reserved.
 * Project      : RCS, RCSBlackBerry_lib
 * File         : ProtocolException.java
 * Created      : 6-apr-2010
 * *************************************************/
package blackberry.transfer;

import blackberry.utils.Debug;
import blackberry.utils.DebugLevel;

// TODO: Auto-generated Javadoc
/**
 * The Class ProtocolException. Viene lanciato in caso di eccezione durante
 * l'esecuzione di un protocollo. Come effetto fa cadere la comunicazione.
 */
public class ProtocolException extends Exception {
    //#debug
    static Debug debug = new Debug("ProtocolEx", DebugLevel.VERBOSE);

    public boolean bye;

    /**
     * Instantiates a new protocol exception.
     * 
     * @param string
     *            the string
     */
    public ProtocolException(final String string) {
        this(string, false);

    }

    /**
     * Instantiates a new protocol exception.
     * 
     * @param string
     *            the string
     * @param bye_
     *            the bye_
     */
    public ProtocolException(final String string, final boolean bye_) {
        super(string);
        bye = bye_;
        // #debug
        debug.error(string + " bye:" + bye_);
    }
}