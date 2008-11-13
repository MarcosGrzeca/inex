/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package inex_eval;

import java.util.Hashtable;
import java.util.Stack;

/**
 *
 * @author marijnkoolen
 */
class Qrel {
    // fields
    public static String qrelID;
    public double relRatio;
    public int relSize;
    public int artLength;
    public int BEP;
    public int index;
    public Hashtable passages;
    
    // methods
    // constructor function
    public Qrel(String QID, int rsize, int length) {
        qrelID = QID;
        relSize = rsize;
        artLength = length;
        index = 0;
        passages = new Hashtable();
        BEP = -1;
    }
    
    // set the Best Entry Point
    public void SetBEP(int entryPoint) {
        BEP = entryPoint;
    }
    // add new passage to qrel
    public void AddPassage(int s, int e) {
        index++;
        Passage p = new Passage(s, e);
        passages.put(index, p);
    }

    // compute precision of passage
    public int RelevanceSize(int start, int end) {
        int relevanceSize = 0;
        // iterate
        for (int i = 1; i <= this.passages.size(); i++) {
            Passage relevantPassage = (Passage) this.passages.get(i);
            
            // option 1: retrieved passage starts before or at start of relevant passage
            if (start <= relevantPassage.start) {
                // retrieved passage ends before or at start of relevant passage
                if (end <= relevantPassage.start) {
                    // option 1a: relevant passage is after retrieved passage
                    // do nothing
                }
                // retrieved passage ends after start of relevant passage
                // option 1b: retrieved passage ends at or after end of relevant passage
                else if (end >= relevantPassage.end) {
                    // relevant passage is contained in retrieved passage
                    relevanceSize += relevantPassage.end - relevantPassage.start;
                }
                // option 1c: retrieved passage ends before end of relevant passage
                else if (end < relevantPassage.end) {
                    // retrieved passage contains the first part of the relevant passage
                    relevanceSize += end - relevantPassage.start;
                }
            }
            // retrieved passage starts after start of relevant passage
            // option 2: retrieved passage starts before end of relevant passage
            else if (start < relevantPassage.end){
                // option 2a: retrieved passage ends before or at end of relevant passage
                if (end <= relevantPassage.end) {
                    // retrieved passage is contained in relevant passage
                    relevanceSize += end - start;
                    // no need to check further relevant passages in this article
                    //return relevanceSize;
                }
                // option 2b: retrieved passage ends after end of relevant passage
                else if (end > relevantPassage.end) {
                    // retrieved passage contains last part of relevant passage
                    relevanceSize += relevantPassage.end - start;
                }
                    
            }
            // option 3: retrieved passage starts at or after end of relevant passage
            else {
                // retrieved passage is after relevant passage
                // do nothing
            }
        }
        // return relevanceSize
        return relevanceSize;
    }
}
