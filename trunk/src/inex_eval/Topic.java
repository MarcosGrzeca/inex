/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package inex_eval;

import java.util.ArrayList;

/**
 *
 * @author marijnkoolen
 */
class Topic {
    // fields
    int maxRank;
    int relDocsFound;
    int retDocs;
    int totalRelDocs;
    int totalRSize;
    int relRetSize;
    int retSize;
    // focussed eval variables
    double [] iP = new double[101];
    double AiP;
    // in context eval variables
    ArrayList<Integer> artRelRetSize;
    ArrayList<Integer> artRetSize;
    ArrayList<Integer> artRelSize;
    ArrayList<Double> bepScore;
    ArrayList<Integer> relAtRank;
    ArrayList<Double> gP;
    ArrayList<Double> gR;
    double AgP;
    //int n;
    
    // methods
    // constructor function
    public Topic(int points)
    {
        totalRelDocs = 0;
        totalRSize = 0;
        maxRank = 0;
        relDocsFound = 0;
        retDocs = 0;
        relRetSize = 0;
        retSize = 0;
        for (int i = 0; i <= points-1; i++) {
            iP[i] = 0;
        }
        AiP = 0;
        artRelRetSize = new ArrayList<Integer>();
        artRetSize = new ArrayList<Integer>();
        artRelSize = new ArrayList<Integer>();
        bepScore = new ArrayList<Double>();
        relAtRank = new ArrayList<Integer>();
        gP = new ArrayList<Double>();
        gR = new ArrayList<Double>();
        AgP = 0;
        //n = maxBEPDistance; // JK: was 1000;
    }

    public void ResetTopic(int points) {
        maxRank = 0;
        relDocsFound = 0;
        retDocs = 0;
        relRetSize = 0;
        retSize = 0;
        for (int i = 0; i <= points-1; i++) {
            iP[i] = 0;
        }
        AiP = 0;
        artRelRetSize = new ArrayList<Integer>();
        artRetSize = new ArrayList<Integer>();
        artRelSize = new ArrayList<Integer>();
        bepScore = new ArrayList<Double>();
        relAtRank = new ArrayList<Integer>();
        gP = new ArrayList<Double>();
        gR = new ArrayList<Double>();
        AgP = 0;
        //n = 1000;
    }
    
    // update rank information
    public void UpdateRank(int rank) {
        maxRank = rank;
    }
    
    // compute precision at specific recall point
    public void ComputeIP(int rSize, int size) {
        // update relevant retrieved and retrieved sizes
        relRetSize += rSize;
        retSize += size;
        // compute Precision at r
        double precAtR = (double)relRetSize /retSize;
        // compuate Recall at r
        double recallAtR = (double)relRetSize / totalRSize;

        int recallPoint = (int) Math.floor(100 * (double) recallAtR);
        // recallPoint should be between 0 and 100
        if (recallPoint < 0 || recallPoint > 100) {
            System.out.println("Impossible recall point: " + recallPoint);
        }
        else {
            // when updating the same rounded down recall point, always keep the highest precision score
            if (iP[recallPoint] < precAtR) {
                iP[recallPoint] = precAtR;
            }
        }
    }
    
    // get precision at specific recallPoint
    public double GetIP(int recallPoint) {
        return iP[recallPoint];
    }
    
    // compute interpolated precision with 101-point recall levels
    public void FillIP(int points) {
        double maxPrec = 0;
        for (int i = points - 1; i >= 0; i--) {
            if (iP[i] > maxPrec) {
                maxPrec = iP[i];
            }
            else if (iP[i] < maxPrec) {
                iP[i] = maxPrec;
            }
            //System.out.println("i: " + i + " maxPrec: " + maxPrec);
        }
    }
    
    // compute interpolated average precision
    public void ComputeAIP(int points) {
        for (int i = 0; i < points; i++) {
            AiP += iP[i];
        }
        AiP /= points;
    }
    
    
    // update size of retrieved and retrieved relevant text for article a in this topic
    public void UpdateRetrieved(int rank, int relS, int retS, int articleRSize) {
        int index = maxRank - 1;
        
        // if text has already been retrieved for this rank, update sizes
        if (rank == artRetSize.size()) {
            artRelRetSize.set(index, relS + artRelRetSize.get(index));
            artRetSize.set(index, retS + artRetSize.get(index));
        }
        // else set sizes for next rank
        else {
            artRetSize.add(retS);
            artRelRetSize.add(relS);
            // first relevant text in article found
            // set total relevance size for article
            artRelSize.add(articleRSize);
            // store recall at each rank
            relAtRank.add(relDocsFound);
            //System.out.println("rank: " + relAtRank.size() + "\tnum_rel_ret: " + relDocsFound);
        }
        // update total retrieved and relevant retrieved text size per topic
        retSize += retS;
        relRetSize += relS;
    }
    
    // compute gP and gR per topic
    public void ComputeGeneralisedScores(char task, double beta) {
        // number of recall points
        int points = 11;
        
        // sum of precision scores 
        double sumScores = 0;
        // sum of generalised precision scores
        double sumGP = 0;
        
        for (int rank = 1; rank <= maxRank; rank++) {
            // variables to store article precision
            double docScore = 0;
            int index = rank -1;
            double docP = 0;
            double docR = 0;
            
            // compute document score
            if (task == 'r' && artRelRetSize.get(index) > 0) {
                docP = (double)artRelRetSize.get(index) / artRetSize.get(index);
                docR = (double)artRelRetSize.get(index) / artRelSize.get(index);
                //JK: docScore = (double)(2 * docP * docR) / (docP + docR);
                docScore = (double)((1 + (beta * beta)) * docP * docR) 
                        / ((beta * beta * docP) + docR);
            }
            else if (task == 'b') {
                docScore = bepScore.get(index);
            }
            
            // compute precision and recall
            double precAtR = 0;
            double recAtR = 0;
            sumScores += docScore;
            precAtR = (double)sumScores / rank;
            recAtR = (double)relAtRank.get(index) / totalRelDocs;
            //System.out.println("docScore: " + docScore + "\tsumScores: " + sumScores + "\tprecAtR: " + precAtR + "\trecAtR: " + recAtR);
            
            gP.add(precAtR);
            gR.add(recAtR);
            if (artRelSize.get(index) > 0) {
                sumGP += (double)sumScores / rank;
            }
            
            // 11-point interpolated precision
            int recallPoint = (int) Math.floor(10 * (double) recAtR);
            if (iP[recallPoint] < precAtR) {
                iP[recallPoint] = precAtR;
            }
        }
        
        // compute recall over whole topic
        double recall = (double)relDocsFound / totalRelDocs;
        
        AgP = (sumGP / ((double)relDocsFound)) * recall;
        AgP = (double)sumGP / totalRelDocs;
        
        // fill the interpolated precision curve
        FillIP(points);
        //ComputeAIP(points);
        
    }
    
    // compute 
    // compute bep score
    public void ComputeBEPScore(int distance, int articleRSize, int n) {
        double score = 0;
        if (distance > -1) {
            if (distance > n) {
                score = 0;
            }
            else {
                score = ((double)n - distance) / n;
            }
        }
        bepScore.add(score);
        relAtRank.add(relDocsFound);
        artRelSize.add(articleRSize);
    }
}
