package ch.brickwork.bsuit.tools.manual;

import java.io.File;

/**
 * Tool to update HTML doc on interpreters from interpreters source files
 * Created by marcel on 12/18/15.
 */
public class CreateManualFromInterpreters {
    public static void main(String[] args) {
        System.out.println("Boilersuit Manual Generator - Generates documentation from Interpreter files\n" +
                "(c) Brickwork Ventures LLC, 2015, Switzerland.\n");

        if(args.length == 0) {
            printUsage();
            System.exit(0);
        }


        if(args.length != 2) {
            printUsage();
            System.exit(1);
        }

        File interpretersDir = new File(args[0]);
        if(!interpretersDir.exists() || !interpretersDir.isDirectory()) {
            System.out.println("Interpreters Directory does not exist or is not a directory");
            System.exit(2);
        }


        FileProcessor fp = new FileProcessor(interpretersDir, new File(args[1]));
        fp.process();
    }


    private static void printUsage() {
        System.out.println("Usage: bscreateman <Interpreters-Path> <Output-File>\n\n");
    }
}
