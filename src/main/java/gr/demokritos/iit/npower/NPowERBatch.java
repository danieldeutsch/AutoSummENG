/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gr.demokritos.iit.npower;

import gr.demokritos.iit.conceptualIndex.structs.Distribution;
import gr.demokritos.iit.jinsect.documentModel.comparators.NGramCachedGraphComparator;
import gr.demokritos.iit.jinsect.documentModel.representations.DocumentNGramGraph;
import gr.demokritos.iit.jinsect.documentModel.representations.DocumentNGramSymWinGraph;
import gr.demokritos.iit.jinsect.structs.GraphSimilarity;
import gr.demokritos.iit.jinsect.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 *
 * @author danieldeutsch
 */
public class NPowERBatch {

    public static void printSyntax() {
        System.out.println("Syntax: NPowER \"-files=files.tsv\" -output=\"output.tsv\""
                + "[-minN=3] [-maxN=3] [-dwin=3]"
                + " [-minScore=0.0] [-maxScore=1.0]");

        // TODO: Add the ability to output all scores (AutoSummENG, MeMoG, NPowER)
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        Hashtable hSwitches = utils.parseCommandLineSwitches(args);

        String filesTsv = utils.getSwitch(hSwitches, "files", "").trim();
        String outputTsv = utils.getSwitch(hSwitches, "output", "").trim();
        Integer iMinN = Integer.valueOf(utils.getSwitch(hSwitches, "minN",
                "3").trim());
        Integer iMaxN = Integer.valueOf(utils.getSwitch(hSwitches, "maxN",
                "3").trim());
        Integer iDWin = Integer.valueOf(utils.getSwitch(hSwitches, "dwin",
                "3").trim());
        Double dMinScore = Double.valueOf(utils.getSwitch(hSwitches,
                "minScore", "0.0").trim());
        Double dMaxScore = Double.valueOf(utils.getSwitch(hSwitches,
                "maxScore", "1.0").trim());

        // Show params to command line
        System.err.println(String.format("MinN: %d, MaxN: %d, Dwin: %d\n"
                        + "MinScore: %4.2f, MaxScore: %4.2f\n", iMinN, iMaxN, iDWin,
                dMinScore, dMaxScore));

        if (filesTsv.length() == 0 || outputTsv.length() == 0) {
            printSyntax();
            return;
        }

        BufferedWriter writer = new BufferedWriter(new FileWriter(outputTsv));
        writer.write("Model\tPeer\tAutoSummENG\tMeMoG\tNPowER\n");

        int modelCounter = 0;
        Scanner scanner = new Scanner(new File(filesTsv));
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            String[] columns = line.split("\t");
            if (columns.length != 2) {
                throw new RuntimeException("Expected two columns. Found " + columns.length + ". " + line);
            }
            String[] sModels = columns[0].split(",");
            String[] sPeers = columns[1].split(",");

            // Init merged model graph (MeMoG)
            DocumentNGramGraph dggModel = new DocumentNGramSymWinGraph(iMinN, iMaxN,
                    iDWin);

            // Init AutoSummENG graph map and result distribution
            Map<String, DocumentNGramGraph> mModelGraphs =
                    new TreeMap<String, DocumentNGramGraph>();
            Distribution<String> dRes = new Distribution<String>();

            List<String> lModelFiles = new ArrayList<String>();

            // For every model
            System.out.println("Loading models " + modelCounter);
            double dMergeCnt = 0.0;
            for (String sCurModel : sModels) {
                File fCurModel = new File(sCurModel);
                if (!fCurModel.canRead()) {
                    System.err.println("Could not read model " + sCurModel);
                    return;
                }
                // Update model list
                lModelFiles.add(sCurModel);

                DocumentNGramGraph dggCur = new DocumentNGramSymWinGraph(iMinN,
                        iMaxN, iDWin);
                try {
                    // Load file
                    dggCur.loadDataStringFromFile(sCurModel);
                    // Update AutoSummENG map
                    mModelGraphs.put(sCurModel, dggCur);

                    // Always increase merge count
                    if (dMergeCnt++ == 0) {
                        // Init graph
                        dggModel = dggCur;
                    } else {
                        // Merge into MeMoG
                        dggModel.merge(dggCur, 1.0 / dMergeCnt);
                    }
                } catch (IOException ex) {
                    System.err.println("Could not read model " + sCurModel
                            + "\n" + ex.getLocalizedMessage());
                    return;
                }
            }

            System.out.println("Processing peers");
            int peerCounter = 0;
            for (String sPeer : sPeers) {
                // Load peer text graph
                DocumentNGramGraph dggPeer = new DocumentNGramSymWinGraph(iMinN,
                        iMaxN, iDWin);
                if (!new File(sPeer).canRead()) {
                    System.err.println("Could not read peer file " + sPeer);
                    return;
                }
                try {
                    dggPeer.loadDataStringFromFile(sPeer);
                } catch (IOException ex) {
                    System.err.println("Could not read peer file " + sPeer
                            + "\n" + ex.getLocalizedMessage());
                }

                // Calculate MeMoG
                NGramCachedGraphComparator ngc = new NGramCachedGraphComparator();
                GraphSimilarity gs = ngc.getSimilarityBetween(dggPeer, dggModel);

                // Calculate AutoSummENG
                for (String sCurModel : lModelFiles) {
                    NGramCachedGraphComparator ngcA = new NGramCachedGraphComparator();
                    gs = ngcA.getSimilarityBetween(dggPeer,
                            mModelGraphs.get(sCurModel));
                    dRes.setValue(sCurModel, gs.ValueSimilarity);
                }

                // Calculate overall value
                // Model (based on TAC 2009 and TAC 2010 - A corpus)
                //5.2905 * AutoSummENG +
                //      3.0053 * MeMoG +
                //      0.5866
                double dMeMoG = gs.ValueSimilarity;
                double dAutoSummENG = dRes.average(true);
                // Actually using normalized score
                double dNPowER = (5.2905 * dAutoSummENG + 3.0053 * dMeMoG + 0.5866) / 10;
                dNPowER = dMinScore + (dMaxScore - dMinScore) * dNPowER;

                writer.write(String.format("%d\t%d\t%8.6f\t%8.6f\t%8.6f\n", modelCounter, peerCounter, dAutoSummENG,
                        dMeMoG, dNPowER));

                peerCounter++;
            }

            modelCounter++;
        }
        writer.close();
    }
}
