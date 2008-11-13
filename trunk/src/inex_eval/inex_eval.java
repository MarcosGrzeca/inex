/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package inex_eval;

import java.io.*;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;


/**
 *
 * @author marijnkoolen
 */
public class inex_eval {
    public static String version = "1.0";
    public static double recallWeight = 0.25; //1; //Weight of recall in F-measure
    public static int maxBEPDistance = 500; //1000; //Max. BEP distance
    public static int meanPassageLength = 18; //Length for unknown passage
    public static boolean detail = false;
    public static boolean complete = false;
    public static boolean all = false; // TO DO: show all measures
    public static char task;
    // initialise cutoffs
    public static int [] focussedCutoffs = {
        0, 
        1, 
        5, 
        10
    };
    public static int [] inContextCutoffs = {
        1, 
        2, 
        3, 
        //4, 
        5, 
        10, 
        25, 
        50, 
        //250, 
       // 500, 
        //1000
    };
    public static int points = 0;

    
        
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
        // read command line arguments
        if (args.length < 2) {
            PrintUsage();
            System.exit(0);
        }

        int i = 0;
        String arg;
        String taskName = "Unknown";
        
        while (i < args.length && args[i].startsWith("-")) {
            arg = args[i++];
            // -q option prints per topic scores
            if (arg.equals("-q")) {
                detail = true;
            }
            // -c option evaluates over all topics in qrels, irrespective of
            // runs containing results for all topics
            else if (arg.equals("-c")) {
                complete = true;
            }
            else if (arg.equals("-f")) {
                task = 'f';
                taskName = "Focussed";
                points = 101;
            }
            else if (arg.equals("-r")) {
                task = 'r';
                taskName = "Relevant in Context";
                points = 11;
            }
            else if (arg.equals("-b")) {
                task = 'b';
                taskName = "Best in Context";
                points = 11;
            }
            // Get BEP's n from "-n 1000"
            else if (arg.equals("-n")) {
                try {
                    maxBEPDistance = Integer.parseInt(args[i++]);
                } 
                catch (NumberFormatException e) {
                    System.err.println("Argument must be an integer");
                    System.exit(1);
                }
            }
            // Get F's beta from "-beta 0.5"
            else if (arg.equals("-beta")) {
                try {
                    recallWeight = Double.parseDouble(args[i++]);
                } 
                catch (NumberFormatException e) {
                    System.err.println("Argument must be a number");
                    System.exit(1);
                }
            }
            else {
                System.out.println("ERROR *** Illegal command line option: " + arg);
                PrintUsage();
                System.exit(0);
            }
        }
        
        // check if proper task is specified
        if (taskName.equals("Unknown")) {
            System.out.println("ERROR *** no task specified");
            PrintUsage();    
            System.exit(0);
        }
        
        // next argument must be qrel file
        String qrelFile = (args[i++]);
        
        // create qrel hashtable
        Hashtable qrelIds = new Hashtable();
        // create total relevance per topic hashtable
        Hashtable topics = new Hashtable();
                
        // read qrels
        ReadQrels(qrelFile, qrelIds, topics);
        
