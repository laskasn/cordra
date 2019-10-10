package net.cnri.cordra.util.cmdline;

import java.util.HashSet;
import java.util.Set;

public class ContiguousPositiveLongs {

    private long highest;
    private Set<Long> nums = new HashSet<>();

    public ContiguousPositiveLongs() {
        highest = -1;
    }

    public ContiguousPositiveLongs(Integer start) {
        if (start < 0) {
            throw new IllegalArgumentException("Start must not be less than 0");
        }
        highest = start;
    }

    public long highest() {
        return highest;
    }

    public synchronized void insert(long n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must not be less than 0");
        }
        nums.add(n);
        long i = highest;
        while (true) {
            i++;
            if (nums.contains(i)) {
                highest = i;
                nums.remove(i);
            } else {
                break;
            }
        }
    }
}
