package net.cnri.cordra;

import net.cnri.cordra.model.Range;

public class PayloadWithRange extends net.cnri.cordra.api.Payload {
    public final Range range;

    public PayloadWithRange(net.cnri.cordra.api.Payload payload, Range range) {
        this.range = range;
        this.size = payload.size;
        this.name = payload.name;
        this.filename = payload.filename;
        this.mediaType = payload.mediaType;
        this.setInputStream(payload.getInputStream());
    }
}
