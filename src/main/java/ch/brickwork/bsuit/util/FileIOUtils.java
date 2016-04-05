package ch.brickwork.bsuit.util;

import ch.brickwork.bsuit.globals.BoilerSuitGlobals;
import ch.brickwork.bsuit.interpreter.interpreters.ProcessingResult;
import org.apache.commons.io.filefilter.WildcardFileFilter;



import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Utility class helps with import or export files
 */
public final class FileIOUtils {

    private static final Logger LOG = Logger.getLogger(FileIOUtils.class.getCanonicalName());

    /**
     * Returns an array of files that satisfy the specified filter (accepting wildcard '*').
     * If filter indicates file, array with one file will be returned.
     * <p/>
     * If the given filter is null then all files are returned.
     *
     * @param filter is a filter used to search files, may contains a wildcard
     *
     * @return array of files
     */

    public static File[] getFiles(String directory, String filter)
    {
        File dir;
        String path;
        int slashOrBackslashLastIndex;
        if (filter.contains("\\") || filter.contains("/")) {
            slashOrBackslashLastIndex = filter.lastIndexOf("/");
            if (-1 == slashOrBackslashLastIndex) {
                slashOrBackslashLastIndex = filter.lastIndexOf("\\");
            }
            path = filter.substring(0, slashOrBackslashLastIndex + 1);
            filter = filter.substring(slashOrBackslashLastIndex + 1, filter.length());
            dir = new File(path);
        } else {
            dir = new File(directory);
        }
        final FileFilter fileFilter = new WildcardFileFilter(filter);
        File[] fileList = dir.listFiles(fileFilter);
        if (null != fileList && 0 == fileList.length) {
            File file = new File(filter);
            if (file.isFile()) {
                fileList = new File[1];
                fileList[0] = file;
            }
        } else if (null == fileList) {
            fileList = new File[0];
        }
        return fileList;
    }

    /**
     * Tests if path to a file or directory has write and read privileges.
     *
     * @param path is path to directory or file
     *
     * @return warning message
     */
    public static boolean checkPath(final String path)
    {
        try {
            if (!FileIOUtils.isValidPath(path)) {
                return false;
            }
        } catch (IOException | SecurityException e) {
            return false;
        }
        return true;
    }

    /**
     * Tests whether path can be used to write a file by creating one and deleting test file.
     *
     * @param pathString is path do directory
     *
     * @return true if file exists, false otherwise
     */
    public static boolean isValidPath(final String pathString) throws IOException
    {
        try {
            Path path = FileSystems.getDefault().getPath(pathString);
            if (!Files.exists(path)) {
                path = path.getParent();
            }
            return null != path && Files.isWritable(path) && Files.isReadable(path);
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /**
     * if file exists, deletes it and writes text into it, if does not exist,
     * creates it and writes text into it
     *
     * @param pFileName must be name of a file - if directory is specified, behaviour
     *                  unspecified...
     * @param text      will be written to file
     *
     * @return the File object, or null if not successful
     */

    public static File overwriteFile(final String pFileName, final String text)
    {
        try {

            // open or create file
            final File file = new File(pFileName);
            if (file.exists() && file.delete() && !file.createNewFile()) {
                return null;
            }

            // write
            final FileWriter fw = new FileWriter(file);
            for (int i = 0; i < text.length(); i++) {
                fw.append(text.charAt(i));
            }
            fw.flush();
            return file;
        } catch (IOException e) {
            LOG.severe(e.getMessage());
            return null;
        }
    }

    /**
     * reads in the whole file with name pFileName, returns the content as a
     * String, or null, if any troubles occurred
     *
     * @TODO: hm...check first if statement!!!! w.t.f.
     *
     * @param pFileName is name of file
     *
     * @return file contents as a String, or null
     */

    public static String readCompleteFile(final String directory, final String pFileName)
    {

        File file = new File(pFileName);
        if (!file.exists()) {
            file = new File(directory + File.separator + pFileName);
        }

        return readCompleteFile(file);
    }

    public static String readCompleteFile(final File file) {
        char[] buf = null;
        if (file.exists()) {
            try {
                final FileReader fr = new FileReader(file);
                final long l = file.length();
                buf = new char[(int) l];
                fr.read(buf, 0, (int) l);
            } catch (Exception e) {
                LOG.severe(e.getMessage());
            }
            return buf != null ? String.valueOf(buf) : null;
        } else {
            return null;
        }
    }



    public static void deleteRuthlessly(File fileOrDirectory) {
        if(fileOrDirectory.exists()) {
            if(fileOrDirectory.isDirectory()) {
                for(File child : fileOrDirectory.listFiles()) {
                    deleteRuthlessly(child);
                }
                fileOrDirectory.delete();
            }
            else
                fileOrDirectory.delete();
        }
    }
}
