/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package inex_eval;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

/**
 *
 * @author marijnkoolen
 */
class Article {
    // fields
    Hashtable passages;
    int index;
    
    // methods
    // constructor function
    public Article() {
        passages = new Hashtable();
        index = 0;
    }
    
    // add retrieved passage to article
    public boolean AddPassage(int start, int end) {
        // iterate over already retrieved passages
        Set set = passages.keySet();
        Iterator itr = set.iterator();
        while (itr.hasNext()) {
            int passage = (Integer) itr.next();
            Passage p = (Passage)passages.get(passage);
            if (p.start >= start && p.start < end) {
                // new passage overlaps with already retrieved passage
                return false;
            }
            if (p.end > start && p.end <= end) {
                // new passage overlaps with already retrieved passage
                return false;
            }
        }
        // new passage does not overlap with already retrieved passages
        Passage newP = new Passage(start, end);
        passages.put(index, newP);
        index++;
                
        return true;
    }

}
