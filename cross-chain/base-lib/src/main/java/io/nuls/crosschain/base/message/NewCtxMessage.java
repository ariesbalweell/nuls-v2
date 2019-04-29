package io.nuls.crosschain.base.message;

import io.nuls.base.basic.NulsByteBuffer;
import io.nuls.base.basic.NulsOutputStreamBuffer;
import io.nuls.base.data.NulsDigestData;
import io.nuls.base.data.Transaction;
import io.nuls.crosschain.base.message.base.BaseMessage;
import io.nuls.tools.exception.NulsException;
import io.nuls.tools.parse.SerializeUtils;

import java.io.IOException;

/**
 * 链内广播的完整跨链交易
 * @author tag
 * @date 2019/4/4
 */
public class NewCtxMessage extends BaseMessage {
    /**
     * 本链协议跨链交易
     * */
    private Transaction ctx;
    /**
     * 本链协议跨链交易hash
     * */
    private NulsDigestData requestHash;
    @Override
    public int size() {
        int size = 0;
        //tx
        size += SerializeUtils.sizeOfNulsData(ctx);
        size += SerializeUtils.sizeOfNulsData(requestHash);
        return size;
    }

    @Override
    public void serializeToStream(NulsOutputStreamBuffer stream) throws IOException {
        stream.writeNulsData(ctx);
        stream.writeNulsData(requestHash);
    }

    @Override
    public void parse(NulsByteBuffer byteBuffer) throws NulsException {
        this.ctx = byteBuffer.readNulsData(new Transaction());
        this.requestHash = byteBuffer.readHash();
    }

    public Transaction getCtx() {
        return ctx;
    }

    public void setCtx(Transaction ctx) {
        this.ctx = ctx;
    }

    public NulsDigestData getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(NulsDigestData requestHash) {
        this.requestHash = requestHash;
    }
}
