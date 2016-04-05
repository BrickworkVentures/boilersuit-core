package ch.brickwork.bsuit.tools.manual;


import ch.brickwork.bsuit.util.FileIOUtils;

import java.io.File;
import java.io.IOException;


public class FileProcessor {

    private final File interpretersPath;
    private final File outputPath;
    private String currentOutput = "";
    private String currentHeader = "";


    public FileProcessor(File interpretersPath, File outputPath) {
        this.interpretersPath = interpretersPath;
        this.outputPath = outputPath;
    }

    public boolean process() {
        // create output path if does not exist
        if (!outputPath.exists())
            outputPath.mkdir();

        boolean success = true;

        success &= apply();

        return success;
    }

    private boolean apply() {
        outputPath.delete();
        try {
            outputPath.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        apply(interpretersPath);

        FileIOUtils.overwriteFile(outputPath.getAbsolutePath(),
                "<html>\n" +
                        "<head>\n" +
                        "<title>Interpreters</title>\n" +
                        "<link rel=\"stylesheet\" type=\"text/css\" href=\"interpreters.css\" />\n" +
                        "</head>\n" +
                        "<body>\n" +
                "<div id=\"menu\">" + currentHeader + "</div>" + "<div id=\"main\">" + currentOutput + "</div>" +
                        "</body>\n" +
                "</html>"
        );

        System.out.println("Processed " + directoriesCount + " folders and " + templatesCount + " jason files.");
        return false;
    }

    int directoriesCount = 0;
    int templatesCount = 0;

    private boolean apply(File file) {
        boolean success = true;

        // recursively traverse all files
        for (File childFile : file.listFiles()) {
            if (childFile.isHidden())
                continue;

            if (childFile.isDirectory()) {
                System.out.println("Entering " + childFile.getAbsolutePath());
                success &= apply(childFile);
                directoriesCount++;
            } else {
                if (childFile.getName().contains("Interpreter.java")
                        && !childFile.getName().equals("AbstractInterpreter.java")
                        && !childFile.getName().equals("IInterpreter.java")
                        && !childFile.getName().equals("ExitInterpreter.java")
                        ) {
                    // process
                    String templateCode = FileIOUtils.readCompleteFile(interpretersPath.getAbsolutePath(), childFile.getAbsolutePath());

                    System.out.println("Processing " + childFile.getAbsolutePath() + "...");

                    processCode(childFile.getName(), templateCode);

                    templatesCount++;
                }
            }
        }

        return success;
    }

    private void processCode(String file, String templateCode) {
        String bookmarkId = file.replace("Interpreter.java", "");
        currentHeader += "\n<br/><a href=\"#" + bookmarkId + "\">" + bookmarkId + "</a>";
        currentOutput += "<div class=\"interpreter\">\n";
        currentOutput += "<a name=\"" + bookmarkId + "\"></a>";
        currentOutput += "<h1>" + bookmarkId + "</h1>\n";
        currentOutput += readRelevantContent(templateCode);
        currentOutput += "</div>";
    }

    private String readRelevantContent(String templateCode) {
        int firstCommentOpen = templateCode.indexOf("/**");
        int commentClose = templateCode.indexOf("*/", firstCommentOpen);
        return templateCode.substring(firstCommentOpen + 3, commentClose).replaceAll("</pre>.*\\n+.*\\*", "</pre>").replaceAll(" \\*", "");
    }
}