        while (i < args.length) {
            // evaluate runs
            String runFile = (args[i]);
            EvalRun(runFile, qrelIds, topics);       
            // reset scores for all topics
            Set set = topics.keySet();
            Iterator itr = set.iterator();
            while (itr.hasNext()) {
                String topicID = (String) itr.next();
                Topic t = (Topic) topics.get(topicID);
                t.ResetTopic(points);
            }
            i++;
        }
    }
    
    public static void ReadQrels(String qrelFile, Hashtable qrelIds, Hashtable topics) {
        try {
            // open file for reading
            BufferedReader qrelReader = new BufferedReader(new FileReader(qrelFile));

            // read file line by line
            String line = null;
            while (( line = qrelReader.readLine()) != null) {
                // parse qrel
                parseQrel(qrelIds, topics, line);
            }
        }
        catch (IOException e) {
            // opening file failed
            System.out.println("IOException error: " + e.getMessage());
        }
        
    }


    public static void parseQrel(Hashtable qrelIds, Hashtable topics, String line) {
        // qrels have fields with whitespace as field separator
        String [] fields = line.split("\\s+");
        Topic t;
        Hashtable articles = new Hashtable();

        // qrels should at least contain the following 4 fields
        // 1: topic id
        // 2: document id
        // 3: ratio of relevant text in this document for this topic 
        // 4: length of this document
        if (fields.length < 4) {
            // error, vital fields are missing
        }

        String topicID = fields[0];
        String qrelID = fields[0] + " " + fields[2];
        int rSize = Integer.parseInt(fields[3]);
        int artLength = Integer.parseInt(fields[4]);
        
        if (!topics.containsKey(topicID)) {
            // initialise total relevance for new topic
            t = new Topic(points);
            topics.put(topicID, t);
        }
        else {
            t = (Topic) topics.get(topicID);
        }
        if (rSize > 0) {
            t.totalRSize += rSize;
            t.totalRelDocs++;
        }
        
        Qrel q;
        if (qrelIds.containsKey(qrelID)) {
            // do nothing
        }
        else {
            // add new topic
            q = new Qrel(qrelID, rSize, artLength);
            qrelIds.put(qrelID, q);
        }

        if (fields.length == 6) {
            // error, BEP but no passages or one passage but no BEP
        }
        else if (fields.length >= 6) {
            q = (Qrel) qrelIds.get(qrelID);
            // article has BEP and passages
            int BEP = Integer.parseInt(fields[5]);
            q.SetBEP(BEP);
            // parse passages
            for (int i = 6; i < fields.length; i++) {
                String [] offsets = fields[i].split(":");
                int start = Integer.parseInt(offsets[0]);
                int end = Integer.parseInt(offsets[1]) + start;
                // add passage to qrel
                q.AddPassage(start, end);
            }

        }
    }

    // eval run using qrels and task
    public static int EvalRun(String runFile, Hashtable qrelIds, Hashtable topics) {
        int rank = 0;
        String prevArtID = "";
        String participantID = "";
        Hashtable articles = new Hashtable();
        
        
        // open runFile for reading
        try {
            // open file for reading
            BufferedReader runReader = new BufferedReader(new FileReader(runFile));

            // read file line by line
            String line = null;
            int lineNumber = 0;
            
            while (( line = runReader.readLine()) != null) {
                // result has fields with whitespace as field separator
                lineNumber++;
                String [] fields = line.split("\\s+");
                
                // result line should contain 8 fields
                if (fields.length > 8) {
                    System.out.println("Error *** (" + runFile + ") too many fields in line " + lineNumber + ": " + line);
                    return 0;
                }
                else if (fields.length < 8) {
                    System.out.println("Error *** (" + runFile + ") fields missing in line " + lineNumber + ": " + line);
                    return 0;
                }
                
                String topicID = (fields[0]);
                // check if topicID has assessments
                if (!topics.containsKey(topicID)) {
                    // do nothing
                }
                else {
                    Topic t = (Topic) topics.get(topicID);
                    Article a;
                    Passage p;
                    
                    // reset prevArtID for new topic
                    if (t.maxRank == 0) {
                        prevArtID = "";
                        rank = 0;
                        articles = new Hashtable();
                        //System.out.println("topic: " + topicID);
                    }
                    
                    // parse result line
                    String result = fields[0] + " " + fields[2];
                    Qrel q;
                    int qRelSize = 0;
                    if (qrelIds.containsKey(result)) {
                        q = (Qrel) qrelIds.get(result);
                        // get relevance size of qrel
                        qRelSize = q.relSize;
                    }
                    String articleID = fields[2];
                    // update rank information
                    if (!articleID.equals(prevArtID)) {
                        rank++;
                        t.UpdateRank(rank);
                        prevArtID = articleID;
                        if (articles.containsKey(articleID)) {
                            // article results are interleaved
                            if (task == 'b' || task == 'r') {
                                System.out.println("ERROR *** (" + runFile + ") article results are interleaved in topic " +topicID + "\tarticle id: " + articleID);
                                System.out.println("\tinterleaved results not allowed in inContext tasks!");
                                return 0;
                            }
                            a = (Article)articles.get(articleID);
                        }
                        else {
                            a = new Article();
                            articles.put(articleID, a);
                            t.retDocs++;
                            if (qRelSize > 0) {
                                t.relDocsFound++;
                            }

                        }
                    }
                    else {
                        if (task == 'b') {
                            // multiple results from single article
                            // not a BIC run
                            System.out.println("ERROR *** (" + runFile + ") multiple results from single article in topic " + topicID + "\tarticle id: " + articleID + "\tin line: " + lineNumber);
                            return 0;
                        }
                        if (task == 'f') {
                            rank++;
                            t.UpdateRank(rank);
                        }
                        a = (Article)articles.get(articleID);
                    }
                    // participants use of the rank field might not be consistent
                    // so we won't use dummyRank
                    //int dummyRank = Integer.parseInt(fields[3]);
                    //double rsv = double.parsedouble(fields[4]);
                    participantID = fields[5];
                    int start = 0;
                    int end = 0;
                    int size;
                    if (fields[6].equals("X") || fields[6].equals("-1")) {
                        // returned passage does not exist according to database
                        // TO DO: give some length to passage outside article range
                        size = meanPassageLength;
                        // make start a huge negative number so that BiC will always be zero
                        start = -10000;
                        end = -10000;
                    }
                    else {
                        start = Integer.parseInt(fields[6]);
                        end = Integer.parseInt(fields[7]) + start;
                        size = end - start;
                        // check for overlap
                        if (a.AddPassage(start, end)) {
                            // new passage does not overlap previously retrieved passages
                        }
                        else {
                            System.out.println("ERROR *** (" + runFile + ") passage overlaps with previously retrieved passages in topic " + topicID + "\tarticle id: " + articleID + "\tin line: " + lineNumber);
                            return 0;
                        }
                    }


                    // sanity checks on result
                    // check if passage has positive length
                    if (end < start) {
                        System.out.println("Error *** (" + runFile + ") negative passage size " + lineNumber + ": " + line);
                        return 0;
                    }

                    // evaluate result
                    int rSize = 0;
                    // see if article is assessed for topic
                    if (qRelSize > 0) {
                        // compute relevance size of passage
                        q = (Qrel) qrelIds.get(result);
                        rSize = q.RelevanceSize(start, end);
                    }
        
                    // compute task specific scores
                    if (task == 'f') {
                        // compute IP
                        t.ComputeIP(rSize, size);
                    }
                    else if (task == 'r') {
                        t.UpdateRetrieved(rank, rSize, size, qRelSize);
                    }
                    else if (task == 'b') {
                        // compute BEP
                        int distance = -1;
                        if (qRelSize > 0) {
                            q = (Qrel) qrelIds.get(result);
                            if (q.BEP > -1) {
                                // article is relevant, has a positive BEP
                                distance = Math.abs(q.BEP - start);
                            }
                        }
                        t.ComputeBEPScore(distance, qRelSize,maxBEPDistance);
                    }
                }
            }  // end of runFile
            
            // compute task specific per topic scores
            System.out.println("<eval run-id=\"" + participantID + "\" file=\"" + runFile + "\">");
            if (task == 'f') {
                EvalFocussed(topics);
            }  // end of focussed eval
            else if (task == 'r' || task == 'b') {
                EvalInContext(topics);
            }  // end of focussed eval
            System.out.println("</eval>");
            return 1;
        }
        catch (IOException e) {
            // opening file failed
            System.out.println("IOException error: " + e.getMessage());
            return 0;
        }
    }
        
    public static void EvalFocussed(Hashtable topics) {
        // variables for overall scores
        double MAiP = 0;
        double [] iP = new double[101];
        NumberFormat nf = NumberFormat.getNumberInstance() ;
        nf.setGroupingUsed(false) ;     // don't group by threes
        nf.setMaximumFractionDigits(2) ;
        nf.setMinimumFractionDigits(2) ;
        // variables for overall statistics
        int num_ret = 0;
        int num_rel = 0;
        int num_rel_ret = 0;
        int ret_size = 0;
        int rel_size = 0;
        int rel_ret_size = 0;
        
        for (int i = 0; i < focussedCutoffs.length; i++) {
            iP[focussedCutoffs[i]] = 0;
        }

        // iterate over all topics
        Vector v = new Vector(topics.keySet());
        Collections.sort(v);
        Iterator itr = v.iterator();
        int topicsInRun = 0;
        while (itr.hasNext()) {
            String topicID = (String) itr.next();
            Topic t = (Topic) topics.get(topicID);
            // count number of topics with at least one returned result
            if (t.maxRank > 0) {
                topicsInRun++;
            }
            
            // skip topics with no results when not using -c
            if (complete || (!complete && t.maxRank > 0)) {

                // print topic statistics
                if (detail) {
                    System.out.println("num_ret\t\t" + topicID + "\t" + t.retDocs);
                    System.out.println("num_rel\t\t" + topicID + "\t" + t.totalRelDocs);
                    System.out.println("num_rel_ret\t" + topicID + "\t" + t.relDocsFound);
                    System.out.println("ret_size\t" + topicID + "\t" + t.retSize);
                    System.out.println("rel_size\t" + topicID + "\t" + t.totalRSize);
                    System.out.println("rel_ret_size\t" + topicID + "\t" + t.relRetSize);
                }
                // interpolate and fill out all recall points
                t.FillIP(points);
                // update overall iP at specific cutoffs
                for (int i = 0; i < points; i++) {
                    double ip = t.GetIP(i);
                    iP[i] += ip;
                }
                
                if (detail) {
                    for (int i = 0; i < focussedCutoffs.length; i++) {
                        double ip = t.GetIP(focussedCutoffs[i]);
                        double recallPoint = (double)focussedCutoffs[i]/(points-1);
                        System.out.println("iP[" + nf.format(recallPoint) + "]\t" + topicID + "\t" + ip);
                    }
                }
                // compute average precision for topic t
                t.ComputeAIP(points);
                // update overall interpolated mean average precision
                double AiP = t.AiP;
                MAiP += AiP;
                if (detail) {
                    System.out.println("AiP\t\t" + topicID + "\t" + AiP);
                }
            }
            // update overall statistics
            num_ret += t.retDocs;
            num_rel += t.totalRelDocs;
            num_rel_ret += t.relDocsFound;
            ret_size += t.retSize;
            rel_size += t.totalRSize;
            rel_ret_size += t.relRetSize;
            
        }

        // print overall statistics
        int num_q = (complete ? topics.size() : topicsInRun);
        System.out.println("num_q\t\tall\t" + num_q);
        
        System.out.println("num_ret\t\tall\t" + num_ret);
        System.out.println("num_rel\t\tall\t" + num_rel);
        System.out.println("num_rel_ret\tall\t" + num_rel_ret);
        System.out.println("ret_size\tall\t" + ret_size);
        System.out.println("rel_size\tall\t" + rel_size);
        System.out.println("rel_ret_size\tall\t" + rel_ret_size);
        
        for (int i = 0; i < points; i++) {
            iP[i] /= (complete ? topics.size() : topicsInRun);
        }
        
        for (int i = 0; i < focussedCutoffs.length; i++) {
            // turn cutoff index into decmial recallPoint
            double recallPoint = (double)focussedCutoffs[i]/100;
            System.out.println("iP[" + nf.format(recallPoint) + "]\tall\t" + iP[focussedCutoffs[i]]);
        }
        MAiP /= (complete ? topics.size() : topicsInRun);
        System.out.println("MAiP\t\tall\t" + MAiP);
        
        // print all 101 recall points for recall/precision curve
        for (int i = 0; i < points; i++) {
            // turn cutoff index into decmial recallPoint
            double recallPoint = (double)i/100;
            System.out.println("ircl_prn." + nf.format(recallPoint) + "\tall\t" + iP[i]);
        }
        

        
    }

    public static void EvalInContext(Hashtable topics) {
            // compute overall scores
        double MAgP = 0;
        double [] gP = new double [inContextCutoffs.length];
        double [] gR = new double [inContextCutoffs.length];
        double [] iRecPrec = new double [points];
            
        // variables for overall statistics
        int num_ret = 0;
        int num_rel = 0;
        int num_rel_ret = 0;
        int ret_size = 0;
        int rel_size = 0;
        int rel_ret_size = 0;
        
        NumberFormat nf = NumberFormat.getNumberInstance() ;
        nf.setGroupingUsed(false) ;     // don't group by threes
        nf.setMaximumFractionDigits(2) ;
        nf.setMinimumFractionDigits(2) ;

        // iterate over all topics
        Vector v = new Vector(topics.keySet());
        Collections.sort(v);
        Iterator itr = v.iterator();
        int topicsInRun = 0;
        while (itr.hasNext()) {
            String topicID = (String) itr.next();
            Topic t = (Topic) topics.get(topicID);
            // count number of topics with at least one returned result
            if (t.maxRank >0) {
                topicsInRun++;
            }
            // print topic statistics
            if (detail) {
                if (!complete && t.maxRank == 0) {
                    // if not -c then skip topics with no retrieved results
                }
                else {
                    System.out.println("num_ret\t\t" + topicID + "\t" + t.maxRank);
                    System.out.println("num_rel\t\t" + topicID + "\t" + t.totalRelDocs);
                    System.out.println("num_rel_ret\t" + topicID + "\t" + t.relDocsFound);
                    System.out.println("ret_size\t" + topicID + "\t" + t.retSize);
                    System.out.println("rel_size\t" + topicID + "\t" + t.totalRSize);
                    System.out.println("rel_ret_size\t" + topicID + "\t" + t.relRetSize);
                }
            }
            // update overall statistics
            num_ret += t.maxRank;
            num_rel += t.totalRelDocs;
            num_rel_ret += t.relDocsFound;
            ret_size += t.retSize;
            rel_size += t.totalRSize;
            rel_ret_size += t.relRetSize;
            
            // compute generalised precision and recall scores for this topic
            t.ComputeGeneralisedScores(task,recallWeight);
            

            for (int i = 0; i < inContextCutoffs.length; i++) {
                int cutoff = inContextCutoffs[i];
                double genPrec = 0;
                double genRec = 0;
                // compute generalised precision and recall at specific cutoffs
                // if number of retrieved ranks is lower than cutoff, use the 
                // precision/recall at the lowest rank
                if (t.maxRank == 0) {
                    // no results retrieved for this topic
                    // do nothing
                }
                else {
                    if (t.gP.size() < cutoff) {
                        genPrec = t.gP.get(t.gP.size()-1);
                        genRec = t.gR.get(t.gR.size()-1);
                    }
                    else {
                        genPrec = t.gP.get(cutoff-1);
                        genRec = t.gR.get(cutoff-1);
                    }
                }
                
                // skip topics with no returned results when not using -c
                if (complete || (!complete && t.maxRank > 0)) {
                    gP[i] += genPrec;
                    gR[i] += genRec;
                    // print per topic details if -q option is used
                    if (detail) {
                        System.out.println("gP[" + cutoff + "]\t\t" + topicID + "\t" + genPrec);
                        System.out.println("gR[" + cutoff + "]\t\t" + topicID + "\t" + genRec);
                    }
                }
                
                
            }
            
                
            // skip topics with no returned results when not using -c
            if (complete || (!complete && t.maxRank > 0)) {
                // print per topic detail if -q option is used
                if (detail) {
                    System.out.println("AgP\t\t" + topicID + "\t" + t.AgP);
                }
                MAgP += t.AgP;
                
                // compute overall precision/recall scores
                for (int i = 0; i < points; i++) {
                    iRecPrec[i] += t.iP[i];
                }
            }
        }

        // print overall statistics
        int num_q = (complete ? topics.size() : topicsInRun);
        System.out.println("num_q\t\tall\t" + num_q);
        
        System.out.println("num_ret\t\tall\t" + num_ret);
        System.out.println("num_rel\t\tall\t" + num_rel);
        System.out.println("num_rel_ret\tall\t" + num_rel_ret);
        System.out.println("ret_size\tall\t" + ret_size);
        System.out.println("rel_size\tall\t" + rel_size);
        System.out.println("rel_ret_size\tall\t" + rel_ret_size);
        
        MAgP /= (complete ? topics.size() : topicsInRun);
        System.out.println("MAgP\t\tall\t" + MAgP);
for (int i = 0; i < inContextCutoffs.length; i++) {
            if (complete) {
                gP[i] /= topics.size();
                gR[i] /= topics.size();
            }
            else {
                gP[i] /= topicsInRun;
                gR[i] /= topicsInRun;
            }
            System.out.println("gP[" + inContextCutoffs[i] + "]\t\tall\t" + gP[i]);
            System.out.println("gR[" + inContextCutoffs[i] + "]\t\tall\t" + gR[i]);
        }
        
        for (int i = 0; i < points; i++) {
            iRecPrec[i] /= (complete ? topics.size() : topicsInRun);
            System.out.println("ircl_prn." + nf.format((double)i/(points-1)) + "\tall\t" + iRecPrec[i]);
        }

        
    }

    private static void PrintUsage() {
        System.out.println("Usage: inex_eval (-f|-r|-b) [-c] [-q] qrels [run+]");
        System.out.println("Version " + version);
        System.out.println("\nYou must specify the task, either:");
        System.out.format("%10s : %s\n", "-f", "focused task");
        System.out.format("%10s : %s\n", "-r", "relevant in context task");
        System.out.format("%10s : %s\n","-b", "best in context task");
        System.out.println("\nOptions:");
        System.out.format("%10s : %s\n","-q", "print per topic scores");
        System.out.format("%10s : %s\n", "-c", "evaluate over complete set of topics in Qrels");
        System.out.format("%10s : %s\n", "-n int", "maximal BEP distance (default " + maxBEPDistance +")");
        System.out.format("%10s : %s\n", "-beta num", "relative weight of recall in F-measure (default " + recallWeight + ")");   
    }
}
