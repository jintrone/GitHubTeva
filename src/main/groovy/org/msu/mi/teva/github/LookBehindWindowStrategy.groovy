package org.msu.mi.teva.github

import edu.mit.cci.text.windowing.WindowStrategy
import edu.mit.cci.text.windowing.Windowable
import edu.mit.cci.util.U

/**
 * Created by josh on 2/1/14.
 */
class LookBehindWindowStrategy implements WindowStrategy<Windowable> {

    Date[][] windows;
    List<? extends Windowable> data;
    int priortokens
    boolean addFirst = false;

    public LookBehindWindowStrategy(int priortokens, Date[] ... windows) {
        this.windows = windows;
        this.priortokens = priortokens
    }

    public LookBehindWindowStrategy(int priortokens, boolean addFirst, Date[] ... windows) {
        this.windows = windows;
        this.priortokens = priortokens
        this.addFirst = addFirst
    }


    @Override
    int getNumberWindows() {
        windows.length
    }

    @Override
    List<Windowable> getWindow(int i) {
        int end = U.binarySearch(data, windows[i][1], { Date post, Date post1 ->
            post <=> post1
        } as Comparator<Date>, { it.start } as U.Adapter<Windowable, Date>)
        if (end < 0) {
            end = -1 * (end+2);
        }

        if (end < 0 || data[end].start < windows[i][0]) {
            return Collections.emptyList();
        } else {
            def tokencount = 0
            def start = end
            while (start > 0 && tokencount < priortokens) {
                tokencount += data[start--].tokens.size()
            }

            if (start == 0 || !addFirst) {data[start..end]}
            else data[0,start..end]
        }

    }

    @Override
    Date[][] getWindowBoundaries() {windows}

    @Override
    void setData(List<? extends Windowable> data) {
        this.data = data;
    }
}
